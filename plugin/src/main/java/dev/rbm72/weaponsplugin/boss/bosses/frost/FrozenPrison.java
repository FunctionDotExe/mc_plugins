package dev.rbm72.weaponsplugin.boss.bosses.frost;

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
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The real ice shell the Chill meter's threshold drops a player into. {@code MeterThresholds.freezeSolid}
 * already pins and bleeds them — this is the other half: a ring of real, breakable ice around their feet,
 * because the design's counterplay is allies physically mining someone free, and that only means anything
 * if the ice is genuinely there to hit.
 * <p>
 * Solo gets a thinner, faster-breaking shell (plain {@link Material#ICE}, which breaks by hand in a
 * couple of hits) so a self-rescue is possible without a partner; a grouped fight gets
 * {@link Material#PACKED_ICE}, which wants a pickaxe and more swings, on purpose — see batch-1 §2.4
 * ("solo: shell has lower hardness so self-escape is possible but costs ~4s").
 */
final class FrozenPrison {

    private final FrostFight fight;

    /** Every live shell's blocks, keyed by the prisoner they surround. */
    private final Map<UUID, List<Block>> shells = new HashMap<>();
    /** Reverse lookup so a break event can find whose shell a block belongs to in O(1). */
    private final Map<Block, UUID> owners = new HashMap<>();

    private Handler handler;
    private int brokenCount;

    FrozenPrison(FrostFight fight) {
        this.fight = fight;
    }

    /** Prisons actually broken open by the group this fight — P1's exit condition counts these. */
    int brokenCount() {
        return brokenCount;
    }

    /** Called from the Chill meter's threshold the instant a player caps out. */
    void encase(Player victim) {
        World world = fight.world();
        if (world == null || shells.containsKey(victim.getUniqueId())) {
            return;
        }
        boolean solo = fight.playerCount() <= 1;
        Material material = solo ? Material.ICE : Material.PACKED_ICE;
        int columns = fight.config().num("prison-ring-columns", solo ? 5 : 8);
        int height = fight.config().num("prison-height", 2);
        Location base = victim.getLocation();

        List<Block> ring = new ArrayList<>();
        for (int i = 0; i < columns; i++) {
            double angle = (Math.PI * 2 * i) / columns;
            int bx = base.getBlockX() + (int) Math.round(Math.cos(angle) * 1.2);
            int bz = base.getBlockZ() + (int) Math.round(Math.sin(angle) * 1.2);
            for (int y = 0; y < height; y++) {
                Block block = world.getBlockAt(bx, base.getBlockY() + y, bz);
                if (block.getType().isAir() && Grief.setMechanicBlock(fight.griefContext(), block, material)) {
                    ring.add(block);
                    owners.put(block, victim.getUniqueId());
                }
            }
        }
        shells.put(victim.getUniqueId(), ring);
        startListening();

        Fx.coloredBurst(base.clone().add(0, 1.0, 0), FrostFight.FROST_BLUE, 2.2f, 60, 1.0);
        Fx.sound(base, Sound.BLOCK_GLASS_PLACE, 1.2f, 0.6f);
        fight.plugin().actionBarHub().flash(victim,
                Component.text("FROZEN SOLID — break the ice or wait it out", NamedTextColor.AQUA),
                2600L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        for (Player ally : fight.combatants()) {
            if (!ally.equals(victim)) {
                fight.plugin().actionBarHub().flash(ally,
                        Component.text(victim.getName() + " IS FROZEN — break the ice", NamedTextColor.AQUA),
                        2600L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
            }
        }
    }

    private void startListening() {
        if (handler != null) {
            return;
        }
        handler = new Handler();
        fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
    }

    private final class Handler implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            UUID prisoner = owners.remove(event.getBlock());
            if (prisoner == null) {
                return;
            }
            List<Block> ring = shells.get(prisoner);
            if (ring == null) {
                return;
            }
            ring.remove(event.getBlock());
            if (!ring.isEmpty()) {
                Fx.blockBurst(event.getBlock().getLocation().add(0.5, 0.5, 0.5), Material.PACKED_ICE, 14, 0.4);
                return;
            }
            shells.remove(prisoner);
            brokenCount++;
            var victim = event.getBlock().getWorld().getPlayers().stream()
                    .filter(p -> p.getUniqueId().equals(prisoner)).findFirst().orElse(null);
            if (victim != null) {
                fight.chill().afflictions().release(victim);
                Fx.coloredBurst(victim.getLocation().add(0, 1.0, 0), FrostFight.PALE_ICE, 2.4f, 60, 1.0);
                Fx.sound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.4f, 1.1f);
                Fx.burst(victim.getLocation().add(0, 1.0, 0), Particle.SNOWFLAKE, 30, 0.6);
            }
        }
    }

    /** Fight teardown: forget every shell (their blocks are in the ledger and restore with the arena). */
    void discard() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        shells.clear();
        owners.clear();
    }
}
