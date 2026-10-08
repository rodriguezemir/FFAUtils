package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInvManager;
import me.putindeer.api.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.*;
import site.zvolcan.fFAUtils.objects.Kit;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KitDetailInventoryTest {
    @TempDir Path directory;
    KitManager manager;
    PlayersManager players;
    PlayerMock player;
    MockedStatic<FFAUtils> singleton;
    org.bukkit.plugin.Plugin registrar;

    @BeforeEach void setUp() {
        var server = MockBukkit.mock();
        player = server.addPlayer();
        var plugin = mock(FFAUtils.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getUtils()).thenReturn(mock(PluginUtils.class));
        players = new PlayersManager();
        when(plugin.getPlayersManager()).thenReturn(players);
        singleton = mockStatic(FFAUtils.class);
        singleton.when(FFAUtils::getInstance).thenReturn(plugin);
        registrar = MockBukkit.createMockPlugin();
        FastInvManager.register(registrar);
        manager = new KitManager(plugin);
        manager.saveKit("test", new Kit("test", new ItemStack[]{new ItemStack(Material.STONE, 16)}));
    }
    @AfterEach void tearDown() {
        MockBukkit.getMock().getPluginManager().callEvent(new org.bukkit.event.server.PluginDisableEvent(registrar));
        singleton.close();
        MockBukkit.unmock();
    }

    @Test void restoreDefault_requiresConfirmationAndNeverDeletesGlobalKitOrOtherPlayersLayout() throws Exception {
        byte[] global = Files.readAllBytes(directory.resolve("kits/test.json"));
        ItemStack[] split = new ItemStack[41];
        split[3] = new ItemStack(Material.STONE, 8);
        split[4] = new ItemStack(Material.STONE, 8);
        var other = MockBukkit.getMock().addPlayer();
        assertTrue(manager.savePersonalLayout(player, "test", split));
        assertTrue(manager.savePersonalLayout(other, "test", split));
        var detail = new KitDetailInventory(manager, "test", 0);
        detail.open(player);
        var summary = detail.getInventory().getItem(13).getItemMeta().lore();
        assertEquals(java.util.List.of(KitEditorInventory.text("<gray>2 items in your effective layout</gray>")), summary);
        assertEquals(KitEditorInventory.text("<red>Restore default</red>"),
                detail.getInventory().getItem(15).getItemMeta().displayName());
        var plain = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 15,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        MockBukkit.getMock().getPluginManager().callEvent(plain);
        assertNotNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
        var confirmed = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 15,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        MockBukkit.getMock().getPluginManager().callEvent(confirmed);
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
        assertNotNull(players.getFFAPlayer(other).getPersonalKitContents("test"));
        assertNotNull(manager.getKit("test"));
        assertArrayEquals(global, Files.readAllBytes(directory.resolve("kits/test.json")));
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof KitEditorInventory);
    }
}
