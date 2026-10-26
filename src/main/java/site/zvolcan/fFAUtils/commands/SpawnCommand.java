package site.zvolcan.fFAUtils.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.commands.abs.FFACommand;
import site.zvolcan.fFAUtils.managers.LobbyManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import org.bukkit.GameMode;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.PlayerState;

public final class SpawnCommand implements FFACommand {

    private final SpawnManager spawnManager;
    private final LobbyManager lobbyManager;
    private final PlayersManager playersManager;

    public SpawnCommand(SpawnManager spawnManager, LobbyManager lobbyManager, PlayersManager playersManager) {
        this.spawnManager = spawnManager;
        this.lobbyManager = lobbyManager;
        this.playersManager = playersManager;
    }

    @Override
    public String name() {
        return "spawn";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /spawn took no arguments: anything longer was an unparsed command, so nothing ran.
        if (args.length != 0) {
            return true;
        }

        if (sender instanceof Player player) {
            player.teleport(spawnManager.getLobbySpawn());
            player.setHealth(20);
            player.setFoodLevel(20);
            player.setSaturation(0);
            player.setGameMode(GameMode.SURVIVAL);
            player.getActivePotionEffects().forEach(e -> {
                player.removePotionEffect(e.getType());
            });
            FFAPlayer ffaPlayer = playersManager.getFFAPlayer(player);
            ffaPlayer.setState(PlayerState.LOBBY);
            lobbyManager.addLobbyItems(player);
        }

        return true;
    }
}
