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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** A real cracked-stone ridge (raised blocks) extends toward the target, damaging and slowing anything standing along it. */
public final class GroundFissureAttack extends BossAttack {

    private final double damage;
    private final double range;
    private final double width;
    private final int slowTicks;
    private final int telegraphTicks;

    public GroundFissureAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("ground-fissure-damage", 6.0);
        this.range = configDouble("ground-fissure-range", 8.0);
        this.width = configDouble("ground-fissure-width", 1.5);
        this.slowTicks = configInt("ground-fissure-slow-ticks", 60);
        this.telegraphTicks = configInt("ground-fissure-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Ground Fissure";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("ground-fissure-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        Vector aimVector = ctx.target().getLocation().toVector().subtract(origin.toVector()).setY(0);
        Vector direction = aimVector.lengthSquared() > 1.0E-6 ? aimVector.normalize() : new Vector(1, 0, 0);
        Location end = origin.clone().add(direction.clone().multiply(range));

        sequence(telegraphTicks,
                () -> Telegraph.line(origin, end, Particle.CRIT),
                () -> {
                    BossAudio.play(origin, "boss.fallen_king.fissure", Sound.BLOCK_STONE_BREAK, 1.0f, 0.6f);
                    Fx.sound(origin, Sound.ENTITY_RAVAGER_STEP, 1.0f, 0.5f);
                    World world = origin.getWorld();
                    if (world == null) {
                        return;
                    }
                    for (double d = 1; d <= range; d += 1.0) {
                        Location point = origin.clone().add(direction.clone().multiply(d));
                        Fx.point(point, Particle.CRIT, 7);
                        Fx.coloredBurst(point, Color.fromRGB(90, 20, 20), 1.1f, 9, 0.2);
                        world.spawnParticle(Particle.SMOKE, point.clone().add(0, 0.3, 0), 15, 0.24, 0.16, 0.24, 0.01);
                        Grief.raiseColumns(ctx, point, Material.COBBLED_DEEPSLATE, 1, 1, 0, 30);
                        for (Entity nearby : world.getNearbyEntities(point, width, width, width)) {
                            if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                    && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                                target.damage(damage, ctx.boss());
                                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 1));
                                Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                            }
                        }
                    }
                    // Final crack at the fissure's tip lands with a heavier stacked burst.
                    Fx.coloredBurst(end, Color.fromRGB(120, 30, 30), 1.4f, 20, 0.4);
                },
                12, onComplete);
    }
}
