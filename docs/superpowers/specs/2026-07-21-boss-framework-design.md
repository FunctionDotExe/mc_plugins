# Boss Framework + The Fallen King (reference boss)

## Context

`WeaponsPlugin` (Paper plugin, `plugin/src/main/java/dev/rbm72/weaponsplugin`) currently ships 22
weapons on a 4-slot ability framework (`items/Weapon.java`, `ability/CooldownManager.java`,
`listeners/WeaponInteractListener.java`). This spec adds a parallel **boss framework** in a new
`boss` package, plus one fully-implemented reference boss (**The Fallen King**) that proves the
framework end to end.

The full user request described 9 more bosses plus an 8-phase capstone. That is out of scope for
this spec. Each later boss is its own spec + plan, built on the engine delivered here. Building
10 bosses' worth of design into one document is unreviewable and unbuildable — this spec
deliberately covers only the reusable engine and one reference implementation.

### Decisions carried in from brainstorming

- **Audio/visual layer:** vanilla-only sounds and particles now, but every sound call goes through
  a namespaced-key indirection (`BossAudio.play(key, fallbackSound, ...)`) so a future resource
  pack can override `key` without touching boss code. No custom models/animations — Display
  entities are the vanilla substitute for "unique animations" (see Arena/Ambiance below).
- **Arena:** no prebuilt arena worlds, no world-editing. A boss claims a center + radius wherever
  it's spawned ("adapt in place"). Arena mechanics are particle/effect and temporary-state driven
  and never leave a permanent mark.
- **Ambiance ("give it its own biome"):** an optional, fully reversible layer — looped ambient
  particles/sound themed to the boss, plus an optional biome swap over the arena's block region
  that snapshots original biomes and restores them when the fight ends (win, lose, disconnect, or
  plugin disable). Config-toggleable per boss; The Fallen King ships with it on.
- **Reference boss:** The Fallen King — melee/summon/dark-magic kit, no flight or heavy projectile
  aiming, so it stresses phases/telegraphs/adds/enrage without adding aerial-AI risk to an
  unproven engine.

## Safety constraints (apply to every piece of this framework, non-negotiable)

- **No grief:** all damage is code-driven via `LivingEntity.damage(...)`. No real fire, no TNT, no
  block breaking/placing as a side effect of combat. Any "explosion" visual uses
  `world.createExplosion(loc, power, false, false)` (no fire, no block damage) or pure
  particle+damage — never a griefing explosion. Arena ambiance block changes (biome only) are
  snapshotted and restored.
- **No entity/task leaks:** every entity the boss spawns (boss mob, adds, Display entities) is
  tracked in `BossInstance` and force-removed on fight end. Every `BukkitTask` is tracked and
  cancelled the same way. `BossManager.shutdownAll()` runs on `onDisable()`.
- **No lag bomb:** per-tick particle counts and add counts are config-capped; arena player-count
  scaling is clamped to a configured min/max multiplier, never unbounded.
- **Main-thread only:** all Bukkit API calls happen from the main thread (scheduler tasks), no
  async world/entity access.
- **Locked down surface:** new commands are permission-gated (default `op`) and validate their
  single argument (boss id) against the registry before acting; unknown ids are rejected with a
  message, never thrown as an uncaught exception.

## 1. Package layout

New package: `dev.rbm72.weaponsplugin.boss`, with sub-packages mirroring the existing
`items`/`ability`/`listeners` split:

```
boss/
  Boss.java                 (abstract, stateless boss definition)
  BossPhase.java            (one phase: threshold, attack pool, entry hooks)
  BossAttack.java            (abstract: telegraph -> execute -> recovery)
  BossInstance.java          (runtime state of one live fight)
  BossManager.java           (registry + live-instance tracker + tick loop)
  BossBarController.java     (custom boss bar per instance)
  BossAudio.java             (namespaced sound play w/ vanilla fallback)
  BossAmbiance.java          (ambient particles/sound + optional biome snapshot/restore)
  Arena.java                 (center/radius/world, player scan, leash)
  AddManager.java            (tracks/despawns summoned adds per instance)
  LootTable.java             (weighted drop resolution)
  ai/
    AttackSelector.java      (weighted-random, no-repeat, cooldown-aware, phase-aware)
    TargetSelector.java      (nearest / lowest-HP / recent-damager, skips invalid targets)
  telegraph/
    Telegraph.java           (shared wind-up indicator shapes, built on Fx)
  bosses/
    FallenKing.java           (reference boss definition)
    attacks/
      (one class per Fallen King attack, ~14 files)
  commands/
    BossSpawnCommand.java
    BossDespawnCommand.java
```

## 2. Core types

### `Boss` (abstract, stateless — like `Weapon`)

```java
public abstract class Boss {
    public abstract String id();
    public abstract Component displayName();
    public abstract EntityType baseEntityType();
    public abstract double maxHealth();
    public abstract List<BossPhase> phases();       // ordered, highest threshold first
    public abstract LootTable lootTable();
    public BossAmbiance ambiance() { return BossAmbiance.NONE; }
    public double arenaRadius() { return 20.0; }
}
```

Config numbers (HP, radius, per-attack tunables) are read via the same
`plugin.getConfig().getDouble("bosses." + id() + "." + key, default)` convention already used by
`Weapon`.

### `BossPhase`

```java
public final class BossPhase {
    String name();
    double healthThresholdFraction();  // phase active while HP% >= this (last phase = 0.0)
    List<BossAttack> attacks();
    boolean isEnrage();
    void onEnter(BossInstance instance);  // phase-transition cinematic: sound, particles, boss bar flash, optional speed/damage modifier
}
```

### `BossAttack` (abstract — enforces "no instant attacks")

```java
public abstract class BossAttack {
    public abstract String name();
    public abstract double cooldownSeconds();
    public abstract int telegraphTicks();     // wind-up duration; minimum enforced >= 10 ticks (0.5s)
    public abstract void telegraph(AttackContext ctx);  // indicator + sound, runs for telegraphTicks
    public abstract void execute(AttackContext ctx);    // the actual hit, runs once after telegraph
    public void recovery(AttackContext ctx) {}          // optional post-attack cooldown window (boss vulnerable/committed)
}
```

`BossInstance`'s attack runner sequences telegraph → execute → recovery as chained scheduler
delays; a boss is never mid-attack and starting a new one simultaneously.

### `AttackContext`

Bundle passed to every attack: `(BossInstance instance, LivingEntity target, Arena arena,
WeaponsPlugin plugin)`. No static/global state read by attack classes.

### `BossInstance`

Owns: the spawned boss `LivingEntity`, current `BossPhase`, current target, `Arena`, `AddManager`,
`BossBarController`, `BossAmbiance` runtime handle, the set of tracked `BukkitTask`s and spawned
entity UUIDs, and `enrage` flag. Drives:
- a repeating tick task (every 10 ticks) that: re-evaluates target via `TargetSelector`, checks
  phase transition against current HP%, and — when not already mid-attack — asks
  `AttackSelector` for the next attack and starts its telegraph→execute→recovery chain.
- `end(EndReason)` (DEFEATED / DESPAWNED / PLUGIN_DISABLE) which: cancels all tracked tasks, removes
  all tracked/spawned entities, hides the boss bar, restores ambiance, and — only on DEFEATED —
  rolls the loot table and drops items at the boss's death location.

### `BossManager`

```java
public final class BossManager {
    void register(Boss boss);
    Optional<BossInstance> spawn(String bossId, Location at);
    Collection<BossInstance> live();
    void despawn(String bossId);   // ends the first live instance of that id, if any
    void shutdownAll();            // called from WeaponsPlugin#onDisable
}
```

### `Arena`

Immutable center `Location` + `radius` + owning `World`, computed once at spawn from the boss's
`arenaRadius()`. Provides `playersInside()` (used for player-count scaling and "who can be
targeted"), and a leash check the tick loop uses to stop the boss wandering past the radius
(teleport-back-to-arena nudge, not a hard wall).

### `BossAmbiance`

```java
public final class BossAmbiance {
    public static final BossAmbiance NONE = ...;
    // ambient loop: particle shape + vanilla sound key, ticked every N seconds while the fight is live
    // optional biome swap: snapshot every column's Biome in the arena's bounding box on start,
    // apply the themed Biome, restore the exact snapshot on end (always, even on abnormal end)
}
```

Biome swap only ever touches `Biome`, never blocks — so "restore" is a single pass writing back
the snapshot, no block-state to get wrong.

### `BossAudio`

```java
public final class BossAudio {
    public static void play(Location loc, String key, Sound vanillaFallback, float volume, float pitch);
}
```

`key` (e.g. `"boss.fallen_king.phase2"`) is stored in a small `Map<String, Sound>` (defaults to
`vanillaFallback` for every key today); swapping in a resource pack later means adding real custom
sound registrations without touching any boss/attack code.

### `ai/AttackSelector`

Weighted-random choice from the current phase's attack pool, excluding: the immediately-previous
attack (no repeats), anything still on cooldown. If every attack is on cooldown, waits one tick and
re-checks rather than forcing a repeat. Weight and phase membership are static per-boss data
(`BossPhase.attacks()` order = base weights, heavier attacks listed first) — no separate weight
table needed for the reference boss.

### `ai/TargetSelector`

Default: nearest living, non-spectator player inside the `Arena`. If more than one player is
present, prefers the one with lowest current health fraction (approximates "target healers first"
without an actual healer-role concept, which the current plugin has no notion of). Re-evaluated
every tick loop pass so the boss switches off a target that dies, disconnects, or leaves the arena
radius.

### `telegraph/Telegraph`

Shared wind-up shapes built on the existing `Fx` helpers (no new particle plumbing needed):
`groundRing(loc, radius, ticks)`, `dangerZone(loc, radius, ticks)` (pulsing red dust matching the
existing `Fx.bloodSpray` palette), `line(from, to, ticks)`, `cone(origin, direction, angle, range,
ticks)`, `targetMarker(entity, ticks)`. Each runs for the attack's `telegraphTicks` and always ends
right before `execute()` fires, so players see the full wind-up.

### `AddManager`

Per-instance `Set<UUID>` of summoned adds. `spawn(EntityType, Location, Consumer<LivingEntity>)`
registers the result for tracking; `despawnAll()` (called on phase transitions where the spec says
adds clear, and always on fight end) removes every tracked add still alive.

### `LootTable`

```java
public final class LootTable {
    // guaranteed entries always drop; weighted entries roll independently against their chance
    LootTable guaranteed(Supplier<ItemStack> item);
    LootTable weighted(double chance, Supplier<ItemStack> item);  // chance in [0,1]
    List<ItemStack> roll();
}
```

Rolled items are dropped as normal item drops at the boss's death location — no new inventory UI
needed.

## 3. Commands

- `/bossspawn <id>` — spawns at the sender's location. Permission `weaponsplugin.boss.spawn`
  (default `op`). Unknown id → red error message, no exception.
- `/bossdespawn <id>` — ends the first live instance of that id (counts as `DESPAWNED`, no loot).
  Same permission.

Both added to `plugin.yml` alongside the existing three commands/permissions.

## 4. The Fallen King (reference boss)

**Entity base:** `WitherSkeleton` (armored, sword-wielding silhouette already close to "cursed
king"), equipped with a netherite sword item and full netherite armor (visual only — no vanilla
attribute reliance; all damage numbers come from our attacks). Custom name via `displayName()`,
`setCustomNameVisible(true)`.

**Max HP:** config-driven, default 300 (scaled per player count by `Arena.playersInside()` at
phase-transition checks — clamped 1.0x–2.5x).

**Phase thresholds** (reconciled from the spec's mixed 75/40/10 language and the general "enrage
under 15%" rule): **Phase 1: 100–75%, Phase 2: 75–40%, Phase 3: 40–15%, Phase 4 (enrage): <15%.**

| Phase | Attacks |
|---|---|
| 1 | Heavy Swing, Triple Combo, Dash Slash, Shockwave Slam, Summon Royal Guards |
| 2 | (armor-shatter `onEnter` cosmetic) Spinning Slash, Sword Rain (flying projectiles), Ground Fissure, Jump Slam |
| 3 | (dark-magic `onEnter`) Shadow Clones, Black Sword Rain, Dark Explosion, Teleport Strike |
| 4 (enrage) | All previous attacks, reduced cooldowns + reduced telegraph time (still >= 10-tick floor), boss speed attribute boosted, arena-wide Enrage Slash unique to this phase |

Each attack is its own `BossAttack` file under `bosses/attacks/`, telegraph + execute + recovery,
built on `Telegraph` + `Fx`. Adds spawned by Summon Royal Guards / Shadow Clones go through
`AddManager` and are cleared on every phase transition and fight end.

**Ambiance:** ruined-throne-room flavor — looped low ambient particle drift (`Fx`-based) + vanilla
sound loop through `BossAudio`, plus the optional biome snapshot/restore swapping the arena's
biome to a "cursed" one (e.g. `DARK_FOREST` or similar somber vanilla biome) for the fight's
duration, restored exactly on end.

**Loot (`King's Judgment`):** one new `Weapon` file, `KingsJudgment.java`, built on the existing
4-slot weapon framework (registered in `WeaponRegistry` like any other weapon) — Legendary
netherite sword, thematically a heavy combo-melee weapon. Guaranteed drop. Weighted drops: a named
netherite armor set (common-ish) and crafting materials; <1% chance cosmetic ("Fallen Crown" —
a player-head trophy item, no functional effect). Titles/pets/achievements are explicitly deferred
— no plugin-wide title/achievement system exists yet, and building one is out of scope for proving
the boss engine.

## 5. Config

`bosses.fallen_king.*` block in `config.yml` for every tunable constructor value (HP, per-attack
damage/radius/duration, phase thresholds if made configurable, loot chances), following the exact
`weapons.<id>.*` convention already in the file.

## 6. Wiring

`WeaponsPlugin.onEnable()` constructs a `BossManager`, registers `FallenKing`, registers
`BossSpawnCommand`/`BossDespawnCommand`, and registers `KingsJudgment` into the existing
`WeaponRegistry` alongside the other 22 weapons. `onDisable()` calls `bossManager.shutdownAll()`.

## Out of scope

- Bosses 2–10 and the capstone (each future spec + plan on this engine).
- Any prebuilt/plugin-authored arena builds.
- A resource pack (hooks only).
- Titles/pets/achievements systems.
- Automated tests (no test framework in this repo; verification is `./gradlew compileJava` +
  manual `/bossspawn fallen_king`).
