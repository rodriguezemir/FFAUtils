package site.zvolcan.fFAUtils.inventory;

import me.putindeer.api.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.objects.Kit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KitEditContentsInventoryTest {
    @TempDir Path directory;
    ServerMock server;
    PlayerMock player;
    FFAUtils plugin;
    KitManager manager;
    PlayersManager players;
    ItemStack[] original;
    ItemStack originalCursor;
    byte[] globalFile;

    @BeforeEach void setUp() throws Exception {
        server = MockBukkit.mock();
        player = server.addPlayer();
        plugin = mock(FFAUtils.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getName()).thenReturn("EditorTest");
        when(plugin.getUtils()).thenReturn(mock(PluginUtils.class));
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EditorTest"));
        players = new PlayersManager();
        when(plugin.getPlayersManager()).thenReturn(players);
        manager = new KitManager(plugin);
        server.getPluginManager().registerEvents(new KitEditContentsInventory.SessionListener(), MockBukkit.createMockPlugin());
        ItemStack[] defaults = new ItemStack[41];
        defaults[0] = new ItemStack(Material.STONE, 16);
        defaults[36] = new ItemStack(Material.DIAMOND_BOOTS);
        defaults[40] = new ItemStack(Material.SHIELD);
        manager.saveKit("test", new Kit("test", defaults));
        globalFile = Files.readAllBytes(directory.resolve("kits/test.json"));
        player.getInventory().setItem(7, new ItemStack(Material.APPLE, 3));
        original = player.getInventory().getContents();
        originalCursor = new ItemStack(Material.EMERALD, 2);
        player.setItemOnCursor(originalCursor.clone());
    }

    @AfterEach void tearDown() {
        try { KitEditContentsInventory.restoreAllSessions(); }
        finally { MockBukkit.unmock(); }
    }

    void open() { KitEditContentsInventory.open(plugin, manager, player, "test"); }
    void tick() { server.getScheduler().performOneTick(); }
    InventoryClickEvent click(int slot, ClickType type, InventoryAction action) {
        var event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot, type, action);
        server.getPluginManager().callEvent(event);
        return event;
    }
    void assertRestored() {
        assertArrayEquals(original, player.getInventory().getContents());
        assertEquals(originalCursor, player.getItemOnCursor());
    }

    @Test void save_reopenAndReset_usePersonalLayoutWithoutTouchingGlobalData() throws Exception {
        open();
        assertTrue(player.getItemOnCursor().getType().isAir());
        player.getInventory().setItem(0, null);
        player.getInventory().setItem(2, new ItemStack(Material.STONE, 8));
        player.getInventory().setItem(3, new ItemStack(Material.STONE, 8));
        assertTrue(click(2, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        tick();
        assertRestored();
        var saved = players.getFFAPlayer(player).getPersonalKitContents("test");
        assertNotNull(saved);
        assertEquals(41, saved.length);
        assertEquals(8, saved[2].getAmount());
        assertEquals(Material.STONE, manager.getKit("test").getContents()[0].getType());
        assertArrayEquals(globalFile, Files.readAllBytes(directory.resolve("kits/test.json")));
        open();
        assertArrayEquals(saved, Arrays.copyOf(player.getInventory().getContents(), 41));
        player.getInventory().clear(2);
        player.setItemOnCursor(new ItemStack(Material.STONE, 8));
        click(4, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertArrayEquals(saved, Arrays.copyOf(player.getInventory().getContents(), 41));
        assertTrue(player.getItemOnCursor().getType().isAir());
        click(6, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        tick();
        assertRestored();
    }

    @Test void save_rejectsCursorMissingExtraAndMetadataChanges() {
        open();
        player.setItemOnCursor(new ItemStack(Material.STONE));
        click(2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
        player.setItemOnCursor(null);
        player.getInventory().clear(0);
        click(2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
        player.getInventory().setItem(0, new ItemStack(Material.STONE, 16));
        player.getInventory().setItem(3, new ItemStack(Material.DIAMOND));
        click(2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
        player.getInventory().clear(3);
        var item = player.getInventory().getItem(0);
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Changed"));
        item.setItemMeta(meta);
        click(2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
    }

    @Test void closeQuitAndDisable_restoreInventoryAndCursorIdempotently() {
        open();
        player.setItemOnCursor(new ItemStack(Material.STONE, 16));
        player.closeInventory();
        tick();
        assertRestored();
        open();
        server.getPluginManager().callEvent(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        tick();
        assertRestored();
        open();
        KitEditContentsInventory.restoreAllSessions();
        KitEditContentsInventory.restoreAllSessions();
        tick();
        assertRestored();
    }

    @Test void dropOutsideCreativeCollectShiftAndOtherInventories_areBlocked() {
        open();
        assertTrue(click(-999, ClickType.LEFT, InventoryAction.DROP_ALL_CURSOR).isCancelled());
        assertTrue(click(9, ClickType.DROP, InventoryAction.DROP_ONE_SLOT).isCancelled());
        assertTrue(click(9, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        assertTrue(click(9, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled());
        var creative = new InventoryCreativeEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 9,
                new ItemStack(Material.DIAMOND));
        server.getPluginManager().callEvent(creative);
        assertTrue(creative.isCancelled());
        var dropped = player.getWorld().dropItem(player.getLocation(), new ItemStack(Material.STONE));
        var drop = new PlayerDropItemEvent(player, dropped);
        server.getPluginManager().callEvent(drop);
        assertTrue(drop.isCancelled());
        var pickup = new EntityPickupItemEvent(player, dropped, 0);
        server.getPluginManager().callEvent(pickup);
        assertTrue(pickup.isCancelled());
        // A real second open is rejected by the registered listener.
        var otherInventory = server.createInventory(null, 9);
        player.openInventory(otherInventory);
        assertNotSame(otherInventory, player.getOpenInventory().getTopInventory(), "Other inventory open is cancelled");
        var finishingDrop = new PlayerDropItemEvent(player, dropped);
        server.getPluginManager().callEvent(finishingDrop);
        assertTrue(finishingDrop.isCancelled(), "Copies stay guarded until deferred cleanup");
        tick();
        assertRestored();
    }

    @Test void useConsumeHandSwapAndCommands_areBlockedWhileEditing() {
        open();
        var use = new PlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                new ItemStack(Material.STONE), null, org.bukkit.block.BlockFace.SELF);
        server.getPluginManager().callEvent(use);
        assertEquals(org.bukkit.event.Event.Result.DENY, use.useItemInHand());
        var consume = new PlayerItemConsumeEvent(player, new ItemStack(Material.APPLE));
        server.getPluginManager().callEvent(consume);
        assertTrue(consume.isCancelled());
        var swap = new PlayerSwapHandItemsEvent(player, new ItemStack(Material.STONE), new ItemStack(Material.SHIELD));
        server.getPluginManager().callEvent(swap);
        assertTrue(swap.isCancelled());
        var command = new PlayerCommandPreprocessEvent(player, "/kit test");
        server.getPluginManager().callEvent(command);
        assertTrue(command.isCancelled());
    }

    @Test void untouchedAirAndTrailingNullMarkers_surviveNormalizedEditorSlots() {
        ItemStack[] defaults = new ItemStack[41];
        defaults[2] = new ItemStack(Material.AIR);
        defaults[38] = new ItemStack(Material.AIR);
        defaults[5] = new ItemStack(Material.STONE, 16);
        manager.saveKit("test", new Kit("test", defaults));
        open();
        // Explicitly exercise the permitted Bukkit empty-slot representation.
        player.getInventory().setItem(2, null);
        player.getInventory().setItem(38, null);
        click(2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        tick();
        assertArrayEquals(defaults, players.getFFAPlayer(player).getPersonalKitContents("test"));
        assertRestored();
    }

    @Test void staleDeferredCleanup_doesNotEndANewSession() {
        open();
        click(6, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        assertRestored();
        open();
        tick();
        assertNotNull(player.getInventory().getItem(0), "Old cleanup must not restore over a newly opened session");
        assertEquals(Material.STONE, player.getInventory().getItem(0).getType());
        assertTrue(player.getItemOnCursor().getType().isAir());
        click(6, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        tick();
        assertRestored();
    }

    @Test void dragsAllowOnlyLowerInventory_andDamageCannotReleaseEditorCopies() {
        open();
        var lowerDrag = new InventoryDragEvent(player.getOpenInventory(), new ItemStack(Material.STONE, 15),
                new ItemStack(Material.STONE, 16), false, java.util.Map.of(9, new ItemStack(Material.STONE)));
        server.getPluginManager().callEvent(lowerDrag);
        assertFalse(lowerDrag.isCancelled());
        var topDrag = new InventoryDragEvent(player.getOpenInventory(), new ItemStack(Material.STONE, 15),
                new ItemStack(Material.STONE, 16), false, java.util.Map.of(0, new ItemStack(Material.STONE)));
        server.getPluginManager().callEvent(topDrag);
        assertTrue(topDrag.isCancelled());
        var damage = new org.bukkit.event.entity.EntityDamageEvent(player,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL, 10);
        server.getPluginManager().callEvent(damage);
        assertTrue(damage.isCancelled());
    }

    @Test void savingChecksCurrentDefinitionIncludingDeletion() {
        manager = spy(manager);
        open();
        manager.saveKit("test", new Kit("test", new ItemStack[]{new ItemStack(Material.APPLE)}));
        click(2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
        doReturn(null).when(manager).getKit("test");
        click(2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
        click(6, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        tick();
        assertRestored();
    }

    @Test void equipmentPreview_swapsOnlyTheActualEquipmentAndCannotBeCollected() {
        open();
        assertEquals(Material.DIAMOND_BOOTS, player.getOpenInventory().getTopInventory().getItem(0).getType());
        click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertNull(player.getInventory().getBoots());
        assertEquals(Material.DIAMOND_BOOTS, player.getItemOnCursor().getType());
        player.getInventory().setItem(2, player.getItemOnCursor().clone());
        player.setItemOnCursor(null);
        click(2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        tick();
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test")[36]);
        assertEquals(Material.DIAMOND_BOOTS, players.getFFAPlayer(player).getPersonalKitContents("test")[2].getType());
        assertRestored();
    }
}
