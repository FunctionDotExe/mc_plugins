package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * One attack: telegraph (wind-up players can react to), execute (the actual
 * hit), optional recovery (a commitment window before the boss can act
 * again). {@link #sequence} enforces the "no instant attacks" rule — every
 * attack spends at least 10 ticks (0.5s) telegraphing before it lands, and
 * automatically actionbars every nearby player a "casting" progress bar for
 * that whole window so the particle telegraph always has a named, readable
 * countdown next to it.
 */
public abstract class BossAttack {

    private static final int MIN_TELEGRAPH_TICKS = 10;
    private static final int CAST_BAR_SEGMENTS = 10;
    /** Slightly longer than the refresh gap, so a cast bar re-sent each tick never blinks between frames. */
    private static final long CAST_BAR_HOLD_MS = 250;

    protected final WeaponsPlugin plugin;
    private final String bossId;

    /**
     * Stashed by the {@link #run} template method for the duration of one cast so
     * {@link #sequence} can actionbar the right players without every one of the 100+
     * subclasses needing to thread {@code ctx} through their own {@code sequence(...)} call.
     * Safe as instance state: {@code BossManager} allows only one live instance per boss id,
     * and {@code BossInstance} never starts a second attack while one is still in progress.
     */
    private AttackContext activeCtx;

    protected BossAttack(WeaponsPlugin plugin, String bossId) {
        this.plugin = plugin;
        this.bossId = bossId;
    }

    public abstract String name();

    public abstract double cooldownSeconds();

    /** Runs the full telegraph/execute/recovery sequence, then calls {@code onComplete} exactly once. */
    public final void run(AttackContext ctx, Runnable onComplete) {
        this.activeCtx = ctx;
        castRun(ctx, onComplete);
    }

    /** Subclass entry point — was named {@code run} before the cast-bar template method took that name over. */
    protected abstract void castRun(AttackContext ctx, Runnable onComplete);

    protected final double configDouble(String key, double def) {
        return plugin.getConfig().getDouble("bosses." + bossId + "." + key, def);
    }

    protected final int configInt(String key, int def) {
        return plugin.getConfig().getInt("bosses." + bossId + "." + key, def);
    }

    /**
     * One tick of a damage-over-time effect — a lingering cloud, a burning trail, a sustained beam,
     * an aura pulse. Health comes off and the tell still plays, but the player keeps their camera,
     * their momentum and their i-frames, because none of those belong to a chip tick.
     * <p>
     * A discrete hit — the slam, the nova, the detonation — stays on
     * {@code target.damage(amount, ctx.boss())}: the hurt tilt is the feedback that lands the blow.
     * See {@link TickDamage} for what exactly this trades away.
     * <p>
     * Non-players in the blast — a stray cow, another mob caught in the cloud — have no camera to
     * shake, so they take the tick through the ordinary pipeline.
     */
    protected static void tickHurt(AttackContext ctx, LivingEntity target, double amount) {
        if (target instanceof Player player) {
            TickDamage.apply(player, amount, ctx.boss());
        } else if (target != null && target.isValid() && !target.isDead() && amount > 0) {
            target.damage(amount, ctx.boss());
        }
    }

    /**
     * Ticks {@code perTelegraphTick} once a tick for {@code telegraphTicks} (clamped to at least
     * {@value #MIN_TELEGRAPH_TICKS}), then runs {@code execute} once, then waits
     * {@code recoveryTicks} before calling {@code onComplete}.
     *
     * <p>{@code perTelegraphTick}/{@code execute} are guarded: if either throws, the boss must
     * still recover and call {@code onComplete}. Without this, one bad tick in any attack's
     * particle/damage logic (a moved-away target, a despawned world, whatever) would leave
     * {@code attackInProgress} stuck {@code true} forever and freeze that boss for the rest of
     * the fight instead of just skipping the botched hit.
     */
    protected final void sequence(int telegraphTicks, Runnable perTelegraphTick, Runnable execute,
                                   int recoveryTicks, Runnable onComplete) {
        int clampedTelegraph = Math.max(MIN_TELEGRAPH_TICKS, telegraphTicks);
        AttackContext castCtx = this.activeCtx;
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed < clampedTelegraph) {
                    runSafely(perTelegraphTick);
                    runSafely(() -> showCastBar(castCtx, elapsed, clampedTelegraph));
                    runSafely(() -> playTelegraphCue(castCtx, elapsed, clampedTelegraph));
                    elapsed++;
                    return;
                }
                cancel();
                runSafely(execute);
                runSafely(() -> clearCastBar(castCtx));
                if (recoveryTicks <= 0) {
                    onComplete.run();
                } else {
                    plugin.getServer().getScheduler().runTaskLater(plugin, onComplete, recoveryTicks);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * "Attack Name ▰▰▰▰▰▱▱▱▱▱" — fills left-to-right as the telegraph counts down toward
     * execution, so the ground-particle warning always has a named, timed readout next to it.
     * A no-op if this attack somehow runs without a context (shouldn't happen — {@link #run} always
     * stashes one first — but a cosmetic actionbar is never worth crashing a boss fight over).
     */
    private void showCastBar(AttackContext ctx, int elapsed, int totalTicks) {
        if (ctx == null) {
            return;
        }
        int filled = Math.min(CAST_BAR_SEGMENTS,
                (int) Math.ceil(CAST_BAR_SEGMENTS * (elapsed + 1) / (double) totalTicks));
        Component bar = Component.text(name() + " ", NamedTextColor.YELLOW)
                .append(Component.text("▰".repeat(filled), NamedTextColor.RED))
                .append(Component.text("▱".repeat(CAST_BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY));
        // Re-flashed every telegraph tick so the bar fills; PRIORITY_SUSTAINED keeps it from stomping
        // a brief combat notice that lands mid-cast, which a raw sendActionBar per tick always did.
        for (Player player : Arena.playersNear(ctx.bossLocation(), ctx.arena().radius())) {
            plugin.actionBarHub().flash(player, bar, CAST_BAR_HOLD_MS, ActionBarHub.PRIORITY_SUSTAINED);
        }
    }

    /**
     * The audible half of a telegraph: a low wind-up note the instant the cast starts, and a higher, louder
     * one on the last tick before it lands.
     * <p>
     * Every mechanic in the roster telegraphed visually only — ground particles and a cast bar, both of
     * which require looking at the right part of the screen at the right moment, in a fight that is
     * deliberately full of particles. Two notes a fixed interval apart are readable peripherally and
     * without reading anything, and the rising pitch is what makes "it is charging" and "it lands now"
     * distinguishable rather than one continuous noise.
     * <p>
     * Deliberately two keys for the whole roster rather than a key per attack. A telegraph cue is a
     * <em>language</em>: its value is that "that rising pair means something is about to land on me" is
     * learned once and then holds for all seventeen bosses. Two hundred distinct wind-up sounds would be
     * two hundred sounds nobody learns, and the cue would carry no information at all. Which attack it is
     * remains the cast bar's job, and what it does remains the attack's own impact sound's job — both of
     * which are per-attack already.
     */
    private void playTelegraphCue(AttackContext ctx, int elapsed, int totalTicks) {
        if (ctx == null || !plugin.getConfig().getBoolean("boss-audio.telegraph-cues", true)) {
            return;
        }
        boolean imminent = elapsed == totalTicks - 1;
        if (elapsed != 0 && !imminent) {
            return;
        }
        BossAudio.play(ctx.bossLocation(),
                imminent ? "boss.telegraph.release" : "boss.telegraph.windup",
                imminent ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
                imminent ? 1.1f : 0.8f,
                imminent ? 1.5f : 0.6f);
    }

    /** Clears the cast bar the instant the attack lands — recovery isn't part of the cast, it shouldn't linger. */
    private void clearCastBar(AttackContext ctx) {
        if (ctx == null) {
            return;
        }
        for (Player player : Arena.playersNear(ctx.bossLocation(), ctx.arena().radius())) {
            plugin.actionBarHub().clearFlash(player, ActionBarHub.PRIORITY_SUSTAINED);
        }
    }

    private void runSafely(Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Boss attack '" + name() + "' threw during execution — recovering instead of freezing the boss.", e);
        }
    }
}
