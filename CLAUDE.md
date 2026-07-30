# CLAUDE.md

Paper plugin (`dev.rbm72.weaponsplugin`): rarity-tiered weapons/armor/shields/accessories plus a
17-boss roster on a shared boss engine. The boss engine is the part with non-negotiable invariants —
read section "Invariants" before touching anything under `plugin/src/main/java/dev/rbm72/weaponsplugin/boss/`.

## Build & verify

```bash
cd plugin && ./gradlew compileJava    # fast correctness check — do this after every change
pnpm run dev                          # from repo root: build + deploy + restart local Paper server
pnpm run console -- <command>         # send a command to the running server
```

- No test framework in this repo. Verification is `compileJava` + manual `/bossspawn <id>` in the dev server.
- `plugin/gradle.properties` is gitignored; copy `gradle.properties.example` if the default JDK isn't on PATH.
- Paper API `26.2` (`compileOnly io.papermc.paper:paper-api:26.2.build.+`), JDK 17+ toolchain.
- `server/` is gitignored — needs a Paper jar matching `api-version` in `plugin/src/main/resources/plugin.yml`.

## Boss engine map

```
boss/
  Boss.java            stateless definition: id, phases, loot, and every config-backed cap
  BossPhase.java       threshold + attack pool + onEnter + mechanicFactory()
  BossAttack.java      telegraph -> execute -> recovery, via the final sequence(...) helper
  BossInstance.java    ALL runtime state for one live fight; owns cleanup in end(EndReason)
  BossManager.java     registry, one live instance per boss id, shutdownAll() on onDisable
  PhaseMechanic.java   per-phase rules: filterDamage, onBossDamaged, readyToAdvance
  Arena.java / ArenaBarrier.java / ArenaSafetyListener.java
  grief/               ArenaLedger (undo log) + Grief (destructive primitives) + explosion/drop listeners
  meter/               unhealable armor-ignoring stack meters (MeterRegistry, PlayerMeter, MeterThresholds)
  mechanics/           ~35 reusable phase mechanics
  events/              scripted one-shot milestone events
  telegraph/           Telegraph wind-up shapes, built on fx/Fx
  bosses/<name>/       per-boss packages; bosses/attacks/ is the shared attack pool
realm/                 generated walled arena worlds (RealmManager locks time to 18000)
```

Player-side families, each with the same shape (abstract base + registry + manager + catalog menu):
`items/weapons`, `items/shields`, `armor/sets`, `accessory/accessories`, `consumable/consumables`,
`ridable/ridables`, `stone/stones`, `opitem/opitems`. Every one is registered by hand in
`WeaponsPlugin.onEnable()` — nothing auto-discovers, in any of them.

`opitem/` is the operator shelf (`weaponsplugin.op`): deliberately unbalanced grants, kept out of the
weapon/consumable registries so `/weaponbalance` never reports on them. `HeartManager` owns bonus
hearts as a per-player tally on disk, rendered as a keyed `MAX_HEALTH` `AttributeModifier` that
`apply()` rebuilds from scratch — runtime modifiers are saved into player data, so anything that isn't
idempotent double-stacks on rejoin. `/hearts` and the Heart Vessel are two doors to that one number.

17 bosses, all registered in `WeaponsPlugin.onEnable()` around line 379: FallenKing, FrostQueen,
StormTyrant, InfernoWarlord, PlagueWarden, VoidSovereign, SolarColossus, TideLeviathan, DragonElder,
NecroOverlord, GraftedHorror, ThreefoldBane, Voidwyrm, AmalgamatedBulk, HollowChoir, WeepingColossus,
Worldender. Adding a boss means registering it here too — nothing auto-discovers.

## Invariants (do not break these)

**No entity or task leaks.** Every spawned entity goes through `AddManager.spawn` (combatant adds) or
`BossInstance.trackEntity` (falling blocks, Display props, `AreaEffectCloud`s). Every `BukkitTask`
goes through `BossInstance.trackTask`. `end(EndReason)` cancels/removes all of it; `shutdownAll()`
runs on `onDisable()`. A raw `world.spawn(...)` or an untracked `runTaskTimer` in an attack is a bug.

**Main-thread only.** All Bukkit API from scheduler tasks. The single async call in the whole boss
package is `DiscordNotifier`'s HTTP post, which touches no Bukkit state. Don't add a second one.

**Every attack telegraphs.** Design rule (§0.2.3): `>= 15 ticks`, scaled by lethality. The engine's
hard floor in `BossAttack.sequence` is `MIN_TELEGRAPH_TICKS = 10` — that is a backstop, not the
target. Roster defaults run 14–50, clustered at 16–24. Telegraph ticks are always config-backed
(`configInt("<attack>-telegraph-ticks", n)`). `sequence` also drives the per-player cast bar and
guards each step so a throwing tick can't leave `attackInProgress` stuck true.

**The ledger restores the world.** Every block write goes through `Grief.setBlock` (grief-gated
damage) or `Grief.setMechanicBlock` (block that *is* the mechanic — not grief-gated, still ledgered).
Never call `block.setType` directly in boss code. `ArenaLedger` rules: first write wins, tile
entities are refused outright, the block budget is a hard stop, all writes use `applyPhysics=false`.
Real explosions are covered by `ExplosionLedgerListener` -> `recordExplosion`.

**Caps are config, and they always apply.** `maxExplosionPower`, `maxCraterRadius`,
`maxFallingBlocks`, `maxParticlesPerTick`, `maxLedgerBlocks`, HP scaling clamped 1.0x–2.5x. Stability
caps are never bypassed, even grief-on.

**Commands stay locked down.** `weaponsplugin.boss.*` (default op), boss id validated against the
registry, unknown id is a message not an exception.

## Design law (specs `docs/superpowers/specs/2026-07-27-boss-rework-batch-1.md` §0)

Code comments cite these as `§0.1`, `§2.4`, `batch-3 §2.3` — that numbering is the batch spec, keep it.

- **§0.1 Weaponise Minecraft, don't simulate it with particles.** Name the real Minecraft object
  first: real lava, real falling blocks, real `AreaEffectCloud`, real end crystals, real blue ice.
  Particles are allowed exactly five jobs — telegraph, danger boundary, impact flash, polish,
  reinforcing a physical effect. If deleting the particle code stops the attack working, it's wrong.
  Corollary: the arena at 20% HP must look nothing like it did at 100%, and players use that terrain.
- **§0.2 Encounter rules.** No text-only phases. Phases exit on HP threshold **AND** objective
  resolved (`readyToAdvance`) so burst can't skip a mechanic. No unavoidable and no ignorable damage.
  Anti-facetank is a meter that ignores armor and can't be healed off, not bigger numbers. **Boss
  stays live during everything — "boss invulnerable while you do a chore" is the anti-pattern this
  engine was rewritten to kill.** Melee and ranged each pay rent. Multiplayer scales mechanic count
  and coverage, never mechanic damage; every 2-player mechanic has a designed solo solve.
- **§0.3 Three settled rulings.** Arena restore is ON, and it's what *licenses* extreme destruction.
  No death by falling — pits are heavy damage plus an eject onto solid floor (`ArenaSafetyListener`
  cancels fall damage in a live fight; `enforcePitFloor` applies the real punishment). The arena
  supplies all item-based counterplay, placed at fight start and replenished per phase (see
  `bosses/bane/BaneSupplies.java`) — scarce and contested, never absent.

## Patterns worth copying

**Fight-scoped state: static registry + untracked watchdog.** State that must outlive a phase (rods,
pylons, clocks, floods) lives in a per-boss `<Name>Fight` class keyed by boss entity UUID in a
`static Map<UUID, …> ACTIVE`, with its own watchdog on a 10-tick timer. The watchdog is deliberately
**not** registered via `trackTask`: `end()` cancels tracked tasks as its first step, so a tracked
watchdog would be dead before teardown needed it. Canonical: `bosses/storm/StormFight.java`.

**Phase mechanics have three levers, and the last two are the interesting ones.** Global
vulnerability (`setDamageMultiplier`) is the old immunity-gate framing — reach for `filterDamage`
(per-attacker rules: only the duel target, only from behind, two players together) and
`readyToAdvance` (exit on survival/positioning, not a number) instead. `clampToPhaseFloor` is the
timeout valve that stops a stuck mechanic deadlocking a fight; a mechanic holding the boss unhittable
must eventually let go.

**Player-side §0.1 has two kits, and neither is optional.** A weapon or stone that wants a real object
spawns it through `items/kit/Props` (falling block, TNT, wind charge, fireball, `AreaEffectCloud`, end
crystal, ender pearl, lightning, loaned item) — each returns already defanged and tagged, and
`WeaponPropListener` strips block damage, ignition and fire spread from anything carrying that tag. A
weapon that wants terrain writes it through `items/kit/TempTerrain`, the time-keyed undo log (weapons
have no fight-end to roll back at). Never `block.setType`, never a bare `world.spawn` of an explosive.
`items/kit/Counterplay` is the third: one call per boss verb a drop is built to answer.

**Spears cast off the charge attack, and that's a whole base class.** A `*_SPEAR` material's right-click is
already a vanilla mechanic: `Item.use` runs the item's `KineticWeapon` component, the spear drops into an
attack position, and it stabs whatever the player's own momentum carries it through. So the five pikes extend
`items/SpearWeapon` (`ability1OnChargeAttack()` = `true`): `WeaponInteractListener` stops cancelling the
main-hand right-click — that cancel is what would deny `Item.use`, which *is* the charge attack — and
`WeaponChargeListener` fires ability1 on the connect, through the same cooldown/switch-lock/`CastFx` path.
The attack itself is never gated: a spear on cooldown still charges. Because the connect hands over the entity
struck, spear abilities implement `ability1(Player, LivingEntity)`; the one-arg `ability1` is `final` on
`SpearWeapon` and just clicks. `items/kit/ChargeStrike.afterCharge` is for the abilities that want where the
caster came to rest instead. Slots 2–4 are unchanged.

Two spear traps, both already paid for:
- **`DamageType.SPEAR` does not identify the charge attack.** Vanilla routes the jab (`PiercingWeapon`) and
  the charge (`KineticWeapon`) through the same `LivingEntity.stabAttack`. The discriminator is
  `player.hasActiveItem()` — the charge attack only runs while the item is in use.
- **The charge attack is speed-gated, so a standing right-click is a legitimate no-op.**
  `NETHERITE_SPEAR`'s `damageConditions` is `ofRelativeSpeed(175, 4.6)` after an 8-tick arm delay — below
  ~4.6 blocks/second (sprint is ~5.6, walk ~4.3) it lands no hit and ability1 never casts. `ability/SpearChargeTask`
  is the action-bar readout for that: winding up / too slow / armed, off `SpearWeapon.chargeArmTicks()` and
  `chargeMinSpeed()`. Those two mirror vanilla — moving them moves the cue, not the real threshold.
- **Lunge is the *other* mechanic, and it is enchantment-gated.** `EntityLungeEvent` is the `lunge`
  enchantment's `post_piercing_attack` effect — the left-click jab shoving you forward — not the right-click
  charge. A spear with no `Enchantment.LUNGE` never lunges and never fires the event, which is why
  `Weapon.buildItem` stamps `LUNGE` (level `lungeEnchantLevel()`) onto every `ability1OnChargeAttack()`
  weapon and `WeaponLungeListener` does nothing but add `lungePowerBonus()` on top. Vanilla still gates it:
  no vehicle, not gliding, not in water, 7+ hunger.

**Stones have two tick cadences.** `Stone.onEquipTick` is 2Hz (`StoneTickTask`) and suits refreshing a
potion effect. Anything that has to answer a movement input — grabbing a wall, arming a double jump,
freezing the water you just stepped onto — goes in `Stone.onFastTick`, driven every tick by
`StoneMovementTask`. `onIdleTick` runs for players *without* that stone, and exists because a stone
that arms a vanilla capability (`allowFlight`) must be able to un-arm someone who unsocketed it.

**Config convention.** `bosses.<id>.<key>` via `Boss.configDouble/configInt/configBoolean` and
`BossAttack.configDouble/configInt`. The parallel families: `weapons.<id>.*`, `stones.<id>.*`,
`consumables.<id>.*`, `op-items.<id>.*`. Every tunable number gets a key — no bare literals for damage,
radius, duration, or telegraph length.

## Gotchas already paid for

- `ArenaLedger.restore` schedules an **untracked** task, and takes `immediate=true` on
  `PLUGIN_DISABLE` — no further tick will run, so a batched restore would silently never finish.
- `end(EndReason)` is wrapped in try/finally so `manager.forget()` and the ledger restore always run;
  an exception mid-cleanup would otherwise leave the boss permanently "live" and block `/bossspawn`.
  Meter teardown runs *first* — meters hold roots/blinds on the player, not on the boss.
- On `DEFEATED` the entity is already mid vanilla death processing; only `DESPAWNED`/`PLUGIN_DISABLE`
  call `entity.remove()`.
- `Particle.DRAGON_BREATH` needs a `Float` data arg per call — the data-less `spawnParticle` overload
  throws and aborts the rest of the attack (see `Telegraph.spawnConePoint`, `Fx.dragonBreath*`).
- `LightningStrike` has no "causes fire" switch in the Bukkit interface — a real bolt from a weapon is
  only safe because `WeaponPropListener` refuses the `BlockIgniteEvent` it produces. The same listener
  refuses spread/burn out of any `TempTerrain` block, which is what makes real fire placeable at all.
- `world.strikeLightningEffect` is the *cosmetic* call (a flash, no entity). An ability that "calls
  lightning" wants `Props.lightning`; the effect-only version was the roster's commonest §0.1 violation.
- An ender pearl must be `launchProjectile`d, not `world.spawn`ed — the pearl teleports its owner, and
  ownership is what launching establishes.
- One double-tap-sneak fires **every** equipped stone's personal ability, not a chosen one (accessories
  behave the same way). Three active stones socketed together all go off on the same tap.
- Terrain placed *inside* a player's own block suffocates and soft-locks them; ring them instead
  (`FrostScythe.growSpike`). Likewise, a fire trail sampled from the caster's path must skip the samples
  nearest where they stopped, or every cast sets the caster alight.
- Blocks a fight placed are `tracks()`-checked by `LedgerDropListener` so props can't be farmed.
- `BossAttack` keeps `activeCtx` as instance state — safe only because `BossManager` allows one live
  instance per boss id and `BossInstance` never starts a second attack mid-cast.
- Restore lifts players out of refilled terrain (`liftTrappedPlayers`) — winning shouldn't suffocate.
- `UI_PRESENCE_BUFFER` (8 blocks) and `INVALID_TICKS_BEFORE_DESPAWN` (4) exist because knockback and
  fresh realm worlds produce transient out-of-arena / invalid-entity reads.
