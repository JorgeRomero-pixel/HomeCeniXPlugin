package com.jorge.homecenix;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class ConnectionListener implements Listener {

    private static final String COMMAND_NAMESPACE = "homecenixplugin:";

    private final HomeCache cache;

    ConnectionListener(HomeCache cache) {
        this.cache = cache;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cache.onJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.onQuit(event.getPlayer().getUniqueId());
    }

    /** Hides the namespaced duplicates from chat suggestions, leaving just /home and /sethome. */
    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        event.getCommands().removeIf(command -> command.startsWith(COMMAND_NAMESPACE));
    }
}
