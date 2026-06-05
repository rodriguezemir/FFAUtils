package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import site.zvolcan.fFAUtils.managers.CombatLogManager;
import site.zvolcan.fFAUtils.managers.DeathEventManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.managers.StatsManager;

public class PlayerDeathListener implements Listener {

    private final DeathEventManager deathEventManager;
    private final SpawnManager spawnManager;
    private final CombatLogManager combatLogManager;
    private final StatsManager statsManager;

    public PlayerDeathListener(DeathEventManager deathEventManager, SpawnManager spawnManager, CombatLogManager combatLogManager, StatsManager statsManager) {
        this.deathEventManager = deathEventManager;
        this.spawnManager = spawnManager;
        this.combatLogManager = combatLogManager;
        this.statsManager = statsManager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        final Player player = event.getPlayer();
        deathEventManager.broadcastDeathEvent(player, player.getKiller());
        combatLogManager.removeFromCombat(player.getUniqueId());
        statsManager.addDeath(player.getUniqueId());
        if (player.getKiller() != null) {
            combatLogManager.removeFromCombat(player.getKiller().getUniqueId());
            statsManager.addKill(player.getKiller().getUniqueId());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(spawnManager.getLobbySpawn());
    }
}
