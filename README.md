# TownyDiscordChat 3.1.0
(English version below)

Plugin Paper/Purpur 1.21+ che sincronizza Towny e DiscordSRV. Richiede Java 21, Towny 0.103.0.0+ e DiscordSRV 1.30.4+.

## Funzioni

- Crea e mantiene i ruoli `town-<citta>` e `nation-<nazione>`.
- Crea canali testuali e vocali privati per town e nation.
- Riconcilia gli account Discord collegati: rimuove i ruoli Towny non più corretti e assegna solo quelli della town/nation corrente.
- Collega la chat della town: Minecraft -> canale Discord della town e canale Discord della town -> residenti Minecraft online.
- Blocca il bridge Discord per utenti non collegati o appartenenti a una town diversa.
- Pubblica ingressi/uscite, cambi di sindaco, movimenti bancari e riepiloghi NewDay nel canale principale della citta, senza creare canali staff separati.
- Al NewDay annuncia nel canale globale DiscordSRV bancarotte, rovine e cadute con embed e dieci cronache configurabili; i residenti NPC sono esclusi.
- Invia i movimenti bancari come embed configurabili in `messages.BankEmbed`, con i placeholder `%town%`, `%type%`, `%amount%`, `%balance%`, `%actor%` (anche `%player%`), `%reason%`, `%residents%` e `%tax%`.
- Se PlaceholderAPI è installato, i suoi placeholder esterni possono essere usati nei formati chat e nei testi dell'embed; sono risolti nel contesto del giocatore che ha generato l'operazione.
- Registra il comando slash `/town`: `info`, `sync`, `map`, `notice` per sindaco/vice e `resync` per gli amministratori Discord.

## Configurazione necessaria

1. Installa Towny e DiscordSRV aggiornati e configura DiscordSRV con un **Main Guild**.
2. Al bot Discord assegna almeno: `Manage Roles`, `Manage Channels`, `View Channels`, `Send Messages` e `Read Message History`. Il ruolo del bot deve stare sopra ai ruoli creati dal plugin.
3. In `plugins/TownyDiscordChat/config.yml`, imposta gli ID delle categorie Discord desiderate:

```yml
town:
  TextCategoryId: "123456789012345678"
  VoiceCategoryId: "123456789012345679"
```

`0` significa “nessuna categoria” per i canali standard.

4. Abilita il **Message Content Intent** per il bot nel portale sviluppatori Discord: è necessario per ricevere i messaggi Discord del bridge.
5. Avvia il server e usa `/tdc sync` come amministratore per creare/riparare tutte le risorse.

## Comandi

- `/tdc check role` — sincronizza i ruoli del giocatore.
- `/tdc check role alllinked` — sincronizza tutti gli account collegati.
- `/tdc check role createalltownsandnations` — crea/verifica ruoli e risorse.
- `/tdc check textchannel alltownsandnations` — verifica le risorse testuali.
- `/tdc check voicechannel alltownsandnations` — verifica le risorse vocali.
- `/tdc sync` — sincronizzazione completa (admin).
- `/tdc channels delete <città|all>` — elimina solo i canali Discord della città indicata o di tutte le città; disponibile ad admin e console, mantiene i ruoli.
- `/tdc channels restore <città|all>` — riabilita e ricrea i canali eliminati intenzionalmente.

## Comandi Discord

- `/town info` — riepilogo privato della propria città.
- `/town sync` — riallinea i propri ruoli Discord.
- `/town map` — genera la vista Dynmap dall'alto della propria città.
- `/town notice messaggio:<testo>` — invia un avviso nel canale cittadino (sindaco/vice).
- `/town resync` — riallinea tutto il plugin (permesso Discord `Administrator`).

## Build

```bash
mvn clean package
```

L'artefatto viene generato in `target/TownyDiscordChat-3.1.0-1.21+.jar`.

## Autori e collaboratori

- thejames10
- Hugo5000
- Smokytek

## Ispirazione

Il progetto nasce come evoluzione e re-implementazione ispirata a [TownyDiscordChat su SpigotMC](https://www.spigotmc.org/resources/townydiscordchat.91026/). Il codice e le funzionalita presenti in questa repository sono stati adattati ed estesi per Paper/Purpur 1.21 e successive versioni 1.21.x.

## Dipendenze

Dipendenze obbligatorie dichiarate in `plugin.yml`:

- [Towny](https://github.com/TownyAdvanced/Towny)
- [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV)

Soft-dependencies opzionali:

- [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI)
- [Dynmap](https://github.com/webbukkit/dynmap)
- [InteractiveChat](https://github.com/LOOHP/InteractiveChat)
- [InteractiveChat DiscordSRV Addon](https://modrinth.com/plugin/interactivechat-discordsrv-addon)
- TownyChat

---

# TownyDiscordChat 3.1.0 — English

TownyDiscordChat bridges Towny and DiscordSRV on Paper/Purpur 1.21 and later 1.21.x releases. It requires Java 21 and creates town/nation roles and channels, provides a town-only two-way chat bridge, synchronizes linked accounts, and publishes configurable Towny notifications.

## Requirements

Required dependencies:

- [Towny](https://github.com/TownyAdvanced/Towny)
- [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV)

Optional soft-dependencies:

- [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) for external placeholders.
- [Dynmap](https://github.com/webbukkit/dynmap) for the `/town map` command.
- [InteractiveChat](https://github.com/LOOHP/InteractiveChat) and [InteractiveChat DiscordSRV Addon](https://modrinth.com/plugin/interactivechat-discordsrv-addon) for item previews.
- TownyChat for channel detection through placeholders and command fallbacks.

## Main features

- Town and nation Discord roles and channels.
- Town-only Minecraft ↔ Discord bridge with linked-account and wrong-town checks.
- Configurable bank, jail, outlaw, resident, rank and tax notifications.
- NewDay town summaries with Taxes, Residents and Outposts buttons.
- Global DiscordSRV announcements for bankrupt, ruined or fallen towns.
- PlaceholderAPI support in configurable text and embed fields.
- Dynmap map command and InteractiveChat item integration.

## Inspiration

This project was inspired by [TownyDiscordChat on SpigotMC](https://www.spigotmc.org/resources/townydiscordchat.91026/). This repository contains an adapted and extended implementation targeting Paper/Purpur 1.21 and later 1.21.x releases.

## Build

```bash
mvn clean package
```

The generated artifact is `target/TownyDiscordChat-3.1.0-1.21+.jar`.
