package com.jorge.homecenix;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps each player's homes in memory, so commands do not query the database. A player's
 * homes are loaded once and dropped a while after they disconnect, unless they return first.
 *
 * <p>Call every method from the server thread. Returned futures complete on other threads.
 */
final class HomeCache {

    static final int MAX_HOMES = 5;

    enum SaveResult { SAVED, LIMIT_REACHED }

    /** Schedules a task to run after the retention period; the returned Runnable cancels it. */
    interface ExpiryScheduler {
        Runnable schedule(Runnable task);
    }

    private static final class Entry {
        /** Completes once the homes are loaded and every save queued so far has finished. */
        CompletableFuture<Map<String, Home>> homes;
        Runnable cancelExpiry;
    }

    private final HomeDatabase database;
    private final ExpiryScheduler expiryScheduler;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    HomeCache(HomeDatabase database, ExpiryScheduler expiryScheduler) {
        this.database = database;
        this.expiryScheduler = expiryScheduler;
    }

    void onJoin(UUID player) {
        Entry entry = entryOf(player);
        cancelExpiry(entry);
    }

    void onQuit(UUID player) {
        Entry entry = entries.get(player);
        if (entry == null) {
            return;
        }
        cancelExpiry(entry);
        entry.cancelExpiry = expiryScheduler.schedule(() -> entries.remove(player, entry));
    }

    /** Fails if the homes could not be loaded; the next call then retries the load. */
    CompletableFuture<Map<String, Home>> homes(UUID player) {
        return entryOf(player).homes;
    }

    /**
     * Writes to the database first, and updates the cache only if that succeeds. Replacing an
     * existing home is always allowed. Saves run one at a time per player, so rapid commands
     * cannot get past the limit.
     */
    CompletableFuture<SaveResult> save(UUID player, String name, Home home) {
        Entry entry = entryOf(player);
        CompletableFuture<Map<String, Home>> previous = entry.homes;
        CompletableFuture<SaveResult> saved = previous.thenCompose(homes -> {
            if (homes.size() >= MAX_HOMES && !homes.containsKey(name)) {
                return CompletableFuture.completedFuture(SaveResult.LIMIT_REACHED);
            }
            return database.save(player, name, home).thenApply(ignored -> {
                homes.put(name, home);
                return SaveResult.SAVED;
            });
        });
        // Later commands wait for this save, but a failed save must not lose the loaded homes.
        entry.homes = saved.handle((ignored, error) -> null).thenCompose(ignored -> previous);
        return saved;
    }

    /** Names starting with the prefix, sorted. Never queries the database or waits for it. */
    List<String> suggestions(UUID player, String prefix) {
        Entry entry = entries.get(player);
        if (entry == null || !entry.homes.isDone() || entry.homes.isCompletedExceptionally()) {
            return List.of();
        }
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return entry.homes.join().keySet().stream()
                .filter(name -> name.startsWith(lowerPrefix))
                .sorted()
                .toList();
    }

    void clear() {
        entries.clear();
    }

    private Entry entryOf(UUID player) {
        Entry existing = entries.get(player);
        if (existing != null) {
            return existing;
        }
        Entry entry = new Entry();
        entries.put(player, entry);
        CompletableFuture<Map<String, Home>> load = database.findAll(player)
                .thenApply(homes -> new ConcurrentHashMap<>(homes));
        entry.homes = load;
        load.exceptionally(error -> {
            entries.remove(player, entry);
            return null;
        });
        return entry;
    }

    private static void cancelExpiry(Entry entry) {
        if (entry.cancelExpiry != null) {
            entry.cancelExpiry.run();
            entry.cancelExpiry = null;
        }
    }
}
