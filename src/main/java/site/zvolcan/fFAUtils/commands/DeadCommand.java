package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;

public final class DeadCommand implements CommandExecutor {
    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = LiteralArgumentBuilder.<CommandSourceStack>literal("dead")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();

                    if (source.getSender() instanceof Player player) {
                        player.setHealth(0);
                    }
                    return 1;
                });

        return literal.build();
    }
}
