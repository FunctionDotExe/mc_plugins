package dev.rbm72.weaponsplugin.boss.bosses.necro;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The artificial night, as a real roof.
 * <p>
 * He spreads an actual block canopy across the sky over the arena and the floor goes dark under it —
 * not a darkness effect, a ceiling. Four destructible anchors hold it up, hanging too high to reach on
 * foot, so somebody has to leave the ground fight and go up there with a bow or a stack of scaffolding.
 * Break them all and the canopy comes down, real daylight lands on the arena, and every undead in it
 * catches fire on vanilla's own rule. That swing is the phase.
 * <p>
 * He re-knits it after a while, so daylight is something the group maintains rather than a switch it
 * flips once — which is what makes the trip upward a repeated cost rather than a one-off chore.
 * <p>
 * Note what this is not: at no point is the Overlord invulnerable, and no hit is ever filtered. The
 * canopy is a roof, not a shield. Five bosses in this roster independently converged on "boss is immune
 * while you break the objective" and it was removed from four of them; the whole reason the shroud is a
 * physical ceiling rather than a health gate is so that the fight never stops while it is being solved.
 */
final class Shroud {

    /** Light-tight and unmistakably his. Sculk also makes the canopy readable as a living thing rather than architecture. */
    private static final Material CANOPY = Material.SCULK;
    private static final Material ANCHOR_CORE = Material.SCULK_CATALYST;
    /** Canopy rings placed per pulse, so it visibly grows across the sky instead of blinking into place. */
    private static final int RINGS_PER_PULSE = 2;

    private final NecroFight fight;

    private final List<Block> canopy = new ArrayList<>();
    private final List<ArenaTotem> anchors = new ArrayList<>();

    private int spreadRing;
    private int ringsToPlace;
    private int rebuildDelayLeft;
    private int breaks;
    private int anchorsPlaced;
    private boolean up;

    Shroud(NecroFight fight) {
        this.fight = fight;
    }

    /** Times the group has torn it open. P2's exit needs at least one. */
    int breaks() {
        return breaks;
    }

    int anchorsStanding() {
        int standing = 0;
        for (ArenaTotem anchor : anchors) {
            if (anchor.isValid()) {
                standing++;
            }
        }
        return standing;
    }

    /** How many this cycle started with, so a readout can show progress rather than a bare count. */
    int anchorsPlaced() {
        return anchorsPlaced;
    }

    boolean isUp() {
        return up;
    }

    /** Seconds until he re-knits it, or 0 while it is standing. */
    int rebuildTicksLeft() {
        return Math.max(0, rebuildDelayLeft);
    }

    void raise() {
        if (up) {
            return;
        }
        up = true;
        spreadRing = 0;
        ringsToPlace = ringCount();
        fight.pullTheNightBack();
        placeAnchors();
        fight.instance().showTitle(
                Component.text("THE SHROUD", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("He is closing the sky — bring it down", NamedTextColor.GRAY));
        Fx.sound(fight.instance().arena().center(), Sound.ENTITY_WITHER_SPAWN, 1.4f, 0.4f);
    }

    void pulse(int intervalTicks) {
        if (!up) {
            rebuildDelayLeft -= intervalTicks;
            if (rebuildDelayLeft <= 0) {
                raise();
            }
            return;
        }
        spread();
        pruneAnchors();
        if (anchorsStanding() == 0) {
            collapse();
        }
    }

    /**
     * Retires the shroud without counting it as broken — a phase change or the fight ending. The canopy
     * blocks go back to air here rather than being left to the ledger, because a phase that ends with a
     * roof still overhead would leave P3's daylight quietly switched off by scenery.
     */
    void discard() {
        for (ArenaTotem anchor : anchors) {
            anchor.discard();
        }
        anchors.clear();
        clearCanopy();
        up = false;
    }

    // ------------------------------------------------------------------ canopy

    /**
     * Lays the canopy outward a couple of rings at a time. Only ever writes into air, so the arena's own
     * walls and towers are never eaten by it, and every write goes through the ledger.
     */
    private void spread() {
        World world = fight.world();
        if (world == null || spreadRing >= ringsToPlace) {
            return;
        }
        Location centre = fight.instance().arena().center();
        int y = fight.floorY() + height();
        for (int step = 0; step < RINGS_PER_PULSE && spreadRing < ringsToPlace; step++, spreadRing++) {
            int inner = spreadRing;
            int outer = spreadRing + 1;
            int innerSq = inner * inner;
            int outerSq = outer * outer;
            for (int x = -outer; x <= outer; x++) {
                for (int z = -outer; z <= outer; z++) {
                    int distSq = x * x + z * z;
                    if (distSq < innerSq || distSq > outerSq) {
                        continue;
                    }
                    Block block = world.getBlockAt(centre.getBlockX() + x, y, centre.getBlockZ() + z);
                    if (!block.getType().isAir() || !Grief.setMechanicBlock(fight.griefContext(), block, CANOPY)) {
                        continue;
                    }
                    canopy.add(block);
                }
            }
            Location ringAt = centre.clone();
            ringAt.setY(y);
            Fx.ring(ringAt, Particle.SCULK_SOUL, Math.max(1.0, outer), 24);
        }
        if (spreadRing >= ringsToPlace) {
            Fx.sound(centre, Sound.BLOCK_SCULK_SPREAD, 1.4f, 0.4f);
        }
    }

    private void clearCanopy() {
        for (Block block : canopy) {
            if (block.getType() == CANOPY) {
                Grief.setMechanicBlock(fight.griefContext(), block, Material.AIR);
            }
        }
        canopy.clear();
        spreadRing = 0;
    }

    // ----------------------------------------------------------------- anchors

    /**
     * Two anchors solo, four in a group — count scaling, never a damage change. Spawned with ground
     * snapping off so they hang at canopy height: a totem has gravity disabled, so an airborne placement
     * simply stays there, and reaching it is the ranged player's job or a scaffolding climb.
     */
    private void placeAnchors() {
        // Clamped to at least one: an anchor count of zero would leave nothing holding the canopy up, and
        // pulse() would read that as "already broken" and cycle raise/collapse forever.
        int count = Math.max(1, fight.playerCount() <= 1
                ? fight.config().num("shroud-anchors-solo", 2)
                : fight.config().num("shroud-anchors-grouped", 4));
        double fraction = fight.config().dbl("shroud-anchor-ring-fraction", 0.5);
        double radius = fight.instance().arena().radius() * fraction;
        Location centre = fight.instance().arena().center();
        // Two blocks clear of the canopy, so the anchor hangs visibly beneath the roof rather than with
        // its head buried in it, and stays shootable from the floor below.
        int y = fight.floorY() + height() - 2;
        anchorsPlaced = count;

        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            Location at = centre.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            at.setY(y);
            anchors.add(ArenaTotem.spawn(fight.plugin(), fight.instance(), at, ANCHOR_CORE,
                    Component.text("Shroud Anchor", NamedTextColor.DARK_AQUA),
                    fight.config().dbl("shroud-anchor-health", 45.0),
                    Math.max(1200, fight.config().num("shroud-anchor-lifetime-ticks", 24000)),
                    destroyed -> onAnchorBroken(at),
                    expired -> {
                    },
                    false));
            Fx.burst(at, Particle.SCULK_SOUL, 24, 0.6);
            Fx.sound(at, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.2f, 0.6f);
        }
    }

    private void pruneAnchors() {
        for (Iterator<ArenaTotem> it = anchors.iterator(); it.hasNext(); ) {
            if (!it.next().isValid()) {
                it.remove();
            }
        }
    }

    private void onAnchorBroken(Location at) {
        Fx.blockBurst(at, CANOPY, 30, 0.8);
        Fx.sound(at, Sound.BLOCK_SCULK_CATALYST_BREAK, 1.4f, 0.5f);
        Fx.sound(fight.instance().arena().center(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.2f, 0.6f);
    }

    // ---------------------------------------------------------------- collapse

    private void collapse() {
        up = false;
        breaks++;
        rebuildDelayLeft = Math.max(100, fight.config().num("shroud-rebuild-delay-ticks", 600));
        anchors.clear();

        Location centre = fight.instance().arena().center();
        clearCanopy();
        fight.letTheSunIn();
        if (fight.needsArtificialSun()) {
            // No sky to let the sun through (roofed arena, or a nether/end realm with no day clock), so
            // the horde is set alight directly. Real fire on real mobs — the beat still lands.
            fight.horde().scorch();
        }

        Fx.expandingRings(fight.plugin(), centre, Particle.END_ROD,
                Math.min(20.0, fight.instance().arena().radius() * 0.8), 6, 2L);
        Fx.sound(centre, Sound.ENTITY_WITHER_DEATH, 1.5f, 1.2f);
        Fx.sound(centre, Sound.ITEM_TOTEM_USE, 1.2f, 1.0f);
        fight.instance().showTitle(
                Component.text("☀ DAYLIGHT ☀", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("The sun is on his army — hold it open", NamedTextColor.GRAY));
    }

    // ----------------------------------------------------------------- helpers

    private int height() {
        return Math.max(6, fight.config().num("shroud-height", 14));
    }

    /** Canopy radius in blocks, so the ring loop can count in whole blocks rather than re-deriving it. */
    private int ringCount() {
        double coverage = fight.config().dbl("shroud-coverage-fraction", 0.85);
        return (int) Math.ceil(fight.instance().arena().radius() * Math.max(0.2, Math.min(1.0, coverage)));
    }
}
