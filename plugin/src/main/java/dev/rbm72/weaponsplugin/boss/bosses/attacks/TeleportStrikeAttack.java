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

/** Teleports the boss just behind the target, then strikes immediately on arrival. */
public final class TeleportStrikeAttack extends BossAttack {

    private final double damage;
    private final double range;
    private final int telegraphTicks;

    public TeleportStrikeAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("teleport-strike-damage", 9.0);
        this.range = configDouble("teleport-strike-range", 2.5);
        this.telegraphTicks = configInt("teleport-strike-telegraph-ticks", 14);
    }

    @Override
    public String name() {
        return "Teleport Strike";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("teleport-strike-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        sequence(telegraphTicks,
                () -> {
                    Fx.burst(boss.getLocation().add(0, 1, 0), Particle.PORTAL, 10, 0.3);
                    Fx.ring(boss.getLocation(), Particle.REVERSE_PORTAL, 1.7, 18);
                },
                () -> {
                    Location targetLoc = ctx.target().getLocation();
                    Vector behind = targetLoc.getDirection().clone().multiply(-1.5).setY(0);
                    Location arriveAt = targetLoc.clone().add(behind);

                    // Vanishes into a collapsing void ring, reappears in a burst of light — the distortion sells the teleport.
                    Fx.burst(boss.getLocation().add(0, 1, 0), Particle.PORTAL, 42, 0.5);
                    Fx.coloredBurst(boss.getLocation().add(0, 1, 0), Color.fromRGB(70, 0, 90), 1.5f, 34, 0.4);
                    boss.teleport(arriveAt);
                    Fx.burst(arriveAt.clone().add(0, 1, 0), Particle.PORTAL, 42, 0.4);
                    Fx.flash(arriveAt.clone().add(0, 1, 0), 2);
                    Fx.burst(arriveAt.clone().add(0, 1, 0), Particle.END_ROD, 26, 0.3);
                    BossAudio.play(arriveAt, "boss.fallen_king.teleport_strike", Sound.ENTITY_ENDERMAN_TELEPORT, 1.15f, 0.7f);

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
