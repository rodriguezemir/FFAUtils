package site.zvolcan.fFAUtils.managers;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.*;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;

import java.util.ArrayList;
import java.util.List;

public final class CommandManager {

    private final FFAUtils plugin;
    private final KitManager kitManager;
    private final SpawnManager spawnManager;
    private final LobbyManager lobbyManager;

    public CommandManager(FFAUtils plugin, KitManager kitManager, SpawnManager spawnManager, LobbyManager lobbyManager) {
        this.plugin = plugin;
        this.kitManager = kitManager;
        this.spawnManager = spawnManager;
        this.lobbyManager = lobbyManager;
        registerCommands();
    }

    public void registerCommands() {
        // TODO - Agregar permisos a los comandos
        final List<CommandExecutor> list = new ArrayList<>();
        list.add(new KitCommand(plugin, kitManager));
        list.add(new LoadMeCommand(plugin, kitManager, spawnManager));
        list.add(new SpawnCommand(spawnManager, lobbyManager));
        list.add(new DeadCommand());
        list.add(new MainCommand(plugin.getUtils()));
        list.add(new SetSpawnCommand(spawnManager, plugin.getUtils()));

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, (cmd) -> {
            for (CommandExecutor executor : list) {
                cmd.registrar().register(executor.execute());
            }
        });
    }

}
