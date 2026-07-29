package dev.rbm72.weaponsplugin.boss.bosses.inferno;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Warlord's signature system (§1.1: "the roster's player-authored terrain boss"): lava rises from
 * the arena rim inward in visible, warned tiers, and the group's only counter is real water poured from
 * real buckets — a lava <em>flow</em> becomes stone, a lava <em>source</em> becomes obsidian, exactly
 * vanilla's own fluid-mixing rule (implementation note: "real water/lava interaction respected rather
 * than reimplemented"). Everything this class writes is destructive terrain damage, so every fill goes
 * through {@link Grief#setBlock} rather than {@link Grief#setMechanicBlock} — a server with grief off
 * gets no lava at all rather than lava it can never turn off (§0.3 clamping applies the same way it does
 * to every other boss's craters).
 * <p>
 * <b>Tile bookkeeping</b> is what makes construction mean something. A column starts life untouched; a
 * tier fill converts it to {@link Tile#FLOW} (the encroaching front) and, once a <em>later</em> tier
 * moves past it, promotes it to {@link Tile#SOURCE} (settled, "fed" lava) — so pouring water on the
 * newest edge of a flood makes stone and pouring it on the older interior makes obsidian, matching the
 * mechanics table row exactly. A column a player has converted becomes {@link Tile#PLAYER_MADE} and is
 * <em>skipped</em> by every later tier: the platform the group built stays theirs, an island the lava
 * flows around, which is the entire point of spending a bucket rather than just delaying the inevitable.
 */
final class RisingLava implements Listener {

    private enum Tile { FLOW, SOURCE, PLAYER_MADE }

    /** Hard ceiling on columns filled in one batch pass, so a big tier fill never spikes server tick time. */
    private static final int MAX_TILES_PER_TICK_BATCH = 60;

    private final InfernoFight fight;
    private final Map<Long, Tile> tiles = new HashMap<>();
    private boolean listening;

    /** Current frontier as a fraction of arena radius; 1.0 = nothing risen yet. Only ever shrinks. */
    private double frontierFraction = 1.0;
    private int playerMadeCount;

    private boolean armed;
    private int nextTierIndex;
    private double intervalCountdownSeconds;
    private double[] tierFractions;

    /** Batched fill queue — see {@link #enqueueRing}. */
    private final ArrayDeque<long[]> pendingColumns = new ArrayDeque<>();
    private BukkitTask batchTask;

    RisingLava(InfernoFight fight) {
        this.fight = fight;
        fight.plugin().getServer().getPluginManager().registerEvents(this, fight.plugin());
        listening = true;
    }

    int playerMadeCount() {
        return playerMadeCount;
    }

    /**
     * Starts (or resumes) the automatic tier schedule — P2's {@code onArm}. Idempotent on the fraction
     * table itself so P3 leaving this armed continues the same schedule rather than restarting it.
     */
    void arm() {
        armed = true;
        if (tierFractions == null) {
            tierFractions = buildTierSchedule();
        }
        intervalCountdownSeconds = fight.config().dbl("lava-tier-interval-seconds", 24.0);
    }

    /** Stops the automatic schedule from advancing further — the batch already in flight still finishes. */
    void disarm() {
        armed = false;
    }

    void pulse(int intervalTicks) {
        if (!armed || tierFractions == null || nextTierIndex >= tierFractions.length) {
            return;
        }
        intervalCountdownSeconds -= intervalTicks / 20.0;
        if (intervalCountdownSeconds > 0) {
            return;
        }
        intervalCountdownSeconds = fight.config().dbl("lava-tier-interval-seconds", 24.0);
        double from = nextTierIndex == 0 ? fight.config().dbl("lava-rim-fraction", 0.98) : tierFractions[nextTierIndex - 1];
        double to = tierFractions[nextTierIndex];
        nextTierIndex++;
        // A short warning window before the fill itself — "a clear warning before each rise" (§1.4).
        int warnTicks = fight.config().num("lava-warn-ticks", 60);
        warn(fight.instance().arena().center(), fight.instance().arena().radius() * from);
        org.bukkit.scheduler.BukkitTask task = fight.plugin().getServer().getScheduler().runTaskLater(
                fight.plugin(), () -> riseTier(from, to), warnTicks);
        fight.instance().trackTask(task);
    }

    private double[] buildTierSchedule() {
        int count = Math.max(1, fight.config().num("lava-tier-count", 5));
        double rim = fight.config().dbl("lava-rim-fraction", 0.98);
        double floor = fight.config().dbl("lava-tier-floor-fraction", 0.22);
        double[] fractions = new double[count];
        for (int i = 0; i < count; i++) {
            double t = (i + 1) / (double) count;
            fractions[i] = rim - (rim - floor) * t;
        }
        return fractions;
    }

    /**
     * True when {@code player} is currently standing on ground the group made — P2's non-health exit
     * condition reads this per combatant (§1.3: "the group is standing on ground it created").
     */
    boolean standingOnPlayerMade(Player player) {
        Block under = player.getLocation().getBlock().getRelative(0, -1, 0);
        return tiles.get(key(under.getX(), under.getZ())) == Tile.PLAYER_MADE
                && (under.getType() == Material.STONE || under.getType() == Material.OBSIDIAN);
    }

    /**
     * Advances the frontier one tier inward, from {@code fromFraction} to {@code toFraction} of the
     * arena radius (both in 0..1, {@code toFraction} smaller). Older FLOW tiles are promoted to SOURCE
     * first, so the tier that just finished crawling settles into "fed" lava right as the new one starts.
     */
    void riseTier(double fromFraction, double toFraction) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        promoteFlowToSource();
        frontierFraction = Math.min(frontierFraction, toFraction);

        Location center = fight.instance().arena().center();
        double radius = fight.instance().arena().radius();
        double outer = radius * Math.max(0.0, Math.min(1.0, fromFraction));
        double inner = radius * Math.max(0.0, Math.min(1.0, toFraction));
        int ir = (int) Math.ceil(outer);
        double outer2 = outer * outer;
        double inner2 = inner * inner;
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        for (int x = -ir; x <= ir; x++) {
            for (int z = -ir; z <= ir; z++) {
                double d2 = x * x + z * z;
                if (d2 > outer2 || d2 < inner2) {
                    continue;
                }
                enqueueRing(cx + x, cz + z);
            }
        }
        startBatchIfNeeded();
    }

    /** P4's opening beat: everything not already player-made goes under, save a small footing fraction. */
    void floodEverything(double keepFraction) {
        riseTier(1.0, keepFraction);
    }

    /** A cluster's platform detonating hands its column back to the flood rather than leaving cover standing. */
    void collapseColumn(Location at) {
        World world = fight.world();
        if (world == null) {
            return;
        }
        int x = at.getBlockX();
        int z = at.getBlockZ();
        tiles.remove(key(x, z));
        Block ground = world.getBlockAt(x, world.getHighestBlockYAt(x, z), z);
        Grief.setBlock(fight.griefContext(), ground.getRelative(0, 1, 0), Material.LAVA);
        tiles.put(key(x, z), Tile.FLOW);
    }

    private void enqueueRing(int x, int z) {
        long key = key(x, z);
        if (tiles.get(key) == Tile.PLAYER_MADE) {
            // The group's own platform — the whole reason to spend a bucket instead of just retreating.
            return;
        }
        pendingColumns.add(new long[] {x, z});
    }

    private void promoteFlowToSource() {
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            if (entry.getValue() == Tile.FLOW) {
                entry.setValue(Tile.SOURCE);
            }
        }
    }

    private void startBatchIfNeeded() {
        if (batchTask != null || pendingColumns.isEmpty()) {
            return;
        }
        batchTask = fight.plugin().getServer().getScheduler().runTaskTimer(fight.plugin(), () -> {
            World world = fight.world();
            if (world == null) {
                return;
            }
            int placed = 0;
            while (placed < MAX_TILES_PER_TICK_BATCH && !pendingColumns.isEmpty()) {
                long[] col = pendingColumns.poll();
                fillColumn(world, (int) col[0], (int) col[1]);
                placed++;
            }
            if (pendingColumns.isEmpty() && batchTask != null) {
                batchTask.cancel();
                batchTask = null;
            }
        }, 1L, 1L);
        fight.instance().trackTask(batchTask);
    }

    private void fillColumn(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        Block ground = world.getBlockAt(x, y, z);
        Block above = ground.getRelative(0, 1, 0);
        // Two ways a column is already under: the surface block itself is the lava a previous tier put
        // there (getHighestBlockYAt lands on it, so `above` is the air over it), or the flood is one
        // higher still. Both are already flooded — filling again would stack a second layer on top,
        // which P4's flood-everything pass would otherwise do to every column it re-walks.
        if (ground.getType() == Material.LAVA || above.getType() == Material.LAVA) {
            // putIfAbsent, not put: a column that has already settled to SOURCE stays SOURCE, so a
            // later tier walking back over it does not quietly turn its obsidian back into stone.
            tiles.putIfAbsent(key(x, z), Tile.FLOW);
            return;
        }
        if (!above.getType().isAir() && !above.getType().isSolid()) {
            return;
        }
        if (Grief.setBlock(fight.griefContext(), above, Material.LAVA)) {
            tiles.put(key(x, z), Tile.FLOW);
            if (ThreadLocalRandom.current().nextInt(6) == 0) {
                Fx.burst(above.getLocation().add(0.5, 0.4, 0.5), Particle.LAVA, 2, 0.3);
            }
        }
    }

    private void warn(Location center, double radius) {
        Fx.coloredRing(center, InfernoFight.DEEP_FIRE, 1.8f, radius, 40, 0);
        Fx.sound(center, Sound.BLOCK_LAVA_AMBIENT, 1.4f, 0.5f);
        Fx.sound(center, Sound.ITEM_BUCKET_EMPTY_LAVA, 1.2f, 0.6f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("THE LAVA RISES", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                    2600L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    // ---------------------------------------------------------------- construction

    /**
     * Pour water on lava -> stone (flow) or obsidian (source), the same vanilla-flavoured rule the
     * design asks for, but driven explicitly rather than left to vanilla's own fluid mixing: mixing
     * writes its result block directly, outside {@link Grief}, which would leave the arena ledger with
     * no record to restore afterwards. Cancelling and converting by hand keeps the whole interaction
     * inside the ledger while still producing exactly the material vanilla would have.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.WATER_BUCKET) {
            return;
        }
        Block target = event.getBlock();
        long key = key(target.getX(), target.getZ());
        Tile tile = tiles.get(key);
        if (tile == null || tile == Tile.PLAYER_MADE) {
            return;
        }
        event.setCancelled(true);
        Material result = tile == Tile.SOURCE ? Material.OBSIDIAN : Material.STONE;
        if (!Grief.setMechanicBlock(fight.griefContext(), target, result)) {
            return;
        }
        tiles.put(key, Tile.PLAYER_MADE);
        playerMadeCount++;
        fight.instance().recordProgress();
        consumeBucket(event);

        Location at = target.getLocation().add(0.5, 0.5, 0.5);
        Fx.burst(at, Particle.CLOUD, 24, 0.4);
        Fx.sound(at, result == Material.OBSIDIAN ? Sound.BLOCK_GLASS_BREAK : Sound.BLOCK_STONE_PLACE, 1.1f, result == Material.OBSIDIAN ? 0.6f : 1.0f);
        Fx.sound(at, Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);
    }

    /**
     * Cancelling {@link PlayerBucketEmptyEvent} stops vanilla from consuming the bucket for us, so the
     * conversion has to spend it by hand — otherwise pouring water on lava would be a free, infinite
     * action instead of the contested resource the bucket economy depends on.
     */
    private void consumeBucket(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        ItemStack empty = new ItemStack(Material.BUCKET);
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(empty);
        } else {
            player.getInventory().setItemInMainHand(empty);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    void discardAll() {
        if (batchTask != null) {
            batchTask.cancel();
            batchTask = null;
        }
        pendingColumns.clear();
        if (listening) {
            HandlerList.unregisterAll(this);
            listening = false;
        }
    }

    private static long key(int x, int z) {
        return ((long) (x & 0x3FFFFFFF) << 32) | (z & 0xFFFFFFFFL);
    }
}
