package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.ai.AttackSelector;
import dev.rbm72.weaponsplugin.boss.ai.TargetSelector;
import dev.rbm72.weaponsplugin.boss.integration.BossHologram;
import dev.rbm72.weaponsplugin.boss.integration.DiscordNotifier;
import dev.rbm72.weaponsplugin.boss.integration.WorldGuardArenaGuard;
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

    private final WeaponsPlugin plugin;
    private final BossManager manager;
    private final Boss boss;
    private final LivingEntity entity;
    private final Arena arena;
    private final double maxHealth;

    private final AddManager addManager = new AddManager();
    private final BossBarController barController = new BossBarController();
    private final BossAmbiance.Handle ambianceHandle;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<BossAttack, Long> lastUsedAtMs = new HashMap<>();
    private final Set<UUID> griefEntities = new HashSet<>();

    private BossPhase currentPhase;
    private BossAttack lastAttack;
    private Player currentTarget;
    private boolean attackInProgress;
    private boolean ended;
    private Vulnerability vulnerability;
    private double damageMultiplier = 1.0;
    private long stunnedUntilMs;
    private boolean forcedInvulnerable;
    private int exposuresThisPhase;
    private long floorLockStartMs;

    BossInstance(WeaponsPlugin plugin, BossManager manager, Boss boss, LivingEntity entity, Arena arena, double maxHealth) {
        this.plugin = plugin;
        this.manager = manager;
        this.boss = boss;
        this.entity = entity;
        this.arena = arena;
        this.maxHealth = maxHealth;

        this.ambianceHandle = boss.ambiance().start(this);
        this.currentPhase = BossPhase.select(boss.phases(), 1.0);
        this.currentPhase.onEnter(this);
        startVulnerability(currentPhase);

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

    public boolean isEnraged() {
        return currentPhase.isEnrage();
    }

    void trackTask(BukkitTask task) {
        tasks.add(task);
    }

    /**
     * Current scalar applied to every player hit — 1.0 outside a vulnerability cycle, armored/exposed
     * otherwise, hard-pinned to 0.0 whenever a trial mechanic (gaze/sanctuary/marked-target) has this
     * boss forcibly invulnerable, regardless of whatever the vulnerability cycle is doing underneath.
     */
    double damageMultiplier() {
        return forcedInvulnerable ? 0.0 : damageMultiplier;
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
     * Called the instant a phase's forced mechanic is actually completed — a weak-point set fully
     * broken ({@link Vulnerability}), or a boss's own signature mechanic (guards cleared, real target
     * found, infection cured, whatever it is) resolved successfully. Either kind satisfies the phase
     * floor below; a boss isn't required to clear both a generic weak-point cycle and its unique
     * mechanic in the same phase, just to have genuinely engaged with at least one. Public: signature
     * mechanics live in {@code bosses.attacks}, a different package from the rest of this framework.
     */
    public void recordExposure() {
        exposuresThisPhase++;
    }

    private boolean phaseMechanicSatisfied() {
        return currentPhase.vulnerabilitySpec() == null || exposuresThisPhase > 0;
    }

    /** Grace period before the floor gives up and lets the fight through anyway — a safety valve, not the intended path. */
    private static final long FLOOR_LOCK_TIMEOUT_MS = 45_000L;

    /**
     * True once the current phase's mandatory mechanic has been left unresolved past
     * {@link #FLOOR_LOCK_TIMEOUT_MS} — a broken or unreachable weak-point set (griefed terrain, a
     * disconnected solo player) must not be able to hard-lock the fight forever, so past the grace
     * period the floor stops gating. The clock is reset (elsewhere) whenever the mechanic is actually
     * satisfied or the phase changes.
     */
    private boolean floorLockTimedOut() {
        long now = System.currentTimeMillis();
        if (floorLockStartMs == 0L) {
            floorLockStartMs = now;
            return false;
        }
        return now - floorLockStartMs > FLOOR_LOCK_TIMEOUT_MS;
    }

    /**
     * Stops raw burst from skipping whole phases without ever being able to hard-lock the fight.
     * <ul>
     *   <li>Every non-final phase: a single hit can never carry the boss <em>past</em> the next
     *       phase's boundary. Reaching that boundary advances the phase (which re-locks this on the
     *       new one), so no amount of damage collapses several phases — and their mechanics — in one
     *       blow. This is NOT gated on completing the phase mechanic: the mechanic is what flips the
     *       boss from armored (slow) to exposed (fast) via its damage multiplier, a strong incentive,
     *       not a wall that can freeze progress if a weak-point set is unreachable or an exposure
     *       fails to register. So the fight always moves; it just crawls until the mechanic's done.</li>
     *   <li>The enrage/final phase has no next boundary, so it stays gated on its mechanic (can't be
     *       finished off until exposed at least once) with the {@link #floorLockTimedOut()} safety
     *       valve as the escape hatch — this is the one place a lingering floor is acceptable because
     *       there's no phase beyond it to get stuck before.</li>
     * </ul>
     * Called from {@link BossDamageListener} at MONITOR, after every other multiplier/bonus — the
     * last word on the number, not an earlier, bypassable step.
     */
    double clampToPhaseFloor(double rawDamage) {
        List<BossPhase> phases = boss.phases();
        int index = phases.indexOf(currentPhase);
        boolean lastPhase = index + 1 >= phases.size();

        double floorFraction;
        if (lastPhase) {
            boolean satisfied = phaseMechanicSatisfied();
            if (satisfied) {
                floorLockStartMs = 0L;
            } else if (floorLockTimedOut()) {
                satisfied = true;
            }
            floorFraction = satisfied ? 0.0 : 0.01;
        } else {
            // No-skip clamp: a hit lands the boss in the next phase at worst, never deeper.
            floorFraction = phases.get(index + 1).entryThresholdFraction();
        }

        if (floorFraction <= 0.0) {
            return rawDamage;
        }
        double floorHealth = maxHealth * floorFraction;
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

    private void startVulnerability(BossPhase phase) {
        VulnerabilitySpec spec = phase.vulnerabilitySpec();
        if (spec != null) {
            vulnerability = new Vulnerability(this, spec);
            vulnerability.start();
        } else {
            damageMultiplier = 1.0;
        }
    }

    private void stopVulnerability() {
        if (vulnerability != null) {
            vulnerability.stop();
            vulnerability = null;
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
     * staying flat until one binary flip at the end. Floors out at 30% of listed cooldowns so
     * attacks never become an unreadable spam-blur; enrage gets an extra kick on top.
     */
    private double phaseCooldownScale() {
        List<BossPhase> phases = boss.phases();
        int index = phases.indexOf(currentPhase);
        int lastIndex = phases.size() - 1;
        double progress = lastIndex <= 0 ? 0.0 : (double) Math.max(index, 0) / lastIndex;
        double scale = 1.0 - progress * 0.65;
        if (currentPhase.isEnrage()) {
            scale *= 0.7;
        }
        return Math.max(0.3, scale);
    }

    /** Caps how much of an attack's cooldown can carry over from a previous phase's use of it. */
    private static final long MAX_PHASE_TRANSITION_COOLDOWN_MS = 2500;

    private void capCarryoverCooldowns(BossPhase phase) {
        long now = System.currentTimeMillis();
        for (BossAttack attack : phase.attacks()) {
            Long lastUsed = lastUsedAtMs.get(attack);
            if (lastUsed != null && now - lastUsed > MAX_PHASE_TRANSITION_COOLDOWN_MS) {
                lastUsedAtMs.put(attack, now - MAX_PHASE_TRANSITION_COOLDOWN_MS);
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

    void tick() {
        if (ended) {
            return;
        }
        if (!entity.isValid() || entity.isDead()) {
            end(EndReason.DESPAWNED);
            return;
        }

        double fraction = Math.max(0.0, Math.min(1.0, entity.getHealth() / maxHealth));
        BossPhase newPhase = BossPhase.select(boss.phases(), fraction);
        if (newPhase != currentPhase) {
            boolean enteringEnrage = newPhase.isEnrage() && !currentPhase.isEnrage();
            currentPhase = newPhase;
            exposuresThisPhase = 0;
            floorLockStartMs = 0L;
            // Safety net: if a signature/trial attack set the boss forced-invulnerable for its window
            // and the health band was burned through before that window resolved, the flag would leak
            // and leave the boss permanently unhittable in the new phase. A phase change always clears
            // it — worst case the interrupted attack's window ends a beat early, never a stuck boss.
            forcedInvulnerable = false;
            if (entity.isValid()) {
                entity.setGlowing(false);
            }
            addManager.despawnAll();
            stopVulnerability();
            // Shared attack instances carry their cooldown timestamp across phases (that's
            // intentional — it stops a boss opening a new phase with the same move it just used).
            // But it also means a phase reused from earlier (Cataclysm reusing Frostbound's
            // absoluteZero, say) can walk in already fully on cooldown from that earlier use, and
            // if players burn through the health band fast the boss never gets a single new-phase
            // attack off. Capping the carried-over cooldown keeps every phase transition to at most
            // a short beat of "nothing" instead of the whole cooldown window.
            capCarryoverCooldowns(currentPhase);
            currentPhase.onEnter(this);
            startVulnerability(currentPhase);
            if (enteringEnrage) {
                empowerForEnrage();
            }
        }

        currentTarget = TargetSelector.select(arena, currentTarget);
        barController.refresh(boss.displayName(), currentPhase, fraction, arena.playersNear(UI_PRESENCE_BUFFER));
        BossHologram.update(this, fraction);

        if (currentTarget == null) {
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
            for (BukkitTask task : tasks) {
                if (!task.isCancelled()) {
                    task.cancel();
                }
            }
            tasks.clear();
            addManager.despawnAll();
            stopVulnerability();
            for (UUID id : griefEntities) {
                Entity griefEntity = Bukkit.getEntity(id);
                if (griefEntity != null) {
                    griefEntity.remove();
                }
            }
            griefEntities.clear();
            barController.hideAll();
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
