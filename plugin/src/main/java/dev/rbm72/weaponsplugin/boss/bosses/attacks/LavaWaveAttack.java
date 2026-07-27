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
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A rolling wall of lava sweeps out from the warlord toward the target, burning everything it rolls over. */
public final class LavaWaveAttack extends BossAttack {

    private static final Color EMBER = Color.fromRGB(255, 120, 0);
    private static final Color DEEP_FIRE = Color.fromRGB(200, 40, 0);

    private final double damage;
    private final double waveSpeed;
    private final double waveWidth;
    private final int fireTicks;
    private final int waveTicks;
    private final int telegraphTicks;

    public LavaWaveAttack(WeaponsPlugin plugin) {
        super(plugin, "inferno_warlord");
        this.damage = configDouble("lava-wave-damage", 11.0);
        this.waveSpeed = configDouble("lava-wave-speed", 0.9);
        this.waveWidth = configDouble("lava-wave-width", 3.0);
        this.fireTicks = configInt("lava-wave-fire-ticks", 80);
        this.waveTicks = configInt("lava-wave-ticks", 30);
        this.telegraphTicks = configInt("lava-wave-telegraph-ticks", 20);
    }

    @Override
    public String name() {
        return "Lava Wave";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("lava-wave-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        Location start = ctx.bossLocation().clone();
        Vector direction = ctx.target().getLocation().toVector().subtract(start.toVector()).setY(0);
        if (direction.lengthSquared() < 0.01) {
            direction = ctx.boss().getLocation().getDirection().setY(0);
        }
        Vector heading = direction.clone().normalize();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.line(start.clone().add(0, 0.5, 0),
                            start.clone().add(heading.clone().multiply(waveSpeed * waveTicks)).add(0, 0.5, 0), Particle.FLAME);
                    Fx.coloredRing(start, DEEP_FIRE, 1.4f, 2.2, 26, 0);
                },
                () -> {
                    BossAudio.play(start, "boss.inferno_warlord.lava_wave", Sound.BLOCK_LAVA_AMBIENT, 1.3f, 0.6f);
                    Fx.sound(start, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
                    Set<UUID> alreadyHit = new HashSet<>();
                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            if (elapsed >= waveTicks || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            Location front = start.clone().add(heading.clone().multiply(waveSpeed * elapsed));
                            front.setY(front.getWorld().getHighestBlockYAt(front) + 1);
                            // Draw the wavefront as a crossbar perpendicular to the heading.
                            Vector perp = new Vector(-heading.getZ(), 0, heading.getX()).normalize();
                            for (double w = -waveWidth; w <= waveWidth; w += 0.75) {
                                Location p = front.clone().add(perp.clone().multiply(w));
                                Fx.point(p, Particle.FLAME, 6);
                                Fx.point(p.clone().add(0, 0.4, 0), Particle.LAVA, 2);
                            }
                            Fx.coloredBurst(front.clone().add(0, 0.5, 0), EMBER, 1.5f, 20, waveWidth * 0.5);
                            // Through Grief.setBlock, not the block directly, so the ledger records it
                            // and the rollback can put the burnt ground back.
                            if (Grief.enabled(ctx)) {
                                Block feet = front.getBlock();
                                if (feet.getType().isAir() && feet.getRelative(0, -1, 0).getType().isSolid()) {
                                    Grief.setBlock(ctx, feet, Material.FIRE);
                                }
                            }
                            for (Entity nearby : front.getWorld().getNearbyEntities(front, waveWidth, 3.0, waveWidth)) {
                                if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                        && !ctx.instance().addManager().isTracked(nearby.getUniqueId())
                                        && alreadyHit.add(nearby.getUniqueId())) {
                                    target.damage(damage, ctx.boss());
                                    target.setFireTicks(fireTicks);
                                    Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                                }
                            }
                            elapsed++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                },
                14, onComplete);
    }
}
