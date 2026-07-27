package dev.rbm72.weaponsplugin.boss.bosses.frost;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * <b>P3 — Heart of Winter.</b> She seals herself in ice and drops the Frozen Heart at arena centre — a
 * real structure, immune to every weapon, that only fire carried in by hand can burn down (see
 * {@link FrozenHeart}).
 * <p>
 * This is the roster's one authorized "boss gated while you deal with an objective" phase (batch-1
 * §2.3's roster note, and the memory note on the anti-pattern this rework otherwise closes everywhere
 * else): she is genuinely untouchable for as long as the Heart stands, because the Heart — not a health
 * bar in disguise — is the actual thing being fought. She keeps attacking throughout; only her own
 * health stops responding.
 * <p>
 * She also keeps hunting whoever is carrying fire — carrying suppresses Chill but paints a target on
 * you, batch-1's stated role inversion from P2 ("the healthiest player should carry, not the safest").
 */
final class HeartOfWinterPhase extends FrostPhaseMechanic {

    HeartOfWinterPhase(BossInstance instance, double exitFraction) {
        super(instance, "Heart of Winter", exitFraction);
    }

    @Override
    protected void onArm() {
        fight.heart().build();
        instance.setForcedInvulnerable(true);
        instance.setTargetOverride(this::carrierOrNull);
    }

    @Override
    protected void onDisarm() {
        instance.setForcedInvulnerable(false);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        // Released the instant the Heart shatters — not gated behind the next phase tick — so the group
        // gets the full remaining span of this health band to actually punish the opening they just won.
        if (fight.heart().destroyed()) {
            instance.setForcedInvulnerable(false);
        }
    }

    private Player carrierOrNull() {
        var carriers = fight.heart().carriers();
        return carriers.isEmpty() ? null : carriers.get(0);
    }

    @Override
    protected boolean objectiveMet() {
        return fight.heart().destroyed();
    }

    /**
     * A new record low on the Heart's HP resets the floor-lock timeout's clock, same as every other
     * phase's {@code progressSignal} — without this, a group actively burning the Heart down for longer
     * than {@link dev.rbm72.weaponsplugin.boss.bosses.FrostQueen#phaseFloorTimeoutMs} without quite
     * finishing it looks identical to a group that never engaged with fire-carrying at all, and the
     * valve would hand the phase over mid-effort. Monotonic by construction: {@code tick()} only calls
     * {@code recordProgress()} when this strictly increases, so the Heart re-freezing while uncontested
     * (its own intended "hesitation resets progress" punishment) never itself resets the clock — only
     * genuinely new damage does.
     */
    @Override
    protected int progressSignal() {
        return (int) Math.round((1.0 - fight.heart().fraction()) * 1000);
    }

    @Override
    protected Component readoutText() {
        if (fight.heart().destroyed()) {
            return Component.text("the Heart is broken — she can be hurt", NamedTextColor.GREEN);
        }
        int pct = (int) Math.round(fight.heart().fraction() * 100);
        return Component.text("Heart " + pct + "% — carry fire to it", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return 1.0 - fight.heart().fraction();
    }
}
