package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.putindeer.api.util.PluginUtils;

import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import site.zvolcan.fFAUtils.FFAPlaceholders;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.objects.Sounds;

public final class MainCommand implements CommandExecutor {

    private final PluginUtils utils;
    private final FFAPlaceholders ffaPlaceholders;
    private final MessagesManager messagesManager;
    private final KitManager kitManager;
    private final SpawnManager spawnManager;

    public MainCommand(PluginUtils utils, FFAPlaceholders ffaPlaceholders, MessagesManager messagesManager, KitManager kitManager, SpawnManager spawnManager) {
        this.utils = utils;
        this.ffaPlaceholders = ffaPlaceholders;
        this.messagesManager = messagesManager;
        this.kitManager = kitManager;
        this.spawnManager = spawnManager;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("ffautils");

        literal.requires(ctx -> ctx.getSender().hasPermission("ffautils.commands.ffautils"));
        literal.then(Commands.literal("reload").executes((ctx) -> {
            CommandSourceStack source = ctx.getSource();
            CommandSender sender = source.getSender();

            final Logger logger = FFAUtils.getInstance().getLogger();
            logger.info("Reloading Plugin...");

            ffaPlaceholders.register();
            logger.info("Loading Placeholders.");
            messagesManager.registerMessages();
            logger.info("Loading Messages.");
            kitManager.loadAllKits();
            logger.info("Loading Kits.");
            spawnManager.loadAllSpawns();
            logger.info("Loading Spawns.");

            utils.message(
                    sender,
                    Sounds.SUCCESS_SOUND,
                    "<green>FFAUtils has been reloaded."
            );
            return 0;
        }));

        return literal.build();
    }
}
