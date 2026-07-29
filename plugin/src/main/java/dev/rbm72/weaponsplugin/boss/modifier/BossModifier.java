package dev.rbm72.weaponsplugin.boss.modifier;

import java.util.Locale;
import java.util.Optional;

/**
 * One composable difficulty affix. Any combination may be active on a boss at once — that is the whole
 * point of the type existing.
 * <p>
 * This replaces the single {@code hardMode} boolean, which could only ever say "harder" one way and so
 * gave the roster exactly two difficulty settings. Affixes stack, which turns seventeen encounters into
 * as many distinct runs as there are combinations, for no new boss content: {@code frenzy + no_heal} on
 * a boss whose pressure is sustained chip damage is a different fight from {@code hard + double_adds} on
 * the same boss, and neither is expressible with a boolean.
 * <p>
 * {@link #HARD} is the old boolean, preserved exactly — {@code /bosshardmode} and the menu's shift-click
 * still toggle precisely this one, so nothing an admin already knows how to do changed.
 */
public enum BossModifier {

    /** ×1.5 health, visibly bigger and faster. The original hard mode, unchanged. */
    HARD("hard", "Hard Mode", "×1.5 health, bigger and faster"),

    /** No healing of any kind for players inside the arena — potions, regen, golden apples, natural. */
    NO_HEAL("no-heal", "No Quarter", "players cannot heal inside the arena"),

    /** Every add the boss summons arrives doubled. */
    DOUBLE_ADDS("double-adds", "Swarmed", "every summoned add spawns twice"),

    /** Attack cooldowns cut further on top of the normal phase ramp. */
    FRENZY("frenzy", "Frenzy", "attack cooldowns cut — far less breathing room"),

    /** A share of every hit the boss takes is dealt straight back to whoever landed it. */
    REFLECT("reflect", "Mirrored Pain", "a share of your damage is reflected back at you"),

    /** Past a time limit the boss escalates on a clock instead of on health — a DPS check with teeth. */
    TIMER("timer", "Deadline", "past the time limit the boss escalates every 30s, forever");

    private final String id;
    private final String displayName;
    private final String description;

    BossModifier(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    /** Stable lowercase-kebab id — what appears in commands, config, and telemetry files. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public static Optional<BossModifier> byId(String id) {
        String key = id.toLowerCase(Locale.ROOT).replace('_', '-');
        for (BossModifier modifier : values()) {
            if (modifier.id.equals(key)) {
                return Optional.of(modifier);
            }
        }
        return Optional.empty();
    }
}
