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
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** A slow homing orb of lightning that drifts toward the target and detonates on contact. */
public final class BallLightningAttack extends BossAttack {

    private static final Color STORM_YELLOW = Color.fromRGB(255, 240, 120);

    private final double damage;
    private final double speed;
    private final double contactRadius;
    private final float explosionPower;
    private final int orbs;
    private final int maxTicks;
    private final int telegraphTicks;

    public BallLightningAttack(WeaponsPlugin plugin) {
        super(plugin, "storm_tyrant");
        this.damage = configDouble("ball-lightning-damage", 10.0);
        this.speed = configDouble("ball-lightning-speed", 0.35);
        this.contactRadius = configDouble("ball-lightning-contact-radius", 1.8);
        this.explosionPower = (float) configDouble("ball-lightning-explosion-power", 1.5);
        this.orbs = configInt("ball-lightning-orbs", 1);
        this.maxTicks = configInt("ball-lightning-max-ticks", 120);
        this.telegraphTicks = configInt("ball-lightning-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Ball Lightning";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("ball-lightning-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredRing(origin, STORM_YELLOW, 1.4f, 1.8, 28, 0);
                    Fx.point(origin.clone().add(0, 2.0, 0), Particle.ELECTRIC_SPARK, 8);
                },
                () -> {
                    BossAudio.play(origin, "boss.storm_tyrant.ball_lightning", Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.3f, 0.9f);
                    Fx.sound(origin, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
                    for (int i = 0; i < orbs; i++) {
                        launchOrb(ctx, origin.clone().add(0, 1.5, 0));
                    }
                },
                14, onComplete);
    }

    private void launchOrb(AttackContext ctx, Location start) {
        Location orb = start.clone();
        ItemDisplay icon = Fx.spinningIcon(plugin, orb, Material.LIGHTNING_ROD, 0.6f, maxTicks + 5, 30.0);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!ctx.boss().isValid() || ticks >= maxTicks) {
                    if (icon != null && !icon.isDead()) {
                        icon.remove();
                    }
                    cancel();
                    return;
                }
                // Home slowly toward the current target's chest.
                Location aim = ctx.target().getLocation().add(0, 1, 0);
                Vector step = aim.toVector().subtract(orb.toVector());
                if (step.lengthSquared() > 0.01) {
                    orb.add(step.normalize().multiply(speed));
                }
                if (icon != null && !icon.isDead()) {
                    icon.teleport(orb);
                }
                Fx.point(orb, Particle.ELECTRIC_SPARK, 9);
                Fx.coloredBurst(orb, STORM_YELLOW, 1.2f, 6, 0.15);

                for (Player player : ctx.arena().playersInside()) {
                    if (player.getLocation().add(0, 1, 0).distanceSquared(orb) <= contactRadius * contactRadius) {
                        player.damage(damage, ctx.boss());
                        Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                        Fx.coloredBurst(orb, STORM_YELLOW, 1.8f, 30, 0.5);
                        Fx.flash(orb, 3);
                        Grief.explosion(ctx, orb, explosionPower);
                        Fx.sound(orb, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.3f, 1.2f);
                        if (icon != null && !icon.isDead()) {
                            icon.remove();
                        }
                        cancel();
                        return;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
