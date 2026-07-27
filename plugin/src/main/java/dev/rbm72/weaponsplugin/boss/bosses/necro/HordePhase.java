package dev.rbm72.weaponsplugin.boss.bosses.necro;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <b>P1 — The Horde.</b> The baseline, and the lesson: the army has a source, and killing the army is
 * not how you turn it off.
 * <p>
 * Waves walk in from lit lanes on the arena edge while he plants grave markers behind them. A group that
 * fights the horde makes no progress at all — the graves out-produce any amount of add clearing — so the
 * phase is over when two of them have actually been broken, whatever the health bar says.
 * <p>
 * Solo this becomes a chokepoint-defence fight rather than a reduced one: the wave sits at the floor of
 * its range, only two lanes open at a time, and exactly one grave stands at a time, so a single player
 * can wall a corridor, hold it, and break the graves one after another.
 */
public final class HordePhase extends NecroPhaseMechanic {

    public HordePhase(BossInstance instance, double exitFraction) {
        super(instance, "The Horde", exitFraction);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.graves().destroyedCount() >= gravesNeeded();
    }

    /** Graves broken. The one grave standing at a time solo means this moves in ones, slowly, on purpose. */
    @Override
    protected int progressSignal() {
        return fight.graves().destroyedCount();
    }

    @Override
    protected Component readoutText() {
        int destroyed = Math.min(gravesNeeded(), fight.graves().destroyedCount());
        return Component.text("graves " + destroyed + "/" + gravesNeeded() + " broken",
                objectiveMet() ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return (double) fight.graves().destroyedCount() / gravesNeeded();
    }

    private int gravesNeeded() {
        return Math.max(1, fight.config().num("graves-to-advance", 2));
    }
}
