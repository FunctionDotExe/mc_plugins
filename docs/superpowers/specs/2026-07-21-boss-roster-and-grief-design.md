# Boss Roster Expansion + Griefable Engine

## Context

The boss framework and its reference boss (**The Fallen King**) are built and working
(`plugin/src/main/java/dev/rbm72/weaponsplugin/boss`, spec
`docs/superpowers/specs/2026-07-21-boss-framework-design.md`). The original user request named
**9 more bosses plus an 8-phase capstone** that the framework spec deliberately deferred — each
"its own spec + plan on this engine." This spec delivers that roster.

Two things change relative to the framework spec:

1. **Grief is now allowed.** The framework was built strictly grief-safe (no block breaking, no
   real explosions, biome-only reversible arena changes). The user has decided bosses *should* be
   destructive — "it's a boss, it's supposed to be dangerous." This spec adds griefable combat
   primitives and lifts the no-grief constraint, gated per boss by config.
2. **The full roster is designed** — 9 new bosses at Fallen-King parity (4 phases, ~12–14 attacks
   each) plus the 8-phase capstone, each dropping a new boss-exclusive weapon.

This is a large, multi-session build. This document is the master design; the implementation plan
turns each boss into an independent task on the shared engine.

### Decisions carried in from brainstorming

- **Roster:** Claude-proposed, user-approved (below).
- **Depth:** full Fallen-King parity per boss — 4 phases, ~12–14 attacks, per-phase `onEnter`
  cinematics.
- **Grief policy:** per-boss config flag `bosses.<id>.grief` (default `true`). Real block damage is
  **permanent** — no snapshot/restore. **Unbounded** — attacks grief wherever they reach, including
  outside the arena radius. The only limits kept are *server-stability* caps (explosion power
  ceiling, falling-block count cap, particle-per-tick cap), never grief-scope limits.
- **VFX:** deliberately over-the-top. Every attack layers multiple particle effects, colored
  bursts, helixes, expanding rings, spinning Display icons, stacked sounds, and screen-shake nudges
  — pushed right up to the configurable particle-per-tick cap.
- **Loot:** each boss drops a new boss-exclusive weapon (Legendary) built on the existing 4-slot
  weapon framework; the capstone drops a Mythic best-in-slot weapon. Plus weighted themed
  armor/materials and a sub-1% cosmetic trophy, following the Fallen King loot pattern.
- **Flight:** included. Dragon Elder is the one aerial boss, built on a `PHANTOM` base with
  hover + periodic ground-dive logic. Accepted as the roster's highest-risk boss.

## Safety constraints (revised)

The framework's non-negotiables **still hold except grief**:

- **No entity/task leaks.** Every entity a boss spawns — boss mob, adds, Display entities, **and
  now `FallingBlock`s thrown by grief attacks** — is tracked in `BossInstance` and force-removed on
  fight end. Every `BukkitTask` is tracked and cancelled the same way. `BossManager.shutdownAll()`
  runs on `onDisable()`.
- **No lag bomb.** Per-tick particle counts, add counts, thrown-block counts, and explosion power
  are all config-capped. Player-count HP scaling stays clamped (1.0×–2.5×).
- **Main-thread only.** All Bukkit API calls from the main thread (scheduler tasks); no async
  world/entity access.
- **Locked-down surface.** Commands stay permission-gated (`weaponsplugin.boss.*`, default `op`)
  and validate the boss id against the registry.

**What changes:** block breaking, block placing, thrown blocks, and real explosions
(`world.createExplosion(loc, power, true, true)` — fire + block damage) are now permitted, gated by
the per-boss `grief` flag. When `grief=false`, every destructive primitive falls back to a
pure particle+code-damage equivalent, so a boss is fully playable grief-off.

## 1. Griefable engine additions

New package `boss/grief/` with one stateless helper class, `Grief`, holding the shared destructive
primitives. Every method takes the `AttackContext` (or `BossInstance`) so it can read the boss's
`grief` flag and the stability caps, and every method no-ops its destructive path into a cosmetic
fallback when `grief=false`.

```java
public final class Grief {
    // Real explosion (fire + block damage) when grief on; particle burst + code damage when off.
    static void explosion(AttackContext ctx, Location loc, float power);

    // Spawn a FallingBlock of `material`, launch it at `target`, code-damage on impact.
    // Registers the entity with BossInstance for cleanup. No-op-to-cosmetic when grief off
    // (a Display-entity or particle projectile is used instead so the attack still telegraphs+hits).
    static void throwBlock(AttackContext ctx, Location from, LivingEntity target, Material material);

    // Break blocks in a radius (slams, fissures, void pits). Grief off -> crack particles only.
    static void breakCrater(AttackContext ctx, Location center, double radius);

    // Place blocks (ice pillars, bone spikes, corruption spread). Grief off -> Display-entity fakes
    // that auto-remove; on -> real blocks placed (permanent).
    static void raiseColumns(AttackContext ctx, Location base, Material material, int height, int count);

    // Convert a growing region of ground blocks to `to` (Plague corruption). Grief-gated.
    static void spread(AttackContext ctx, Location center, Material to, double radius);
}
```

Movement primitives (grief-independent — they never touch blocks) live on the existing
`telegraph/Telegraph` or a small `boss/ai` movement helper:

- `dash(LivingEntity self, Location toward, double speed)` — velocity lunge.
- `leap(LivingEntity self, Location toward, double up, double forward)` — arcing jump.
- `launchTarget(LivingEntity target, double up)` — knock a player airborne (Storm/Void kits).

### Stability caps (config, read by `Grief`)

- `bosses.<id>.grief` (boolean, default `true`).
- `bosses.<id>.max-explosion-power` (double, default e.g. `4.0`) — ceiling passed to any
  `explosion()` call; individual attacks request a power but it's clamped to this.
- `bosses.<id>.max-falling-blocks` (int) — hard cap on live thrown blocks per instance; excess
  throw requests are dropped, not queued.
- `bosses.<id>.max-particles-per-tick` (int) — VFX ride up to but never past this.

### `BossInstance` change

Add a tracked entity set for grief-spawned entities (falling blocks, Display fakes):
`trackEntity(Entity)` registering a UUID, force-removed in the existing `end(...)` cleanup
alongside adds and tasks. `AddManager` stays adds-only; grief entities are a separate set because
they aren't combatant adds and shouldn't count against add caps.

### `Boss` change

Add `protected final boolean configBoolean(String key, boolean def)` mirroring the existing
`configDouble`/`configInt`, and a convenience `public boolean griefEnabled()` returning
`configBoolean("grief", true)`. `AttackContext`/`BossInstance` expose it to attack classes.

### Fallen King retrofit

The reference boss is updated to use the new primitives so it matches the new "dangerous" bar and
proves the grief engine:

- **Shockwave Slam** and **Jump Slam** → real `breakCrater` on impact.
- **Dark Explosion** → real `Grief.explosion`.
- **Dash Slash** → real `dash` movement.
- Add one **new block-throw attack** ("Siege Hurl" — rips up ground blocks and throws them at the
  target) to phase 1/2.
- Add `bosses.fallen_king.grief: true` and the stability-cap keys to config.

The Fallen King's own-explosion damage is cancelled in `BossDamageListener` (boss entities ignore
explosion-type damage) so the king doesn't suicide on its own craters.

## 2. Roster (9 bosses)

Every boss follows the Fallen King phase spine: **P1 100–75%, P2 75–40% (+`onEnter` cosmetic),
P3 40–15% (dark/ultimate theme), P4 enrage <15% (reuse pool + one phase-4-only signature)**, with
per-player HP scaling clamped 1.0×–2.5×. "Sig grief" = the boss's defining destructive move.

| # | Boss | id | Entity base | Arena biome | Sig grief | Weapon drop |
|---|------|-----|-------------|-------------|-----------|-------------|
| 2 | Frost Queen | `frost_queen` | `STRAY` | `FROZEN_PEAKS` | Glacier Spikes (raise ice columns) | Glacial Scepter |
| 3 | Storm Tyrant | `storm_tyrant` | `WITHER_SKELETON` | thunderstorm (weather set) | Thunderstrike (real lightning, fire + chain) | Tempest Maul |
| 4 | Inferno Warlord | `inferno_warlord` | `WITHER_SKELETON` | `BASALT_DELTAS` | Meteor (flaming falling block, fire + crater) | Cinder Cleaver |
| 5 | Plague Warden | `plague_warden` | `HUSK` | `SWAMP` | Corruption Spread (convert ground blocks) | Rotscourge |
| 6 | Void Sovereign | `void_sovereign` | `ENDERMAN` | `THE_END` | Void Rift (break blocks straight down, pits) | Nullblade |
| 7 | Solar Colossus | `solar_colossus` | `IRON_GOLEM` | `DESERT` | Seismic Slam (huge crater) | Dawnbreaker |
| 8 | Tide Leviathan | `tide_leviathan` | `DROWNED` | `DEEP_OCEAN` | Tidal Surge (flood arena, throw prismarine) | Maelstrom Trident |
| 9 | Dragon Elder | `dragon_elder` | `PHANTOM` (aerial) | mountain (weather/biome as-is) | Dive Bomb (swoop → crater) | Wyrmscale Bow |
| 10 | Necro Overlord | `necro_overlord` | `WITHER_SKELETON` | `DARK_FOREST` | Bone Spikes (raise bone columns) | Soulharvester |

### Per-boss kits (identity + representative attacks)

The plan itemizes each to the full ~12–14 attack count; these fix each boss's identity and its
distinctive attacks. All attacks: `telegraph → execute → recovery`, `telegraphTicks >= 10`, built on
`Telegraph` + `Fx` + `Grief`, with maximal layered VFX.

- **Frost Queen** — Frost Nova (AoE slow), Ice Lance volley (projectiles), Blizzard (arena storm +
  slowness), Frozen Prison (encase target in ice briefly), Avalanche (throw packed-ice blocks),
  **Glacier Spikes** (raise ice columns under players). Enrage: **Absolute Zero** (arena-wide freeze
  burst).
- **Storm Tyrant** — Chain Lightning, Gale Push (launch players airborne), Tornado (pull + toss
  blocks), Ball Lightning (slow homing orb), Static Field. **Thunderstrike** (real lightning).
  Enrage: **Stormcall** (rolling lightning barrage across arena).
- **Inferno Warlord** — Flame Breath cone, Fire Trail (ignite ground line), Magma Throw, Eruption
  (crater + upward launch), Cinder Nova. **Meteor Rain**. Enrage: **Firestorm** (arena-wide meteor
  barrage).
- **Plague Warden** — Poison Cloud, Summon Undead (adds via `AddManager`), Plague Bolt, Miasma
  (lingering DoT zones), Rotting Grasp (pull). **Corruption Spread**. Enrage: **Pandemic**
  (arena-wide poison + healing-reduction).
- **Void Sovereign** — Blink Strike (teleport combo), Gravity Flip (levitate + toss blocks), Arcane
  Missiles, Singularity (pull-in), Banish (teleport target away). **Void Rift** (pits). Enrage:
  **Collapse** (arena-wide rifts + pull).
- **Solar Colossus** — Solar Beam (line), Meteor (single heavy), Radiant Nova, Fist Throw (launch),
  Sun Flare (blind). **Seismic Slam**. Enrage: **Supernova** (massive explosion + arena crater).
- **Tide Leviathan** — Water Jet (line), Whirlpool (pull), Bubble Trap, Tsunami (block wall throw),
  Ice Shard (from frozen water). **Tidal Surge** (flood). Enrage: **Maelstrom** (arena whirlpool +
  drowning pressure).
- **Dragon Elder** — Fireball Barrage (aerial), Wing Gust (knockback), Tail Sweep (ground window),
  Grab-and-drop (lift a player, drop from height), Firestorm Breath. **Dive Bomb**. Enrage:
  **Cataclysm** (strafing fire runs across arena). Flight: hovers above the arena, periodically
  dives to ground and stays landed for a melee-vulnerable window, then relifts.
- **Necro Overlord** — Raise Undead (large add wave), Soul Drain (heal from damage dealt), Death
  Bolt, Grave Grasp (root), Wither Cloud. **Bone Spikes**. Enrage: **Army of the Dead** (mass
  summon + relentless pressure).

## 3. The Worldender (8-phase capstone)

- **id:** `worldender`. **Entity base:** `WARDEN`. **Weapon drop:** **Apotheosis** (Mythic,
  best-in-slot).
- **Arena warps per phase:** biome + ambiance shift on each `onEnter` to match the channeled boss
  (reusing `BossAmbiance` and, where grief is on, leaving the previous phase's destruction in place).
- **8 phases by HP** (each `onEnter` = a full arena-warp cinematic):

| Phase | HP band | Theme | Kit |
|---|---|---|---|
| 1 Awakening | 100–87% | own / void | Warden sonic boom, void slam, dash |
| 2 Frostbound | 87–75% | Frost Queen | Blizzard, Glacier Spikes, Absolute Zero |
| 3 Stormforged | 75–62% | Storm Tyrant | Thunderstrike, Tornado, Stormcall |
| 4 Infernal | 62–50% | Inferno Warlord | Meteor Rain, Fire Trail, Firestorm |
| 5 Plaguebound | 50–37% | Plague + Necro | Undead army, Corruption Spread, Soul Drain |
| 6 Voidtouched | 37–25% | Void Sovereign | Void Rifts, Singularity, Blink Strike |
| 7 Cataclysm | 25–12% | all at once | meteors + lightning + ice cycling; arena warps rapidly |
| 8 The Unmaking | <12% enrage | own final form | arena-wide destruction, all signatures on short cooldown, relentless |

Channeled-phase attacks **reuse the existing attack classes** from the corresponding boss (the
attack classes take their tunables from `AttackContext`, so the same class works under a different
boss id). Phases 1, 7, and 8 add Worldender-only attacks.

**Gating deferred:** `/bossspawn worldender` works directly. Requiring the 9 bosses be defeated
first needs a persistent-progression system that doesn't exist yet — deferred, like the framework
spec deferred achievements.

## 4. New weapons

Ten new weapons on the existing 4-slot ability framework (ability1/2/3 + ultimate + passive),
registered in `WeaponRegistry` alongside the current set. Full ability kits are designed per weapon
in the plan; each is themed to its boss.

| Weapon | Boss | Rarity | Base material (indicative) |
|---|---|---|---|
| Glacial Scepter | Frost Queen | Legendary | stick/blaze-rod |
| Tempest Maul | Storm Tyrant | Legendary | mace/axe |
| Cinder Cleaver | Inferno Warlord | Legendary | netherite axe |
| Rotscourge | Plague Warden | Legendary | netherite hoe |
| Nullblade | Void Sovereign | Legendary | netherite sword |
| Dawnbreaker | Solar Colossus | Legendary | netherite sword |
| Maelstrom Trident | Tide Leviathan | Legendary | trident |
| Wyrmscale Bow | Dragon Elder | Legendary | bow/crossbow |
| Soulharvester | Necro Overlord | Legendary | netherite hoe |
| **Apotheosis** | The Worldender | **Mythic** | netherite sword |

Loot per boss: guaranteed weapon + weighted themed armor/materials + sub-1% cosmetic trophy
(player-head), matching `FallenKing`'s `LootTable` usage.

## 5. Wiring, config, verification

- **Registration:** `WeaponsPlugin.onEnable()` registers the 10 new bosses (9 + capstone) into
  `BossManager` and the 10 new weapons into `WeaponRegistry`.
- **Commands:** `/bossspawn` and `/bossdespawn` are registry-driven; new bosses auto-appear in
  tab-complete once registered. Permissions already declared in `plugin.yml`.
- **Config:** one `bosses.<id>.*` block per boss following the existing convention — `max-health`,
  `arena-radius`, per-attack tunables (damage/radius/duration/counts), `grief` (default true),
  `max-explosion-power`, `max-falling-blocks`, `max-particles-per-tick`, loot chances.
- **Cleanup:** grief-spawned entities tracked via the new `BossInstance.trackEntity`; existing
  `end(...)` + `shutdownAll()` teardown covers them with no new leak path.
- **Verification:** `./gradlew compileJava` plus manual `/bossspawn <id>` per boss. No test
  framework exists in this repo (matches the established convention; automated tests remain out of
  scope).

## Out of scope

- Persistent progression / capstone gating / achievements / titles / pets.
- Prebuilt or plugin-authored arena structures (arenas still "adapt in place").
- A resource pack (the `BossAudio` namespaced-key hook remains, unused).
- Automated tests.
- Reworking the existing 22 weapons or the Fallen King's phase/threshold structure (only the
  grief retrofit above touches it).

## Build order (for the plan)

Grief engine first (blocks everything), then bosses independently, capstone last (reuses their
attacks):

1. **Grief engine + Fallen King retrofit** — `Grief` helper, movement primitives, `BossInstance`
   entity tracking, `Boss.griefEnabled`, config caps, retrofit + verify on the known-good boss.
2. **Bosses 2–10** — one task each (boss class + ~12–14 attacks + new weapon + config + register).
   Independent; order by ascending risk (grounded melee bosses before Dragon Elder's flight).
3. **The Worldender** — 8-phase capstone reusing the finished bosses' attack classes + Apotheosis.



