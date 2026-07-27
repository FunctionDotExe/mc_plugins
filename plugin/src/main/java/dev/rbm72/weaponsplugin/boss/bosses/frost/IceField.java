package dev.rbm72.weaponsplugin.boss.bosses.frost;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * The slick region growing outward from wherever she is standing — packed ice first, blue ice at the
 * very centre. This is the roster's one genuine traversal-physics boss (batch-1 spec §2.1): the floor
 * itself has to actually change under a player's feet, not just look different, so this converts real
 * ground through {@link Grief#spread} rather than painting a particle disc over ordinary terrain.
 * <p>
 * Gated on the grief switch like any other terrain conversion — a server with grief off gets the
 * cosmetic tint {@code Grief.spread} already falls back to, and the Chill meter's "faster on ice"
 * multiplier simply never triggers there, which is a fair trade for a boss that would otherwise be
 * carving real ice into a protected world.
 * <p>
 * Radius only ever grows for the life of the fight — it is not un-grown on a phase change, because the
 * design's whole point is that the room you started in is not the room you finish in.
 */
final class IceField {

    private final FrostFight fight;
    private double radius;

    IceField(FrostFight fight) {
        this.fight = fight;
    }

    double radius() {
        return radius;
    }

    void pulse(int intervalTicks) {
        double growthPerSecond = fight.config().dbl("ice-field-growth-per-second", 0.12);
        double cap = fight.instance().arena().radius() * fight.config().dbl("ice-field-max-fraction", 0.85);
        radius = Math.min(cap, radius + growthPerSecond * (intervalTicks / 20.0));
        if (radius < 1.0) {
            return;
        }
        Location origin = fight.instance().entity().getLocation();
        Grief.spread(fight.griefContext(), origin, Material.PACKED_ICE, radius);
        Grief.spread(fight.griefContext(), origin, Material.BLUE_ICE, radius * 0.4);
    }
}
