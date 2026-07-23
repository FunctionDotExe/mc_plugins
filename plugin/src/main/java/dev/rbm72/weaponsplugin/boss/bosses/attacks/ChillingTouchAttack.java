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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A gliding lunge that chills whoever it touches — heavy hit plus Slowness III. */
public final class ChillingTouchAttack extends BossAttack {

    private static final Color FROST = Color.fromRGB(150, 220, 255);

    private final double damage;
    private final double dashSpeed;
    private final double hitRadius;
    private final int slowTicks;
    private final int slowAmplifier;
    private final int dashTicks;
    private final int telegraphTicks;

    public ChillingTouchAttack(WeaponsPlugin plugin) {
        super(plugin, "frost_queen");
        this.damage = configDouble("chilling-touch-damage", 10.0);
        this.dashSpeed = configDouble("chilling-touch-speed", 1.6);
        this.hitRadius = configDouble("chilling-touch-hit-radius", 1.9);
        this.slowTicks = configInt("chilling-touch-slow-ticks", 60);
        this.slowAmplifier = configInt("chilling-touch-slow-amplifier", 2);
        this.dashTicks = configInt("chilling-touch-dash-ticks", 10);
        this.telegraphTicks = configInt("chilling-touch-telegraph-ticks", 14);
    }

    @Override
    public String name() {
        return "Chilling Touch";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("chilling-touch-cooldown-seconds", 8.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        LivingEntity boss = ctx.boss();
        Location targetLoc = ctx.target().getLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.line(boss.getLocation().add(0, 1, 0), ctx.target().getLocation().add(0, 1, 0), Particle.SNOWFLAKE);
                    Fx.coloredBurst(boss.getLocation().add(0, 1, 0), FROST, 1.3f, 16, 0.3);
                },
                () -> {
                    Movement.dash(boss, targetLoc, dashSpeed);
                    BossAudio.play(boss.getLocation(), "boss.frost_queen.chilling_touch", Sound.ENTITY_PLAYER_HURT_FREEZE, 1.3f, 0.9f);
                    Fx.sound(boss.getLocation(), Sound.BLOCK_GLASS_PLACE, 1.3f, 0.7f);
                    Set<UUID> alreadyHit = new HashSet<>();
                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            if (!boss.isValid() || elapsed >= dashTicks) {
                                cancel();
                                return;
                            }
                            Fx.trail(boss.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 9, 0.2, 0.01);
                            Fx.coloredBurst(boss.getLocation().add(0, 1, 0), FROST, 1.0f, 8, 0.15);
                            Fx.point(boss.getLocation().add(0, 0.2, 0), Particle.ITEM_SNOWBALL, 5);
                            for (Entity nearby : boss.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                                if (nearby instanceof LivingEntity target && !nearby.equals(boss)
                                        && !ctx.instance().addManager().isTracked(nearby.getUniqueId())
                                        && alreadyHit.add(nearby.getUniqueId())) {
                                    target.damage(damage, boss);
                                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowAmplifier));
                                    Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                                    Fx.coloredBurst(target.getLocation().add(0, 1, 0), FROST, 1.8f, 26, 0.5);
                                }
                            }
                            elapsed++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }
}
