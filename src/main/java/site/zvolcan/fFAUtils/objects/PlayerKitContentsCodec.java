package site.zvolcan.fFAUtils.objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.*;

/** Versioned slot framing around Paper's item bytes, never Java object deserialization. */
public final class PlayerKitContentsCodec {
    public static final int SLOT_COUNT = 41;
    private static final int MAGIC = 0x46464B31; // FFK1
    private static final int MAX_ITEM_BYTES = 1024 * 1024;

    public byte[] encode(ItemStack[] contents) {
        if (contents == null || contents.length != SLOT_COUNT) {
            throw new IllegalArgumentException("Expected exactly 41 slots");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeInt(SLOT_COUNT);
                for (ItemStack item : contents) {
                    if (item == null) {
                        out.writeByte(0);
                    } else if (item.getType().isAir()) {
                        // Paper's item encoding normalizes empty items; retain AIR versus null explicitly.
                        out.writeByte(1);
                        out.writeUTF(item.getType().name());
                        out.writeInt(item.getAmount());
                    } else {
                        out.writeByte(2);
                        byte[] itemBytes = item.serializeAsBytes();
                        if (itemBytes.length == 0 || itemBytes.length > MAX_ITEM_BYTES) {
                            throw new IllegalArgumentException("Invalid item payload length");
                        }
                        out.writeInt(itemBytes.length);
                        out.write(itemBytes);
                    }
                }
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot encode personal kit", e);
        }
    }

    public ItemStack[] decode(byte[] bytes) {
        if (bytes == null || bytes.length > SLOT_COUNT * (MAX_ITEM_BYTES + 5) + 8) {
            throw new IllegalArgumentException("Invalid personal kit payload");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readInt() != SLOT_COUNT) {
                throw new IllegalArgumentException("Unsupported personal kit format");
            }
            ItemStack[] contents = new ItemStack[SLOT_COUNT];
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                switch (in.readUnsignedByte()) {
                    case 0 -> { }
                    case 1 -> {
                        Material material = Material.valueOf(in.readUTF());
                        if (!material.isAir()) throw new IllegalArgumentException("Invalid AIR marker");
                        contents[slot] = new ItemStack(material, in.readInt());
                    }
                    case 2 -> {
                        int length = in.readInt();
                        if (length <= 0 || length > MAX_ITEM_BYTES || length > in.available()) {
                            throw new IllegalArgumentException("Invalid item payload length");
                        }
                        contents[slot] = ItemStack.deserializeBytes(in.readNBytes(length));
                        if (contents[slot] == null || contents[slot].getType().isAir()) {
                            throw new IllegalArgumentException("Invalid nonempty item payload");
                        }
                    }
                    default -> throw new IllegalArgumentException("Invalid slot marker");
                }
            }
            if (in.available() != 0) throw new IllegalArgumentException("Trailing personal kit data");
            return contents;
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed personal kit payload", e);
        }
    }
}
