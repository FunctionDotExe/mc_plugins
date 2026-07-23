package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Casts the target out: a short damage burst, then a forced blink several blocks away from the Sovereign. */
public final class BanishAttack extends BossAttack {

    private static final Color VOID_PURPLE = Color.fromRGB(120, 40, 180);

    private final double damage;
    private final double distance;
    private final int telegraphTicks;

    public BanishAttack(WeaponsPlugin plugin) {
        super(plugin, "void_sovereign");
        this.damage = configDouble("banish-damage", 5.0);
        this.distance = configDouble("banish-distance", 8.0);
        this.telegraphTicks = configInt("banish-telegraph-ticks", 14);
    }

    @Override
    public String name() {
        return "Banish";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("banish-cooldown-seconds", 10.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Player target = ctx.target();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(target);
                    Fx.coloredRing(target.getLocation(), VOID_PURPLE, 1.4f, 1.6, 26, 0);
                },
                () -> {
                    Location from = target.getLocation().clone();
                    Vector away = from.toVector().subtract(ctx.bossLocation().toVector()).setY(0);
                    if (away.lengthSquared() < 0.001) {
                        away = from.getDirection().setY(0);
                    }
                    // getDirection() can itself land near-zero after setY(0) (looking straight up/down
                    // while standing on the boss) — fall back to a fixed horizontal vector rather than
                    // normalize()-ing a second unchecked zero vector into NaN.
                    if (away.lengthSquared() < 0.001) {
                        away = new Vector(1, 0, 0);
                    }
                    Location to = from.clone().add(away.normalize().multiply(distance));
                    to.setPitch(from.getPitch());
                    to.setYaw(from.getYaw());

                    target.damage(damage, ctx.boss());
                    Fx.burst(from.clone().add(0, 1, 0), Particle.PORTAL, 48, 0.5);
                    Fx.coloredBurst(from.clone().add(0, 1, 0), VOID_PURPLE, 2.0f, 38, 0.5);
                    BossAudio.play(from, "boss.void_sovereign.banish", Sound.ENTITY_ENDERMAN_TELEPORT, 1.3f, 0.5f);
                    Fx.sound(from, Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.4f);

                    target.teleport(to);
                    Fx.burst(to.clone().add(0, 1, 0), Particle.PORTAL, 48, 0.4);
                    Fx.flash(to.clone().add(0, 1, 0), 1);
                    Fx.coloredBurst(to.clone().add(0, 1, 0), VOID_PURPLE, 1.6f, 26, 0.4);
                    Fx.bloodSpray(to.clone().add(0, 1, 0));
                },
                10, onComplete);
    }
}
