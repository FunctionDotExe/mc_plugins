package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P4 — The Burning (64–52%) · removes free ground.</b> Lava floods the arena floor in one visible
 * eruption; water poured on it makes stone, kept for the rest of the fight (P6's rifts lean on the same
 * buckets). Pouring water is loud, same as planting P3's rod — building a platform announces exactly
 * where the builder is standing.
 */
final class TheBurningPhase extends WorldenderPhaseMechanic {

    TheBurningPhase(BossInstance instance) {
        super(instance, "The Burning");
    }

    @Override
    protected void onArm() {
        WorldenderSupplies.waterBuckets(fight);
        fight.lava().floodEverything(fight.config().dbl("burning-keep-fraction", 0.22));
    }

    @Override
    protected Component readoutText() {
        return Component.text("the floor is burning — pour water to build", NamedTextColor.GOLD);
    }
}
