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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Opens several growing void zones around the arena; standing in one bleeds health and drags you downward. */
public final class VoidZoneAttack extends BossAttack {

    private static final Color VOID_BLACK = Color.fromRGB(25, 0, 40);
    private static final Color VOID_PURPLE = Color.fromRGB(120, 40, 180);

    private final double damagePerSecond;
    private final int zones;
    private final double startRadius;
    private final double maxRadius;
    private final int durationTicks;
    private final int telegraphTicks;

    public VoidZoneAttack(WeaponsPlugin plugin) {
        super(plugin, "void_sovereign");
        this.damagePerSecond = configDouble("void-zone-damage-per-second", 3.0);
        this.zones = configInt("void-zone-count", 3);
        this.startRadius = configDouble("void-zone-start-radius", 2.0);
        this.maxRadius = configDouble("void-zone-max-radius", 4.5);
        this.durationTicks = configInt("void-zone-duration-ticks", 160);
        this.telegraphTicks = configInt("void-zone-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Void Zone";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("void-zone-cooldown-seconds", 15.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location arenaCenter = ctx.arena().center();
        double arenaRadius = ctx.arena().radius();
        List<Location> centers = new ArrayList<>();
        for (int i = 0; i < zones; i++) {
            double ang = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(0, Math.max(1.0, arenaRadius * 0.6));
            centers.add(arenaCenter.clone().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist));
        }

        sequence(telegraphTicks,
                () -> {
                    for (Location c : centers) {
                        Fx.coloredRing(c, VOID_PURPLE, 1.4f, startRadius, 30, 0);
                    }
                },
                () -> {
                    BossAudio.play(arenaCenter, "boss.void_sovereign.void_zone", Sound.BLOCK_PORTAL_TRIGGER, 1.15f, 0.6f);
                    Fx.sound(arenaCenter, Sound.AMBIENT_CAVE, 1.1f, 0.5f);

                    // Rifts grow over their lifetime; players caught inside take periodic damage and are
                    // dragged downward. Self-cancels on boss death or when the duration expires.
                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (!ctx.boss().isValid() || ticks >= durationTicks) {
                                cancel();
                                return;
                            }
                            double progress = ticks / (double) durationTicks;
                            double radius = startRadius + (maxRadius - startRadius) * progress;
                            for (Location c : centers) {
                                Fx.coloredRing(c, VOID_BLACK, 1.6f, radius, 38, ticks * 0.15);
                                Fx.point(c.clone().add(0, 0.2, 0), Particle.REVERSE_PORTAL, 7);
                            }
                            if (ticks % 20 == 0) {
                                for (Player player : ctx.arena().playersInside()) {
                                    for (Location c : centers) {
                                        if (player.getLocation().distanceSquared(c) <= radius * radius) {
                                            tickHurt(ctx, player, damagePerSecond);
                                            player.setVelocity(player.getVelocity().add(new Vector(0, -0.4, 0)));
                                            Fx.coloredBurst(player.getLocation().add(0, 1, 0), VOID_PURPLE, 1.3f, 12, 0.4);
                                            Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                                            break;
                                        }
                                    }
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }
}
