package com.jorge.homecenix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs HomeStore against a local emulator, using the same open() path as the plugin.
 * Needs FIRESTORE_EMULATOR_HOST; without it these tests are skipped and never touch a real database.
 */
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
class HomeStoreEmulatorTest {

    @TempDir
    Path tempDir;

    /** A throwaway service account: the emulator does not check it. */
    private HomeStore openStore() throws IOException, NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String key = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        String json = """
                {"type": "service_account",
                 "project_id": "demo-homecenix",
                 "private_key_id": "test",
                 "private_key": "-----BEGIN PRIVATE KEY-----\\n%s\\n-----END PRIVATE KEY-----\\n",
                 "client_email": "test@demo-homecenix.iam.gserviceaccount.com",
                 "client_id": "1",
                 "token_uri": "https://oauth2.googleapis.com/token"}
                """.formatted(key);
        Path account = tempDir.resolve("account.json");
        Files.writeString(account, json);
        return HomeStore.open(account);
    }

    @Test
    void savedHomeIsReturnedToItsOwner() throws Exception {
        try (HomeStore store = openStore()) {
            UUID owner = UUID.randomUUID();
            Home home = new Home("world", 1.5, 64, -3.25, 90f, -10f);

            store.save(owner, "base", home).get();

            assertEquals(Map.of("base", home), store.findAll(owner).get());
        }
    }

    @Test
    void findAllReturnsEveryHomeOfTheOwner() throws Exception {
        try (HomeStore store = openStore()) {
            UUID owner = UUID.randomUUID();
            Home base = new Home("world", 0, 64, 0, 0f, 0f);
            Home mine = new Home("world", 50, 12, 50, 0f, 0f);

            store.save(owner, "base", base).get();
            store.save(owner, "mina", mine).get();

            assertEquals(Map.of("base", base, "mina", mine), store.findAll(owner).get());
        }
    }

    @Test
    void homesAreNotVisibleToOtherPlayers() throws Exception {
        try (HomeStore store = openStore()) {
            UUID owner = UUID.randomUUID();
            UUID other = UUID.randomUUID();

            store.save(owner, "base", new Home("world", 0, 64, 0, 0f, 0f)).get();

            assertTrue(store.findAll(other).get().isEmpty());
        }
    }

    @Test
    void savingSameNameReplacesHome() throws Exception {
        try (HomeStore store = openStore()) {
            UUID owner = UUID.randomUUID();
            Home updated = new Home("world_nether", 10, 70, 10, 0f, 0f);

            store.save(owner, "base", new Home("world", 0, 64, 0, 0f, 0f)).get();
            store.save(owner, "base", updated).get();

            assertEquals(Map.of("base", updated), store.findAll(owner).get());
        }
    }
}
