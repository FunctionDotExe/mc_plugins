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
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Tears the souls from everyone nearby in a necrotic nova, siphoning their essence to mend the overlord. */
public final class SoulDrainAttack extends BossAttack {

    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);

    private final double damage;
    private final double radius;
    private final double healFraction;
    private final int telegraphTicks;

    public SoulDrainAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.damage = configDouble("soul-drain-damage", 10.0);
        this.radius = configDouble("soul-drain-radius", 6.0);
        this.healFraction = configDouble("soul-drain-heal-fraction", 0.5);
        this.telegraphTicks = configInt("soul-drain-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Soul Drain";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("soul-drain-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(origin, NECROTIC, 1.5f, radius, 42, 0);
                    Fx.helixFrame(origin, Particle.SOUL, 1.4, 6, System.currentTimeMillis() * 0.01, 1.0);
                },
                () -> {
                    BossAudio.play(origin, "boss.necro_overlord.soul_drain", Sound.ENTITY_WITHER_AMBIENT, 1.15f, 0.7f);
                    Fx.sound(origin, Sound.PARTICLE_SOUL_ESCAPE, 1.1f, 0.5f);
                    Fx.expandingRings(plugin, origin, Particle.SCULK_SOUL, radius, 4, 3L);
                    origin.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, origin.clone().add(0, 0.2, 0), 6, 0, 0, 0, 0);

                    double totalDealt = 0;
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            totalDealt += damage;
                            Fx.line(target.getLocation().add(0, 1, 0), origin.clone().add(0, 1, 0), Particle.SOUL, 26);
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }

                    if (totalDealt > 0) {
                        double heal = totalDealt * healFraction;
                        double maxHealth = ctx.boss().getAttribute(Attribute.MAX_HEALTH).getValue();
                        ctx.boss().setHealth(Math.min(ctx.boss().getHealth() + heal, maxHealth));
                        Fx.coloredBurst(origin.clone().add(0, 1.2, 0), NECROTIC, 2.0f, 50, 0.9);
                        Fx.burst(origin.clone().add(0, 1.2, 0), Particle.SOUL, 24, 0.5);
                    }
                },
                14, onComplete);
    }
}
