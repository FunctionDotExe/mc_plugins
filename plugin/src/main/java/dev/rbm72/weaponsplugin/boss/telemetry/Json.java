package dev.rbm72.weaponsplugin.boss.telemetry;

import java.util.Collection;
import java.util.Map;

/**
 * The smallest JSON writer that can emit a fight record: objects, arrays, strings, numbers, booleans.
 * <p>
 * Deliberately hand-rolled rather than pulling in a serializer. A telemetry file is written on the
 * main thread at the end of every fight, so the one property that matters more than convenience is
 * that it cannot fail on anything a boss name or a player name might contain — hence explicit
 * escaping, and no reflection over live Bukkit objects (whose graphs reach the whole server).
 */
final class Json {

    private final StringBuilder out = new StringBuilder();
    private boolean needsComma;

    Json object(Runnable body) {
        comma();
        out.append('{');
        needsComma = false;
        body.run();
        out.append('}');
        needsComma = true;
        return this;
    }

    Json field(String name, String value) {
        key(name);
        if (value == null) {
            out.append("null");
        } else {
            string(value);
        }
        needsComma = true;
        return this;
    }

    Json field(String name, long value) {
        key(name);
        out.append(value);
        needsComma = true;
        return this;
    }

    Json field(String name, double value) {
        key(name);
        // Non-finite doubles are not legal JSON; a telemetry file must stay parseable even if a
        // damage/health number somewhere went to infinity or NaN, which is itself worth seeing.
        out.append(Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.2f", value) : "null");
        needsComma = true;
        return this;
    }

    Json field(String name, boolean value) {
        key(name);
        out.append(value);
        needsComma = true;
        return this;
    }

    /** An object of name → count, sorted highest-first so the interesting rows are the ones you read first. */
    Json counts(String name, Map<String, Integer> values) {
        key(name);
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Integer> entry : values.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            string(entry.getKey());
            out.append(':').append(entry.getValue().intValue());
        }
        out.append('}');
        needsComma = true;
        return this;
    }

    Json strings(String name, Collection<String> values) {
        key(name);
        out.append('[');
        boolean first = true;
        for (String value : values) {
            if (!first) {
                out.append(',');
            }
            first = false;
            string(value);
        }
        out.append(']');
        needsComma = true;
        return this;
    }

    /** An array whose elements each write one {@link #object} into this same writer. */
    <T> Json array(String name, Collection<T> items, java.util.function.Consumer<T> writeItem) {
        key(name);
        out.append('[');
        needsComma = false;
        for (T item : items) {
            writeItem.accept(item);
        }
        out.append(']');
        needsComma = true;
        return this;
    }

    private void key(String name) {
        comma();
        string(name);
        out.append(':');
    }

    private void comma() {
        if (needsComma) {
            out.append(',');
            needsComma = false;
        }
    }

    private void string(String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
