package dev.rbm72.weaponsplugin.boss.bosses.weeping;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P3's roof. The walls close over the group's heads and the room goes genuinely dark — <b>real block
 * light</b>, not the Darkness status effect (batch-3 §5.3, which calls the distinction out explicitly
 * against the Hollow Choir's supernatural version). The fix is therefore the ordinary Minecraft one: put
 * torches up. Every torch placed is a tile of the room the group can still fight in, and the Colossus
 * snuffs them, so light is contested rather than solved once.
 * <p>
 * The roof is laid only over the <em>current</em> chamber, which by P3 is small — sealing the full arena
 * would be thousands of ledger entries for a room the walls have already taken away.
 */
final class SealedCeiling implements Listener {

    private final WeepingFight fight;
    private final List<Block> roof = new ArrayList<>();

    /**
     * Lights <em>players</em> put up in the arena, tracked from the placement event rather than found by
     * sweeping the room. The sweep behind {@link #litCount()} cannot tell a player's torch from the
     * realm's own decor — the weeping realm lights itself with lanterns, and {@link #snuffOne()} writes
     * {@code AIR} outside the grief ledger on purpose (see its javadoc), so snuffing a decor lantern
     * would delete it for good rather than have the arena restore hand it back. Only what the group put
     * up is ever a candidate.
     */
    private final Set<Block> playerLights = new LinkedHashSet<>();

    private boolean sealed;
    private boolean listening;
    private int snuffCooldownTicks;
    private int snuffed;
    private int litCache;
    private int litCacheAgeTicks;

    SealedCeiling(WeepingFight fight) {
        this.fight = fight;
        fight.plugin().getServer().getPluginManager().registerEvents(this, fight.plugin());
        listening = true;
    }

    /** Every light the group puts up anywhere in the arena, from the first torch of P1 onward. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!isLight(block.getType()) || block.getWorld() != fight.world()) {
            return;
        }
        if (!fight.instance().arena().isInside(block.getLocation())) {
            return;
        }
        playerLights.add(block);
    }

    void seal() {
        if (sealed) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        sealed = true;
        Location centre = fight.instance().arena().center();
        int radius = fight.walls().chamberRadius();
        int height = Math.max(5, fight.config().num("ceiling-height", 7));
        int baseY = fight.floorY(centre.getBlockX(), centre.getBlockZ()) + height;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                Block block = world.getBlockAt(centre.getBlockX() + x, baseY, centre.getBlockZ() + z);
                if (Grief.setMechanicBlock(fight.griefContext(), block, Material.DEEPSLATE_BRICKS)) {
                    roof.add(block);
                }
            }
        }
        Fx.sound(centre, Sound.BLOCK_PISTON_EXTEND, 1.8f, 0.4f);
        Fx.burst(centre.clone().add(0, 3, 0), Particle.BLOCK_CRUMBLE, 60, 1.2);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("THE CEILING CLOSES — get torches up", NamedTextColor.BLUE),
                    3000L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    void pulse(int intervalTicks) {
        if (!sealed) {
            return;
        }
        // The lit-block sweep is a few thousand block lookups, so it runs about once a second here
        // rather than on every mechanic-bar refresh, and the readout serves the cache.
        litCacheAgeTicks -= intervalTicks;
        if (litCacheAgeTicks <= 0) {
            litCacheAgeTicks = 20;
            litCache = countLights();
        }
        snuffCooldownTicks -= intervalTicks;
        if (snuffCooldownTicks > 0) {
            return;
        }
        snuffCooldownTicks = fight.config().num("ceiling-snuff-interval-ticks", 140);
        snuffOne();
    }

    /**
     * It puts a light out — and only ever one of the group's own, drawn from {@link #playerLights}
     * rather than found by sweeping the room. The write is direct rather than through the ledger on
     * purpose: the ledger's job is to undo what the <em>fight</em> built, and recording a player's torch
     * as "original" would have the arena restore hand it back at the end. That is also exactly why the
     * candidate list matters — the same write on a lantern the realm itself placed as decor would delete
     * it with nothing recorded to put it back.
     */
    private void snuffOne() {
        playerLights.removeIf(block -> !isLight(block.getType()));
        List<Block> lights = new ArrayList<>(playerLights);
        if (lights.isEmpty()) {
            return;
        }
        Block target = lights.get(ThreadLocalRandom.current().nextInt(lights.size()));
        target.setType(Material.AIR, false);
        playerLights.remove(target);
        snuffed++;
        Fx.burst(target.getLocation().add(0.5, 0.5, 0.5), Particle.SMOKE, 18, 0.3);
        Fx.sound(target.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.8f);
    }

    private static boolean isLight(Material type) {
        return type == Material.TORCH || type == Material.WALL_TORCH
                || type == Material.SOUL_TORCH || type == Material.SOUL_WALL_TORCH
                || type == Material.LANTERN || type == Material.SOUL_LANTERN
                || type == Material.JACK_O_LANTERN || type == Material.GLOWSTONE
                || type == Material.SHROOMLIGHT;
    }

    /** How many lights are burning in the chamber — the readout P3 is actually asking about, cached. */
    int litCount() {
        return litCache;
    }

    private int countLights() {
        World world = fight.world();
        if (world == null) {
            return 0;
        }
        Location centre = fight.instance().arena().center();
        int radius = fight.walls().chamberRadius();
        int lit = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int baseY = fight.floorY(centre.getBlockX() + x, centre.getBlockZ() + z);
                for (int y = 0; y <= 3; y++) {
                    if (isLight(world.getBlockAt(centre.getBlockX() + x, baseY + y, centre.getBlockZ() + z).getType())) {
                        lit++;
                        break;
                    }
                }
            }
        }
        return lit;
    }

    int snuffedCount() {
        return snuffed;
    }

    boolean sealed() {
        return sealed;
    }

    void discardAll() {
        roof.clear();
        playerLights.clear();
        if (listening) {
            HandlerList.unregisterAll(this);
            listening = false;
        }
    }
}
