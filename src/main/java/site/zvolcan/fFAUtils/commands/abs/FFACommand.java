package site.zvolcan.fFAUtils.commands.abs;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A plugin command implemented with Bukkit's classic command API.
 *
 * <p>Every implementation owns a single literal {@link #name() name} that must
 * also be declared under {@code commands:} in {@code plugin.yml}: the command is
 * registered by looking that declaration up from the plugin, so a command that
 * is not declared cannot be executed at all.</p>
 */
public interface FFACommand extends CommandExecutor, TabCompleter {

    /** Literal command name, exactly as declared in {@code plugin.yml}. */
    String name();

    /** Commands without suggestions keep Brigadier's default: an empty, never-null list. */
    @Override
    default List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    /**
     * Suggestions for the first token of a command, where its child literals live.
     *
     * <p>Bukkit passes the tokens already typed, so position zero is
     * {@code args.length == 0} with the cursor right after the command name and
     * {@code args.length == 1} while the first token is still being typed. Any
     * deeper position belongs to an argument, which suggests nothing.</p>
     */
    static List<String> atFirstToken(String[] args, String... literals) {
        if (args.length > 1) {
            return Collections.emptyList();
        }
        return matching(args.length == 0 ? "" : args[0], literals);
    }

    /**
     * The literal suggestions Brigadier handed out for free at a node: every child
     * literal whose name starts with the token being typed, case-insensitively.
     * Argument positions pass no candidates, so they keep suggesting nothing.
     */
    static List<String> matching(String typed, String... candidates) {
        String prefix = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>(candidates.length);
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                suggestions.add(candidate);
            }
        }
        return suggestions;
    }
}
