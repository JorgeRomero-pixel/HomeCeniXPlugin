package com.jorge.homecenix;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

interface HomeDatabase {

    CompletableFuture<Void> save(UUID owner, String name, Home home);

    CompletableFuture<Map<String, Home>> findAll(UUID owner);
}
