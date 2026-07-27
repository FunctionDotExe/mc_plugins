package dev.rbm72.weaponsplugin.boss.meter;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The "you are held, and it is costing you" state every meter threshold ends in — frozen solid,
 * struck rigid, ruptured, banished. One owner for all of it, per fight.
 * <p>
 * Two decisions here are load-bearing and both exist because of how a Minecraft server actually loses
 * player state:
 * <ul>
 *   <li><b>Effects are re-asserted every pulse with a short duration rather than applied once for the
 *       whole hold.</b> A player who disconnects mid-hold cannot be reached by any cleanup we run —
 *       {@code Bukkit.getPlayer} returns null and their profile is already on disk. A one-shot
 *       eight-second Slowness 250 written into that profile means they log back in unable to move,
 *       possibly after the fight has ended and the boss no longer exists. A duration barely longer
 *       than the pulse means the worst case is a second of stiffness on relog, self-healing with no
 *       cleanup path required.</li>
 *   <li><b>Walk speed is deliberately never touched.</b> It is the obvious way to root a player and it
 *       is the wrong one for exactly the same reason: {@code setWalkSpeed(0)} persists across logout
 *       and there is no offline API to put it back. Slowness at a saturating amplifier plus a negative
 *       jump boost holds a player just as still and expires by itself.</li>
 *   <li><b>A hold only ever takes back what it put on.</b> Releasing used to call
 *       {@code removePotionEffect} on all three types outright, which silently deleted a player's own
 *       Slowness from a potion, a Jump Boost from a beacon, or a Blindness another boss mechanic had
 *       applied — the hold was reaching past its own state and wiping unrelated effects the player had
 *       paid for. What the player was carrying is snapshotted the first time the hold touches them and
 *       put back on release; see {@link #clearEffects}.</li>
 * </ul>
 * Everything here runs on the main thread, driven by {@link MeterRegistry}'s single pulse — no
 * affliction schedules a task of its own, so there is nothing per-player left to leak.
 */
public final class MeterAfflictions {

    /**
     * Amplifier that takes movement speed to zero rather than merely slowing it. Anything less and a
     * "frozen solid" player can still shuffle to the campfire, which turns the threshold from a
     * consequence into an inconvenience.
     */
    private static final int ROOT_SLOWNESS_AMPLIFIER = 250;
    /** The vanilla trick for "cannot jump at all" — high amplifiers wrap to a negative jump modifier. */
    private static final int ROOT_JUMP_AMPLIFIER = 128;
    /** Blindness has no useful amplifier; named only so {@link #clearEffects} can match on it. */
    private static final int BLIND_AMPLIFIER = 0;
    /** Extra ticks of headroom on each re-assertion so the effect never lapses between two pulses. */
    private static final int REASSERT_BUFFER_TICKS = 20;

    /** The three effects a hold overwrites, and therefore the three it has to be able to put back. */
    private static final PotionEffectType[] HELD_TYPES = {
            PotionEffectType.SLOWNESS, PotionEffectType.JUMP_BOOST, PotionEffectType.BLINDNESS
    };

    /**
     * One player's live hold.
     *
     * @param label            what the readout should call it while it runs
     * @param expiresAtMs      wall clock end of the hold
     * @param damagePerSecond  bleed while held — the designs are explicit that a held player is losing
     *                         health, not merely parked
     * @param blind            whether the hold also takes their sight (banishment, an ice shell over the eyes)
     * @param frost            whether to drive the vanilla freeze overlay, for the cold skins
     */
    private record Hold(String label, long expiresAtMs, double damagePerSecond, boolean blind, boolean frost) {
    }

    /**
     * What one player was carrying before a hold overwrote it, taken the first time that hold actually
     * applies anything and consumed when it releases.
     * <p>
     * Deliberately not a record: the frost baseline is captured later than the potion snapshot, and only
     * if a hold ever drives the freeze overlay at all. A hold that never freezes must not touch a
     * player's powder-snow freeze on the way out, and {@code null} here is how that is expressed.
     */
    private static final class PriorState {

        private final List<PotionEffect> effects;
        private final long capturedAtMs;
        private Integer freezeTicks;

        private PriorState(List<PotionEffect> effects, long capturedAtMs) {
            this.effects = effects;
            this.capturedAtMs = capturedAtMs;
        }
    }

    private final BossInstance instance;
    private final Map<UUID, Hold> holds = new HashMap<>();
    /**
     * Present exactly while this class has effects of its own live on a player. Doubles as the "did we
     * ever apply anything" flag, so a hold released before its first pulse removes nothing at all.
     */
    private final Map<UUID, PriorState> priorStates = new HashMap<>();
    /**
     * Duration of the most recent re-assertion, which is the longest one of ours can possibly be. The
     * ownership test in {@link #clearEffects} needs it, and it is derived from the pulse interval rather
     * than fixed. Seeded with the buffer alone so a release that somehow beats the first pulse still has
     * a sane bound to compare against.
     */
    private int lastAssertedDurationTicks = REASSERT_BUFFER_TICKS;

    MeterAfflictions(BossInstance instance) {
        this.instance = instance;
    }

    /**
     * Pins {@code player} in place for {@code ticks}, bleeding them at {@code damagePerSecond}.
     * <p>
     * Re-holding someone already held takes the longer of the two expiries rather than restarting the
     * clock, so a second threshold landing on a player mid-freeze cannot shorten the first one.
     */
    public void hold(Player player, String label, int ticks, double damagePerSecond, boolean blind, boolean frost) {
        if (player == null || !player.isOnline()) {
            return;
        }
        long until = System.currentTimeMillis() + Math.max(0, ticks) * 50L;
        Hold existing = holds.get(player.getUniqueId());
        if (existing != null) {
            until = Math.max(until, existing.expiresAtMs());
        }
        holds.put(player.getUniqueId(), new Hold(label, until, damagePerSecond, blind, frost));
    }

    /** True while this player is pinned by a threshold — the meter itself checks this to stop piling on. */
    public boolean isHeld(Player player) {
        Hold hold = holds.get(player.getUniqueId());
        return hold != null && System.currentTimeMillis() < hold.expiresAtMs();
    }

    /** What the readout should say about this player right now, or null if they are free. */
    public String heldLabel(Player player) {
        Hold hold = holds.get(player.getUniqueId());
        return hold != null && System.currentTimeMillis() < hold.expiresAtMs() ? hold.label() : null;
    }

    /**
     * Cuts a hold short — the ally who broke the ice shell, the tether crystal destroyed, the chorus
     * fruit eaten. Every threshold in the designs has a rescue act, so this is the API those acts call.
     */
    public void release(Player player) {
        if (holds.remove(player.getUniqueId()) != null) {
            clearEffects(player);
        }
    }

    /**
     * One step of every live hold: expire the finished ones, re-assert the effects on the rest, and
     * take the bleed. Driven by {@link MeterRegistry} so afflictions never own a task.
     *
     * @param stepTicks how long until the next pulse, which sets the re-assertion duration
     */
    void pulse(long stepTicks) {
        if (holds.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        double stepSeconds = stepTicks / 20.0;
        int duration = (int) stepTicks + REASSERT_BUFFER_TICKS;
        lastAssertedDurationTicks = duration;

        // Resolved once for the whole sweep. This method runs BEFORE MeterRegistry's own validity
        // check, on purpose — holds have to expire and release even when the boss is already gone — so
        // the entity here is routinely mid-despawn or mid-death and cannot be assumed usable.
        LivingEntity source = damageSource();

        for (Iterator<Map.Entry<UUID, Hold>> it = holds.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Hold> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                // Gone mid-hold. Dropping the entry is the whole cleanup: the short effects they are
                // carrying expire on their own within a second of them logging back in. The snapshot of
                // what they were carrying goes with it — it is only meaningful against a live session,
                // and keeping it would grow forever across a fight players keep rejoining.
                it.remove();
                priorStates.remove(entry.getKey());
                continue;
            }
            Hold hold = entry.getValue();
            if (now >= hold.expiresAtMs()) {
                it.remove();
                clearEffects(player);
                continue;
            }
            applyEffects(player, hold, duration);
            if (hold.damagePerSecond() > 0 && player.isValid() && !player.isDead()) {
                bleed(player, hold.damagePerSecond() * stepSeconds, source);
            }
        }
    }

    /**
     * The boss entity if it is still a usable damage source, or null.
     * <p>
     * Passing a removed or mid-death entity to {@link Player#damage(double, org.bukkit.entity.Entity)}
     * is the failure this guards: at best the kill is credited to a corpse that is no longer in the
     * world, at worst the damage call throws on the way into the damage source and takes the whole
     * pulse — including every other player's hold expiry — down with it.
     */
    private LivingEntity damageSource() {
        LivingEntity entity = instance.entity();
        return entity != null && entity.isValid() && !entity.isDead() ? entity : null;
    }

    /**
     * The hold's bleed. With no usable boss the damage still lands — a hold that stopped hurting the
     * moment the boss blinked out would be a free escape from a threshold — but it lands unattributed
     * rather than credited to something that no longer exists.
     */
    private void bleed(Player player, double amount, LivingEntity source) {
        if (source == null) {
            player.damage(amount);
        } else {
            player.damage(amount, source);
        }
    }

    /**
     * Frees everyone, whatever state they were in. Called from fight teardown, and the reason a fight
     * that ends mid-freeze does not leave a player standing rooted in an empty arena.
     */
    void releaseAll() {
        for (UUID id : holds.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                clearEffects(player);
            }
        }
        holds.clear();
        // Anyone offline at teardown never gets a clearEffects call, so their snapshot would otherwise
        // outlive the fight that took it. Their own effects are a second from expiring regardless.
        priorStates.clear();
    }

    private void applyEffects(Player player, Hold hold, int durationTicks) {
        // First touch is the only moment the player's own effects are still visible, so it is the only
        // moment the snapshot can be taken. Everything after this is our own re-assertion overwriting
        // our own re-assertion.
        PriorState prior = priorStates.computeIfAbsent(player.getUniqueId(), id -> capture(player));

        // Direct addPotionEffect rather than StatusEffectManager: that helper deliberately keeps the
        // longer of the two durations, which here would extend the hold past its own expiry every
        // single pulse and never let go.
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, ROOT_SLOWNESS_AMPLIFIER, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, durationTicks, ROOT_JUMP_AMPLIFIER, false, false));
        if (hold.blind()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationTicks, BLIND_AMPLIFIER, false, false));
        }
        if (hold.frost()) {
            if (prior.freezeTicks == null) {
                prior.freezeTicks = player.getFreezeTicks();
            }
            // Vanilla's own freeze overlay and shiver — the physical tell for the cold skins, and it
            // ticks down by itself the moment we stop topping it up.
            player.setFreezeTicks(Math.min(player.getMaxFreezeTicks(), player.getFreezeTicks() + durationTicks));
        }
    }

    /** What the player is carrying on the three types a hold is about to overwrite. */
    private PriorState capture(Player player) {
        List<PotionEffect> carried = new ArrayList<>(HELD_TYPES.length);
        for (PotionEffectType type : HELD_TYPES) {
            PotionEffect active = player.getPotionEffect(type);
            if (active != null) {
                carried.add(active);
            }
        }
        return new PriorState(carried, System.currentTimeMillis());
    }

    /**
     * Undoes this hold and only this hold: our effects come off, whatever the player had before the
     * hold started goes back on with its clock advanced, and anything a third party applied while the
     * hold was running is left where it is.
     * <p>
     * Ownership is decided by shape, not by trust. Our effects carry amplifiers nothing else in this
     * plugin uses and are re-asserted every pulse with a duration barely longer than one, so an active
     * effect that does not match both is not ours to delete. The residual case — something else applying
     * Slowness 250 with a sub-second duration during the hold — is deliberately not handled: Bukkit
     * offers no provenance on an applied effect, so distinguishing it would mean tagging players with
     * metadata that then has to survive the same disconnects this class already refuses to depend on,
     * and the cost of being wrong (a player keeps a root they would have shed a moment later) is smaller
     * than the cost of the bug this replaces.
     */
    private void clearEffects(Player player) {
        PriorState prior = priorStates.remove(player.getUniqueId());
        if (prior == null) {
            // Held but never pulsed, or already cleared. Nothing of ours is on this player, and removing
            // the three types here would be the exact bug this method exists to stop.
            return;
        }
        removeIfOurs(player, PotionEffectType.SLOWNESS, ROOT_SLOWNESS_AMPLIFIER);
        removeIfOurs(player, PotionEffectType.JUMP_BOOST, ROOT_JUMP_AMPLIFIER);
        removeIfOurs(player, PotionEffectType.BLINDNESS, BLIND_AMPLIFIER);
        restore(player, prior);

        if (prior.freezeTicks != null) {
            // Only a hold that actually froze touches this, and it drops back to the reading from before
            // the hold rather than to zero: a player standing in powder snow is legitimately freezing on
            // their own, and zeroing it outright was handing them a free reset on a vanilla hazard.
            player.setFreezeTicks(Math.min(player.getFreezeTicks(), prior.freezeTicks));
        }
    }

    private void removeIfOurs(Player player, PotionEffectType type, int amplifier) {
        PotionEffect active = player.getPotionEffect(type);
        if (active == null) {
            return;
        }
        // A negative duration is vanilla's infinite marker (a beacon, an /effect infinite) — never ours,
        // and it would read as "shorter than a re-assertion" on a naive comparison.
        if (active.getAmplifier() != amplifier
                || active.getDuration() < 0
                || active.getDuration() > lastAssertedDurationTicks) {
            return;
        }
        player.removePotionEffect(type);
    }

    /**
     * Puts back what the player had, with the time they spent held taken off the clock — restoring the
     * full original duration would hand them back more of their own potion than they actually had left,
     * and a long hold could turn a nearly-expired effect into a fresh one.
     */
    private void restore(Player player, PriorState prior) {
        int elapsedTicks = (int) Math.min(Integer.MAX_VALUE,
                (System.currentTimeMillis() - prior.capturedAtMs) / 50L);
        for (PotionEffect effect : prior.effects) {
            if (effect.getDuration() < 0) {
                // Infinite: no clock to advance, so it goes back exactly as it was.
                player.addPotionEffect(effect);
                continue;
            }
            int remaining = effect.getDuration() - elapsedTicks;
            if (remaining > 0) {
                player.addPotionEffect(new PotionEffect(effect.getType(), remaining, effect.getAmplifier(),
                        effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
            }
        }
    }
}
