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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Real skeletal hands (evoker fangs) claw up from the ground around the overlord, rooting and clawing at anyone caught in them. */
public final class GraveGraspAttack extends BossAttack {

    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);

    private final double damage;
    private final double radius;
    private final int rootTicks;
    private final int telegraphTicks;

    public GraveGraspAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.damage = configDouble("grave-grasp-damage", 6.0);
        this.radius = configDouble("grave-grasp-radius", 5.0);
        this.rootTicks = configInt("grave-grasp-root-ticks", 40);
        this.telegraphTicks = configInt("grave-grasp-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Grave Grasp";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("grave-grasp-cooldown-seconds", 9.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, NECROTIC, 1.4f, radius, 42, 0);
                },
                () -> {
                    BossAudio.play(origin, "boss.necro_overlord.grave_grasp", Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.1f, 0.5f);
                    Fx.sound(origin, Sound.BLOCK_SOUL_SAND_STEP, 1.0f, 0.6f);
                    Fx.expandingRings(plugin, origin, Particle.SOUL, radius, 4, 3L);
                    origin.getWorld().spawnParticle(Particle.SCULK_SOUL, origin.clone().add(0, 0.3, 0), 204, radius * 0.8, 0.32, radius * 0.8, 0.02);
                    Fx.coloredBurst(origin.clone().add(0, 0.5, 0), NECROTIC, 1.6f, 30, radius * 0.4);
                    // Real skeletal hands: a ring of evoker fangs bursting up around the boss.
                    int fangCount = 10;
                    for (int i = 0; i < fangCount; i++) {
                        double angle = (2 * Math.PI * i) / fangCount;
                        Location spot = origin.clone().add(Math.cos(angle) * radius * 0.7, 0, Math.sin(angle) * radius * 0.7);
                        EvokerFangs fangs = origin.getWorld().spawn(spot, EvokerFangs.class);
                        fangs.setOwner(ctx.boss());
                    }
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, rootTicks, 5));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                },
                14, onComplete);
    }
}
