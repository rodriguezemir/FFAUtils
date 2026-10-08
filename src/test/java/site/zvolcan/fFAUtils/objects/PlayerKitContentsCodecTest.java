package site.zvolcan.fFAUtils.objects;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PlayerKitContentsCodecTest {
    private final PlayerKitContentsCodec codec = new PlayerKitContentsCodec();

    @BeforeEach
    void setUp() { MockBukkit.mock(); }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void roundTrip_preservesEmptyAndAirSlots() {
        ItemStack[] slots = new ItemStack[41];
        assertArrayEquals(slots, codec.decode(codec.encode(slots)));
        slots[2] = new ItemStack(Material.AIR);
        slots[40] = new ItemStack(Material.AIR);
        assertArrayEquals(slots, codec.decode(codec.encode(slots)));
    }

    @Test
    void roundTrip_preservesMetadataArmorOffhandAndFullLayouts() {
        ItemStack[] slots = new ItemStack[41];
        Arrays.setAll(slots, i -> new ItemStack(Material.STONE, i + 1));
        slots[36] = new ItemStack(Material.DIAMOND_BOOTS);
        slots[39] = new ItemStack(Material.DIAMOND_HELMET);
        slots[40] = new ItemStack(Material.SHIELD);
        var meta = slots[40].getItemMeta();
        meta.displayName(Component.text("Personal shield"));
        meta.lore(java.util.List.of(Component.text("Custom lore")));
        ((org.bukkit.inventory.meta.Damageable) meta).setDamage(7);
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("ffautils", "test"),
                org.bukkit.persistence.PersistentDataType.STRING, "metadata");
        slots[40].setItemMeta(meta);
        assertArrayEquals(slots, codec.decode(codec.encode(slots)));
        slots[3] = null;
        slots[40] = null;
        assertArrayEquals(slots, codec.decode(codec.encode(slots)));
    }

    @Test
    void malformedPayloads_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new ItemStack[40]));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[]{1, 2, 3}));
        byte[] valid = codec.encode(new ItemStack[41]);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(valid, valid.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(Arrays.copyOf(valid, valid.length + 1)));
        byte[] badMarker = valid.clone();
        badMarker[8] = 3;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(badMarker));
        byte[] badVersion = valid.clone();
        badVersion[3]++;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(badVersion));
        byte[] badItemLength = valid.clone();
        badItemLength[8] = 2;
        java.util.Arrays.fill(badItemLength, 9, 13, (byte) 0x7f);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(badItemLength));
    }
}
