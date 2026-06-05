package site.zvolcan.fFAUtils.managers;

import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.objects.FFAPlayer;

import java.io.File;
import java.sql.*;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class StatsManager {

    private final FFAUtils plugin;
    private final Map<UUID, FFAPlayer> players = new ConcurrentHashMap<>();
    private Connection connection;

    public StatsManager(FFAUtils plugin) {
        this.plugin = plugin;
    }

    public void init() {
        connect();
        createTable();
    }

    private void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, "stats.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to SQLite database", e);
        }
    }

    private void createTable() {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "CREATE TABLE IF NOT EXISTS player_stats (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "kills INT DEFAULT 0, " +
                "deaths INT DEFAULT 0" +
                ")"
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create player_stats table", e);
        }
    }

    public FFAPlayer loadPlayer(UUID uuid) {
        FFAPlayer ffaPlayer = new FFAPlayer(uuid);
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT kills, deaths FROM player_stats WHERE uuid = ?"
        )) {
            statement.setString(1, uuid.toString());
            ResultSet result = statement.executeQuery();
            if (result.next()) {
                ffaPlayer.setKills(result.getInt("kills"));
                ffaPlayer.setDeaths(result.getInt("deaths"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load stats for " + uuid, e);
        }
        players.put(uuid, ffaPlayer);
        return ffaPlayer;
    }

    public void savePlayer(UUID uuid) {
        FFAPlayer ffaPlayer = players.get(uuid);
        if (ffaPlayer == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO player_stats (uuid, kills, deaths) VALUES (?, ?, ?) " +
            "ON CONFLICT(uuid) DO UPDATE SET kills = ?, deaths = ?"
        )) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, ffaPlayer.getKills());
            statement.setInt(3, ffaPlayer.getDeaths());
            statement.setInt(4, ffaPlayer.getKills());
            statement.setInt(5, ffaPlayer.getDeaths());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save stats for " + uuid, e);
        }
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        players.remove(uuid);
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
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to close database connection", e);
            }
        }
    }
}
