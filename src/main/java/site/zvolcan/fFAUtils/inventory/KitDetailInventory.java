package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.Sounds;

import java.util.ArrayList;
import java.util.List;

import static site.zvolcan.fFAUtils.inventory.KitEditorInventory.countItems;
import static site.zvolcan.fFAUtils.inventory.KitEditorInventory.plainText;
import static site.zvolcan.fFAUtils.inventory.KitEditorInventory.playClick;
import static site.zvolcan.fFAUtils.inventory.KitEditorInventory.text;

/** Detail view of the viewing player's layout, never shared-definition management. */
public class KitDetailInventory extends FastInv {

    private static final int SUMMARY_SLOT = 13;
    private static final int EDIT_SLOT = 11;
    private static final int RESTORE_SLOT = 15;
    private static final int BACK_SLOT = 22;

    private final KitManager kitManager;
    private final String kitName;

    public KitDetailInventory(KitManager kitManager, String kitName, int returnPage) {
        super(27, "Your kit: " + kitName);
        this.kitManager = kitManager;
        this.kitName = kitName;

        Kit kit = kitManager.getKit(kitName);

        ItemStack summary = new ItemStack(Material.CHEST);
        ItemMeta summaryMeta = summary.getItemMeta();
        summaryMeta.displayName(plainText(kitName));
        List<Component> summaryLore = new ArrayList<>();
        summaryLore.add(text("<gray>" + (kit == null ? 0 : countItems(kit)) + " items</gray>"));
        summaryMeta.lore(summaryLore);
        summary.setItemMeta(summaryMeta);
        setItem(SUMMARY_SLOT, summary);

        ItemStack edit = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta editMeta = edit.getItemMeta();
        editMeta.displayName(text("<green>Edit your layout</green>"));
        List<Component> editLore = new ArrayList<>();
        editLore.add(text("<gray>Rearrange your items; shared kit stays unchanged</gray>"));
        editMeta.lore(editLore);
        edit.setItemMeta(editMeta);
        setItem(EDIT_SLOT, edit, e -> {
            Player clicker = (Player) e.getWhoClicked();
            if (kitManager.getKit(kitName) == null) {
                FFAUtils.getInstance().getUtils().message(clicker, Sounds.ERROR_SOUND,
                        "<red>This kit no longer exists.</red>");
                new KitEditorInventory(kitManager, returnPage).open(clicker);
                return;
            }
            playClick(clicker);
            KitEditContentsInventory.open(FFAUtils.getInstance(), kitManager, clicker, kitName);
        });

        ItemStack restore = new ItemStack(Material.TNT);
        ItemMeta restoreMeta = restore.getItemMeta();
        restoreMeta.displayName(text("<red>Restore default</red>"));
        restoreMeta.lore(List.of(text("<gray>Shift-click to remove only your saved layout</gray>")));
        restore.setItemMeta(restoreMeta);
        setItem(RESTORE_SLOT, restore, e -> {
            Player clicker = (Player) e.getWhoClicked();
            // Confirm removal of this player's override, not the shared definition.
            if (!e.isShiftClick()) {
                FFAUtils.getInstance().getUtils().message(clicker, Sounds.ERROR_SOUND,
                        "<yellow>Shift-click to restore your default layout.</yellow>");
                return;
            }
            playClick(clicker);
            kitManager.restoreDefault(clicker, kitName);
            FFAUtils.getInstance().getUtils().message(clicker, Sounds.SUCCESS_SOUND,
                    "<green>Your default layout for <white>" + kitName + "</white> restored.</green>");
            new KitEditorInventory(kitManager, returnPage).open(clicker);
        });

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(text("<gray>Back</gray>"));
        back.setItemMeta(backMeta);
        setItem(BACK_SLOT, back, e -> {
            Player clicker = (Player) e.getWhoClicked();
            playClick(clicker);
            new KitEditorInventory(kitManager, returnPage).open(clicker);
        });
    }

    @Override
    protected void onOpen(InventoryOpenEvent event) {
        Player player = (Player) event.getPlayer();
        ItemStack[] contents = kitManager.getEffectiveContents(player, kitName);
        ItemStack summary = getInventory().getItem(SUMMARY_SLOT);
        ItemMeta meta = summary.getItemMeta();
        meta.lore(List.of(text("<gray>" + (contents == null ? 0 : KitEditorInventory.countItems(contents))
                + " items in your effective layout</gray>")));
        summary.setItemMeta(meta);
    }
}
