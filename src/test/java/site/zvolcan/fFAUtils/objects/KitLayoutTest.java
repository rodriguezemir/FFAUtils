package site.zvolcan.fFAUtils.objects;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

class KitLayoutTest {
    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void splitAndMerge_preserveTotalsIgnoringNullAndAir() {
        ItemStack[] original = new ItemStack[]{new ItemStack(Material.STONE, 32), new ItemStack(Material.STONE, 16)};
        ItemStack[] layout = new ItemStack[41];
        layout[4] = new ItemStack(Material.STONE, 24);
        layout[40] = new ItemStack(Material.STONE, 24);
        layout[3] = new ItemStack(Material.AIR);
        assertTrue(KitLayout.conservesItems(original, layout));
        layout[4].setAmount(48);
        layout[40] = null;
        assertTrue(KitLayout.conservesItems(original, layout));
        // Existing definitions may contain oversized stacks: conservation, not an extra size policy.
        original[0].setAmount(80);
        original[1] = null;
        layout[4].setAmount(80);
        assertTrue(KitLayout.conservesItems(original, layout));
    }

    @Test void missingExtraChangedMetadataAndInvalidStacks_areRejected() {
        ItemStack[] original = new ItemStack[]{new ItemStack(Material.STONE, 16)};
        ItemStack[] layout = KitLayout.copy(original);
        layout[0].setAmount(15);
        assertFalse(KitLayout.conservesItems(original, layout));
        layout[0].setAmount(17);
        assertFalse(KitLayout.conservesItems(original, layout));
        layout[0].setAmount(16);
        var meta = layout[0].getItemMeta();
        meta.displayName(Component.text("Changed"));
        layout[0].setItemMeta(meta);
        assertFalse(KitLayout.conservesItems(original, layout));
        assertFalse(KitLayout.conservesItems(original, new ItemStack[40]));
        layout = KitLayout.copy(original);
        layout[1] = new ItemStack(Material.DIAMOND);
        assertFalse(KitLayout.conservesItems(original, layout));
        layout[1] = null;
        layout[0].setAmount(0);
        assertFalse(KitLayout.conservesItems(original, layout));
        layout[0].setAmount(65);
        assertFalse(KitLayout.conservesItems(original, layout));
    }

    @Test void copies_preserveAllPositionsAndDetachMetadata() {
        ItemStack[] slots = new ItemStack[41];
        slots[3] = new ItemStack(Material.AIR);
        slots[36] = new ItemStack(Material.DIAMOND_BOOTS);
        slots[40] = new ItemStack(Material.SHIELD);
        ItemStack[] copy = KitLayout.copy(slots);
        assertArrayEquals(slots, copy);
        copy[36].setAmount(2);
        assertEquals(1, slots[36].getAmount());
        var meta = copy[40].getItemMeta();
        meta.displayName(Component.text("Copy only"));
        copy[40].setItemMeta(meta);
        assertNull(slots[40].getItemMeta().displayName());
        assertThrows(IllegalArgumentException.class, () -> KitLayout.copy(new ItemStack[42]));
    }

    @Test void fingerprints_captureSlotAmountAndMetadata_butNormalizeTrailingNulls() {
        ItemStack[] original = new ItemStack[]{new ItemStack(Material.STONE, 16)};
        String fingerprint = KitLayout.fingerprint(original);
        assertEquals(fingerprint, KitLayout.fingerprint(KitLayout.copy(original)));
        ItemStack[] changed = KitLayout.copy(original);
        changed[1] = changed[0];
        changed[0] = null;
        assertNotEquals(fingerprint, KitLayout.fingerprint(changed));
        changed = KitLayout.copy(original);
        changed[0].setAmount(15);
        assertNotEquals(fingerprint, KitLayout.fingerprint(changed));
        changed = KitLayout.copy(original);
        var meta = changed[0].getItemMeta();
        meta.displayName(Component.text("Changed"));
        changed[0].setItemMeta(meta);
        assertNotEquals(fingerprint, KitLayout.fingerprint(changed));
    }
}
