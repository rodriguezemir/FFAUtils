package site.zvolcan.fFAUtils.managers;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.KitLayout;
import java.nio.file.Files;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KitManagerTest {
    @TempDir Path directory;
    KitManager manager;
    PlayersManager players;
    PlayerMock player;

    @BeforeEach void setUp() {
        var server = MockBukkit.mock();
        player = server.addPlayer();
        FFAUtils plugin = mock(FFAUtils.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("KitManagerTest"));
        players = new PlayersManager();
        when(plugin.getPlayersManager()).thenReturn(players);
        manager = new KitManager(plugin);
    }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void personalLayouts_areIndependentAndNeverRewriteGlobalFiles() throws Exception {
        ItemStack[] defaults = new ItemStack[41];
        defaults[0] = new ItemStack(Material.STONE, 16);
        defaults[1] = new ItemStack(Material.DIAMOND_HELMET);
        defaults[36] = new ItemStack(Material.DIAMOND_BOOTS);
        defaults[40] = new ItemStack(Material.SHIELD);
        Kit kit = new Kit("test", defaults);
        manager.saveKit("test", kit);
        byte[] original = Files.readAllBytes(directory.resolve("kits/test.json"));
        ItemStack[] personal = KitLayout.copy(defaults);
        personal[0] = null;
        personal[2] = new ItemStack(Material.STONE, 8);
        personal[35] = new ItemStack(Material.STONE, 8);
        personal[3] = new ItemStack(Material.AIR);
        assertTrue(manager.savePersonalLayout(player, "test", personal));
        PlayerMock other = MockBukkit.getMock().addPlayer();
        assertArrayEquals(defaults, manager.getEffectiveContents(other, "test"));
        assertArrayEquals(personal, manager.getEffectiveContents(player, "test"));
        var target = mock(org.bukkit.entity.Player.class);
        var targetInventory = mock(org.bukkit.inventory.PlayerInventory.class);
        when(target.getUniqueId()).thenReturn(player.getUniqueId());
        when(target.getInventory()).thenReturn(targetInventory);
        manager.applyKit(target, kit);
        var exact = org.mockito.ArgumentCaptor.forClass(ItemStack[].class);
        verify(targetInventory).setContents(exact.capture());
        assertArrayEquals(personal, exact.getValue(), "The API receives all 41 slots including AIR");
        verify(targetInventory, never()).setHelmet(any());
        manager.applyKit(player, kit);
        ItemStack[] normalized = KitLayout.copy(personal);
        normalized[3] = null; // MockBukkit normalizes AIR in actual player inventory.
        assertArrayEquals(normalized, java.util.Arrays.copyOf(player.getInventory().getContents(), 41));
        assertNull(player.getInventory().getHelmet(), "Personal helmet must remain in storage");
        player.getInventory().getItem(2).setAmount(1);
        assertEquals(8, manager.getEffectiveContents(player, "test")[2].getAmount());
        assertArrayEquals(defaults, kit.getContents());
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("kits/test.json")));
        manager.applyKit(other, kit);
        assertEquals(Material.DIAMOND_HELMET, other.getInventory().getHelmet().getType());
    }

    @Test void globalChanges_andCorruptionInvalidateOverrides_andOldObjectsResolveCurrentDefinition() {
        Kit old = new Kit("test", new ItemStack[]{new ItemStack(Material.STONE, 16)});
        manager.saveKit("test", old);
        ItemStack[] personal = KitLayout.copy(old.getContents());
        assertTrue(manager.savePersonalLayout(player, "test", personal));
        personal[2] = new ItemStack(Material.DIAMOND);
        players.getFFAPlayer(player).setPersonalKitContents("test", personal, KitLayout.fingerprint(old.getContents()));
        assertArrayEquals(KitLayout.copy(old.getContents()), manager.getEffectiveContents(player, "test"));
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
        assertTrue(manager.savePersonalLayout(player, "test", KitLayout.copy(old.getContents())));
        Kit changed = new Kit("test", new ItemStack[]{null, new ItemStack(Material.APPLE)});
        manager.saveKit("test", changed);
        manager.applyKit(player, old);
        assertArrayEquals(KitLayout.copy(changed.getContents()), java.util.Arrays.copyOf(player.getInventory().getContents(), 41));
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
        assertFalse(manager.savePersonalLayout(player, "test", KitLayout.copy(old.getContents())));
    }

    @Test void fingerprintsSurviveMetadataCodecRoundTripAndTrailingNullNormalization() {
        ItemStack[] defaults = new ItemStack[41];
        defaults[2] = new ItemStack(Material.AIR);
        defaults[8] = new ItemStack(Material.DIAMOND_SWORD);
        var meta = defaults[8].getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Sword"));
        ((org.bukkit.inventory.meta.Damageable) meta).setDamage(3);
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("ffautils", "test"),
                org.bukkit.persistence.PersistentDataType.STRING, "identity");
        defaults[8].setItemMeta(meta);
        manager.saveKit("test", new Kit("test", defaults));
        String fingerprint = KitLayout.fingerprint(defaults);
        var codec = new site.zvolcan.fFAUtils.objects.PlayerKitContentsCodec();
        assertEquals(fingerprint, KitLayout.fingerprint(codec.decode(codec.encode(defaults))));
        assertEquals(fingerprint, KitLayout.fingerprint(java.util.Arrays.copyOf(defaults, 9)));
    }

    @Test void slotOnlyGlobalChange_invalidatesEvenWhenItemTotalsStillMatch() {
        ItemStack[] defaults = new ItemStack[41];
        defaults[0] = new ItemStack(Material.STONE, 16);
        manager.saveKit("test", new Kit("test", defaults));
        assertTrue(manager.savePersonalLayout(player, "test", defaults));
        ItemStack[] moved = KitLayout.copy(defaults);
        moved[4] = moved[0];
        moved[0] = null;
        manager.saveKit("test", new Kit("test", moved));
        assertTrue(KitLayout.conservesItems(moved, defaults));
        assertArrayEquals(moved, manager.getEffectiveContents(player, "test"));
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
        players.getFFAPlayer(player).setPersonalKitContents("test", moved, null);
        assertArrayEquals(moved, manager.getEffectiveContents(player, "test"));
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
    }

    @Test void restoreDefault_removesOnlyTheRequestingPlayersOverride() {
        manager.saveKit("test", new Kit("test", new ItemStack[41]));
        PlayerMock other = MockBukkit.getMock().addPlayer();
        assertTrue(manager.savePersonalLayout(player, "test", new ItemStack[41]));
        assertTrue(manager.savePersonalLayout(other, "test", new ItemStack[41]));
        manager.restoreDefault(player, "test");
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("test"));
        assertNotNull(players.getFFAPlayer(other).getPersonalKitContents("test"));
        assertNotNull(manager.getKit("test"));
    }

    @Test void applyKit_doesNotTrustAnUnregisteredOldKitObject() {
        player.getInventory().setItem(0, new ItemStack(Material.APPLE));
        Kit removed = new Kit("removed", new ItemStack[]{new ItemStack(Material.DIAMOND)});
        players.getFFAPlayer(player).setPersonalKitContents("removed", new ItemStack[41], "old");
        manager.applyKit(player, removed);
        assertEquals(Material.APPLE, player.getInventory().getItem(0).getType());
        assertNull(players.getFFAPlayer(player).getPersonalKitContents("removed"));
    }
}
