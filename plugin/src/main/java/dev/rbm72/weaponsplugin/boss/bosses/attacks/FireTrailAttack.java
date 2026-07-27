package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.ai.Movement;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** The warlord charges across the arena, scorching everything in his wake and leaving a burning trail. */
public final class FireTrailAttack extends BossAttack {

    private static final Color EMBER = Color.fromRGB(255, 120, 0);

    private final double damage;
    private final double dashSpeed;
    private final double hitRadius;
    private final int fireTicks;
    private final int dashTicks;
    private final int telegraphTicks;

    public FireTrailAttack(WeaponsPlugin plugin) {
        super(plugin, "inferno_warlord");
        this.damage = configDouble("fire-trail-damage", 8.0);
        this.dashSpeed = configDouble("fire-trail-speed", 1.6);
        this.hitRadius = configDouble("fire-trail-hit-radius", 1.9);
        this.fireTicks = configInt("fire-trail-fire-ticks", 80);
        this.dashTicks = configInt("fire-trail-dash-ticks", 12);
        this.telegraphTicks = configInt("fire-trail-telegraph-ticks", 14);
    }

    @Override
    public String name() {
        return "Fire Trail";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("fire-trail-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location targetLoc = ctx.target().getLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.line(boss.getLocation().add(0, 1, 0), ctx.target().getLocation().add(0, 1, 0), Particle.FLAME);
                    Fx.coloredRing(boss.getLocation(), EMBER, 1.4f, 1.8, 26, 0);
                },
                () -> {
                    Movement.dash(boss, targetLoc, dashSpeed);
                    BossAudio.play(boss.getLocation(), "boss.inferno_warlord.fire_trail", Sound.ENTITY_BLAZE_SHOOT, 1.4f, 0.5f);
                    Fx.sound(boss.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.3f, 0.7f);
                    Set<UUID> alreadyHit = new HashSet<>();
                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            if (!boss.isValid() || elapsed >= dashTicks) {
                                if (boss.isValid()) {
                                    Fx.coloredBurst(boss.getLocation().add(0, 1, 0), EMBER, 2.0f, 38, 0.5);
                                    Fx.burst(boss.getLocation().add(0, 1, 0), Particle.FLAME, 20, 0.5);
                                    Fx.sound(boss.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.3f, 0.7f);
                                }
                                cancel();
                                return;
                            }
                            Location loc = boss.getLocation();
                            Fx.trail(loc.clone().add(0, 0.6, 0), Particle.FLAME, 19, 0.3, 0.02);
                            Fx.trail(loc.clone().add(0, 0.3, 0), Particle.LAVA, 5, 0.25, 0.01);
                            Fx.point(loc.clone().add(0, 0.1, 0), Particle.SMALL_FLAME, 9);
                            // Leave real fire only when grief is enabled; never place blocks otherwise.
                            // Routed through Grief.setBlock so the placement lands in the arena
                            // ledger — fire written straight to the world would survive the fight's
                            // rollback and keep spreading long after the boss was dead.
                            if (Grief.enabled(ctx)) {
                                Block feet = loc.getBlock();
                                if (feet.getType().isAir() && feet.getRelative(0, -1, 0).getType().isSolid()) {
                                    Grief.setBlock(ctx, feet, Material.FIRE);
                                }
                            }
                            for (Entity nearby : boss.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                                if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                                        && !ctx.instance().addManager().isTracked(nearby.getUniqueId())
                                        && alreadyHit.add(nearby.getUniqueId())) {
                                    target.damage(damage, boss);
                                    target.setFireTicks(fireTicks);
                                    Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                                    Fx.coloredBurst(target.getLocation().add(0, 1, 0), EMBER, 1.5f, 18, 0.4);
                                }
                            }
                            elapsed++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }
}
