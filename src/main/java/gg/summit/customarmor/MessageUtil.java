package gg.summit.customarmor;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Sends messages to players/senders with prefix injection.
 *
 * Config:
 *   prefix: "<dark_gray>[<gradient:#4DCCFF:#FFFFFF>CustomArmor</gradient><dark_gray>]"
 *
 * In any message string, use %prefix% to insert the configured prefix.
 * All messages support & codes, hex colours, and MiniMessage tags.
 */
public final class MessageUtil {

    private final SummitCustomArmor plugin;
    private String cachedPrefix;

    public MessageUtil(SummitCustomArmor plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cachedPrefix = plugin.getConfig().getString("prefix", "<dark_gray>[<aqua>CustomArmor<dark_gray>]");
    }

    /** Sends a chat message to a player. Resolves %prefix%, parses colours. */
    public void send(Player player, String messageKey) {
        String raw = plugin.getConfig().getString(messageKey, "");
        if (raw.isBlank()) return;
        player.sendMessage(TextUtil.parseMessage(resolve(raw)));
    }

    /** Sends a raw string (not a config key) directly to a player. */
    public void sendRaw(Player player, String text) {
        player.sendMessage(TextUtil.parseMessage(resolve(text)));
    }

    /** Sends a raw string to any CommandSender. */
    public void sendRaw(CommandSender sender, String text) {
        sender.sendMessage(TextUtil.parseMessage(resolve(text)));
    }

    /** Returns a Component for the given raw text with prefix resolved. */
    public Component component(String text) {
        return TextUtil.parseMessage(resolve(text));
    }

    /** Returns a Component for an item display name or lore line (italic disabled). */
    public Component itemComponent(String text) {
        return TextUtil.parse(resolve(text));
    }

    /** Resolves %prefix% in a string. */
    public String resolve(String text) {
        if (text == null) return "";
        return text.replace("%prefix%", cachedPrefix);
    }

    public String getPrefix() { return cachedPrefix; }
}
