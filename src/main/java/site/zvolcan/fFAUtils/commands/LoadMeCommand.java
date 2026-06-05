package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.Sounds;

public final class LoadMeCommand implements CommandExecutor {

    private final FFAUtils plugin;
    private final KitManager kitManager;
    private final SpawnManager spawnManager;

    public LoadMeCommand(FFAUtils plugin, KitManager kitManager, SpawnManager spawnManager) {
        this.plugin = plugin;
        this.kitManager = kitManager;
        this.spawnManager = spawnManager;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("loadme");

        literal.then(
            Commands.argument("kit", StringArgumentType.word())
                .then(Commands.argument("spawn", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        CommandSender sender = source.getSender();
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage("Only players can execute this command.");
                            return 1;
                        }
                        String kitName = StringArgumentType.getString(ctx, "kit");
                        String spawnName = StringArgumentType.getString(ctx, "spawn");
                        Kit kit = kitManager.getKit(kitName);
                        if (kit == null) {
                            plugin.getUtils().message(player, Sounds.SUCCESS_SOUND, "<red>Kit '" + kitName + "' not found.");
                            return 1;
                        }
                        Location spawn = spawnManager.getSpawn(spawnName);
                        if (spawn == null) {
                            plugin.getUtils().message(player, Sounds.SUCCESS_SOUND, "<red>Spawn '" + spawnName + "' not found.");
                            return 1;
                        }
                        player.getInventory().setContents(kit.getContents());
                        player.teleport(spawn);
                        plugin.getUtils().message(player, Sounds.SUCCESS_SOUND, "<green>Loaded kit '" + kitName + "' and teleported to spawn '" + spawnName + "'.");
                        return 1;
                    })
                )
        );

        return literal.build();
    }
}
