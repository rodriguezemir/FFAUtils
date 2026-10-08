package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.*;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.PlayerState;

public class PlayerConnectListener implements Listener {

    private final CombatLogManager combatLogManager;
    private final LobbyManager lobbyManager;
    private final PlayersManager playersManager;
    private final SpawnManager spawnManager;
    private final StatsManager statsManager;
    private final TierManager tierManager;
    private final Fto10Manager fto10Manager;

    public PlayerConnectListener(@NotNull FFAUtils plugin, LobbyManager lobbyManager, PlayersManager playersManager,
            SpawnManager spawnManager, StatsManager statsManager) {
        this.lobbyManager = lobbyManager;
        this.playersManager = playersManager;
        this.combatLogManager = plugin.getCombatLogManager();
        this.spawnManager = spawnManager;
        this.statsManager = statsManager;
        this.tierManager = plugin.getTierManager();
        this.fto10Manager = plugin.getFto10Manager();
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        if (fto10Manager != null) {
            fto10Manager.handleQuit(player);
        }
        if (combatLogManager.isInCombat(player.getUniqueId())) {
            combatLogManager.removeFromCombat(player.getUniqueId());
            player.setHealth(0);
        }
        // Combat death can mutate the shared profile synchronously; save only afterward.
        statsManager.unloadPlayer(player.getUniqueId());
        playersManager.removePlayer(player);
        lobbyManager.clearPendingRespawn(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player damaged) {
            if (fto10Manager != null && fto10Manager.shouldCancelDamage(damaged, event)) {
                event.setCancelled(true);
                return;
            }
            if (event.getDamager() instanceof Player damager) {
                combatLogManager.setInCombat(damaged.getUniqueId());
                combatLogManager.setInCombat(damager.getUniqueId());
            }
        }
    }

    @EventHandler
    public void joinPlayer(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        playersManager.registerPlayer(profile);
        final FFAPlayer profile = statsManager.loadPlayer(player.getUniqueId());
        statsManager.loadPlayer(player.getUniqueId());
        // A failed quit save can retain this profile; only runtime state starts fresh.
        profile.setState(PlayerState.LOBBY);
        profile.setKillstreak(0);
        profile.setLastKit(null);
        profile.setLastSpawn(null);
        lobbyManager.addLobbyItems(player);
        player.teleport(spawnManager.getLobbySpawn());
        // Warm the MCTiers cache so the spawn gate resolves without a round trip.
        if (tierManager != null && tierManager.isPrefetchOnJoin()) {
            tierManager.prefetch(player.getUniqueId(), player.getName());
        }
    }
}
