package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * <b>P3 — Between.</b> He splits into three identical phantoms (see {@link Phantoms}). Nothing is
 * invulnerable — the phase is pure identification, since ordinary damage only ever lands on the real
 * {@code instance.entity()} regardless of which of the three a player is looking at. Real end crystals
 * stand as an optional objective: destroying one shortens the tell interval but costs more floor.
 * <p>
 * Exit requires the real Sovereign struck three times, on top of the health threshold — counted from
 * real hits alone, since a decoy is a separate entity a hit on it never reaches this phase at all.
 */
final class BetweenPhase extends SovereignPhaseMechanic {

    private int realHits;

    BetweenPhase(BossInstance instance, double exitFraction) {
        super(instance, "Between", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.phantoms().spawn();
        fight.crystals().build();
    }

    @Override
    protected void onDisarm() {
        fight.phantoms().discard();
        fight.crystals().discard();
    }

    @Override
    protected void onPulse(int intervalTicks) {
        fight.phantoms().pulse(intervalTicks);
    }

    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        if (damageDealt > 0) {
            realHits++;
        }
    }

    @Override
    protected boolean objectiveMet() {
        return realHits >= fight.config().num("between-hits-required", 3);
    }

    @Override
    protected int progressSignal() {
        return realHits;
    }

    @Override
    protected Component readoutText() {
        int required = fight.config().num("between-hits-required", 3);
        if (realHits >= required) {
            return Component.text("you found him", NamedTextColor.GREEN);
        }
        return Component.text(realHits + "/" + required + " real hits — find the falling-block shadow",
                NamedTextColor.GRAY);
    }

    @Override
    protected double readoutProgress() {
        return Math.min(1.0, (double) realHits / fight.config().num("between-hits-required", 3));
    }
}
