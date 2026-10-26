package site.zvolcan.fFAUtils.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.FFAPlaceholders;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.abs.FFACommand;
import site.zvolcan.fFAUtils.managers.DeathEventManager;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.objects.Sounds;
import site.zvolcan.fFAUtils.util.PluginUtils;

import java.util.List;
import java.util.logging.Logger;

public final class MainCommand implements FFACommand {

    private static final String[] SUBCOMMANDS = {"reload"};

    private final PluginUtils utils;
    private final FFAPlaceholders ffaPlaceholders;
    private final MessagesManager messagesManager;
    private final KitManager kitManager;
    private final SpawnManager spawnManager;
    private final DeathEventManager deathEventManager;

    public MainCommand(PluginUtils utils, FFAPlaceholders ffaPlaceholders, MessagesManager messagesManager,
            KitManager kitManager, SpawnManager spawnManager,
            DeathEventManager deathEventManager) {
        this.utils = utils;
        this.ffaPlaceholders = ffaPlaceholders;
        this.messagesManager = messagesManager;
        this.kitManager = kitManager;
        this.spawnManager = spawnManager;
        this.deathEventManager = deathEventManager;
    }

    @Override
    public String name() {
        return "ffautils";
    }

    /** {@code /ffautils reload}: the only subcommand of the tree. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // The command tree required a Player sender, so console never reached this body.
        if (!(sender instanceof Player)) {
            return true;
        }
        if (args.length != 1 || !args[0].equals("reload")) {
            return true;
        }

        final Logger logger = FFAUtils.getInstance().getLogger();
        logger.info("Reloading Plugin...");

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            ffaPlaceholders.register();
            logger.info("Loading Placeholders.");
        }
        messagesManager.registerMessages();
        logger.info("Loading Messages.");
        kitManager.loadAllKits();
        logger.info("Loading Kits.");
        spawnManager.loadAllSpawns();
        logger.info("Loading Spawns.");
        deathEventManager.registerDeathMessages();
        logger.info("Loading Death Messages.");

        utils.message(
                sender,
                Sounds.SUCCESS_SOUND,
                messagesManager.getMessage("reload-success"));
        return true;
    }

    /** Only the {@code reload} literal; it was the single child of this node. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }
        return FFACommand.atFirstToken(args, SUBCOMMANDS);
    }
}
