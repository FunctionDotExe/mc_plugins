package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Telegraphed ground hazard, reusable by any boss: a tightening danger zone locks onto the target's
 * current spot, then a rain of falling blocks slams down into it — anyone still standing inside when
 * they land takes damage. Grief on turns the impacts into real craters (via {@link Grief}); grief off
 * keeps the same shape as pure particles/sound. Built entirely on {@link Telegraph} + {@link Grief} —
 * no new particle or grief-gating plumbing, just a new attack shape for bosses that want one.
 */
public final class EnvironmentalHazardAttack extends BossAttack {

    private final double damage;
    private final double radius;
    private final int telegraphTicks;
    private final int blockCount;
    private final double dropHeight;

    public EnvironmentalHazardAttack(WeaponsPlugin plugin, String bossId) {
        super(plugin, bossId);
        this.damage = configDouble("hazard-damage", 6.0);
        this.radius = configDouble("hazard-radius", 4.0);
        this.telegraphTicks = configInt("hazard-telegraph-ticks", 24);
        this.blockCount = configInt("hazard-block-count", 5);
        this.dropHeight = configDouble("hazard-drop-height", 12.0);
    }

    @Override
    public String name() {
        return "Environmental Hazard";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("hazard-cooldown-seconds", 12.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        // Snapshotted once at cast start — the zone locks to where the target was standing, not a
        // moving point, so "don't stand here" has an actual fixed spot to read and dodge.
        Location zoneCenter = ctx.target().getLocation();

        sequence(telegraphTicks,
                () -> Telegraph.dangerZone(zoneCenter, radius),
                () -> execute(ctx, zoneCenter),
                12, onComplete);
    }

    private void execute(AttackContext ctx, Location zoneCenter) {
        World world = zoneCenter.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(zoneCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.1f);
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(zoneCenter) <= radius * radius) {
                player.damage(damage, ctx.boss());
                Fx.bloodSpray(player.getLocation().add(0, 1, 0));
            }
        }
        for (int i = 0; i < blockCount; i++) {
            dropHazardBlock(ctx, zoneCenter);
        }
    }

    /** One falling block dropped straight down from above a random point in the zone — no player-tracking, just terrain-shaped fear. */
    private void dropHazardBlock(AttackContext ctx, Location zoneCenter) {
        World world = zoneCenter.getWorld();
        if (world == null || ctx.instance().liveFallingBlockCount() >= ctx.instance().boss().maxFallingBlocks()) {
            return;
        }
        double ox = ThreadLocalRandom.current().nextDouble(-radius, radius);
        double oz = ThreadLocalRandom.current().nextDouble(-radius, radius);
        Location from = zoneCenter.clone().add(ox, dropHeight, oz);
        Material material = Grief.enabled(ctx) ? Material.MAGMA_BLOCK : Material.NETHERRACK;

        BlockData blockData = material.createBlockData();
        FallingBlock block = world.spawn(from, FallingBlock.class, fb -> fb.setBlockData(blockData));
        block.setDropItem(false);
        block.setCancelDrop(true);
        block.setHurtEntities(false);
        block.setPersistent(false);
        ctx.instance().trackEntity(block);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!block.isValid() || block.isOnGround() || ticks >= 40) {
                    if (block.isValid()) {
                        Location impact = block.getLocation();
                        for (Player player : world.getPlayers()) {
                            if (player.getLocation().distanceSquared(impact) <= 4.0) {
                                player.damage(damage * 0.5, ctx.boss());
                            }
                        }
                        Grief.explosion(ctx, impact, 1.5f);
                        block.remove();
                    }
                    cancel();
                    return;
                }
                Fx.trail(block.getLocation(), Particle.FLAME, 3, 0.1, 0.01);
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
