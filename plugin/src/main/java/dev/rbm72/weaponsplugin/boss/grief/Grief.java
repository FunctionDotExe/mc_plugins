package dev.rbm72.weaponsplugin.boss.grief;

import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Stateless destructive primitives shared by every griefable boss attack.
 * Each method runs its real block/explosion effect only when the boss's
 * grief flag is on; when off it falls back to a pure particle/sound cosmetic
 * so the boss stays fully playable grief-disabled. Server-stability caps
 * (explosion power, crater radius, live falling-block count) always apply.
 */
public final class Grief {

    /** Server-wide kill switch toggled by {@code /grief} — overrides every boss's own grief config while on. */
    private static volatile boolean globallyDisabled = false;

    private Grief() {
    }

    public static void setGloballyDisabled(boolean disabled) {
        globallyDisabled = disabled;
    }

    public static boolean isGloballyDisabled() {
        return globallyDisabled;
    }

    public static boolean enabled(AttackContext ctx) {
        return !globallyDisabled && ctx.instance().boss().griefEnabled();
    }

    /**
     * Real explosion (fire + block break) when grief on; a cosmetic explosion burst + sound when off.
     * The boss is already immune to explosion damage via {@code BossDamageListener} (non-player damage
     * to a boss is cancelled), so it never blows itself up.
     */
    public static void explosion(AttackContext ctx, Location loc, float power) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        if (enabled(ctx)) {
            float capped = Math.min(power, ctx.instance().boss().maxExplosionPower());
            world.createExplosion(loc, capped, true, true);
        } else {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 6, 0.8, 0.8, 0.8, 0);
            world.spawnParticle(Particle.EXPLOSION, loc, 72, 2.24, 2.24, 2.24, 0.1);
            Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        }
    }

    /**
     * Break every block in a sphere of {@code radius} around {@code center} (clamped to the boss's
     * crater cap). Grief off -> block-crack particles + sound only. Skips air, bedrock, and barriers.
     */
    public static void breakCrater(AttackContext ctx, Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double r = Math.min(radius, ctx.instance().boss().maxCraterRadius());
        if (!enabled(ctx)) {
            world.spawnParticle(Particle.BLOCK, center, 180, r * 0.8, 0.8, r * 0.8, 0.1, Material.STONE.createBlockData());
            Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.9f);
            return;
        }
        int ir = (int) Math.ceil(r);
        double r2 = r * r;
        for (int x = -ir; x <= ir; x++) {
            for (int y = -ir; y <= ir; y++) {
                for (int z = -ir; z <= ir; z++) {
                    if (x * x + y * y + z * z > r2) {
                        continue;
                    }
                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    Material type = block.getType();
                    if (type.isAir() || type == Material.BEDROCK || type == Material.BARRIER) {
                        continue;
                    }
                    block.setType(Material.AIR, false);
                }
            }
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 6, 0.8, 0.8, 0.8, 0);
        Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
    }

    /**
     * Launch a FallingBlock projectile from {@code from} at {@code target}, damaging any player within
     * 2 blocks of impact and (grief on) cratering on landing. The block never places or drops
     * ({@code setCancelDrop(true)}), so it leaves no stray terrain; it is tracked for cleanup and
     * honors the boss's live-falling-block cap.
     */
    public static void throwBlock(AttackContext ctx, Location from, LivingEntity target, Material material,
                                  double damage, float impactPower) {
        World world = from.getWorld();
        if (world == null || ctx.instance().liveFallingBlockCount() >= ctx.instance().boss().maxFallingBlocks()) {
            return;
        }
        FallingBlock block = world.spawnFallingBlock(from, material.createBlockData());
        block.setDropItem(false);
        block.setCancelDrop(true);
        block.setHurtEntities(false);
        block.setPersistent(false);
        ctx.instance().trackEntity(block);

        Vector velocity = target.getLocation().add(0, 1, 0).toVector()
                .subtract(from.toVector()).normalize().multiply(1.4);
        velocity.setY(0.5);
        block.setVelocity(velocity);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!block.isValid() || block.isOnGround() || ticks >= 60) {
                    if (block.isValid()) {
                        Location impact = block.getLocation();
                        for (Player player : world.getPlayers()) {
                            if (player.getLocation().distanceSquared(impact) <= 4.0) {
                                player.damage(damage, ctx.boss());
                            }
                        }
                        if (enabled(ctx)) {
                            explosion(ctx, impact, impactPower);
                        } else {
                            Fx.burst(impact, Particle.CLOUD, 20, 0.4);
                            Fx.sound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.0f);
                        }
                        block.remove();
                    }
                    cancel();
                    return;
                }
                Fx.trail(block.getLocation(), Particle.SMOKE, 3, 0.1, 0.01);
                ticks++;
            }
        }.runTaskTimer(ctx.plugin(), 1L, 1L);
    }

    /**
     * Raise {@code count} vertical columns of {@code material}, {@code height} tall, scattered within
     * {@code spread} blocks of {@code base}. Grief on -> real placed blocks (permanent). Grief off ->
     * self-removing {@link BlockDisplay} props (tracked, auto-removed after {@code durationTicks}).
     */
    public static void raiseColumns(AttackContext ctx, Location base, Material material, int height, int count,
                                    double spread, int durationTicks) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        boolean grief = enabled(ctx);
        for (int i = 0; i < count; i++) {
            double ox = ThreadLocalRandom.current().nextDouble(-spread, spread);
            double oz = ThreadLocalRandom.current().nextDouble(-spread, spread);
            Location col = base.clone().add(ox, 0, oz);
            if (grief) {
                for (int y = 0; y < height; y++) {
                    Block block = world.getBlockAt(col.getBlockX(), col.getBlockY() + y, col.getBlockZ());
                    if (block.getType().isAir() || block.isLiquid()) {
                        block.setType(material, false);
                    }
                }
            } else {
                BlockData data = material.createBlockData();
                BlockDisplay display = world.spawn(col, BlockDisplay.class, entity -> {
                    entity.setBlock(data);
                    entity.setBillboard(Display.Billboard.FIXED);
                    entity.setBrightness(new Display.Brightness(15, 15));
                    entity.setTransformation(new Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f(0, 0, 1, 0),
                            new Vector3f(1, height, 1),
                            new AxisAngle4f(0, 0, 1, 0)));
                    entity.setPersistent(false);
                });
                ctx.instance().trackEntity(display);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!display.isDead()) {
                            display.remove();
                        }
                    }
                }.runTaskLater(ctx.plugin(), durationTicks);
            }
            Fx.blockBurst(col.clone().add(0, height / 2.0, 0), material, 15, 0.3);
        }
    }

    /**
     * Convert the top ground block of every column within {@code radius} of {@code center} to
     * {@code to} (creeping corruption). Grief-gated; grief off -> tinting particles only.
     */
    public static void spread(AttackContext ctx, Location center, Material to, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        boolean grief = enabled(ctx);
        double r = Math.min(radius, ctx.instance().boss().maxCraterRadius());
        int ir = (int) Math.ceil(r);
        double r2 = r * r;
        for (int x = -ir; x <= ir; x++) {
            for (int z = -ir; z <= ir; z++) {
                if (x * x + z * z > r2) {
                    continue;
                }
                Block ground = world.getHighestBlockAt(center.getBlockX() + x, center.getBlockZ() + z);
                if (grief) {
                    if (!ground.getType().isAir() && ground.getType() != Material.BEDROCK) {
                        ground.setType(to, false);
                    }
                } else {
                    Fx.burst(ground.getLocation().add(0.5, 1, 0.5), Particle.SPORE_BLOSSOM_AIR, 2, 0.2);
                }
            }
        }
    }
}
