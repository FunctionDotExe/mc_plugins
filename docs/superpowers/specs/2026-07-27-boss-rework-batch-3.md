# Boss Rework — Design Pass, Batch 3 of 5

**Status:** design only. No code. Continues
[batch 1](2026-07-27-boss-rework-batch-1.md) and [batch 2](2026-07-27-boss-rework-batch-2.md).
Batch 1 §0 philosophy and §0.3 rulings (arena restore, pits-not-falls, arena-supplied items) apply
here unchanged.

**Batch 3 picks:** Grafted Horror, Threefold Bane, Amalgamated Bulk, Hollow Choir, Weeping Colossus.

**Vocabulary ruling (user, 2026-07-27):** these five are the **mechanical horror** batch —
sculk, darkness, redstone, pistons, observers, dispensers, note blocks, dripstone. Not creature
horror. The horror comes from *things that were built*: circuits you can trace, machines that keep
running after their reason is gone, walls that close, a listening floor.

Each boss keeps the identity already in the codebase — a stitched experiment, a wither of three ages,
a shedding ooze mountain, an evoker of borrowed voices, a ghast that shrinks as it hurts — but each is
re-expressed through machinery instead of particle spam.

### Verb map (no overlaps across batches 1–3)

| Batch 1 | Batch 2 | Batch 3 |
|---|---|---|
| duel / target priority | player-authored terrain | **systems sabotage** (Grafted Horror) |
| traversal physics | climbing the boss | **tempo / rhythm** (Threefold Bane) |
| verticality + conduction | breath + 3D space | **restraint — killing is dangerous** (Amalgamated Bulk) |
| attrition + silence | ranged-mandatory aerial | **active sound misdirection** (Hollow Choir) |
| floor loss | horde denial | **compression + light** (Weeping Colossus) |

### Deconfliction note: sculk is used twice, deliberately differently

Batch 1's **Plague Warden** uses sculk as **silence** — sneak, don't sprint, don't get heard.
Batch 3's **Hollow Choir** uses sculk as **active noise** — you must *deliberately make sound in the
right place* to steer it. Same blocks, opposite verb. If both are implemented, the Choir must never
punish sprinting, and the Warden must never reward noise-making, so the two never blur.

Batch 3's **Amalgamated Bulk** uses sculk a third way — the real vanilla **catalyst** rule (mobs dying
near a catalyst spread sculk) — which is a growth-economy mechanic, not a sound mechanic at all.

---

## 1. The Grafted Horror

### 1.1 Identity

- **Theme (kept):** a failed experiment stitched from everything that ever died in its arena — but the
  stitching is **redstone**. It is a corpse someone wired back up and never turned off.
- **Fantasy:** fighting a machine that is visibly held together by cabling you can see, follow, and
  cut.
- **Unique:** the roster's **systems-sabotage** boss. Its attacks come from bolted-on **graft
  modules** — dispensers, pistons, observers physically attached to its body — and every module is
  powered by a **visible redstone line running across the arena floor** to a power source. You don't
  out-damage the Horror. You disable it, limb by limb, by cutting circuits.

### 1.2 Core gameplay loop

At any moment two or three graft modules are live. Each has a physically visible wire: redstone dust
and repeaters running from the module, down the Horror's body, along the floor, to a power source at
the arena edge. Cutting the line (break any dust segment, or pull the repeater) kills that module's
attacks for a while.

But the Horror **re-grafts**: it drags severed lines back, rebuilds broken segments with dispensers,
and adds a new module each phase while keeping the old ones. So the fight is a maintenance race —
the group is constantly deciding which circuit is the most dangerous *right now*, and cutting that
one, knowing the others are coming back online behind them.

Meanwhile it's a melee threat in its own right, so somebody always has to be holding its attention
while the others do wire work.

### 1.3 Phases

**P1 — Two Grafts** (100–74%, exit requires: both starting circuits cut at least once)

- *What changes:* baseline. Two modules live: a **dispenser graft** (fires real arrows/fire charges in
  volleys) and a **piston graft** (shoves players into hazards).
- *New mechanic:* wire-tracing and cutting. The first cut teaches everything.
- *Strategy:* one player tanks, others trace and cut.
- *Punishes:* attacking the boss's body instead of its systems.

**P2 — Observer Graft** (74–50%, exit requires: the observer circuit cut while it is actively watching)

- *What changes:* it grafts on **observers** — the module fires when it *sees change*. Movement in its
  facing arc triggers it. The arena develops a "don't move in front of it" rule that is enforced by a
  real block, not a script.
- *New mechanic:* line-of-sight discipline plus wire work at the same time — you must approach the
  observer circuit from outside its facing arc.
- *Strategy:* flanking becomes mandatory; the group has to split around it.
- *Punishes:* running straight at it; also punishes standing still in the wrong place, because the
  older modules are still live.

**P3 — Self-Repair** (50–24%, exit requires: three circuits cut inside one repair window)

- *What changes:* it grafts a **repair module** that rebuilds severed lines on a visible timer.
  Cutting one wire is now useless — cuts have to be simultaneous.
- *New mechanic:* coordinated multi-cut. The repair module has its own wire, and cutting *that* first
  opens the window for everything else.
- *Strategy:* the group's first genuinely planned play: assign a wire each, cut on a call.
- *Punishes:* uncoordinated groups; solo-hero play.

**P4 — Seams Open** (<24%)

- *What changes:* the grafts tear loose. Modules detach and keep firing **from the floor as
  independent turrets** — real dispensers still wired to power, now stationary and destructible. The
  Horror itself is finally soft.
- *New mechanic:* the arena becomes a turret field of the fight's own history. Every module you never
  cut is now a permanent emplacement shooting at you.
- *Strategy:* clear turrets or burst the Horror — a real risk decision.
- *Punishes:* letting modules run all fight.

### 1.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Graft modules** | phase-gated, cumulative | real dispensers/pistons/observers bolted visibly onto its body, each with a lit wire | cut the wire | that module keeps attacking forever | module count fixed by phase; not by player count |
| **Redstone lines** | always visible | real dust and repeaters running down its body and across the floor, lit when powered | break any segment, or pull a repeater | — | line length fixed; more players = more parallel cutting, which P3 is built to demand |
| **Dispenser graft** | continuous while powered | the dispenser visibly clicks and fires; real arrows and fire charges | break line of sight, cut the wire | steady ranged pressure | volley size scales |
| **Piston graft** | on nearby players | pistons visibly extend with the vanilla sound and a half-second of warning | step off the piston face | shoved into hazards, off ledges (pit rules, §0.3) | piston count scales |
| **Observer graft** | P2, on movement in its arc | real observers on its body, visibly facing an arc, flashing when triggered | move outside the arc; approach from behind | triggers a heavy retaliation volley | arc fixed; more players = more accidental triggers |
| **Re-grafting** | after any cut | it visibly drags the severed line back and reconnects it, with sparks and sound | cut again, or destroy the module itself | modules come back online | repair speed fixed |
| **Repair module** | P3 | a distinct, visibly busier module with its own thick wire | cut the repair wire first to open the window | all cuts undone almost immediately | window length fixed — coordination, not numbers, is the answer |
| **Rending Claw** | melee range | it visibly rears, claws back | dodge back; it commits hard | heavy melee damage | unchanged |
| **Detached turrets** | P4 | modules physically fall off and sit on the floor, still wired and still firing | destroy them, or cut their floor wires | permanent crossfire | turret count = however many modules survived — a direct consequence of play |

### 1.5 Multiplayer

- **1 player:** P3's repair window is long enough for one player to run a planned three-cut route —
  it becomes a route-optimisation puzzle rather than a coordination one, which is a genuinely good
  solo shape. Module count is unchanged, so solo players fight the same machine, just with more
  running.
- **2 players:** one holds aggro, one cuts. P3's multi-cut becomes a real two-person call-out.
- **3–5 players:** the fight opens up — assign a wire each and P3 becomes clean, which is the reward
  for having bodies. Balanced by observer grafts triggering far more often with more people moving,
  and by P4 turret density reflecting how much the group let slide.

### 1.6 Anti-cheese

- **Face-tanking:** its body damage is modest; the modules are what kill you, and they can't be tanked
  because they're everywhere at once.
- **Ignoring mechanics:** modules never stop. A group that ignores wires fights an ever-growing
  machine gun emplacement.
- **Burst-skipping:** every phase exits on circuit objectives, never HP.
- **Camping:** dispensers and pistons specifically cover fixed positions; a camped tile gets zeroed in.
- **Ranged cheesing:** wire-cutting is melee-range floor work, and the dispenser grafts out-range
  players. Ranged players can pop modules but can't cut lines.
- **Infinite healing:** healing does not turn off a machine. The fail state is "the boss now has six
  live modules", which sustain does not address.

### 1.7 Difficulty

- **Mechanical:** Medium-High — the tracing itself is easy; doing it under fire is not.
- **Damage:** Medium-High in P4 if the group let modules accumulate; otherwise Medium.
- **Learning curve:** Low to grasp, High to optimise. "Follow the wire, break the wire" reads
  instantly. Knowing *which* wire, and cutting three at once, is the mastery.

### 1.8 Implementation difficulty

**Medium.** Needs: attached module entities on the boss body, a wire-path model (real redstone dust
placed along a computed route) with break-detection, module enable/disable state, real dispenser
firing, piston actuation, observer-arc detection, a repair timer, and module detachment in P4. All
ordinary Bukkit — the wire routing is the only fiddly piece. Good early candidate for batch 3.

---

## 2. The Threefold Bane

### 2.1 Identity

- **Theme (kept):** a wither given three ages at once, swelling larger every phase.
- **Fantasy:** three minds on three different clocks, and you have to play in the gaps between them.
- **Unique:** the roster's **tempo** boss. Its three heads fire on three independent, *audible,
  visible* redstone clocks — real repeaters and note blocks ticking at the arena edge. The whole fight
  is rhythm: you learn the beat, you act between beats, and the horror is watching all three clocks
  drift toward alignment, because when they converge everything fires at once.

### 2.2 Core gameplay loop

Three heads, three clocks, three different periods. Each clock is a physical redstone loop at the
arena edge with an attached note block, so every player can *hear* the tempo without looking. Each
head's attack fires on its own tick.

Most of the time the clocks are offset and the arena is a manageable rhythm of incoming attacks. But
they drift, and periodically two or three align — a **Convergence** — and everything fires
simultaneously, which is unsurvivable in the open.

Counterplay is to physically alter the clocks: **remove or add a repeater** to change a head's period,
either to keep the clocks apart or, more advanced, to *deliberately align two* so you get a long quiet
window on the third. That's the mastery play — using the boss's own machinery to buy time.

### 2.3 Phases

**P1 — Three Clocks** (100–76%, exit also requires: survive one Convergence)

- *What changes:* baseline. Learn each head's tell, each clock's sound, and the first Convergence.
- *New mechanic:* rhythm reading; clock tampering introduced.
- *Strategy:* dodge on the beat; find cover for Convergence.
- *Punishes:* players who fight reactively instead of on tempo.

**P2 — Swelling** (76–52%, exit requires: one clock permanently slowed)

- *What changes:* it grows, its reach grows, and the safe gaps between beats physically shrink because
  the boss occupies more of the arena.
- *New mechanic:* clock sabotage becomes mandatory — the group must pull a repeater out of one loop to
  make the fight survivable at the new size.
- *Strategy:* someone leaves the fight to do engineering while the others hold tempo.
- *Punishes:* trying to muscle through a bad rhythm.

**P3 — Coronation** (52–26%, exit requires: two Convergences broken by desync)

- *What changes:* it forces the clocks back into alignment itself, repairing sabotage on a timer.
  Convergences become frequent and lethal.
- *New mechanic:* an active tug-of-war over the machinery — it re-adds repeaters, you pull them out.
  Getting caught at a clock during a Convergence is fatal, so the sabotage runs have to be timed
  against the beat you're sabotaging.
- *Strategy:* peak expression of the boss: you must count.
- *Punishes:* poor timing, panic, and anyone who never learned the periods.

**P4 — All Three Speak** (<26%)

- *What changes:* it is screen-filling and all three clocks are locked at maximum. There is no more
  sabotage — the machinery is destroyed. What's left is a pure, fast, fully-telegraphed rhythm gauntlet
  at the tempo the group *left it at*.
- *New mechanic:* the finale is literally the tempo you engineered. A group that slowed the clocks
  well gets a fast but fair finish; a group that ignored them gets a wall.
- *Strategy:* execute the beat, burst it down.
- *Punishes:* all earlier sabotage neglect, in the most direct possible way.

### 2.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Left head — Skull Volley** | its clock tick | real wither skulls, plus a note block tone on the beat before | move off the firing line on the beat | heavy damage, blocks broken | volley size scales |
| **Centre head — Decay Nova** | its clock tick | it visibly inhales, wide ring on the floor | leave the ring or get behind cover | heavy AoE + no-regen decay | radius fixed |
| **Right head — Triple Gaze** | its clock tick | three visible beams sweep from its eyes | break the sweep line | sustained burn along the beams | sweep speed fixed |
| **The clocks** | always running | real redstone loops with repeaters and note blocks at the arena edge, audible and visible | **pull a repeater** to slow a head, add one to shift its phase | you fight at whatever tempo it chose | three clocks at all sizes |
| **Convergence** | when two or three clocks align | all three note blocks strike together; a full second of unmistakable warning | be behind cover, or have desynced the clocks beforehand | simultaneous fire from every head — near-certain death in the open | alignment schedule fixed; the *survivability* scales with how many people were free to sabotage |
| **Clock repair** | P3 onward | it visibly reaches out and re-places repeaters, with sound | pull them back out; time the run against the beat | clocks realign, Convergences accelerate | repair rate fixed |
| **Swelling** | phase transitions | it visibly grows, bone plates cracking | adjust spacing — the safe gaps physically shrink | attacks that used to miss now connect | unchanged |
| **Bone Choir adds** | on interval | real wither skeletons that visibly tether-heal it | kill them, or break the tether by moving them | it heals through your damage | count scales |

### 2.5 Multiplayer

- **1 player:** the clocks are the great equaliser — a solo player who learns the periods can sabotage
  between beats and never be overwhelmed, because Convergence is *predictable*, not random. Solo turns
  this into a rhythm-game boss, which is a strong, distinct solo identity.
- **2 players:** one holds tempo on the boss, one runs sabotage. The sabotage runner is genuinely
  exposed, so the pair has to call Convergences to each other.
- **3–5 players:** three clocks and one boss means a group can cover everything — the scaling is that
  more people generate more incoming attacks (volley/add counts scale), so the group's *own* tempo
  load rises even as their sabotage capacity does. Convergence stays lethal at every size, which keeps
  the mechanic honest.

### 2.6 Anti-cheese

- **Face-tanking:** Convergence damage is calibrated to be unsurvivable in the open regardless of gear,
  and the counter is cover and timing, not mitigation.
- **Ignoring mechanics:** ignoring clocks means fighting at maximum tempo from P2 on.
- **Burst-skipping:** every phase exits on rhythm objectives (survive one, slow one, break two).
- **Camping:** the three heads cover different geometry — line, ring, sweep — so no single tile is safe
  against all three, by construction.
- **Ranged cheesing:** clocks are at the arena edge and must be physically handled; Triple Gaze
  specifically punishes distant stationary targets.
- **Infinite healing:** Decay applies no-regen, and Convergence is a burst spike healing cannot
  precede.

### 2.7 Difficulty

- **Mechanical:** High — sustained timing pressure with no quiet moments.
- **Damage:** High at Convergence, Low-Medium otherwise. Very spiky by design.
- **Learning curve:** Medium-High. Unusually *fair*: the clocks are audible, so failure always traces
  to "I didn't count". The most learnable-feeling boss in the roster once it clicks.

### 2.8 Implementation difficulty

**Medium-Hard.** Needs: three independent tick schedulers bound to physical redstone loops, note-block
audio on the beat, player-modifiable repeaters that actually change attack periods, an alignment
detector for Convergence, boss scaling per phase, and tether-healing adds. The hard part is honesty —
the visible machinery and the actual attack timing must be the same source of truth, or the fight
becomes a lie. Non-negotiable design constraint for implementation.

---

## 3. The Amalgamated Bulk

### 3.1 Identity

- **Theme (kept):** a mountain of mass that sheds pieces and reabsorbs them — re-expressed as **sculk**
  rather than ooze. It is a growth, not a creature.
- **Fantasy:** the thing gets bigger every time something dies, and you are here to kill things.
- **Unique:** the roster's **restraint** boss — the one fight where *killing is dangerous*. Built on
  the real vanilla **sculk catalyst** rule: a mob dying near a catalyst spreads sculk. The Bulk is a
  walking catalyst. Every add you kill near it feeds it.

### 3.2 Core gameplay loop

The Bulk is surrounded by **catalyst nodes** and constantly sheds **Bulklings** — adds that are
individually weak and endlessly renewable. The reflexive play (kill everything immediately) is the
losing play: each death inside a catalyst radius spreads sculk across the floor, and sculk-covered
ground both empowers the Bulk and slows the players.

So the group has to **drag adds out of catalyst range before killing them** — kiting as a core verb,
not a fallback — or destroy the catalyst nodes first, or simply let some adds live and control them.
It is a discipline fight about *not* pressing the attack button.

Meanwhile the Bulk itself sheds and reabsorbs mass, physically shrinking and growing, and reabsorption
is the punish for letting Bulklings live too long — so the restraint has a hard limit. Kill too eagerly
and you feed it; kill too slowly and it reabsorbs. The correct play is a narrow, deliberate band.

### 3.3 Phases

**P1 — Shedding** (100–75%, exit requires: five Bulklings killed outside catalyst range)

- *What changes:* baseline. Bulklings shed continuously, catalysts are placed.
- *New mechanic:* the catalyst rule, taught by an unmissable first mistake — kill one add close in and
  watch the sculk visibly bloom.
- *Strategy:* learn to pull before killing.
- *Punishes:* reflexive cleave; AoE-happy groups get hit hardest, which is the intended lesson.

**P2 — The Spread** (75–50%, exit requires: sculk coverage pushed below a threshold)

- *What changes:* the sculk floor is now a real problem — it slows players, and **sculk shriekers**
  grow in the thickest patches, applying Darkness.
- *New mechanic:* **clearing sculk** — sculk is removable (it's a block, players can break it), so the
  group has to spend time literally cleaning the floor while managing adds.
- *Strategy:* an economy of attention: kill rate versus clean rate versus boss damage.
- *Punishes:* everything let slide in P1.

**P3 — Reabsorption** (50–24%, exit requires: three reabsorption attempts denied)

- *What changes:* the Bulk starts pulling its Bulklings back in, visibly swelling with each one it
  reclaims and healing substantially.
- *New mechanic:* the restraint band tightens hard. Now adds *must* die — but still not near a
  catalyst. Players are forced to execute the kite-and-kill under a timer.
- *Strategy:* the phase where the fight's whole thesis is tested.
- *Punishes:* both extremes — hoarding adds and mass-killing them.

**P4 — Full Mass** (<24%)

- *What changes:* it balloons to full size, stops shedding, and simply comes at the group across a
  sculk-choked arena. No more adds, no more catalysts — just a huge slow thing and whatever floor the
  group managed to keep clean.
- *New mechanic:* the finale is a straight movement fight on terrain the players either maintained or
  didn't.
- *Strategy:* kite it on clean ground.
- *Punishes:* a fully sculked arena means no room to move.

### 3.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Catalyst nodes** | placed by the Bulk | real sculk catalysts, unmistakable, with a visible influence radius marked on the floor | destroy them, or fight outside their radius | every nearby death spreads sculk | node count = 2 + 1 per 2 players |
| **Bulklings** | shed continuously | real small mobs visibly splitting off its mass | pull them out of catalyst range, then kill | killed close = arena sculks; left alive too long = reabsorbed | shed rate scales with player count |
| **Sculk spread** | on any death near a catalyst | real sculk blocks visibly blooming outward, with the vanilla sound | break sculk blocks to clear it | slowed movement, shriekers grow, the Bulk empowers | spread per death is fixed — so more killing = more spread, the group's own tax |
| **Sculk shriekers** | in thick sculk | real shriekers visibly growing out of the floor | destroy them; avoid the thickest patches | **Darkness** — the fight goes blind while a giant thing charges you | shrieker count follows coverage, not player count |
| **Reabsorption** | P3, on interval | it visibly reaches for a Bulkling and pulls, long telegraph | kill the targeted Bulkling first, or body-block the pull | it heals and swells | one target at a time |
| **Ooze Slam** | on interval | it rears its full mass, huge shadow | leave the impact zone | heavy AoE + sculk bloom at the impact point | radius scales with its current size |
| **Acid Spray** | on ranged players | it visibly bulges and fires a spread | strafe out of the spread | ground denial + armour damage | spread width scales |
| **Engulf** | on a melee player at close range | it visibly opens, slow and obvious | do not be there; allies pull the target free | swallowed, taking heavy damage until freed | solo: escape by breaking out after a fixed duration, always possible |

### 3.5 Multiplayer

- **1 player:** shed rate at the floor and two catalysts. Solo is the *purest* version of the boss —
  one player, one add at a time, pull it out, kill it, repeat. The restraint lesson is unmissable.
- **2 players:** one kites adds outward, one holds the Bulk. The pull-then-kill loop becomes a genuine
  hand-off.
- **3–5 players:** this boss punishes big groups more than any other in the roster, and does it fairly.
  Five players kill fast, and fast killing near catalysts sculks the arena in under a minute. The group
  has to *actively suppress its own damage*, which is a demand no other encounter makes. Node count
  scales so there's more space to fight in cleanly, keeping it solvable rather than punitive.

### 3.6 Anti-cheese

- **Face-tanking:** Darkness and slowed ground beat armour; Engulf ignores it.
- **Ignoring mechanics:** ignoring the catalyst rule floods the arena with sculk and ends the fight.
- **Burst-skipping:** phases exit on kill-placement, sculk coverage and denied reabsorptions.
- **Camping:** sculk spreads to any spot where fighting happens — camping *causes* the spread.
- **Ranged cheesing:** ranged players kill adds where they stand, which is exactly the mistake — so
  the boss naturally taxes ranged play unless they reposition. Acid Spray targets range specifically.
- **Infinite healing:** the fail state is terrain and boss size, neither of which healing touches.
  Notably this is the only boss where **excess DPS is itself the cheese**, and it's structurally
  punished.

### 3.7 Difficulty

- **Mechanical:** Medium — the actions are simple, the impulse control is not.
- **Damage:** Medium — slow and heavy rather than spiky.
- **Learning curve:** Low to understand, Medium-High to actually obey. Everyone understands "don't kill
  it there" in one attempt and then does it wrong anyway for another three. Very memorable for exactly
  that reason.

### 3.8 Implementation difficulty

**Medium.** Needs: real sculk catalyst placement with radius display, death-location hooks feeding
sculk spread, sculk block spread and player clearing, shrieker growth tied to coverage, boss size
scaling with mass, add shed/reabsorb accounting, and an engulf state. Vanilla does much of the sculk
work if catalysts are real — lean on that rather than reimplementing spread.

---

## 4. The Hollow Choir

### 4.1 Identity

- **Theme (kept):** an evoker that sang so many voices into itself it lost its own — re-expressed as a
  machine of **note blocks, bells and sculk sensors** that is still playing a song nobody remembers
  writing.
- **Fantasy:** it doesn't see you. It *hears* you. And you can lie to it.
- **Unique:** the roster's **active sound misdirection** boss. Where Plague Warden's sculk demands
  silence, the Choir demands the opposite — you must **deliberately make noise in the right place** to
  steer its attention. Note blocks and bells are tools, not decoration.

### 4.2 Core gameplay loop

The Choir is blind. It targets whatever the arena's **sculk sensors** last heard, and the arena is
ringed with **note blocks and bells** that any player can strike. So the loop is: make noise away from
where you want it to attack, then attack from the quiet side.

Its own attacks are sound-shaped — a **Choir Wail** that fires along the path sound travelled, fanged
lines that erupt where noise was made, vex swarms that home on the loudest player. And because
*fighting is loud*, every hit you land makes you a target. Damage and safety are directly opposed at
all times.

Layered on: it periodically forces **Darkness** via shriekers, so for stretches of the fight the group
genuinely cannot see, and sound is the only channel anyone has.

### 4.3 Phases

**P1 — The Listening** (100–75%, exit requires: three attacks successfully misdirected)

- *What changes:* baseline. Sensors, note blocks, and the rule that noise draws attacks.
- *New mechanic:* misdirection — hit a note block, watch the attack land there instead of on you.
- *Strategy:* learn to alternate between making noise and being quiet.
- *Punishes:* fighting loudly in one place.

**P2 — The Dark** (75–50%, exit requires: survive one full Darkness cycle)

- *What changes:* shriekers apply Darkness in waves. Vision goes. Now the *only* reliable information
  is audio — the note blocks the group is striking, the bells, and the Choir's own tells.
- *New mechanic:* fighting on audio cues alone. The arena supplies torches and lanterns, so light is a
  contested, placeable resource the Choir actively snuffs.
- *Strategy:* the group establishes an audio protocol — designated ringers, designated attackers.
- *Punishes:* groups that rely entirely on visuals; also punishes silence, because a silent group
  cannot misdirect.

**P3 — The Round** (50–24%, exit requires: the correct three-note sequence played)

- *What changes:* it starts **singing a phrase** — a real note-block melody, three notes, played
  audibly and repeated. Playing that phrase back on the arena's note blocks shatters its ward.
- *New mechanic:* a genuine call-and-response puzzle, with a fresh phrase each cycle, in the dark,
  while it hunts. Not a memory *test* — the phrase repeats continuously, so it is a listening and
  execution challenge, not a gotcha.
- *Strategy:* the single most distinctive moment in the roster: a boss fight that briefly becomes
  music.
- *Punishes:* wrong notes (it retaliates at the note block that played wrong), and panic.

**P4 — All Voices** (<24%)

- *What changes:* everything sings at once — every note block in the arena fires continuously, so
  misdirection stops working because everything is loud. It reverts to hunting the *nearest* player.
- *New mechanic:* the tools are removed and the group must simply fight it in the open dark, using
  the light they preserved.
- *Strategy:* a deliberate, brutal simplification — the payoff for a complex fight is a clean finish.
- *Punishes:* groups that never learned to fight without the crutch.

### 4.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Sculk sensors** | always | real sensors ringing the arena, visibly pulsing when they hear something | make noise elsewhere deliberately | it targets whoever it last heard — usually whoever last hit it | sensor count fixed |
| **Note blocks / bells** | player-activated | real note blocks and bells around the arena | strike them to pull its attention | without using them, every attack lands on the attackers | count fixed — a shared tool, not a scaling knob |
| **Choir Wail** | on interval | it visibly inhales; the attack travels along the path sound took, visible on the floor | be off that path | heavy damage along the line | one wail per cycle |
| **Fang Line** | at the last heard position | fangs visibly erupt in a line from the sensor toward the noise | make the noise somewhere you aren't | heavy damage, briefly rooted | line count scales |
| **Vex swarm** | on the loudest player | real vexes spawning visibly | quiet down; hand off aggro by having someone else make noise | sustained harassment during precise mechanics | swarm size scales |
| **Darkness** | P2 onward, in waves | real shriekers firing, the vanilla Darkness effect | place and defend torches/lanterns (arena-supplied) | you fight blind | wave timing fixed |
| **Torches/lanterns** | arena-supplied | real light sources players place | place them; it snuffs them | permanent blindness in later phases | supply scales with players |
| **The Phrase** | P3 | it audibly sings three real note-block tones, repeating | play the same three notes back on the arena's note blocks | its ward holds and it keeps hunting | phrase length fixed at 3 — never longer for bigger groups |
| **Grand Illusion** | P3 onward | multiple identical Choirs appear; **only the real one makes sound when it moves** | listen, don't look | attacking an illusion does nothing and it punishes you | illusion count fixed |

### 4.5 Multiplayer

- **1 player:** misdirection is entirely under your control — you make all the noise, so you always
  know where its attention is. Solo is the *cleanest* version of the puzzle. The Phrase stays three
  notes, well within one player's reach.
- **2 players:** the classic split — one is the noise, one is the knife. Genuinely satisfying, and the
  first size where misdirection feels like teamwork rather than self-management.
- **3–5 players:** noise discipline gets much harder because everyone is making it. Groups must
  explicitly designate who is loud, and P3's phrase needs one clear caller. The arena's note blocks
  stay at a fixed count so a big group cannot brute-force the puzzle by spamming every block — wrong
  notes are punished, so spamming is actively bad.

### 4.6 Anti-cheese

- **Face-tanking:** hitting it makes noise which makes you the target; tanking is self-defeating by
  construction.
- **Ignoring mechanics:** without misdirection every attack lands on the damage dealers.
- **Burst-skipping:** phases exit on misdirections, a survived Darkness cycle, and the phrase.
- **Camping:** camping means all your noise comes from one place, and it will bury that place.
- **Ranged cheesing:** ranged attacks are still noise, and the wail travels the sound path back to
  source. Range provides no anonymity, which is a genuinely novel anti-ranged answer.
- **Infinite healing:** vexes, Darkness and illusions are attention problems, not throughput problems.

### 4.7 Difficulty

- **Mechanical:** High — inverted targeting plus blindness plus a live puzzle.
- **Damage:** Medium — it kills through confusion, not numbers.
- **Learning curve:** High initially. This is the batch's most conceptually unusual fight, and the one
  most likely to be someone's favourite. Worth accepting a steeper curve for.

### 4.8 Implementation difficulty

**Hard.** Needs: a noise-event model (who made sound, where, how recently) driving target selection,
real note block and bell interaction with pitch tracking, real sculk sensors, Darkness application,
placeable player light with boss snuffing, a call-and-response phrase system with correct/incorrect
detection, and sound-gated illusions. The noise model is novel work and the whole boss depends on it
being *legible* — if players can't tell what the boss heard, the fight is unfair. Prototype the noise
model before committing to the rest.

---

## 5. The Weeping Colossus

### 5.1 Identity

- **Theme (kept):** a titanic ghast that shrinks and accelerates as it hurts — but the horror is the
  **room**, not the creature. It is chained inside a machine that closes on it, and on you.
- **Fantasy:** the walls come in. The thing in here with you gets faster every time the room gets
  smaller.
- **Unique:** the roster's **compression + light** boss. Real **piston walls** physically advance
  inward on a schedule, real **dripstone** drips from above, and the fight ends in a tiny, dark,
  crowded box with something very fast in it. It is the inverse of Void Sovereign — that boss removes
  floor, this one removes *space*.

### 5.2 Core gameplay loop

The arena is a machine: piston walls on all sides, driven by visible redstone, advancing one course
every cycle. Players can **jam the pistons** — break the redstone feeding a wall section, or wedge a
block — to buy space, but the Colossus repairs them.

As space shrinks, the Colossus shrinks with it and gets correspondingly faster, so the fight
continuously trades room for speed. Meanwhile **pointed dripstone** grows from the ceiling and falls,
and its tears form real water that pools on the floor, and in P3 the lights go out.

Every phase the room is smaller. There is no phase where you get space back. The tension is
monotonic, and the ending is claustrophobic by design.

### 5.3 Phases

**P1 — The Chamber** (100–76%, exit requires: two wall sections jammed)

- *What changes:* baseline. Full-size arena, slow huge Colossus, walls begin advancing.
- *New mechanic:* piston walls and jamming.
- *Strategy:* learn the wall cycle; jam early.
- *Punishes:* ignoring the walls — space lost early is never recovered.

**P2 — Dripstone** (76–52%, exit requires: survive one full drip cycle without a player pinned)

- *What changes:* real **pointed dripstone** grows from the ceiling above marked positions and falls.
  Fallen dripstone stays as floor obstruction, further reducing usable space.
- *New mechanic:* ceiling awareness — the danger is now vertical, in a room already shrinking
  horizontally.
- *Strategy:* the group must look up while managing walls and a boss.
- *Punishes:* tunnel vision; also punishes clustering, since dripstone targets clusters.

**P3 — The Dark** (52–26%, exit requires: three wall sections jammed during Darkness)

- *What changes:* the piston walls **seal the ceiling**. This is not the Darkness status effect — it
  is real, physical, light-level darkness caused by the room closing over your head, and the fix is
  the ordinary Minecraft one: put torches up. (Deliberately distinct from Hollow Choir's supernatural
  Darkness, which cannot be lit and forces the switch to audio. Same feeling, opposite counterplay —
  see the batch 4 audit.)
- *New mechanic:* light as construction — arena-supplied torches and lanterns, placed and defended,
  while doing piston work by feel. Every torch placed is a tile of the room you can still fight in.
- *Strategy:* the horror phase, and the one people will describe afterwards.
- *Punishes:* poor light discipline; panicked movement in a crowded room.

**P4 — The Box** (<26%)

- *What changes:* the walls stop at their final position: a small chamber. The Colossus is now tiny
  and extremely fast, ricocheting around a space full of fallen dripstone and standing water.
- *New mechanic:* a pure close-quarters duel in terrain the fight created. No more walls to jam, no
  more mechanics — just execution.
- *Strategy:* clean, tight, fast.
- *Punishes:* a group that let the walls run unopposed now fights in a genuinely tiny box.

### 5.4 Mechanics

| Mechanic | Trigger | Visual tell (physical) | Counterplay | Failure punishment | Multiplayer scaling |
|---|---|---|---|---|---|
| **Piston walls** | on a visible cycle | real pistons with visible redstone feed and the vanilla extend sound; a clear warning tick before each advance | jam a section — break its redstone, or wedge a block into the piston head | the room permanently shrinks | wall sections = 4; more players means more hands but the same four sections |
| **Wall repair** | after any jam | it visibly repairs the redstone feed | re-jam; jams are temporary by design | walls resume advancing | repair rate fixed |
| **Crush** | standing against an advancing wall | the wall is visibly and audibly moving | step away — the telegraph is generous | heavy damage and forced repositioning (never an instant kill) | unchanged |
| **Pointed dripstone** | P2 onward, on clusters | stalactites visibly grow above marked floor tiles over ~2s | move out from under it | heavy damage; the fallen dripstone stays as permanent floor obstruction | growth count = 1 + 1 per player |
| **Tears** | continuous | real water dripping and pooling on the floor | avoid deep pools; they slow you | slowed in a shrinking room | pool spread fixed |
| **Sorrowful Wail** | on interval | it visibly swells before the cry | break line of sight behind dripstone or wall geometry | heavy AoE + brief disorientation | radius scales with its current size — smaller Colossus, smaller wail, so the trade is real |
| **Collapsing Gaze** | on the furthest player | its eye visibly fixes on one target, a beam traces | break line of sight | sustained damage on the isolated target | targets furthest — the anti-ranged tax |
| **Contraction** | phase transitions | it visibly shrinks and speeds up, unmistakable | adjust: it becomes a dodging problem instead of a spacing problem | attacks that were readable become fast | unchanged |
| **Sealed ceiling** | P3 | the walls visibly close overhead; the room's real light level drops, corner by corner | place torches and lanterns (arena-supplied); it snuffs them, so relight | fighting a fast boss in genuine darkness in a small room | light supply scales with players |
| **Tear Barrage** | on interval | real projectiles fired in a visible spread | strafe; use dripstone as cover | ground denial in a room with little ground left | volley size scales |

### 5.5 Multiplayer

- **1 player:** four wall sections is a lot for one person, so the solo wall cycle is slower and the
  jam duration longer — solo players trade a comfortable room for constant work. Dripstone count at
  the floor. Solo is the most claustrophobic and arguably the most thematically correct experience.
- **2 players:** one jams, one fights, alternating. The pair can hold roughly half the walls, so the
  room ends up medium-sized — a legible consequence.
- **3–5 players:** a coordinated five can hold all four walls almost indefinitely, so the boss scales
  by making the *room* the limiting factor rather than the walls: more players in a small room means
  less space each, dripstone targets clusters, and the wail catches groups. Big groups keep more space
  and need more of it. Self-balancing without a single number changing.

### 5.6 Anti-cheese

- **Face-tanking:** the fail state is space, not health. You cannot tank a wall.
- **Ignoring mechanics:** unjammed walls end the fight in a box too small to dodge in.
- **Burst-skipping:** phases exit on jams and survived cycles.
- **Camping:** camping is impossible in a moving room — the geometry itself invalidates fixed
  positions, which is the cleanest anti-camp mechanism in the roster.
- **Ranged cheesing:** Collapsing Gaze specifically hunts the *furthest* player, and the room shrinks
  until range doesn't exist. A ranged strategy has a built-in expiry date.
- **Infinite healing:** healing does not create space or light.

### 5.7 Difficulty

- **Mechanical:** Medium-High — wall management is simple, doing it while the room closes and the
  lights die is not.
- **Damage:** Medium — the Colossus is not a heavy hitter; the room is the threat.
- **Learning curve:** Low-Medium. "The walls are closing, stop them" needs no explanation whatsoever.
  The depth is in *when* to spend time jamming versus damaging.

### 5.8 Implementation difficulty

**Medium-Hard.** Needs: piston wall structures with real actuation and a jam/repair state, redstone
feeds players can break, arena geometry that shrinks safely (with the ledger restoring it), pointed
dripstone growth and fall, water pooling, boss size/speed scaling per phase, Darkness with placeable
player light, and a hard guarantee that the final chamber is always large enough to be winnable at
every group size. That last constraint is the real work — the room must never compress into an
unwinnable state.

---

## 6. Batch notes

### 6.1 Shared systems added by batch 3

On top of batch 1's five and batch 2's five:

11. **Redstone-circuit props** — wires players can trace and cut, repeaters they can pull, pistons
    they can jam, dispensers that fire. Used by Grafted Horror, Threefold Bane, Weeping Colossus.
    Build once, three bosses benefit.
12. **Audible-tempo scheduler** — attack timing bound to real note blocks and redstone loops, where
    the visible machinery *is* the schedule. Threefold Bane needs it; the Worldender capstone can
    reuse it.
13. **Noise/attention model** — who made sound, where, how recently, driving boss targeting.
    Hollow Choir only, but it is the novel system of this batch and should be prototyped first.
14. **Sculk-catalyst growth economy** — deaths near catalysts spread real sculk. Amalgamated Bulk.
15. **Moving arena geometry** — piston walls that shrink the playable space, with a winnability floor.
    Weeping Colossus.

### 6.2 Suggested implementation order within batch 3

Grafted Horror (Medium, conventional redstone props) → Amalgamated Bulk (Medium, leans on vanilla
sculk) → Threefold Bane (needs the tempo scheduler) → Weeping Colossus (moving geometry) →
Hollow Choir (novel noise model, highest risk).

### 6.3 Remaining roster

- **Batch 4:** Voidwyrm, plus a consolidation pass on anything from batches 1–3 that needs revision
  once the shared systems list has settled.
- **Batch 5:** The Worldender (8-phase capstone). By then every vocabulary exists, so the capstone can
  be assembled from established verbs rather than inventing new ones — which is exactly how an
  8-phase fight stays coherent instead of becoming a highlight reel.

### 6.4 Note for batch 4

Batch 4 is light on bosses (Voidwyrm alone), so it's the natural place to also do a **cross-roster
audit**: check that no two bosses have drifted into the same verb, that the shared systems list is
minimal, and that the anti-cheese answers aren't all the same three tricks. Worth doing before the
capstone locks anything in.
