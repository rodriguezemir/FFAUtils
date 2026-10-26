package site.zvolcan.fFAUtils.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.abs.FFACommand;
import site.zvolcan.fFAUtils.managers.Fto10Manager;
import site.zvolcan.fFAUtils.managers.MessagesManager;

import java.util.List;

/** Fto10 invitations and match abandonment. */
public final class Fto10Command implements FFACommand {

    private static final String[] SUBCOMMANDS = {"invite", "accept", "deny", "leave"};

    private final FFAUtils plugin;
    private final Fto10Manager fto10Manager;

    public Fto10Command(FFAUtils plugin, Fto10Manager fto10Manager) {
        this.plugin = plugin;
        this.fto10Manager = fto10Manager;
    }

    @Override
    public String name() {
        return "fto10";
    }

    /**
     * {@code /fto10 <invite|accept|deny|leave> <player>}. The subcommand has no
     * default branch: an unknown action, a missing player or any extra argument
     * left the command unparsed, so nothing ran.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 2) {
            return true;
        }
        String name = args[1];
        switch (args[0]) {
            case "invite" -> invite(sender, name);
            case "accept" -> accept(sender, name);
            case "deny" -> deny(sender, name);
            case "leave" -> leave(sender, name);
            default -> {
                // Not a subcommand of this tree.
            }
        }
        return true;
    }

    /** The action literals; the player argument had no {@code suggests}, so it suggests nothing. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return FFACommand.atFirstToken(args, SUBCOMMANDS);
    }

    private int invite(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance().getMessage("only-players-execute"));
            return 1;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            return error(player, "fto10-player-not-found", "{player}", name);
        }
        return result(player, fto10Manager.invite(player, target), name);
    }

    private int accept(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance().getMessage("only-players-execute"));
            return 1;
        }
        Player challenger = Bukkit.getPlayerExact(name);
        if (challenger == null) {
            return error(player, "fto10-player-not-found", "{player}", name);
        }
        return result(player, fto10Manager.accept(player, challenger), name);
    }

    private int deny(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance().getMessage("only-players-execute"));
            return 1;
        }
        Player challenger = Bukkit.getPlayerExact(name);
        if (challenger == null) {
            return error(player, "fto10-player-not-found", "{player}", name);
        }
        return result(player, fto10Manager.deny(player, challenger), name);
    }

    private int leave(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance().getMessage("only-players-execute"));
            return 1;
        }
        Player opponent = Bukkit.getPlayerExact(name);
        if (opponent == null) {
            return error(player, "fto10-player-not-found", "{player}", name);
        }
        return result(player, fto10Manager.leave(player, opponent), name);
    }

    private int result(Player player, Fto10Manager.Result result, String name) {
        if (result == Fto10Manager.Result.SUCCESS) {
            return 1;
        }
        String key = switch (result) {
            case SELF -> "fto10-cannot-challenge-yourself";
            case NOT_IN_FFA -> "fto10-not-in-ffa";
            case ALREADY_BUSY -> "fto10-already-busy";
            case NO_INVITATION -> "fto10-no-invitation";
            case WRONG_PLAYER -> "fto10-wrong-player";
            case NOT_IN_MATCH -> "fto10-not-in-match";
            case SUCCESS -> "fto10-not-in-match";
        };
        return error(player, key, "{player}", name);
    }

    private int error(Player player, String key, String placeholder, String value) {
        player.sendMessage(MessagesManager.getInstance().getMessage(key, placeholder, value));
        return 1;
    }
}
