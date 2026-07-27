# Boss Rework — Design Pass, Batch 4 of 5

**Status:** design only. No code. Continues batches
[1](2026-07-27-boss-rework-batch-1.md), [2](2026-07-27-boss-rework-batch-2.md),
[3](2026-07-27-boss-rework-batch-3.md). Batch 1 §0 philosophy and §0.3 rulings apply unchanged.

**Contents:** the Voidwyrm design, then a **cross-roster audit** of all 16 designed bosses.

The audit is the more important half of this document. It found one structural problem serious enough
that it should be fixed before any implementation starts — see §2.2.

---

## 1. The Voidwyrm

### 1.1 Identity

- **Theme (kept):** it spawns as a wyrmling barely bigger than a player and grows every phase, ending
  as an ancient, screen-filling thing. Size is the entire arc.
- **Signature (kept):** **False Ground** — parts of the floor are illusion, faintly marked the whole
  time and obvious only in the second before they go. Rewards studying the arena early rather than
  reacting fast late.
- **Unique:** the roster's **category-shifting** boss. It is not one fight that escalates; it is
  **four different fights** wearing one name. It starts as a *chase* — the only boss in the roster
  that flees from you — then becomes an ambusher, then a multi-segment serpent whose body is the
  terrain, then something you fight from the inside. Nothing else in the roster changes what kind of
  encounter it is more than once.

### 1.2 Core gameplay loop

Phase by phase the *question the fight asks* changes completely:

1. **Can you catch it?** A tiny, fast, evasive target that will not stand and fight. Players must cut
   it off, corner it, and trap it — using arena geometry, not damage.
2. **Can you predict it?** It burrows. Real tunnels open in the floor, and it strikes from below.
   Combined with False Ground, the floor becomes untrustworthy in two different ways at once.
3. **Can you read a body?** It surfaces as a long multi-segment serpent, coiling through the arena.
   Segments are cover, obstruction and target all at once, and only some segments are vulnerable.
4. **Can you fight from inside?** It swallows players into a real interior space, and the group is
   split between outside and inside until they cut their way out.

Throughout, False Ground means the floor itself is never fully trusted — a constant low-level tax on
positioning that never goes away and never escalates into something unfair.

### 1.3 Phases

**P1 — The Wyrmling** (100–78%, exit requires: cornered three times)

- *What changes:* it is small, extremely fast, and actively evasive. It will not engage. It darts,
  circles, and slips past players.
- *New mechanic:* **cornering** — it can only be damaged meaningfully when it has no escape line.
  Players cut off routes using arena geometry and placed blocks (arena-supplied, §0.3). Trap it
  against a wall or between bodies and it must fight.
- *Strategy:* a pursuit fight — genuinely novel, since every other boss in the roster comes to you.
- *Punishes:* passive play and standing in a group waiting for it. It will simply not come.

**P2 — The Burrower** (78–54%, exit requires: two surfacings punished)

- *What changes:* it goes underground. Real tunnels open in the floor. It travels beneath the arena
  and erupts under players.
- *New mechanic:* **tremor reading** — the ground visibly and audibly disturbs along its path a
  second or two before it surfaces, so its position underground is always knowable if you watch. And
  the tunnel mouths it leaves are permanent floor damage, interacting with False Ground.
- *Strategy:* the group tracks a boss it cannot see and meets it at the surfacing point to punish the
  recovery window.
- *Punishes:* ignoring the ground; also punishes clustering, since eruptions catch groups.

**P3 — The Serpent** (54–28%, exit requires: three vulnerable segments destroyed)

- *What changes:* it surfaces fully — a long segmented body coiling through the arena, parts of it
  above ground and parts below at any time. The body is now **terrain**: it blocks lines, creates
  corridors, and moves.
- *New mechanic:* **segment targeting** — most of the body is armoured, but a small number of segments
  are exposed (visibly cracked, and they move along the body as it coils). Damage only counts on those.
  Hitting armour does nothing, so the group is constantly repositioning around a moving wall of boss.
- *Strategy:* the most spatially complex phase in the roster — the arena's geometry is literally the
  boss, and it changes shape every few seconds.
- *Punishes:* static positioning; attacking whatever is nearest.

**P4 — The Ancient** (<28%)

- *What changes:* full size. It coils around the arena perimeter, so the fightable space is ringed by
  its own body, and it periodically **swallows** a player.
- *New mechanic:* the **interior** — a swallowed player lands in a real enclosed space inside it and
  must cut their way out through breakable interior blocks, while the outside group fights on
  shorthanded. Getting out damages it from within, which is the phase's best damage source, so being
  swallowed is *an opportunity*, not just a punishment.
- *Strategy:* a genuinely split encounter — two groups, two problems, one boss.
- *Punishes:* groups who panic when separated, and anyone who never learned the False Ground pattern,
  because the ringed arena leaves nowhere to retreat to.

### 1.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **False Ground** | all fight | affected floor tiles are faintly but permanently marked, and visibly begin to fail ~1s before they go | learn the marks early; don't stand on them | the floor gives out — lethal-damage pit with eject (§0.3), never a fall death | pattern count scales with players; marks are always visible from fight start |
| **Evasion** | P1 | it visibly juke-dodges and breaks lines, never committing | cut escape lines, place blocks, corner it | it simply never fights, and the phase stalls | more players make cornering much easier — this is the one mechanic that genuinely eases with numbers, deliberately, since P1 solo is otherwise miserable |
| **Void Breath** | on interval | it rears with a visible throat glow, then a cone | leave the cone | heavy damage + it eats the blocks it hits | cone width fixed |
| **Wyrm Dive** | P1–P2 | it visibly launches and arcs, long readable travel | move off the landing line | heavy impact + a crater | one dive per cycle |
| **Burrow / tremor** | P2 onward | the ground visibly ripples and audibly rumbles along its underground path | track the tremor; be off the surfacing point | erupts underneath, launching and heavy damage | tremor path always visible; eruption count = 1 + 1 per 2 players |
| **Tunnel mouths** | after each burrow | real holes left in the floor | avoid; they compound with False Ground | permanent floor loss for the fight (pit rules) | count grows with fight length |
| **Segment armour** | P3 onward | armoured segments are visibly plated; vulnerable ones are visibly cracked and glowing | only strike cracked segments; they migrate along the body | hitting armour deals nothing | vulnerable segment count = 1 + 1 per 2 players |
| **Coiling** | P3 onward | the body physically moves through the arena, opening and closing corridors | read the coil, move with it | crushed between segments, or cut off from your group | coil speed fixed |
| **Swallow** | P4, on one player | it visibly rears and lunges with its mouth open, generous telegraph | dodge it — or accept it deliberately for the interior damage window | you are inside, alone, on a timer | one player at a time, always; solo the interior is smaller and quicker to cut out of |
| **Interior** | after a swallow | a real enclosed space with breakable interior blocks and a visible weak point | break out; hitting the weak point is the fight's best damage | you take steady damage until out; outside group is a player down | interior size scales down for solo so it's never a death sentence |
| **Starfall Nova** | P4, on interval | it visibly coils tight and glows before releasing | be behind a raised segment of its own body | heavy arena-wide damage | radius fixed; its own body is always the cover |

### 1.5 Multiplayer

- **1 player:** P1 cornering is the hard part solo, so the wyrmling's evasion is reduced and the arena
  supplies more blocking material — solo it becomes a trapping puzzle rather than a pincer. The
  interior in P4 is smaller and faster to escape, so being swallowed solo is a short detour, not a
  loss. Segment count at the floor.
- **2 players:** cornering works properly for the first time (two bodies = a real pincer), and the P4
  swallow creates a clean 1-in/1-out split that both players feel.
- **3–5 players:** P1 becomes easy, which is intentional — it's the only phase in the roster that gets
  *easier* with numbers, offsetting how brutal solo cornering would otherwise be. P3 scales by adding
  vulnerable segments, so a bigger group has more valid targets but has to spread around a moving
  body. P4 can have only one player swallowed at a time regardless of size, so a big group is never
  gutted by it.

### 1.6 Anti-cheese

- **Face-tanking:** P1 has nothing to tank (it won't engage), P3 gates damage on segments, P4 rings
  the arena with its own body. Armour is never the answer.
- **Ignoring mechanics:** segment armour means ignoring the mechanic deals literally zero damage.
- **Burst-skipping:** every phase exits on an objective — cornerings, punished surfacings, segments
  destroyed.
- **Camping:** False Ground marks the floor from the start, tunnel mouths remove more of it, and the
  coiling body physically moves through camped positions.
- **Ranged cheesing:** P1's evasion specifically breaks lines of sight against stationary shooters,
  and P3's vulnerable segments are frequently on the far side of its own body. Interior work is melee
  only.
- **Infinite healing:** cornering, segment gating and the interior timer are all non-damage problems.

### 1.7 Difficulty

- **Mechanical:** High — four different skill sets in one fight.
- **Damage:** Medium — spiky at eruptions and Nova, forgiving otherwise.
- **Learning curve:** High, but pleasantly so — each phase teaches itself from scratch, so a wipe in
  P3 doesn't invalidate what you learned in P1. The "four fights in one" structure means the fight
  never feels solved, which is the right note for a late-roster boss.

### 1.8 Implementation difficulty

**Hard.** Needs: evasive AI that actively flees and can be cornered (novel — every other boss AI
pursues), burrow/underground state with a visible tremor path, a multi-segment body with per-segment
health and armour states that migrate, coiling movement that reshapes the arena, a real interior space
with breakable blocks and player teleport in/out, plus False Ground (already exists in the codebase).
The segmented coiling body and the evasive AI are the two genuinely new pieces.

---

## 2. Cross-roster audit (16 bosses)

### 2.1 Verb map — no duplicates, one to watch

| Boss | Core verb |
|---|---|
| Fallen King | duel / target priority |
| Frost Queen | traversal physics |
| Storm Tyrant | verticality + conduction |
| Plague Warden | attrition + silence |
| Void Sovereign | floor loss |
| Inferno Warlord | player-authored terrain |
| Solar Colossus | climbing the boss |
| Tide Leviathan | breath + 3D space |
| Dragon Elder | ranged-mandatory aerial |
| Necro Overlord | horde denial |
| Grafted Horror | systems sabotage |
| Threefold Bane | tempo |
| Amalgamated Bulk | restraint |
| Hollow Choir | sound misdirection |
| Weeping Colossus | compression + light |
| Voidwyrm | category-shifting |

**One to watch:** Solar Colossus (climb the boss's body) and Voidwyrm P3 (fight around the boss's
body as terrain). Both make the boss into level geometry. They stay distinct because the Colossus is
**vertical and static-ish** — you go up a standing figure — while the Voidwyrm is **horizontal and
moving** — the level rearranges around you. Keep it that way: the Colossus must never crawl, and the
Voidwyrm must never be climbable.

### 2.2 Finding: five bosses share the same Phase 3 — fix before implementing

This is the significant finding. As designed, **five bosses have a P3 built on "boss is immune while
you break a thing"**:

| Boss | P3 gate |
|---|---|
| Frost Queen | Frozen Heart |
| Storm Tyrant | Storm Pylons |
| Plague Warden | the Host |
| Void Sovereign | end crystals |
| Solar Colossus | beacons (repair-denial, closest to the pattern) |

The codebase already warns about exactly this. `PhaseMechanic`'s own documentation says every
implementation of the old gate interface "produced the identical three beats — boss immune, do a
chore, boss hittable for a few seconds, repeat", and that "breaking plates, killing guards, freeing a
hostage and hitting a timing window are all the same fight wearing different costumes." Batch 1 and 2
walked straight back into that, because "boss is invulnerable, destroy the objective" is the easiest
way to write an unskippable phase.

The props are different (ice, pylons, flesh, crystals, beacons). The *play* is the same: stop
fighting the boss, hit a different health bar, resume.

**Proposed fixes, one per boss, keeping each boss's identity:**

1. **Frost Queen — keep it.** This one earns the pattern because the Heart is immune to weapons
   entirely and is solved by *carrying fire*, not by damage. It is a delivery objective, not a second
   health bar. It's the only one of the five that isn't secretly a DPS check. Keep as the roster's
   single canonical example of the archetype.

2. **Storm Tyrant — convert to a damage rule.** Rather than immunity, the pylons should govern *how*
   he takes damage: while pylons stand, he only takes damage from players who are **currently
   discharged** (Static Charge near zero). This uses `filterDamage` instead of a wall, so the fight
   never stops — the group keeps attacking, but the rod rotation now directly gates their DPS.
   Destroying pylons becomes optional-but-strong rather than mandatory, and the phase gets more
   interesting, not less.

3. **Plague Warden — convert to positional.** The Host shouldn't make him invulnerable; it should make
   him take damage **only from players who are currently below 25% Infection**. Clean players do the
   damage, infected players do the cleansing and the noise discipline. That turns P3 into a role
   rotation driven by the boss's own core meter, which is far more his identity than another
   destructible object.

4. **Void Sovereign — convert to identification.** Drop the crystals-as-gate framing. The three
   phantoms are the phase: damage only counts on the real one, identified by the falling-block tell.
   Crystals stay in the fight but become an *optional* objective — destroying one shortens the
   Convergence window and blows away more floor. Risk/reward instead of a chore.

5. **Solar Colossus — already fine, sharpen the framing.** The beacons don't gate damage, they *undo
   progress*. That's a genuinely different pressure (loss aversion, not permission) and it should be
   written up that way explicitly so it doesn't get implemented as another immunity wall by
   reflex. Add: the Colossus stays fully damageable throughout P3.

After these changes: one boss uses the immunity archetype, three use `filterDamage` damage-rules, one
uses progress-loss. That is the variety the engine's three levers were built for.

### 2.3 Finding: anti-cheese answers cluster

Counts across 16 bosses:

- **Armour-ignoring, non-healable meter:** Frost Queen (Chill), Storm Tyrant (Static), Plague Warden
  (Infection), Void Sovereign (Void Echo). Four. **Verdict: acceptable, do not add more.** The cures
  are genuinely different (campfire / lightning rod / fire-and-pyres / movement), and batches 2–4 add
  none — Inferno uses real fire, Tide uses the real air bar, Voidwyrm uses none. Hold the line at four.
- **"Objective is at range/height so ranged players have a job":** Storm Tyrant pylons, Necro anchors,
  Solar beacons, Dragon wings. Four, but they're each doing different work, and it's the healthiest
  way to tax melee-only groups. Fine.
- **"Boss preferentially targets the camped/stationary player":** appears in six bosses. This is fine
  as a background rule but should be **stated once as a global engine behaviour** rather than
  re-designed per boss.
- **Progress-loss as a punishment** (boss heals or repairs): Fallen King, Dragon Elder, Solar Colossus,
  Necro Overlord, Amalgamated Bulk, Grafted Horror. Six. Slightly heavy but each is thematically
  earned, and it's the one punishment healing cannot answer. Acceptable.

### 2.4 Finding: duplicate darkness mechanic

**Hollow Choir P2** and **Weeping Colossus P3** both do "the lights go out, place and defend torches".
Two bosses, one batch, same beat.

**Fix:** they should use *different kinds* of dark.

- **Hollow Choir** keeps the **Darkness status effect** from shriekers — supernatural, unfightable,
  and it forces the switch to the audio channel the whole boss is built around. Correct there.
- **Weeping Colossus** should instead go dark **physically** — the piston walls seal the ceiling, so
  the chamber's real light level drops. That means torches genuinely work, mob-spawn-grade darkness is
  a real thing players can fix, and the counterplay is construction rather than endurance. It also
  reinforces that boss's "the room is the enemy" identity.

Same feeling, different mechanism, no overlap.

### 2.5 Finding: fire-carry appears twice

**Frost Queen P3** (carry fire to the Heart) and **Plague Warden** (fire/pyres cleanse Infection).
Both make fire a carried or visited resource.

**Verdict: acceptable, with one adjustment.** The Queen's is *transport* (take fire somewhere), the
Warden's is *visit* (go stand in fire). Different verbs. But the Warden should drop any fire-*carrying*
and rely purely on stationary pyres plus Carrier-burst cleansing, so the two never converge.

### 2.6 Shared systems — consolidation

Batches 1–3 listed 15 systems. Several collapse:

| Consolidated system | Absorbs |
|---|---|
| **1. Terrain engine + arena ledger** | block conversion, craters, columns, floor deletion, falling blocks, lethal-damage pits, staged fluid fill (lava + water), moving arena geometry |
| **2. Player meter framework** | Chill, Static, Infection, Void Echo — one system, four skins |
| **3. Carry / escort state** | crown shards, fire, chorus fruit, caught pearls, water buckets, light sources |
| **4. Physical objective props** | bells, campfires, rods, pylons, graves, beacons, crystals, conduits, spore nodes, catalysts |
| **5. Redstone-prop system** | wires, repeaters, pistons, dispensers, observers, note blocks (Grafted Horror, Threefold Bane, Weeping Colossus) |
| **6. Add-wave management** | already exists; used by Necro, Plague, Fallen King, Bulk |
| **7. Multi-part boss bodies** | Colossus joints, Dragon wings, Voidwyrm segments, Bulk mass |
| **8. Aerial + evasive AI** | Dragon flight, Voidwyrm evasion and burrowing |
| **9. Noise / attention model** | Hollow Choir only — the one genuinely single-use system |

**Nine, down from fifteen.** Systems 1–4 cover most of the roster and should be built first; 9 is the
only one worth questioning, and it's justified because the Choir is unbuildable without it.

### 2.7 Recommended global engine behaviours

Rather than re-specifying these per boss, state them once:

1. Stationary players are preferentially targeted by ground-hazard mechanics.
2. Pits deal heavy damage and eject to solid floor; nothing kills by falling (§0.3).
3. The arena supplies every item a mechanic depends on, replenished per phase (§0.3).
4. All arena changes are ledgered and restored on fight end (§0.3).
5. Every phase has a non-HP exit condition in addition to its threshold.
6. Every attack telegraphs for ≥ 15 ticks, scaled up with lethality.
7. Player-count scaling adjusts mechanic count and coverage, never mechanic damage; HP scaling stays
   clamped 1.0×–2.5× as it already is.

---

## 3. Batch 5 preview

The Worldender is the only boss left. Its 8 phases can now be assembled entirely from established
verbs — which is the point of designing it last. The open question for batch 5 is structural rather
than thematic:

**Should the Worldender channel the other bosses (as the current spec has it — phase 2 is Frost Queen,
phase 3 is Storm Tyrant, and so on), or should it have its own eight-phase identity that merely
*references* them?**

The current channeling design risks being a highlight reel — eight phases of "here's a worse version
of a fight you already did". My recommendation is a hybrid: it channels their **verbs** rather than
their attack lists, and the final phases force the group to use several verbs simultaneously. But it's
a taste call, and it decides the whole capstone, so it's worth settling before I design it.
