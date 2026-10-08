package site.zvolcan.fFAUtils.objects;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FFAPlayer value object.
 */
class FFAPlayerTest {

    @Test
    void personalLayouts_areIsolatedAndDeepCopied() {
        FFAPlayer first = new FFAPlayer(UUID.randomUUID());
        FFAPlayer second = new FFAPlayer(UUID.randomUUID());
        org.bukkit.inventory.ItemStack item = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.bukkit.inventory.ItemStack copy = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(item.clone()).thenReturn(copy);
        org.mockito.Mockito.when(copy.clone()).thenAnswer(invocation -> org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class));
        org.bukkit.inventory.ItemStack[] contents = new org.bukkit.inventory.ItemStack[41];
        contents[40] = item;
        first.setPersonalKitContents("kit", contents, "version-1");
        contents[40] = null;

        assertNotNull(first.getPersonalKitContents("kit")[40]);
        assertNotSame(first.getPersonalKitContents("kit")[40], first.getPersonalKitContents("kit")[40]);
        var snapshot = first.getPersonalKitContents();
        snapshot.get("kit")[40] = null;
        assertNotNull(first.getPersonalKitContents("kit")[40]);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        assertNull(second.getPersonalKitContents("kit"));
        assertEquals("version-1", first.getPersonalKitFingerprint("kit"));
        first.removePersonalKitContents("kit");
        assertNull(first.getPersonalKitContents("kit"));
        assertNull(first.getPersonalKitFingerprint("kit"));
    }

    @Test
    void personalLayouts_cloneMutableItemsAndMetadataOnEveryBoundary() {
        org.mockbukkit.mockbukkit.MockBukkit.mock();
        try {
            FFAPlayer first = new FFAPlayer(UUID.randomUUID());
            FFAPlayer second = new FFAPlayer(UUID.randomUUID());
            var item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE, 12);
            var meta = item.getItemMeta();
            meta.displayName(net.kyori.adventure.text.Component.text("Original"));
            item.setItemMeta(meta);
            var contents = new org.bukkit.inventory.ItemStack[41];
            contents[0] = item;
            first.setPersonalKitContents("kit", contents, "v1");
            second.setPersonalKitContents("kit", contents, "v1");
            item.setAmount(1);
            var returned = first.getPersonalKitContents("kit")[0];
            returned.setAmount(2);
            meta.displayName(net.kyori.adventure.text.Component.text("Changed"));
            returned.setItemMeta(meta);
            assertEquals(12, first.getPersonalKitContents("kit")[0].getAmount());
            assertEquals(12, second.getPersonalKitContents("kit")[0].getAmount());
            assertEquals(net.kyori.adventure.text.Component.text("Original"),
                    first.getPersonalKitContents("kit")[0].getItemMeta().displayName());
            first.setPersonalKitContents("kit", contents, null);
            assertNull(first.getPersonalKitFingerprint("kit"));
            assertEquals("v1", second.getPersonalKitFingerprint("kit"));
        } finally {
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
        }
    }

    @Test
    void personalLayouts_preserveAllEmptySlotsAndValidateLength() {
        FFAPlayer player = new FFAPlayer(UUID.randomUUID());
        player.setPersonalKitContents("empty", new org.bukkit.inventory.ItemStack[41], null);
        assertEquals(41, player.getPersonalKitContents("empty").length);
        assertThrows(IllegalArgumentException.class,
                () -> player.setPersonalKitContents("bad", new org.bukkit.inventory.ItemStack[40], null));
    }

    @Test
    void killstreak_shouldDefaultToZero() {
        FFAPlayer player = new FFAPlayer(UUID.randomUUID());
        assertEquals(0, player.getKillstreak(), "New FFAPlayer must have killstreak = 0");
    }

    @Test
    void killstreak_shouldRoundtripGetterAndSetter() {
        FFAPlayer player = new FFAPlayer(UUID.randomUUID());
        player.setKillstreak(7);
        assertEquals(7, player.getKillstreak(), "killstreak set to 7 must be returned by getter");
    }

    @Test
    void killstreak_shouldResetToZero() {
        FFAPlayer player = new FFAPlayer(UUID.randomUUID());
        player.setKillstreak(10);
        player.setKillstreak(0);
        assertEquals(0, player.getKillstreak(), "After resetting to 0, killstreak must be 0");
    }
}
