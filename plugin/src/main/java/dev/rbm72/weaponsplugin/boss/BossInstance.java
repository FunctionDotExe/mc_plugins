package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.ai.AttackSelector;
import dev.rbm72.weaponsplugin.boss.ai.TargetSelector;
import dev.rbm72.weaponsplugin.boss.integration.BossHologram;
import dev.rbm72.weaponsplugin.boss.integration.DiscordNotifier;
import dev.rbm72.weaponsplugin.boss.integration.WorldGuardArenaGuard;
import dev.rbm72.weaponsplugin.boss.grief.ArenaLedger;
import dev.rbm72.weaponsplugin.boss.meter.MeterRegistry;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime state of one live boss fight: the spawned entity, current phase,
 * target, arena, adds, and every task/entity that needs cleaning up when
 * the fight ends. Nothing here is shared across fights — {@link Boss} is
 * the stateless definition, this is the instance.
 */
public final class BossInstance {

    public enum EndReason {
        DEFEATED, DESPAWNED, PLUGIN_DISABLE
    }

    /** Enrage phase gets visibly bigger, faster, and louder — the "final form" every boss lacked. */
    private static final double ENRAGE_SCALE_MULTIPLIER = 1.35;
    private static final double ENRAGE_SPEED_MULTIPLIER = 1.4;

    /**
     * Extra distance beyond the arena radius a player still counts as "present" for the boss
     * bar and phase-transition titles. Combat knockback routinely shoves players past the strict
     * arena radius for a tick or two; without this margin that reads as the bar randomly
     * vanishing and phase titles silently never showing.
     */
    private static final double UI_PRESENCE_BUFFER = 8.0;
    /** Consecutive ticks (at {@code BossManager.TICK_INTERVAL} apart) entity.isValid() must read
     *  false before the fight actually ends — see the debounce comment in {@link #tick()}. */
    private static final int INVALID_TICKS_BEFORE_DESPAWN = 4;

    /** Ceiling on stacked permanent hardening, so repeated failures can never make a boss unkillable. */
    private static final double MAX_PERMANENT_DAMAGE_REDUCTION = 0.6;

    private final WeaponsPlugin plugin;
    private final BossManager manager;
    private final Boss boss;
    private final LivingEntity entity;
    private final Arena arena;
    private final double maxHealth;

    private final AddManager addManager = new AddManager();
    private final BossBarController barController = new BossBarController();
    private final MechanicBar mechanicBar = new MechanicBar();
    private final ArenaLedger ledger;
    private final MeterRegistry meters;
    private final ArenaBarrier arenaBarrier;
    private final BossAmbiance.Handle ambianceHandle;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<BossAttack, Long> lastUsedAtMs = new HashMap<>();
    private final Set<UUID> griefEntities = new HashSet<>();

    private BossPhase currentPhase;
    private BossAttack lastAttack;
    private Player currentTarget;
    private boolean attackInProgress;
    private boolean ended;
    private PhaseMechanic gate;
    private BossEvent activeEvent;
    /** "<eventId>@<fraction>" for every milestone already spent, so none can replay if the boss heals. */
    private final Set<String> firedEvents = new HashSet<>();
    /**
     * A mechanic's standing say on who the boss should be chasing, or null for the default rule.
     * Cleared on every phase change so an override can never outlive the mechanic that set it.
     */
    private java.util.function.Supplier<Player> targetOverride;
    private double damageMultiplier = 1.0;
    /** Never resets on phase change — see {@link #addPermanentDamageReduction}. */
    private double permanentDamageReduction;
    private long stunnedUntilMs;
    private boolean forcedInvulnerable;
    private int exposuresThisPhase;
    private long floorLockStartMs;
    /** One warning per phase when the floor-lock valve fires, so a bypassed objective is never silent. */
    private boolean floorLockTimeoutLogged;
    private long lastDeflectMs;
    private int invalidTicks;

    BossInstance(WeaponsPlugin plugin, BossManager manager, Boss boss, LivingEntity entity, Arena arena, double maxHealth) {
        this.plugin = plugin;
        this.manager = manager;
        this.boss = boss;
        this.entity = entity;
        this.arena = arena;
        this.maxHealth = maxHealth;

        this.ledger = new ArenaLedger(plugin, boss.arenaRestoreEnabled(), boss.maxLedgerBlocks(),
                boss.restoreBlocksPerTick());
        this.arenaBarrier = boss.arenaBarrierEnabled() ? new ArenaBarrier(arena.center(), arena.radius()) : null;
        // Built before the first phase enters, because a phase's onEnter is one of the two places a
        // meter is legitimately attached (the other being the boss's own spawn hook).
        this.meters = new MeterRegistry(this);

        this.ambianceHandle = boss.ambiance().start(this);
        this.currentPhase = BossPhase.select(boss.phases(), 1.0);
        this.currentPhase.onEnter(this);
        startGate(currentPhase);

        if (boss.worldGuardProtectionEnabled()) {
            WorldGuardArenaGuard.start(boss.id(), arena.center(), arena.radius());
        }
        BossHologram.start(this);

        showTitle(boss.entranceTitle(), boss.entranceSubtitle());
    }

    public WeaponsPlugin plugin() {
        return plugin;
    }

    public Boss boss() {
        return boss;
    }

    public LivingEntity entity() {
        return entity;
    }

    public Arena arena() {
        return arena;
    }

    public AddManager addManager() {
        return addManager;
    }

    /**
     * True while an attack is mid-sequence — telegraph up, or the hit itself still landing.
     * <p>
     * Exposed for mechanics that <em>move the boss</em> outside of the attack system. The framework
     * already refuses to start a second attack or to pathfind while this is set, but a mechanic that
     * teleports him has no way to see it, and a boss that relocates between a telegraph and its payload
     * reads as a bug even when the payload lands honestly where the telegraph pointed.
     */
    public boolean attacking() {
        return attackInProgress;
    }

    /**
     * This fight's undo log for world damage. Every block a boss changes goes through here first, and
     * the whole thing is rolled back in {@link #end}. Public because the destructive primitives and the
     * explosion listener both live outside this package.
     */
    public ArenaLedger ledger() {
        return ledger;
    }

    /**
     * This fight's player meters — the armour-ignoring, non-healable clocks four bosses in the roster
     * are built around (Chill, Static Charge, Infection, Void Echo).
     * <p>
     * A boss or a phase attaches a skin with {@code meters().attach(spec)} and keeps the returned
     * handle; anything else in the fight finds it again with {@code meters().meter("chill")}. A
     * fight-long meter should be attached from the boss's first phase and simply left alone — the
     * registry's teardown in {@link #end} is what puts every affected player back to normal. A meter
     * that genuinely belongs to one phase must be detached by that phase's mechanic in its
     * {@code onStop()}, since a phase change deliberately does not touch meters (a Chill clock that
     * reset itself every phase transition would hand players a free purge for pushing damage).
     */
    public MeterRegistry meters() {
        return meters;
    }

    /**
     * The dedicated mechanic readout bar. Anything with running state a player must be able to see —
     * a hit counter, a countdown, which half of the floor they are standing on — belongs here rather
     * than on the action bar, where it competes with cast bars and combat notices and loses.
     */
    public MechanicBar mechanicBar() {
        return mechanicBar;
    }

    /** Everyone the mechanic bar should currently be talking to. */
    public java.util.List<Player> barViewers() {
        return arena.playersNear(UI_PRESENCE_BUFFER);
    }

    /**
     * How many of this fight's boss bars {@code player} is being shown right now — the health bar, and
     * the mechanic bar when the running mechanic has something to say to them.
     * <p>
     * Deliberately excludes the meter readout, which is the thing asking: a meter that counted its own
     * bar would flip channels on its own output every pulse. Boss bars tile up the screen and their
     * names sit tight against the bar above, so a third one is where a stack stops being readable — see
     * {@link dev.rbm72.weaponsplugin.boss.meter.MeterRegistry} for what is done about it.
     */
    public int visibleBarCount(Player player) {
        int count = barController.isShowingTo(player) ? 1 : 0;
        if (mechanicBar.isShowingTo(player)) {
            count++;
        }
        return count;
    }

    public boolean isEnraged() {
        return currentPhase.isEnrage();
    }

    /**
     * Registers a task for cancellation when the fight ends. Public: {@link PhaseMechanic}
     * implementations live outside this package and every one of them schedules work.
     */
    public void trackTask(BukkitTask task) {
        tasks.add(task);
    }

    /**
     * Current scalar applied to every player hit — 1.0 outside a vulnerability cycle, armored/exposed
     * otherwise, hard-pinned to 0.0 whenever a trial mechanic (gaze/sanctuary/marked-target) has this
     * boss forcibly invulnerable, regardless of whatever the vulnerability cycle is doing underneath.
     */
    double damageMultiplier() {
        if (forcedInvulnerable) {
            return 0.0;
        }
        return damageMultiplier * (1.0 - permanentDamageReduction);
    }

    /**
     * Permanently hardens this boss for the rest of the fight — armour it recovered, a ritual it
     * completed, anything the group had a chance to prevent and didn't. Unlike the phase multiplier
     * this never resets on a phase change, which is the entire point: it is the lasting consequence of
     * a failed optional mechanic, and it compounds each time that mechanic is failed again.
     * <p>
     * Capped well short of total immunity so no amount of stacked failure can make a boss unkillable
     * and strand a group in a fight they cannot finish or leave.
     */
    public void addPermanentDamageReduction(double amount) {
        if (amount <= 0) {
            return;
        }
        permanentDamageReduction = Math.min(MAX_PERMANENT_DAMAGE_REDUCTION, permanentDamageReduction + amount);
    }

    /**
     * Lets the running mechanic decide who the boss chases, overriding {@link TargetSelector}'s default
     * "press the weakest player" rule for as long as the supplier keeps naming a live combatant.
     * <p>
     * Three of the batch-1 designs are built on the boss pursuing a <em>specific</em> player for reasons
     * the default rule cannot see: the Fallen King hunts whoever is carrying a Crown Shard, the Frost
     * Queen's lances follow whoever is carrying fire to the Heart, and the Void Sovereign fixes on the
     * player he has just blinked onto. Each of those is the phase's whole pressure, and expressing it by
     * having a mechanic damage its preferred target into the lowest-health slot would be both indirect
     * and wrong — the carrier is frequently the healthiest person in the room, on purpose.
     * <p>
     * A supplier rather than a player, because the answer changes every pulse and a mechanic should not
     * have to re-register it. Returning null falls straight back to the default rule, so a phase with no
     * carrier right now behaves exactly as an unhooked fight does. Cleared automatically on every phase
     * change; a mechanic that sets it mid-phase should still clear it in its own teardown.
     */
    public void setTargetOverride(java.util.function.Supplier<Player> override) {
        this.targetOverride = override;
    }

    /**
     * The override's answer for this tick, or null when there is none or it named somebody the fight
     * should not be pointed at (offline, spectating, dead, gone from the arena).
     */
    private Player overriddenTarget() {
        if (targetOverride == null) {
            return null;
        }
        Player chosen;
        try {
            chosen = targetOverride.get();
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Target override threw for boss '" + boss.id() + "' — falling back to the default rule.", e);
            return null;
        }
        return chosen != null && chosen.isValid() && !chosen.isDead() && Arena.isCombatant(chosen) ? chosen : null;
    }

    /** Public: trial attacks live in {@code bosses.attacks}, a different package from the rest of this framework. */
    public void setDamageMultiplier(double multiplier) {
        this.damageMultiplier = multiplier;
    }

    /**
     * A trial attack (gaze/sanctuary/marked-target) uses this to make the boss briefly untouchable
     * while it demands players do something other than hit it — surviving the mechanic, not more DPS,
     * is the actual task during that window. Public: trial attacks live in {@code bosses.attacks}, a
     * different package from the rest of this framework.
     */
    public void setForcedInvulnerable(boolean invulnerable) {
        this.forcedInvulnerable = invulnerable;
    }

    /**
     * Called the instant a phase's forced mechanic is actually completed — this phase's
     * {@link PhaseMechanic} opening (weak-point set broken, guards cleared, hostage freed, parry window
     * struck), or a signature trial attack resolving successfully. Either kind satisfies the phase
     * floor below; a boss isn't required to clear both its gate and a trial in the same phase, just
     * to have genuinely engaged with at least one. Public: gates and signature mechanics both live
     * outside this package.
     */
    public void recordExposure() {
        exposuresThisPhase++;
    }

    /**
     * Called by a mechanic every time the group makes <em>partial</em> headway on this phase's
     * objective — one grave of two broken, one anchor of four cut, a pile mined out. Resets the floor
     * lock's patience clock without satisfying the phase.
     * <p>
     * This is what separates "the objective is unreachable" from "the objective is long". The timeout
     * valve exists for the first case, but it could not tell the two apart: an objective that takes
     * three minutes of block work looked exactly like a griefed weak point nobody could touch, so a
     * group that ignored the mechanic entirely and a group grinding through it were both handed the
     * phase at the same moment — and the group ignoring it got there first, because pure DPS is faster
     * than DPS plus mining. With progress resetting the clock, the valve only ever fires on a fight
     * that is genuinely making no headway at all.
     */
    public void recordProgress() {
        floorLockStartMs = 0L;
    }

    private boolean phaseMechanicSatisfied() {
        if (!currentPhase.hasMechanic()) {
            return true;
        }
        // A phase that ends on its own terms (survive the timer, push it off the throne) counts as
        // satisfied the moment it declares itself done — otherwise the floor would pin the boss on the
        // seam while the mechanic is simultaneously trying to move the fight past it.
        return exposuresThisPhase > 0 || mechanicReadyToAdvance();
    }

    private boolean mechanicReadyToAdvance() {
        if (gate == null) {
            return false;
        }
        try {
            return gate.readyToAdvance();
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Phase mechanic threw deciding whether to advance — treating it as not ready.", e);
            return false;
        }
    }

    /**
     * The active mechanic's per-attacker say on one hit, applied after the global multiplier and
     * before the phase floor. This is what lets a phase express a rule about <em>how</em> you are
     * fighting — only the duel target's blows land, only rear hits count — rather than the single
     * global "can anyone hurt it right now" switch the old gate system was limited to.
     */
    double filterMechanicDamage(Player attacker, double damage) {
        if (gate == null || attacker == null) {
            return damage;
        }
        try {
            return gate.filterDamage(attacker, damage);
        } catch (Exception e) {
            // Never let a broken rule cancel a legitimate hit — fall back to the unfiltered number.
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Phase mechanic threw filtering a hit — using the unfiltered damage.", e);
            return damage;
        }
    }

    /**
     * Relays a resolved player hit to the active mechanic so a reaction archetype can see the hit
     * itself rather than only the clock, and a pacing archetype can meter how fast damage is arriving.
     * Called from {@link BossDamageListener} once the final damage number is settled.
     */
    void notifyGateDamaged(Player attacker, double damageDealt) {
        // Events see the hit first and see it unconditionally, including hits their own shield just
        // blocked down to zero — a hit-count check is counting swings, not damage.
        if (activeEvent != null) {
            try {
                activeEvent.onHit(this, attacker, damageDealt);
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Boss event '" + activeEvent.id() + "' threw handling a hit — ignoring it.", e);
            }
        }
        if (gate == null) {
            return;
        }
        try {
            gate.onBossDamaged(attacker, damageDealt);
        } catch (Exception e) {
            // A mechanic throwing on a damage notification must never cancel the hit or kill the fight.
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Phase gate threw handling a boss hit — ignoring the notification.", e);
        }
    }

    /**
     * True once the current phase's mandatory mechanic has been left <em>entirely</em> unengaged past
     * {@link Boss#phaseFloorTimeoutMs()} — a broken or unreachable weak-point set (griefed terrain, a
     * disconnected solo player) must not be able to hard-lock the fight forever, so past the grace
     * period the floor stops gating. The clock is reset whenever the mechanic is satisfied, whenever the
     * phase changes, and on any partial progress via {@link #recordProgress()}.
     * <p>
     * Logged when it fires, once per phase. A silent valve is indistinguishable from a phase gate that
     * was never wired up — which is exactly how it read in play: four phases handed over on the timer,
     * every objective still standing, and nothing anywhere saying why.
     */
    private boolean floorLockTimedOut() {
        long now = System.currentTimeMillis();
        if (floorLockStartMs == 0L) {
            floorLockStartMs = now;
            return false;
        }
        if (now - floorLockStartMs <= boss.phaseFloorTimeoutMs()) {
            return false;
        }
        if (!floorLockTimeoutLogged) {
            floorLockTimeoutLogged = true;
            plugin.getLogger().warning(() -> "Boss '" + boss.id() + "' phase '" + currentPhase.name()
                    + "' handed over its health seam on the floor-lock timeout — its objective went "
                    + boss.phaseFloorTimeoutMs() + "ms without any progress. The mechanic was bypassed, "
                    + "not completed.");
        }
        return true;
    }

    /**
     * Stops raw burst from skipping whole phases without ever being able to hard-lock the fight.
     * Every phase — not just the final one — holds its floor until that phase's mechanic has actually
     * been engaged, with {@link #floorLockTimedOut()} as the escape hatch.
     * <ul>
     *   <li><b>Mechanic not yet satisfied:</b> the floor pins health <em>exactly on</em> the next
     *       phase's boundary. {@link BossPhase#select} only advances once health drops strictly below
     *       a threshold, so sitting on the seam means the phase deliberately does not tick over: the
     *       boss stays here until players do what this phase is asking. The final phase, having no
     *       next boundary, pins at 1% instead.</li>
     *   <li><b>Mechanic satisfied</b> (or the floor lock timed out): the floor drops a sliver
     *       <em>past</em> the seam so the crossing actually fires, and the final phase opens all the
     *       way to 0 so the boss can be finished.</li>
     * </ul>
     * An ungated phase counts as satisfied from the instant it starts, so it behaves as a pure,
     * unobstructed damage race — that is the whole point of authoring one.
     * <p>
     * This used to gate only the final phase, on the reasoning that a phase's armored multiplier was
     * itself the wall and a floor that lingered risked freezing the fight. That was wrong in a way
     * that let high-DPS groups delete a boss outright: every gate opens with a short grace window
     * before it arms (so a phase transition isn't three cinematics at once), during which damage runs
     * at ×1.0. With the floor allowing a hit to land past the seam, each of those windows was enough
     * to cross a whole band — chaining every phase's grace window back-to-back took a boss from full
     * to 1% in seconds, with no gate ever finishing arming, and the timeout valve then handed over the
     * kill. Pinning the seam until the mechanic is engaged closes that chain; the timeout valve (now
     * applying to every phase) is what keeps an unreachable weak point from deadlocking the fight.
     * <p>
     * Called from {@link BossDamageListener} at MONITOR, after every other multiplier/bonus — the
     * last word on the number, not an earlier, bypassable step.
     */
    double clampToPhaseFloor(double rawDamage) {
        List<BossPhase> phases = boss.phases();
        int index = phases.indexOf(currentPhase);
        boolean lastPhase = index + 1 >= phases.size();

        boolean satisfied = phaseMechanicSatisfied();
        if (satisfied) {
            floorLockStartMs = 0L;
        } else if (floorLockTimedOut()) {
            satisfied = true;
        }

        double floorFraction = lastPhase
                ? (satisfied ? 0.0 : 0.01)
                : phases.get(index + 1).entryThresholdFraction();

        if (floorFraction <= 0.0) {
            return rawDamage;
        }
        double floorHealth = maxHealth * floorFraction;
        if (!lastPhase && satisfied) {
            // Mechanic done — let the boundary-reaching hit land a hair PAST the seam instead of
            // exactly on it. BossPhase.select only advances once health drops strictly below a
            // threshold, so a floor pinned exactly at the next threshold leaves the boss asymptoting
            // onto the seam and the phase change never fires. That pinning is precisely what the
            // unsatisfied branch above wants; here it would be a deadlock, so nudge past it.
            floorHealth -= Math.max(0.5, maxHealth * 0.002);
        }
        double allowedDamage = Math.max(0.0, entity.getHealth() - floorHealth);
        return Math.min(rawDamage, allowedDamage);
    }

    /**
     * Freezes attack-selection and chase movement for {@code ticks} — the punishable opening after a
     * break/expose. Public: trial attacks live in {@code bosses.attacks}, a different package from the
     * rest of this framework.
     */
    public void stagger(int ticks) {
        this.stunnedUntilMs = System.currentTimeMillis() + ticks * 50L;
    }

    private boolean isStaggered() {
        return System.currentTimeMillis() < stunnedUntilMs;
    }

    /**
     * Rate-limits the "your hit was fully shielded" cue so a fast weapon's stream of blocked hits
     * gives one clear deflect beat, not a machine-gun of them. Returns true at most every 350ms.
     * Public: the boss-damage listener lives in this package but calls it per hit. Whether a hit is
     * actually blocked (boss armored while weak points stand, or a trial has it invulnerable) is the
     * caller's call — this only throttles the feedback.
     */
    public boolean signalDeflectReady() {
        long now = System.currentTimeMillis();
        if (now - lastDeflectMs < 350L) {
            return false;
        }
        lastDeflectMs = now;
        return true;
    }

    private void startGate(BossPhase phase) {
        var factory = phase.mechanicFactory();
        if (factory == null) {
            // Deliberately ungated phase: fully hittable for the whole band. Reset the multiplier so
            // no armored/exposed value left over from the previous phase's gate leaks into this one.
            damageMultiplier = 1.0;
            return;
        }
        gate = factory.apply(this);
        gate.start();
    }

    private void stopGate() {
        if (gate != null) {
            gate.stop();
            gate = null;
        }
    }

    /** Register a grief-spawned entity (thrown block, display prop) for force-removal on fight end. */
    public void trackEntity(Entity entity) {
        griefEntities.add(entity.getUniqueId());
    }

    /** Count of this fight's still-alive thrown blocks — used to enforce the falling-block cap. */
    public int liveFallingBlockCount() {
        int count = 0;
        for (UUID id : griefEntities) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof FallingBlock && entity.isValid()) {
                count++;
            }
        }
        return count;
    }

    /** Shows a big title/subtitle to every player currently in the arena — spawn, phase, and death cinematics. */
    public void showTitle(Component title, Component subtitle) {
        Title shown = Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(800)));
        for (Player player : arena.playersNear(UI_PRESENCE_BUFFER)) {
            player.showTitle(shown);
        }
    }

    /**
     * Every boss now hits harder the deeper into the fight it gets — cooldowns shrink phase over
     * phase (not just at the very last, "enrage" one), so the pace visibly ramps up instead of
     * staying flat until one binary flip at the end. Floored so attacks never become an unreadable
     * spam-blur; enrage gets an extra kick on top.
     * <p>
     * All three numbers are {@code bosses.<id>.*} config keys (see {@link Boss#configDouble}) rather
     * than hardcoded, so a boss whose late-phase pacing reads as spam rather than an escalating
     * pattern can be retuned without recompiling. Defaults are deliberately softer than the original
     * 0.65/0.30/0.70 — players reported the ramp turning "dodge this" attacks into a blur with no
     * room to react by the final phase.
     */
    private double phaseCooldownScale() {
        List<BossPhase> phases = boss.phases();
        int index = phases.indexOf(currentPhase);
        int lastIndex = phases.size() - 1;
        double progress = lastIndex <= 0 ? 0.0 : (double) Math.max(index, 0) / lastIndex;
        double progressCut = boss.phaseCooldownProgressCut();
        double scale = 1.0 - progress * progressCut;
        if (currentPhase.isEnrage()) {
            scale *= boss.phaseCooldownEnrageCut();
        }
        return Math.max(boss.phaseCooldownFloor(), scale);
    }

    /** Most cooldown an attack may still owe when a phase it belongs to becomes active. */
    private static final long MAX_PHASE_TRANSITION_COOLDOWN_MS = 2500;

    /**
     * Trims how much of an attack's cooldown carries into a phase that just became active. Shared
     * attack instances keep their cooldown timestamp across phases (intentional — it stops a boss
     * opening a new phase with the move it just finished), but a phase reusing an attack from an
     * earlier phase could otherwise walk in owing its full cooldown and never get it off.
     * <p>
     * Only ever makes an attack <em>more</em> ready, never less. The previous version stamped every
     * attack last used more than {@value #MAX_PHASE_TRANSITION_COOLDOWN_MS}ms ago as though it had
     * been used exactly that long ago — which for anything with a real cooldown (8-26s across the
     * roster) meant taking an attack that was fully off cooldown and putting most of it back on. The
     * practical effect was that every phase transition blacked out most of the new phase's pool, so
     * bosses visibly cycled the same two or three moves all fight instead of their whole kit.
     */
    private void capCarryoverCooldowns(BossPhase phase) {
        long now = System.currentTimeMillis();
        double scale = phaseCooldownScale();
        for (BossAttack attack : phase.attacks()) {
            Long lastUsed = lastUsedAtMs.get(attack);
            if (lastUsed == null) {
                continue;
            }
            long cooldownMs = Math.round(attack.cooldownSeconds() * scale * 1000);
            // Timestamp at which this attack would owe exactly the allowed carryover. Anything used
            // before that is already at least as ready and must be left alone.
            long earliestAllowed = now - Math.max(0L, cooldownMs - MAX_PHASE_TRANSITION_COOLDOWN_MS);
            if (lastUsed > earliestAllowed) {
                lastUsedAtMs.put(attack, earliestAllowed);
            }
        }
    }

    /**
     * Fired once, the instant a boss crosses into its enrage phase: permanently grows and speeds
     * up the entity (on top of whatever that phase's own {@code onEnter} cinematic already did),
     * plus its own screen-filling burst — so "final form" reads as a real escalation and not just
     * a health-bar color change.
     */
    private void empowerForEnrage() {
        AttributeInstance scaleAttr = entity.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(scaleAttr.getBaseValue() * ENRAGE_SCALE_MULTIPLIER);
        }
        AttributeInstance speedAttr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * ENRAGE_SPEED_MULTIPLIER);
        }

        Location loc = entity.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 1.2, 0), 6, 0.96, 1.44, 0.96, 0);
        Fx.flash(loc.clone().add(0, 1.2, 0), 3);
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1.2, 0), 360, 2.4, 2.4, 2.4, 0.3);
        Fx.expandingRings(plugin, loc, Particle.SOUL_FIRE_FLAME, Math.min(12.0, arena.radius() * 0.6), 5, 2L);
        Fx.sound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f, 0.6f);
        Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.3f, 0.8f);
        Fx.sound(loc, Sound.ENTITY_WARDEN_ROAR, 1.2f, 0.9f);
        enrageScreenShake();
    }

    /**
     * A short Darkness+Nausea jolt on every player in the arena — the closest a vanilla-only client
     * gets to a real screen vignette/camera-shake without a resource pack, layered on top of
     * {@link #empowerForEnrage()}'s existing particle/sound burst so the enrage transition reads as
     * a genuine "the world just lurched" beat instead of only a health-bar color change.
     */
    private void enrageScreenShake() {
        for (Player player : arena.playersNear(UI_PRESENCE_BUFFER)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20, 0, false, false));
        }
    }

    /**
     * Permanently scales this boss's SCALE and MOVEMENT_SPEED attributes by the given multipliers —
     * the same lever {@link #empowerForEnrage()} pulls once at enrage, exposed here for a boss's own
     * signature "it gets worse the longer this drags on" soft-enrage pressure. Public: those
     * mechanics live in {@code bosses.attacks}, a different package from the rest of this framework.
     */
    public void empower(double scaleMultiplier, double speedMultiplier) {
        AttributeInstance scaleAttr = entity.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(scaleAttr.getBaseValue() * scaleMultiplier);
        }
        AttributeInstance speedAttr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * speedMultiplier);
        }
    }

    /** Layered, screen-filling finale burst — pure particles/sound, never a real explosion. */
    private void deathCinematic(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 1, 0), 9, 0.96, 0.96, 0.96, 0);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 240, 1.92, 1.92, 1.92, 0.4);
        Fx.flash(loc.clone().add(0, 1, 0), 2);
        Fx.expandingRings(plugin, loc, Particle.FLAME, 12, 5, 2L);
        Fx.sound(loc, Sound.ENTITY_WITHER_DEATH, 1.2f, 0.7f);
        Fx.sound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.6f);
        showTitle(boss.defeatTitle(), boss.defeatSubtitle());
    }

    /**
     * Soft leash that mirrors the player wall for the boss itself: a per-player world border can't
     * hold a mob, so if the boss ever ends up past the arena radius (an attack's knockback, ordinary
     * pathfinding around terrain) it's clamped straight back to just inside the wall. It preserves
     * facing so the teleport reads as "shoved back", not a spin, and drops the boss onto the surface
     * at the clamped column rather than keeping its old Y — otherwise clamping the horizontal position
     * into a hillside would bury it inside blocks (looks like it "dug itself a hole").
     */
    private void confineToArena() {
        Location center = arena.center();
        Location loc = entity.getLocation();
        World world = loc.getWorld();
        if (world == null || center.getWorld() == null || !world.equals(center.getWorld())) {
            return;
        }
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        double distSq = dx * dx + dz * dz;
        double radius = arena.radius();
        if (distSq <= radius * radius) {
            return;
        }
        double scale = (radius - 1.0) / Math.sqrt(distSq); // pull just inside the wall, not onto it
        Location clamped = loc.clone();
        clamped.setX(center.getX() + dx * scale);
        clamped.setZ(center.getZ() + dz * scale);
        // Land on the surface of the clamped column so we never teleport the boss into solid ground
        // (which is what read as it "digging a hole") — and it's being yanked back to the floor anyway.
        clamped.setY(world.getHighestBlockYAt(clamped.getBlockX(), clamped.getBlockZ()) + 1);
        entity.teleport(clamped);
    }

    /**
     * The roster-wide "pits hurt, they don't remove you" rule, applied to anyone who has ended up far
     * enough below the arena floor to count as having fallen into one.
     * <p>
     * Bosses across the roster delete the ground on purpose, and the design constraint on all of them
     * is the same: falling in costs a large, survivable-at-full-health bite of the player's bar and
     * puts them back on solid footing. It is deliberately expensive enough that repeated falls kill —
     * the punishment stacks — without any single mistake ending someone's run. Paired with
     * {@link ArenaSafetyListener}, which cancels the fall damage this replaces.
     * <p>
     * Deliberately keyed off depth below the arena rather than off any registry of carved pits: every
     * mechanic that removes floor gets the behaviour for free, including ones that remove it by
     * accident (a stray explosion, a crater under a crater), with no bookkeeping to fall out of sync.
     */
    private void enforcePitFloor() {
        World world = arena.world();
        if (world == null) {
            return;
        }
        double floorY = arena.center().getY() - boss.pitDepth();
        for (Player player : arena.playersInside()) {
            if (!Arena.isCombatant(player) || player.getLocation().getY() > floorY) {
                continue;
            }
            Location safe = player.getLocation().clone();
            safe.setY(world.getHighestBlockYAt(safe.getBlockX(), safe.getBlockZ()) + 1.0);
            // Lift first, damage second: taking the hit while still falling would let the vanilla
            // death message read as a fall, and would stack another tick of pit checks on the way up.
            player.teleport(safe);
            player.setFallDistance(0f);
            player.damage(Math.max(1.0, player.getMaxHealth() * boss.pitDamageFraction()), entity);
            Fx.burst(safe, Particle.SMOKE, 30, 0.6);
            Fx.sound(safe, Sound.ENTITY_GENERIC_BIG_FALL, 1.0f, 0.7f);
        }
    }

    /**
     * The phase this tick should be running, from two independent sources that can each only push the
     * fight <em>forward</em>:
     * <ul>
     *   <li>the health band, exactly as before — the default for a phase that ends when a number drops;</li>
     *   <li>the active mechanic declaring itself finished, which advances one phase regardless of
     *       health. That is what makes "survive this stretch", "push it off the throne" and "drop the
     *       arena out from under it" expressible at all, since none of them is a health threshold.</li>
     * </ul>
     * Taking the later of the two means a boss healing back up (Add-cull's failure burst, say) can
     * never rewind the fight into a phase whose mechanic the group has already beaten.
     */
    private BossPhase selectPhase(double fraction) {
        List<BossPhase> phases = boss.phases();
        int currentIndex = phases.indexOf(currentPhase);
        int byHealth = phases.indexOf(BossPhase.select(phases, fraction));
        int target = Math.max(currentIndex, byHealth);
        if (mechanicReadyToAdvance() && currentIndex + 1 < phases.size()) {
            target = Math.max(target, currentIndex + 1);
        }
        return phases.get(Math.max(0, Math.min(target, phases.size() - 1)));
    }

    /**
     * Fires the first armed event whose milestone the boss has dropped to, if any. Each
     * {@code id@fraction} pair is recorded the instant it starts, so a boss that heals back above a
     * milestone (Add-cull's failure burst, the shield's own absorb heal) can never replay it.
     *
     * @return true if an event took over this tick.
     */
    private boolean tryStartEvent(double fraction) {
        for (BossEvent event : boss.events()) {
            for (double trigger : event.triggerFractions()) {
                if (fraction > trigger) {
                    continue;
                }
                String key = event.id() + "@" + trigger;
                if (!firedEvents.add(key)) {
                    continue;
                }
                activeEvent = event;
                try {
                    event.run(this, () -> activeEvent = null);
                } catch (Exception e) {
                    // A thrown event must never leave the boss frozen with activeEvent stuck set.
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Boss event '" + event.id() + "' threw on start — skipping it.", e);
                    try {
                        event.cleanup(this);
                    } catch (Exception ignored) {
                        // Cleanup of an already-broken event is best effort.
                    }
                    activeEvent = null;
                    continue;
                }
                return activeEvent != null;
            }
        }
        return false;
    }

    void tick() {
        if (ended) {
            return;
        }
        // A genuinely dead boss (isDead) ends immediately — that state doesn't reverse. But
        // isValid() alone (not dead, just "not currently in the world") has been seen to read false
        // for a tick or two on a boss spawned into a just-created realm world, before the entity's
        // chunk finishes settling into the world's tracked/ticking state — real spawns onto an
        // already-existing world never hit this. A single false reading there used to end the fight
        // immediately: BossInstance tore itself down (bar, gate, tick loop registration) while the
        // actual mob entity was left behind, unmanaged, in the world — which is exactly "the boss
        // spawned in at the right size but with no bar and no attacks, behaving like a plain mob"
        // reported after a first-ever realm entry. Debounced so a few ticks of that transient state
        // don't end the fight; a real despawn (killed by another plugin, chunk actually unloaded
        // because everyone left) stays invalid past this window regardless.
        if (entity.isDead()) {
            end(EndReason.DESPAWNED);
            return;
        }
        if (!entity.isValid()) {
            invalidTicks++;
            if (invalidTicks == 1) {
                plugin.getLogger().warning(() -> "Boss '" + boss.id()
                        + "' entity reported invalid on a tick — waiting to see if it's transient before ending the fight.");
            }
            if (invalidTicks >= INVALID_TICKS_BEFORE_DESPAWN) {
                end(EndReason.DESPAWNED);
            }
            return;
        }
        invalidTicks = 0;

        // Re-anchor the arena's combat/UI center to where the boss actually is this tick. Everything
        // downstream (target selection, boss bar viewers, AoE damage/presence checks in attacks) keys
        // off this, so a chased/drifted fight keeps its bar and keeps attacking instead of decaying
        // into a passive, bar-less regular mob the moment it leaves its spawn point.
        // The boss's own leash back inside the radius runs unconditionally — a flier (ghast, phantom,
        // ender dragon) must never be able to wander off, whether or not the per-player visual border
        // (arenaBarrier, purely cosmetic and off by default now that every boss fights inside a walled
        // realm) is turned on for this boss.
        confineToArena();
        if (arenaBarrier != null) {
            arenaBarrier.update();
        }

        arena.updateLiveCenter(entity.getLocation());
        enforcePitFloor();

        double fraction = Math.max(0.0, Math.min(1.0, entity.getHealth() / maxHealth));
        BossPhase newPhase = selectPhase(fraction);
        if (newPhase != currentPhase) {
            boolean enteringEnrage = newPhase.isEnrage() && !currentPhase.isEnrage();
            currentPhase = newPhase;
            exposuresThisPhase = 0;
            floorLockStartMs = 0L;
            floorLockTimeoutLogged = false;
            // Safety net: if a signature/trial attack set the boss forced-invulnerable for its window
            // and the health band was burned through before that window resolved, the flag would leak
            // and leave the boss permanently unhittable in the new phase. A phase change always clears
            // it — worst case the interrupted attack's window ends a beat early, never a stuck boss.
            forcedInvulnerable = false;
            // Same reasoning as the invulnerability flag above: an override belongs to the mechanic that
            // set it, and a phase change is the one moment we can guarantee that mechanic is gone.
            targetOverride = null;
            if (entity.isValid()) {
                entity.setGlowing(false);
            }
            addManager.despawnAll();
            stopGate();
            mechanicBar.clear();
            // Shared attack instances carry their cooldown timestamp across phases (that's
            // intentional — it stops a boss opening a new phase with the same move it just used).
            // But it also means a phase reused from earlier (Cataclysm reusing Frostbound's
            // absoluteZero, say) can walk in already fully on cooldown from that earlier use, and
            // if players burn through the health band fast the boss never gets a single new-phase
            // attack off. Capping the carried-over cooldown keeps every phase transition to at most
            // a short beat of "nothing" instead of the whole cooldown window.
            capCarryoverCooldowns(currentPhase);
            currentPhase.onEnter(this);
            startGate(currentPhase);
            if (enteringEnrage) {
                empowerForEnrage();
            }
        }

        Player overridden = overriddenTarget();
        currentTarget = overridden != null ? overridden : TargetSelector.select(arena, currentTarget);
        barController.refresh(boss.displayName(), currentPhase, fraction, arena.playersNear(UI_PRESENCE_BUFFER));
        BossHologram.update(this, fraction);

        if (currentTarget == null) {
            return;
        }

        // A blocking event owns the fight outright while it runs: no attack selection, no chase, the
        // player's whole attention on the one thing. A non-blocking one runs alongside normal combat,
        // which is what makes a repositioning or side-objective mechanic tense rather than a free chore.
        if (activeEvent != null) {
            if (activeEvent.blocksCombat()) {
                return;
            }
        } else if (tryStartEvent(fraction) && activeEvent != null && activeEvent.blocksCombat()) {
            return;
        }

        // Staggered from a just-broken/exposed vulnerability cycle: the boss holds still and stops
        // attacking for the window — the actual "punish it now" opening the mechanic exists to create.
        if (isStaggered()) {
            return;
        }

        if (!attackInProgress && entity instanceof Mob mob) {
            mob.getPathfinder().moveTo(currentTarget, 1.0);
            mob.lookAt(currentTarget.getEyeLocation());
        }

        if (!attackInProgress) {
            BossAttack attack = AttackSelector.select(currentPhase.attacks(), lastAttack, lastUsedAtMs, phaseCooldownScale());
            if (attack != null) {
                attackInProgress = true;
                AttackContext ctx = new AttackContext(plugin, this, currentTarget);
                try {
                    attack.run(ctx, () -> {
                        attackInProgress = false;
                        lastAttack = attack;
                        lastUsedAtMs.put(attack, System.currentTimeMillis());
                    });
                } catch (Exception e) {
                    // attack.run() itself (before it ever reaches BossAttack#sequence) can throw —
                    // e.g. computing an aim vector off a target snapshot. Without this, that exception
                    // would leave attackInProgress stuck true and freeze the boss for the rest of the
                    // fight instead of just skipping this one botched attack attempt.
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Boss attack '" + attack.name() + "' threw before starting — recovering instead of freezing the boss.", e);
                    attackInProgress = false;
                }
            }
        }
    }

    public void end(EndReason reason) {
        if (ended) {
            return;
        }
        ended = true;

        // manager.forget() MUST run no matter what happens below — it's what allows the boss to be
        // spawned again. Without the try/finally, an exception partway through cleanup (e.g. from
        // touching an entity mid vanilla-death-processing) would silently skip it and leave the boss
        // permanently "live" from the manager's point of view, blocking every future /bossspawn.
        try {
            // First, before anything else can throw: a meter can have players rooted, blinded and
            // bleeding right now, and those holds are the one piece of fight state that lives on the
            // player rather than on the boss. Anything that skipped this would leave someone frozen in
            // an empty arena with nothing left running to release them.
            try {
                meters.detachAll();
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Meter teardown threw for boss '" + boss.id() + "' — players may keep a meter effect briefly.", e);
            }
            for (BukkitTask task : tasks) {
                if (!task.isCancelled()) {
                    task.cancel();
                }
            }
            tasks.clear();
            addManager.despawnAll();
            stopGate();
            if (activeEvent != null) {
                // Mid-event teardown: its props and invulnerability flag must not outlive the fight.
                try {
                    activeEvent.cleanup(this);
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Boss event '" + activeEvent.id() + "' threw during cleanup.", e);
                }
                activeEvent = null;
            }
            for (UUID id : griefEntities) {
                Entity griefEntity = Bukkit.getEntity(id);
                if (griefEntity != null) {
                    griefEntity.remove();
                }
            }
            griefEntities.clear();
            barController.hideAll();
            mechanicBar.clear();
            if (arenaBarrier != null) {
                arenaBarrier.clear();
            }
            if (ambianceHandle != null) {
                ambianceHandle.end();
            }
            BossHologram.stop(this);

            World world = entity.getWorld();
            Location deathLocation = entity.getLocation();
            WorldGuardArenaGuard.stop(boss.id(), world);

            // On DEFEATED, the entity is already mid vanilla death/removal (we're reacting to its own
            // EntityDeathEvent) — forcing another removal here fights that in-progress teardown. Only
            // DESPAWNED/PLUGIN_DISABLE deal with a still-alive entity that actually needs removing.
            if (reason != EndReason.DEFEATED && entity.isValid()) {
                entity.remove();
            }

            if (reason == EndReason.DEFEATED) {
                deathCinematic(deathLocation);
                double rareThreshold = plugin.getConfig().getDouble("discord.rare-drop-threshold", 0.05);
                String bossName = LegacyComponentSerializer.legacySection().serialize(boss.displayName());
                int nearbyPlayers = Math.max(1, Arena.playersNear(deathLocation, arena.radius() + UI_PRESENCE_BUFFER).size());
                DiscordNotifier.kill(plugin, bossName, nearbyPlayers);
                for (LootTable.RolledDrop drop : boss.lootTable().rollWithOdds()) {
                    world.dropItemNaturally(deathLocation, drop.item());
                    if (drop.chance() <= rareThreshold) {
                        DiscordNotifier.rareDrop(plugin, bossName, itemDisplayName(drop.item()), drop.chance());
                    }
                }
            }
        } finally {
            // In the finally block, and after every other teardown step: whatever else went wrong
            // above, the world gets put back. A fight that threw partway through cleanup is exactly
            // the case where leaving a half-melted arena behind would be worst.
            //
            // PLUGIN_DISABLE restores synchronously — no tick will ever run again, so a batched
            // restore would never finish and the damage would become permanent at shutdown.
            try {
                ledger.restore(reason == EndReason.PLUGIN_DISABLE);
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Arena restore failed for boss '" + boss.id() + "' — terrain damage may persist.", e);
            }
            manager.forget(this);
        }
    }

    private static String itemDisplayName(ItemStack item) {
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return LegacyComponentSerializer.legacySection().serialize(meta.displayName());
        }
        return item.getType().toString();
    }
}
