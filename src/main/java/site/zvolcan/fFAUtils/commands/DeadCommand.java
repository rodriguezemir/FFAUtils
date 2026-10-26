package site.zvolcan.fFAUtils.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.commands.abs.FFACommand;

public final class DeadCommand implements FFACommand {

    @Override
    public String name() {
        return "dead";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /dead took no arguments: anything longer was an unparsed command, so nothing ran.
        if (args.length != 0) {
            return true;
        }

        if (sender instanceof Player player) {
            player.setHealth(0);
        }
        return true;
    }
}
