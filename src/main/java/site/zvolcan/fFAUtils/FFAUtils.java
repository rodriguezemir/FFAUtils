package site.zvolcan.fFAUtils;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import me.putindeer.api.util.PluginUtils;
import site.zvolcan.fFAUtils.listeners.PlayerConnectListener;
import site.zvolcan.fFAUtils.managers.CombatLogManager;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.LobbyManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;

public class FFAUtils extends JavaPlugin {

    @Getter
    private PluginUtils utils;
    @Getter
    private SpawnManager spawnManager;
    @Getter
    private KitManager kitManager;
    @Getter
    private CombatLogManager combatLogManager;
    @Getter
    private LobbyManager lobbyManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        utils = new PluginUtils(this, getConfig().getString("messages-prefix", "<red><b>FFA <reset>"));
        spawnManager = new SpawnManager(this);
        spawnManager.registerSpawns();
        kitManager = new KitManager(this);
        kitManager.registerKits();
        combatLogManager = new CombatLogManager(this, getConfig().getLong("combatlog.timeout-ticks", 300L));
        combatLogManager.startCleanupTask();
        getServer().getPluginManager().registerEvents(new PlayerConnectListener(this), this);
        lobbyManager = new LobbyManager(this);
    }

    @Override
    public void onDisable() {
    }
}
