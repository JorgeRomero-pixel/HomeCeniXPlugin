package com.jorge.homecenix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class HomeCeniXPlugin extends JavaPlugin {

    private static final String CREDENTIALS_FILE = "firebase-service-account.json";
    private static final long CACHE_RETENTION_TICKS = 10 * 60 * 20;

    private HomeStore store;
    private HomeCache cache;

    @Override
    public void onEnable() {
        Path credentials = getDataFolder().toPath().resolve(CREDENTIALS_FILE);
        try {
            Files.createDirectories(credentials.getParent());
            if (Files.notExists(credentials)) {
                getLogger().severe("Falta " + credentials + ". Plugin desactivado.");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            store = HomeStore.open(credentials);
        } catch (IOException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "No se pudo conectar con Firebase. Plugin desactivado.", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        cache = new HomeCache(store, task -> {
            BukkitTask scheduled = Bukkit.getScheduler().runTaskLater(this, task, CACHE_RETENTION_TICKS);
            return scheduled::cancel;
        });
        Bukkit.getPluginManager().registerEvents(new ConnectionListener(cache), this);
        getCommand("sethome").setExecutor(new SetHomeCommand(this, cache));
        getCommand("home").setExecutor(new HomeCommand(this, cache));
    }

    @Override
    public void onDisable() {
        if (cache != null) {
            cache.clear();
            cache = null;
        }
        if (store != null) {
            store.close();
            store = null;
        }
    }

    /** Runs a task on the server thread, unless the plugin has been disabled meanwhile. */
    void runOnServerThread(Runnable task) {
        if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, task);
        }
    }
}
