package com.TownyDiscordChat.TownyDiscordChat.Listeners;

import com.TownyDiscordChat.TownyDiscordChat.Main;
import com.TownyDiscordChat.TownyDiscordChat.TDCManager.ItemPreview;
import com.TownyDiscordChat.TownyDiscordChat.TDCMessages;
import com.TownyDiscordChat.TownyDiscordChat.TDCPlaceholders;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Paper 1.21 chat listener: relays a town resident's message to their town Discord channel. */
public final class TDCMinecraftChatListener implements Listener {
    private static final Pattern INTERACTIVE_CHAT_TOKEN = Pattern.compile("<chat=[^:<>]+:(.*?):>");
    private final Main plugin;
    private final Map<UUID, String> channelHints = new ConcurrentHashMap<>();
    private final Map<Event, MessageRoute> routesByEvent = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<UUID, RoutedMessage> latestRoutes = new ConcurrentHashMap<>();
    private volatile boolean townyChatHookActive;

    private enum MessageRoute { GLOBAL, TOWN, NATION, IGNORE }
    private record RoutedMessage(String message, MessageRoute route, long createdAt) {}

    public TDCMinecraftChatListener(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers TownyChat's per-message hook without linking TownyChat classes
     * into this plugin. This keeps TownyChat optional and avoids class-loading
     * failures from its optional integrations.
     */
    @SuppressWarnings("unchecked")
    public boolean registerTownyChatMessageHook() {
        if (!plugin.configuration().getBoolean("townychat.Enabled", true)) return false;
        Plugin townyChat = plugin.getServer().getPluginManager().getPlugin("TownyChat");
        if (townyChat == null || !townyChat.isEnabled()) return false;
        try {
            ClassLoader loader = townyChat.getClass().getClassLoader();
            Class<? extends Event> eventClass = (Class<? extends Event>) Class
                    .forName("com.palmergames.bukkit.TownyChat.events.AsyncChatHookEvent", false, loader)
                    .asSubclass(Event.class);
            plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR,
                    (listener, event) -> handleTownyChatMessage(event), plugin, true);
            enableTownyChatHookEvents(townyChat);
            townyChatHookActive = true;
            plugin.getLogger().info("TownyChat per-message routing enabled; channel types are now authoritative.");
            return true;
        } catch (Throwable error) {
            plugin.getLogger().warning("Could not enable TownyChat per-message routing: " + error.getMessage());
            return false;
        }
    }

    private void enableTownyChatHookEvents(Plugin townyChat) throws ReflectiveOperationException {
        Object holder = townyChat.getClass().getMethod("getChannelsHandler").invoke(townyChat);
        Object value = holder.getClass().getMethod("getAllChannels").invoke(holder);
        if (!(value instanceof Map<?, ?> channels)) return;
        for (Object channel : channels.values()) {
            if (channel != null) channel.getClass().getMethod("setHooked", boolean.class).invoke(channel, true);
        }
    }

    private void handleTownyChatMessage(Event hookEvent) {
        try {
            if ((boolean) hookEvent.getClass().getMethod("isCancelled").invoke(hookEvent)) return;
            Player player = (Player) hookEvent.getClass().getMethod("getPlayer").invoke(hookEvent);
            String message = String.valueOf(hookEvent.getClass().getMethod("getMessage").invoke(hookEvent));
            Object channel = hookEvent.getClass().getMethod("getChannel").invoke(hookEvent);
            if (player == null || channel == null || message.isBlank()) return;

            String channelName = String.valueOf(channel.getClass().getMethod("getName").invoke(channel));
            String channelType = String.valueOf(channel.getClass().getMethod("getType").invoke(channel));
            MessageRoute route = routeFor(channelName, channelType);
            Object source = hookEvent.getClass().getMethod("getAsyncPlayerChatEvent").invoke(hookEvent);
            if (source instanceof Event sourceEvent) routesByEvent.put(sourceEvent, route);
            latestRoutes.put(player.getUniqueId(), new RoutedMessage(message, route, System.nanoTime()));

            String discordText = restoreInteractiveChatPlaceholders(message);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player online = plugin.getServer().getPlayer(player.getUniqueId());
                if (online == null) return;
                if (route == MessageRoute.TOWN) {
                    if (!plugin.manager().relayMinecraftMessageThroughDiscordSRV(online, discordText)) {
                        plugin.manager().relayMinecraftMessage(online.getUniqueId(), online.getName(), discordText);
                    }
                } else if (route == MessageRoute.NATION) {
                    if (!plugin.manager().relayNationMinecraftMessageThroughDiscordSRV(online, discordText)) {
                        plugin.manager().relayNationMinecraftMessage(online, online.getName(), discordText);
                    }
                } else if (isConfiguredChannel(channelName, "townychat.LocalNoNearbyPlayersWarning.ChannelNames")) {
                    warnLocalChatIfAlone(online);
                }
            });
        } catch (Throwable error) {
            plugin.getLogger().warning("Could not route a TownyChat message: " + error.getMessage());
        }
    }

    private MessageRoute routeFor(String channelName, String channelType) {
        if (channelType.equalsIgnoreCase("TOWN")) return MessageRoute.TOWN;
        if (channelType.equalsIgnoreCase("NATION")) return MessageRoute.NATION;
        if (channelType.equalsIgnoreCase("GLOBAL")
                && isConfiguredChannel(channelName, "townychat.GlobalChannelNames")) return MessageRoute.GLOBAL;
        return MessageRoute.IGNORE;
    }

    /** True only for the exact TownyChat message that DiscordSRV must not copy globally. */
    public boolean shouldSuppressGlobalCopy(Event triggeringEvent, Player player, String message) {
        if (!townyChatHookActive || player == null) return false;
        MessageRoute route = triggeringEvent == null ? null : routesByEvent.get(triggeringEvent);
        if (route == null) {
            RoutedMessage latest = latestRoutes.get(player.getUniqueId());
            long maximumAge = java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            if (latest != null && System.nanoTime() - latest.createdAt() <= maximumAge
                    && latest.message().equals(message)) route = latest.route();
        }
        return route != null && route != MessageRoute.GLOBAL;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (townyChatHookActive) return;
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (text.isBlank()) return;
        java.util.UUID playerId = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();
        List<ItemPreview> items = interactiveChatItems(event.message());
        // Towny is not thread-safe: only read its model back on the server thread.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) return;
            String channel = currentTownyChatChannel(player);
            if (isConfiguredChannel(channel, "townychat.TownChannelNames")) {
                Set<UUID> recipients = new HashSet<>();
                for (Audience viewer : event.viewers()) {
                    if (viewer instanceof Player recipient) recipients.add(recipient.getUniqueId());
                }
                // A stale channel hint must never route a global message into
                // the town channel. TownyChat's actual viewer set is authoritative.
                if (!recipients.isEmpty() && !plugin.manager().isTownChatAudience(playerId, recipients)) return;
                String discordText = restoreInteractiveChatPlaceholders(text);
                boolean delegated = plugin.manager().relayMinecraftMessageThroughDiscordSRV(player, discordText);
                if (!delegated) {
                    plugin.manager().relayMinecraftMessage(playerId, playerName, discordText);
                    plugin.manager().relayInteractiveChatItems(playerId, playerName, items);
                }
            } else if (isConfiguredChannel(channel, "townychat.NationChannelNames")) {
                String discordText = restoreInteractiveChatPlaceholders(text);
                if (!plugin.manager().relayNationMinecraftMessageThroughDiscordSRV(player, discordText)) {
                    plugin.manager().relayNationMinecraftMessage(player, playerName, discordText);
                }
            } else if (isConfiguredChannel(channel, "townychat.LocalNoNearbyPlayersWarning.ChannelNames")) {
                warnLocalChatIfAlone(player);
            }
        });
    }

    /** Reads TownyChat's PlaceholderAPI channel value, with command tracking as a no-hook fallback. */
    private String currentTownyChatChannel(Player player) {
        if (!plugin.configuration().getBoolean("townychat.Enabled", true)) return "";
        // TownyChat 0.119 exposes the authoritative channel through
        // Chat#getPlayerChannel(Player). Resolve it reflectively so TownyChat
        // remains optional and no hard class dependency is introduced.
        String apiChannel = townyChatApiChannel(player);
        if (!apiChannel.isBlank()) {
            channelHints.put(player.getUniqueId(), apiChannel);
            return apiChannel;
        }
        String placeholder = plugin.configuration().getString("townychat.ChannelPlaceholder", "%townychat_channel_name%");
        String resolved = TDCPlaceholders.resolve(plugin, player, placeholder);
        if (resolved != null && !resolved.isBlank() && !resolved.equals(placeholder) && !resolved.contains("%")) {
            String channel = TDCMessages.strip(resolved).trim();
            if (!channel.isBlank()) {
                channelHints.put(player.getUniqueId(), channel);
                return channel;
            }
        }
        // Keep the command-based fallback for TownyChat versions without the
        // PlaceholderAPI expansion. Global aliases are invalidated below by
        // isGlobalChatCommand(), preventing the old town hint from leaking.
        return channelHints.getOrDefault(player.getUniqueId(), "");
    }

    private String townyChatApiChannel(Player player) {
        try {
            Class<?> chatClass = Class.forName("com.palmergames.bukkit.TownyChat.Chat");
            Object chat = chatClass.getMethod("getTownyChat").invoke(null);
            if (chat == null) return "";
            Object channel = chatClass.getMethod("getPlayerChannel", Player.class).invoke(chat, player);
            if (channel == null) return "";
            Object name = channel.getClass().getMethod("getName").invoke(channel);
            return name == null ? "" : TDCMessages.strip(String.valueOf(name)).trim();
        } catch (Throwable ignored) {
            // TownyChat is optional; fall back to PlaceholderAPI/commands.
            return "";
        }
    }

    /** Used by the DiscordSRV listener to suppress its second, global copy of town chat. */
    public boolean isCurrentTownChannel(Player player) {
        return player != null && isConfiguredChannel(currentTownyChatChannel(player), "townychat.TownChannelNames");
    }

    /** InteractiveChat stores placeholders in chat components as <chat=uuid:[item]:>. */
    private String restoreInteractiveChatPlaceholders(String text) {
        Matcher matcher = INTERACTIVE_CHAT_TOKEN.matcher(text);
        StringBuffer restored = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(restored, Matcher.quoteReplacement(matcher.group(1)));
        }
        matcher.appendTail(restored);
        return restored.toString();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onTownyChatCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.configuration().getBoolean("townychat.Enabled", true)
                || !plugin.configuration().getBoolean("townychat.CommandListener.Enabled", true)) return;
        String raw = event.getMessage().trim();
        if (raw.length() < 2) return;
        String command = raw.substring(1).split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        int namespace = command.indexOf(':');
        if (namespace >= 0) command = command.substring(namespace + 1);
        UUID playerId = event.getPlayer().getUniqueId();
        String[] parts = raw.substring(1).split("\\s+");
        // TownyChat installations commonly expose global as /g, /gc, /global
        // or as an argument to /ch, /chat and /channel.  Handle these aliases
        // independently of the configurable list so a global switch always
        // invalidates a previous town hint.
        if (isGlobalChatCommand(command, parts)) {
            channelHints.remove(playerId);
            return;
        }
        if (matchesCommand(command, "townychat.CommandListener.TownCommands")
                || channelArgumentMatches(parts, "townychat.TownChannelNames")) {
            channelHints.put(playerId, configuredPrimaryName("townychat.TownChannelNames", "town"));
        } else if (matchesCommand(command, "townychat.CommandListener.NationCommands")
                || channelArgumentMatches(parts, "townychat.NationChannelNames")) {
            channelHints.put(playerId, configuredPrimaryName("townychat.NationChannelNames", "nation"));
        } else if (matchesCommand(command, "townychat.CommandListener.LocalCommands")
                || channelArgumentMatches(parts, "townychat.LocalNoNearbyPlayersWarning.ChannelNames")) {
            channelHints.put(playerId, configuredPrimaryName("townychat.LocalNoNearbyPlayersWarning.ChannelNames", "local"));
        } else if (matchesCommand(command, "townychat.CommandListener.OtherCommands")
                || channelArgumentMatches(parts, "townychat.CommandListener.OtherCommands")) {
            channelHints.remove(playerId);
        }
    }

    private boolean isGlobalChatCommand(String command, String[] parts) {
        if (command.equals("g") || command.equals("gc") || command.equals("global")
                || command.equals("globalchat") || command.equals("globalchannel")) return true;
        if (!(command.equals("ch") || command.equals("channel") || command.equals("chat")
                || command.equals("townychat"))) return false;
        if (parts.length < 2) return false;
        String argument = parts[1].toLowerCase(java.util.Locale.ROOT);
        return argument.equals("g") || argument.equals("gc") || argument.equals("global")
                || argument.equals("globalchat") || argument.equals("globalchannel");
    }

    /** Supports channel-switch commands such as /ch town, /chat global and /channel local. */
    private boolean channelArgumentMatches(String[] parts, String configPath) {
        if (parts.length < 2) return false;
        String command = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (!(command.equals("ch") || command.equals("channel") || command.equals("chat") || command.equals("townychat"))) return false;
        String argument = parts[1].toLowerCase(java.util.Locale.ROOT);
        return plugin.configuration().getStringList(configPath).stream().anyMatch(value -> value.equalsIgnoreCase(argument));
    }

    private boolean matchesCommand(String command, String configPath) {
        return plugin.configuration().getStringList(configPath).stream().anyMatch(value -> value.equalsIgnoreCase(command));
    }

    private boolean isConfiguredChannel(String channel, String configPath) {
        return !channel.isBlank() && plugin.configuration().getStringList(configPath).stream()
                .anyMatch(value -> value.equalsIgnoreCase(channel));
    }

    private String configuredPrimaryName(String configPath, String fallback) {
        List<String> names = plugin.configuration().getStringList(configPath);
        return names.isEmpty() ? fallback : names.getFirst();
    }

    private void warnLocalChatIfAlone(Player player) {
        if (!plugin.configuration().getBoolean("townychat.LocalNoNearbyPlayersWarning.Enabled", true)) return;
        double radius = Math.max(1D, plugin.configuration().getDouble("townychat.LocalNoNearbyPlayersWarning.Radius", 100D));
        Location origin = player.getLocation();
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other.equals(player) || !other.getWorld().equals(origin.getWorld())) continue;
            if (other.getLocation().distanceSquared(origin) < radius * radius) return;
        }
        String message = plugin.configuration().getString("townychat.LocalNoNearbyPlayersWarning.Message", "");
        player.sendMessage(TDCMessages.colour(TDCPlaceholders.resolve(plugin, player, message)));
        String title = plugin.configuration().getString("townychat.LocalNoNearbyPlayersWarning.Title", "");
        String subtitle = plugin.configuration().getString("townychat.LocalNoNearbyPlayersWarning.Subtitle", "");
        String resolvedTitle = TDCMessages.colour(TDCPlaceholders.resolve(plugin, player, title));
        String resolvedSubtitle = TDCMessages.colour(TDCPlaceholders.resolve(plugin, player, subtitle));
        int fadeIn = Math.max(0, plugin.configuration().getInt("townychat.LocalNoNearbyPlayersWarning.TitleFadeIn", 10));
        int stay = Math.max(0, plugin.configuration().getInt("townychat.LocalNoNearbyPlayersWarning.TitleStay", 50));
        int fadeOut = Math.max(0, plugin.configuration().getInt("townychat.LocalNoNearbyPlayersWarning.TitleFadeOut", 20));
        if (!resolvedTitle.isBlank() || !resolvedSubtitle.isBlank()) {
            player.sendTitle(resolvedTitle, resolvedSubtitle, fadeIn, stay, fadeOut);
        }
        if (plugin.configuration().getBoolean("townychat.LocalNoNearbyPlayersWarning.SoundEnabled", true)) {
            String soundName = plugin.configuration().getString("townychat.LocalNoNearbyPlayersWarning.Sound", "BLOCK_NOTE_BLOCK_BELL");
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase(java.util.Locale.ROOT));
                float volume = (float) Math.max(0D, plugin.configuration().getDouble("townychat.LocalNoNearbyPlayersWarning.SoundVolume", 1D));
                float pitch = (float) Math.max(0D, plugin.configuration().getDouble("townychat.LocalNoNearbyPlayersWarning.SoundPitch", 1D));
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Suono non valido per l'avviso chat locale: " + soundName);
            }
        }
    }

    private List<ItemPreview> interactiveChatItems(Component message) {
        if (!plugin.configuration().getBoolean("interactivechat.Enabled", true)
                || !plugin.getServer().getPluginManager().isPluginEnabled("InteractiveChat")) {
            return List.of();
        }
        int maxItems = Math.max(1, Math.min(10, plugin.configuration().getInt("interactivechat.MaxItemsPerMessage", 3)));
        List<ItemPreview> items = new ArrayList<>();
        collectItemHovers(message, items, new HashSet<>(), maxItems);
        return items;
    }

    private void collectItemHovers(Component component, List<ItemPreview> items, Set<String> seen, int maximum) {
        if (items.size() >= maximum) return;
        HoverEvent<?> hover = component.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_ITEM && hover.value() instanceof HoverEvent.ShowItem showItem) {
            String key = showItem.item().asString();
            String displayName = PlainTextComponentSerializer.plainText().serialize(component).trim();
            if (displayName.isBlank()) displayName = prettifyItemName(key);
            String identity = key + ":" + showItem.count() + ":" + displayName;
            if (seen.add(identity)) items.add(new ItemPreview(key, Math.max(1, showItem.count()), displayName));
        }
        for (Component child : component.children()) {
            collectItemHovers(child, items, seen, maximum);
            if (items.size() >= maximum) return;
        }
    }

    private String prettifyItemName(String key) {
        String value = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        String[] words = value.replace('_', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
