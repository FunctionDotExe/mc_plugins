package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A meter that fills on every player individually while they are somewhere the boss is punishing, and
 * drains while they are somewhere it is not. Let it fill and something bad happens to that player
 * specifically; nobody else is locked out and the boss never stops being hittable.
 * <p>
 * This is the rework's workhorse "keep doing Y while you fight" shape, and the reason it is one class
 * rather than four is that heat stacks, freezing stacks, and creeping rot are all the same rule with
 * different fiction: a per-player number, a place where it stops climbing, and a consequence for
 * ignoring it. What makes each boss's version feel different is <em>where</em> the safe ground is —
 * a drifting pocket, the far edge of an aura, the shade — which is the {@link MechanicField}'s job,
 * not this class's.
 * <p>
 * Solo behaviour is inherent: the meter is per player, so a lone player faces exactly their own
 * meter and can always solve it by relocating. Failing costs them and grants nothing, so eating the
 * detonation is never the efficient line (Pitfall 3).
 */
public final class StackMeterMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 5L;
    private static final int BAR_SEGMENTS = 12;

    /** What happens to a player whose meter fills. Never rewards the failure. */
    @FunctionalInterface
    public interface StackPayload {
        void detonate(BossInstance instance, Player player, double stacks);
    }

    private final String label;
    private final Color color;
    private final MechanicField ventField;
    private final double gainPerSecond;
    private final double ventDrainPerSecond;
    private final double cap;
    private final StackPayload payload;
    private final Component openingTitle;
    private final Component openingSubtitle;
    private final String safeHint;
    private final String dangerHint;

    private final Map<UUID, Double> stacks = new HashMap<>();
    /** Throttles the phase-floor credit to at most one per second regardless of tick rate. */
    private int ticksSinceExposureCredit;

    public StackMeterMechanic(BossInstance instance, String label, Color color, MechanicField ventField,
                               double gainPerSecond, double ventDrainPerSecond, double cap,
                               StackPayload payload, Component openingTitle, Component openingSubtitle,
                               String safeHint, String dangerHint) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.ventField = ventField;
        this.gainPerSecond = gainPerSecond;
        this.ventDrainPerSecond = ventDrainPerSecond;
        this.cap = Math.max(1.0, cap);
        this.payload = payload;
        this.openingTitle = openingTitle;
        this.openingSubtitle = openingSubtitle;
        this.safeHint = safeHint;
        this.dangerHint = dangerHint;
    }

    @Override
    protected void onStart() {
        ventField.start(instance);
        instance.showTitle(openingTitle, openingSubtitle);
        Fx.sound(instance.entity().getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.7f);
    }

    @Override
    protected void onStop() {
        ventField.stop(instance);
        stacks.clear();
    }

    @Override
    protected void tick() {
        ventField.tick(instance, elapsedTicks);

        List<Player> present = combatants();
        Set<UUID> here = new HashSet<>();
        boolean anyoneVenting = false;
        double step = TICK_INTERVAL / 20.0;

        for (Player player : present) {
            here.add(player.getUniqueId());
            boolean venting = ventField.contains(instance, player);
            anyoneVenting |= venting;

            double current = stacks.getOrDefault(player.getUniqueId(), 0.0);
            current += venting ? -ventDrainPerSecond * step : gainPerSecond * step;
            current = Math.max(0.0, current);

            if (current >= cap) {
                // Reset before detonating: the payload can damage, and a death mid-payload must not
                // leave a full meter behind that instantly re-detonates when they respawn and return.
                stacks.put(player.getUniqueId(), 0.0);
                detonate(player, cap);
                continue;
            }
            stacks.put(player.getUniqueId(), current);

            if (!venting && current > cap * 0.6) {
                Fx.coloredBurst(player.getLocation().add(0, 1.2, 0), color, 1.0f, 6, 0.3);
                if (elapsedTicks % 20 == 0) {
                    Fx.sound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f,
                            0.6f + 0.8f * (float) (current / cap));
                }
            }
        }

        // Anyone who left the fight loses their meter rather than keeping it banked for a return.
        stacks.keySet().retainAll(here);

        ticksSinceExposureCredit += TICK_INTERVAL;
        if (anyoneVenting && ticksSinceExposureCredit >= 20) {
            ticksSinceExposureCredit = 0;
            // The phase floor's definition of "the group engaged": somebody is actually managing the
            // meter rather than tanking it. Failing to vent deliberately earns nothing here.
            instance.recordExposure();
        }

        showBars();
    }

    private void showBars() {
        instance.mechanicBar().update(instance.barViewers(), player -> {
            double current = stacks.getOrDefault(player.getUniqueId(), 0.0);
            double fraction = Math.min(1.0, current / cap);
            boolean venting = ventField.contains(instance, player);
            int filled = (int) Math.round(BAR_SEGMENTS * fraction);
            NamedTextColor tone = venting ? NamedTextColor.GREEN
                    : fraction > 0.75 ? NamedTextColor.RED
                    : fraction > 0.45 ? NamedTextColor.GOLD : NamedTextColor.YELLOW;
            Component text = Component.text(label + "  ", NamedTextColor.WHITE)
                    .append(Component.text("▮".repeat(filled), tone))
                    .append(Component.text("▯".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY))
                    .append(Component.text("   " + (venting ? safeHint : dangerHint),
                            venting ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            BossBar.Color barColor = venting ? BossBar.Color.GREEN
                    : fraction > 0.75 ? BossBar.Color.RED
                    : fraction > 0.45 ? BossBar.Color.YELLOW : BossBar.Color.WHITE;
            return MechanicBar.Readout.of(text, fraction, barColor);
        });
    }

    private void detonate(Player player, double atStacks) {
        Location at = player.getLocation();
        Fx.coloredBurst(at.clone().add(0, 1.0, 0), color, 2.4f, 50, 0.9);
        Fx.flash(at.clone().add(0, 1.0, 0), 1);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 1.1f);
        payload.detonate(instance, player, atStacks);
    }

    // ------------------------------------------------------------ stock payloads

    /**
     * The carrier bursts, hitting themselves hardest and everyone standing with them for less — so a
     * group that ignores the meter and stays stacked up punishes itself twice over.
     */
    public static StackPayload selfDetonate(double radius, double centreDamage, double splashDamage) {
        return (instance, player, stacksAtCap) -> {
            Location at = player.getLocation();
            Fx.coloredRing(at, Color.fromRGB(255, 120, 40), 1.6f, radius, 26, 0);
            for (Player nearby : Arena.combatants(at, radius)) {
                double amount = nearby.equals(player) ? centreDamage : splashDamage;
                if (nearby.isValid() && !nearby.isDead()) {
                    nearby.damage(amount, instance.entity());
                }
            }
        };
    }

    /** A heavy hit plus a crippling window — legible, severe, and over with. */
    public static StackPayload crippling(double damage, int debuffTicks, int slownessAmplifier) {
        return (instance, player, stacksAtCap) -> {
            if (!player.isValid() || player.isDead()) {
                return;
            }
            player.damage(damage, instance.entity());
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, debuffTicks, slownessAmplifier));
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, debuffTicks, 1));
        };
    }

    /**
     * A heavy hit that also hardens the boss a little, permanently. Reserved for the late roster,
     * where design rule 4 asks that failure compound rather than simply hurt.
     */
    public static StackPayload cripplingAndEmpower(double damage, int debuffTicks, double permanentReduction) {
        return (instance, player, stacksAtCap) -> {
            if (player.isValid() && !player.isDead()) {
                player.damage(damage, instance.entity());
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, debuffTicks, 2));
            }
            instance.addPermanentDamageReduction(permanentReduction);
            instance.showTitle(
                    Component.text("IT DRINKS THE FAILURE", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                    Component.text("Its hide thickens", NamedTextColor.GRAY));
        };
    }
}
