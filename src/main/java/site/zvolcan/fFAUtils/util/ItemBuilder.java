package site.zvolcan.fFAUtils.util;

import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;

/** Minimal fluent item builder: MiniMessage names and lore with italic decoration removed. */
public final class ItemBuilder {

    private final ItemStack item;
    private final PluginUtils utils;

    ItemBuilder(ItemStack item, PluginUtils utils) {
        this.item = item;
        this.utils = utils;
    }

    public ItemBuilder name(String name) {
        item.editMeta(meta -> meta.displayName(
                utils.chat(name).decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)));
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemStack build() {
        return item;
    }
}
