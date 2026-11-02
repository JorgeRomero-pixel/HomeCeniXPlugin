package com.jorge.homecenix;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

final class SetHomeCommand implements TabExecutor {

    private final HomeCeniXPlugin plugin;
    private final HomeCache cache;

    SetHomeCommand(HomeCeniXPlugin plugin, HomeCache cache) {
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

        Location location = player.getLocation();
        Home home = new Home(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
        UUID owner = player.getUniqueId();

        cache.save(owner, name.get(), home).whenComplete((result, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "No se pudo guardar una casa de " + owner, error);
            }
            plugin.runOnServerThread(() -> {
                Player online = Bukkit.getPlayer(owner);
                if (online == null) {
                    return;
                }
                if (error != null) {
                    online.sendMessage(Component.text("No se pudo guardar la casa. Inténtalo más tarde.", NamedTextColor.RED));
                } else if (result == HomeCache.SaveResult.LIMIT_REACHED) {
                    online.sendMessage(Component.text("Has alcanzado el límite de " + HomeCache.MAX_HOMES
                            + " casas. Solo puedes reemplazar una existente usando su nombre.", NamedTextColor.RED));
                } else {
                    online.sendMessage(Component.text("Casa '" + name.get() + "' guardada.", NamedTextColor.GREEN));
                }
            });
        });
        return true;
    }

    /** The name is free text, so this replaces the default suggestion of online player names. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
