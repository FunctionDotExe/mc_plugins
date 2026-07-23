package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.ai.Movement;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Boss dashes through the target's position, hitting everything along the path once. */
public final class DashSlashAttack extends BossAttack {

    private final double damage;
    private final double dashSpeed;
    private final double hitRadius;
    private final int dashTicks;
    private final int telegraphTicks;

    public DashSlashAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("dash-slash-damage", 8.0);
        this.dashSpeed = configDouble("dash-slash-speed", 1.7);
        this.hitRadius = configDouble("dash-slash-hit-radius", 1.8);
        this.dashTicks = configInt("dash-slash-dash-ticks", 10);
        this.telegraphTicks = configInt("dash-slash-telegraph-ticks", 14);
    }

    @Override
    public String name() {
        return "Dash Slash";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("dash-slash-cooldown-seconds", 7.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location targetLoc = ctx.target().getLocation();

        sequence(telegraphTicks,
                () -> Telegraph.line(boss.getLocation().add(0, 1, 0), targetLoc.clone().add(0, 1, 0), Particle.CRIT),
                () -> {
                    Movement.dash(boss, targetLoc, dashSpeed);
                    BossAudio.play(boss.getLocation(), "boss.fallen_king.dash", Sound.ITEM_TRIDENT_RIPTIDE_1, 1.3f, 0.7f);
                    Fx.coloredBurst(boss.getLocation().add(0, 1, 0), Color.fromRGB(160, 20, 20), 1.3f, 20, 0.3);

                    Set<UUID> alreadyHit = new HashSet<>();
                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            if (!boss.isValid() || elapsed >= dashTicks) {
                                if (boss.isValid()) {
                                    Fx.coloredBurst(boss.getLocation().add(0, 1, 0), Color.fromRGB(160, 20, 20), 1.7f, 32, 0.4);
                                    Fx.burst(boss.getLocation().add(0, 1, 0), Particle.CRIT, 22, 0.4);
                                    Fx.sound(boss.getLocation(), Sound.ITEM_TRIDENT_HIT_GROUND, 1.3f, 0.8f);
                                }
                                cancel();
                                return;
                            }
                            // Trailing comet — a colored core with a faint reversed-portal "afterimage" ribbon.
                            Fx.trail(boss.getLocation().add(0, 1, 0), Particle.CRIT, 9, 0.2, 0.01);
                            Fx.coloredBurst(boss.getLocation().add(0, 1, 0), Color.fromRGB(200, 40, 40), 1.0f, 6, 0.15);
                            Fx.point(boss.getLocation().add(0, 0.2, 0), Particle.REVERSE_PORTAL, 5);
                            for (Entity nearby : boss.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                                if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                                        && !ctx.instance().addManager().isTracked(nearby.getUniqueId())
                                        && alreadyHit.add(nearby.getUniqueId())) {
                                    target.damage(damage, boss);
                                    Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                                    Fx.coloredBurst(target.getLocation().add(0, 1, 0), Color.fromRGB(160, 20, 20), 1.4f, 18, 0.4);
                                }
                            }
                            elapsed++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }
}
