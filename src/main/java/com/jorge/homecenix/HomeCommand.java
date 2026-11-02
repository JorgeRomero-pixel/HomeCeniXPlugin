package com.jorge.homecenix;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

final class HomeCommand implements TabExecutor {

    private final HomeCeniXPlugin plugin;
    private final HomeCache cache;

    HomeCommand(HomeCeniXPlugin plugin, HomeCache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Solo los jugadores pueden usar este comando.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            return false;
        }
        Optional<String> name = HomeName.normalize(args[0]);
        if (name.isEmpty()) {
            player.sendMessage(Component.text(
                    "Nombre no válido: usa de 1 a 32 letras, números, _ o -.", NamedTextColor.RED));
            return true;
        }

        // The lookup always uses the sender's own UUID: no command argument can select another player.
        UUID owner = player.getUniqueId();
        cache.homes(owner).whenComplete((homes, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "No se pudieron cargar las casas de " + owner, error);
            }
            plugin.runOnServerThread(() -> {
                Player online = Bukkit.getPlayer(owner);
                if (online == null) {
                    return;
                }
                if (error != null) {
                    online.sendMessage(Component.text("No se pudo leer la casa. Inténtalo más tarde.", NamedTextColor.RED));
                    return;
                }
                Home home = homes.get(name.get());
                if (home == null) {
                    online.sendMessage(Component.text("No tienes ninguna casa '" + name.get() + "'.", NamedTextColor.RED));
                } else {
                    teleport(online, name.get(), home);
                }
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        return cache.suggestions(player.getUniqueId(), args[0]);
    }

    private static void teleport(Player player, String name, Home home) {
        World world = Bukkit.getWorld(home.world());
        if (world == null) {
            player.sendMessage(Component.text("El mundo de esa casa ya no existe.", NamedTextColor.RED));
            return;
        }
        Location target = new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
        player.teleportAsync(target).thenAccept(success -> player.sendMessage(success
                ? Component.text("Bienvenido a '" + name + "'.", NamedTextColor.GREEN)
                : Component.text("No se pudo completar el teletransporte.", NamedTextColor.RED)));
    }
}
