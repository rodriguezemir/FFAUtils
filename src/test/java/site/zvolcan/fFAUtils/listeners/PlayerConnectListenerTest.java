package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.*;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.util.PluginUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    /**
     * The startup check only ever logs. The in-game half is this join notice, and
     * it must reach exactly the staff who asked for it.
     */
    @Test
    void joinTellsPermittedStaffAboutANewerReleaseAndNobodyElse() {
        FFAUtils plugin = mock(FFAUtils.class);
        LobbyManager lobby = mock(LobbyManager.class);
        SpawnManager spawn = mock(SpawnManager.class);
        StatsManager stats = mock(StatsManager.class);
        PlayersManager gameplay = new PlayersManager();
        UpdateCheckerManager updates = mock(UpdateCheckerManager.class);
        PluginUtils utils = mock(PluginUtils.class);
        when(plugin.getCombatLogManager()).thenReturn(mock(CombatLogManager.class));
        when(plugin.getTierManager()).thenReturn(mock(TierManager.class));
        when(plugin.getUpdateCheckerManager()).thenReturn(updates);
        when(plugin.getUtils()).thenReturn(utils);
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile(
                "FFAUtils", "1.3.0-SNAPSHOT", "site.zvolcan.fFAUtils.FFAUtils"));
        when(updates.notifyPlayers()).thenReturn(true);
        when(updates.newerRelease()).thenReturn(Optional.of(
                new UpdateCheckerManager.Release("v1.4.0", "https://example.test/v1.4.0")));

        Player permitted = joinedPlayer(stats);
        when(permitted.hasPermission("ffautils.update-notify")).thenReturn(true);
        Player anonymous = joinedPlayer(stats);
        when(anonymous.hasPermission("ffautils.update-notify")).thenReturn(false);

        try (org.mockito.MockedStatic<MessagesManager> messagesMock = mockStatic(MessagesManager.class)) {
            MessagesManager messages = mock(MessagesManager.class);
            messagesMock.when(MessagesManager::getInstance).thenReturn(messages);
            when(messages.getMessage(eq("update-available"),
                    eq("{latest}"), eq("v1.4.0"),
                    eq("{current}"), eq("1.3.0-SNAPSHOT"),
                    eq("{url}"), eq("https://example.test/v1.4.0")))
                    .thenReturn("A new release v1.4.0 exists");

            PlayerConnectListener listener = new PlayerConnectListener(plugin, lobby, gameplay, spawn, stats);
            listener.joinPlayer(joinOf(permitted));
            listener.joinPlayer(joinOf(anonymous));

            verify(utils).message(permitted, false, "A new release v1.4.0 exists");
            verify(utils, never()).message(eq(anonymous), any(Boolean.class), anyString());
        }
    }

    private static Player joinedPlayer(StatsManager stats) {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(stats.loadPlayer(uuid)).thenReturn(new FFAPlayer(uuid));
        return player;
    }

    private static PlayerJoinEvent joinOf(Player player) {
        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);
        return join;
    }
}
