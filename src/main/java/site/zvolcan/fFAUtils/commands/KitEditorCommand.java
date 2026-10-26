package site.zvolcan.fFAUtils.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.commands.abs.FFACommand;
import site.zvolcan.fFAUtils.inventory.KitEditorInventory;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.PlayerState;
import site.zvolcan.fFAUtils.objects.Sounds;
import site.zvolcan.fFAUtils.FFAUtils;

public final class KitEditorCommand implements FFACommand {

    private final FFAUtils plugin;
    private final KitManager kitManager;
    private final PlayersManager playersManager;

    public KitEditorCommand(FFAUtils plugin, KitManager kitManager, PlayersManager playersManager) {
        this.plugin = plugin;
        this.kitManager = kitManager;
        this.playersManager = playersManager;
    }

    @Override
    public String name() {
        return "ffakiteditor";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /ffakiteditor took no arguments: anything longer was an unparsed command.
        if (args.length != 0) {
            return true;
        }

        // The command tree required a Player sender, so console saw no command at all.
        if (!(sender instanceof Player player)) {
            return true;
        }

        FFAPlayer ffaPlayer = playersManager.getFFAPlayer(player);
        if (ffaPlayer.getState() == PlayerState.IN_FFA) {
            plugin.getUtils().message(player, Sounds.ERROR_SOUND,
                    MessagesManager.getInstance().getMessage("player-already-in-ffa"));
            return true;
        }

        new KitEditorInventory(kitManager, 0).open(player);
        return true;
    }
}
