package site.zvolcan.fFAUtils.inventory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.KitLayout;
import site.zvolcan.fFAUtils.objects.Sounds;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static site.zvolcan.fFAUtils.inventory.KitEditorInventory.playClick;
import static site.zvolcan.fFAUtils.inventory.KitEditorInventory.text;

/**
 * Kit content editor built on raw Bukkit inventories - deliberately not a FastInv.
 * <p>
 * The player edits the kit with their OWN inventory. The chest opened on top only
 * carries Save / Cancel / Reset controls and non-transferable equipment previews.
 * The real inventory and cursor are snapshotted and restored on every exit path.
 * <p>
 * Serialisation order of {@link Kit#getContents()} is exactly
 * {@link PlayerInventory#getContents()}: slots 0-35 storage (0-8 hotbar, 9-35 main),
 * 36-39 armor (boots, leggings, chestplate, helmet) and 40 offhand. This is the same
 * layout {@code KitManager#applyKit} feeds back into {@code PlayerInventory#setContents},
 * so kits round-trip unchanged.
 */
public final class KitEditContentsInventory {

    private static final int CONTROL_SIZE = 9;
    private static final int SAVE_SLOT = 2;
    private static final int RESET_SLOT = 4;
    private static final int CANCEL_SLOT = 6;
    // Top row: boots, legs, SAVE, chest, RESET, helmet, CANCEL, blank, offhand.
    private static final Map<Integer, Integer> EQUIPMENT = Map.of(0, 36, 1, 37, 3, 38, 5, 39, 8, 40);

    private static final String SAVE_SKIN_URL = "http://textures.minecraft.net/texture/925b8eed5c565bd440ec47c79c20d5cf370162b1d9b5dd3100ed6283fe01d6e";
    private static final String RESET_SKIN_URL = "http://textures.minecraft.net/texture/a89b93fd616ed3670ccf647a0f9380398c0d4615634f2deff46c6edbdc712885";
    private static final String CANCEL_SKIN_URL = "http://textures.minecraft.net/texture/68d40935279771adc63936ed9c8463abdf5c5ba78d2e86cb1ec10b4d1d225fb";

    /** Active editing sessions keyed by player UUID */
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private KitEditContentsInventory() {
    }

    /** Snapshots the player's inventory, loads the kit into it and opens the control chest */
    public static void open(@NotNull FFAUtils plugin, @NotNull KitManager kitManager, @NotNull Player player,
            @NotNull String kitName) {
        // A player already editing must not be snapshotted twice - that would capture kit items.
        if (SESSIONS.containsKey(player.getUniqueId())) {
            return;
        }

        if (kitManager.getKit(kitName) == null) return;
        ItemStack[] snapshot = deepCopy(player.getInventory().getContents());
        ItemStack cursor = player.getItemOnCursor().clone();

        Inventory controls = Bukkit.createInventory(new ControlHolder(), CONTROL_SIZE, "Your layout: " + kitName);
        controls.setItem(SAVE_SLOT, button(SAVE_SKIN_URL, "<green>Save</green>",
                "<gray>Save your personal rearrangement only</gray>"));
        controls.setItem(RESET_SLOT, button(RESET_SKIN_URL, "<aqua>Reset</aqua>",
                "<gray>Reload the saved kit, discarding edits</gray>"));
        controls.setItem(CANCEL_SLOT, button(CANCEL_SKIN_URL, "<red>Cancel</red>",
                "<gray>Discard changes and restore your items</gray>"));

        Session session = new Session(plugin, kitManager, kitName, snapshot, cursor, controls);
        player.setItemOnCursor(null);
        SESSIONS.put(player.getUniqueId(), session);
        player.openInventory(controls);
        if (player.getOpenInventory().getTopInventory() != controls) {
            endSession(player);
            return;
        }
        session.effectiveSlots = loadKitInto(kitManager, player, kitName);
        session.refreshEquipment(player);
    }

    /** Restores every open session - used on plugin disable */
    public static void restoreAllSessions() {
        for (UUID uuid : new ArrayList<>(SESSIONS.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            Session session = SESSIONS.remove(uuid);
            if (player != null && session != null) {
                player.setItemOnCursor(null);
                player.closeInventory();
                session.restore(player);
            }
        }
    }

    /** Removes and restores a session; a no-op when it was already ended (idempotent) */
    private static void endSession(@NotNull Player player) {
        Session session = SESSIONS.remove(player.getUniqueId());
        if (session != null) {
            player.setItemOnCursor(null);
            player.closeInventory();
            session.restore(player);
        }
    }

    private static void endSession(Player player, Session expected) {
        if (SESSIONS.get(player.getUniqueId()) == expected) endSession(player);
    }

    /** Wipes the player's inventory and loads the saved kit into it */
    private static ItemStack[] loadKitInto(@NotNull KitManager kitManager, @NotNull Player player, @NotNull String kitName) {
        player.setItemOnCursor(null);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();

        ItemStack[] effective = kitManager.getEffectiveContents(player, kitName);
        Kit kit = kitManager.getKit(kitName);
        if (kit != null) kitManager.applyKit(player, kit);
        return effective == null ? new ItemStack[KitLayout.SLOT_COUNT] : effective;
    }

    private static ItemStack[] deepCopy(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    /** Builds a player-head control button wearing the skin fetched from {@code textureUrl} */
    private static ItemStack button(String textureUrl, String name, String lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL(textureUrl));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid skin URL: " + textureUrl, e);
        }
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);

        meta.displayName(text(name));
        List<net.kyori.adventure.text.Component> loreLines = new ArrayList<>();
        loreLines.add(text(lore));
        meta.lore(loreLines);
        item.setItemMeta(meta);
        return item;
    }

    /** Marks the control inventory so the listener can recognise it */
    private static final class ControlHolder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException("Control inventory is tracked by the open view");
        }
    }

    /** One player's editing state: the kit being edited plus their untouched real inventory */
    private static final class Session {

        private final FFAUtils plugin;
        private final KitManager kitManager;
        private final String kitName;
        private final ItemStack[] snapshot;
        private final ItemStack cursor;
        private final Inventory controls;
        private boolean finishing;
        private ItemStack[] effectiveSlots;

        private Session(FFAUtils plugin, KitManager kitManager, String kitName, ItemStack[] snapshot,
                        ItemStack cursor, Inventory controls) {
            this.plugin = plugin;
            this.kitManager = kitManager;
            this.kitName = kitName;
            this.snapshot = snapshot;
            this.cursor = cursor;
            this.controls = controls;
        }

        private void refreshEquipment(Player player) {
            EQUIPMENT.forEach((control, slot) -> {
                ItemStack item = player.getInventory().getItem(slot);
                controls.setItem(control, item == null ? null : item.clone());
            });
        }

        private void restore(@NotNull Player player) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            controls.clear();
            inventory.setContents(deepCopy(snapshot));
            player.setItemOnCursor(cursor.clone());
        }
    }

    /** Handles the control buttons and every exit path of an editing session */
    public static final class SessionListener implements Listener {

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            Session session = SESSIONS.get(player.getUniqueId());
            if (session == null) {
                return;
            }

            Inventory clicked = event.getClickedInventory();
            if (session.finishing || event instanceof InventoryCreativeEvent
                    || event.getInventory() != session.controls || clicked == null
                    || event.getClick().isCreativeAction() || event.getClick().isKeyboardClick()
                    || event.isShiftClick() || event.getClick() == org.bukkit.event.inventory.ClickType.DROP
                    || event.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP
                    || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                    || event.getAction() == InventoryAction.UNKNOWN) {
                event.setCancelled(true);
                return;
            }
            if (clicked == player.getInventory()) return;
            event.setCancelled(true);
            Integer equipmentSlot = EQUIPMENT.get(event.getSlot());
            if (equipmentSlot != null) {
                swapEquipment(event, player, session, equipmentSlot);
                return;
            }
            switch (event.getSlot()) {
                case SAVE_SLOT -> save(session, player);
                case RESET_SLOT -> reset(session, player);
                case CANCEL_SLOT -> cancel(session, player);
                default -> {
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDrag(InventoryDragEvent event) {
            Session session = SESSIONS.get(event.getWhoClicked().getUniqueId());
            if (session == null) return;
            if (session.finishing || event.getInventory() != session.controls) {
                event.setCancelled(true);
                return;
            }
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < CONTROL_SIZE) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            if (!(event.getInventory().getHolder() instanceof ControlHolder)) {
                return;
            }
            HumanEntity human = event.getPlayer();
            if (human instanceof Player player) {
                Session session = SESSIONS.get(player.getUniqueId());
                if (session == null || session.finishing) return;
                // Vanilla returns/drops the cursor after this event: clear editor copies now,
                // and restore the original cursor only after the close completes.
                player.setItemOnCursor(null);
                session.finishing = true;
                Bukkit.getScheduler().runTask(session.plugin, () -> endSession(player, session));
            }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onQuit(PlayerQuitEvent event) {
            endSession(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDrop(PlayerDropItemEvent event) {
            if (SESSIONS.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onPickup(EntityPickupItemEvent event) {
            if (SESSIONS.containsKey(event.getEntity().getUniqueId())) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onOpen(InventoryOpenEvent event) {
            Session session = SESSIONS.get(event.getPlayer().getUniqueId());
            if (session != null && event.getInventory() != session.controls) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onUse(PlayerInteractEvent event) {
            if (SESSIONS.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onUseEntity(PlayerInteractEntityEvent event) {
            if (SESSIONS.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onConsume(PlayerItemConsumeEvent event) {
            if (SESSIONS.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onSwap(PlayerSwapHandItemsEvent event) {
            if (SESSIONS.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onCommand(PlayerCommandPreprocessEvent event) {
            if (SESSIONS.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDamage(EntityDamageEvent event) {
            if (SESSIONS.containsKey(event.getEntity().getUniqueId())
                    || (event instanceof EntityDamageByEntityEvent damage
                    && SESSIONS.containsKey(damage.getDamager().getUniqueId()))) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDeath(PlayerDeathEvent event) {
            if (!SESSIONS.containsKey(event.getEntity().getUniqueId())) return;
            event.getDrops().clear();
            event.setKeepInventory(true);
            endSession(event.getEntity());
        }

        private void swapEquipment(InventoryClickEvent event, Player player, Session session, int slot) {
            if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                    && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) return;
            ItemStack cursor = player.getItemOnCursor();
            ItemStack item = player.getInventory().getItem(slot);
            // Explicit swap: preview copies in the top inventory are never transferred.
            player.getInventory().setItem(slot, KitLayout.isEmpty(cursor) ? null : cursor.clone());
            player.setItemOnCursor(item == null ? null : item.clone());
            session.refreshEquipment(player);
        }

        private void save(Session session, Player player) {
            playClick(player);
            ItemStack[] contents = deepCopy(java.util.Arrays.copyOf(
                    player.getInventory().getContents(), KitLayout.SLOT_COUNT));
            // Bukkit may represent AIR as null. Retain unchanged explicit empty markers.
            for (int i = 0; i < contents.length; i++) {
                ItemStack original = session.effectiveSlots[i];
                if (KitLayout.isEmpty(contents[i]) && original != null && original.getType().isAir()) {
                    contents[i] = original.clone();
                }
            }
            if (!KitLayout.isEmpty(player.getItemOnCursor())
                    || !session.kitManager.savePersonalLayout(player, session.kitName, contents)) {
                session.plugin.getUtils().message(player, Sounds.ERROR_SOUND,
                        "<red>Keep all original kit items unchanged and place cursor items in a slot before saving.</red>");
                return;
            }

            // Guard the session until next-tick close/restore; no more editing is allowed.
            finish(session, player, Sounds.SUCCESS_SOUND,
                    "<green>Your layout for <white>" + session.kitName + "</white> saved.</green>");
        }

        private void cancel(Session session, Player player) {
            playClick(player);
            finish(session, player, Sounds.ERROR_SOUND, "<yellow>Kit editing cancelled.</yellow>");
        }

        private void reset(Session session, Player player) {
            playClick(player);
            session.effectiveSlots = loadKitInto(session.kitManager, player, session.kitName);
            session.refreshEquipment(player);
            session.plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                    "<gray>Reloaded the saved contents of <white>" + session.kitName + "</white>.</gray>");
        }

        /** Defers closing/restoring until outside the inventory-click transaction. */
        private void finish(Session session, Player player, net.kyori.adventure.sound.Sound sound, String message) {
            session.finishing = true;
            session.plugin.getUtils().message(player, sound, message);
            Bukkit.getScheduler().runTask(session.plugin, () -> endSession(player, session));
        }
    }
}
