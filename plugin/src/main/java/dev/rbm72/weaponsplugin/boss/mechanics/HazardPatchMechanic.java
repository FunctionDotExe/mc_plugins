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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The arena fills up. Patches keep appearing, keep growing, and only leave when they are good and
 * ready — so the usable floor shrinks steadily and the group is forced to keep giving ground while
 * still holding the boss.
 * <p>
 * The design job here is denial-of-space rather than damage. A patch's tick damage is deliberately
 * modest: standing in one briefly is a mistake you can absorb, and being <em>surrounded</em> by them
 * because nobody moved for twenty seconds is the real failure. That makes the phase about reading
 * the floor and repositioning early, and it compounds naturally with anything else running at the
 * same time, since every other mechanic's safe ground can be eaten by a patch.
 * <p>
 * Patches never spawn directly on top of a player, so nobody is ever taxed for standing still at the
 * exact wrong instant, and the arena can never fully close: the oldest patch always expires, and a
 * hard cap keeps a guaranteed share of the floor open.
 */
public final class HazardPatchMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 4L;
    private static final int RING_POINTS = 18;
    /** Never let patches claim more of the arena than this, whatever the tuning says. */
    private static final double MAX_ARENA_COVERAGE = 0.45;

    private final String label;
    private final Color color;
    private final Particle particle;
    private final int spawnIntervalTicks;
    private final int maxPatches;
    private final double startRadius;
    private final double maxRadius;
    private final double growthPerSecond;
    private final int lifetimeTicks;
    private final double damagePerSecond;
    private final PotionEffectType effect;
    private final int effectAmplifier;
    private final double placementFraction;

    private final List<Patch> patches = new ArrayList<>();
    private int spawnCountdown;
    private int ticksSinceExposureCredit;

    private static final class Patch {
        final Location at;
        double radius;
        int age;

        Patch(Location at, double radius) {
            this.at = at;
            this.radius = radius;
        }
    }

    public HazardPatchMechanic(BossInstance instance, String label, Color color, Particle particle,
                                int spawnIntervalTicks, int maxPatches, double startRadius, double maxRadius,
                                double growthPerSecond, int lifetimeTicks, double damagePerSecond,
                                PotionEffectType effect, int effectAmplifier, double placementFraction) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.particle = particle;
        this.spawnIntervalTicks = Math.max(10, spawnIntervalTicks);
        this.maxPatches = Math.max(1, maxPatches);
        this.startRadius = Math.max(1.0, startRadius);
        this.maxRadius = Math.max(startRadius, maxRadius);
        this.growthPerSecond = growthPerSecond;
        this.lifetimeTicks = Math.max(40, lifetimeTicks);
        this.damagePerSecond = damagePerSecond;
        this.effect = effect;
        this.effectAmplifier = effectAmplifier;
        this.placementFraction = placementFraction;
    }

    @Override
    protected void onStart() {
        spawnCountdown = 20;
        instance.showTitle(
                Component.text(label, NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("The floor is closing in — keep moving", NamedTextColor.GRAY));
    }

    @Override
    protected void onStop() {
        patches.clear();
    }

    /** Effective cap, so a small arena can never be sealed shut by a large patch budget. */
    private int effectiveMaxPatches() {
        double arenaArea = Math.PI * instance.arena().radius() * instance.arena().radius();
        double patchArea = Math.PI * maxRadius * maxRadius;
        int areaCap = (int) Math.floor(arenaArea * MAX_ARENA_COVERAGE / Math.max(1.0, patchArea));
        return Math.max(1, Math.min(maxPatches, areaCap));
    }

    @Override
    protected void tick() {
        double step = TICK_INTERVAL / 20.0;

        for (Iterator<Patch> it = patches.iterator(); it.hasNext(); ) {
            Patch patch = it.next();
            patch.age += TICK_INTERVAL;
            patch.radius = Math.min(maxRadius, patch.radius + growthPerSecond * step);
            if (patch.age >= lifetimeTicks) {
                Fx.burst(patch.at.clone().add(0, 0.4, 0), particle, 10, 0.5);
                it.remove();
                continue;
            }
            double fade = patch.age > lifetimeTicks - 40 ? 0.6f : 1.0f;
            Fx.coloredRing(patch.at, color, (float) (1.3 * fade), patch.radius, RING_POINTS, elapsedTicks * 0.05);
            if (elapsedTicks % 8 == 0) {
                Fx.burst(patch.at.clone().add(0, 0.5, 0), particle, 5, patch.radius * 0.35);
            }
        }

        boolean anyoneClear = false;
        for (Player player : combatants()) {
            Patch standing = patchUnder(player);
            if (standing == null) {
                anyoneClear = true;
                continue;
            }
            if (elapsedTicks % 20 == 0) {
                hurt(player, damagePerSecond);
                if (effect != null && player.isValid() && !player.isDead()) {
                    player.addPotionEffect(new PotionEffect(effect, 60, effectAmplifier));
                }
                Fx.coloredBurst(player.getLocation().add(0, 0.6, 0), color, 1.2f, 10, 0.3);
            }
        }

        ticksSinceExposureCredit += TICK_INTERVAL;
        if (anyoneClear && ticksSinceExposureCredit >= 20) {
            ticksSinceExposureCredit = 0;
            // Staying out of the spread while still fighting is the ask; nobody is credited for
            // standing in it and tanking the ticks.
            instance.recordExposure();
        }

        spawnCountdown -= TICK_INTERVAL;
        if (spawnCountdown <= 0) {
            spawnCountdown = spawnIntervalTicks;
            if (patches.size() < effectiveMaxPatches()) {
                sprout();
            }
        }
        showBars();
    }

    private void showBars() {
        int cap = effectiveMaxPatches();
        instance.mechanicBar().update(instance.barViewers(), viewer -> {
            boolean tainted = patchUnder(viewer) != null;
            Component text = Component.text(label + "  ", NamedTextColor.DARK_GREEN)
                    .append(Component.text(patches.size() + " / " + cap + " patches", NamedTextColor.WHITE))
                    .append(tainted
                            ? Component.text("   YOU ARE STANDING IN IT", NamedTextColor.RED)
                            : Component.text("   clear ground", NamedTextColor.GREEN));
            return MechanicBar.Readout.of(text, patches.size() / (double) cap,
                    tainted ? BossBar.Color.RED : BossBar.Color.GREEN);
        });
    }

    private Patch patchUnder(Player player) {
        Location at = player.getLocation();
        for (Patch patch : patches) {
            if (flatDistance(at, patch.at) <= patch.radius) {
                return patch;
            }
        }
        return null;
    }

    /**
     * A new patch on open ground. Retried a few times to avoid landing on someone's feet — a patch
     * that appears under a player is damage they had no chance to read, which is the one thing this
     * mechanic is not supposed to be.
     */
    private void sprout() {
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double spread = ThreadLocalRandom.current().nextDouble(0.15, placementFraction);
            Location spot = surfaceSpot(angle, spread);
            boolean onSomeone = combatants().stream()
                    .anyMatch(p -> flatDistance(p.getLocation(), spot) < startRadius + 2.0);
            if (onSomeone && attempt < 5) {
                continue;
            }
            patches.add(new Patch(spot, startRadius));
            Fx.coloredBurst(spot.clone().add(0, 0.6, 0), color, 1.6f, 20, 0.4);
            Fx.sound(spot, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.7f);
            return;
        }
    }
}
