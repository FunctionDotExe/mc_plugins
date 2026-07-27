# Boss Rework — Design Pass, Batch 1 of 5

**Status:** design only. No code, no implementation work. Batches 2–5 cover the remaining 12 bosses.

**Roster (17):** Fallen King, Frost Queen, Storm Tyrant, Inferno Warlord, Plague Warden,
Void Sovereign, Solar Colossus, Tide Leviathan, Dragon Elder, Necro Overlord, Grafted Horror,
Threefold Bane, Voidwyrm, Amalgamated Bulk, Hollow Choir, Weeping Colossus, Worldender.

**Batch 1 picks:** Fallen King, Frost Queen, Storm Tyrant, Plague Warden, Void Sovereign.

Why these five: they establish five *non-overlapping interaction verbs* that every later boss remixes
— **duel/target-priority** (King), **terrain-denial + traversal physics** (Queen), **verticality +
conduction/knockback** (Tyrant), **attrition + stealth/resource-denial** (Warden), **floor-loss +
forced relocation** (Sovereign). Get these right and batches 2–5 have a vocabulary to draw on instead
of inventing another AoE circle.

---

## 0. Design philosophy (applies to every boss and every ability from here on)

### 0.1 Weaponise Minecraft, don't simulate it with particles

Rule: before designing any attack, name the **Minecraft object** that produces the experience.
Particles come last and only as garnish.

| Instead of | Use |
|---|---|
| fire particle circle | real lava flowing into arena sections, burning logs as falling blocks, fire spread, chained TNT |
| rock explosion particles | real falling stone/deepslate, cracked terrain, ice/bone/stone pillars placed upward, ceiling collapse |
| ice particles | real blue ice on floor (players actually slide), powder snow pits, falling packed-ice, ice-encasement blocks |
| wind particles | real wind charges, Breeze mobs, velocity shoves, falling blocks blown across arena, scaffolding towers collapsing |
| poison cloud particles | real `AreaEffectCloud` entities, thrown splash potions, sculk spread, mud/soul sand ground conversion |
| void/dark particles | real end crystals, shulkers, ender pearls, actual removed floor blocks |

**Particles are permitted for exactly five jobs:** telegraph, danger-zone boundary, impact flash,
polish, and reinforcing a physical effect that already exists. A particle may never *be* the
mechanic. If deleting the particle code makes the attack stop working, the attack is designed wrong.

**Corollary — the world is the memory of the fight.** An arena at 20% boss HP should look nothing
like it did at 100%: anvils embedded in the floor, ice sheets, sculk crawling outward, holes where
the floor used to be. Players should be able to *read the fight's history off the terrain*, and
should be able to *use* that terrain (cover behind fallen anvils, boats on ice, scaffolding).

### 0.2 Encounter rules

1. **No text-only phases.** A phase transition must change what the player physically does. Titles
   are confirmation of something the player already saw happen in the world, never the event itself.
2. **Phases end on resolution, not just health.** Every phase exit = `HP threshold reached` **AND**
   `phase objective resolved` (the `readyToAdvance` lever). This kills burst-skipping outright: you
   cannot delete a phase before its mechanic has been played at least once.
3. **No unavoidable-by-design damage, no ignorable damage either.** Every attack telegraphs
   (≥ 15 ticks, scaled by lethality) and every attack has a physical counterplay: move, break a
   block, use an item, body-block, get behind cover.
4. **Anti-facetank is structural, not numeric.** The answer to "player just tanks it" is never
   "more damage". It is a **stack/meter that ignores armor and cannot be healed off** (freeze,
   infection, static charge, void-echo), curable only by doing the mechanic.
5. **Boss stays live during everything.** No "boss stands invulnerable while you do a chore" — if
   the boss is gated, it is actively pressuring during the gate (chasing the carrier, sweeping the
   arena, firing from above). Downtime is a bug.
6. **Melee and ranged both pay rent.** Melee eats close-range mechanics. Ranged eats
   perimeter/isolation mechanics. Neither range band can solve the fight alone.
7. **Multiplayer scales mechanic *count and coverage*, never mechanic *damage*.** Every
   "needs 2 players" mechanic has an explicitly designed solo substitute — not a disabled mechanic,
   a *different* solve.

### 0.3 Ruled decisions (user, 2026-07-27)

These three answers are settled and bind every batch from here on.

**1. Arena restore is ON.** An arena keeps a **block ledger** of everything a fight changed, and
rolls back on fight end. This reverses the earlier "permanent, unbounded grief" policy, and it
*unlocks* the design rather than limiting it: because destruction is undone, bosses can be far more
destructive than a permanent-grief design could ever safely allow. Consequences for design:

- Terrain change is now free to be aggressive — flood the floor with lava, delete half the arena,
  bury it in anvils. It all comes back.
- Destruction outside the arena radius should be **clamped**, not permanent-and-unbounded: anything
  the ledger can't restore shouldn't happen. Attacks reach the arena edge and stop.
- Terrain-as-memory (§0.1) still holds *within* a fight — the arena at 20% HP looks nothing like it
  did at 100%. It just resets for the next group.

**2. No death by falling. Pits are damage, not drops.** Any mechanic that "removes the floor" is a
**lethal-damage pit**, not a hole into the void. Concretely: falling in deals very heavy damage
(scaled to a large fraction of max HP, survivable at full health, fatal if you were already hurt) and
then **ejects the player back onto solid arena floor**. Nobody dies to gravity, nobody falls out of
the world, nobody has to run back. Every "you fall" line in the mechanics tables below means exactly
this. Repeated pit falls are punishing because the damage stacks up, not because one mistake removes
you from the fight.

**3. The arena supplies all item-based counterplay.** Any design that leans on a vanilla item —
leather boots, chorus fruit, water buckets, flint & steel, catchable ender pearls, arrows — the
**arena provides it**, dropped or placed at fight start and replenished per phase. No mechanic may
assume a player prepared correctly beforehand, and no fight is unwinnable because someone showed up
with an empty inventory. Item counterplay stays *scarce and contested* (that's the interesting part),
but never *absent*.

---

## 1. The Fallen King

### 1.1 Identity

- **Theme:** an undead sovereign holding court in a collapsing throne room.
- **Fantasy:** a formal *duel* — steel on steel, with his court trying to interfere and the crown
  itself as the prize on the floor.
- **Unique:** the only boss in the roster where **who is allowed to hurt him is a mechanic**. He
  recognises one Challenger at a time. Everyone else fights the court, carries the regalia, and rings
  the bell. It is the roster's target-priority / role-rotation encounter.

### 1.2 Core gameplay loop

The King picks a **Challenger** and duels them with three readable melee combos. The Challenger's
job is *reading and spacing* — each combo has a distinct wind-up pose and a distinct correct answer
(step back / step in / strafe). Everyone else's job is *the court*: real armoured skeleton knights
that will chain-tether the Challenger if left alive, plus the Crown Shards on the floor and the
throne bell.

The Challenger role **rotates on events, not timers** — it passes when the current Challenger lands
a riposte (hit inside the post-combo recovery window) or when they take a Wound. So the group is
constantly re-forming around a moving focal point, and no one player just tanks the whole fight.

Terrain accretes throughout: every Judgment drops real anvils that stay on the floor as permanent
cover and permanent obstruction.

### 1.3 Phases

**P1 — The Court** (100–70% HP, exits also requires: first full Challenger rotation completed)

- *What changes:* baseline duel established. Three combos taught, one at a time, in a fixed order
  first cycle then shuffled.
- *New mechanic:* Challenger mark + court knights + Chains.
- *Strategy:* learn tells; non-challengers clear knights before chains land.
- *Punishes:* everyone piling onto the boss (only the Challenger's hits count), and ignoring adds.

**P2 — Regicide** (70–40%, exit also requires: all 3 Crown Shards seated on the throne)

- *What changes:* the King's crown physically shatters — three **real dropped items** land at three
  points of the arena. While any shard is uncollected, the King *reflects* damage back at his
  attacker. Players must pick a shard up (carrying = Slowness, no sprint, visibly held in hand) and
  place it on the throne block while the King actively hunts whoever holds one.
- *New mechanic:* item-carry escort under pursuit; hand-offs are legal and encouraged (throw the item
  to a teammate — real item toss).
- *Strategy:* fight becomes kiting + relay. The Challenger's job becomes *pulling the King away* from
  carriers instead of dueling him.
- *Punishes:* tunnel-visioning DPS (reflect), and one player trying to solo the relay.

**P3 — The Broken Oath** (40–15%, exit also requires: bell rung at least twice)

- *What changes:* he abandons the duel — no more Challenger, he swings at everyone, faster, and the
  ceiling starts failing. **Judgment** anvil barrages become continuous. But he now has an exposed
  spine: only hits landed from **behind his facing arc** deal full damage.
- *New mechanic:* facing-relative damage + the throne **Bell**. Ringing the bell (real bell block,
  real ring) staggers him for a fixed window and re-orients him, letting the group get behind.
- *Strategy:* the group must coordinate a rotation — someone tanks his front, someone rings, everyone
  else swings from behind. Reward: positioning literacy.
- *Punishes:* face-tanking (front hits are ~0), camping one spot (anvils will bury it).

**P4 — Last Stand** (<15% HP)

- *What changes:* the throne room floor is largely gone/blocked by anvils. He alternates between a
  wide sweep that must be jumped over anvil cover and an **Execution**: he marks the lowest-HP player
  and charges them with a real long wind-up. The charge can be **body-blocked** — any other player
  standing directly between him and the target takes the hit instead, and it doesn't one-shot them.
- *New mechanic:* intercept/body-block. Solo substitute: the marked player can break line by putting
  a standing anvil column between themselves and him — the charge collides and staggers him.
- *Strategy:* peak coordination; the terrain the fight created is now the tool that saves you.
- *Punishes:* letting anyone sit at low HP; a group that never learned to use cover.

### 1.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Challenger Mark** | phase start / on rotation event | a real **chain** stretches King→Challenger, plus a glowing outline on that player | be the Challenger and duel; others disengage | non-challenger hits deal ~10%; King gains a small heal if the group ignores the duel for too long | mark count stays 1 at any size; rotation just cycles faster with more players |
| **Overhead Cleave** | duel rotation | King rears sword high, arm raised pose, floor cracks in a line ahead | step back out of the line | heavy hit + real **crater** (floor breaks under you) | line length fixed; unchanged |
| **Ring Sweep** | duel rotation | sword drops low, sweeping dust ring on floor | jump, or step *inside* his guard | knockback into the arena edge + light damage | radius fixed |
| **Thrust** | duel rotation | sword pulled back, single narrow line telegraph | strafe sideways | hardest single hit + brief bleed (no-regen) | unchanged |
| **Riposte window** | 20 ticks after any combo ends | King's sword visibly lowered, no guard particles | hit him here for a damage spike + pass the Challenger role | miss = no punishment, just no reward | unchanged |
| **Court Knights** | P1 and on a cycle after | real armoured skeletons walk in from arena edge with actual iron/netherite gear | kill them; they are killable but hit hard | after ~15s each surviving knight throws a **Chain** at the Challenger (real chain blocks placed, rooting them) — Challenger dies to the duel if chained | knight count = 2 + 1 per player above 1, capped |
| **Chains** | knight survives too long | chain blocks physically link player to floor, visible and audible | **break the chain blocks** (any player, any tool) to free | rooted player cannot dodge combos | more players = faster breaking, so more knights are allowed |
| **Crown Shards** | P2 start | crown physically flies apart, 3 real items land with sound + item-glow | pick up, carry (slowed), throw to teammates, seat on throne | while any shard is loose, the King reflects a share of damage taken | 3 shards at all sizes; solo = King's pursuit speed reduced and reflect is lower (see §1.5) |
| **Judgment (anvils)** | P3 onward, on interval | ceiling cracks + a red danger ring on the floor ~1.5s before each anvil | walk out of the ring | anvil hit is near-lethal and *lands as a real anvil block* — permanently blocking that tile | anvil count per volley = 2 + players; spread across marked players |
| **The Bell** | available all fight, becomes mandatory P3 | real bell block on the throne dais, audible ring | hit/ring it to stagger + re-orient the King | not ringing = the group can never get behind him in P3 | one bell; with more players, one person can be dedicated to it — solo, the bell has a longer stagger to compensate for the trip |
| **Execution charge** | P4, on lowest-HP player | he freezes, points, long audible wind-up, straight-line ground scar | body-block, or break line-of-sight with an anvil column | target takes a huge hit | target selection is always exactly 1 |

### 1.5 Multiplayer

- **1 player:** Challenger mark is permanently on you — the duel *is* the fight, and knights spawn at
  the floor of the range (2) so you can clear them between combos. Crown Shards: reflect is reduced
  and the King's pursuit speed drops while you carry, so a solo relay is possible with good kiting.
  Bell stagger duration extended. P4 Execution is solved with anvil cover instead of body-blocking.
- **2 players:** the intended "read": one duels, one solves. Challenger rotation on riposte means the
  roles genuinely swap rather than one player being the permanent tank. Shard relay uses hand-offs.
- **3–5 players:** knight count and anvil count scale up so the non-duel half of the group has real
  work. Reflect in P2 punishes the "everyone just DPS" default hard, which is exactly the habit this
  boss exists to break. Challenger rotation cycles fast, so the whole group learns the combos rather
  than one designated tank.

### 1.6 Anti-cheese

- **Face-tanking:** P3's facing-relative damage means standing in front is literally near-zero DPS.
- **Ignoring mechanics:** ignoring knights → chained Challenger → the duel kills you. Ignoring shards
  → reflect kills your damage dealers.
- **Burst-skipping:** P2 cannot end until all three shards are seated; P3 cannot end until the bell
  has been used. Health thresholds alone do not advance phases.
- **Camping one spot:** Judgment permanently converts camped tiles into anvil blocks. Camp long
  enough and you have walled yourself in.
- **Ranged cheesing:** shard carrying, bell ringing, chain-breaking and body-blocking are all
  melee-range physical acts. A pure-ranged group stalls at P2.
- **Infinite healing:** Thrust applies a no-regen bleed; the Execution hit scales off max HP, not a
  flat number, so overhealing doesn't trivialise it.

### 1.7 Difficulty

- **Mechanical:** Medium-High — role rotation and facing awareness are the hard parts.
- **Damage:** Medium — punishing but rarely one-shot; deaths come from being chained or buried.
- **Learning curve:** Medium — three combos and a bell are quick to learn, the rotation discipline is
  what takes a few attempts. Very legible: every death traces to one identifiable mistake.

### 1.8 Implementation difficulty

**Medium.** Needs: per-attacker damage filtering (already exists via `PhaseMechanic.filterDamage`),
facing-arc damage check, carried-item tracking, real dropped-item entities with cleanup, real
placeable/breakable chain blocks with a player-break listener, falling-anvil physics with landed
block persistence, a bell interaction listener, and body-block line checks. All of these are
one-off but ordinary Bukkit — no novel systems.

---

## 2. The Frost Queen

### 2.1 Identity

- **Theme:** a shrinking, freezing arena that fights you alongside her.
- **Fantasy:** cold as an enemy — you're not dodging a spell, you're losing the ground and losing
  body heat.
- **Unique:** the roster's **traversal-physics** boss. The floor becomes real blue ice: you actually
  slide. Boats work. Powder snow actually traps. Leather boots actually matter. She is the boss where
  your *movement itself* stops being reliable.

### 2.2 Core gameplay loop

Two clocks run constantly: **Chill** (a per-player meter that only goes down near heat) and
**floor integrity** (usable ground shrinks over the fight). Players cycle between pressuring the
Queen and returning to campfires to burn off Chill, while the ice floor makes every approach a
momentum problem — you cannot stop on a dime, so you must commit to routes early.

She actively denies heat: she snuffs campfires, drops powder snow between you and them, and encases
players in real ice. The whole encounter is a heat-economy puzzle wrapped around a movement puzzle.

### 2.3 Phases

**P1 — First Frost** (100–70%, exit also requires: at least one Frozen Prison broken)

- *What changes:* baseline. The floor progressively converts to packed ice then blue ice under her
  footsteps — the slick region grows from her outward.
- *New mechanic:* Chill meter + campfires + Ice Lance volleys (real projectiles, dodgeable, they
  freeze the block they land on).
- *Strategy:* learn to fight on ice; learn the campfire rotation.
- *Punishes:* standing still to trade hits (Chill climbs fastest when you're near her).

**P2 — The Shattering** (70–40%, exit also requires: survive one full Avalanche cycle)

- *What changes:* the ceiling starts dropping real **packed-ice and icicle falling blocks**, which
  *break the floor where they land*. Arena develops holes. Combined with sliding, the fight becomes
  route-planning at speed.
- *New mechanic:* Avalanche (falling-block barrage in a moving band across the arena) and Powder Snow
  pits filling the holes — falling in freezes you fast unless you have leather boots.
- *Strategy:* group must maintain a mental map of safe ground; boats parked on the ice become a
  legitimate escape tool.
- *Punishes:* camping, memorised static routes, and ignoring the leather-boots counter.

**P3 — Heart of Winter** (40–15%, exit requires: Heart destroyed — HP threshold alone will not pass)

- *What changes:* she seals herself in a real ice shell and drops the **Frozen Heart** — a physical
  ice structure at arena centre that is immune to weapons but melts to *fire*. Players must carry
  fire to it: pick up a **torch/flint & steel/blaze rod** dropped by her Frostbound adds, and bring it
  to the Heart. Carrying fire suppresses your Chill but makes you the target of every Ice Lance.
- *New mechanic:* fire-carry escort, inverted — the carrier is safe from cold and unsafe from her.
  She remains fully active, chasing carriers and re-freezing the Heart if you're too slow.
- *Strategy:* complete role inversion from P2. The healthiest player should carry, not the safest.
- *Punishes:* passive play — the Heart re-freezes on a timer, so hesitation resets progress.

> **Roster note (batch 4 audit):** this is the **one canonical use of the "boss is gated while you
> deal with an objective" archetype in the entire roster.** It earns it because the Heart is immune to
> weapons outright and is solved by *delivering fire*, so it is a transport objective rather than a
> disguised second health bar. Every other boss that had drifted into this pattern has been converted
> to a damage-rule or progress-loss gate instead. Do not add a sixth.

**P4 — Absolute Zero** (<15%)

- *What changes:* the entire arena freezes in pulses. Between pulses, **only the radius of a lit
  campfire is survivable** — and she snuffs one campfire per pulse. The safe area shrinks
  campfire by campfire.
- *New mechanic:* pulse survival + campfire relighting under pressure, on a floor that is now mostly
  holes and blue ice.
- *Strategy:* the endgame is a sprint economy: relight, reposition, burst her in the gaps.
- *Punishes:* everything you failed to learn earlier, at once. Legibly.

### 2.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Chill** | passive, faster near her, faster on ice | frost vignette + your breath; visible frost overlay stacks | stand near a lit **campfire**, hold a fire source, or leave the ice | at max Chill you are **frozen solid** — encased, immobile, taking damage | per-player meter; identical at all sizes |
| **Frozen Prison** | on a marked player at high Chill | ice audibly forms upward around their feet, 1s of warning | allies **break the ice blocks** (real block breaking, pickaxe fast) | frozen player is helpless and bleeding HP until freed | solo: shell has lower hardness so self-escape is possible but costs ~4s |
| **Ice Lance** | on interval | she raises an arm, a real icicle projectile spawns and tracks briefly before firing straight | sidestep; use pillars/anvil-like ice cover | moderate damage + a big Chill spike; the impact tile becomes ice | volley size = 1 per player, targets distinct players |
| **Slick Floor** | passive, grows from her position | floor visibly converts packed ice → blue ice | commit to routes early, use boats, stay on unconverted ground | overshooting into hazards, powder snow, or holes | area growth rate fixed; more players = more contested safe ground |
| **Powder Snow** | fills broken floor from P2 | visibly white, distinctly different from floor | avoid, or wear **leather boots** — provided in the arena (§0.3) — to walk on it | you sink in and freeze fast; climbing out costs seconds you don't have | pit count scales with player count |
| **Avalanche** | P2 cycle | ceiling cracks along a visible band, ~2s before drop | move perpendicular to the band; it sweeps predictably | crushing damage + the floor beneath breaks into a lethal-damage pit (§0.3) | band width fixed; band count = 1 + 1 per 2 players |
| **Glacier Spikes** | on clustered players | ground frosts in a tight ring under a group | spread out before it lands | ice pillars erupt, launching and separating the group | triggers on cluster size ≥ 2 — solo version triggers on standing still too long |
| **Campfires** | arena furniture, all fight | real lit campfires, visible flame and smoke | stand near to burn Chill; relight snuffed ones | no heat = the Chill clock kills you | count fixed at 4; she snuffs faster with more players so the economy stays tight |
| **Frozen Heart** | P3 | a real ice structure at centre, visibly re-freezing over time | bring fire to it; weapons do nothing | Heart fully re-freezes, phase resets its progress | Heart HP scales; fire-carry slots = 1 solo, up to 3 in a group |
| **Absolute Zero pulse** | P4 interval | the whole arena audibly cracks + a visible frost front sweeps outward | be inside a lit campfire radius | very heavy damage + instant near-max Chill | pulse damage constant; campfire count is the shared resource |

### 2.5 Multiplayer

- **1 player:** Frozen Prison is self-escapable (slower). Fire-carry is one slot and the Heart's
  re-freeze rate is reduced so a solo round trip is viable. Glacier Spikes retrigger on standing
  still rather than on clustering. Avalanche runs one band. Fully playable, same shape.
- **2 players:** the natural read — one breaks prisons and relights, one pressures. Prison-breaking
  becomes an actual social contract: ignore your partner and they die.
- **3–5 players:** more Avalanche bands, more powder-snow pits, faster campfire snuffing — the arena
  gets meaningfully more hostile rather than the Queen getting more numbers. Clustering punishment
  (Glacier Spikes) keeps groups spread, which keeps the Chill economy individual.

### 2.6 Anti-cheese

- **Face-tanking:** Chill ignores armour entirely and is not healable. Standing next to her is the
  single fastest way to fill the meter.
- **Ignoring mechanics:** the Heart cannot be damaged by weapons at all. There is no DPS answer.
- **Burst-skipping:** P3 exits on the Heart, not on HP. You cannot delete her out of the phase.
- **Camping:** the floor under a camped spot becomes blue ice then gets shattered by Avalanche.
  Campfires are the only camp-worthy spots and she removes them one at a time.
- **Ranged cheesing:** prison-breaking, fire-carrying and campfire relighting are melee-proximity
  acts, and the ice floor makes ranged kiting genuinely difficult (you slide out of your own range).
- **Infinite healing:** Chill and Frozen Prison are not damage, so healing does not counter them.

### 2.7 Difficulty

- **Mechanical:** High — sliding physics plus a shrinking floor plus a resource clock is a lot of
  simultaneous state.
- **Damage:** Medium — she rarely bursts you down; the arena does it.
- **Learning curve:** Medium-High. The first attempt will feel chaotic; the second will feel fair,
  because every death is traceable ("I slid into powder snow", "I let Chill cap").

### 2.8 Implementation difficulty

**Hard.** Needs: bulk block conversion with performance batching (ice spread), falling blocks that
break floor on landing, powder-snow + leather-boots interaction (vanilla, but must be respected not
overridden), a per-player Chill meter with UI, ice-encasement create/break with a player-break
listener, campfire lit/snuffed state tracking, an item-carry state, and a destructible multi-block
structure (the Heart). The block-churn volume is the main risk — this boss is the one that will
prove or break the terrain engine.

---

## 3. The Storm Tyrant

### 3.1 Identity

- **Theme:** an open-sky arena where the storm owns the vertical axis.
- **Fantasy:** being physically thrown around — you don't walk to safety, you get blown there.
- **Unique:** the roster's **verticality + knockback** boss, and the only one where the
  boss's own attacks are your mobility. Built on real wind charges, real Breezes, real lightning
  rods and real water conduction. Being airborne is normal, not a failure state.

### 3.2 Core gameplay loop

Every player carries a **Static Charge** that rises constantly and rises much faster when you're
standing in water or next to another charged player. You dump charge by touching a **lightning rod**
— but rods are limited, they're placed away from the boss, and discharging takes a moment during
which you can't fight. So the loop is: pressure → charge climbs → break off → discharge → return.

Layered on top: the Tyrant floods thin water sheets across the floor, so *where* the water is
determines where you can safely stand when a bolt lands. And Breeze adds plus his own wind charges
constantly reposition you against your will — the skill is landing where you intended.

### 3.3 Phases

**P1 — Static Build** (100–70%, exit also requires: every player has discharged at least once)

- *What changes:* baseline. Charge meter, four lightning rods, chain lightning between close players.
- *New mechanic:* Static Charge + rods + Chain Lightning (arcs between players within ~5 blocks).
- *Strategy:* stay spread, rotate to rods.
- *Punishes:* stacking up, and ignoring the meter.

**P2 — Floodplain** (70–45%, exit also requires: survive one full Thunderstrike cycle without a rod
destroyed)

- *What changes:* he floods **real water** across sections of the floor. Water conducts: a bolt
  landing anywhere in a connected water body damages everyone standing in it. The safe floor is now
  defined by water, not by distance.
- *New mechanic:* conduction zones + Thunderstrike (real lightning bolts, real fire ignition).
- *Strategy:* the group reads water shapes and fights on dry islands. Rods can be *destroyed* by
  the Tyrant, so protecting rods becomes a job.
- *Punishes:* not looking at the ground; treating water as harmless.

**P3 — Eye of the Storm** (45–15%, exit also requires: two Storm Pylons destroyed)

- *What changes:* he ascends and is fed by four **Storm Pylons** — physical structures at the arena
  edge, each protected by a Breeze. **He never becomes invulnerable.** Instead, while any pylon
  stands, he takes damage *only from players who are currently discharged* — Static Charge at or near
  zero. The rod rotation the whole boss is built around now directly gates the group's damage, so the
  fight never pauses; it just demands you fight clean. Destroying pylons removes the restriction
  permanently, one pylon at a time, making them strong-but-optional rather than a chore wall. All the
  while he strafes the arena with rolling lightning as a rotating safe corridor sweeps the floor.
- *New mechanic:* verticality as a puzzle. To reach a pylon you have to use **wind charges** — his,
  or ones dropped by Breezes — to launch yourself, or build up temporary **scaffolding** towers that
  he blows down.
- *Strategy:* an unmistakable phase pivot: the boss stops being a target and becomes weather.
- *Punishes:* melee-only groups with no answer to height; pure DPS strategies.

**P4 — Stormcall** (<15%)

- *What changes:* he lands, permanently charged. Every attack now chains, and rods burn out after one
  use. The floor is scarred with fire from earlier bolts. Charge climbs roughly twice as fast.
- *New mechanic:* rod scarcity — the group must sequence who discharges and when.
- *Strategy:* a real coordination endgame that is about *turn order*, not damage.
- *Punishes:* selfish rod use.

### 3.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Static Charge** | passive; ×2 in water, ×2 near a charged ally | crackling arcs on your model, escalating audible hum | touch a **lightning rod** and hold ~1.5s | at max, you become a lightning rod yourself — bolt strikes you, chains to anyone near | per player; more players = more chain risk, so spreading matters more |
| **Chain Lightning** | on interval | he points, arcs visibly reach toward the nearest cluster | spread to > 5 blocks apart | damage multiplies per player in the chain | intentionally scales *up* with clustering — this is the anti-stack tax |
| **Wind Charge volley** | on interval | he winds his arm back; real wind charge projectiles fly with visible trails | dodge, or **let it hit you deliberately** to travel somewhere fast | knocked into water / off a ledge / into fire | volley size = players, one per target |
| **Breeze adds** | P2 onward | real Breeze mobs drop in with sound | kill them; they drop wind charges you can use | constant unwanted repositioning during precise mechanics | count = 1 + 1 per 2 players |
| **Thunderstrike** | P2 cycle | sky darkens over a marked area, a rod-like beam descends ~2s early | leave the marked area and leave any connected water | heavy damage, real fire started, conducts through water | strike count scales; marks target distinct players |
| **Flooding** | P2 onward | water visibly pours and spreads across the floor | fight on dry ground; break/avoid connected pools | any bolt hitting the pool hits everyone in it | flood area fixed; more players = harder to all fit on dry ground |
| **Lightning Rods** | arena furniture | real rods, visibly sparking when they hold charge | use to discharge; defend them from him | destroyed rods do not respawn until next phase | 4 rods at all sizes — deliberately scarce for groups |
| **Gale Push** | on melee-range players | he plants his feet, a visible pressure ring forms | brace behind a block, or ride it intentionally | flung across the arena, often into water or off ground | radius fixed |
| **Storm Pylons** | P3 | four physical lit structures at arena edge, beams feeding him | reach and destroy — requires height (wind charges or scaffolding) | while pylons stand he takes damage **only from discharged players**; ignoring them means a group that never rotates rods deals nothing | pylon HP scales; solo gets 2 pylons instead of 4 |
| **Rolling Barrage** | P3 continuous | a visible advancing wall of strikes with a clear gap | stay in the moving safe corridor | repeated heavy hits | corridor width fixed; speed constant |
| **Stormcall** | P4 | permanent electrical aura, ambient bolts | rod turn-order discipline | wipe by cascading chains | rods burn out after one use each — scarcity, not damage, is the scaling |

### 3.5 Multiplayer

- **1 player:** Chain Lightning has no allies to chain to, so instead it arcs from **charged water
  pools** — same rule (don't be near a conductor), same lesson, solo-legible. Two pylons instead of
  four in P3, and one Breeze guards each. Rods stay at 4, which makes solo P4 survivable.
- **2 players:** the clustering tax first becomes real. Two people can trivially over-cluster on the
  dry ground and delete themselves — this is the boss's core teaching moment.
- **3–5 players:** rod scarcity (still 4) plus faster charge means the group *must* sequence
  discharges. Chain Lightning's per-player multiplier makes a stacked 5-player group evaporate.
  Nothing about the fight gets numerically harder; the coordination cost does.

### 3.6 Anti-cheese

- **Face-tanking:** Static Charge ignores armour and is uncurable by healing; standing in melee
  while ignoring rods is a fixed-length countdown to death.
- **Ignoring mechanics:** in P3 only discharged players deal damage, so a group that never rotates
  rods has no DPS path at all — without the boss ever standing there invulnerable.
- **Burst-skipping:** P3 exits on two pylons, P1 on universal discharge, P2 on a survived cycle.
- **Camping:** flooding reaches every static position eventually, and Thunderstrike marks camped
  ground preferentially.
- **Ranged cheesing:** rods and pylons are physical objects at specific places; and Gale Push +
  Breezes make holding a fixed sniping perch nearly impossible. Conversely, melee-only groups fail
  P3 — both bands are taxed.
- **Infinite healing:** charge, knockback and drowning/fire are not damage-over-time you can outheal.

### 3.7 Difficulty

- **Mechanical:** High — the vertical axis plus involuntary movement is the hardest control problem
  in batch 1.
- **Damage:** High in bursts (chains multiply) but almost entirely self-inflicted.
- **Learning curve:** High initially, then it drops off sharply once the group internalises "spread
  out, watch the water, rotate rods". Very satisfying mastery curve.

### 3.8 Implementation difficulty

**Hard.** Needs: real wind-charge spawning and player-usable pickup, Breeze add integration, real
water body placement and connectivity checks (for conduction), lightning bolts with fire and
chain-target resolution, a per-player charge meter, destructible rods and pylons, an aerial boss
state with an untargetable window, a rotating corridor hazard (exists in the codebase), and
player-usable scaffolding interplay. The water-conduction connectivity check is the novel piece.

---

## 4. The Plague Warden

### 4.1 Identity

- **Theme:** rot and sculk consuming an arena, and each other's bodies as the vector.
- **Fantasy:** a slow, horrible attrition fight where *you are the danger to your allies*.
- **Unique:** the roster's **anti-healing, anti-noise** boss. Infection spreads player-to-player, so
  positioning is about *distance from friends*, not distance from the boss. And in P3 the arena is
  sculk: real sculk sensors and shriekers mean **moving loudly is punished**, so the fight briefly
  becomes a stealth encounter. Nothing else in the roster does that.

### 4.2 Core gameplay loop

Every player carries **Infection**, rising passively near the Warden and much faster near infected
allies and on corrupted ground. Cleansing means standing in real **fire/campfire** or catching the
burst from a killed Bloated Carrier — both of which require going somewhere specific and dangerous.

The Warden fights with real objects: thrown splash potions, lingering `AreaEffectCloud`s, cobweb
growths, mud and soul-sand ground conversion, infested blocks that break into silverfish. Almost none
of his damage is instant; nearly all of it is a clock you must service.

Critically: **healing above a cap converts to Infection.** This is the boss that exists to break
"just heal through it".

### 4.3 Phases

**P1 — Contagion** (100–70%, exit also requires: no player above 50% Infection at threshold)

- *What changes:* baseline. Infection spreads, cleansing pyres are lit, Carriers walk in.
- *New mechanic:* Infection meter + player-to-player transmission + Bloated Carrier adds that burst
  into a real lingering cloud when killed — kill them *away* from the group, and stand in the burst
  only if you want the cleanse trade.
- *Strategy:* deliberate spacing between allies; managed add killing.
- *Punishes:* huddling, and killing Carriers on top of your team.

**P2 — The Bloom** (70–45%, exit also requires: 3 Spore Nodes destroyed)

- *What changes:* the ground physically corrupts — real mud, soul sand and fungal growth spreading
  outward from **Spore Nodes**, plus cobwebs strangling the lanes. Corrupted ground doubles Infection
  gain and slows you.
- *New mechanic:* terrain denial you must actively fight — destroy nodes to stop spread, cut cobwebs
  with a sword (real vanilla interaction), and preserve clean ground.
- *Strategy:* the group splits between boss pressure and node clearing; clean floor is a resource.
- *Punishes:* passivity — do nothing and the entire arena becomes hostile ground.

**P3 — Host** (45–20%, exit also requires: the Host body broken open)

- *What changes:* the Warden burrows into a massive **Host** — a physical multi-block growth at
  arena centre studded with real **sculk sensors and shriekers**. **He is not invulnerable.** Instead,
  while he is inside the Host he takes damage *only from players currently below 25% Infection* — the
  rot inside the growth ignores anyone already rotting. That forces a live role rotation off his own
  core meter: clean players push in and deal damage, infected players peel off to the pyres and take
  over the noise-discipline and add-clearing work, then swap. Breaking the Host open removes the
  restriction, but the sensors react to sprinting, jumping and hitting: too much noise triggers
  shriekers, which apply Darkness and summon a wave of adds.
- *New mechanic:* **noise discipline** — approach crouched, strike in measured windows, and use
  thrown items as decoy noise on the far side (real vanilla sculk behaviour).
- *Strategy:* complete tonal pivot — the fight goes quiet and tense. Frantic play is actively worse
  than careful play. This is the batch's most memorable moment.
- *Punishes:* button-mashing, uncontrolled DPS, and pure aggression.

**P4 — Pandemic** (<20%)

- *What changes:* he emerges, Infection rises on everyone regardless of position, and the pyres burn
  out one by one. Healing is capped hard.
- *New mechanic:* a genuine race — cleanse efficiency versus the clock, with the boss actively
  contesting the last pyres.
- *Strategy:* burn cooldowns, rotate cleanses, finish him before the last pyre dies.
- *Punishes:* having wasted pyre charges earlier in the fight.

### 4.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Infection** | passive; ×2 near infected allies, ×2 on corrupted ground | your model visibly rots, spores drift off you, audible wet breathing | stand in **fire/campfire**, or catch a Carrier cleanse burst | at 100 you **rupture**: heavy self-damage + a large infection spike to everyone near you | per player; transmission makes group size the difficulty knob |
| **Necrotic Rot** | passive, all fight | green tint on healing numbers/effect | keep healing under the cap; use mitigation instead of raw healing | healing above cap converts directly into Infection | cap is per-player, unchanged by size |
| **Splash flasks** | on interval | he physically winds up and throws a real potion, visible arc | move out of the landing area; the arc is readable | lingering cloud denies that ground for ~10s | throw count = 1 per 2 players |
| **Miasma clouds** | on death of a Carrier, and on interval | real `AreaEffectCloud`, unmistakable volume | stay out — or step in on purpose if you need the Carrier cleanse | steady Infection gain | cloud count scales with adds |
| **Bloated Carriers** | P1 onward | fat, slow, audibly gurgling real mobs, visibly swelling before burst | kill them away from the group; use the burst deliberately | killed on top of the group = mass infection | count = 2 + 1 per player |
| **Cobwebs** | P2 onward | real cobweb blocks growing in lanes | cut with a sword (vanilla), or route around | stuck in place during a splash flask = dead | web density scales with player count |
| **Corrupted ground** | P2, spreads from nodes | mud / soul sand / fungal blocks visibly creeping outward | destroy Spore Nodes to stop the source | slow + doubled Infection gain over most of the arena | 3 nodes solo, up to 5 in a group |
| **Spore Nodes** | P2 | physical growths, pulsing, audibly breathing | destroy them (weapons work) | corruption never stops spreading, P2 never ends | node HP scales |
| **Sculk sensors / shriekers** | P3 | real sculk blocks covering the Host, visibly pulsing when they hear you | **crouch-walk**, avoid sprint/jump, throw items to misdirect | shriek = Darkness + an immediate add wave | shrieker count fixed; more players = more accidental noise, which is the intended group tax |
| **The Host** | P3 | a real multi-block mass at centre, breakable layer by layer | break it open under noise discipline | while it stands he takes damage **only from players under 25% Infection**, forcing a clean/infected role rotation | Host HP scales; solo has fewer sensors, and the 25% rule makes solo cleansing uptime the real gate |
| **Silverfish infestation** | on breaking Host / corrupted blocks | infested block texture cracks distinctively | break carefully, or kill the swarm fast | swarms chew through you while you're mid-mechanic | swarm size scales |
| **Pyres** | arena furniture | real lit campfires with limited fuel, visibly dimming as they're used | use sparingly; ration across the fight | no cleanse source in P4 | fixed count; the shared-resource tension is the group scaling |

### 4.5 Multiplayer

- **1 player:** transmission has no allies to bounce off, so Infection instead ramps from **the boss's
  proximity and corrupted ground** — same meter, same cures, different source. Fewer sensors on the
  Host and fewer Spore Nodes. Solo is the *easiest* version of the infection economy and the *hardest*
  version of the node/Host workload — a deliberate trade.
- **2 players:** transmission becomes real and immediately teaches spacing. Carrier-killing placement
  matters. One can hold noise discipline while the other decoys in P3.
- **3–5 players:** the infection web is the whole difficulty. A 5-player group that stands together
  will chain-rupture and wipe with no boss ability involved. Pyre rationing becomes a genuine group
  negotiation, and P3's noise floor rises just because there are more feet — groups must explicitly
  assign who approaches.

### 4.6 Anti-cheese

- **Face-tanking:** Infection ignores armour, and Necrotic Rot means the healer trying to brute-force
  it is actively making it worse.
- **Ignoring mechanics:** in P3 only players under 25% Infection can hurt him at all, so a group that
  never cleanses deals nothing — enforced as a rule about how you're playing, not as an immunity wall.
  Corruption likewise cannot be outrun without killing nodes.
- **Burst-skipping:** P1 gates on group Infection state, P2 on nodes, P3 on the Host. Damage alone
  never advances.
- **Camping:** corrupted ground spreads to any camped tile and doubles the clock there.
- **Ranged cheesing:** sculk sensors punish *ranged* players less, so P3 deliberately places the Host
  behind cobweb-choked, mud-slowed ground — you have to walk in. Additionally the Host's sensors are
  only breakable in melee.
- **Infinite healing:** structurally impossible — this is the boss built specifically to close that
  loophole, and it does it by making healing a cost rather than by nerfing it.

### 4.7 Difficulty

- **Mechanical:** Medium-High — the individual actions are simple; the discipline is not.
- **Damage:** Low-Medium moment to moment, High cumulatively. Almost no burst; almost no forgiveness.
- **Learning curve:** Medium. Infection is instantly legible ("I stood next to Bob"), P3's noise rule
  takes one shriek to understand and a whole attempt to execute.

### 4.8 Implementation difficulty

**Medium-Hard.** Needs: per-player infection meter with transmission proximity checks, a healing
interception hook (the Rot cap), real `AreaEffectCloud` and `ThrownPotion` usage, bulk ground
conversion (shared with Frost Queen's engine), cobweb placement, real sculk sensor/shrieker blocks
wired to a custom trigger (vanilla sensors work but need boss-owned response logic), infested blocks
/ silverfish spawning, a destructible multi-block Host, and campfire fuel state. The sculk phase is
the novel work; most of the rest reuses the terrain engine built for the Queen.

---

## 5. The Void Sovereign

### 5.1 Identity

- **Theme:** an End-touched arena being unmade beneath you.
- **Fantasy:** space itself is unreliable — you get moved, the floor stops existing, and the boss
  attacks where you *were*.
- **Unique:** the roster's **floor-loss and forced-relocation** boss. The arena permanently shrinks
  because he physically deletes it. Built on real end crystals, real shulkers, real ender pearls,
  real chorus fruit and real pistons. He is the boss that makes standing still literally impossible.

### 5.2 Core gameplay loop

Two pressures, both about space. **Void Echoes**: every few seconds he strikes the spot each player
occupied ~3 seconds ago — so continuous movement isn't a suggestion, it's the baseline. **Rifts**:
he tears real holes in the floor that never come back, so the usable arena shrinks monotonically and
the group's routes must keep re-planning.

On top of that he constantly *relocates you against your will*: ender pearls that swap your position
with his, shulker bullets that levitate you, singularity pulls, and pistons that shove you off ledges.
Counterplay is real items — chorus fruit dropped in the arena gives you an escape teleport, and
pearls you catch can be re-thrown.

### 5.3 Phases

**P1 — Echoes** (100–72%, exit also requires: survive one full Echo cycle with no player struck)

- *What changes:* baseline. Echo strikes, blink combos, arcane volleys.
- *New mechanic:* Void Echoes — delayed strikes on your recent positions, visibly marked when they
  arm so the rule is learnable within seconds.
- *Strategy:* keep moving, never backtrack into your own trail.
- *Punishes:* standing still; also punishes panic-circling into your own old positions.

**P2 — Collapse** (72–45%, exit also requires: survive one Singularity)

- *What changes:* **Void Rifts** open — he deletes real floor blocks in growing patches for the rest
  of the fight. The arena genuinely disappears out from under the group (and is restored afterwards
  by the arena ledger, §0.3). Shulkers arrive on the remaining ledges.
- *New mechanic:* rifts are **lethal-damage pits**, not drops — falling in hurts enormously and spits
  you back onto solid floor, so the cost is your health bar, not your participation. **Levitation**
  from shulker bullets becomes both a threat (drift over a rift) and a tool (cross one).
- *Strategy:* the group has to consciously preserve a fighting platform and manage vertical drift.
- *Punishes:* fighting in one corner; ignoring shulkers.

**P3 — Between** (45–18%, exit requires: the real Sovereign struck 3 times)

- *What changes:* he splits into three identical phantoms. **Nothing is invulnerable** — the phase is
  pure identification. Damage only counts on the real one, told apart by a physical tell rather than
  text: the real one *casts a shadow of falling blocks beneath itself*, the phantoms don't. Strike a
  phantom and it counter-blinks onto you.
- *New mechanic:* identification under pressure — and **real end crystals** on pillars as an
  *optional* objective. Destroying a crystal shortens the interval between tells (making the real one
  easier to find) but explodes for real and blows away more of your remaining floor.
- *Strategy:* a genuine risk/reward dilemma rather than a chore — buy clarity with arena, or fight
  blind and keep your footing.
- *Punishes:* attacking blindly; and greedily cracking every crystal without planning where you'll
  stand afterwards.

**P4 — The Unmaking** (<18%)

- *What changes:* most of the arena is gone. What remains is a small platform network connected by
  ledges, with pistons periodically shoving. He teleports constantly and pulls.
- *New mechanic:* pure spatial endgame — chorus fruit and caught pearls are your survival kit, and
  they're finite.
- *Strategy:* the fight ends on a knife-edge of footing, using the tools you didn't waste earlier.
- *Punishes:* every rift you let open carelessly in P2 and P3.

### 5.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Void Echoes** | continuous from P1 | a visible dark marker physically sits on each recent position for ~1s before it detonates | keep moving forward; never retrace | sharp damage + a **Void Echo stack** (armour-ignoring, non-healable; at 5 you're briefly banished) | one trail per player; identical solo and grouped |
| **Blink Strike** | on interval | he vanishes with a real teleport sound; a marker lands where he'll reappear ~0.5s early | step off the marker before he lands | heavy melee combo on arrival | one target at a time |
| **Rift** | P2 onward, on interval | floor visibly cracks and darkens 2s before it drops out | leave the marked tiles | the tile becomes a **lethal-damage pit** — fall in for a huge hit, then get ejected back onto solid floor; the floor stays gone for the fight | rift count = 1 + 1 per 2 players — more players, faster arena loss |
| **Shulkers** | P2 onward | real shulkers physically attached to ledges and pillars | kill them, or use their levitation to cross rifts | drift over a rift and take the pit hit | count scales; they always sit near rift edges by design |
| **Ender pearls** | on interval | he holds a visibly glowing pearl, then throws a real projectile | dodge — or **catch it** (it drops as an item) and re-throw it later | you and he swap positions, often straight into a rift | one throw per cycle |
| **Singularity** | P2 cycle | the arena visibly pulls inward, items and loose blocks slide toward the centre | run away early, or anchor behind a placed block | dragged into the centre for a heavy AoE, or dragged into a rift | pull strength constant; more players = more collision chaos, which is the group tax |
| **Chorus fruit** | dropped by phantoms and shulkers | real item entities on the floor | pick up and eat for an emergency random teleport | without it, P4 footing mistakes are unrecoverable | drop rate constant — a shared, contested resource |
| **End crystals** | P3 | real end crystals with real beams to the phantoms | optional — destroy one to make the real Sovereign's tell fire more often | leaving them up means a harder read; breaking them costs you floor | crystal count = 2 solo, 4 grouped |
| **Phantom split** | P3 | three visually identical Sovereigns | find the one dropping real falling blocks beneath itself | hitting a phantom does nothing and it counter-blinks onto you | phantom count fixed at 3 |
| **Pistons** | P4 | real pistons on the platform edges, audible extend warning | step off the piston line | shoved off the platform | piston count scales with platform count |
| **Banish** | at 5 Void Echo stacks | the player is visibly pulled into a dark pocket | allies destroy the tether crystal that appears; **solo**: eat chorus fruit or break out by destroying the pocket wall | banished player is out of the fight and bleeding HP | tether is always breakable solo — never a hard 2-player requirement |

### 5.5 Multiplayer

- **1 player:** Banish is always self-solvable (chorus fruit or breaking the pocket). Two crystals
  instead of four in P3. Rift rate at the floor of its range so the solo arena survives long enough
  to finish. The Echo trail mechanic is identical at all sizes, which is what keeps the boss's
  identity intact solo.
- **2 players:** Banish rescue becomes the faster option, and Singularity becomes genuinely dangerous
  because two bodies collide while being dragged.
- **3–5 players:** rift rate scales, so a 5-player group **destroys their own arena much faster** —
  the group's own success is the pressure. That is the design intent: more players means less floor,
  not a bigger health bar. Chorus fruit stays a fixed, contested drop, so groups must decide who gets
  the escape tools.

### 5.6 Anti-cheese

- **Face-tanking:** Void Echo stacks ignore armour, cannot be healed, and end in Banish. Standing
  and trading is a fixed countdown.
- **Ignoring mechanics:** damage only registers on the real Sovereign, so a group that won't read the
  tell deals nothing — without anything ever being immune.
- **Burst-skipping:** P3 exits on three verified strikes on the real Sovereign, not on HP. P1 and P2
  gate on survived cycles.
- **Camping:** rifts preferentially open under stationary players. The camped tile literally ceases
  to exist.
- **Ranged cheesing:** the perimeter is the *first* thing to fall away, and shulkers plus pistons make
  the edges the most dangerous real estate. Crystals also require closing distance.
- **Infinite healing:** Banish and falling are not damage; no amount of healing addresses either.

### 5.7 Difficulty

- **Mechanical:** High — continuous movement plus permanent arena loss plus identification.
- **Damage:** Medium — most deaths are falls and Banish, not damage numbers.
- **Learning curve:** Medium-High. The Echo rule teaches itself in about ten seconds; the arena-budget
  discipline ("we're destroying our own floor") takes a full attempt to internalise, and is the most
  interesting thing to learn in batch 1.

### 5.8 Implementation difficulty

**Hard.** Needs: per-player position history for Echoes, floor deletion backed by the arena ledger,
the shared lethal-damage-pit + eject behaviour (§0.3), real end crystal entities with beam anchoring, shulker placement and
levitation interplay, real ender-pearl projectiles with catchable item drops, a physics-style pull for
Singularity, piston mechanisms, a decoy/phantom system (partially exists in the codebase), and a
banish sub-space. The riskiest piece is the arena-deletion budget — the fight must remain finishable
after the floor loss, at every group size.

---

## 6. Cross-batch notes

### 6.1 Shared systems this batch implies

These are the reusable pieces batch 1 will need, which batches 2–5 should be designed to reuse rather
than reinvent:

1. **Terrain engine + arena ledger** — batched block conversion, columns up, craters down, floor
   deletion, falling blocks that land as real blocks, all recorded and rolled back on fight end
   (§0.3). Includes the shared **lethal-damage pit** behaviour (heavy hit + eject to solid floor).
   Used by all five, and by nearly every later boss.
2. **Player meter framework** — Chill, Static Charge, Infection, Void Echo stacks are one system with
   four skins: armour-ignoring, non-healable, cured only by a physical act.
3. **Carry/escort state** — crown shards, fire sources, chorus fruit, caught pearls. Real items that
   change how the boss treats you.
4. **Physical objective props** — bell, campfires, lightning rods, pylons, spore nodes, end crystals,
   the Host. All "a thing in the world you must go touch or break".
5. **Phase-exit conditions beyond HP** — already supported by `readyToAdvance`; batch 1 uses it on
   every single phase, which is the main structural fix to burst-skipping.

### 6.2 Remaining roster for batches 2–5

Not designed yet, listed so the batch order is visible:

- **Batch 2 (elemental/heavy):** Inferno Warlord, Solar Colossus, Tide Leviathan, Dragon Elder,
  Necro Overlord.
- **Batch 3 (horror/anomaly):** Grafted Horror, Threefold Bane, Amalgamated Bulk, Hollow Choir,
  Weeping Colossus.
- **Batch 4:** Voidwyrm + any redesign carry-over.
- **Batch 5:** The Worldender (capstone, reuses the finished vocabularies).

### 6.3 Resolved (see §0.3)

1. **Arena restoration** — YES, ledger-backed rollback on fight end. Destruction can therefore be
   much more aggressive than the old permanent-grief policy allowed.
2. **Falling** — no fall deaths. Pits deal heavy damage and eject the player back onto solid floor.
3. **Item counterplay** — the arena supplies every item a mechanic depends on. Scarce and contested,
   never absent.
