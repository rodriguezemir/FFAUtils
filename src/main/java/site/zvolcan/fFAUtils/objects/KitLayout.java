package site.zvolcan.fFAUtils.objects;

import com.google.gson.Gson;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Slot-preserving copies and the rearrangement-only contract for personal kits. */
public final class KitLayout {
    public static final int SLOT_COUNT = 41;

    private KitLayout() { }

    public static ItemStack[] copy(ItemStack[] contents) {
        if (contents.length > SLOT_COUNT) throw new IllegalArgumentException("Too many kit slots");
        ItemStack[] copy = new ItemStack[SLOT_COUNT];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    /** Amount is independent of identity; splitting and merging similar stacks is allowed. */
    public static boolean conservesItems(ItemStack[] definition, ItemStack[] layout) {
        if (layout == null || layout.length != SLOT_COUNT || definition.length > SLOT_COUNT) return false;
        List<ItemStack> identities = new ArrayList<>();
        List<Long> totals = new ArrayList<>();
        return accumulate(definition, identities, totals, 1)
                && accumulate(layout, identities, totals, -1)
                && totals.stream().allMatch(total -> total == 0);
    }

    private static boolean accumulate(ItemStack[] slots, List<ItemStack> identities, List<Long> totals, int sign) {
        for (ItemStack item : slots) {
            if (isEmpty(item)) continue;
            if (item.getAmount() <= 0) return false;
            int index = 0;
            while (index < identities.size() && !item.isSimilar(identities.get(index))) index++;
            if (index == identities.size()) {
                identities.add(item);
                totals.add(0L);
            }
            totals.set(index, totals.get(index) + (long) sign * item.getAmount());
        }
        return true;
    }

    /** Canonical Bukkit serialization: sorted maps, ordered slots/lists, explicit empty slots. */
    public static String fingerprint(ItemStack[] definition) {
        List<Object> slots = new ArrayList<>();
        for (ItemStack item : copy(definition)) slots.add(item == null ? null : canonical(item));
        try {
            byte[] data = new Gson().toJson(slots).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Object canonical(Object value) {
        if (value instanceof ConfigurationSerializable serializable) {
            Map<String, Object> map = new TreeMap<>(serializable.serialize());
            map.put(ConfigurationSerialization.SERIALIZED_TYPE_KEY,
                    ConfigurationSerialization.getAlias(serializable.getClass()));
            return canonical(map);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, element) -> sorted.put(key.toString(), canonical(element)));
            return sorted;
        }
        if (value instanceof Iterable<?> values) {
            List<Object> list = new ArrayList<>();
            values.forEach(element -> list.add(canonical(element)));
            return list;
        }
        return value;
    }
}
