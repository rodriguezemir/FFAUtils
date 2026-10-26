package site.zvolcan.fFAUtils.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import site.zvolcan.fFAUtils.FFAPlaceholders;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.DeathEventManager;
import site.zvolcan.fFAUtils.managers.Fto10Manager;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.managers.RegionManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.managers.TierManager;
import site.zvolcan.fFAUtils.util.PluginUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Locks the parts of the command surface that the classic Bukkit API cannot
 * express declaratively.
 *
 * <p>Paper 1.18.2 has no Brigadier command API, so three guarantees that the old
 * command trees provided by construction are now hand-written and easy to lose:
 * the Player-sender requirement of {@code /ffautils} and {@code /ffakiteditor},
 * the silent no-op for an invocation whose arity does not exist, and the child
 * literal suggestions Brigadier produced at the first token.</p>
 */
class CommandSurfaceTest {

    private final Command command = mock(Command.class);

    /** The old nodes required a Player sender, so console never reached these bodies. */
    @Test
    void consoleCannotRunThePlayerOnlyAdminCommands() {
        KitManager kitManager = mock(KitManager.class);
        SpawnManager spawnManager = mock(SpawnManager.class);
        DeathEventManager deathEventManager = mock(DeathEventManager.class);
        MessagesManager messagesManager = mock(MessagesManager.class);
        CommandSender console = mock(CommandSender.class);

        MainCommand main = new MainCommand(mock(PluginUtils.class), mock(FFAPlaceholders.class),
                messagesManager, kitManager, spawnManager, deathEventManager);

        assertTrue(main.onCommand(console, command, "ffautils", new String[] { "reload" }));
        assertTrue(main.onTabComplete(console, command, "ffautils", new String[] { "re" }).isEmpty());
        verifyNoInteractions(kitManager, spawnManager, deathEventManager, messagesManager, console);

        KitManager kits = mock(KitManager.class);
        PlayersManager players = mock(PlayersManager.class);
        CommandSender consoleEditor = mock(CommandSender.class);

        KitEditorCommand editor = new KitEditorCommand(mock(FFAUtils.class), kits, players);

        assertTrue(editor.onCommand(consoleEditor, command, "ffakiteditor", new String[0]));
        verifyNoInteractions(kits, players, consoleEditor);
    }

    /** An argument count the old tree had no node for executed nothing and said nothing. */
    @Test
    void mismatchedArityIsASilentNoOp() {
        KitManager kitManager = mock(KitManager.class);
        Player player = mock(Player.class);
        KitCommand kit = new KitCommand(mock(FFAUtils.class), kitManager);

        assertTrue(kit.onCommand(player, command, "kit", new String[0]));
        assertTrue(kit.onCommand(player, command, "kit", new String[] { "create" }));
        assertTrue(kit.onCommand(player, command, "kit", new String[] { "create", "a", "b" }));
        verifyNoInteractions(kitManager, player);

        Player victim = mock(Player.class);
        assertTrue(new DeadCommand().onCommand(victim, command, "dead", new String[] { "now" }));
        verifyNoInteractions(victim);
    }

    /** The child literals Brigadier suggested for free at the first token, and nothing deeper. */
    @Test
    void firstTokenSuggestsTheSubcommandsAndNothingDeeper() {
        FFAUtils plugin = mock(FFAUtils.class);
        KitCommand kit = new KitCommand(plugin, mock(KitManager.class));

        assertEquals(List.of("create", "edit", "delete", "list"),
                kit.onTabComplete(null, command, "kit", new String[0]));
        assertEquals(List.of("create"), kit.onTabComplete(null, command, "kit", new String[] { "cr" }));
        assertEquals(List.of(), kit.onTabComplete(null, command, "kit", new String[] { "create", "" }));
        assertEquals(List.of(), kit.onTabComplete(null, command, "kit", new String[] { "mykit", "" }));

        assertEquals(List.of("accept"), new Fto10Command(plugin, mock(Fto10Manager.class))
                .onTabComplete(null, command, "fto10", new String[] { "a" }));
        assertEquals(List.of("removekit"), new SetSpawnCommand(mock(SpawnManager.class), mock(KitManager.class),
                mock(PluginUtils.class)).onTabComplete(null, command, "setspawn", new String[] { "re" }));
        assertEquals(List.of("pos1", "pos2"), new RegionCommand(plugin, mock(KitManager.class),
                mock(RegionManager.class)).onTabComplete(null, command, "kitregion", new String[] { "k", "p" }));

        TierManager tierManager = mock(TierManager.class);
        when(tierManager.getProviders()).thenReturn(java.util.Map.of());
        TierCommand tiers = new TierCommand(plugin, tierManager);
        assertEquals(List.of("cache", "check"), tiers.onTabComplete(null, command, "tiers", new String[] { "c" }));
        assertEquals(List.of("clear"), tiers.onTabComplete(null, command, "tiers", new String[] { "cache", "" }));

        Player player = mock(Player.class);
        MainCommand main = new MainCommand(mock(PluginUtils.class), mock(FFAPlaceholders.class),
                mock(MessagesManager.class), mock(KitManager.class), mock(SpawnManager.class),
                mock(DeathEventManager.class));
        assertEquals(List.of("reload"), main.onTabComplete(player, command, "ffautils", new String[] { "re" }));
    }
}
