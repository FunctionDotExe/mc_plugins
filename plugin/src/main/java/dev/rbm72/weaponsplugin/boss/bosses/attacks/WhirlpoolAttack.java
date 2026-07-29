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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Spins up a whirlpool at the target's position, dragging everyone inward and grinding them for several seconds. */
public final class WhirlpoolAttack extends BossAttack {

    private static final Color TEAL = Color.fromRGB(40, 200, 200);
    private static final Color DEEP_TEAL = Color.fromRGB(10, 90, 130);

    private final double damagePerSecond;
    private final double radius;
    private final double pullStrength;
    private final int durationTicks;
    private final int telegraphTicks;

    public WhirlpoolAttack(WeaponsPlugin plugin) {
        super(plugin, "tide_leviathan");
        this.damagePerSecond = configDouble("whirlpool-damage-per-second", 2.0);
        this.radius = configDouble("whirlpool-radius", 5.0);
        this.pullStrength = configDouble("whirlpool-pull-strength", 0.22);
        this.durationTicks = configInt("whirlpool-duration-ticks", 80);
        this.telegraphTicks = configInt("whirlpool-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Whirlpool";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("whirlpool-cooldown-seconds", 11.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.target().getLocation().clone();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(center, radius);
                    Fx.coloredRing(center, TEAL, 1.4f, radius, 42, 0);
                },
                () -> {
                    BossAudio.play(center, "boss.tide_leviathan.whirlpool", Sound.ITEM_TRIDENT_RIPTIDE_3, 1.15f, 0.6f);
                    Fx.sound(center, Sound.ENTITY_GENERIC_SPLASH, 1.1f, 0.5f);
                    Fx.spinningIcon(plugin, center.clone().add(0, 0.2, 0), org.bukkit.Material.HEART_OF_THE_SEA, 0.9f, durationTicks, 14);
                    new BukkitRunnable() {
                        int ticks = 0;
                        double angle = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            double r = radius * (0.4 + 0.6 * Math.abs(Math.cos(ticks * 0.12)));
                            Fx.ring(center, Particle.SPLASH, r, 42, angle);
                            Fx.ring(center.clone().add(0, 0.5, 0), Particle.BUBBLE, r * 0.8, 34, angle + 0.6);
                            Fx.ring(center.clone().add(0, 1.0, 0), Particle.SPLASH, r * 0.6, 28, angle + 1.2);
                            Fx.coloredBurst(center.clone().add(0, 0.1, 0), DEEP_TEAL, 1.2f, 14, r * 0.5);
                            angle += 0.4;

                            for (Player player : ctx.arena().playersInside()) {
                                Vector toCenter = center.toVector().subtract(player.getLocation().toVector());
                                toCenter.setY(0);
                                if (toCenter.lengthSquared() > radius * radius || toCenter.lengthSquared() < 1.0e-4) {
                                    continue;
                                }
                                player.setVelocity(player.getVelocity().add(toCenter.normalize().multiply(pullStrength)));
                                if (ticks % 20 == 0) {
                                    tickHurt(ctx, player, damagePerSecond);
                                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), TEAL, 1.2f, 10, 0.4);
                                    Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }
}
