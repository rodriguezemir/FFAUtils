package site.zvolcan.fFAUtils.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.listeners.PlayerConnectListener;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.PlayerState;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatsManagerTest {
    @TempDir Path directory;
    private HikariDataSource source;
    private StatsManager manager;
    private final UUID uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + directory.resolve("stats.db"));
        config.setMaximumPoolSize(1);
        source = new HikariDataSource(config);
        try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE player_stats (uuid VARCHAR(36) PRIMARY KEY, kills INT DEFAULT 0, deaths INT DEFAULT 0)");
            statement.execute("INSERT INTO player_stats VALUES ('" + uuid + "', 7, 3)");
        }
        manager = new StatsManager(source, Logger.getLogger("StatsManagerTest"));
        manager.init();
    }

    @AfterEach
    void tearDown() {
        if (source != null) source.close();
        MockBukkit.unmock();
    }

    @Test
    void saveUnloadReload_preservesLegacyStatsSlotsFingerprintAndDeletion() {
        FFAPlayer profile = manager.loadPlayer(uuid);
        assertEquals(7, profile.getKills());
        assertEquals(3, profile.getDeaths());
        assertSame(profile, manager.loadPlayer(uuid));
        ItemStack[] slots = new ItemStack[41];
        slots[40] = new ItemStack(Material.AIR);
        profile.setPersonalKitContents("kit", slots, "global-v1");
        profile.setPersonalKitContents("empty", new ItemStack[41], null);
        profile.setKills(9);
        manager.unloadPlayer(uuid);
        assertNull(manager.getFFAPlayer(uuid));
        FFAPlayer reloaded = manager.loadPlayer(uuid);
        assertNotSame(profile, reloaded);
        assertEquals(9, reloaded.getKills());
        assertEquals(3, reloaded.getDeaths());
        assertArrayEquals(slots, reloaded.getPersonalKitContents("kit"));
        assertEquals("global-v1", reloaded.getPersonalKitFingerprint("kit"));
        assertEquals(41, reloaded.getPersonalKitContents("empty").length);
        assertNull(manager.loadPlayer(UUID.randomUUID()).getPersonalKitContents("kit"));
        reloaded.removePersonalKitContents("kit");
        manager.unloadPlayer(uuid);
        assertNull(manager.loadPlayer(uuid).getPersonalKitContents("kit"));
        assertNotNull(manager.getFFAPlayer(uuid).getPersonalKitContents("empty"));
    }

    @Test
    void fullLayout_roundTripsThroughSQLiteIncludingMetadataAndEquipment() {
        ItemStack[] slots = new ItemStack[41];
        java.util.Arrays.setAll(slots, slot -> new ItemStack(Material.STONE, slot + 1));
        slots[36] = new ItemStack(Material.DIAMOND_BOOTS);
        slots[39] = new ItemStack(Material.DIAMOND_HELMET);
        slots[40] = new ItemStack(Material.SHIELD);
        var meta = slots[40].getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Saved shield"));
        slots[40].setItemMeta(meta);
        manager.loadPlayer(uuid).setPersonalKitContents("full", slots, "v1");
        manager.unloadPlayer(uuid);
        assertArrayEquals(slots, manager.loadPlayer(uuid).getPersonalKitContents("full"));
    }

    @Test
    void failedSave_rollsBackStatsRetainsProfileAndRetries() throws Exception {
        FFAPlayer profile = manager.loadPlayer(uuid);
        profile.setKills(99);
        profile.setPersonalKitContents("valid", new ItemStack[41], null);
        execute("ALTER TABLE player_kit_contents RENAME TO unavailable_kits");
        manager.unloadPlayer(uuid);
        assertSame(profile, manager.getFFAPlayer(uuid));
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT kills FROM player_stats")) {
            assertTrue(result.next());
            assertEquals(7, result.getInt(1));
        }
        execute("ALTER TABLE unavailable_kits RENAME TO player_kit_contents");
        manager.unloadPlayer(uuid);
        assertNull(manager.getFFAPlayer(uuid));
        assertEquals(99, manager.loadPlayer(uuid).getKills());
        assertNotNull(manager.getFFAPlayer(uuid).getPersonalKitContents("valid"));
    }

    @Test
    void reconnectAfterFailedSave_resetsRuntimeStateAndPreservesDirtyProfile() throws Exception {
        FFAUtils plugin = mock(FFAUtils.class);
        LobbyManager lobby = mock(LobbyManager.class);
        SpawnManager spawn = mock(SpawnManager.class);
        when(plugin.getCombatLogManager()).thenReturn(mock(CombatLogManager.class));
        PlayersManager gameplay = new PlayersManager();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        Location lobbySpawn = new Location(null, 0, 64, 0);
        when(spawn.getLobbySpawn()).thenReturn(lobbySpawn);
        PlayerConnectListener listener = new PlayerConnectListener(plugin, lobby, gameplay, spawn, manager);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);

        FFAPlayer profile = manager.loadPlayer(uuid);
        ItemStack[] slots = new ItemStack[41];
        slots[0] = new ItemStack(Material.STONE, 12);
        slots[36] = new ItemStack(Material.DIAMOND_BOOTS);
        slots[40] = new ItemStack(Material.SHIELD);
        profile.setPersonalKitContents("edited", slots, "global-v2");
        profile.setKills(99);
        profile.setDeaths(8);
        profile.setState(PlayerState.IN_FFA);
        profile.setKillstreak(5);
        profile.setLastKit(new Kit("edited", slots));
        profile.setLastSpawn(new Location(null, 10, 80, 10));
        gameplay.registerPlayer(profile);

        execute("ALTER TABLE player_kit_contents RENAME TO unavailable_kits");
        listener.onPlayerQuit(quit);
        assertFalse(gameplay.getAllPlayers().containsKey(uuid));
        assertSame(profile, manager.getFFAPlayer(uuid));
        assertSame(profile, manager.loadPlayer(uuid));
        doAnswer(invocation -> {
            assertLobbyRuntimeState(profile);
            return null;
        }).when(lobby).addLobbyItems(player);
        listener.joinPlayer(join);

        assertAll(
                () -> assertSame(profile, gameplay.getFFAPlayer(player)),
                () -> assertSame(profile, manager.getFFAPlayer(uuid)),
                () -> assertLobbyRuntimeState(profile),
                () -> assertEquals(99, profile.getKills()),
                () -> assertEquals(8, profile.getDeaths()),
                () -> assertArrayEquals(slots, profile.getPersonalKitContents("edited")),
                () -> assertEquals("global-v2", profile.getPersonalKitFingerprint("edited")));
        verify(lobby).addLobbyItems(player);
        verify(player).teleport(lobbySpawn);

        execute("ALTER TABLE unavailable_kits RENAME TO player_kit_contents");
        listener.onPlayerQuit(quit);
        assertNull(manager.getFFAPlayer(uuid));
        FFAPlayer reloaded = manager.loadPlayer(uuid);
        assertNotSame(profile, reloaded);
        assertEquals(99, reloaded.getKills());
        assertEquals(8, reloaded.getDeaths());
        assertArrayEquals(slots, reloaded.getPersonalKitContents("edited"));
        assertEquals("global-v2", reloaded.getPersonalKitFingerprint("edited"));
    }

    @Test
    void malformedRow_doesNotDiscardStatsOrOtherLayoutsOrEraseUnreadableRow() throws Exception {
        FFAPlayer profile = manager.loadPlayer(uuid);
        profile.setPersonalKitContents("valid", new ItemStack[41], "v1");
        manager.unloadPlayer(uuid);
        execute("INSERT INTO player_kit_contents VALUES ('" + uuid + "', 'broken', X'010203', 'v0')");
        profile = manager.loadPlayer(uuid);
        assertEquals(7, profile.getKills());
        assertNotNull(profile.getPersonalKitContents("valid"));
        assertNull(profile.getPersonalKitContents("broken"));
        profile.setKills(8);
        manager.unloadPlayer(uuid);
        assertEquals(2, count("player_kit_contents"));
        assertEquals(8, manager.loadPlayer(uuid).getKills());
    }

    @Test
    void failedLayoutQuery_cannotEraseExistingLayouts() throws Exception {
        FFAPlayer profile = manager.loadPlayer(uuid);
        profile.setPersonalKitContents("valid", new ItemStack[41], null);
        manager.unloadPlayer(uuid);
        execute("ALTER TABLE player_kit_contents RENAME TO unavailable_kits");
        profile = manager.loadPlayer(uuid);
        assertEquals(7, profile.getKills());
        execute("ALTER TABLE unavailable_kits RENAME TO player_kit_contents");
        profile.setKills(10);
        profile.setPersonalKitContents("new", new ItemStack[41], null);
        manager.unloadPlayer(uuid);
        assertEquals(1, count("player_kit_contents"));
        assertNotNull(manager.loadPlayer(uuid).getPersonalKitContents("valid"));
        assertEquals(10, manager.getFFAPlayer(uuid).getKills());
    }

    @Test
    void failedStatsQuery_cannotOverwriteExistingStats() throws Exception {
        execute("ALTER TABLE player_stats RENAME TO unavailable_stats");
        FFAPlayer profile = manager.loadPlayer(uuid);
        execute("ALTER TABLE unavailable_stats RENAME TO player_stats");
        profile.setKills(99);
        profile.setPersonalKitContents("valid", new ItemStack[41], null);
        manager.unloadPlayer(uuid);
        assertEquals(7, manager.loadPlayer(uuid).getKills());
        assertNotNull(manager.getFFAPlayer(uuid).getPersonalKitContents("valid"));
    }

    @Test
    void close_flushesActualEditedProfile() throws Exception {
        FFAPlayer profile = manager.loadPlayer(uuid);
        PlayersManager gameplay = new PlayersManager();
        gameplay.registerPlayer(profile);
        gameplay.getAllPlayers().get(uuid).setPersonalKitContents("edited", new ItemStack[41], "v2");
        manager.addKill(uuid);
        assertSame(profile, gameplay.getAllPlayers().get(uuid));
        manager.close();
        // Reopen the actual SQLite database, rather than consulting the closed manager's cache.
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("stats.db"));
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT kills FROM player_stats WHERE uuid = '" + uuid + "'")) {
            assertTrue(result.next());
            assertEquals(8, result.getInt(1));
        }
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("stats.db"));
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT fingerprint FROM player_kit_contents WHERE uuid = '" + uuid + "'")) {
            assertTrue(result.next());
            assertEquals("v2", result.getString(1));
        }
    }

    private void assertLobbyRuntimeState(FFAPlayer profile) {
        assertAll(
                () -> assertEquals(PlayerState.LOBBY, profile.getState()),
                () -> assertEquals(0, profile.getKillstreak()),
                () -> assertNull(profile.getLastKit()),
                () -> assertNull(profile.getLastSpawn()));
    }

    private void execute(String sql) throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int count(String table) throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
