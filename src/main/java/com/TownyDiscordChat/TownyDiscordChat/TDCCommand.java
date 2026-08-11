package com.TownyDiscordChat.TownyDiscordChat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class TDCCommand implements CommandExecutor {
    private final Main plugin;

    public TDCCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            TDCMessages.send(sender, plugin, "&a/TDC check role &7- sincronizza i tuoi ruoli.");
            TDCMessages.send(sender, plugin, "&a/TDC sync &7- sincronizza ruoli e canali (admin).");
            TDCMessages.send(sender, plugin, "&a/TDC reload &7- ricarica la configurazione (admin).");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            boolean console = !(sender instanceof Player);
            if (!console && !sender.hasPermission("TownyDiscordChat.Admin")) {
                TDCMessages.send(sender, plugin, plugin.configuration().getString("messages.Commands.NoPermission"));
                return true;
            }
            plugin.reloadConfig();
            plugin.getConfig().options().copyDefaults(true);
            plugin.saveConfig();
            plugin.manager().synchroniseAllResources();
            plugin.manager().synchroniseAllLinkedAccounts();
            TDCMessages.send(sender, plugin, "&aConfigurazione ricaricata e risorse sincronizzate.");
            return true;
        }

        if (args[0].equalsIgnoreCase("sync")) {
            if (!sender.hasPermission("TownyDiscordChat.Sync")) {
                TDCMessages.send(sender, plugin, plugin.configuration().getString("messages.Commands.NoPermission"));
                return true;
            }
            plugin.manager().synchroniseAllResources();
            plugin.manager().synchroniseAllLinkedAccounts();
            TDCMessages.send(sender, plugin, "&7Sincronizzazione avviata.");
            return true;
        }

        if (args.length >= 3 && (args[0].equalsIgnoreCase("channels") || args[0].equalsIgnoreCase("channel"))
                && args[1].equalsIgnoreCase("delete")) {
            boolean console = !(sender instanceof Player);
            if (!console && !sender.hasPermission("TownyDiscordChat.Admin")) {
                TDCMessages.send(sender, plugin, plugin.configuration().getString("messages.Commands.NoPermission"));
                return true;
            }
            String target = args[2];
            if (target.equalsIgnoreCase("all")) {
                int count = plugin.manager().deleteAllTownChannels();
                TDCMessages.send(sender, plugin, "&aEliminazione avviata per i canali di " + count + " città. I ruoli sono stati mantenuti.");
                return true;
            }
            if (!plugin.manager().deleteTownChannels(target)) {
                TDCMessages.send(sender, plugin, "&cCittà non trovata o Discord non disponibile: &f" + target);
                return true;
            }
            TDCMessages.send(sender, plugin, "&aEliminazione dei canali di &f" + target + " &aavviata. Il ruolo è stato mantenuto.");
            return true;
        }

        if (args.length >= 3 && (args[0].equalsIgnoreCase("channels") || args[0].equalsIgnoreCase("channel"))
                && args[1].equalsIgnoreCase("restore")) {
            boolean console = !(sender instanceof Player);
            if (!console && !sender.hasPermission("TownyDiscordChat.Admin")) {
                TDCMessages.send(sender, plugin, plugin.configuration().getString("messages.Commands.NoPermission"));
                return true;
            }
            String target = args[2];
            if (target.equalsIgnoreCase("all")) {
                int count = plugin.manager().restoreAllTownChannels();
                TDCMessages.send(sender, plugin, "&aRipristino avviato per i canali di " + count + " città.");
                return true;
            }
            if (!plugin.manager().restoreTownChannels(target)) {
                TDCMessages.send(sender, plugin, "&cCittà non trovata: &f" + target);
                return true;
            }
            TDCMessages.send(sender, plugin, "&aRipristino dei canali di &f" + target + " &aavviato.");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("check") && args[1].equalsIgnoreCase("role")) {
            if (args.length >= 3 && args[2].equalsIgnoreCase("alllinked")) {
                return runAdmin(sender, "TownyDiscordChat.Check.Role.AllLinked", plugin.manager()::synchroniseAllLinkedAccounts);
            }
            if (args.length >= 3 && args[2].equalsIgnoreCase("createalltownsandnations")) {
                return runAdmin(sender, "TownyDiscordChat.Check.Role.CreateAllTownsAndNations", plugin.manager()::synchroniseAllResources);
            }
            if (!(sender instanceof Player player)) {
                TDCMessages.send(sender, plugin, "&cQuesto controllo richiede un giocatore collegato.");
                return true;
            }
            if (!sender.hasPermission("TownyDiscordChat.Check.Role")) {
                TDCMessages.send(sender, plugin, plugin.configuration().getString("messages.Commands.NoPermission"));
                return true;
            }
            if (!plugin.manager().isLinked(player.getUniqueId())) {
                TDCMessages.send(player, plugin, "&cCollega prima Discord con &f/discord link&c.");
                return true;
            }
            plugin.manager().synchronisePlayer(player.getUniqueId());
            TDCMessages.send(player, plugin, "&7Verifica dei ruoli avviata.");
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("check") && args[1].equalsIgnoreCase("textchannel")
                && args[2].equalsIgnoreCase("alltownsandnations")) {
            return runAdmin(sender, "TownyDiscordChat.Check.TextChannel.AllTownsAndNations", plugin.manager()::synchroniseAllResources);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("check") && args[1].equalsIgnoreCase("voicechannel")
                && args[2].equalsIgnoreCase("alltownsandnations")) {
            return runAdmin(sender, "TownyDiscordChat.Check.VoiceChannel.AllTownsAndNations", plugin.manager()::synchroniseAllResources);
        }

        TDCMessages.send(sender, plugin, "&cSintassi non valida. Usa &f/tdc&c per l'aiuto.");
        return true;
    }

    private boolean runAdmin(CommandSender sender, String permission, Runnable action) {
        if (!sender.hasPermission(permission) && !sender.hasPermission("TownyDiscordChat.Admin")) {
            TDCMessages.send(sender, plugin, plugin.configuration().getString("messages.Commands.NoPermission"));
            return true;
        }
        action.run();
        TDCMessages.send(sender, plugin, "&7Sincronizzazione avviata.");
        return true;
    }
}
