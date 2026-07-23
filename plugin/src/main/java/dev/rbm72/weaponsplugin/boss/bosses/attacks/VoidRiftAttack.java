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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Signature grief: tears open a pit straight down under the target, then launches everyone caught in it. */
public final class VoidRiftAttack extends BossAttack {

    private static final Color VOID_BLACK = Color.fromRGB(25, 0, 40);
    private static final Color VOID_PURPLE = Color.fromRGB(120, 40, 180);

    private final double damage;
    private final double radius;
    private final double pitRadius;
    private final int levitationTicks;
    private final int telegraphTicks;

    public VoidRiftAttack(WeaponsPlugin plugin) {
        super(plugin, "void_sovereign");
        this.damage = configDouble("void-rift-damage", 8.0);
        this.radius = configDouble("void-rift-radius", 3.5);
        this.pitRadius = configDouble("void-rift-pit-radius", 2.5);
        this.levitationTicks = configInt("void-rift-levitation-ticks", 30);
        this.telegraphTicks = configInt("void-rift-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Void Rift";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("void-rift-cooldown-seconds", 11.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.target().getLocation().clone();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(center, radius);
                    Fx.coloredRing(center, VOID_PURPLE, 1.6f, radius, 46, 0);
                    Fx.point(center.clone().add(0, 0.2, 0), Particle.REVERSE_PORTAL, 10);
                },
                () -> {
                    Fx.expandingRings(plugin, center, Particle.PORTAL, radius, 4, 3L);
                    Fx.coloredBurst(center.clone().add(0, 0.5, 0), VOID_BLACK, 2.0f, 66, radius * 0.4);
                    Fx.burst(center.clone().add(0, 0.5, 0), Particle.REVERSE_PORTAL, 24, 0.5);
                    center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 0.2, 0), 6, 0, 0, 0, 0);
                    BossAudio.play(center, "boss.void_sovereign.void_rift", Sound.BLOCK_PORTAL_TRIGGER, 1.15f, 0.5f);
                    Fx.sound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.95f, 0.7f);

                    for (Entity nearby : ctx.boss().getWorld().getNearbyEntities(center, radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, levitationTicks, 0));
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                    // Signature grief: hollow a pit straight down beneath the rift.
                    Grief.breakCrater(ctx, center.clone().subtract(0, 3, 0), pitRadius);
                },
                12, onComplete);
    }
}
