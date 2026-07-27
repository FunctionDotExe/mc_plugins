package dev.rbm72.weaponsplugin.boss;

import org.bukkit.entity.Player;

/**
 * Whatever makes one phase play differently from every other phase in the game.
 * <p>
 * This started life as {@code PhaseMechanic}, whose only job was deciding what unlocks damage. That
 * framing turned out to be the problem rather than the fix: every implementation of it, however
 * different its props looked, produced the identical three beats — boss immune, do a chore, boss
 * hittable for a few seconds, repeat. Breaking plates, killing guards, freeing a hostage and hitting
 * a timing window are all the same fight wearing different costumes.
 * <p>
 * So a mechanic now gets three levers instead of one, and the interesting phases are the ones that
 * reach for the latter two:
 * <ul>
 *   <li><b>Global vulnerability</b> — {@link BossInstance#setDamageMultiplier} and friends, the old
 *       gate behaviour. Still right for a phase that genuinely is "break the thing".</li>
 *   <li><b>{@link #filterDamage}</b> — per-attacker damage rules. The boss can be immune to everyone
 *       except its chosen duel opponent, take full damage only from behind, or require two players to
 *       strike together. Damage stops being a permission the boss grants and becomes a rule about how
 *       you are fighting.</li>
 *   <li><b>{@link #readyToAdvance}</b> — an exit that isn't a health threshold. Lets a phase end
 *       because the group survived it, or moved the boss somewhere, or dropped the arena out from
 *       under it, rather than because a number went down.</li>
 * </ul>
 * One instance per active phase, built from {@link BossPhase#mechanicFactory()} and owned/torn down
 * by {@link BossInstance}. Any mechanic that holds the boss unhittable must eventually let go — see
 * {@link BossInstance#clampToPhaseFloor} for the timeout valve that stops a stuck one deadlocking a
 * fight.
 */
public interface PhaseMechanic {

    /** Arms the mechanic. Called once, the instant its phase becomes active. */
    void start();

    /**
     * Tears it down without firing any success/failure outcome — phase change or fight end. Must
     * cancel every task it scheduled, clear every entity it spawned, and be safe to call twice.
     */
    void stop();

    /**
     * Last word on what one player's hit actually deals, applied after the global multiplier and
     * before the phase floor. Return {@code damage} unchanged to opt out (the default).
     * <p>
     * This is where a phase expresses a rule rather than a wall: reject hits from anyone who isn't the
     * current duel target, scale by whether the attacker is behind the boss, reward a coordinated
     * pair. Called on the main thread inside the damage event, so keep it cheap and never schedule
     * from here.
     */
    default double filterDamage(Player attacker, double damage) {
        return damage;
    }

    /**
     * Fired after a player's hit fully resolves, with the attacker and the damage that actually landed
     * (0 when it was blocked). Lets a reaction mechanic notice the hit itself, and a pacing mechanic
     * meter how fast damage is arriving.
     */
    default void onBossDamaged(Player attacker, double damageDealt) {
    }

    /**
     * True once this phase is finished on its own terms, independently of the boss's health. Returning
     * true advances the fight to the next phase at the next tick; the default never does, leaving the
     * phase to end on its health threshold as usual.
     */
    default boolean readyToAdvance() {
        return false;
    }
}
