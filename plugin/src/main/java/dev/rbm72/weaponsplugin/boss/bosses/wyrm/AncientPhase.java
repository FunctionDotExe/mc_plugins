package dev.rbm72.weaponsplugin.boss.bosses.wyrm;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <b>P4 — The Ancient.</b> Full size, coiled around the arena perimeter (a decorative ring reusing
 * {@link WyrmSegments}, cover rather than a damage gate — every ring segment is left permanently
 * "exposed" so an errant hit on one still lands full damage; the point is obstruction, not another
 * armor puzzle) and periodically swallowing a player into {@link Interior}'s pocket room. Fully
 * damageable throughout — no {@link #filterDamage} override — same "the boss stays live during
 * everything" rule the whole rework runs on. The last phase: it has no exit condition of its own, the
 * fight simply ends on health like {@code StormcallPhase} does.
 */
final class AncientPhase extends WyrmPhaseMechanic {

    private int ticksUntilSwallow;
    private boolean telegraphed;

    AncientPhase(BossInstance instance) {
        super(instance, null);
    }

    @Override
    protected void onArm() {
        if (instance.entity().isValid()) {
            instance.entity().setAI(true);
            instance.entity().setInvisible(false);
        }
        int ringCount = fight.config().num("ring-segment-count", 10);
        fight.segments().spawnChain(ringCount, fight.config().dbl("ring-segment-hp", 1_000_000.0));
        for (int i = 0; i < ringCount; i++) {
            fight.segments().setExposed(i, true);
        }
        fight.segments().positionRing(fight.config().dbl("ring-radius-fraction", 0.9));

        ticksUntilSwallow = fight.config().num("swallow-first-delay-ticks", 140);
        telegraphed = false;
    }

    @Override
    protected void onDisarm() {
        fight.segments().discardAll();
        fight.interior().forceRelease();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.segments().positionRing(fight.config().dbl("ring-radius-fraction", 0.9));

        if (fight.interior().hasActiveSwallow()) {
            return;
        }
        ticksUntilSwallow -= intervalTicks;
        int telegraphTicks = fight.config().num("swallow-telegraph-ticks", 50);
        if (!telegraphed && ticksUntilSwallow <= telegraphTicks) {
            telegraphed = true;
            instance.showTitle(Component.empty(),
                    Component.text("IT RISES — someone is about to be swallowed", NamedTextColor.LIGHT_PURPLE));
        }
        if (ticksUntilSwallow <= 0) {
            attemptSwallow();
        }
    }

    private void attemptSwallow() {
        List<Player> combatants = fight.combatants();
        telegraphed = false;
        int cooldown = fight.config().num("swallow-cooldown-ticks", 280);
        if (combatants.isEmpty()) {
            ticksUntilSwallow = cooldown;
            return;
        }
        Player target = combatants.get(ThreadLocalRandom.current().nextInt(combatants.size()));
        fight.interior().swallow(target);
        ticksUntilSwallow = cooldown;
    }

    @Override
    protected boolean objectiveMet() {
        return true;
    }
}
