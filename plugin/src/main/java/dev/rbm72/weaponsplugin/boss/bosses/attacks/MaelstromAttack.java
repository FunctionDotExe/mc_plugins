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
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Enrage finisher: the whole arena becomes a drowning maelstrom — everyone is dragged to the center, slowed, and crushed. */
public final class MaelstromAttack extends BossAttack {

    private static final Color TEAL = Color.fromRGB(40, 200, 200);
    private static final Color DEEP_TEAL = Color.fromRGB(10, 90, 130);

    private final double damagePerSecond;
    private final double pullStrength;
    private final double floodRadius;
    private final int slowAmplifier;
    private final int airDrainPerTick;
    private final int durationTicks;
    private final int telegraphTicks;

    public MaelstromAttack(WeaponsPlugin plugin) {
        super(plugin, "tide_leviathan");
        this.damagePerSecond = configDouble("maelstrom-damage-per-second", 3.0);
        this.pullStrength = configDouble("maelstrom-pull-strength", 0.28);
        this.floodRadius = configDouble("maelstrom-flood-radius", 6.0);
        this.slowAmplifier = configInt("maelstrom-slow-amplifier", 2);
        this.airDrainPerTick = configInt("maelstrom-air-drain-per-tick", 6);
        this.durationTicks = configInt("maelstrom-duration-ticks", 160);
        this.telegraphTicks = configInt("maelstrom-telegraph-ticks", 24);
    }

    @Override
    public String name() {
        return "Maelstrom";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("maelstrom-cooldown-seconds", 24.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location center = ctx.arena().center();
        double arenaRadius = ctx.arena().radius();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(center, arenaRadius * 0.9);
                    Fx.coloredRing(center, TEAL, 1.8f, arenaRadius * 0.9, 78, 0);
                    Fx.point(center.clone().add(0, 2.5, 0), Particle.BUBBLE, 13);
                },
                () -> {
                    BossAudio.play(center, "boss.tide_leviathan.maelstrom", Sound.ITEM_TRIDENT_RIPTIDE_3, 1.3f, 0.4f);
                    Fx.sound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.6f);
                    Grief.spread(ctx, center, Material.WATER, floodRadius);
                    new BukkitRunnable() {
                        int ticks = 0;
                        double angle = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                cancel();
                                if (ctx.boss().isValid()) {
                                    Fx.coloredBurst(center.clone().add(0, 1, 0), DEEP_TEAL, 2.2f, 100, arenaRadius * 0.4);
                                    Fx.burst(center.clone().add(0, 1, 0), Particle.SPLASH, 40, arenaRadius * 0.35);
                                    Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.6f);
                                }
                                return;
                            }
                            // Sweeping vortex rings across the arena footprint.
                            double r = arenaRadius * (0.3 + 0.6 * Math.abs(Math.cos(ticks * 0.08)));
                            Fx.ring(center, Particle.SPLASH, r, 74, angle);
                            Fx.ring(center.clone().add(0, 1.0, 0), Particle.BUBBLE, r * 0.75, 56, angle + 0.7);
                            Fx.ring(center.clone().add(0, 2.0, 0), Particle.SPLASH, r * 0.5, 38, angle + 1.4);
                            center.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, center.clone().add(0, 0.5, 0),
                                    150, floodRadius * 0.96, 1.6, floodRadius * 0.96, 0.05);
                            angle += 0.5;

                            for (Player player : ctx.arena().playersInside()) {
                                Vector toCenter = center.toVector().subtract(player.getLocation().toVector());
                                toCenter.setY(0);
                                if (toCenter.lengthSquared() > 1.0e-4) {
                                    player.setVelocity(player.getVelocity().add(toCenter.normalize().multiply(pullStrength)));
                                }
                                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, slowAmplifier));
                                player.setRemainingAir(player.getRemainingAir() - airDrainPerTick);
                                if (ticks % 20 == 0) {
                                    tickHurt(ctx, player, damagePerSecond);
                                    Fx.bloodSpray(player.getLocation().add(0, 1, 0));
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                16, onComplete);
    }
}
