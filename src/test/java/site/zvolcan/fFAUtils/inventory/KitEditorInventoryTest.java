package site.zvolcan.fFAUtils.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import site.zvolcan.fFAUtils.objects.Kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KitEditorInventoryTest {

    @Test
    void countItems_ignoresEmptySlotsAndAirStacks() {
        ItemStack air = mock(ItemStack.class);
        when(air.getType()).thenReturn(Material.AIR);
        ItemStack stone = mock(ItemStack.class);
        when(stone.getType()).thenReturn(Material.STONE);
        Kit kit = new Kit("test", new ItemStack[]{null, air, stone});

        assertEquals(1, KitEditorInventory.countItems(kit));
    }

    @Test
    void openingList_countsTheViewingPlayersEffectiveSplitStacks() {
        var server = org.mockbukkit.mockbukkit.MockBukkit.mock();
        var registrar = org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin();
        try {
            fr.mrmicky.fastinv.FastInvManager.register(registrar);
            var manager = mock(site.zvolcan.fFAUtils.managers.KitManager.class);
            var kit = new Kit("test", new ItemStack[]{new ItemStack(Material.STONE, 16)});
            when(manager.getAllKits()).thenReturn(java.util.Map.of("test", kit));
            var first = server.addPlayer();
            var second = server.addPlayer();
            ItemStack[] split = new ItemStack[41];
            split[3] = new ItemStack(Material.STONE, 8);
            split[4] = new ItemStack(Material.STONE, 8);
            when(manager.getEffectiveContents(first, "test")).thenReturn(split);
            when(manager.getEffectiveContents(second, "test")).thenReturn(kit.getContents());
            var list = new KitEditorInventory(manager, 0);
            list.open(first);
            assertEquals(java.util.List.of(KitEditorInventory.text("<gray>2 items in your effective layout</gray>"),
                    KitEditorInventory.text("<yellow>Click to edit your layout</yellow>")),
                    list.getInventory().getItem(0).getItemMeta().lore());
            first.closeInventory();
            list.open(second);
            assertEquals(KitEditorInventory.text("<gray>1 items in your effective layout</gray>"),
                    list.getInventory().getItem(0).getItemMeta().lore().getFirst());
        } finally {
            server.getPluginManager().callEvent(new org.bukkit.event.server.PluginDisableEvent(registrar));
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
        }
    }

    @Test
    void plainText_doesNotInterpretKitNamesAsMiniMessage() {
        assertEquals(Component.text("<red>admin</red>").decoration(TextDecoration.ITALIC, false),
                KitEditorInventory.plainText("<red>admin</red>"));
    }
}
