# `/loadstructure` manual test plan

Covers the procedural boss dungeon feature: `DungeonBuilder`, `DungeonInstance`,
`DungeonMobProfiles`, `MobAbility`, `LoadStructureCommand` (see
`plugin/src/main/java/dev/rbm72/weaponsplugin/structuregen/`).

No automated tests exist for this (repo has no test framework — see root `CLAUDE.md`).
Everything below is manual, in the dev server.

## Setup

```bash
pnpm run dev
```

Builds, deploys, and restarts the local Paper server. Join in your client, `/op` yourself if needed.

Pick a flat-ish spot to stand on for the first test — the generator doesn't grade terrain yet, so
steep ground will leave rooms partly buried or floating (known gap, not a bug to report).

## 1. Basic generation (now underground)

```bash
/loadstructure frost_queen
```

- [ ] Command doesn't error; you get "Dungeon generated. Entrance at X, Y, Z — crystal is sealed
      inside until every room is cleared."
- [ ] `/tp` yourself to those exact coordinates. You should land right at (or just above) a 1-block
      hole in the ground, ringed by 4 accent-stone blocks (the dig-site marker).
- [ ] Climb down the ladder shaft. It should end by breaking straight into the dungeon's start room's
      ceiling — confirm the ladder doesn't dead-end in solid stone (i.e. the shaft actually punched
      all the way from surface to the room, not just partway).
- [ ] The dungeon itself is now centered on a random point within `structures.search-radius` (default
      250 blocks) of where you stood when you ran the command, at a random depth
      (`structures.min-depth`/`max-depth`, default 20–45 blocks below the surface at that column) —
      not at your literal feet anymore. Don't expect to see it without using the reported coordinates.
- [ ] Rooms + corridors are built from Frost Queen's palette (packed ice / snow / blue ice / sea
      lantern — matches `realm/RealmDefinitions.java`'s `frozen_reach` theme).
- [ ] Walk the corridors: doorways are clean openings through room walls, not a wall spiking through
      the middle of a room (this was a bug I caught and fixed during design — worth double-checking).
- [ ] No floating/disconnected rooms; every room reachable by corridor.

Try an unknown id to check the guard rail:

```bash
/loadstructure not_a_real_boss
```

- [ ] Message: "Unknown boss: not_a_real_boss" — no exception in console.

## 2. Combat + gating

- [ ] Each non-treasure room has ~3 themed mobs waiting (`structures.mobs-per-room` in config,
      default 3) — for Frost Queen these are "Frost Stalker" (Stray).
- [ ] One room is visibly walled off at its entrance (solid wall material where a doorway should be)
      — that's the sealed treasure room.
- [ ] Kill every mob in every room. As the last mob anywhere dies, the sealed wall should vanish and
      the treasure room becomes walkable.
- [ ] Confirm it does **not** open early — leave one mob alive in a far room and check the door stays
      shut even after clearing every other room.
- [ ] Inside the treasure room: a pedestal + chest. Open it — it should contain a Realm Crystal for
      `frost_queen` (item name "Realm Crystal: The Frozen Reach").

## 3. Side loot

- [ ] Some (not all — `structures.loot-chest-chance`, default 40%) non-treasure rooms have an extra
      chest with a real weapon item (Common or Rare rarity only). Confirm the item is a real,
      usable weapon (right-click ability works, tooltip renders).

## 4. Crystal → realm loop (the actual point of the feature)

- [ ] Take the crystal from the treasure chest.
- [ ] Right-click it. You should teleport into the Frost Queen's realm (`RealmListener` handles
      this — unchanged code path, just confirming nothing about the dungeon breaks it).
- [ ] Frost Queen spawns on the dais as normal.

## 5. Repeat on 2–3 more bosses

Pick bosses with different themes to sanity-check the palette-reuse and mob-profile mapping, e.g.:

```bash
/loadstructure inferno_warlord
/loadstructure tide_leviathan
/loadstructure worldender
```

- [ ] Each dungeon's block palette matches that boss's realm theme.
- [ ] Each dungeon's grunts match `DungeonMobProfiles` (Blaze for Inferno Warlord, Drowned w/
      trident for Tide Leviathan, Evoker for Worldender).
- [ ] Note: Weeping Colossus's grunt is a Guardian — expect weak/flopping melee AI on dry land, this
      is a known vanilla-AI limitation, not something to debug.

## 6. Cartographer treasure maps

New entry point: buying a map from a cartographer villager instead of running the admin command.

- [ ] Find or spawn a Villager, turn it into a Cartographer (give it a cartography table to claim
      the profession, standard vanilla mechanic), let it restock trades a few times (breaking and
      reclaiming its profession, or waiting through restock windows, forces new trade rolls).
- [ ] Eventually one of its trade slots should offer a "Sealed Dungeon Map: <Boss Name>" for a
      compass + emeralds (`structures.cartographer-trade-cost`, default 14). This is chance-based
      (`structures.cartographer-trade-chance`, default 50%) — if you don't see it after several
      restocks, that's the RNG, not necessarily a bug; check multiple villagers.
- [ ] Buy it. You should receive a plain "Map" item named "Sealed Dungeon Map: <Boss>" — not yet a
      real filled map.
- [ ] Right-click it. It should: generate a dungeon underground (same as `/loadstructure`, random
      location within radius of wherever you're standing), replace the item in your hand with a real
      Filled Map, and message you the entrance coordinates in chat.
- [ ] Open the received map. It should render normal terrain around the dig site as you explore, with
      a fixed red X sitting exactly on the entrance's column (the map is centered there, so the X
      should be dead-center on the map, not off to one side).
- [ ] Confirm two different maps (from two different villagers, or two purchases) can point to two
      different bosses / two different locations — "each can spawn different maps that just lead to
      different structures" was the point of this.
- [ ] Right-click the sealed map a second time (if you kept a spare from a stack) — should generate a
      **second, separate** dungeon at a new random location, not reuse the first.

## 7. Tuning knobs (optional)

In `config.yml`, under `structures:`:

```yaml
structures:
  room-count: 8
  cell-spacing: 14
  mobs-per-room: 3
  loot-chest-chance: 0.4
  search-radius: 250.0
  min-depth: 20
  max-depth: 45
  cartographer-trade-chance: 0.5
  cartographer-trade-cost: 14
```

- [ ] Bump `room-count` up/down, `/bossreload`, regenerate — dungeon size changes accordingly.
- [ ] Set `mobs-per-room: 0` — dungeon should generate with no mobs and the treasure door should
      already read as clearable (all rooms start at 0 alive, so it should open immediately after
      generation — confirm this doesn't error).

## 8. Server lifecycle

- [ ] Restart the server (or `/reload`) mid-dungeon-clear (some mobs still alive). Confirm no
      exceptions in console on shutdown (`DungeonBuilder.shutdownAll()` should cancel watchdog tasks
      cleanly).
- [ ] Restart with a sealed (unrevealed) map still in someone's inventory — confirm it survives the
      restart with its PDC tag intact and still reveals correctly afterward (PDC is saved with the
      item, so this should just work, but worth confirming once).
- [ ] After restart, the already-placed dungeon blocks are still there (it's persistent world
      content, not fight-ledgered) — confirm you can still fight through and reach the chest, even
      though the in-memory gating state reset (expect: gate may show sealed again since
      `DungeonInstance` state is not saved across restarts — if so, that's a real gap worth flagging,
      not expected behavior to silently accept).

## Known gaps going in (don't file these as new bugs, they're already tracked)

- No terrain grading — steep ground leaves rooms partly buried/floating.
- Dungeon gate state (`DungeonInstance`) is in-memory only — a server restart mid-clear may
  re-seal a partially-cleared dungeon's door with no way to re-open it without killing mobs that no
  longer exist. Worth confirming exactly what happens in step 7 above.
- Guardian-based grunt (Weeping Colossus) has weak AI out of water.
- The cartographer trade is chance-based per restock, not guaranteed — testing it may take patience
  or a few different villagers.
- Buying a sealed map costs nothing world-side; the dungeon is only built when the map is
  right-clicked, so a bought-but-unused map leaves no trace.
