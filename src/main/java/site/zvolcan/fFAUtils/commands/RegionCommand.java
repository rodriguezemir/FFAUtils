package site.zvolcan.fFAUtils.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.abs.FFACommand;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.RegionManager;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.Region;
import site.zvolcan.fFAUtils.objects.Sounds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RegionCommand implements FFACommand {

    private static final String[] ACTIONS = {"pos1", "pos2", "delete", "info"};

    private final FFAUtils plugin;
    private final KitManager kitManager;
    private final RegionManager regionManager;
    private final Map<UUID, PendingCorner> pendingCorners = new HashMap<>();

    public RegionCommand(FFAUtils plugin, KitManager kitManager, RegionManager regionManager) {
        this.plugin = plugin;
        this.kitManager = kitManager;
        this.regionManager = regionManager;
    }

    private record PendingCorner(String kitName, Location location) {}

    @Override
    public String name() {
        return "kitregion";
    }

    /** {@code /kitregion <kit> <pos1|pos2|delete|info>}: all four actions need the kit name. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 2) {
            return true;
        }
        String kitName = args[0];
        switch (args[1]) {
            case "pos1" -> setCorner(sender, kitName, 1);
            case "pos2" -> setCorner(sender, kitName, 2);
            case "delete" -> {
                if (!regionManager.deleteRegion(kitName)) {
                    message(sender, Sounds.ERROR_SOUND,
                            messages().getMessage("region-not-found", "{kit}", kitName));
                    return true;
                }
                message(sender, Sounds.SUCCESS_SOUND,
                        messages().getMessage("region-deleted", "{kit}", kitName));
            }
            case "info" -> {
                Region region = regionManager.getRegion(kitName);
                if (region == null) {
                    message(sender, Sounds.ERROR_SOUND,
                            messages().getMessage("region-not-found", "{kit}", kitName));
                    return true;
                }
                message(sender, null, messages().getMessage(
                        "region-info",
                        "{kit}", kitName,
                        "{world}", region.getWorld(),
                        "{min}", region.getMinX() + ", " + region.getMinY() + ", " + region.getMinZ(),
                        "{max}", region.getMaxX() + ", " + region.getMaxY() + ", " + region.getMaxZ()
                ));
            }
            default -> {
                // Not an action of this tree.
            }
        }
        return true;
    }

    /** The action literals sit below the kit argument; the kit name itself had no {@code suggests}. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2) {
            return FFACommand.matching(args[1], ACTIONS);
        }
        return List.of();
    }

    private int setCorner(CommandSender sender, String kitName, int corner) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages().getMessage("only-players-execute"));
            return 1;
        }
        Kit kit = kitManager.getKit(kitName);
        if (kit == null) {
            message(player, Sounds.ERROR_SOUND,
                    messages().getMessage("kit-not-found", "{name}", kitName));
            return 1;
        }
        String normalizedKit = kit.getName().toLowerCase();
        Location location = player.getLocation();

        PendingCorner previous = pendingCorners.get(player.getUniqueId());
        if (previous != null && previous.kitName().equals(normalizedKit)) {
            Location pos1 = corner == 1 ? location : previous.location();
            Location pos2 = corner == 2 ? location : previous.location();

            if (regionManager.setRegion(normalizedKit, pos1, pos2)) {
                pendingCorners.remove(player.getUniqueId());
                message(player, Sounds.SUCCESS_SOUND,
                        messages().getMessage("region-saved", "{kit}", kit.getName()));
                return 1;
            }
            message(player, Sounds.ERROR_SOUND,
                    messages().getMessage("region-world-mismatch"));
            return 1;
        }

        pendingCorners.put(player.getUniqueId(), new PendingCorner(normalizedKit, location));
        message(player, Sounds.SUCCESS_SOUND, messages().getMessage(
                "region-corner-set",
                "{kit}", kit.getName(),
                "{corner}", String.valueOf(corner),
                "{other}", String.valueOf(corner == 1 ? 2 : 1)
        ));
        return 1;
    }

    private void message(CommandSender sender, net.kyori.adventure.sound.Sound sound, String message) {
        if (sound == null) {
            plugin.getUtils().message(sender, false, message);
            return;
        }
        plugin.getUtils().message(sender, sound, message);
    }

    private MessagesManager messages() {
        return MessagesManager.getInstance();
    }
}
