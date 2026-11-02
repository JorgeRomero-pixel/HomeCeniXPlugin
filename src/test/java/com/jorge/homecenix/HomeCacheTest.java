package com.jorge.homecenix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class HomeCacheTest {

    private static final Home BASE = new Home("world", 1, 64, 2, 0f, 0f);
    private static final Home MINE = new Home("world", 50, 12, 50, 0f, 0f);

    private static final class FakeDatabase implements HomeDatabase {
        final Map<UUID, Map<String, Home>> stored = new HashMap<>();
        CompletableFuture<Map<String, Home>> nextLoad;
        CompletableFuture<Void> nextSave;
        int reads;
        int writes;

        @Override
        public CompletableFuture<Map<String, Home>> findAll(UUID owner) {
            reads++;
            if (nextLoad != null) {
                CompletableFuture<Map<String, Home>> load = nextLoad;
                nextLoad = null;
                return load;
            }
            return CompletableFuture.completedFuture(new HashMap<>(stored.getOrDefault(owner, Map.of())));
        }

        @Override
        public CompletableFuture<Void> save(UUID owner, String name, Home home) {
            writes++;
            if (nextSave != null) {
                CompletableFuture<Void> save = nextSave;
                nextSave = null;
                return save;
            }
            stored.computeIfAbsent(owner, ignored -> new HashMap<>()).put(name, home);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeExpiry implements HomeCache.ExpiryScheduler {
        final List<Runnable> pending = new ArrayList<>();

        @Override
        public Runnable schedule(Runnable task) {
            pending.add(task);
            return () -> pending.remove(task);
        }

        void elapse() {
            new ArrayList<>(pending).forEach(Runnable::run);
            pending.clear();
        }
    }

    private final FakeDatabase database = new FakeDatabase();
    private final FakeExpiry expiry = new FakeExpiry();
    private final HomeCache cache = new HomeCache(database, expiry);
    private final UUID player = UUID.randomUUID();

    @Test
    void joinLoadsHomesAndCommandsDoNotQueryAgain() throws Exception {
        database.stored.put(player, Map.of("base", BASE));

        cache.onJoin(player);
        assertEquals(1, database.reads);

        assertEquals(BASE, cache.homes(player).get().get("base"));
        assertEquals(BASE, cache.homes(player).get().get("base"));
        assertEquals(1, database.reads);
    }

    @Test
    void returningBeforeExpiryDoesNotQueryAgain() throws Exception {
        database.stored.put(player, Map.of("base", BASE));
        cache.onJoin(player);

        cache.onQuit(player);
        cache.onJoin(player);

        assertEquals(1, database.reads);
        assertTrue(expiry.pending.isEmpty(), "returning must cancel the expiry");
        assertEquals(BASE, cache.homes(player).get().get("base"));
    }

    @Test
    void returningAfterExpiryQueriesAgain() {
        cache.onJoin(player);
        cache.onQuit(player);

        expiry.elapse();
        cache.onJoin(player);

        assertEquals(2, database.reads);
    }

    @Test
    void reconnectingRestartsTheExpiry() {
        cache.onJoin(player);
        cache.onQuit(player);
        cache.onJoin(player);
        cache.onQuit(player);

        assertEquals(1, expiry.pending.size());
    }

    @Test
    void savedHomeIsServedWithoutQuerying() throws Exception {
        cache.onJoin(player);

        cache.save(player, "base", BASE).get();

        assertEquals(BASE, cache.homes(player).get().get("base"));
        assertEquals(1, database.reads);
        assertEquals(BASE, database.stored.get(player).get("base"));
    }

    @Test
    void failedSaveKeepsCacheAndReportsFailure() throws Exception {
        database.stored.put(player, Map.of("base", BASE));
        cache.onJoin(player);
        database.nextSave = CompletableFuture.failedFuture(new IllegalStateException("down"));

        assertTrue(cache.save(player, "mina", MINE).isCompletedExceptionally());

        Map<String, Home> homes = cache.homes(player).get();
        assertEquals(BASE, homes.get("base"));
        assertFalse(homes.containsKey("mina"), "a home the database rejected must not be cached");
    }

    @Test
    void failedLoadIsRetriedInsteadOfLookingLikeNoHomes() throws Exception {
        database.stored.put(player, Map.of("base", BASE));
        database.nextLoad = CompletableFuture.failedFuture(new IllegalStateException("down"));

        cache.onJoin(player);

        assertEquals(BASE, cache.homes(player).get().get("base"));
        assertEquals(2, database.reads);
    }

    @Test
    void saveDuringLoadIsNotLostWhenLoadFinishes() throws Exception {
        CompletableFuture<Map<String, Home>> load = new CompletableFuture<>();
        database.nextLoad = load;
        cache.onJoin(player);

        CompletableFuture<HomeCache.SaveResult> saved = cache.save(player, "mina", MINE);
        assertEquals(0, database.writes, "the save must wait for the load");

        load.complete(Map.of("base", BASE));
        saved.get();

        Map<String, Home> homes = cache.homes(player).get();
        assertEquals(BASE, homes.get("base"));
        assertEquals(MINE, homes.get("mina"));
    }

    @Test
    void playersOnlySeeTheirOwnHomes() throws Exception {
        UUID other = UUID.randomUUID();
        cache.onJoin(player);
        cache.onJoin(other);

        cache.save(player, "base", BASE).get();

        assertFalse(cache.homes(other).get().containsKey("base"));
    }

    private void fillToLimit() throws Exception {
        for (int i = 0; i < HomeCache.MAX_HOMES; i++) {
            assertEquals(HomeCache.SaveResult.SAVED, cache.save(player, "casa" + i, BASE).get());
        }
    }

    @Test
    void newHomeOverLimitIsRejectedWithoutWriting() throws Exception {
        fillToLimit();
        int writes = database.writes;

        assertEquals(HomeCache.SaveResult.LIMIT_REACHED, cache.save(player, "extra", MINE).get());

        assertEquals(writes, database.writes);
        Map<String, Home> homes = cache.homes(player).get();
        assertEquals(5, homes.size());
        assertFalse(homes.containsKey("extra"));
    }

    @Test
    void existingHomeCanBeReplacedAtLimit() throws Exception {
        fillToLimit();

        assertEquals(HomeCache.SaveResult.SAVED, cache.save(player, "casa0", MINE).get());

        assertEquals(MINE, cache.homes(player).get().get("casa0"));
        assertEquals(5, cache.homes(player).get().size());
    }

    @Test
    void limitHoldsForSavesQueuedDuringLoad() throws Exception {
        CompletableFuture<Map<String, Home>> load = new CompletableFuture<>();
        database.nextLoad = load;
        cache.onJoin(player);

        List<CompletableFuture<HomeCache.SaveResult>> saves = new ArrayList<>();
        for (int i = 0; i < HomeCache.MAX_HOMES + 2; i++) {
            saves.add(cache.save(player, "casa" + i, BASE));
        }
        load.complete(Map.of());

        for (int i = 0; i < saves.size(); i++) {
            assertEquals(i < HomeCache.MAX_HOMES ? HomeCache.SaveResult.SAVED : HomeCache.SaveResult.LIMIT_REACHED,
                    saves.get(i).get());
        }
        assertEquals(HomeCache.MAX_HOMES, database.writes);
    }

    @Test
    void limitAppliesPerPlayer() throws Exception {
        fillToLimit();
        UUID other = UUID.randomUUID();

        assertEquals(HomeCache.SaveResult.SAVED, cache.save(other, "base", BASE).get());
    }

    @Test
    void suggestsOwnHomesMatchingPrefixSorted() throws Exception {
        cache.onJoin(player);
        cache.save(player, "mina", MINE).get();
        cache.save(player, "base", BASE).get();
        cache.save(player, "barco", BASE).get();

        assertEquals(List.of("barco", "base", "mina"), cache.suggestions(player, ""));
        assertEquals(List.of("barco", "base"), cache.suggestions(player, "BA"));
        assertEquals(List.of(), cache.suggestions(player, "x"));
    }

    @Test
    void suggestionsNeverIncludeOtherPlayersHomes() throws Exception {
        UUID other = UUID.randomUUID();
        cache.onJoin(player);
        cache.onJoin(other);
        cache.save(player, "base", BASE).get();

        assertEquals(List.of(), cache.suggestions(other, ""));
    }

    @Test
    void suggestionsDoNotQueryOrWaitForTheDatabase() {
        assertEquals(List.of(), cache.suggestions(player, ""));
        assertEquals(0, database.reads);

        database.nextLoad = new CompletableFuture<>();
        cache.onJoin(player);

        assertEquals(List.of(), cache.suggestions(player, ""));
        assertEquals(1, database.reads);
    }

    @Test
    void quittingWithoutCachedHomesSchedulesNothing() {
        cache.onQuit(player);

        assertTrue(expiry.pending.isEmpty());
    }
}
