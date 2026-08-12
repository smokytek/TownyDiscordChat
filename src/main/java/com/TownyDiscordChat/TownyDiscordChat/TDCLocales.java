package com.TownyDiscordChat.TownyDiscordChat;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

/** Loads the bundled translations and falls back to English for missing keys. */
public final class TDCLocales {
    private final Main plugin;
    private YamlConfiguration selected;
    private YamlConfiguration english;

    public TDCLocales(Main plugin) {
        this.plugin = plugin;
        for (String language : new String[]{"en", "it", "fr", "es", "pl"}) {
            File file = new File(plugin.getDataFolder(), "locales/" + language + ".yml");
            if (!file.exists()) plugin.saveResource("locales/" + language + ".yml", false);
        }
        reload();
    }

    public void reload() {
        File base = new File(plugin.getDataFolder(), "locales");
        english = YamlConfiguration.loadConfiguration(new File(base, "en.yml"));
        String language = plugin.getConfig().getString("language", "en").toLowerCase().replace('_', '-');
        if (language.startsWith("it")) language = "it";
        else if (language.startsWith("fr")) language = "fr";
        else if (language.startsWith("es")) language = "es";
        else if (language.startsWith("pl")) language = "pl";
        else language = "en";
        selected = YamlConfiguration.loadConfiguration(new File(base, language + ".yml"));
    }

    public String get(String key, Map<String, ?> values) {
        String value = selected.getString(key, english.getString(key, key));
        if (values != null) for (Map.Entry<String, ?> entry : values.entrySet()) {
            String replacement = String.valueOf(entry.getValue());
            value = value.replace("%" + entry.getKey() + "%", replacement)
                    .replace("{" + entry.getKey() + "}", replacement);
        }
        return value;
    }
}
