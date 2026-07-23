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

/** Heavy uppercut: the colossus lunges in and hammers the target skyward. */
public final class FistThrowAttack extends BossAttack {

    private static final Color SOLAR_GOLD = Color.fromRGB(255, 214, 120);

    private final double damage;
    private final double dashSpeed;
    private final double hitRadius;
    private final double launchUp;
    private final int dashTicks;
    private final int telegraphTicks;

    public FistThrowAttack(WeaponsPlugin plugin) {
        super(plugin, "solar_colossus");
        this.damage = configDouble("fist-throw-damage", 15.0);
        this.dashSpeed = configDouble("fist-throw-speed", 1.6);
        this.hitRadius = configDouble("fist-throw-hit-radius", 3.0);
        this.launchUp = configDouble("fist-throw-launch-up", 1.1);
        this.dashTicks = configInt("fist-throw-dash-ticks", 10);
        this.telegraphTicks = configInt("fist-throw-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Fist Throw";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("fist-throw-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location targetLoc = ctx.target().getLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.line(boss.getLocation().add(0, 1.2, 0), targetLoc.clone().add(0, 1, 0), Particle.END_ROD);
                    Fx.coloredBurst(boss.getLocation().add(0, 1.5, 0), SOLAR_GOLD, 1.4f, 16, 0.4);
                },
                () -> {
                    Movement.dash(boss, targetLoc, dashSpeed);
                    BossAudio.play(boss.getLocation(), "boss.solar_colossus.fist_throw", Sound.ENTITY_IRON_GOLEM_ATTACK, 1.6f, 0.7f);
                    Fx.coloredBurst(boss.getLocation().add(0, 1.5, 0), SOLAR_GOLD, 1.5f, 26, 0.4);
                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            if (!boss.isValid() || elapsed >= dashTicks) {
                                if (boss.isValid()) {
                                    Fx.coloredBurst(boss.getLocation().add(0, 1.5, 0), SOLAR_GOLD, 2.1f, 42, 0.5);
                                    Fx.burst(boss.getLocation().add(0, 1.5, 0), Particle.END_ROD, 20, 0.4);
                                    Fx.flash(boss.getLocation().add(0, 1.5, 0), 1);
                                    Fx.sound(boss.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.6f);
                                    for (Entity nearby : boss.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                                        if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                                            target.damage(damage, boss);
                                            Movement.launchTarget(target, launchUp);
                                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                                        }
                                    }
                                }
                                cancel();
                                return;
                            }
                            Fx.trail(boss.getLocation().add(0, 1.2, 0), Particle.END_ROD, 8, 0.2, 0.01);
                            elapsed++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }
}
