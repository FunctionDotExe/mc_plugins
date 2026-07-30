package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Damage-over-time that costs health without hijacking the player's body.
 * <p>
 * Every damage-over-time source in the roster used to be a plain {@link Player#damage(double,
 * org.bukkit.entity.Entity)} on a repeating task — a lava trail, an aura pulse, a meter bleed, a
 * void zone. Each of those calls is a full vanilla hit, and a full vanilla hit does three things
 * beyond taking health:
 * <ul>
 *   <li><b>The hurt tilt.</b> The client rolls the camera on every damage event it receives. Four
 *       DoT sources ticking at 10-tick resolution means the camera is twitching several times a
 *       second for the whole phase, which is unaimable and, for some players, genuinely nauseating.</li>
 *   <li><b>Knockback.</b> {@code damage(amount, source)} applies knockback away from the source and
 *       overwrites velocity, so a DoT pulse cancels a sprint jump, an elytra line or a mid-air dodge.
 *       The player loses the movement the fight is asking them to make, to damage they already
 *       accepted.</li>
 *   <li><b>Invulnerability ticks.</b> A DoT tick sets {@code noDamageTicks} to 20, which then eats
 *       the boss's actual telegraphed slam. A ticking aura was silently acting as damage immunity
 *       against the attack the player was supposed to dodge.</li>
 * </ul>
 * None of that is the mechanic. The mechanic is "standing here costs health", and that is all this
 * applies: health comes off directly, absorption is consumed first, Resistance still reduces it, and
 * the player keeps their camera, their momentum and their i-frames for the hit that is actually aimed
 * at them. The tell is still there — damage-indicator particles, the hurt sound at low volume, and
 * the vanilla heart animation the health change drives on its own.
 * <p>
 * <b>Armor is deliberately not applied.</b> Ticking pressure that armor could shrug off is ignorable
 * damage, which §0.2 forbids; the roster's anti-facetank pressure is armor-ignoring by design. Burst
 * hits are not routed through here and keep the full vanilla pipeline — the hurt tilt on a telegraphed
 * slam is feedback the player wants.
 * <p>
 * <b>A lethal tick is handed back to vanilla.</b> Killing a player with {@code setHealth(0)} skips
 * totems, the death message and the killer credit, so once a tick would finish someone it goes through
 * {@link Player#damage} like any other hit. One hurt tilt on the tick that kills you is not the
 * complaint this class exists to fix.
 */
public final class TickDamage {

    /** Vanilla's Resistance curve: 20% per level, fully immune at Resistance V. */
    private static final double RESISTANCE_STEP = 0.20;
    /** The hurt cue, quiet enough to read as "this is chipping me" rather than "I am being hit". */
    private static final float HURT_VOLUME = 0.35f;
    private static final float HURT_PITCH = 1.2f;
    private static final int INDICATOR_PARTICLES = 4;
    private static final double INDICATOR_SPREAD = 0.35;
    /**
     * Floor between hurt-sound/particle tells for the same player. A single DoT source at 10-tick
     * resolution is fine at full rate, but Worldender-tier fights stack several sources on one
     * player at once (aura + fire trail + meter bleed), and every one of them called {@link #indicate}
     * independently — same {@code ENTITY_PLAYER_HURT} sound firing several times a second reads as a
     * stutter/glitch rather than "I am taking damage", even with the vanilla hurt tilt already gone.
     * Damage itself is never throttled, only the tell.
     */
    private static final long INDICATE_COOLDOWN_MS = 150L;

    private static final Map<UUID, Long> nextIndicateAt = new ConcurrentHashMap<>();

    /**
     * Players who should not hear the hurt cue at all — wired to the accessories that promise it (see
     * {@code Accessory#negatesTickFlinch}). A quiet hurt sound several times a second is the single most
     * grating thing about a long attrition phase, and an item whose whole purpose is "ticks don't rattle
     * me" should silence it. The visual tell is kept for them regardless: damage-indicator particles
     * still fire, and the health bar still animates, so the damage is never invisible.
     */
    private static Predicate<Player> hurtCueExempt = player -> false;

    private TickDamage() {
    }

    /** Called once at startup; the accessory manager is what actually answers the question. */
    public static void setHurtCueExempt(Predicate<Player> exempt) {
        hurtCueExempt = exempt != null ? exempt : player -> false;
    }

    /** Credits the tick to the boss when it is still a usable source, exactly like the burst helpers. */
    public static void apply(BossInstance instance, Player player, double amount) {
        apply(player, amount, source(instance));
    }

    /**
     * One tick of damage-over-time. Safe to call every pulse on anyone, including players who left,
     * died or switched to spectator since the target list was taken.
     *
     * @param source the boss, for kill credit on a lethal tick; null lands the damage unattributed
     */
    public static void apply(Player player, double amount, LivingEntity source) {
        if (player == null || !player.isOnline() || !player.isValid() || player.isDead() || amount <= 0) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || player.isInvulnerable()) {
            return;
        }

        double remaining = afterResistance(player, amount);
        if (remaining <= 0) {
            return;
        }

        double health = player.getHealth();
        double absorption = player.getAbsorptionAmount();
        if (remaining >= health + absorption) {
            // Lethal. Vanilla owns dying — totems, death message, kill credit, the lot. The raw amount
            // goes through rather than the resisted one so the pipeline applies its own Resistance
            // instead of applying it twice.
            if (source != null && source.isValid() && !source.isDead()) {
                player.damage(amount, source);
            } else {
                player.damage(amount);
            }
            return;
        }

        if (absorption > 0) {
            double soaked = Math.min(absorption, remaining);
            player.setAbsorptionAmount(absorption - soaked);
            remaining -= soaked;
        }
        if (remaining > 0) {
            player.setHealth(Math.max(0.0, health - remaining));
        }
        indicate(player);
    }

    /**
     * Takes an amount the vanilla pipeline has <em>already</em> resolved — armor, enchantments and
     * Resistance all applied — and lands it without the tilt. For {@link AmbientTickListener}, which
     * reads a real {@link org.bukkit.event.entity.EntityDamageEvent}'s final damage and then cancels
     * it; nothing here may reduce the number a second time.
     *
     * @return true if this took the damage, meaning the caller should cancel its event; false if the
     *         caller must leave the event alone and let vanilla resolve it
     */
    public static boolean applyResolved(Player player, double amount) {
        if (player == null || !player.isOnline() || !player.isValid() || player.isDead() || amount <= 0) {
            return false;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || player.isInvulnerable()) {
            return false;
        }
        double health = player.getHealth();
        if (amount >= health) {
            // Lethal: vanilla owns dying, and it also owns the death message this cause carries.
            return false;
        }
        // Absorption is already netted out of a resolved amount, but it is not actually spent until the
        // event resolves — and the caller is about to cancel that event, so nothing else will spend it.
        // Left alone it becomes a golden apple that soaks damage forever.
        //
        // The bookkeeping is exact rather than a guess: vanilla soaks min(absorption, damage), so a
        // resolved amount that is still above zero can only mean absorption was drained in full on the
        // way to that number. Zeroing it is what vanilla already did to reach the figure being applied.
        // The case this cannot resolve — absorption soaking the hit entirely, leaving a resolved zero —
        // never reaches here: with nothing to apply, callers must let vanilla have the event so the
        // absorption is really spent.
        if (player.getAbsorptionAmount() > 0) {
            player.setAbsorptionAmount(0);
        }
        player.setHealth(health - amount);
        indicate(player);
        return true;
    }

    /**
     * The boss entity if it is still a usable damage source, or null. A removed or mid-death entity
     * passed to {@link Player#damage(double, org.bukkit.entity.Entity)} credits a corpse at best and
     * throws mid-pulse at worst.
     */
    private static LivingEntity source(BossInstance instance) {
        if (instance == null) {
            return null;
        }
        LivingEntity entity = instance.entity();
        return entity != null && entity.isValid() && !entity.isDead() ? entity : null;
    }

    /**
     * Resistance still counts. Bypassing the damage pipeline means nothing reduces this automatically,
     * and a potion the arena handed the player has to keep working or the counterplay is a lie.
     */
    private static double afterResistance(Player player, double amount) {
        PotionEffect resistance = player.getPotionEffect(PotionEffectType.RESISTANCE);
        if (resistance == null) {
            return amount;
        }
        double reduction = Math.min(1.0, RESISTANCE_STEP * (resistance.getAmplifier() + 1));
        return amount * (1.0 - reduction);
    }

    /**
     * The replacement tell. Damage-indicator particles read to everyone in the fight, the hurt sound
     * is played to the victim alone and at a fraction of its normal volume, and the health bar
     * animates by itself because the health actually changed.
     */
    private static void indicate(Player player) {
        long now = System.currentTimeMillis();
        Long next = nextIndicateAt.get(player.getUniqueId());
        if (next != null && now < next) {
            return;
        }
        nextIndicateAt.put(player.getUniqueId(), now + INDICATE_COOLDOWN_MS);
        Location at = player.getLocation().add(0, 1.0, 0);
        Fx.burst(at, Particle.DAMAGE_INDICATOR, INDICATOR_PARTICLES, INDICATOR_SPREAD);
        if (!hurtCueExempt.test(player)) {
            Fx.sound(player, Sound.ENTITY_PLAYER_HURT, HURT_VOLUME, HURT_PITCH);
        }
    }
}
