package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * A clock the group can only hold back with sustained damage. It fills on its own, every second, for
 * the whole phase; every point of damage dealt pushes it back down. Let it top out and the boss takes
 * a permanent step up — bigger, faster, harder to hurt — and the clock resets to do it again.
 * <p>
 * This is the roster's soft enrage, and it exists because the alternative shape for a final phase is
 * a hard timer that simply wipes the group, which is a much worse experience: it ends the fight
 * without teaching anything and it cannot be partially succeeded at. Here, falling behind is a real,
 * visible, compounding cost that a group can still fight through, and the ceiling on permanent
 * hardening means no amount of it can make the boss unkillable.
 * <p>
 * There is no lockout anywhere in it and the boss is fully hittable from the first second to the
 * last: the phase is purely "keep the pressure up, or it gets worse", which is the one thing a last
 * stand should be about.
 */
public final class EscalatingDoomMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 5L;
    private static final int BAR_SEGMENTS = 16;

    private final String label;
    private final Color color;
    private final double fillPerSecond;
    private final double damageToPushBack;
    private final double cap;
    private final double scaleStepPerOverflow;
    private final double speedStepPerOverflow;
    private final double permanentHardenPerOverflow;
    private final double overflowNovaDamage;

    private double doom;
    private int overflows;

    public EscalatingDoomMechanic(BossInstance instance, String label, Color color, double fillPerSecond,
                                   double damageToPushBack, double cap, double scaleStepPerOverflow,
                                   double speedStepPerOverflow, double permanentHardenPerOverflow,
                                   double overflowNovaDamage) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.fillPerSecond = fillPerSecond;
        this.damageToPushBack = Math.max(0.01, damageToPushBack);
        this.cap = Math.max(1.0, cap);
        this.scaleStepPerOverflow = scaleStepPerOverflow;
        this.speedStepPerOverflow = speedStepPerOverflow;
        this.permanentHardenPerOverflow = permanentHardenPerOverflow;
        this.overflowNovaDamage = overflowNovaDamage;
    }

    @Override
    protected void onStart() {
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("Keep hitting it — every second you do not, it grows", NamedTextColor.GRAY));
        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 0.6f);
    }

    @Override
    protected void onStop() {
        doom = 0.0;
    }

    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        if (stopped || damageDealt <= 0) {
            return;
        }
        doom = Math.max(0.0, doom - damageDealt / damageToPushBack);
        // Sustained pressure is exactly what the phase wants; every landed hit is engagement.
        instance.recordExposure();
    }

    @Override
    protected void tick() {
        doom += fillPerSecond * (TICK_INTERVAL / 20.0);

        Location loc = instance.entity().getLocation();
        double fraction = Math.min(1.0, doom / cap);
        if (fraction > 0.7) {
            Fx.coloredBurst(loc.clone().add(0, 1.8, 0), color, 1.4f, (int) (8 + 14 * fraction), 0.6);
            if (elapsedTicks % 20 == 0) {
                Fx.sound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f + 0.6f * (float) fraction);
            }
        }

        if (doom >= cap) {
            overflow();
        }
        showBars();
    }

    private void showBars() {
        double fraction = Math.min(1.0, doom / cap);
        int filled = (int) Math.round(BAR_SEGMENTS * fraction);
        NamedTextColor tone = fraction > 0.75 ? NamedTextColor.RED
                : fraction > 0.4 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
        Component text = Component.text(label + "  ", NamedTextColor.DARK_RED)
                .append(Component.text("▮".repeat(filled), tone))
                .append(Component.text("▯".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text(overflows > 0 ? "   risen ×" + overflows : "   hold it down",
                        overflows > 0 ? NamedTextColor.RED : NamedTextColor.GRAY));
        BossBar.Color barColor = fraction > 0.75 ? BossBar.Color.RED
                : fraction > 0.4 ? BossBar.Color.YELLOW : BossBar.Color.GREEN;
        instance.mechanicBar().updateShared(instance.barViewers(), text, fraction, barColor);
    }

    /**
     * The clock ran out. The boss steps up permanently and the meter resets — the group has not lost,
     * but every future second of this phase is harder than the last one was.
     */
    private void overflow() {
        doom = 0.0;
        overflows++;

        instance.empower(scaleStepPerOverflow, speedStepPerOverflow);
        instance.addPermanentDamageReduction(permanentHardenPerOverflow);

        Location loc = instance.entity().getLocation();
        Fx.coloredBurst(loc.clone().add(0, 1.4, 0), color, 2.8f, 80, 1.2);
        Fx.flash(loc.clone().add(0, 1.4, 0), 3);
        Fx.expandingRings(plugin, loc, Particle.SOUL_FIRE_FLAME,
                Math.min(14.0, instance.arena().radius() * 0.6), 4, 2L);
        Fx.sound(loc, Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.6f);
        instance.showTitle(
                Component.text("IT RISES", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                Component.text("You were too slow — it is stronger now", NamedTextColor.GRAY));

        for (Player player : combatants()) {
            hurt(player, overflowNovaDamage);
        }
    }
}
