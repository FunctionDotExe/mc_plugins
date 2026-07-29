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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** A roaming vortex that drags players toward its core and flings debris for several seconds. */
public final class TornadoAttack extends BossAttack {

    private static final Color STORM_WHITE = Color.fromRGB(235, 245, 255);

    private final double pullRadius;
    private final double pullStrength;
    private final double damagePerSecond;
    private final int blocksThrown;
    private final int durationTicks;
    private final int telegraphTicks;

    public TornadoAttack(WeaponsPlugin plugin) {
        super(plugin, "storm_tyrant");
        this.pullRadius = configDouble("tornado-pull-radius", 6.0);
        this.pullStrength = configDouble("tornado-pull-strength", 0.35);
        this.damagePerSecond = configDouble("tornado-damage-per-second", 2.0);
        this.blocksThrown = configInt("tornado-blocks-thrown", 3);
        this.durationTicks = configInt("tornado-duration-ticks", 120);
        this.telegraphTicks = configInt("tornado-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Tornado";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("tornado-cooldown-seconds", 18.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location core = ctx.target().getLocation().clone();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(core, pullRadius);
                    Fx.coloredRing(core, STORM_WHITE, 1.4f, pullRadius, 42, 0);
                },
                () -> {
                    BossAudio.play(core, "boss.storm_tyrant.tornado", Sound.ENTITY_ENDER_DRAGON_FLAP, 1.15f, 0.6f);
                    Fx.sound(core, Sound.ITEM_ELYTRA_FLYING, 1.1f, 0.8f);
                    // Interval between debris throws so all blocksThrown are spread across the duration.
                    int throwInterval = Math.max(20, durationTicks / Math.max(1, blocksThrown));
                    new BukkitRunnable() {
                        int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks >= durationTicks || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            // Rising helix core plus a drifting funnel of spark/cloud particles.
                            Fx.helixFrame(core, Particle.CLOUD, 1.8, 6, ticks * 0.6, (ticks % 40) * 0.12);
                            Fx.helixFrame(core, Particle.ELECTRIC_SPARK, 1.15, 5, ticks * 0.9, (ticks % 40) * 0.14 + 1.0);
                            Fx.point(core.clone().add(0, 0.2, 0), Particle.CLOUD, 7);
                            for (Player player : ctx.arena().playersInside()) {
                                double distSq = player.getLocation().distanceSquared(core);
                                if (distSq <= pullRadius * pullRadius) {
                                    Vector pull = core.toVector().subtract(player.getLocation().toVector());
                                    if (pull.lengthSquared() > 0.01) {
                                        player.setVelocity(player.getVelocity().add(pull.normalize().multiply(pullStrength)));
                                    }
                                    if (ticks % 20 == 0) {
                                        tickHurt(ctx, player, damagePerSecond);
                                        Fx.coloredBurst(player.getLocation().add(0, 1, 0), STORM_WHITE, 1.1f, 10, 0.3);
                                        Fx.burst(player.getLocation().add(0, 1, 0), Particle.CLOUD, 8, 0.3);
                                    }
                                }
                            }
                            if (ticks > 0 && ticks % throwInterval == 0) {
                                Grief.throwBlock(ctx, core.clone().add(0, 1.5, 0), ctx.target(), Material.DIRT, damagePerSecond, 0.0f);
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                12, onComplete);
    }
}
