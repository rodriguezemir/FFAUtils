package site.zvolcan.fFAUtils.objects;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import site.zvolcan.fFAUtils.FFAUtils;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Objects;

public final class FFAPlayer {

    @Getter
    private final UUID uuid;
    @Getter
    @Setter
    private Kit lastKit = null;
    @Getter
    @Setter
    private Location lastSpawn = null;
    @Getter
    @Setter
    private int kills = 0;
    @Getter
    @Setter
    private int deaths = 0;
    @Getter
    @Setter
    private int killstreak = 0;
    @Getter
    @Setter
    private PlayerState state = PlayerState.LOBBY;
    private final Map<String, ItemStack[]> personalKitContents = new HashMap<>();
    private final Map<String, String> personalKitFingerprints = new HashMap<>();

    /** Returns a deep snapshot; no mutable inventory state is shared with callers. */
    public Map<String, ItemStack[]> getPersonalKitContents() {
        Map<String, ItemStack[]> copy = new HashMap<>();
        personalKitContents.forEach((name, contents) -> copy.put(name, copyContents(contents)));
        return Collections.unmodifiableMap(copy);
    }

    public ItemStack[] getPersonalKitContents(String kitName) {
        ItemStack[] contents = personalKitContents.get(kitName);
        return contents == null ? null : copyContents(contents);
    }

    /** Fingerprints are opaque; comparison with the global kit belongs to the kit service. */
    public String getPersonalKitFingerprint(String kitName) {
        return personalKitFingerprints.get(kitName);
    }

    public void setPersonalKitContents(String kitName, ItemStack[] contents, String fingerprint) {
        Objects.requireNonNull(kitName, "kitName");
        Objects.requireNonNull(contents, "contents");
        if (contents.length != PlayerKitContentsCodec.SLOT_COUNT) {
            throw new IllegalArgumentException("Personal kits must contain exactly 41 slots");
        }
        personalKitContents.put(kitName, copyContents(contents));
        if (fingerprint == null) {
            personalKitFingerprints.remove(kitName);
        } else {
            personalKitFingerprints.put(kitName, fingerprint);
        }
    }

    public void removePersonalKitContents(String kitName) {
        personalKitContents.remove(kitName);
        personalKitFingerprints.remove(kitName);
    }

    private static ItemStack[] copyContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            copy[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
        return copy;
    }

    public FFAPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    public double getKDR() {
        if (deaths == 0) {
            return kills;
        }
        return (double) kills / deaths;
    }

    public void teleportToSpawn() {
        if (state == PlayerState.LOBBY) {
            return;
        }

        setState(PlayerState.LOBBY);
        final Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            FFAUtils.getInstance().getLobbyManager().addLobbyItems(player);
            player.teleport(FFAUtils.getInstance().getSpawnManager().getLobbySpawn());
        }
    }

}
