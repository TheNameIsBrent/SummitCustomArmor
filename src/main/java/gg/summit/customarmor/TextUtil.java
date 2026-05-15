package gg.summit.customarmor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Centralised text parsing for SummitCustomArmor.
 *
 * Supported formats (can be mixed freely in any string):
 *
 *   & codes      — §-style colour codes  e.g. &6, &l, &r
 *   <#RRGGBB>    — Hex colour            e.g. <#FF5500>text</color>
 *   <gradient:#hex1:#hex2>text</gradient>
 *                — Gradient between two hex colours
 *   Any MiniMessage tag — <bold>, <italic>, <rainbow>, etc.
 *
 * Config examples:
 *   name: "<gradient:#4DCCFF:#FFFFFF>Key Finder Chestplate</gradient>"
 *   message: "%prefix% &aYou found a <#FFD700>Legendary Key</color>!"
 *   lore line: "&7Owner: &f%owner%"
 */
public final class TextUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    // Legacy serializer that converts & codes to § and back
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private TextUtil() {}

    /**
     * Parses a string supporting & codes AND MiniMessage tags.
     * Returns a Component with italic disabled (for item names/lore).
     */
    public static Component parse(String text) {
        return parseRaw(text).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Parses a string supporting & codes AND MiniMessage tags.
     * Returns a Component without forcing italic off (for chat messages).
     */
    public static Component parseMessage(String text) {
        return parseRaw(text);
    }

    // -------------------------------------------------------------------------

    private static Component parseRaw(String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        // Convert & codes to <legacy_char>N placeholders before MiniMessage sees it,
        // so MiniMessage doesn't accidentally interpret & as something else.
        // Strategy: replace &X with the § equivalent, then let MiniMessage handle
        // everything INCLUDING legacy § codes via its built-in legacy support.
        String withSection = text.replace("§", "&")   // normalise any raw § to &
                                 .replace("&&", "\0"); // escape doubled &&

        // Pre-process: convert &X to MiniMessage colour tags inline
        String mm = ampToMiniMessage(withSection).replace("\0", "&");

        return MM.deserialize(mm);
    }

    /**
     * Converts &X colour codes in a string to MiniMessage tags.
     * Leaves MiniMessage tags (< ... >) untouched.
     * &a → <green>  &6 → <gold>  &l → <bold>  &r → <reset>  etc.
     */
    private static String ampToMiniMessage(String input) {
        if (!input.contains("&")) return input;

        StringBuilder sb = new StringBuilder(input.length() + 32);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                String tag = codeToTag(code);
                if (tag != null) {
                    sb.append(tag);
                    i++; // skip the code char
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String codeToTag(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'l' -> "<bold>";
            case 'o' -> "<italic>";
            case 'n' -> "<underlined>";
            case 'm' -> "<strikethrough>";
            case 'k' -> "<obfuscated>";
            case 'r' -> "<reset>";
            default  -> null;
        };
    }
}
