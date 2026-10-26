package site.zvolcan.fFAUtils.support;

import be.seeseemelk.mockbukkit.AsyncCatcher;
import be.seeseemelk.mockbukkit.MockUnsafeValues;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import be.seeseemelk.mockbukkit.inventory.ItemFactoryMock;
import be.seeseemelk.mockbukkit.inventory.PlayerInventoryViewMock;
import be.seeseemelk.mockbukkit.inventory.meta.BookMetaMock;
import be.seeseemelk.mockbukkit.inventory.meta.ItemMetaMock;
import be.seeseemelk.mockbukkit.inventory.meta.SkullMetaMock;
import be.seeseemelk.mockbukkit.profile.PlayerProfileMock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MockBukkit 1.18.2 harness for the plugin's real server API surface.
 *
 * <p>Four operations the plugin depends on are implemented by newer MockBukkit releases but throw
 * {@code UnimplementedOperationException} in {@code MockBukkit-v1.18:2.85.2} (or, for the inventory
 * open, behave unlike a server). That exception extends {@code TestAbortedException}, so a test that
 * reaches one of them is reported as skipped instead of failing. This server fills exactly those gaps
 * and nothing else, so the assertions keep their meaning:
 *
 * <ul>
 *   <li>{@code ItemMeta.displayName(Component)} / {@code ItemMeta.lore(List<Component>)} — the v1.18
 *       {@code ItemMetaMock} only knows the legacy String API. Components are stored as Gson JSON in
 *       those same legacy fields, which is what newer MockBukkit does.</li>
 *   <li>{@code UnsafeValues.serializeItem/deserializeItem} — the v1.18 {@code MockUnsafeValues}
 *       throws, which breaks {@code ItemStack.serializeAsBytes()} and therefore the personal-kit
 *       codec. An item is serialized as type + amount + its {@code ItemMeta.serialize()} snapshot,
 *       which keeps every meta field (display name, lore, damage, persistent data) intact.</li>
 *   <li>{@code PlayerProfile.getTextures()} / {@code SkullMeta.setOwnerProfile(...)} — needed to
 *       build the player-head editor buttons.</li>
 *   <li>{@code HumanEntity.openInventory(Inventory)} — the v1.18 player mock assigns the view without
 *       firing {@code InventoryOpenEvent}, so neither {@code FastInv#onOpen} nor a listener that
 *       rejects a second open ever runs. Players created here fire the event and honour its
 *       cancellation, like newer MockBukkit does.</li>
 * </ul>
 *
 * <p>Use it with {@code MockBukkit.mock(new MockBukkit18ServerMock())}; tests that do not reach those
 * APIs keep using plain {@code MockBukkit.mock()}.
 */
public class MockBukkit18ServerMock extends ServerMock {
    private final ItemFactory itemFactory = new AdventureItemFactory();
    private final MockUnsafeValues unsafeValues = new ItemSerializingUnsafeValues();

    @Override
    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    @Override
    public MockUnsafeValues getUnsafe() {
        return unsafeValues;
    }

    @Override
    public PlayerProfileMock createProfile(UUID id) {
        return new TexturedPlayerProfileMock(null, id);
    }

    @Override
    public PlayerProfileMock createProfile(String name) {
        return new TexturedPlayerProfileMock(name, null);
    }

    @Override
    public PlayerProfileMock createProfile(UUID id, String name) {
        return new TexturedPlayerProfileMock(name, id);
    }

    @Override
    public PlayerMock addPlayer() {
        return addPlayer("Player" + nextPlayerNumber++);
    }

    @Override
    public PlayerMock addPlayer(String name) {
        MockBukkit18PlayerMock player = new MockBukkit18PlayerMock(this, name);
        addPlayer(player);
        return player;
    }

    private int nextPlayerNumber;

    /** Player that fires {@code InventoryOpenEvent} when an inventory opens, as a real server does. */
    private static final class MockBukkit18PlayerMock extends PlayerMock {
        MockBukkit18PlayerMock(ServerMock server, String name) {
            super(server, name);
        }

        @Override
        public InventoryView openInventory(Inventory inventory) {
            AsyncCatcher.catchOp("open inventory");
            if (inventory == null) throw new IllegalArgumentException("Inventory cannot be null");
            closeInventory();
            PlayerInventoryViewMock view = new PlayerInventoryViewMock(this, inventory);
            InventoryOpenEvent event = new InventoryOpenEvent(view);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) super.openInventory(view);
            return getOpenInventory();
        }
    }

    /**
     * Component conversion shared by the meta mocks.
     *
     * <p>Deliberate limitation of these mocks: only the Adventure getters/setters are bridged. The
     * legacy setters normalize their input to Gson JSON because the mock keeps a single String field,
     * and the legacy getters keep returning that stored value, exactly like MockBukkit does before it
     * learned the Adventure API. Nothing in the plugin reads the legacy display-name/lore getters.
     */
    private static final class AdventureText {
        private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
        private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

        static Component decode(String raw) {
            return raw == null ? null : GSON.deserialize(raw);
        }

        static String encode(Component component) {
            return component == null ? null : GSON.serialize(component);
        }

        static String encodeLegacy(String legacy) {
            return legacy == null ? null : GSON.serialize(LEGACY.deserialize(legacy));
        }

        static List<Component> decodeAll(List<String> raw) {
            if (raw == null) return null;
            List<Component> components = new ArrayList<>(raw.size());
            for (String line : raw) components.add(GSON.deserialize(line));
            return components;
        }

        static List<String> encodeAll(List<Component> components) {
            if (components == null) return null;
            List<String> lines = new ArrayList<>(components.size());
            for (Component line : components) lines.add(GSON.serialize(line));
            return lines;
        }

        static List<String> encodeLegacy(List<String> legacy) {
            if (legacy == null) return null;
            List<String> lines = new ArrayList<>(legacy.size());
            for (String line : legacy) lines.add(encodeLegacy(line));
            return lines;
        }
    }

    /** Adventure display name/lore on top of the stock item meta mock. */
    private static final class AdventureItemMetaMock extends ItemMetaMock {
        AdventureItemMetaMock() {
            super();
        }

        AdventureItemMetaMock(ItemMeta source) {
            super(source);
        }

        @Override
        public Component displayName() {
            return AdventureText.decode(super.getDisplayName());
        }

        @Override
        public void displayName(Component component) {
            super.setDisplayName(AdventureText.encode(component));
        }

        @Override
        public void setDisplayName(String name) {
            super.setDisplayName(AdventureText.encodeLegacy(name));
        }

        @Override
        public List<Component> lore() {
            return AdventureText.decodeAll(super.getLore());
        }

        @Override
        public void lore(List<Component> lore) {
            super.setLore(AdventureText.encodeAll(lore));
        }

        @Override
        public void setLore(List<String> lore) {
            super.setLore(AdventureText.encodeLegacy(lore));
        }
    }

    /** Same as {@link AdventureItemMetaMock}, for the materials whose stock mock is a skull meta. */
    private static final class AdventureSkullMetaMock extends SkullMetaMock {
        private PlayerProfile ownerProfile;

        AdventureSkullMetaMock() {
            super();
        }

        AdventureSkullMetaMock(SkullMeta source) {
            super(source);
        }

        @Override
        public Component displayName() {
            return AdventureText.decode(super.getDisplayName());
        }

        @Override
        public void displayName(Component component) {
            super.setDisplayName(AdventureText.encode(component));
        }

        @Override
        public void setDisplayName(String name) {
            super.setDisplayName(AdventureText.encodeLegacy(name));
        }

        @Override
        public List<Component> lore() {
            return AdventureText.decodeAll(super.getLore());
        }

        @Override
        public void lore(List<Component> lore) {
            super.setLore(AdventureText.encodeAll(lore));
        }

        @Override
        public void setLore(List<String> lore) {
            super.setLore(AdventureText.encodeLegacy(lore));
        }

        @Override
        public PlayerProfile getOwnerProfile() {
            return ownerProfile;
        }

        @Override
        public void setOwnerProfile(PlayerProfile profile) {
            this.ownerProfile = profile;
            super.setOwner(profile == null ? null : profile.getName());
        }
    }

    /** Same as {@link AdventureItemMetaMock}, for the materials whose stock mock is a book meta. */
    private static final class AdventureBookMetaMock extends BookMetaMock {
        AdventureBookMetaMock() {
            super();
        }

        AdventureBookMetaMock(BookMeta source) {
            super(source);
        }

        @Override
        public Component displayName() {
            return AdventureText.decode(super.getDisplayName());
        }

        @Override
        public void displayName(Component component) {
            super.setDisplayName(AdventureText.encode(component));
        }

        @Override
        public void setDisplayName(String name) {
            super.setDisplayName(AdventureText.encodeLegacy(name));
        }

        @Override
        public List<Component> lore() {
            return AdventureText.decodeAll(super.getLore());
        }

        @Override
        public void lore(List<Component> lore) {
            super.setLore(AdventureText.encodeAll(lore));
        }

        @Override
        public void setLore(List<String> lore) {
            super.setLore(AdventureText.encodeLegacy(lore));
        }
    }

    /**
     * Hands out the Adventure-aware meta mocks and keeps them through {@code ItemStack.setItemMeta}.
     * Every other meta kind keeps the stock behaviour.
     */
    private static final class AdventureItemFactory extends ItemFactoryMock {
        @Override
        public ItemMeta getItemMeta(Material material) {
            return adventure(super.getItemMeta(material));
        }

        @Override
        public ItemMeta asMetaFor(ItemMeta meta, Material material) {
            return adventure(super.asMetaFor(meta, material));
        }

        @Override
        public ItemMeta asMetaFor(ItemMeta meta, ItemStack stack) {
            return adventure(super.asMetaFor(meta, stack));
        }

        private static ItemMeta adventure(ItemMeta meta) {
            Class<?> type = meta.getClass();
            if (type == ItemMetaMock.class) return new AdventureItemMetaMock(meta);
            if (type == SkullMetaMock.class) return new AdventureSkullMetaMock((SkullMeta) meta);
            if (type == BookMetaMock.class) return new AdventureBookMetaMock((BookMeta) meta);
            return meta;
        }
    }

    /** Byte serialization of items, standing in for the server's own item NBT writer. */
    private static final class ItemSerializingUnsafeValues extends MockUnsafeValues {
        @Override
        public byte[] serializeItem(ItemStack item) {
            if (item == null) throw new IllegalArgumentException("null cannot be serialized");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", item.getType().name());
            data.put("amount", item.getAmount());
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
            if (meta != null) data.put("meta", new HashMap<>(meta.serialize()));
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                 ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(new HashMap<>(data));
                return bytes.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("Cannot serialize item", e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public ItemStack deserializeItem(byte[] data) {
            if (data == null) throw new IllegalArgumentException("null cannot be deserialized");
            if (data.length == 0) throw new IllegalArgumentException("cannot deserialize nothing");
            Map<String, Object> read;
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
                read = (Map<String, Object>) in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException("Cannot deserialize item", e);
            }
            Material type = Material.valueOf((String) read.get("type"));
            ItemStack item = new ItemStack(type, (Integer) read.get("amount"));
            Object metaData = read.get("meta");
            if (metaData instanceof Map<?, ?> metaMap) {
                ItemMeta restored = ItemMetaMock.deserialize(new HashMap<>((Map<String, Object>) metaMap));
                item.setItemMeta(org.bukkit.Bukkit.getItemFactory().asMetaFor(restored, type));
            }
            return item;
        }
    }

    /** Profile that can carry the skin textures the editor buttons need. */
    private static final class TexturedPlayerProfileMock extends PlayerProfileMock {
        private PlayerTextures textures = new SimplePlayerTextures();

        TexturedPlayerProfileMock(String name, UUID id) {
            super(name, id);
        }

        @Override
        public PlayerTextures getTextures() {
            return textures;
        }

        @Override
        public void setTextures(PlayerTextures textures) {
            this.textures = textures;
        }
    }

    /** Minimal {@link PlayerTextures} holder. */
    private static final class SimplePlayerTextures implements PlayerTextures {
        private URL skin;
        private URL cape;
        private SkinModel skinModel = SkinModel.CLASSIC;

        @Override
        public boolean isEmpty() {
            return skin == null && cape == null;
        }

        @Override
        public void clear() {
            skin = null;
            cape = null;
        }

        @Override
        public URL getSkin() {
            return skin;
        }

        @Override
        public void setSkin(URL skinUrl) {
            this.skin = skinUrl;
        }

        @Override
        public void setSkin(URL skinUrl, SkinModel model) {
            this.skin = skinUrl;
            this.skinModel = model;
        }

        @Override
        public SkinModel getSkinModel() {
            return skinModel;
        }

        @Override
        public URL getCape() {
            return cape;
        }

        @Override
        public void setCape(URL capeUrl) {
            this.cape = capeUrl;
        }

        @Override
        public long getTimestamp() {
            return 0L;
        }

        @Override
        public boolean isSigned() {
            return false;
        }
    }
}
