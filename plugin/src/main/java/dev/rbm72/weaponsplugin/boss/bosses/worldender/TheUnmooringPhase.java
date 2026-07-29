package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.concurrent.ThreadLocalRandom;

/**
 * <b>P6 — The Unmooring (40–28%) · removes the arena.</b> The floor starts going: rifts open on a clock
 * for the rest of the phase, and the fightable space shrinks fast. Chorus fruit drops as the emergency,
 * finite, contested answer.
 */
final class TheUnmooringPhase extends WorldenderPhaseMechanic {

    private int riftCooldown;

    TheUnmooringPhase(BossInstance instance) {
        super(instance, "The Unmooring");
    }

    @Override
    protected void onArm() {
        WorldenderSupplies.chorusFruit(fight);
        riftCooldown = fight.config().num("unmooring-first-rift-delay-ticks", 40);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        riftCooldown -= intervalTicks;
        if (riftCooldown > 0) {
            return;
        }
        riftCooldown = fight.config().num("unmooring-rift-interval-ticks", 90);
        var combatants = fight.combatants();
        if (combatants.isEmpty()) {
            return;
        }
        var target = combatants.get(ThreadLocalRandom.current().nextInt(combatants.size()));
        double radius = fight.config().dbl("unmooring-rift-radius", 3.0);
        fight.rifts().openOne(target.getLocation(), radius);
    }

    @Override
    protected Component readoutText() {
        return Component.text("the floor is going — " + fight.rifts().opened() + " rifts open",
                NamedTextColor.LIGHT_PURPLE);
    }
}
