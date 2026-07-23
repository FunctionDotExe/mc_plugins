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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Enrage finisher: reality caves in — every player is dragged to center, hoisted, then slammed as pits burst open. */
public final class CollapseAttack extends BossAttack {

    private static final Color VOID_BLACK = Color.fromRGB(25, 0, 40);
    private static final Color VOID_PURPLE = Color.fromRGB(120, 40, 180);

    private final double damage;
    private final double pullStrength;
    private final int pullTicks;
    private final int levitationTicks;
    private final int pits;
    private final double pitRadius;
    private final double slamRadius;
    private final int telegraphTicks;

    public CollapseAttack(WeaponsPlugin plugin) {
        super(plugin, "void_sovereign");
        this.damage = configDouble("collapse-damage", 16.0);
        this.pullStrength = configDouble("collapse-pull-strength", 0.4);
        this.pullTicks = configInt("collapse-pull-ticks", 40);
        this.levitationTicks = configInt("collapse-levitation-ticks", 30);
        this.pits = configInt("collapse-pits", 4);
        this.pitRadius = configDouble("collapse-pit-radius", 2.5);
        this.slamRadius = configDouble("collapse-slam-radius", 5.5);
        this.telegraphTicks = configInt("collapse-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Collapse";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("collapse-cooldown-seconds", 18.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(center, ctx.arena().radius() * 0.5);
                    Fx.coloredRing(center, VOID_BLACK, 1.8f, 5.0, 54, telegraphTicks * 0.1);
                    Fx.helixFrame(center, Particle.PORTAL, 2.5, 7, telegraphTicks * 0.3, 1.5);
                },
                () -> {
                    BossAudio.play(center, "boss.void_sovereign.collapse", Sound.BLOCK_PORTAL_TRIGGER, 1.2f, 0.4f);
                    Fx.sound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);

                    Location pullPoint = center.clone().add(0, 1, 0);
                    new BukkitRunnable() {
                        int ticks = 0;
                        double angle = 0;
                        boolean slammed = false;

                        @Override
                        public void run() {
                            if (!ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            if (ticks < pullTicks) {
                                Fx.ring(pullPoint, Particle.PORTAL, 3.0, 46, angle);
                                Fx.ring(pullPoint.clone().add(0, 0.8, 0), Particle.REVERSE_PORTAL, 2.2, 34, angle + 0.5);
                                angle += 0.4;
                                for (Player player : ctx.arena().playersInside()) {
                                    Vector toPoint = pullPoint.toVector().subtract(player.getLocation().toVector());
                                    if (toPoint.lengthSquared() <= 1.0E-6) {
                                        continue;
                                    }
                                    Vector pull = toPoint.normalize().multiply(pullStrength);
                                    player.setVelocity(player.getVelocity().add(pull));
                                    if (ticks == 0) {
                                        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, levitationTicks, 1));
                                    }
                                }
                                ticks++;
                                return;
                            }

                            if (!slammed) {
                                slammed = true;
                                Fx.burst(center, Particle.EXPLOSION_EMITTER, 3, 0.2);
                                Fx.coloredBurst(center.clone().add(0, 1, 0), VOID_PURPLE, 3.0f, 100, 5.0);
                                Fx.coloredBurst(center.clone().add(0, 1, 0), VOID_BLACK, 2.2f, 60, 5.5);
                                Fx.expandingRings(plugin, center, Particle.PORTAL, 8.0, 6, 2L);
                                Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                                // Only whoever the pull actually dragged in range takes the slam — fighting
                                // the pull for the whole channel (Singularity already rewards this the same
                                // way) is a real way out, not just flavor while the same damage lands anyway.
                                for (Player player : ctx.arena().playersInside()) {
                                    player.removePotionEffect(PotionEffectType.LEVITATION);
                                    if (player.getLocation().distanceSquared(center) > slamRadius * slamRadius) {
                                        continue;
                                    }
                                    player.setVelocity(new Vector(0, -1.4, 0));
                                    player.damage(damage, ctx.boss());
                                    Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                                }
                                // Multiple pits burst open across the arena floor.
                                double spread = Math.max(2.0, ctx.arena().radius() * 0.5);
                                for (int i = 0; i < pits; i++) {
                                    double ang = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
                                    double dist = ThreadLocalRandom.current().nextDouble(0, spread);
                                    Location pit = center.clone().add(Math.cos(ang) * dist, -2, Math.sin(ang) * dist);
                                    Grief.breakCrater(ctx, pit, pitRadius);
                                }
                            }
                            cancel();
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                14, onComplete);
    }
}
