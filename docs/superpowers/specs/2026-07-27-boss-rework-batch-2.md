# Boss Rework — Design Pass, Batch 2 of 5

**Status:** design only. No code. Continues
[batch 1](2026-07-27-boss-rework-batch-1.md) — its §0 philosophy and §0.3 rulings apply here
unchanged and are not repeated.

**Batch 2 picks:** Inferno Warlord, Solar Colossus, Tide Leviathan, Dragon Elder, Necro Overlord.

Why these five together: batch 1 owned *duel, traversal, verticality, attrition, floor-loss*. Batch 2
takes the five heavy elemental/siege slots and gives each a verb batch 1 does not have —
**terrain authoring by the players** (Inferno), **climbing the boss itself** (Colossus),
**breath and 3D water space** (Leviathan), **ranged-mandatory aerial priority** (Dragon), and
**horde control and denial** (Necro). No two share a core interaction.

Inherited rulings from batch 1 §0.3, restated in one line each because every boss below depends on
them: **arena restores after the fight** (so destruction can be extreme), **pits deal heavy damage
and eject you, nobody dies to falling**, and **the arena supplies every item a mechanic needs**.

---

## 1. The Inferno Warlord

### 1.1 Identity

- **Theme:** a foundry being deliberately overloaded until it melts.
- **Fantasy:** the room is going to kill you before he does, and your best weapon is a bucket.
- **Unique:** the roster's **player-authored terrain** boss. Lava rises in stages, and the counter is
  the most Minecraft thing possible — **pour water on lava to make stone and build your own footing**.
  No other boss asks players to *construct* the arena mid-fight. He is also the fire/fuel boss: real
  fire spread, real fuse lines crawling toward real TNT.

### 1.2 Core gameplay loop

The arena floods with lava on a schedule the players can see coming. Survivable ground is whatever is
high enough or whatever the group has *made* — water buckets (arena-supplied, limited charges,
refillable at cauldrons) turn lava into stone and obsidian, letting the group bridge, wall off flows,
and hold a shrinking platform network.

Meanwhile the Warlord is not waiting. He hurls magma, drops burning logs from the ceiling, and lights
**fuse lines** — real crawling fire that travels along the floor toward stacked TNT. Players cut
fuses by dousing them or by breaking the line with a placed block. Ignore a fuse and a chunk of your
hard-won platform goes up.

The fight's tension is a genuine economy: every bucket you spend bridging is a bucket you don't have
to cut a fuse or douse a burning teammate.

### 1.3 Phases

**P1 — The Forge** (100–72%, exit also requires: one fuse line cut before detonation)

- *What changes:* baseline. Ground-level fight, magma blocks scattered, fire trails, first fuse.
- *New mechanic:* Fire Trails (real fire spreading along the floor along a readable path) and
  **Burning** — catching fire is a real state cured by water, a cauldron, or another player dousing
  you.
- *Strategy:* learn the fire rules; learn what a bucket is worth.
- *Punishes:* standing in fire and healing through it; wasting water.

**P2 — Flood the Foundry** (72–48%, exit also requires: the group is standing on ground it created)

- *What changes:* lava begins rising in tiers, flowing from the arena edges inward. The original
  floor is progressively gone. Water-to-stone bridging goes from optional to mandatory.
- *New mechanic:* **rising lava** + **construction**. Pour water on a lava flow to make stone,
  on a lava source to make obsidian, and hold a platform.
- *Strategy:* the group has to think like builders under fire. Someone must be dedicated to
  bucket logistics.
- *Punishes:* pure-DPS groups with nobody on utility; hoarding buckets and never committing.

**P3 — Powder Keg** (48–20%, exit requires: three TNT clusters neutralised, not detonated)

- *What changes:* he stacks real TNT clusters on the remaining platforms and lights long, visible
  fuse lines. Fuses crawl at a readable speed. A cluster that goes off removes a platform outright.
- *New mechanic:* an explicit tug-of-war over your own footing. Cut the fuse, or douse the TNT
  cluster's ignition, or wall it off with a placed block — three valid solves, all physical.
- *Strategy:* the highest-pressure phase; the group is splitting attention three ways while lava
  keeps rising.
- *Punishes:* tunnel vision. Also punishes standing on top of the thing you're defusing.

**P4 — Meltdown** (<20%)

- *What changes:* almost everything is lava. What remains is the platform network the group built,
  plus his own basalt pillar. He is finally reachable, but only from the ground the players made.
- *New mechanic:* the endgame is a direct referendum on P2 and P3 — a group that built well has a
  comfortable arena, a group that didn't is fighting on two tiles.
- *Strategy:* burst him down on your own terrain.
- *Punishes:* every bucket wasted, every platform lost.

### 1.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Burning** | fire trails, magma, log impacts | you are visibly on fire, with the real sound | step in water, stand at a **cauldron**, or have an ally douse you | steady damage that ignores most mitigation and can't be outhealed cheaply | per player |
| **Fire Trail** | on interval | real fire ignites in a line and spreads along a readable path | step over the gap, or douse a segment to break it | the trail reaches your platform and burns anything flammable on it | trail count = 1 + 1 per 2 players |
| **Magma Throw** | on interval | he physically hauls a chunk of the floor up and winds back | sidestep the marked landing zone | heavy hit; the impact tile becomes a **magma block** (permanent hazard for the fight) | throw count scales; targets distinct players |
| **Burning Logs** | P1 onward | ceiling cracks, then real falling log blocks | move out of the marked zone | impact damage + the log lands lit and starts a fresh fire | log count = 1 per player |
| **Rising Lava** | P2 onward, in visible tiers | lava audibly and visibly pours from the arena rim, one tier at a time, with a clear warning before each rise | get high, or build stone with water | contact damage plus Burning; the ground you were on stops existing | tier schedule is fixed — more players just means more people competing for the same high ground |
| **Water buckets** | arena-supplied at cauldrons | real buckets as item entities; cauldrons visibly fill and drain | pour on lava to make stone/obsidian; douse fires, fuses and burning players | with no water the group cannot make footing | bucket count = 2 + 1 per player; cauldrons refill on a timer (the contested resource) |
| **Fuse Line** | P1 (one), P3 (many) | a real crawling fire trail with an obvious direction and destination, several seconds of travel | douse it, or break the line with any placed block | reaches the TNT and detonates a platform | fuse count = 1 + 1 per player |
| **TNT Cluster** | P3 | real TNT blocks stacked visibly on a platform | neutralise before ignition — douse, wall off, or cut the fuse | real explosion, platform gone, heavy damage to anyone near | 3 clusters solo, up to 5 grouped |
| **Heat Aura** | passive in melee range | air visibly shimmers around him, rising heat | rotate out periodically; refresh with water | continuous Burning application while in melee | radius fixed — this is the anti-melee-camp tax |
| **Cinder Nova** | when nobody is in melee range for too long | he crouches and glows, whole-arena wind-up | close the distance, or take cover behind a raised stone wall you built | arena-wide heavy fire damage | the anti-ranged tax; unchanged by size |

### 1.5 Multiplayer

- **1 player:** bucket count at the floor of the range but cauldron refill is faster, so a solo player
  can still bridge — just never generously. Three TNT clusters instead of five, one fuse at a time.
  Solo is a slower, tighter, more deliberate version of the same puzzle rather than a stripped one.
- **2 players:** the natural split emerges immediately — one builds and douses, one pressures. Dousing
  a burning teammate is faster than self-dousing, so the pair genuinely help each other.
- **3–5 players:** more fuses and more clusters than any one person can watch, forcing explicit
  assignment. The high ground is the same size for five as for one, so crowding on a platform during a
  lava rise is its own problem. Difficulty comes from attention bandwidth, not numbers.

### 1.6 Anti-cheese

- **Face-tanking:** the Heat Aura applies Burning continuously in melee, and Burning is cheap for the
  boss and expensive for the player to cure.
- **Ignoring mechanics:** ignoring lava rises deletes your standing room. There is no DPS answer to
  terrain.
- **Burst-skipping:** P2 exits on the group standing on constructed ground, P3 on three clusters
  neutralised. HP alone advances nothing.
- **Camping:** lava rises everywhere, on a schedule, regardless of where you are.
- **Ranged cheesing:** Cinder Nova fires specifically when nobody is in melee — the punish for
  turtling at range. Also, ranged players still need footing, and footing is a melee-range utility job.
- **Infinite healing:** Burning plus lava contact plus TNT is far more throughput than sustainable
  healing, and none of it is prevented by healing — only by doing the mechanic.

### 1.7 Difficulty

- **Mechanical:** Medium-High — the actions are simple (pour water, break a line), the *prioritising*
  is the challenge.
- **Damage:** High — lava and TNT are genuinely lethal, which is correct for a fire boss.
- **Learning curve:** Low-Medium to understand, High to execute well. "Water makes stone" is instantly
  intuitive to any Minecraft player; the bucket economy takes practice. Probably the most immediately
  *readable* boss in the whole roster.

### 1.8 Implementation difficulty

**Hard.** Needs: staged lava fill with flow control and performance batching, real water/lava
interaction respected rather than reimplemented, bucket and cauldron item state, fire spread along a
scripted path (vanilla fire spread is too random to telegraph — needs a controlled crawl), real primed
TNT with a defuse path, magma block placement, and the arena ledger doing heavy lifting on rollback.
The lava volume is the performance risk; the fuse crawl is the fiddly bit.

---

## 2. The Solar Colossus

### 2.1 Identity

- **Theme:** a colossal sun-powered construct, fought at the scale of a building.
- **Fantasy:** you are an insect on a moving statue, and the way to kill it is to climb it and take
  it apart.
- **Unique:** the roster's **climbing** boss — the arena *is the boss's body*. Players scaffold up its
  legs, break joints, ride it while it moves, and get shaken off. It also uses the single best unused
  vanilla interaction available: **beacon beams are blocked by placing a block over them**, which
  becomes the counter to its charge-up.

### 2.2 Core gameplay loop

On the ground, it is a slow siege monster: fist slams that crater the floor, sweeping arms, and real
falling sand/gravel pillars it collapses onto the group. You cannot meaningfully hurt it from down
there — its shell is armoured, and only its **joints** (ankle, knee, shoulder, chest core) take real
damage.

So the loop is: destabilise a leg on the ground → it drops to a knee → **climb** using
arena-supplied scaffolding and ladders → break the next joint up while it thrashes → it stands back
up and shakes climbers off → repeat higher. Between climbs, it plants **beacons** to recharge, and
the group must physically block the beams with any block before the charge completes.

### 2.3 Phases

**P1 — Siege** (100–75%, exit requires: both ankle joints broken)

- *What changes:* pure ground fight. Learn its slam patterns and its reach.
- *New mechanic:* joint targeting — hits anywhere but a joint barely register, and joints are at
  ground level only while it's mid-step or mid-slam, so damage requires timing, not just position.
- *Strategy:* punish its recovery frames at the ankles.
- *Punishes:* swinging at the body; standing in front of a slam.

**P2 — The Climb** (75–50%, exit requires: shoulder core broken)

- *What changes:* it kneels. The body becomes climbable terrain, and stays climbable for a window
  before it rises and shakes.
- *New mechanic:* **verticality on a moving boss** — place scaffolding, climb, hold on. It sheds
  climbers with a shake; being shaken off is a pit-style heavy hit and an eject to the floor (§0.3),
  not a death.
- *Strategy:* the group splits into climbers and ground crew — the ground crew must keep it kneeling
  by re-breaking leg joints, or the climbers get thrown.
- *Punishes:* everyone climbing at once (nobody keeps it down), and nobody climbing (no progress).

**P3 — Solar Charge** (50–22%, exit requires: three charge cycles interrupted)

- *What changes:* it plants **real beacons** around the arena and stands in the beams to recharge.
  A completed charge fully restores its broken joints — undoing your progress, which is a far better
  threat than damage.
- *Explicitly:* **the Colossus stays fully damageable throughout this phase.** The beacons are not a
  gate and must never be implemented as one — they threaten *progress loss*, not permission to fight.
  That distinction is what keeps this phase from collapsing into the same "boss immune, break the
  thing" beat the batch 4 audit removed from four other bosses.
- *New mechanic:* block the beams. Any player, any block, placed over a beacon. Vanilla behaviour,
  zero explanation needed, and it makes carrying spare blocks a real decision.
- *Strategy:* a scramble phase — spread to the beacons, block them, get back before the slams land.
- *Punishes:* slow reaction; being clustered when the beams come up.

**P4 — Collapse** (<22%)

- *What changes:* it is visibly falling apart — real blocks shedding off it as falling debris,
  littering the arena with cover and obstruction. It can no longer stand fully; it fights kneeling,
  which means the chest core is permanently reachable.
- *New mechanic:* the finish is a straight brawl at the core, in a debris field the fight created.
- *Strategy:* use the debris as cover from its remaining arm sweeps.
- *Punishes:* a group with no discipline about the last sweeps.

### 2.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Joint armour** | passive | joints visibly glow/expose; the rest is plated | hit joints only | body hits deal near-nothing | unchanged — the boss's core rule |
| **Fist Slam** | on interval | it raises an arm high, long visible shadow on the target zone | leave the shadow | heavy damage + a real crater; the ankle joint is exposed during recovery | slam count = 1 + 1 per 2 players |
| **Arm Sweep** | on melee cluster | it winds its arm back horizontally across a visible arc | duck behind debris, or leave the arc | knocked across the arena | arc fixed |
| **Sand/Gravel Pillars** | on interval | it punches a pillar which visibly starts to fall, real falling-block physics | move out of the collapse line | crushing damage + the debris blocks that lane for the fight | pillar count scales |
| **Kneel window** | both leg joints broken | it audibly drops, the body physically lowers and becomes climbable | climb it | — | window duration fixed; more players just means more climbers |
| **Climbing** | during kneel | real scaffolding/ladders, arena-supplied | place, climb, break the next joint | — | scaffolding supply = 1 stack per player |
| **Shake-off** | it stands back up | it visibly tenses and rises, with a clear audible cue | get down deliberately, or be knocked off | pit-style heavy damage + eject to the floor (§0.3), never a death | unchanged |
| **Beacons** | P3 | real beacons planted with visible beams | **place any block over the beam** | a completed charge repairs its broken joints — real lost progress | beacon count = 2 solo, up to 4 grouped |
| **Solar Beam** | P3 onward | it charges visibly, a bright line traces along the ground before firing | leave the line; use debris as cover | very heavy damage along the line and it ignites the ground | one beam, sweeps predictably |
| **Debris field** | P4 | real blocks shed from its body as falling blocks | use as cover | the arena clutters and lanes close | debris scales with fight length |
| **Core** | P2 shoulder, P4 chest | a visibly exposed glowing mechanism | attack it — this is where the real damage happens | — | core HP scales with player count |

### 2.5 Multiplayer

- **1 player:** the kneel window is longer and the shake-off telegraph is longer, so a solo climber can
  reliably get up and back down. Two beacons rather than four. The ground-crew/climber split
  collapses into a single sequence — break legs, climb fast, get down — which makes solo the most
  *rhythmic* version of this fight.
- **2 players:** the intended shape. One holds the legs, one climbs. Genuine trust: the climber is
  entirely dependent on the ground player keeping it kneeling.
- **3–5 players:** four beacons in P3 means real spread, and the group must resist the urge to all
  climb. Slam and pillar counts scale so the ground crew has work. Notably, more players does *not*
  make the climb faster in a way that trivialises it — the kneel window is the limiter.

### 2.6 Anti-cheese

- **Face-tanking:** damage is gated on joints, not on survival. You can stand there all day and
  accomplish nothing.
- **Ignoring mechanics:** beacons repair its joints. Ignore them and the fight moves backwards.
- **Burst-skipping:** every phase exits on a physical objective (ankles, shoulder core, three
  interrupts). No HP shortcut exists anywhere in this fight.
- **Camping:** pillar collapses and craters progressively deny the ground; the boss also physically
  walks, so a fixed spot stops being in range.
- **Ranged cheesing:** joints on a moving construct are hard targets at range, and the climb is
  strictly physical. Ranged players are the natural beacon-blockers, which is real work, not a free
  ride.
- **Infinite healing:** healing does nothing about a repaired boss. Progress loss, not damage, is the
  fail state — the one anti-cheese lever no amount of sustain touches.

### 2.7 Difficulty

- **Mechanical:** High — climbing a moving entity is the most demanding execution in batches 1–2.
- **Damage:** Medium — slams hurt, but the fight kills you slowly.
- **Learning curve:** Medium. The rules are unusually clear ("hit the glowing joints", "block the
  beam"), but executing the climb under a shake-off timer takes real practice. Highest
  "I got better at this" ceiling of the batch.

### 2.8 Implementation difficulty

**Hard.** Needs: a large multi-part boss body (display entities or a real block structure) that moves
and can be stood on, per-joint damage regions with independent health, a climbable/rideable surface
with a shake-off eject, real beacon placement with beam-obstruction detection, falling-pillar physics,
and progress-repair logic. The moving climbable body is the single hardest technical piece in the
whole roster — worth scheduling this boss late in the implementation order even though its design is
locked now.

---

## 3. The Tide Leviathan

### 3.1 Identity

- **Theme:** an arena that becomes an ocean, then drains.
- **Fantasy:** drowning is the enemy. The boss is just what's in the water with you.
- **Unique:** the roster's **breath and 3D-space** boss. It is the only fight where the arena becomes
  fully volumetric — up and down are real tactical axes, using real bubble columns (soul sand up,
  magma block down), real conduits for breathing, and real guardians with their unmistakable beam
  telegraph. And it's the only boss that **drains** its own arena for a hard tonal reversal at the end.

### 3.2 Core gameplay loop

Water rises until the arena is submerged. From then on, the master resource is **air**. Conduits
(arena-supplied, placed on pedestals) grant water breathing in a radius — but the Leviathan smashes
them, so defending and re-placing conduits is the fight's spine. Air pockets under overhangs are the
backup.

Movement is vertical: soul-sand bubble columns shoot you upward, magma-block columns drag you down,
and both are placed by the boss as traps *and* usable by players as transport. Guardians patrol as
adds with real charging beams that force line-of-sight breaks.

Then P4 drains everything, and the whole skillset inverts.

### 3.3 Phases

**P1 — Rising Tide** (100–75%, exit also requires: one conduit placed and held)

- *What changes:* water floods in stages. Still partly a ground fight, with the shoreline moving.
- *New mechanic:* air management begins; the first conduit is placed.
- *Strategy:* learn where the air is; learn the tide schedule.
- *Punishes:* fighting deep with no plan to breathe.

**P2 — The Deep** (75–48%, exit also requires: survive one Whirlpool)

- *What changes:* full submersion. Combat is now genuinely 3D. Guardians arrive. Bubble columns
  appear as both hazard and highway.
- *New mechanic:* **conduit defence** — he actively targets conduits, and a broken conduit means the
  group's air clock restarts.
- *Strategy:* the group must hold a breathable volume while fighting a boss that is faster than them
  underwater.
- *Punishes:* chasing him out of the conduit radius. He wants you to.

**P3 — Maelstrom** (48–20%, exit requires: three Whirlpools broken by grounding on soul-sand columns)

- *What changes:* a permanent whirlpool at the arena centre drags everything inward. Magma columns
  pull down toward the floor, soul-sand columns push up.
- *New mechanic:* the pull is constant, so *position is something you maintain rather than choose*.
  Escaping the pull means riding a soul-sand column out — real vanilla bubble-column physics as
  movement tech.
- *Strategy:* the most alien-feeling phase in the roster: a fight with no stable footing at all.
- *Punishes:* passivity — drift into the centre and you are chewed up.

**P4 — Low Tide** (<20%)

- *What changes:* he drains the arena in one dramatic event. Water gone. He is **beached** — slower,
  heavier, flailing on wet ground, and now vulnerable to the melee he could always outswim.
- *New mechanic:* full inversion — everything you learned about verticality is discarded, and the
  fight becomes a close, brutal, grounded brawl with a wounded animal. Wet ground is slippery, and
  the leftover water pockets are the only thing that puts out his residual thrashing.
- *Strategy:* close in and finish it.
- *Punishes:* nothing new — this phase is deliberately a *release*, the payoff for surviving the deep.

### 3.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Air** | passive when submerged | real vanilla bubble meter | stay in a conduit radius, find air pockets, surface | drowning damage — armour-irrelevant, unhealable in practice | per player |
| **Conduits** | arena-supplied, placed on pedestals | real conduits with their unmistakable animation and sound | place, defend, replace when smashed | no breathing radius; the group's clock starts | conduit count = 1 solo, up to 3 grouped |
| **Conduit Smash** | on interval in P2+ | he visibly turns and charges toward a conduit, long approach | intercept him, or bait him away | conduit destroyed, air crisis | he prioritises the most-used conduit — punishes stacking |
| **Guardians** | P2 onward | real guardians with the vanilla charging beam telegraph | break line of sight, kill them | steady chip damage that stacks with drowning | count = 1 + 1 per player |
| **Water Jet** | on interval | he visibly inhales, then a straight high-pressure line | leave the line | heavy damage + massive knockback, usually out of conduit range | one jet per cycle |
| **Bubble columns** | P2 onward, placed by him | real soul sand (up) and magma blocks (down), visually unmistakable | avoid the magma ones; **use the soul sand ones** to travel | dragged to the floor and pinned, or launched into a guardian line | column count scales |
| **Whirlpool** | P2 once, P3 permanent | the whole water volume visibly spirals, debris and items circle inward | swim out early, or ride a soul-sand column | dragged to the centre for repeated heavy damage | pull strength constant; more bodies = more collisions |
| **Bubble Trap** | on a marked player | they are visibly encased in a bubble and lifted | allies pop it; solo, it pops on its own after longer | isolated and drowning, out of conduit range | rescue always possible solo, just slower (§0.3 spirit) |
| **Tidal Surge** | phase transitions | water level visibly and audibly changes in stages, with warning | reposition before the level changes | swept, disoriented, out of position | schedule fixed |
| **Beaching** | P4 | the arena drains in one huge visible event | close to melee and finish | — | unchanged |

### 3.5 Multiplayer

- **1 player:** one conduit, and he smashes it less often. Bubble Trap self-pops. Guardian count at
  the floor. Solo is a tense, claustrophobic breath-management fight — arguably the most atmospheric
  version.
- **2 players:** conduit defence becomes a real job while the other pressures. Bubble Trap rescue is
  fast, which makes the pair feel genuinely necessary to each other.
- **3–5 players:** three conduits means the group can hold a network — but he targets the *most-used*
  one, which punishes everyone huddling in a single radius and pushes the group to spread across the
  volume. Guardian count scales, so line-of-sight discipline matters more with a crowd.

### 3.6 Anti-cheese

- **Face-tanking:** drowning ignores armour completely and cannot be healed away.
- **Ignoring mechanics:** there is no way to fight underwater without conduits or air pockets. The
  mechanic *is* the fight.
- **Burst-skipping:** P1 gates on a held conduit, P2 on a survived whirlpool, P3 on three broken ones.
- **Camping:** he smashes the most-used conduit; the tide level moves; the whirlpool pulls. No fixed
  point survives.
- **Ranged cheesing:** underwater projectiles are already weak in vanilla, and the fight leans into
  that deliberately — ranged players become conduit-defenders and guardian-killers. Melee players get
  the boss but pay in air.
- **Infinite healing:** drowning, whirlpool pinning and bubble traps are not damage you can outheal;
  they are position and resource problems.

### 3.7 Difficulty

- **Mechanical:** High — 3D movement plus a breath clock plus objective defence.
- **Damage:** Medium — most deaths are drowning, not hits.
- **Learning curve:** Medium-High. Underwater combat is unfamiliar to most players, which is exactly
  the point, and P4's release makes the whole arc feel earned rather than exhausting.

### 3.8 Implementation difficulty

**Hard.** Needs: staged water fill/drain over a large volume, air/breath interaction, real conduit
placement with destructible state, bubble column placement, guardian add integration, a directional
pull field for the whirlpool, and underwater-aware boss movement. Shares its "staged fluid fill"
engine with the Inferno Warlord — building those two adjacently is the efficient order.

---

## 4. The Dragon Elder

### 4.1 Identity

- **Theme:** an ancient wyrm that owns the sky and only touches the ground on its own terms.
- **Fantasy:** you cannot reach it, so you take away everything that lets it stay up there.
- **Unique:** the roster's **ranged-mandatory** boss and the inverse of every other fight — melee has
  to *earn* windows instead of having them by default. Its signature interaction is pure vanilla:
  **fireballs you deflect by hitting them**, and **perches you destroy to deny it rest**.

### 4.2 Core gameplay loop

It circles overhead, out of melee reach, throwing real fireballs. Those fireballs are the melee
player's whole toolkit — hit one back and it damages the dragon, which is the single most satisfying
vanilla interaction in the game and the reason this fight isn't archer-only.

It periodically lands on **perch pillars** to recover. Every perch it uses restores health, so the
group must break the pillars — which is straightforward, until P3, when those same pillars turn out
to be the only cover from its strafing runs. Breaking every pillar in P2 makes P2 easy and P3
brutal. Leaving some makes P2 grindy and P3 survivable. That tension is the encounter.

### 4.3 Phases

**P1 — Circling** (100–76%, exit also requires: one fireball deflected)

- *What changes:* baseline aerial fight. It circles, throws fireballs, occasionally swoops low.
- *New mechanic:* deflection, wing gusts, and the first perch landing (a free melee window to teach
  what a window looks like).
- *Strategy:* archers work the wings; melee waits on fireballs and swoops.
- *Punishes:* melee players standing uselessly; archers ignoring positioning.

**P2 — Deny the Perch** (76–50%, exit requires: it is denied a perch three consecutive times)

- *What changes:* it starts using perches to heal in earnest, and its healing is visible and
  substantial.
- *New mechanic:* **pillar destruction** — the players' own grief is the counterplay. Break the pillar
  it's heading for before it lands, and it must stay airborne, which drains its stamina and eventually
  forces a long grounded window.
- *Strategy:* read where it's going and get there first. Genuinely predictive play.
- *Punishes:* reactive play; letting it heal.

**P3 — Dive Bomb** (50–22%, exit also requires: survive three strafing runs)

- *What changes:* it stops perching and starts strafing the arena in straight runs, breathing fire
  along the ground and leaving real burning lanes.
- *New mechanic:* cover — and cover is whatever pillars survived P2. This is where the P2 decision
  gets paid for, in either direction.
- *Strategy:* lane awareness, sprint timing, and using terrain the group itself decided to keep or
  destroy.
- *Punishes:* thoughtless demolition in P2; also punishes over-cautious groups who let it heal.

**P4 — Grounded** (<22%)

- *What changes:* wings shredded, it comes down for good. Now it's a melee brawl with tail sweeps,
  bites, and fire fields — and the roles fully invert, with archers suddenly the ones scrambling for
  safe angles.
- *New mechanic:* role reversal as the finale.
- *Strategy:* close and finish, using burnt lanes as no-go zones.
- *Punishes:* a group that never practised close-quarters movement because they'd been shooting all
  fight.

### 4.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Fireball** | continuous | real fireball projectile with a visible arc and trail | **hit it back** — deflected fireballs damage the dragon | heavy damage + real fire started where it lands | throw rate scales with player count; targets distinct players |
| **Wing Gust** | when it passes low | it visibly flares its wings, dust kicks up | brace behind a pillar, or move out of the pass line | flung across the arena into fire or off high ground | pass frequency fixed |
| **Perch landing** | P1 tutorial, P2 onward | it visibly banks toward a specific pillar, several seconds of approach | get to that pillar and break it first | it lands and heals a visible, substantial chunk | perch count = 4 pillars; it always telegraphs which |
| **Grounded window** | denied a perch, or stamina drained | it lands hard, visibly winded, wings down | all-in melee damage | — | window length fixed — the reward is the same for everyone |
| **Tail Sweep** | during grounded windows | tail visibly coils before the swing | jump it, or stand inside the arc near its body | knockback + damage | arc fixed |
| **Grab and Drop** | on a marked player, P2+ | it dives with claws out, long visible approach on one target | break the approach line with a pillar, or dodge at the last moment | picked up, carried, and dropped — treated as a **lethal-damage pit** hit with an eject, never a fall death (§0.3) | one target at a time, always |
| **Strafing Run** | P3, repeating | it visibly lines up at one arena edge with a clear run-up | get out of the lane, or take cover behind a surviving pillar | heavy damage + a lane of real fire that persists | run count = 3 + 1 per 2 players |
| **Fire Breath** | P3 onward | throat glows, an audible inhale, then a cone | leave the cone; it is generous but committed | sustained heavy damage + burning ground | cone fixed |
| **Wing membranes** | all fight | visibly tattered as they take damage | shoot them — this is what eventually grounds it for P4 | it stays aerial longer, so the fight drags | membrane HP scales; arena supplies arrows (§0.3) |

### 4.5 Multiplayer

- **1 player:** the arena supplies arrows generously and the dragon's perch approach telegraph is
  longer, so a solo player can beat it to a pillar. Fireball deflection is the main melee damage
  source, which makes solo a genuinely distinctive skill test — hit every fireball back and you win
  fast. This is the boss most transformed by playing solo, in a good way.
- **2 players:** natural split — one denies perches, one deflects and shoots. The P2/P3 pillar
  dilemma becomes an actual conversation between two players.
- **3–5 players:** fireball rate and strafing run count scale so everyone has something incoming. More
  players make perch denial much easier, which is intentionally balanced by P3 having fewer surviving
  pillars for cover — the group's own efficiency raises the later difficulty.

### 4.6 Anti-cheese

- **Face-tanking:** for most of the fight there is nothing to face-tank; it is not in reach.
- **Ignoring mechanics:** unbroken perches mean it out-heals your damage outright.
- **Burst-skipping:** P2 exits on three denied perches, P3 on three survived runs.
- **Camping:** strafing runs and fire lanes systematically cover the arena; a camped spot burns.
- **Ranged cheesing:** this is the one boss where ranged is *mandatory*, so instead the anti-cheese
  runs the other way — Grab-and-Drop and Wing Gust specifically hunt stationary archers, and P4
  drops it into melee range where archers are least comfortable.
- **Infinite healing:** its perch healing outpaces player healing, so sustain doesn't win — denial
  does.

### 4.7 Difficulty

- **Mechanical:** Medium-High — deflection timing and predictive pillar denial.
- **Damage:** Medium-High — fireballs and strafing runs hit hard, but all are readable.
- **Learning curve:** Medium. Deflection is one of those mechanics a player understands the instant it
  works once. The pillar dilemma is the deeper lesson and takes a full clear to appreciate.

### 4.8 Implementation difficulty

**Hard.** Needs: reliable aerial pathing and hover/circle/strafe states (the roster's known
highest-risk area), real fireball entities with player deflection, a perch/landing state machine with
telegraphed target selection, destructible pillars, a carry-and-drop state for a player, and
wing-membrane sub-health. The flight AI is the risk — recommend implementing this one after at least
two of the grounded batch-2 bosses are done.

---

## 5. The Necro Overlord

### 5.1 Identity

- **Theme:** a necromancer holding an army together under an artificial night.
- **Fantasy:** you are outnumbered, permanently, and the only way out is to change the *conditions*
  rather than out-kill the horde.
- **Unique:** the roster's **horde-control and denial** boss, built on the best vanilla rule available
  — **undead burn in daylight**. He blots out the sky; the players tear the shroud open and let the
  sun do the work. No other boss is solved by changing the weather over your own head.

### 5.2 Core gameplay loop

He never stops summoning. Killing adds is never the win condition — the horde regenerates from
**corpse piles** (real bone-block piles that physically accumulate on the floor and rise again unless
mined out) and from **grave markers** he plants. So the loop is: hold a chokepoint, mine the corpses
before they rise, break the graves, and — the phase-defining beat — break the **shroud anchors**
holding his artificial night, so that daylight pours in and every undead in the arena starts burning.

Players may place blocks freely, and the arena supplies materials, so **building a chokepoint is
legitimate strategy** — one of the few places where the game's own core verb (building) is the tactic.

### 5.3 Phases

**P1 — The Horde** (100–76%, exit also requires: two grave markers destroyed)

- *What changes:* baseline. Waves arrive from the arena edges; he stays behind them.
- *New mechanic:* grave markers (physical spawners he plants, destructible) and corpse piles.
- *Strategy:* funnel the horde, break the graves, don't get surrounded.
- *Punishes:* fighting in the open; ignoring the source and killing symptoms.

**P2 — The Shroud** (76–50%, exit requires: the shroud broken at least once)

- *What changes:* he physically blots out the sky — a real block canopy spreads overhead, and the
  arena goes dark. Adds get stronger in darkness and stop burning.
- *New mechanic:* **shroud anchors** — break the anchors (they're up high, so this needs the arena's
  scaffolding or bows) and the canopy collapses, letting real daylight in, which ignites every undead
  in the arena at once. It is an enormous, visible, deeply satisfying swing.
- *Strategy:* the group must divert real effort upward while the horde presses.
- *Punishes:* pure ground focus; letting the darkness stand.

**P3 — Reanimation** (50–22%, exit requires: the corpse floor cleared below a threshold)

- *What changes:* every add killed leaves a real bone-block pile, and piles rise again on a timer.
  The floor fills up with the fight's own dead, physically obstructing movement.
- *New mechanic:* **mining as combat** — clearing corpse piles requires actually breaking blocks,
  which competes for the same time as fighting. The arena literally clogs with your success.
- *Strategy:* the group must decide between killing more (making more corpses) and cleaning up. The
  correct answer is uncomfortable and interesting: sometimes stop killing.
- *Punishes:* mindless add-clearing — the classic MMO reflex, punished directly.

**P4 — Army of the Dead** (<22%)

- *What changes:* he commits everything and finally steps into the fight himself. The shroud is gone
  for good, so daylight is permanently burning his own army — his summons melt as fast as they arrive.
- *New mechanic:* the reward state. The players' P2 work makes the final phase survivable; a group
  that never broke the shroud properly faces a genuinely overwhelming horde here.
- *Strategy:* hold the chokepoint and burst him.
- *Punishes:* everything skipped earlier.

### 5.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Summon waves** | continuous | real undead mobs visibly walking in from marked arena edges | funnel them; block off lanes with placed blocks | you get surrounded and cut off from the graves | wave size = 4 + 2 per player, hard-capped for performance |
| **Grave markers** | P1 onward | physical grave structures he visibly plants, with particles and sound | destroy them | they spawn indefinitely | marker count scales with players |
| **Corpse piles** | on any add death, P3 especially | real bone-block piles physically accumulating on the floor | **mine them out** before the rise timer | the pile reanimates as a fresh add, and the floor clogs | rise timer fixed; more players kill more, so more players *create* more corpses — the group's own tax |
| **The Shroud** | P2 | a real block canopy visibly spreading across the sky; the arena darkens | break the **anchors** holding it | undead stop burning and get stronger; the arena stays dark | anchor count = 2 solo, 4 grouped; height requires bows or scaffolding |
| **Daylight** | shroud broken | actual sunlight in the arena; undead visibly catch fire | keep the shroud down as long as possible | — | the single biggest swing in the fight, identical at all sizes |
| **Soul Drain** | on interval | he visibly tethers to nearby adds, drawing them in | kill the tethered adds fast, or break line of sight | he heals substantially off his own army | tether count scales |
| **Grave Grasp** | on a marked player | hands visibly erupt from the ground under them, ~1s warning | move off the marked tile | rooted in place inside a horde — usually fatal | one target per cycle per 2 players |
| **Wither Cloud** | on interval | a real lingering cloud, visually distinct | leave the area | withering damage that blocks natural regen | cloud count scales |
| **Building materials** | arena-supplied | real block stacks provided at fight start (§0.3) | build chokepoints and cover | fighting a horde in the open | supply scales with player count |
| **He steps in** | P4 | he physically leaves the back line for the first time, unmistakable | finally attack him directly | — | unchanged |

### 5.5 Multiplayer

- **1 player:** wave size at the floor, two anchors instead of four, and one grave marker at a time.
  Solo turns into a tight chokepoint-defence fight, which is a completely legitimate and fun shape —
  arguably the most classic-Minecraft solo experience in the roster.
- **2 players:** one holds the line, one goes up for the anchors. The anchor trip is a genuine act of
  trust — you are leaving your partner alone with the horde.
- **3–5 players:** wave size and marker count scale, but the interesting scaling is P3: a five-player
  group kills far more, therefore creates far more corpse piles, therefore clogs their own arena
  faster. The group's efficiency is the difficulty. That is the cleanest expression in either batch of
  "more players changes the problem instead of inflating the numbers".

### 5.6 Anti-cheese

- **Face-tanking:** being surrounded is a positional loss, not a damage race; armour does not stop
  encirclement.
- **Ignoring mechanics:** graves and corpses mean the horde is genuinely infinite. Kill-only groups
  lose by attrition, guaranteed.
- **Burst-skipping:** he is behind his army and untargetable until P4. P2 exits on the shroud, P3 on
  the corpse floor.
- **Camping:** camping is *encouraged* here — but the camp must be built and maintained, corpse piles
  fill it, and Grave Grasp roots campers. A static spot without upkeep collapses.
- **Ranged cheesing:** shroud anchors are the ranged player's job, which is real and important work.
  Meanwhile the horde reaches any perch on the ground plane, and Grave Grasp specifically targets
  players who haven't moved.
- **Infinite healing:** Wither Cloud blocks natural regen, and the failure state is being overrun,
  which healing does not address.

### 5.7 Difficulty

- **Mechanical:** Medium — individually simple actions (kill, mine, break anchors) under heavy
  pressure.
- **Damage:** Medium — deaths come from encirclement, not big hits.
- **Learning curve:** Low to start, Medium to master. "Break the sky, let the sun in" is a concept any
  Minecraft player gets instantly; "sometimes stop killing things" is the counter-intuitive lesson
  that makes the fight memorable.

### 5.8 Implementation difficulty

**Medium.** The most conventional boss in batch 2. Needs: scalable add-wave management with
performance caps (mostly exists), destructible grave props, corpse-pile blocks with a reanimation
timer, a sky canopy structure with destructible anchors, real daylight/undead-burning interaction
(vanilla handles the burning if the canopy is real blocks and the arena is outdoors), and supplied
building materials. No novel systems — good candidate to implement early in batch 2 as a confidence
builder.

---

## 6. Batch notes

### 6.1 Shared systems added by batch 2

On top of batch 1's five systems (terrain engine + ledger, player meters, carry/escort state,
physical objective props, non-HP phase exits):

6. **Staged fluid fill** — lava rising (Inferno) and water rising/draining (Leviathan) are one system.
   Build once.
7. **Player-placed construction as counterplay** — water-to-stone bridging, beam-blocking, chokepoint
   building, scaffolding towers. Requires arenas to permit player block placement, tracked by the
   ledger.
8. **Multi-part / rideable boss bodies** — joints, climbable surfaces, shake-off (Colossus), and
   wing-membrane sub-health (Dragon). The heaviest new tech in the roster.
9. **Aerial boss state machine** — circle / strafe / perch / grounded (Dragon). Highest risk item.
10. **Vanilla-rule mechanics** — daylight burning undead, beacon beams blocked by blocks, bubble
    columns, fireball deflection, powder snow + leather boots. These cost almost nothing to implement
    and carry the most "this is a real Minecraft boss" feeling per line of code. Prefer them always.

### 6.2 Suggested implementation order within batch 2

Necro Overlord (Medium, conventional) → Inferno Warlord (shares fluid engine) → Tide Leviathan
(reuses fluid engine) → Dragon Elder (aerial risk) → Solar Colossus (hardest tech, most novel body
system). Design order and build order deliberately differ.

### 6.3 Remaining roster

- **Batch 3 (horror/anomaly):** Grafted Horror, Threefold Bane, Amalgamated Bulk, Hollow Choir,
  Weeping Colossus.
- **Batch 4:** Voidwyrm + carry-over.
- **Batch 5:** The Worldender (8-phase capstone, reuses every vocabulary established in batches 1–4).

### 6.4 Open question for batch 3

Batch 3's five are the abstract/horror bosses, and they have the least obvious mapping to physical
Minecraft objects — which is exactly where the old design defaulted to particle spam. Worth deciding
before batch 3 whether those bosses should lean on **sculk/darkness/redstone/piston** vocabulary
(mechanical horror) or **mob-and-structure** vocabulary (creature horror). I have a recommendation
either way, but it's a taste call and it shapes all five.
