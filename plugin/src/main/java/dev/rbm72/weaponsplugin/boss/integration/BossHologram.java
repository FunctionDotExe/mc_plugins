package dev.rbm72.weaponsplugin.boss.integration;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;

/**
 * Per-boss DecentHolograms name/health-bar hologram, floating above the arena's spawn point.
 * Every method no-ops if DecentHolograms isn't installed or the boss has it disabled
 * ({@link dev.rbm72.weaponsplugin.boss.Boss#hologramEnabled()}) — a purely cosmetic add-on that
 * never affects fight logic.
 */
public final class BossHologram {

    private static final int BAR_SEGMENTS = 20;

    private BossHologram() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
    }

    private static String id(BossInstance instance) {
        return "bossfight-" + instance.boss().id();
    }

    public static void start(BossInstance instance) {
        if (!available() || !instance.boss().hologramEnabled()) {
            return;
        }
        Location loc = instance.arena().center().add(0, 3.0, 0);
        DHAPI.createHologram(id(instance), loc, lines(instance, 1.0));
    }

    /** Called from the boss's tick loop — refreshes the health line to the current fraction. */
    public static void update(BossInstance instance, double fraction) {
        if (!available() || !instance.boss().hologramEnabled()) {
            return;
        }
        Hologram hologram = DHAPI.getHologram(id(instance));
        if (hologram == null) {
            return;
        }
        DHAPI.setHologramLines(hologram, lines(instance, fraction));
    }

    public static void stop(BossInstance instance) {
        if (!available()) {
            return;
        }
        Hologram hologram = DHAPI.getHologram(id(instance));
        if (hologram != null) {
            hologram.delete();
        }
    }

    private static List<String> lines(BossInstance instance, double fraction) {
        String name = LegacyComponentSerializer.legacySection().serialize(instance.boss().displayName());
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * BAR_SEGMENTS);
        String bar = "§a" + "|".repeat(filled) + "§7" + "|".repeat(BAR_SEGMENTS - filled)
                + " §f" + Math.round(fraction * 100) + "%";
        return List.of(name, bar);
    }
}
