package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * He eats his own army to stay alive.
 * <p>
 * Tethers latch onto undead standing near him and haul them in, and for as long as a tether holds he heals
 * off the thing on the end of it. This is the boss's progress-loss punishment and it has two physical
 * answers, both of which the fight is already asking for:
 * <ul>
 *   <li><b>Kill the tethered add.</b> A tether to a corpse pays nothing — but the kill leaves a corpse pile,
 *       so cutting the drain costs the group floor space. That trade is the boss in miniature.</li>
 *   <li><b>Break line of sight.</b> A tether is cut the moment something solid gets between him and the add,
 *       which makes the walls the group builds for its chokepoint do double duty. The arena hands out the
 *       cobble for exactly this reason.</li>
 * </ul>
 * Tether <em>count</em> scales with the group. The heal each tether pays is the same at every size, so a
 * bigger group faces more tethers to cut rather than a harder-hitting one.
 */
public final class SoulDrainAttack extends BossAttack {

    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);
    /** Channel resolution. Heals land per second; this is only how often the tethers are re-checked and drawn. */
    private static final long CHANNEL_INTERVAL_TICKS = 5L;

    private final double radius;
    private final double healPerTetherPerSecond;
    private final double pullStrength;
    private final int channelTicks;
    private final int telegraphTicks;

    public SoulDrainAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.radius = configDouble("soul-drain-radius", 12.0);
        this.healPerTetherPerSecond = configDouble("soul-drain-heal-per-tether-per-second", 4.0);
        this.pullStrength = configDouble("soul-drain-pull-strength", 0.35);
        this.channelTicks = configInt("soul-drain-channel-ticks", 120);
        this.telegraphTicks = configInt("soul-drain-telegraph-ticks", 30);
    }

    @Override
    public String name() {
        return "Soul Drain";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("soul-drain-cooldown-seconds", 16.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        List<LivingEntity> tethered = pickTethers(ctx);
        sequence(telegraphTicks,
                () -> {
                    Location origin = ctx.bossLocation().add(0, 1.2, 0);
                    Fx.coloredRing(ctx.bossLocation(), NECROTIC, 1.5f, 3.0, 30, 0);
                    for (LivingEntity add : tethered) {
                        if (add.isValid()) {
                            Fx.line(add.getLocation().add(0, 1, 0), origin, Particle.SOUL, 12);
                        }
                    }
                },
                () -> {
                    BossAudio.play(ctx.bossLocation(), "boss.necro_overlord.soul_drain",
                            Sound.ENTITY_WITHER_AMBIENT, 1.15f, 0.7f);
                    Fx.sound(ctx.bossLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.1f, 0.5f);
                    channel(ctx, tethered);
                },
                14, onComplete);
    }

    /** One tether per player, minimum one, taken from his own tracked adds nearest to him. */
    private List<LivingEntity> pickTethers(AttackContext ctx) {
        int wanted = Math.max(1, Math.min(configInt("soul-drain-max-tethers", 5),
                Arena.combatants(ctx.bossLocation(), ctx.arena().radius()).size()));
        List<LivingEntity> found = new ArrayList<>(wanted);
        for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
            if (found.size() >= wanted) {
                break;
            }
            if (nearby instanceof LivingEntity add && ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                found.add(add);
            }
        }
        return found;
    }

    private void channel(AttackContext ctx, List<LivingEntity> tethered) {
        if (tethered.isEmpty()) {
            return;
        }
        AttributeInstance maxHealth = ctx.boss().getAttribute(Attribute.MAX_HEALTH);
        double cap = maxHealth != null ? maxHealth.getValue() : ctx.boss().getHealth();

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= channelTicks || !ctx.boss().isValid()) {
                    cancel();
                    return;
                }
                Location origin = ctx.boss().getLocation().add(0, 1.2, 0);
                int holding = 0;
                for (LivingEntity add : tethered) {
                    if (!add.isValid() || add.isDead() || !ctx.boss().hasLineOfSight(add)) {
                        continue;
                    }
                    holding++;
                    Fx.line(add.getLocation().add(0, 1, 0), origin, Particle.SOUL, 10);
                    // Hauled in physically rather than teleported, so the group can see which adds are
                    // feeding him and get in front of them.
                    Vector pull = origin.toVector().subtract(add.getLocation().toVector());
                    if (pull.lengthSquared() > 1.0E-4) {
                        add.setVelocity(add.getVelocity().add(pull.normalize().multiply(pullStrength)));
                    }
                }
                if (holding > 0 && elapsed % 20 == 0) {
                    double heal = healPerTetherPerSecond * holding;
                    ctx.boss().setHealth(Math.min(cap, ctx.boss().getHealth() + heal));
                    Fx.coloredBurst(origin, NECROTIC, 1.8f, 20, 0.6);
                }
                elapsed += (int) CHANNEL_INTERVAL_TICKS;
            }
        }.runTaskTimer(plugin, 0L, CHANNEL_INTERVAL_TICKS);
        // Tracked, so a fight that ends mid-channel cannot leave a task healing a boss that no longer exists.
        ctx.instance().trackTask(task);
    }
}
