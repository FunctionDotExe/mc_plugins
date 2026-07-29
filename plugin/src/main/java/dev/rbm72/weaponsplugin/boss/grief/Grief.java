package dev.rbm72.weaponsplugin.boss.grief;

import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.util.BlockGuard;
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
     * The single gate every <em>destructive</em> block write goes through: honours the grief switch,
     * refuses anything the fight could not put back, records the original state, then makes the change.
     * <p>
     * Nothing here may write a block directly. Terrain damage is only safe to be as extreme as the
     * roster demands because {@link ArenaLedger} is holding the undo log for all of it, and one
     * unguarded {@code setType} is enough to leave a permanent hole in a rollback that otherwise
     * looks like it worked.
     * <p>
     * Grief-gated, so a server that turns grief off gets no terrain damage at all rather than "no
     * craters, but every primitive that happened to call this still rewrites the floor". Block work
     * that <em>is</em> a mechanic rather than damage must say so with {@link #setMechanicBlock}.
     *
     * @return true if the block was actually changed.
     */
    public static boolean setBlock(AttackContext ctx, Block block, Material material) {
        if (!enabled(ctx)) {
            return false;
        }
        return write(ctx, block, material);
    }

    /**
     * A block that <em>is</em> the mechanic — a corpse pile to mine out, a grave marker to break, a
     * canopy blotting out the sky — rather than damage done to the arena.
     * <p>
     * Deliberately <b>not</b> gated on the grief switch, and the distinction is load-bearing: the switch
     * exists so an admin can stop bosses wrecking the world, not so they can silently delete three of
     * the Necro Overlord's four phases. Gating these would leave those phases running with nothing for
     * players to interact with, which reads as a broken boss rather than a disabled feature. Still
     * ledgered exactly like a destructive write, so the arena comes back clean either way.
     *
     * @return true if the block was actually changed.
     */
    public static boolean setMechanicBlock(AttackContext ctx, Block block, Material material) {
        return write(ctx, block, material);
    }

    private static boolean write(AttackContext ctx, Block block, Material material) {
        if (BlockGuard.isProtected(block.getType())) {
            return false;
        }
        if (!ctx.instance().ledger().record(block)) {
            return false;
        }
        // applyPhysics=false: a boss carving a crater should not also trigger cascades of falling
        // gravel and flowing liquids across the rest of the arena, which would damage blocks the
        // ledger never recorded and never sees to restore.
        block.setType(material, false);
        return true;
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
                    if (block.getType().isAir()) {
                        continue;
                    }
                    setBlock(ctx, block, Material.AIR);
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
     * A block dropped from the ceiling that <em>stays</em> where it lands — the anvil barrage, the
     * avalanche, the ice the Frost Queen shears off the roof.
     * <p>
     * Deliberately not a plain {@link FallingBlock} left to place itself. Vanilla's own landing writes
     * the block straight into the world without ever consulting {@link ArenaLedger}, so every anvil that
     * came down would be a permanent addition to an arena the fight otherwise puts back perfectly — the
     * one hole in the rollback that looks like it worked. Instead the entity is a pure visual: it never
     * places and never drops, and this places the real block itself, through the ledger, at the column
     * it came to rest in.
     * <p>
     * §0.1: the block landing is the mechanic. The impact damage is what makes standing under it a
     * mistake, and the block staying is what makes the arena remember the mistake — cover to hide
     * behind, or a tile nobody can stand on again.
     *
     * @param heightAbove  how far above {@code column} the block is spawned; the drop is the telegraph
     * @param damage       dealt to combatants within {@code radius} of the impact
     * @param onLand       nullable hook fired with the block's final location once it has settled
     */
    public static void dropAsBlock(AttackContext ctx, Location column, Material material, double heightAbove,
                                    double damage, double radius, java.util.function.Consumer<Location> onLand) {
        World world = column.getWorld();
        if (world == null) {
            return;
        }
        Location from = column.clone().add(0.5, heightAbove, 0.5);
        if (ctx.instance().liveFallingBlockCount() >= ctx.instance().boss().maxFallingBlocks()) {
            // Over the live-entity cap: land it immediately rather than silently skipping the hazard.
            // The volley's danger ring has already been shown, and a telegraph that resolves into
            // nothing is worse than one that resolves early.
            settle(ctx, column, material, damage, radius, onLand);
            return;
        }
        FallingBlock falling = world.spawnFallingBlock(from, material.createBlockData());
        falling.setDropItem(false);
        falling.setCancelDrop(true);
        falling.setHurtEntities(false);
        falling.setPersistent(false);
        ctx.instance().trackEntity(falling);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!falling.isValid()) {
                    cancel();
                    return;
                }
                if (falling.isOnGround() || ticks++ >= 100) {
                    Location impact = falling.getLocation();
                    falling.remove();
                    settle(ctx, impact, material, damage, radius, onLand);
                    cancel();
                    return;
                }
                Fx.trail(falling.getLocation(), Particle.SMOKE, 2, 0.1, 0.01);
            }
        }.runTaskTimer(ctx.plugin(), 1L, 1L);
    }

    /**
     * The landing itself: everyone underneath pays, and the block becomes part of the arena.
     * <p>
     * Written with {@link #setMechanicBlock} rather than {@link #setBlock} because a landed anvil is
     * cover and obstruction the design asks players to use, not damage done to the world — a server
     * that has turned grief off should still get the barrage it has to dodge, only without the crater.
     */
    private static void settle(AttackContext ctx, Location impact, Material material, double damage,
                                double radius, java.util.function.Consumer<Location> onLand) {
        World world = impact.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : dev.rbm72.weaponsplugin.boss.Arena.combatants(impact, radius)) {
            player.damage(damage, ctx.boss());
        }
        Block resting = world.getBlockAt(impact);
        // Walk up out of whatever it settled inside, so a block landing in a one-deep crater does not
        // silently overwrite the floor it fell into.
        for (int i = 0; i < 3 && !resting.getType().isAir(); i++) {
            resting = resting.getRelative(0, 1, 0);
        }
        if (resting.getType().isAir()) {
            setMechanicBlock(ctx, resting, material);
        }
        Fx.blockBurst(impact, material, 26, 0.5);
        Fx.sound(impact, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.7f);
        if (onLand != null) {
            onLand.accept(resting.getLocation());
        }
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
                        setBlock(ctx, block, material);
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
                    if (!ground.getType().isAir()) {
                        setBlock(ctx, ground, to);
                    }
                } else {
                    Fx.burst(ground.getLocation().add(0.5, 1, 0.5), Particle.SPORE_BLOSSOM_AIR, 2, 0.2);
                }
            }
        }
    }
}
