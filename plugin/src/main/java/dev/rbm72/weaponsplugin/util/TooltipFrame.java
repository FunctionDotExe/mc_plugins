package dev.rbm72.weaponsplugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders the rarity-colored footer shared by every item tooltip (weapon, shield, accessory,
 * armor). Minecraft's tooltip font is proportional, not monospace, so a footer of fixed text
 * doesn't visually center under the lore above it. This estimates rendered pixel width from
 * Minecraft's default font metrics so the footer can be centered against the actual widest lore
 * line instead of just left-aligned.
 */
public final class TooltipFrame {

    private TooltipFrame() {
    }

    private static final int DEFAULT_WIDTH = 6;

    private static final Map<Character, Integer> WIDTHS = Map.ofEntries(
            Map.entry(' ', 4), Map.entry('!', 2), Map.entry('"', 5), Map.entry('\'', 3),
            Map.entry('(', 5), Map.entry(')', 5), Map.entry('*', 5), Map.entry(',', 2),
            Map.entry('.', 2), Map.entry(':', 2), Map.entry(';', 2), Map.entry('<', 5),
            Map.entry('>', 5), Map.entry('@', 7), Map.entry('I', 4), Map.entry('i', 2),
            Map.entry('l', 3), Map.entry('f', 5), Map.entry('k', 5), Map.entry('t', 4),
            Map.entry('◆', 7));

    private static int width(String text, boolean bold) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += WIDTHS.getOrDefault(text.charAt(i), DEFAULT_WIDTH) + (bold ? 1 : 0);
        }
        return total;
    }

    private static int width(Component component) {
        return width(PlainTextComponentSerializer.plainText().serialize(component), false);
    }

    /** Widest rendered line among the given lore components, in estimated pixels. */
    public static int widestLine(List<Component> lines) {
        int max = 0;
        for (Component line : lines) {
            max = Math.max(max, width(line));
        }
        return max;
    }

    /**
     * Word-wraps {@code text} so no line exceeds {@code maxWidth} estimated pixels. Splits on spaces
     * only; a single word longer than {@code maxWidth} still occupies its own line intact. Returns at
     * least one segment (possibly empty for empty input). Used to break long lore lines Minecraft would
     * otherwise render as one unwrapped run.
     */
    public static List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() == 0) {
                current.append(word);
            } else if (width(current + " " + word, false) <= maxWidth) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        lines.add(current.toString());
        return lines;
    }

    private static String footerText(String label) {
        return "◆ " + label + " ◆";
    }

    /** Estimated pixel width of the bold footer label, so callers can compute the centering target. */
    public static int footerWidth(String label) {
        return width(footerText(label), true);
    }

    /** Bold "◆ LABEL ◆" footer, left-padded with spaces so it centers within {@code targetWidth}. */
    public static Component footer(String label, TextColor color, int targetWidth) {
        String text = footerText(label);
        int padding = Math.max(0, (targetWidth - width(text, true)) / 2);
        int spaces = padding / WIDTHS.getOrDefault(' ', DEFAULT_WIDTH);

        Component result = spaces > 0 ? Component.text(" ".repeat(spaces)) : Component.empty();
        return result.append(Component.text(text, color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
    }
}
