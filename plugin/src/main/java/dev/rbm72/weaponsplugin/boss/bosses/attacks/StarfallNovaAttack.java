package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * The Voidwyrm's enrage finale: a collapsing star drags everything toward its center for a few
 * seconds before it detonates outward — the one attack in the fight that punishes standing still
 * as hard as it punishes getting close.
 */
public final class StarfallNovaAttack extends BossAttack {

    private static final Color VOID_PURPLE = Color.fromRGB(110, 20, 160);
    private static final Color STARLIGHT = Color.fromRGB(220, 180, 255);

    private final double pullRadius;
    private final double pullStrength;
    private final int pullDurationTicks;
    private final double detonationDamage;
    private final double detonationRadius;
    private final int telegraphTicks;

    public StarfallNovaAttack(WeaponsPlugin plugin) {
        super(plugin, "voidwyrm");
        this.pullRadius = configDouble("starfall-nova-pull-radius", 9.0);
        this.pullStrength = configDouble("starfall-nova-pull-strength", 0.22);
        this.pullDurationTicks = configInt("starfall-nova-pull-duration-ticks", 50);
        this.detonationDamage = configDouble("starfall-nova-detonation-damage", 13.0);
        this.detonationRadius = configDouble("starfall-nova-detonation-radius", 6.0);
        this.telegraphTicks = configInt("starfall-nova-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Starfall Nova";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("starfall-nova-cooldown-seconds", 30.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(center, VOID_PURPLE, 1.6f, 3.0, 26, 0);
                    Fx.point(center.clone().add(0, 1.4, 0), Particle.PORTAL, 6);
                },
                () -> {
                    ctx.instance().showTitle(
                            Component.text("STARFALL", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                            Component.text("Get clear before it collapses", NamedTextColor.GRAY));
                    BossAudio.play(center, "boss.voidwyrm.starfall_nova", Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 0.5f);

                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= pullDurationTicks || !ctx.boss().isValid()) {
                                cancel();
                                detonate(ctx, center);
                                return;
                            }
                            Fx.coloredBurst(center.clone().add(0, 1.2, 0), VOID_PURPLE, 1.4f, 10, 0.3);
                            Fx.point(center.clone().add(0, 1.2, 0), Particle.PORTAL, 8);
                            for (Entity nearby : ctx.boss().getNearbyEntities(pullRadius, pullRadius, pullRadius)) {
                                if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                        && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                                    Vector toCenter = center.toVector().subtract(target.getLocation().toVector());
                                    if (toCenter.lengthSquared() > 1.0) {
                                        target.setVelocity(target.getVelocity().add(toCenter.normalize().multiply(pullStrength)));
                                    }
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                50, onComplete);
    }

    private void detonate(AttackContext ctx, Location center) {
        Fx.expandingRings(plugin, center, Particle.PORTAL, detonationRadius, 4, 2L);
        Fx.coloredBurst(center.clone().add(0, 1, 0), STARLIGHT, 2.4f, 60, 0.9);
        Fx.burst(center.clone().add(0, 1, 0), Particle.EXPLOSION_EMITTER, 4, 0.3);
        BossAudio.play(center, "boss.voidwyrm.starfall_detonate", Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.7f);
        // A real collapsing-star blast, not just an emitter particle.
        Grief.explosion(ctx, center, 2.5f);
        for (Entity nearby : ctx.boss().getNearbyEntities(detonationRadius, detonationRadius, detonationRadius)) {
            if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                    && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                target.damage(detonationDamage, ctx.boss());
                Vector outward = target.getLocation().toVector().subtract(center.toVector()).setY(0.3).normalize();
                target.setVelocity(outward.multiply(1.4));
                Fx.bloodSpray(target.getLocation().add(0, 1, 0));
            }
        }
    }
}
