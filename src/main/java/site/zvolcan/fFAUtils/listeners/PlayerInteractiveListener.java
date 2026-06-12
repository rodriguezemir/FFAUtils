package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import net.kyori.adventure.text.minimessage.MiniMessage;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.objects.PlayerState;

public class PlayerInteractiveListener implements Listener {

    private final PlayersManager playersManager;

    public PlayerInteractiveListener(PlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @EventHandler
    public void onPlayerPing(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof Player player) {
            event.getPlayer().sendActionBar(
                    MiniMessage.miniMessage().deserialize(
                            MessagesManager.getInstance().getMessage("interact-player-actionbar", "{player}",
                                    player.getName(), "{ping}", String.valueOf(player.getPing()))));
        }

    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (playersManager.getFFAPlayer(event.getPlayer()).getState() == PlayerState.LOBBY) {
            event.setCancelled(true);
        }
    }

}
