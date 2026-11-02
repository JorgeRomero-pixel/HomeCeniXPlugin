package com.jorge.homecenix;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Firestore access. Homes live at players/{uuid}/homes/{name}, so every lookup is
 * scoped to its owner and one player's homes cannot be reached through another's.
 */
final class HomeStore implements HomeDatabase, AutoCloseable {

    private static final String APP_NAME = "HomeCeniXPlugin";
    private static final long TIMEOUT_SECONDS = 10;

    private final Firestore firestore;
    private final Runnable closeAction;
    private final ExecutorService executor;

    private HomeStore(Firestore firestore, Runnable closeAction) {
        this.firestore = firestore;
        this.closeAction = closeAction;
        ClassLoader pluginLoader = HomeStore.class.getClassLoader();
        this.executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "HomeCeniX-Firestore");
            thread.setDaemon(true);
            thread.setContextClassLoader(pluginLoader);
            return thread;
        });
    }

    static HomeStore open(Path serviceAccount) throws IOException {
        // gRPC looks up its transport through the context class loader.
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(HomeStore.class.getClassLoader());
        try (InputStream credentials = Files.newInputStream(serviceAccount)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();
            FirebaseApp app = FirebaseApp.initializeApp(options, APP_NAME);
            return new HomeStore(FirestoreClient.getFirestore(app), app::delete);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Override
    public CompletableFuture<Void> save(UUID owner, String name, Home home) {
        Map<String, Object> data = Map.of(
                "world", home.world(),
                "x", home.x(),
                "y", home.y(),
                "z", home.z(),
                "yaw", (double) home.yaw(),
                "pitch", (double) home.pitch());
        return CompletableFuture.runAsync(() -> await(homesOf(owner).document(name).set(data)), executor);
    }

    @Override
    public CompletableFuture<Map<String, Home>> findAll(UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Home> homes = new HashMap<>();
            for (DocumentSnapshot document : await(homesOf(owner).get()).getDocuments()) {
                homes.put(document.getId(), toHome(document));
            }
            return homes;
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
        closeAction.run();
    }

    private CollectionReference homesOf(UUID owner) {
        return firestore.collection("players").document(owner.toString()).collection("homes");
    }

    private static Home toHome(DocumentSnapshot snapshot) {
        return new Home(
                snapshot.getString("world"),
                snapshot.getDouble("x"),
                snapshot.getDouble("y"),
                snapshot.getDouble("z"),
                snapshot.getDouble("yaw").floatValue(),
                snapshot.getDouble("pitch").floatValue());
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Firestore", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Firestore request failed", e);
        }
    }
}
