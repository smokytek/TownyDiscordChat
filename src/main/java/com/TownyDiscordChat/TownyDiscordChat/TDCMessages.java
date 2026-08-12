package com.TownyDiscordChat.TownyDiscordChat;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import java.util.Map;

public final class TDCMessages {
    private TDCMessages() {
    }

    public static String prefix(Main plugin) {
        return colour(plugin.configuration().getString("messages.Prefix", "&8[&2TDC&8]"));
    }

    public static String prefix(Main plugin, OfflinePlayer context) {
        return colour(TDCPlaceholders.resolve(plugin, context, plugin.configuration().getString("messages.Prefix", "&8[&2TDC&8]")));
    }

    public static void send(CommandSender sender, Main plugin, String message) {
        OfflinePlayer context = sender instanceof Player player ? player : null;
        sender.sendMessage(prefix(plugin, context) + " " + colour(TDCPlaceholders.resolve(plugin, context, message)));
    }

    public static void send(Player player, Main plugin, String message) {
        send((CommandSender) player, plugin, message);
    }

    public static String tr(Main plugin, String key, Map<String, ?> values) {
        return plugin.locales().get(key, values);
    }

    public static String tr(Main plugin, String key) {
        return tr(plugin, key, Map.of());
    }

    public static void sendKey(CommandSender sender, Main plugin, String key, Map<String, ?> values) {
        send(sender, plugin, tr(plugin, key, values));
    }

    public static String colour(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    public static String strip(String value) {
        return ChatColor.stripColor(colour(value));
    }
}
