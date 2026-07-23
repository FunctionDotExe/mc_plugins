package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Enrage finisher: the colossus overloads into a star and detonates, flattening the arena. */
public final class SupernovaAttack extends BossAttack {

    private static final Color SOLAR_GOLD = Color.fromRGB(255, 214, 120);
    private static final Color RADIANT_WHITE = Color.fromRGB(255, 250, 220);

    private final double damage;
    private final double blastRadius;
    private final double craterRadius;
    private final int chargeTicks;

    public SupernovaAttack(WeaponsPlugin plugin) {
        super(plugin, "solar_colossus");
        this.damage = configDouble("supernova-damage", 20.0);
        this.blastRadius = configDouble("supernova-blast-radius", 13.0);
        this.craterRadius = configDouble("supernova-crater-radius", 6.0);
        this.chargeTicks = configInt("supernova-charge-ticks", 30);
    }

    @Override
    public String name() {
        return "Supernova";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("supernova-cooldown-seconds", 26.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        int[] charge = {0};
        sequence(chargeTicks,
                () -> {
                    charge[0]++;
                    double t = Math.min(1.0, charge[0] / (double) chargeTicks);
                    // A tightening, brightening star gathering around the colossus.
                    Fx.coloredRing(origin, SOLAR_GOLD, 1.4f + (float) t * 1.2f, 6.0 * (1.0 - t) + 1.0, 42, charge[0] * 0.4);
                    Fx.helixFrame(origin, Particle.END_ROD, 2.0 * (1.0 - t) + 0.5, 6, charge[0] * 0.5, (charge[0] % 6) * 0.5);
                    Fx.coloredBurst(origin.clone().add(0, 1.4, 0), RADIANT_WHITE, 1.0f + (float) t, 14, 0.3);
                    if (charge[0] % 6 == 0) {
                        Fx.sound(origin, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.5f + (float) t);
                    }
                },
                () -> {
                    Fx.flash(origin.clone().add(0, 1.4, 0), 6);
                    Fx.coloredBurst(origin.clone().add(0, 1.4, 0), RADIANT_WHITE, 2.2f, 130, 1.0);
                    Fx.burst(origin.clone().add(0, 1.4, 0), Particle.FLAME, 40, 0.8);
                    Fx.expandingRings(plugin, origin, Particle.END_ROD, craterRadius * 1.4, 6, 2L);
                    BossAudio.play(origin, "boss.solar_colossus.supernova", Sound.ENTITY_GENERIC_EXPLODE, 1.75f, 0.4f);
                    Fx.sound(origin, Sound.ENTITY_WITHER_SPAWN, 1.35f, 0.5f);
                    // The long, visibly tightening charge is the telegraph — sprinting clear of the
                    // colossus during it is a real way to blunt or dodge the hit, not just flavor while
                    // the same damage lands on everyone in the arena regardless of distance.
                    for (Player player : ctx.arena().playersInside()) {
                        double distSq = player.getLocation().distanceSquared(origin);
                        if (distSq > blastRadius * blastRadius) {
                            continue;
                        }
                        double falloff = 1.0 - Math.min(1.0, Math.sqrt(distSq) / blastRadius) * 0.5;
                        player.damage(damage * falloff, ctx.boss());
                        player.setFireTicks(80);
                        Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                    }
                    Grief.explosion(ctx, origin, ctx.instance().boss().maxExplosionPower());
                    Grief.breakCrater(ctx, origin, craterRadius);
                },
                20, onComplete);
    }
}
