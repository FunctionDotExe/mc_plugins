package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * P2's flash-freeze: real blue/packed ice across the floor, all at once, from the fixed arena centre —
 * Frost Queen's traversal physics (batch-5 §3, P2) without her Chill meter or her campfires. The only
 * counterplay is footing itself: leather boots (dropped by {@link WorldenderSupplies#leatherBoots}) and
 * staying quiet, since sneaking on ice is slower but still silent, while sliding into the boss's reach
 * registers as vibration through the normal melee/movement noise the fight already tracks.
 */
final class IceFloor {

    private final WorldenderFight fight;

    IceFloor(WorldenderFight fight) {
        this.fight = fight;
    }

    /** One instant, arena-wide freeze pass — the "flash" the design calls for, not a slow crawl. */
    void freeze() {
        Location centre = fight.instance().arena().center();
        double radius = fight.instance().arena().radius() * fight.config().dbl("freezing-ice-fraction", 0.95);
        Grief.spread(fight.griefContext(), centre, Material.PACKED_ICE, radius);
        Grief.spread(fight.griefContext(), centre, Material.BLUE_ICE, radius * 0.5);
    }
}
