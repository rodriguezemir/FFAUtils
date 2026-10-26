package site.zvolcan.fFAUtils.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.commands.abs.FFACommand;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.objects.Sounds;
import site.zvolcan.fFAUtils.util.PluginUtils;

import java.util.List;

public final class SetSpawnCommand implements FFACommand {

    private static final String[] SUBCOMMANDS = {"create", "allowkit", "removekit", "delete"};

    private final SpawnManager spawnManager;
    private final KitManager kitManager;
    private final PluginUtils utils;

    public SetSpawnCommand(SpawnManager spawnManager, KitManager kitManager, PluginUtils utils) {
        this.spawnManager = spawnManager;
        this.kitManager = kitManager;
        this.utils = utils;
    }

    @Override
    public String name() {
        return "setspawn";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            return true;
        }
        switch (args[0]) {
            case "create" -> {
                if (args.length == 2) {
                    create(sender, args[1]);
                }
            }
            case "allowkit" -> {
                if (args.length == 3) {
                    allowKit(sender, args[1], args[2]);
                }
            }
            case "removekit" -> {
                if (args.length == 3) {
                    removeKit(sender, args[1], args[2]);
                }
            }
            case "delete" -> {
                if (args.length == 2) {
                    delete(sender, args[1]);
                }
            }
            default -> {
                // Not a subcommand of this tree.
            }
        }
        return true;
    }

    /** The subcommand literals; spawn and kit arguments had no {@code suggests}. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return FFACommand.atFirstToken(args, SUBCOMMANDS);
    }

    /** {@code /setspawn create <name>} — creates a spawn at player's location. */
    private void create(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance()
                    .getMessage("only-players-execute"));
            return;
        }
        String spawnName = name.toLowerCase();
        spawnManager.saveSpawn(spawnName, player.getLocation());
        utils.message(player,
                Sounds.SUCCESS_SOUND,
                MessagesManager.getInstance().getMessage(
                        "save-spawn", "{spawn}", spawnName
                )
        );
    }

    /** {@code /setspawn allowkit <spawn> <kit>} — adds kit to spawn's allowed list. */
    private void allowKit(CommandSender sender, String spawn, String kit) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance()
                    .getMessage("only-players-execute"));
            return;
        }
        String spawnName = spawn.toLowerCase();
        String kitName = kit.toLowerCase();

        if (!spawnManager.spawnExists(spawnName)) {
            utils.message(player, Sounds.ERROR_SOUND,
                    MessagesManager.getInstance().getMessage(
                            "spawn-not-found", "{spawn}", spawnName
                    ));
            return;
        }
        if (kitManager.getKit(kitName) == null) {
            utils.message(player, Sounds.ERROR_SOUND,
                    MessagesManager.getInstance().getMessage(
                            "kit-not-found", "{name}", kitName
                    )
            );
            return;
        }

        spawnManager.addAllowedKit(spawnName, kitName);
        utils.message(player, Sounds.SUCCESS_SOUND,
                MessagesManager.getInstance().getMessage(
                        "kit-added-to-spawn", "{kit}", kitName, "{spawn}", spawnName
                ));
    }

    /** {@code /setspawn removekit <spawn> <kit>} — removes kit from spawn's allowed list. */
    private void removeKit(CommandSender sender, String spawn, String kit) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance()
                    .getMessage("only-players-execute"));
            return;
        }
        String spawnName = spawn.toLowerCase();
        String kitName = kit.toLowerCase();

        if (!spawnManager.spawnExists(spawnName)) {
            utils.message(player, Sounds.ERROR_SOUND,
                    MessagesManager.getInstance().getMessage(
                            "spawn-not-found", "{spawn}", spawnName
                    ));
            return;
        }

        spawnManager.removeAllowedKit(spawnName, kitName);
        utils.message(player, Sounds.SUCCESS_SOUND,
                MessagesManager.getInstance().getMessage(
                        "kit-removed-from-spawn", "{kit}", kitName, "{spawn}", spawnName
                ));
    }

    /** {@code /setspawn delete <spawn>} — deletes a spawn. */
    private void delete(CommandSender sender, String spawn) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance()
                    .getMessage("only-players-execute"));
            return;
        }
        String spawnName = spawn.toLowerCase();

        if (!spawnManager.spawnExists(spawnName)) {
            utils.message(player, Sounds.ERROR_SOUND,
                    MessagesManager.getInstance().getMessage(
                            "spawn-not-found", "{spawn}", spawnName
                    ));
            return;
        }

        spawnManager.deleteSpawn(spawnName);
        utils.message(player, Sounds.SUCCESS_SOUND,
                MessagesManager.getInstance().getMessage(
                        "spawn-deleted", "{spawn}", spawnName
                ));
    }
}
