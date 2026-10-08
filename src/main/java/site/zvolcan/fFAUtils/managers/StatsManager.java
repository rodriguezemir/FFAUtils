package site.zvolcan.fFAUtils.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.PlayerKitContentsCodec;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StatsManager {

    private final FFAUtils plugin;
    private final Logger logger;
    private final PlayerKitContentsCodec codec = new PlayerKitContentsCodec();
    private final Map<UUID, FFAPlayer> players = new ConcurrentHashMap<>();
    private final Map<UUID, LoadState> loadStates = new HashMap<>();
    private HikariDataSource dataSource;

    private static final class LoadState {
        boolean statsLoaded;
        boolean kitsLoaded;
        Set<String> knownKits = new HashSet<>();
    }

    public StatsManager(FFAUtils plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    StatsManager(HikariDataSource dataSource, Logger logger) {
        this.plugin = null;
        this.dataSource = dataSource;
        this.logger = logger;
    }

    public void init() {
        if (plugin != null) connect();
        if (dataSource != null) createTable();
    }

    private void connect() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, plugin.getConfig().getString("stats-database-name", "stats.db"));

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(10);
            config.setConnectionTimeout(5000);
            config.setPoolName("FFAUtils-SQLite");
            dataSource = new HikariDataSource(config);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to connect to SQLite database via HikariCP", e);
        }
    }

    private void createTable() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_stats (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "kills INT DEFAULT 0, " +
                            "deaths INT DEFAULT 0" +
                            ")");
            statement.execute("CREATE TABLE IF NOT EXISTS player_kit_contents (" +
                    "uuid VARCHAR(36) NOT NULL, kit_name TEXT NOT NULL, contents BLOB NOT NULL, " +
                    "fingerprint TEXT, PRIMARY KEY (uuid, kit_name))");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create player persistence tables", e);
        }
    }

    /** Idempotent within a session: gameplay must register this exact instance. */
    public FFAPlayer loadPlayer(UUID uuid) {
        return players.computeIfAbsent(uuid, this::readPlayer);
    }

    private FFAPlayer readPlayer(UUID uuid) {
        FFAPlayer profile = new FFAPlayer(uuid);
        LoadState state = new LoadState();
        loadStates.put(uuid, state);
        if (dataSource == null) return profile;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT kills, deaths FROM player_stats WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        int kills = result.getInt("kills");
                        int deaths = result.getInt("deaths");
                        profile.setKills(kills);
                        profile.setDeaths(deaths);
                    }
                }
                state.statsLoaded = true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to load stats for " + uuid + "; stats writes disabled", e);
            }
            // Stage results: a failed query must not publish a partial layout snapshot.
            FFAPlayer layouts = new FFAPlayer(uuid);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT kit_name, contents, fingerprint FROM player_kit_contents WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String name = result.getString("kit_name");
                        byte[] contents = result.getBytes("contents");
                        String fingerprint = result.getString("fingerprint");
                        try {
                            layouts.setPersonalKitContents(name, codec.decode(contents), fingerprint);
                        } catch (RuntimeException e) {
                            logger.log(Level.WARNING, "Ignoring malformed personal kit " + name + " for " + uuid, e);
                        }
                    }
                }
                layouts.getPersonalKitContents().forEach((name, contents) ->
                        profile.setPersonalKitContents(name, contents, layouts.getPersonalKitFingerprint(name)));
                state.knownKits.addAll(layouts.getPersonalKitContents().keySet());
                state.kitsLoaded = true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to load personal kits for " + uuid + "; kit writes disabled", e);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load player " + uuid + "; writes disabled", e);
        }
        return profile;
    }

    public void savePlayer(UUID uuid) {
        persistPlayer(uuid);
    }

    private boolean persistPlayer(UUID uuid) {
        FFAPlayer profile = players.get(uuid);
        LoadState state = loadStates.get(uuid);
        if (profile == null || state == null) return true;
        if (dataSource == null) return false;
        try {
            Map<String, byte[]> encoded = new HashMap<>();
            if (state.kitsLoaded) {
                profile.getPersonalKitContents().forEach((name, contents) -> encoded.put(name, codec.encode(contents)));
            }
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    if (state.statsLoaded) {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO player_stats (uuid, kills, deaths) VALUES (?, ?, ?) " +
                                        "ON CONFLICT(uuid) DO UPDATE SET kills = excluded.kills, deaths = excluded.deaths")) {
                            statement.setString(1, uuid.toString());
                            statement.setInt(2, profile.getKills());
                            statement.setInt(3, profile.getDeaths());
                            statement.executeUpdate();
                        }
                    }
                    if (state.kitsLoaded) saveLayouts(connection, uuid, profile, state, encoded);
                    connection.commit();
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    throw e;
                }
            }
            if (state.kitsLoaded) state.knownKits = new HashSet<>(encoded.keySet());
            return true;
        } catch (SQLException | RuntimeException e) {
            logger.log(Level.WARNING, "Failed to save player " + uuid + "; retaining loaded profile for retry", e);
            return false;
        }
    }

    private void saveLayouts(Connection connection, UUID uuid, FFAPlayer profile, LoadState state,
                             Map<String, byte[]> encoded) throws SQLException {
        // Only delete overrides that were successfully read or saved; unreadable rows remain untouched.
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM player_kit_contents WHERE uuid = ? AND kit_name = ?")) {
            for (String name : state.knownKits) {
                if (!encoded.containsKey(name)) {
                    delete.setString(1, uuid.toString());
                    delete.setString(2, name);
                    delete.executeUpdate();
                }
            }
        }
        try (PreparedStatement upsert = connection.prepareStatement(
                "INSERT INTO player_kit_contents (uuid, kit_name, contents, fingerprint) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(uuid, kit_name) DO UPDATE SET contents = excluded.contents, fingerprint = excluded.fingerprint")) {
            for (Map.Entry<String, byte[]> entry : encoded.entrySet()) {
                upsert.setString(1, uuid.toString());
                upsert.setString(2, entry.getKey());
                upsert.setBytes(3, entry.getValue());
                upsert.setString(4, profile.getPersonalKitFingerprint(entry.getKey()));
                upsert.executeUpdate();
            }
        }
    }

    public void unloadPlayer(UUID uuid) {
        if (persistPlayer(uuid)) {
            players.remove(uuid);
            loadStates.remove(uuid);
        }
    }

    public void saveAllPlayers() {
        for (UUID uuid : players.keySet()) {
            savePlayer(uuid);
        }
    }

    public FFAPlayer getFFAPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public void addKill(UUID uuid) {
        FFAPlayer ffaPlayer = players.get(uuid);
        if (ffaPlayer != null) {
            ffaPlayer.setKills(ffaPlayer.getKills() + 1);
        }
    }

    public void addDeath(UUID uuid) {
        FFAPlayer ffaPlayer = players.get(uuid);
        if (ffaPlayer != null) {
            ffaPlayer.setDeaths(ffaPlayer.getDeaths() + 1);
        }
    }

    public int getKills(UUID uuid) {
        FFAPlayer ffaPlayer = players.get(uuid);
        return ffaPlayer != null ? ffaPlayer.getKills() : 0;
    }

    public int getDeaths(UUID uuid) {
        FFAPlayer ffaPlayer = players.get(uuid);
        return ffaPlayer != null ? ffaPlayer.getDeaths() : 0;
    }

    public double getKDR(UUID uuid) {
        FFAPlayer ffaPlayer = players.get(uuid);
        return ffaPlayer != null ? ffaPlayer.getKDR() : 0;
    }

    public Collection<FFAPlayer> getAllPlayers() {
        return players.values();
    }

    public void close() {
        saveAllPlayers();
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
