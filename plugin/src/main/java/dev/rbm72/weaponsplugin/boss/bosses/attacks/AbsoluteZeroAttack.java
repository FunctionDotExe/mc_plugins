package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Enrage finisher: a long telegraph then an arena-wide flash-freeze that shatters the ground. */
public final class AbsoluteZeroAttack extends BossAttack {

    private static final Color FROST = Color.fromRGB(180, 235, 255);

    private final double damage;
    private final double radius;
    private final int slowTicks;
    private final int slowAmplifier;
    private final double craterRadius;
    private final int telegraphTicks;

    public AbsoluteZeroAttack(WeaponsPlugin plugin) {
        super(plugin, "frost_queen");
        this.damage = configDouble("absolute-zero-damage", 16.0);
        this.radius = configDouble("absolute-zero-radius", 12.0);
        this.slowTicks = configInt("absolute-zero-slow-ticks", 60);
        this.slowAmplifier = configInt("absolute-zero-slow-amplifier", 5);
        this.craterRadius = configDouble("absolute-zero-crater-radius", 4.0);
        this.telegraphTicks = configInt("absolute-zero-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Absolute Zero";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("absolute-zero-cooldown-seconds", 22.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, FROST, 2.0f, radius, 64, 0);
                    Fx.point(origin.clone().add(0, 2.5, 0), Particle.SNOWFLAKE, 10);
                },
                () -> {
                    BossAudio.play(origin, "boss.frost_queen.absolute_zero", Sound.ENTITY_PLAYER_HURT_FREEZE, 1.4f, 0.4f);
                    Fx.sound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.5f);
                    origin.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, origin.clone().add(0, 1, 0), 15, 0.96, 0.96, 0.96, 0);
                    origin.getWorld().spawnParticle(Particle.SNOWFLAKE, origin.clone().add(0, 2, 0), 960, radius * 1.12, 8.0, radius * 1.12, 0.05);
                    Fx.expandingRings(plugin, origin, Particle.SNOWFLAKE, radius, 7, 2L);
                    Fx.coloredBurst(origin.clone().add(0, 1.5, 0), FROST, 2.6f, 100, radius * 0.4);
                    Fx.coloredRing(origin, Color.fromRGB(230, 250, 255), 1.6f, radius * 1.3, 48, 0);
                    // The long telegraph is the counterplay: sprinting outside `radius` before it lands
                    // avoids the hit entirely. (Previously `radius` was declared but never checked here —
                    // every player in the arena took the full hit no matter how far they'd run.)
                    for (Player player : ctx.arena().playersInside()) {
                        if (player.getLocation().distanceSquared(origin) > radius * radius) {
                            continue;
                        }
                        player.damage(damage, ctx.boss());
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowAmplifier));
                        Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                    }
                    Grief.breakCrater(ctx, origin, craterRadius);
                },
                16, onComplete);
    }
}
