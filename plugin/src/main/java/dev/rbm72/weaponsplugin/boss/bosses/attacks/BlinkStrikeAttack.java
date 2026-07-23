package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** The Sovereign blinks out of reality and reappears directly behind its target, striking on arrival. */
public final class BlinkStrikeAttack extends BossAttack {

    private static final Color VOID_PURPLE = Color.fromRGB(120, 40, 180);
    private static final Color DEEP_VOID = Color.fromRGB(45, 0, 70);

    private final double damage;
    private final double range;
    private final int telegraphTicks;

    public BlinkStrikeAttack(WeaponsPlugin plugin) {
        super(plugin, "void_sovereign");
        this.damage = configDouble("blink-strike-damage", 10.0);
        this.range = configDouble("blink-strike-range", 2.5);
        this.telegraphTicks = configInt("blink-strike-telegraph-ticks", 14);
    }

    @Override
    public String name() {
        return "Blink Strike";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("blink-strike-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        sequence(telegraphTicks,
                () -> {
                    Fx.burst(boss.getLocation().add(0, 1, 0), Particle.PORTAL, 13, 0.3);
                    Fx.coloredRing(boss.getLocation(), VOID_PURPLE, 1.4f, 1.6, 26, 0);
                },
                () -> {
                    // Reappear one step behind the target, facing their back.
                    Location targetLoc = ctx.target().getLocation();
                    Vector facing = targetLoc.getDirection().clone().setY(0);
                    // Looking straight up/down zeroes the horizontal component — can't normalize that.
                    Vector behind = (facing.lengthSquared() > 1.0E-6 ? facing.normalize() : new Vector(1, 0, 0)).multiply(-1.6);
                    Location arriveAt = targetLoc.clone().add(behind);

                    Fx.burst(boss.getLocation().add(0, 1, 0), Particle.PORTAL, 44, 0.5);
                    Fx.coloredBurst(boss.getLocation().add(0, 1, 0), DEEP_VOID, 1.9f, 36, 0.4);
                    boss.teleport(arriveAt);
                    Fx.burst(arriveAt.clone().add(0, 1, 0), Particle.PORTAL, 44, 0.4);
                    Fx.flash(arriveAt.clone().add(0, 1, 0), 1);
                    Fx.coloredBurst(arriveAt.clone().add(0, 1, 0), VOID_PURPLE, 1.7f, 32, 0.4);
                    Fx.coloredRing(arriveAt, DEEP_VOID, 1.4f, 1.6, 22, 0);
                    BossAudio.play(arriveAt, "boss.void_sovereign.blink_strike", Sound.ENTITY_ENDERMAN_TELEPORT, 1.3f, 0.7f);
                    Fx.sound(arriveAt, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, 0.6f);

                    for (Entity nearby : boss.getNearbyEntities(range, range, range)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, boss);
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                10, onComplete);
    }
}
