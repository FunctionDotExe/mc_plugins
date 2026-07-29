# Boss endgame systems — 2026-07-28

Closes the seven gaps between "seventeen bosses exist" and "seventeen bosses are a progression, tunable,
and verifiable". Nothing here adds a boss; everything here makes the ones that exist pay off, measurable,
or testable.

Ordering was not arbitrary: the kill ledger had to land first because three other items are downstream of
"what has this player actually beaten", and the affix system had to land before telemetry because a fight
record that cannot say which affixes were armed is not comparable across runs.

---

## 1. Persistent progression — `boss.progress`

The thing three previous specs deferred, and the thing blocking every payoff.

| Piece | Where |
| --- | --- |
| Per-player ledger (kills, first clear, last clear, fastest) | `BossProgressStore`, `PlayerProgress`, `BossClearRecord` |
| One YAML per player | `plugins/WeaponsPlugin/progress/<uuid>.yml` |
| Credit on kill | `BossInstance#creditClears` |
| Worldender gate (16 distinct clears) | `Boss#requiredClears`, `Worldender#requiredClears`, enforced in `RealmManager#clearGateOpen` |
| First-clear vs repeat loot | `LootTable#firstClear` / `rollWithOdds(guaranteedChance)`, `Boss#repeatClearSignatureChance` |
| Player-facing readout | `/bossinfo` progress block, `BossMenu` icon lore |
| Leaderboards | `/bossleaderboard [id]` |

**Decisions worth keeping straight:**

- **Credit is presence, not damage.** This roster has real jobs that deal no damage (carry a Crown Shard,
  hold a control zone, run fire to the Heart). A damage-weighted ledger would tell the people doing them
  they hadn't beaten the boss.
- **The gate counts distinct bosses, never total kills**, and never counts the gated boss itself. Farming
  one encounter sixteen times teaches none of the eight vocabularies the Worldender is assembled from.
- **The gate lives at realm entry, not at spawn.** `/bossspawn` is an admin action and stays one.
  `weaponsplugin.boss.gate.bypass` exists so the capstone is testable without farming on a staff account.
  The check returns before anything is created, so a locked realm never eats the crystal.
- **First vs repeat** is expressed two ways: `firstClear` entries go straight into that player's inventory
  and never drop on the floor (a repeat clearer standing next to them must not scoop them up), and on a
  *pure farm run* — nobody present is on a first clear — the signature item becomes a
  `repeat-clear-signature-chance` roll instead of a certainty. Any first-timer present restores full pay.
- Loading is eager (all files at startup) because the leaderboard needs every row, including offline
  players, at once. Writes are write-through per kill: a kill is exactly the moment a crash must not lose.

## 2. Fight telemetry + wipe recap — `boss.telemetry`

17 bosses × 12–18 mechanics is past what anyone tunes from memory. `FightRecord` is opened at spawn and
sealed at teardown; `FightLog` writes it to `plugins/WeaponsPlugin/telemetry/<stamp>-<boss>[-wipe].json`
and keeps recent ones in memory for `/bossreport [id|list]`.

Recorded per fight: end reason, duration, boss health left, players at start, survivors, affixes armed,
participants, events fired, attack use counts. Per phase: duration, deaths, objective completions, and
**`mechanicBypassed`** — whether the phase handed over its health seam on the floor-lock timeout instead of
because its objective was done. Per death: player, phase, and the boss attack last credited with hitting
them.

- **`mechanicBypassed` is the point of the whole system.** One bypassed phase is a story about one group;
  the same phase bypassed across twenty files is a mechanic nobody can read or reach. That distinction is
  invisible from inside a fight and was previously invisible everywhere.
- **Death attribution needs the damage listener**, not the death event: `PlayerDeathEvent` can name a
  damage type but never a mechanic, and "killed by MAGIC" is exactly the useless wipe report this replaces.
  `BossDamageListener` stamps the boss's currently-casting attack onto each player it hits.
- **Deaths are attributed by location**, so a pit fall, a poured lava floor, or an add all count as the
  fight killing you. Attributing only direct boss hits would report the safest-looking fights as deadliest.
- Every recording call goes through `FightLog#safely`. Instrumentation that can cancel a hit or abort a
  phase transition is strictly worse than instrumentation that occasionally drops a row.
- `/bossreport` also lists attacks that **never fired** — a whole authored pool that never comes out means
  that phase's health band is too short for its own kit.

Wipe recap (boss alive, ≥1 death, no survivors) is pushed to chat on the spot: phase reached, boss health
left, time, the attack with the most deaths, and every phase whose objective was never engaged.

## 3. Leak + dry-run harness — `boss.harness`

`/bosstest <id|all>`. Spawns the boss, force-runs every distinct attack across every phase exactly once
against the operator (made invulnerable for the duration, then restored), tears the fight down, waits for
the batched arena rollback, then asserts:

- boss no longer registered live (a stuck registration means it can never be spawned again),
- no leftover non-player, non-item entities near the arena,
- no leftover scheduled plugin tasks,
- no unrestored ledger blocks,
- **zero SEVERE log records during the run.**

That last one is the reason this catches anything. `BossAttack#sequence` deliberately swallows exceptions
so one bad tick cannot freeze a boss — which also makes a broken attack invisible in play. The harness
installs a temporary log handler for the duration, so a swallowed throw becomes a reported failure.

The operator is made invulnerable rather than moved to creative: many attacks pick victims with
`Arena.combatants()`, which excludes creative players, and a run where the boss can find nobody to hit
exercises none of the damage paths most likely to be broken. `all` runs strictly sequentially — two
overlapping runs would each count the other's entities and tasks as their own leaks.

## 4. BossAudio, connected — `BossAudio`, `BossAttack#playTelegraphCue`

The indirection was written from the start and connected to nothing: the override map was always empty, so
all ~100 call sites played their vanilla fallback and every mechanic in the roster telegraphed visually
only.

- Keys now resolve to `weaponsplugin:<key>` when `boss-audio.custom-sounds` is on, and to the vanilla
  fallback when off. **Default off**, because a server that has not pushed the pack would otherwise go
  silent in every boss fight — a much worse failure than not yet having custom audio.
- Every key that plays is recorded with its fallback. `/bossaudio dump` emits a pack-ready `sounds.json`
  where each key delegates to its fallback's vanilla sound *event* — so a freshly generated pack is
  audibly identical to no pack, and individual entries can be replaced with real audio one at a time.
  The intended sequence is `/bosstest all` (touches every key) then `/bossaudio dump`.
- **Telegraph cues**: a low note when a cast starts and a higher, louder one on the last tick before it
  lands, on every attack in the roster, gated by `boss-audio.telegraph-cues`. Two keys for all seventeen
  bosses, not one per attack, on purpose: a cue is a *language*, and 200 distinct wind-up sounds is 200
  sounds nobody learns. Which attack it is stays the cast bar's job; what it does stays the impact
  sound's job.
- `resourcepack/assets/weaponsplugin/sounds.json` seeds the two telegraph keys, so that win lands
  immediately.

## 5. Solo-substitute audit — batch-1 rule 0.2 #7

Rule 0.2 #7: every "needs 2 players" mechanic has an explicitly designed solo substitute — **not a disabled
mechanic**. Swept every ally-dependent mechanic in the roster. Most were already compliant; two had drifted
to the opposite failure modes.

| Mechanic | Solo path | Verdict |
| --- | --- | --- |
| `TrappedAlliesMechanic` | lone captive digs out at 1× instead of rescuers× | OK — slower, never impossible |
| `FrozenPrison` (frost phase) | `ICE` instead of `PACKED_ICE`, 5 columns instead of 8 | OK |
| `BanishPocket` | tether always breakable solo | OK |
| `GroundingRodsMechanic` | rod count scales to player count | OK |
| `Shroud` | 2 anchors solo, 4 grouped | OK |
| `CrownShards` | reflect 0.18 solo vs 0.35 grouped | OK |
| `ChainTagMechanic` | chain grounds on the first hop with nobody to jump to | OK — deliberately mostly not about you |
| `ContagionLedgerMechanic` | lone player owns the whole ledger, paces themselves | OK |
| `SharedPoolMechanic` | boss is never fully immune, so an unsolved split still finishes | OK |
| `DuelLockMechanic` | falls back to the only player present | OK |
| **`FrozenHeartAttack`** | **none — every cast was an unavoidable 20-damage hit** | **FIXED** |
| **`RescueGate`** | **auto-rescue with no input at all, and a class doc claiming the opposite** | **FIXED** |

Both fixes use one shared solo verb — `SoloEscape`: **hold sneak to struggle**, at twice the channel length.

- One verb across all captive mechanics for the same reason the telegraph cue is one sound: a solo player
  learns "held means struggle" once.
- Sneak is the right input because the captive holds already crush movement and mining but not the sneak
  key, it is unmistakably deliberate, and it visibly costs the struggling player the ability to
  reposition — which is what makes escaping alone slower and more dangerous than being freed, without
  making it impossible.
- The requirement is recomputed every tick, so an ally arriving mid-hold drops it straight back to the
  group figure rather than leaving the captive on the solo clock for a rescue that already happened.
- A lone captive is prompted on the action bar. A solo substitute nobody is told about is the same as not
  having one.

`RescueGate`'s old free release was the subtler bug of the two: it cost a lone player nothing, and so
taught them nothing about a mechanic they will meet again in a group.

## 6. Config split — `boss.config.BossConfigFiles`

One file per boss under `plugins/WeaponsPlugin/bosses/<id>.yml`, migrated automatically on first run, then
overlaid onto the live config at startup and on every `/bossreload`.

The overlay is the whole trick: every tunable is already read as `bosses.<id>.<key>` off
`plugin.getConfig()`, so writing each file's values into that path at load time means several hundred
existing call sites keep working untouched — no accessor changes, nothing to half-migrate.

- **Precedence:** `config.yml` is the base, the per-boss file overrides it. After migration both hold
  identical values, so the first run changes nothing; from then on the per-boss file is the one to edit.
- `config.yml`'s `bosses:` section is **left in place rather than rewritten**, because
  `FileConfiguration#save` discards every comment in the file, and those comments are the only
  documentation several of these numbers have. Trim it by hand if you want to.
- `/bossreload` must re-apply the overlay, because `reloadConfig()` re-reads `config.yml` from disk and
  discards it. Without that, the first reload would silently revert every boss and make the split files
  look inert.

## 7. Composable affixes — `boss.modifier`

`hardMode` was a boolean, so the roster had exactly two difficulty settings. `BossModifier` is an enum and
any combination stacks.

| Affix | Effect |
| --- | --- |
| `hard` | ×1.5 health, bigger, faster — the old boolean, unchanged |
| `no-heal` | no healing of any kind inside the arena |
| `double-adds` | every summoned add spawns twice |
| `frenzy` | attack cooldowns cut below the authored floor |
| `reflect` | a share of your damage comes back at you |
| `timer` | past the deadline the boss escalates every 30s, forever |

`/bossaffix <id> <add|remove|toggle> <affix>`, `/bossaffix <id> clear`, `/bossaffix list`.
`/bosshardmode` and the menu's shift-click stay as shortcuts for `hard`.

- Armed per boss id, read at spawn, then **fixed for the fight** — an admin arming an affix mid-pull cannot
  change the rules under the group already in the arena.
- **Persisted** to `modifiers.yml`. The old boolean lived only in memory, so any restart quietly reverted
  every boss to normal with nothing to say so.
- `double-adds` is applied inside `AddManager`, the single choke point every add already goes through, so
  ~100 summon sites need no cooperation. Duplicates are deliberately anonymous — a mechanic counting "its"
  add (a hostage, a duel opponent, the one guard whose death opens the phase) must keep counting exactly
  one, or the affix would make its own objective unsatisfiable.
- `frenzy` cuts *below* `phase-cooldown-floor` on purpose. The floor exists to keep normal late-phase
  pacing readable; an affix armed specifically to remove breathing room would be pointless if the floor it
  is meant to break clamped it away.
- `reflect` is dealt with no damager, so it cannot recurse back through the boss's own outgoing multiplier
  and become a function of the boss's tuning rather than of the player's hit.
- `timer` escalates rather than wiping. An instant kill at the deadline ends runs with no read on how close
  the group was; escalating pressure ends them too, but makes the group's real damage ceiling visible on
  the way down — which is what a timer affix is armed to find out.
- **`mirror` (a genuine duplicate boss) was deliberately not built.** `BossManager` keys live fights by
  boss id and enforces one per id; a second instance needs that rule lifted, which ripples into `isLive`,
  `despawn`, and the spawn guard. That is a manager change, not an affix, and pretending otherwise would
  have meant a half-working affix. `reflect` covers the "mirrored pain" reading of the same idea.

---

## New commands

| Command | Permission | What |
| --- | --- | --- |
| `/bossaffix` | `weaponsplugin.boss.admin` | arm/disarm stackable affixes |
| `/bossreport [id\|list]` | `weaponsplugin.boss.admin` | telemetry for a recorded fight |
| `/bosstest <id\|all>` | `weaponsplugin.boss.admin` | dry run + leak assertions |
| `/bossaudio [list\|dump]` | `weaponsplugin.boss.admin` | sound keys seen / generate `sounds.json` |
| `/bossleaderboard [id]` | — | kills, or fastest clears of one boss |

New permission: `weaponsplugin.boss.gate.bypass` (op) — enter a realm whose clear gate you have not met.

## New config sections

`boss-telemetry` (enabled, keep-in-memory, keep-files), `boss-audio` (custom-sounds, telegraph-cues),
`boss-modifiers` (per-affix magnitudes). Per-boss: `required-clears`, `repeat-clear-signature-chance`.

## New data files under `plugins/WeaponsPlugin/`

`progress/<uuid>.yml` · `telemetry/*.json` · `bosses/<id>.yml` · `modifiers.yml` · `sounds.json` (on dump)

## Verification

`./gradlew build` clean. The behavioural check is `/bosstest all` on a scratch world — it is now the
cheapest way to confirm all seventeen bosses still run, leak nothing, and throw nothing.
