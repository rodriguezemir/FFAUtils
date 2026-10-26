package site.zvolcan.fFAUtils.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.abs.FFACommand;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.Sounds;

import java.util.List;
import java.util.Map;

public final class KitCommand implements FFACommand {

    private static final String CREATE = "create";
    private static final String EDIT = "edit";
    private static final String DELETE = "delete";
    private static final String LIST = "list";
    private static final String[] SUBCOMMANDS = {CREATE, EDIT, DELETE, LIST};

    private final FFAUtils plugin;
    private final KitManager kitManager;

    public KitCommand(FFAUtils plugin, KitManager kitManager) {
        this.plugin = plugin;
        this.kitManager = kitManager;
    }

    @Override
    public String name() {
        return "kit";
    }

    /**
     * {@code /kit <name>}, {@code /kit create <name>}, {@code /kit edit <name>},
     * {@code /kit delete <name>} and {@code /kit list}. A literal always wins over
     * the kit-name argument, exactly like the Brigadier tree resolved it.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return true;
        }
        switch (args[0]) {
            case CREATE -> {
                if (args.length == 2) {
                    create(sender, args[1]);
                }
            }
            case EDIT -> {
                if (args.length == 2) {
                    edit(sender, args[1]);
                }
            }
            case DELETE -> {
                if (args.length == 2) {
                    delete(sender, args[1]);
                }
            }
            case LIST -> {
                if (args.length == 1) {
                    list(sender);
                }
            }
            default -> {
                if (args.length == 1) {
                    apply(sender, args[0]);
                }
            }
        }
        return true;
    }

    /**
     * Only the subcommand literals are suggested; the kit-name argument had no
     * {@code suggests} on its node, so it keeps suggesting nothing.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return FFACommand.atFirstToken(args, SUBCOMMANDS);
    }

    private void apply(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance()
                    .getMessage("only-players-execute"));
            return;
        }
        Kit kit = kitManager.getKit(name);
        if (kit == null) {
            plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                    MessagesManager.getInstance().getMessage(
                            "kit-not-found", "{name}", name
                    )
            );
            return;
        }
        kitManager.applyKit(player, kit);
        plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                MessagesManager.getInstance().getMessage(
                        "kit-applied", "{name}", name
                )
        );
    }

    private void create(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance()
                    .getMessage("only-players-execute"));
            return;
        }
        if (kitManager.getKit(name) != null) {
            plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                    MessagesManager.getInstance().getMessage(
                            "kit-already-exists", "{name}", name
                    )
            );
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        Kit kit = new Kit(name, contents);
        kitManager.saveKit(name, kit);
        plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                MessagesManager.getInstance().getMessage(
                        "kit-created", "{name}", name
                )
        );
    }

    private void edit(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance()
                    .getMessage("only-players-execute"));
            return;
        }
        if (kitManager.getKit(name) == null) {
            plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                    MessagesManager.getInstance().getMessage(
                            "kit-not-found", "{name}", name
                    )
            );
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        Kit kit = new Kit(name, contents);
        kitManager.saveKit(name, kit);
        plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                MessagesManager.getInstance().getMessage(
                        "kit-edited", "{name}", name
                )
        );
    }

    private void delete(CommandSender sender, String name) {
        boolean deleted = kitManager.deleteKit(name);
        if (!deleted) {
            plugin.getUtils().message(
                    sender,
                    MessagesManager.getInstance().getMessage(
                            "kit-not-found",
                            "{name}",
                            name
                    )
            );
            return;
        }
        plugin.getUtils().message(sender, Sounds.SUCCESS_SOUND,
                MessagesManager.getInstance().getMessage(
                        "kit-deleted", "{name}", name
                )
        );
    }

    private void list(CommandSender sender) {
        Map<String, Kit> kits = kitManager.getAllKits();
        if (kits.isEmpty()) {
            plugin.getUtils().message(
                    sender,
                    MessagesManager.getInstance().getMessage(
                            "no-kits-available"
                    )
            );
            return;
        }
        plugin.getUtils().message(sender,
                MessagesManager.getInstance().getMessage(
                        "kits-list", "{kits}", String.join(", ", kits.keySet())
                )
        );
    }
}
