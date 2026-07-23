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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Encases the target in a rising bubble column, launching them helplessly into the air. */
public final class BubbleTrapAttack extends BossAttack {

    private static final Color TEAL = Color.fromRGB(40, 200, 200);
    private static final Color PALE = Color.fromRGB(160, 240, 250);

    private final double damage;
    private final double launchPower;
    private final int columnTicks;
    private final int telegraphTicks;

    public BubbleTrapAttack(WeaponsPlugin plugin) {
        super(plugin, "tide_leviathan");
        this.damage = configDouble("bubble-trap-damage", 6.0);
        this.launchPower = configDouble("bubble-trap-launch-power", 1.1);
        this.columnTicks = configInt("bubble-trap-column-ticks", 20);
        this.telegraphTicks = configInt("bubble-trap-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Bubble Trap";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("bubble-trap-cooldown-seconds", 10.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Player victim = ctx.target();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(victim.getLocation(), 1.5);
                    Fx.coloredRing(victim.getLocation(), TEAL, 1.3f, 1.5, 26, 0);
                },
                () -> {
                    Location loc = victim.getLocation();
                    BossAudio.play(loc, "boss.tide_leviathan.bubble_trap", Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.3f, 0.8f);
                    Fx.sound(loc, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.3f, 0.7f);
                    Movement.launchTarget(victim, launchPower);
                    victim.damage(damage, ctx.boss());
                    Fx.bloodSpray(loc.add(0, 1, 0));
                    Fx.coloredBurst(victim.getLocation().add(0, 1, 0), PALE, 1.9f, 50, 0.5);
                    Fx.coloredBurst(victim.getLocation().add(0, 1, 0), TEAL, 1.4f, 26, 0.6);
                    // A rising bubble column follows the launched target for the flight.
                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= columnTicks || !victim.isValid() || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            Location at = victim.getLocation();
                            at.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, at.clone().add(0, 0.5, 0), 96, 0.64, 1.28, 0.64, 0.05);
                            at.getWorld().spawnParticle(Particle.BUBBLE, at.clone().add(0, 1, 0), 57, 0.64, 1.28, 0.64, 0.02);
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }
}
