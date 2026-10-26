package site.zvolcan.fFAUtils.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.abs.FFACommand;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.TierManager;
import site.zvolcan.fFAUtils.objects.Sounds;
import site.zvolcan.fFAUtils.objects.TierProfile;
import site.zvolcan.fFAUtils.objects.TierRanking;
import site.zvolcan.fFAUtils.providers.TierProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
/**
 * {@code /tiers} — turns the tier spawn gate on and off and inspects it.
 *
 * <ul>
 * <li>{@code /tiers} — current state, providers and restricted spawns</li>
 * <li>{@code /tiers on|off|toggle} — enable or disable the gate</li>
 * <li>{@code /tiers reload} — re-read the {@code tiers} config section</li>
 * <li>{@code /tiers cache clear} — drop every cached tier</li>
 * <li>{@code /tiers check <player> [provider]} — look up a player's tier</li>
 * </ul>
 */
public final class TierCommand implements FFACommand {

    private static final String CACHE = "cache";
    private static final String CLEAR = "clear";
    private static final String CHECK = "check";
    private static final String[] SUBCOMMANDS = {"status", "on", "off", "toggle", "reload", CACHE, CHECK};

    private final FFAUtils plugin;
    private final TierManager tierManager;

    public TierCommand(FFAUtils plugin, TierManager tierManager) {
        this.plugin = plugin;
        this.tierManager = tierManager;
    }

    @Override
    public String name() {
        return "tiers";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }
        switch (args[0]) {
            case "status" -> {
                if (args.length == 1) {
                    sendStatus(sender);
                }
            }
            case "on" -> {
                if (args.length == 1) {
                    setEnabled(sender, true);
                }
            }
            case "off" -> {
                if (args.length == 1) {
                    setEnabled(sender, false);
                }
            }
            case "toggle" -> {
                if (args.length == 1) {
                    setEnabled(sender, !tierManager.isEnabled());
                }
            }
            case "reload" -> {
                if (args.length == 1) {
                    reload(sender);
                }
            }
            case "cache" -> {
                if (args.length == 2 && args[1].equals("clear")) {
                    clearCache(sender);
                }
            }
            case "check" -> {
                if (args.length == 2) {
                    check(sender, args[1], null);
                } else if (args.length == 3) {
                    check(sender, args[1], args[2]);
                }
            }
            default -> {
                // Not a subcommand of this tree.
            }
        }
        return true;
    }

    /**
     * The subcommand literals this node accepts, {@code clear} below {@code cache}, and
     * the provider argument of {@code /tiers check <player> [provider]}. The player
     * argument keeps suggesting nothing, exactly like {@code StringArgumentType.word()}.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            return FFACommand.matching(args[0], SUBCOMMANDS);
        }
        if (CACHE.equals(args[0])) {
            return args.length == 2 ? FFACommand.matching(args[1], CLEAR) : List.of();
        }
        if (!CHECK.equals(args[0]) || args.length != 3) {
            return List.of();
        }
        String prefix = args[2].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (String provider : tierManager.getProviders().keySet()) {
            if (provider.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                suggestions.add(provider);
            }
        }
        if (TierManager.PROVIDER_ANY.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            suggestions.add(TierManager.PROVIDER_ANY);
        }
        return suggestions;
    }

    private void reload(CommandSender sender) {
        tierManager.loadSettings();
        tierManager.clearCache();
        message(sender, Sounds.SUCCESS_SOUND, messages().getMessage("tiers-reloaded"));
    }

    private void clearCache(CommandSender sender) {
        int cleared = tierManager.clearCache();
        message(sender, Sounds.SUCCESS_SOUND,
                messages().getMessage("tiers-cache-cleared", "{entries}", String.valueOf(cleared)));
    }

    private int setEnabled(CommandSender sender, boolean enabled) {
        tierManager.setEnabled(enabled);
        message(sender, enabled ? Sounds.SUCCESS_SOUND : Sounds.ERROR_SOUND,
                messages().getMessage(enabled ? "tiers-enabled" : "tiers-disabled"));
        return 1;
    }

    private int sendStatus(CommandSender sender) {
        MessagesManager messages = messages();
        message(sender, Sounds.SUCCESS_SOUND, messages.getMessage(
                "tiers-status",
                "{status}", tierManager.isEnabled() ? "<green>activado</green>" : "<red>desactivado</red>",
                "{provider}", tierManager.getDefaultProvider(),
                "{gamemode}", tierManager.getDefaultGamemode()));

        for (TierProvider provider : tierManager.getProviders().values()) {
            message(sender, null, messages.getMessage(
                    "tiers-status-provider",
                    "{provider}", provider.getDisplayName(),
                    "{url}", provider.getSettings().getApiUrl(),
                    "{cache}", String.valueOf(provider.getCacheSize())));
        }

        Map<String, TierManager.TierRequirement> restricted = tierManager.getRestrictedSpawns();
        if (restricted.isEmpty()) {
            message(sender, null, messages.getMessage("tiers-status-no-spawns"));
            return 1;
        }
        for (TierManager.TierRequirement requirement : restricted.values()) {
            message(sender, null, messages.getMessage(
                    "tiers-status-spawn",
                    "{spawn}", requirement.getSpawnName(),
                    "{required}", requirement.display(),
                    "{gamemode}", TierManager.isAnyGamemode(requirement.getGamemode())
                            ? "cualquiera"
                            : requirement.getGamemode(),
                    "{provider}", requirement.getProvider()));
        }
        return 1;
    }

    /** Reports a player's tier on one provider, or on all of them. */
    private int check(CommandSender sender, String targetName, String providerId) {
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            message(sender, Sounds.ERROR_SOUND,
                    messages().getMessage("tiers-player-not-found", "{player}", targetName));
            return 1;
        }

        List<TierProvider> targets;
        if (providerId == null) {
            targets = tierManager.resolveProviders(null);
        } else if (TierManager.PROVIDER_ANY.equalsIgnoreCase(providerId)) {
            targets = List.copyOf(tierManager.getProviders().values());
        } else {
            TierProvider provider = tierManager.getProvider(providerId);
            if (provider == null) {
                message(sender, Sounds.ERROR_SOUND,
                        messages().getMessage("tiers-unknown-provider", "{provider}", providerId));
                return 1;
            }
            targets = List.of(provider);
        }

        String gamemode = tierManager.getDefaultGamemode();
        for (TierProvider provider : targets) {
            provider.fetchProfile(target.getUniqueId(), target.getName())
                    .whenComplete((profile, error) -> plugin.getServer()
                    .getScheduler().runTask(plugin, () -> {
                        if (error != null) {
                            message(sender, Sounds.ERROR_SOUND, messages().getMessage(
                                    "tiers-lookup-failed",
                                    "{player}", target.getName(),
                                    "{provider}", provider.getDisplayName()));
                            return;
                        }
                        reportProfile(sender, target.getName(), profile, gamemode, provider);
                    }));
        }
        return 1;
    }

    private void reportProfile(CommandSender sender, String targetName, TierProfile profile, String gamemode,
            TierProvider provider) {
        MessagesManager messages = messages();
        if (profile == null) {
            message(sender, Sounds.ERROR_SOUND, messages.getMessage(
                    "tiers-lookup-none", "{player}", targetName, "{provider}", provider.getDisplayName()));
            return;
        }

        boolean usePeak = tierManager.isUsePeakWhenRetired();
        // Translate the configured gamemode into this provider's own slug.
        String resolved = TierManager.isAnyGamemode(gamemode) ? gamemode : provider.resolveGamemode(gamemode);

        TierRanking ranking;
        String matchedGamemode;
        if (TierManager.isAnyGamemode(resolved)) {
            ranking = profile.getBestRanking(usePeak);
            matchedGamemode = profile.getBestGamemode(usePeak);
        } else {
            ranking = profile.getRanking(resolved);
            matchedGamemode = resolved;
        }

        if (ranking == null) {
            message(sender, Sounds.ERROR_SOUND, messages.getMessage(
                    "tiers-lookup-none", "{player}", targetName, "{provider}", provider.getDisplayName()));
            return;
        }
        message(sender, Sounds.SUCCESS_SOUND, messages.getMessage(
                "tiers-lookup",
                "{player}", targetName,
                "{provider}", provider.getDisplayName(),
                "{current}", ranking.display(usePeak),
                "{gamemode}", matchedGamemode == null ? resolved : matchedGamemode));
    }

    private void message(CommandSender sender, net.kyori.adventure.sound.Sound sound, String message) {
        if (sound == null) {
            plugin.getUtils().message(sender, false, message);
            return;
        }
        plugin.getUtils().message(sender, sound, message);
    }

    private MessagesManager messages() {
        return MessagesManager.getInstance();
    }
}
