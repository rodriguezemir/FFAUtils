package site.zvolcan.fFAUtils.util;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Message and item helpers for this plugin.
 *
 * <p>Every text input is parsed as MiniMessage after legacy {@code &}-codes are
 * translated, so configuration files written for older versions keep working.</p>
 */
public class PluginUtils {

    public final JavaPlugin plugin;
    public Component prefix;

    public PluginUtils(JavaPlugin plugin, String prefix) {
        this.plugin = plugin;
        setPrefix(prefix);
    }

    public void setPrefix(String prefix) {
        this.prefix = chat(prefix);
    }

    public Component chat(String message) {
        return MiniMessage.miniMessage().deserialize(convert(message));
    }

    /** Translates Bukkit legacy colour codes into MiniMessage tags. */
    private static String convert(String message) {
        String converted = message.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");
        converted = converted.replace("&0", "<black>");
        converted = converted.replace("&1", "<dark_blue>");
        converted = converted.replace("&2", "<dark_green>");
        converted = converted.replace("&3", "<dark_aqua>");
        converted = converted.replace("&4", "<dark_red>");
        converted = converted.replace("&5", "<dark_purple>");
        converted = converted.replace("&6", "<gold>");
        converted = converted.replace("&7", "<gray>");
        converted = converted.replace("&8", "<dark_gray>");
        converted = converted.replace("&9", "<blue>");
        converted = converted.replace("&a", "<green>");
        converted = converted.replace("&b", "<aqua>");
        converted = converted.replace("&c", "<red>");
        converted = converted.replace("&d", "<light_purple>");
        converted = converted.replace("&e", "<yellow>");
        converted = converted.replace("&f", "<white>");
        converted = converted.replace("&k", "<obfuscated>");
        converted = converted.replace("&m", "<strikethrough>");
        converted = converted.replace("&n", "<underlined>");
        converted = converted.replace("&o", "<italic>");
        converted = converted.replace("&l", "<bold>");
        converted = converted.replace("&r", "<reset>");
        return converted;
    }

    public void message(CommandSender sender, String... messages) {
        message(sender, true, null, messages);
    }

    public void message(CommandSender sender, boolean withPrefix, String... messages) {
        message(sender, withPrefix, null, messages);
    }

    public void message(CommandSender sender, Sound sound, String... messages) {
        message(sender, true, sound, messages);
    }

    public void message(CommandSender sender, boolean withPrefix, Sound sound, String... messages) {
        for (String raw : messages) {
            Component text = chat(raw);
            sender.sendMessage(withPrefix ? prefix.append(text) : text);
        }
        playSound(sender, sound);
    }

    public void broadcast(String... messages) {
        broadcast(true, null, messages);
    }

    public void broadcast(boolean withPrefix, String... messages) {
        broadcast(withPrefix, null, messages);
    }

    public void broadcast(boolean withPrefix, Sound sound, String... messages) {
        List<CommandSender> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
        targets.add(Bukkit.getConsoleSender());
        for (CommandSender target : targets) {
            message(target, withPrefix, sound, messages);
        }
    }

    private static void playSound(CommandSender sender, Sound sound) {
        if (sound != null && sender instanceof Player player) {
            player.playSound(sound);
        }
    }

    public ItemBuilder ib(Material material) {
        return new ItemBuilder(new ItemStack(material), this);
    }

    public ItemBuilder ib(ItemStack item) {
        return new ItemBuilder(item, this);
    }
}
