package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.*;
import site.zvolcan.fFAUtils.objects.FFAPlayer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerConnectListenerTest {
    @Test
    void joinAndQuit_shareLoadedProfileAndSaveAfterCombatDeath() {
        FFAUtils plugin = mock(FFAUtils.class);
        LobbyManager lobby = mock(LobbyManager.class);
        SpawnManager spawn = mock(SpawnManager.class);
        StatsManager stats = mock(StatsManager.class);
        CombatLogManager combat = mock(CombatLogManager.class);
        when(plugin.getCombatLogManager()).thenReturn(combat);
        PlayersManager gameplay = new PlayersManager();
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        FFAPlayer profile = new FFAPlayer(uuid);
        profile.setKills(4);
        when(stats.loadPlayer(uuid)).thenReturn(profile);
        PlayerConnectListener listener = new PlayerConnectListener(plugin, lobby, gameplay, spawn, stats);
        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);
        listener.joinPlayer(join);
        assertSame(profile, gameplay.getFFAPlayer(player));
        assertEquals(4, gameplay.getFFAPlayer(player).getKills());
        when(combat.isInCombat(uuid)).thenReturn(true);
        doAnswer(invocation -> {
            assertSame(profile, gameplay.getFFAPlayer(player));
            profile.setDeaths(profile.getDeaths() + 1);
            return null;
        }).when(player).setHealth(0);
        doAnswer(invocation -> {
            assertEquals(1, profile.getDeaths());
            return null;
        }).when(stats).unloadPlayer(uuid);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        listener.onPlayerQuit(quit);
        verify(stats).unloadPlayer(uuid);
        assertFalse(gameplay.getAllPlayers().containsKey(uuid));
    }
}
