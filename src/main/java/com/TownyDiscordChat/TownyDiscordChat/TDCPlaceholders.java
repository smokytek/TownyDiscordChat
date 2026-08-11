package com.TownyDiscordChat.TownyDiscordChat;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/** Optional bridge to PlaceholderAPI: no hard dependency is required. */
public final class TDCPlaceholders {
    private static Method setPlaceholders;
    private static boolean lookupAttempted;

    private TDCPlaceholders() {
    }

    public static String resolve(Main plugin, OfflinePlayer player, String text) {
        if (text == null || player == null || !plugin.configuration().getBoolean("placeholderapi.Enabled", true)) return text == null ? "" : text;
        Plugin placeholderApi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null || !placeholderApi.isEnabled()) return text;
        try {
            if (!lookupAttempted) {
                lookupAttempted = true;
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                setPlaceholders = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            }
            Object resolved = setPlaceholders == null ? null : setPlaceholders.invoke(null, player, text);
            return resolved instanceof String value ? value : text;
        } catch (ReflectiveOperationException error) {
            plugin.getLogger().warning("PlaceholderAPI could not resolve placeholders: " + error.getMessage());
            return text;
        }
    }
}
