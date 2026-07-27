# Boss Rework Handoff — Make the Remaining 16 Bosses Feel Like Skyblock Slayers

You are picking up a Minecraft Paper plugin (`WeaponsPlugin`) mid-rework. One boss (the Fallen King)
has been fully converted to a new three-layer boss architecture and playtested. **Your job is to bring
the other 16 bosses up to the same standard**, using the per-boss mechanic specs at the end of this
document.

Read this whole document before writing code. The "Hard-Won Pitfalls" section exists because every one
of those bugs actually shipped and had to be found in play — do not rediscover them.

---

## 1. Repo Orientation

- **Repo root:** `C:\Users\rbm72\Documents\GitHub\mc_plugins`
- **Plugin module:** `plugin/` (Gradle, Java 21+, Paper API 26.2)
- **Boss framework:** `plugin/src/main/java/dev/rbm72/weaponsplugin/boss/`
- **Boss definitions:** `boss/bosses/*.java` (17 bosses)
- **Attacks:** `boss/bosses/attacks/*.java` (~130 classes)
- **Phase mechanics:** `boss/mechanics/*.java` and `boss/gates/*.java`
- **Events:** `boss/events/*.java`
- **Arenas ("realms"):** `realm/` — every boss fights in its own generated dimension
- **Registration:** `WeaponsPlugin.java` around line 370

### Build / deploy / test loop

```bash
node scripts/mc.mjs build
node scripts/mc.mjs stop
node scripts/mc.mjs deploy
node scripts/mc.mjs start
```

`deploy` fails with `EBUSY` if the server is running — always stop first. Compile-only check:

```bash
cd plugin && ./gradlew compileJava --console=plain
```

Server log: `server/logs/latest.log`. Boss spawns log one line each — grep for `Boss '<id>' spawned`.
If that line is absent, the boss never registered and is not ticking.

You **cannot** spawn a boss from the console (`/bossspawn` requires a Player sender; `execute as` does
not satisfy it). Ask the user to test in-game.

---

## 2. The Three Layers

This separation is the core of the rework. Keep it strict.

| Layer | Cadence | Owns | Player experience |
|---|---|---|---|
| **Attacks** | constant, random from a pool | `BossAttack` | "dodge this" |
| **Phases** | long, health bands | `PhaseMechanic` | "while X is true, you must keep doing Y" |
| **Events** | sharp, at health milestones | `BossEvent` | "stop everything, do this now, or else" |

### What went wrong before (do not repeat)

The original design had **one** mechanic — break destructible plates to unlock a damage window — used
in all 72 phases across all 17 bosses. Reskinning it (break plates / kill guards / hit a timing window
/ free a hostage) did **not** fix the problem, because every variant collapsed to the same three beats:

```
boss immune → do chore → boss hittable for N seconds → repeat
```

The user's exact complaint: *"it's just break plates, hit guy, break plates but for healer, hit guy,
then hit guards, then hit guy."*

**The lesson:** stop varying *what unlocks damage*. The boss should be hittable almost always. Vary
what else the player must be doing **while** they fight.

### The test a phase must pass

```
While the boss is doing X  →  the player must keep doing Y  →  or Z happens
```

Both sides continuous, running *alongside* normal combat. If your mechanic pauses the fight so players
can do a chore, it is a gate and it is wrong.

---

## 3. API Reference

### `BossPhase`

```java
// Ungated — boss fully hittable the whole band. Every boss should have at least one.
new BossPhase(String name, double entryThresholdFraction, List<BossAttack> attacks,
              boolean enrage, Consumer<BossInstance> onEnter)

// With a mechanic
new BossPhase(name, threshold, attacks, enrage, onEnter,
              Function<BossInstance, PhaseMechanic> mechanicFactory)

// Legacy weak-point gate — AVOID for new work
new BossPhase(name, threshold, attacks, enrage, onEnter, VulnerabilitySpec spec)
```

Thresholds must strictly descend; phase 1 is always `1.0`. Convention: `1.0 / 0.75 / 0.40 / 0.15`,
last phase `enrage = true`.

### `PhaseMechanic` (`boss/PhaseMechanic.java`)

```java
public interface PhaseMechanic {
    void start();
    void stop();                                            // must be idempotent
    default double filterDamage(Player attacker, double damage) { return damage; }
    default void onBossDamaged(Player attacker, double damageDealt) {}
    default boolean readyToAdvance() { return false; }      // non-health phase exit
}
```

- `filterDamage` is the **per-attacker** lever — duel locks, rear-only hits, "only the furthest player".
  Runs inside the damage event on the main thread. Keep it cheap; never schedule from it.
- `readyToAdvance` lets a phase end on an event ("survived 60s", "pushed off the throne") instead of a
  health threshold. `selectPhase` takes the later of health-band and mechanic-signalled, so a boss
  healing can never rewind past a beaten phase.

### `BossEvent` (`boss/BossEvent.java`)

```java
public abstract class BossEvent {
    protected BossEvent(WeaponsPlugin plugin, String bossId);
    public abstract String id();
    public abstract double[] triggerFractions();            // e.g. {0.88, 0.62, 0.28}
    public abstract void run(BossInstance instance, Runnable onComplete);
    public void onHit(BossInstance instance, Player attacker, double damageDealt) {}
    public void cleanup(BossInstance instance) {}
    public boolean blocksCombat() { return true; }
    protected final double configDouble(String key, double def);
    protected final int configInt(String key, int def);
}
```

Register on the boss:

```java
@Override
public List<BossEvent> events() {
    return List.of(new HitCountShieldEvent(plugin, id(), new double[] {0.88, 0.28}));
}
```

**`blocksCombat()`:**
- `true` — boss stops attacking and chasing. Only for **short, total-attention** interruptions
  (shield check, channelled execution). Never for anything over ~8 seconds.
- `false` — boss keeps fighting. Use for anything long or meant to be handled *while* fighting
  (repositioning, side objectives). A 10-second event with the boss frozen is dead air.

**`onHit` fires even when damage is 0** — that is how hit-count mechanics work (count swings, not damage).

### `BossInstance` — the levers

```java
WeaponsPlugin plugin();  Boss boss();  LivingEntity entity();  Arena arena();
AddManager addManager();  MechanicBar mechanicBar();  List<Player> barViewers();

void trackTask(BukkitTask);          // REQUIRED for every scheduled task
void trackEntity(Entity);            // REQUIRED for every spawned prop/display
void setDamageMultiplier(double);    // 0.0 = immune, 1.0 = normal, >1 = exposed
void setForcedInvulnerable(boolean); // hard override, cleared on phase change
void recordExposure();               // satisfies the phase floor — see §4
void stagger(int ticks);             // freezes attack selection + chase
void showTitle(Component, Component);
void empower(double scaleMult, double speedMult);
void addPermanentDamageReduction(double);  // never resets; capped at 0.6
boolean signalDeflectReady();        // throttles "your hit was blocked" feedback to ~1/350ms
```

### `MechanicBar` (`boss/MechanicBar.java`) — **use this for all mechanic state**

```java
instance.mechanicBar().updateShared(instance.barViewers(), component, progress, BossBar.Color.BLUE);

instance.mechanicBar().update(instance.barViewers(), player ->
    MechanicBar.Readout.of(component, progress, BossBar.Color.RED));  // per-player

instance.mechanicBar().clear();      // MUST be called in cleanup()/stop()
```

A second boss bar, separate from the health bar, per-player. **Never put running mechanic state on the
action bar** — see Pitfall 4.

### Other helpers

```java
// Particles / sound — dev/rbm72/weaponsplugin/fx/Fx.java
Fx.coloredBurst(Location, Color, float size, int count, double spread);
Fx.coloredRing(Location center, Color, float size, double radius, int points, double angleOffset);
Fx.line(Location from, Location to, Particle, int points);
Fx.burst / ring / flash / sound / expandingRings / blockBurst / helixFrame
Fx.glowPillar(Plugin, Location base, Material, float thickness, float height, int durationTicks); // BlockDisplay, nullable
Fx.spinningIcon(...); Fx.shatterDebris(...); Fx.damageNumber(...);

// Destructible prop — boss/props/ArenaTotem.java
ArenaTotem.spawn(plugin, instance, loc, headItem, label, maxHealth, lifetimeTicks,
                 onDestroyed, onExpired);
ArenaTotem.spawn(..., boolean snapToGround);   // false = hangs in the air (ranged-only)
totem.isValid(); totem.location(); totem.discard();

// Adds — boss/AddManager.java (auto-despawned on phase change and fight end)
instance.addManager().spawn(World, Location, EntityType, Consumer<LivingEntity> customize);

// Players — boss/Arena.java
instance.arena().center();     // FIXED spawn point, never moves
instance.arena().radius();
Arena.playersNear(Location, double radius);
```

**Terrain destruction (`boss/grief/Grief.java`) requires an `AttackContext`**, which only `BossAttack`
has. Mechanics and events cannot call it. If a mechanic needs terrain changes, either do the block
edits directly (and restore them in `cleanup`) or express it with `BlockDisplay` props instead.

---

## 4. The Phase Floor — read this before touching damage

`BossInstance.clampToPhaseFloor` is the last word on every hit. Semantics:

- **Mechanic not satisfied** → floor pins health *exactly on* the next phase's threshold.
  `BossPhase.select` needs strictly-below to advance, so the boss sits on the seam and **will not
  phase** until the group engages.
- **Mechanic satisfied** (`recordExposure()` was called, or `readyToAdvance()`) → floor drops a sliver
  past the seam so the crossing fires.
- **Ungated phase** → satisfied immediately; pure damage race.
- **45-second timeout valve** per phase — if a mechanic is never satisfied (unreachable prop,
  disconnected solo player), the floor stops gating so a fight can never hard-lock.

**Every mechanic must call `recordExposure()` when the group does the thing it is asking for.** If you
forget, the boss stalls on the seam for 45 seconds every phase.

---

## 5. Hard-Won Pitfalls

1. **`onComplete` must fire exactly once on every path.** An event that returns without calling it
   freezes the boss permanently (no attacks, no chase, forever). Route all outcomes — success,
   failure, timeout, entity-invalid — through a single private `finish()` that nulls the runnable
   before invoking it. This shipped once already.

2. **Never leave the boss permanently unhittable.** `setForcedInvulnerable(true)` without a guaranteed
   `false` is a dead fight. Always clear it in `cleanup()`/`stop()` as well as on the success path.

3. **Failing a mechanic must not be *better* than doing it.** An early version opened the boss up
   between failed attempts, making it optimal to ignore the mechanic entirely. Failure should cost
   something and grant nothing.

4. **The action bar cannot hold mechanic state.** `ActionBarHub` is one line; a `PRIORITY_NOTICE`
   flash (10) suppresses `PRIORITY_SUSTAINED` (5) for its whole duration. Notices fired per-hit were
   smothering every counter in the fight, and `BossAttack`'s cast bar sits at the same priority as
   sustained readouts and strobes against them. **Use `MechanicBar`.** Reserve `ActionBarHub.flash`
   for genuine one-offs, and never fire one on every hit.

5. **`ArenaTotem.spawn` ground-snaps by default.** Pass `snapToGround = false` for airborne props.

6. **Weak-point placement must be clamped inside the arena.** The boss is usually pressed against the
   wall by its own leash, so anything placed relative to the boss can land outside the reachable area.
   Anchor to `arena.center()` when the thing must be reachable.

7. **`world.setTime()` throws on `NETHER`/`THE_END` worlds** ("Cannot set time in world without world
   clock"). Three realms use `NETHER`. Guard with `world.getEnvironment() == World.Environment.NORMAL`.

8. **Track everything you create.** `instance.trackTask(task)` and `instance.trackEntity(entity)` or it
   leaks past the fight.

9. **Do not fire an event on a phase boundary.** Offset milestones from the phase thresholds
   (0.75/0.40/0.15) so events land mid-phase — a transition already has a title and cinematic firing.

10. **Solo must be survivable.** Every mechanic needs a defined solo behaviour. Prefer "the solo player
    can do it alone but it costs them" over "impossible".

11. **Particle budget.** Never fill an area with particles per tick — draw seams, rings, and outlines.
    `boss.maxParticlesPerTick()` (default 400) is the guideline.

---

## 6. Reference Implementation — Read These First

The Fallen King is the fully converted, playtested example. Read before writing anything:

- `boss/bosses/FallenKing.java` — four phases, five events, all wiring
- `boss/mechanics/DuelLockMechanic.java` — `filterDamage` per-attacker rule
- `boss/mechanics/EmpoweringAddsMechanic.java` — soft scaling, no lockout, real player choice
- `boss/mechanics/WrathMeterMechanic.java` — damage *pacing*; sometimes the right play is to stop attacking
- `boss/mechanics/RegicideMechanic.java` — health is not the win condition
- `boss/events/HitCountShieldEvent.java` — counts **hits not damage**; the gear-check event
- `boss/events/CourtJudgmentEvent.java` — per-player `MechanicBar` readout
- `boss/events/CursedCoinTossEvent.java` — non-blocking event, conditional floor
- `boss/events/WardplateAuctionEvent.java` — permanent consequence, optional objective

Reusable as-is on other bosses: `HitCountShieldEvent`, `BeaconEvent`, and the four gate archetypes in
`boss/gates/` (`AddCullGate`, `SkyshotGate`, `ControlZoneGate`, `RescueGate`, `PunishWindowGate`) plus
the `Gates` one-line factory helper. **Use gates sparingly** — they are the old "do a chore" shape.

---

## 7. What "Skyblock Slayer" Means Here

The user's reference points, and what to take from each:

- **Hit-count shield** — a check that rewards attack *speed*, not damage per swing. Makes gear choice
  matter. Already built.
- **Beacon** — a blunt 5-second positional deadline with an unmissable visual and a severe cost.
  Already built.
- **Tracking projectiles / floor glyphs** — demand constant movement.
- **Look-at mechanics** — clear something by aiming your camera at it. A verb nothing else uses.
- **Invulnerable channel with rotating beams** — the boss stops, becomes untouchable for ~8s, and the
  group survives a spectacle. Stacking debuffs (healing reduction, damage amp) that persist.

Common properties to preserve:

- Short and sharp; recurring at health intervals, not once per fight
- One unambiguous verb per mechanic
- Telegraphed loudly — sound **and** visual, because players are looking at the boss, not the UI
- Failure is severe and legible; you always know you failed and why
- Different mechanics punish different playstyles

---

## 8. Design Rules (agreed with the user)

1. **Every boss gets a signature mechanic no other boss has.**
2. **Every boss gets at least one ungated phase** — a pure damage race with no mechanic. This is the
   one deliberate repeat across the roster; it is the palate cleanser.
3. **No boss repeats a mechanic within its own four phases.**
4. **Failure scales with roster position:** early bosses punish the players directly (damage, debuff);
   late bosses *also* empower themselves (heal, harden, speed up) so ignoring a mechanic compounds.
   Use `addPermanentDamageReduction` for lasting consequences.
5. **Attacks are separate from phases.** The existing attack pools are good and the user likes them.
   Do not rewrite attacks; only rewire phases and add events.
6. **Complexity ramps with roster order** (see below). Boss #1 is simple; boss #17 layers mechanics.

### Roster order (difficulty progression)

`FallenKing(done) → FrostQueen → StormTyrant → InfernoWarlord → PlagueWarden → VoidSovereign →
SolarColossus → TideLeviathan → DragonElder → NecroOverlord → GraftedHorror → ThreefoldBane →
Voidwyrm → AmalgamatedBulk → HollowChoir → WeepingColossus → Worldender`

Health: most 300; DragonElder 400; SolarColossus 500; Worldender 1000 with **8 phases**.

### Current state of each boss

**All 17 are converted.** Every boss now has four phases (Worldender has eight), exactly one ungated
phase, three distinct mechanics with no repeat inside a single boss, and 2–3 events at milestones
offset from its phase boundaries. No `VulnerabilitySpec` weak-point phase remains anywhere on the
roster; the only surviving gate is Storm Tyrant's `ControlZoneGate`, kept deliberately because it
*is* the Eye of the Storm spec.

Everything below this line is the design brief the conversion was built from. It is still the
reference for **tuning** and for judging whether a change is in keeping — the pitfalls in §5 and the
phase-floor semantics in §4 remain live constraints on any new work.

**Not yet playtested.** It compiles clean and the server boots with all 17 bosses registered and no
`SEVERE`, but `/bossspawn` needs a Player sender, so nothing has been verified in play. Expect
tuning passes on damage numbers, timers and radii — every number is a `bosses.<id>.<key>` config key
precisely so that can happen without recompiling.

---

## 9. Per-Boss Mechanic Specs

These are the user's own designs. Implement them as written; they are better than a generic pass.
Assign each boss roughly one signature phase mechanic, one or two supporting phases, one ungated
phase, and 2–3 events at offset health milestones.

### Frost Queen
- **Thin Ice** — floor breaks into shrinking ice floes while she novas; fall through and you die.
- **Frozen Court** — encases 2 random players solid; teammates must break the ice before they suffocate.
- **Absolute Zero Nova** — room-wide freeze pulses stack a slow-to-death debuff unless players find a heat pocket.

### Storm Tyrant
- **Grounding Rods** — charged rods light up; spread across them or eat unrouted chain lightning.
- **Eye of the Storm** — wind-shielded except inside a slow-moving calm bubble; chase it to land hits.
  *(Already built as `ControlZoneGate` — reuse, do not rebuild.)*
- **Chain Lightning Tag** — a bolt jumps player-to-player, growing stronger each hop; spread to dilute.

### Inferno Warlord
- **Moat Eruption** — lava moat geysers into an expanding ring with shrinking safe gaps.
- **Molten Brand** — brands players with rising heat stacks that must be vented or they self-detonate.
- **Warlord's Crucible** — yanks everyone into melee range for a point-blank AoE unless the pull is broken.

### Plague Warden
- **Contagion Ledger** — hits stack infection; bursts room-wide at threshold, scaled by total stacks.
- **Spore Cloud Maze** — spreading poison clouds carve the floor into a shifting, shrinking maze.
- **Withering Roots** — telegraphed roots erupt underfoot, snaring/poisoning anyone slow to move off.

### Void Sovereign
- **Mirrorflesh** — splits into 3 blinking duplicates; hit the fake and you get randomly blinked yourself.
- **Blink Snare** — traps a player in a personal void bubble; they must portal-puzzle out before time runs out.
- **Gravity Well** — a slow void rift drags everyone toward center; walk against it, dodging orbiting motes.

### Solar Colossus
- **Idol Sequence** — goes invulnerable while 4 sun idols light in order; strike the sequence or eat a flare wipe.
- **Solar Flare Countdown** — charges a beam; break line of sight behind arena pillars or take true damage.
- **Golem's Judgment** — fist slams spread ground-cracks that shatter the floor outward.

### Tide Leviathan
- **Undertow** — constant riptide pull, broken only by periodic safe bubbles.
- **Bubble Prison** — traps a player in a rising water bubble teammates must pop before it drowns them.
- **Riptide Slam** — a wide knockback wave timed to the arena's flood level.

### Dragon Elder
- **Shadow Bombardment** — dive-bomb trails criss-cross the floor; track its shadow, not the impact.
- **Wing Cyclone** — an outward wind vortex flings anyone not anchored to a wall/pillar off the platform.
- **Sky Fracture** — marks 3 players with a delayed dive-strike, punishing anyone who clusters up.

### Necro Overlord
- **Bound Trio** — 3 linked wraiths share one health pool; no drain unless all three take damage together.
- **Soul Harvest** — tethers a soul-orb to a random player; kill the tether add before it heals the overlord.
- **Grave Bloom** — gravestones sprout grabbing hands that root anyone standing over them.

### Grafted Horror
- **Suture Shield** — grows a regenerating flesh shield; fast hits sever it before it knits back.
- **Limb Detachment** — a detached arm becomes its own smashing add; kill it early to deny a regrow-heal.
- **Toxic Graft** — spreading ground patch inflicts a healing-reduced "grafted" state until cleansed.

### Threefold Bane
- **Three Heads, Three Mechanics** — each head runs its own gimmick (nuke, tracking projectiles,
  execute-mark) simultaneously.
- **Discord Howl** — all three heads roar on a delay; overlapping cones punish grouping up.
- **Withering Convergence** — heads periodically sync for one combined ultra-nuke needing a full-party
  dodge/cooldown window.

### Voidwyrm
- **False Ground** — patches of floor are illusory and vanish mid-fight during its breath sweeps.
- **Void Breath Corridor** — a rotating safe corridor forms inside its breath; track it and stay inside.
- **Star Collapse** — a shrinking void orb drags and damages; DPS race to kill it before full collapse.

### Amalgamated Bulk
- **Split Pressure** — splits into shards at HP thresholds; kill them fast or they re-merge and heal it back.
- **Acid Puddle Legacy** — dying shards leave lingering acid, slowly filling the arena floor.
- **Total Absorption** — re-merges early unless enough shards die within a short window, undoing split progress.

### Hollow Choir
- **Discordant Verse** — vex-heads pulse in rhythm; get caught looking at one when it sings, take heavy damage.
- **Silence Ward** — a zone disables abilities unless the caster head is interrupted first.
- **Vex Round** — vex carry shield shards that must die in a specific chime-cued order.

### Weeping Colossus
- **Rising Tears** — the arena floods incrementally; reach high ground before it peaks.
- **Grief Echo** — echo-zones replay its last attack pattern on a delay; remember it, dodge the replay.
- **Downpour Despair** — storm intensifies, vision-obscuring, pulsing AoE unless sheltered.

### Worldender (8 phases, 1000 HP, final boss)
- **Total Eclipse** — invuln-nuke phase layered with one-shot sculk-shriek cones; break line of sight.
- **Resonance Cascade** — shrieks chain between players standing too close; forces spread.
- **The Deep Silence** — darkness + deafness phase; only visual pulse cues telegraph the next attack, no sound.
- Remaining phases should **replay other bosses' signature mechanics** — it is an amalgamation. Zero
  new mechanics needed beyond the three above.

### Known duplicates — merge, do not build twice

- Storm Tyrant's *Eye of the Storm* is the existing `ControlZoneGate`.
- Necro's *Bound Trio*, Threefold's *Three Heads*, Amalgamated's *Split Pressure*, and Void Sovereign's
  *Mirrorflesh* are all the **shared-health linked-entity** primitive. Build it once, parameterise it.
- Tide Leviathan's *Bubble Prison* and Frost Queen's *Frozen Court* are both the **trap-a-player,
  teammates-free-them** primitive — `RescueGate` already implements this. Generalise to N players.
- Weeping Colossus's *Rising Tears* and Tide Leviathan's *Rising Tide* are the same **flooding floor**
  primitive.

---

## 10. Suggested Order of Work

1. Build the shared primitives first: **linked shared-health entities**, **flooding/collapsing floor**,
   **per-player stack meter**, **proximity chain**, **look-at/gaze check**, **line-of-sight check**,
   **force field (pull/push)**. Most of the 51 specs are combinations of these.
2. Convert bosses in roster order so difficulty ramps naturally.
3. **Convert one boss, deploy, have the user playtest, then continue.** Do not write ten bosses before
   anything is tested — the first pass of this rework did exactly that and every batch had a bug that
   only surfaced in play.
4. After each boss: `./gradlew compileJava`, then full build/deploy/restart, then confirm
   `Boss '<id>' spawned` appears in the log.

## 11. Definition of Done, Per Boss

- Four phases, no two using the same mechanic
- At least one ungated phase
- A signature mechanic no other boss has
- 2–3 events at milestones offset from phase boundaries
- Every mechanic calls `recordExposure()` when satisfied
- Every mechanic and event puts its running state on `MechanicBar`, cleared in `cleanup()`/`stop()`
- Every scheduled task is `trackTask`'d; every spawned entity is `trackEntity`'d
- Boss cannot become permanently unhittable on any path
- Defined solo behaviour
- Config keys under `bosses.<boss_id>.<mechanic-key>-*` for every tuned number
- Compiles clean and boots with no `SEVERE` in the log
