# Ideas

Parked feature ideas — not scheduled, not designed. Anything here is a sketch, not a spec.

## "Multi" weapons

Raised 2026-07-26, deferred without picking a direction. Four readings of the idea, any of which
could be built independently:

- **Multi-stance weapons** — one weapon with swappable stances or elements. A sneak-swap gesture
  changes its whole ability set (Fire stance vs Frost stance), each stance carrying its own
  cooldowns and its own tooltip page. Biggest build of the four: `Weapon` currently assumes one
  fixed set of four ability slots, and `CooldownManager` keys cooldowns on `weaponId:slot`, so
  stances would need to be part of that key.
- **Multi-hit weapons** — abilities that land several times in a burst, or cleave several enemies
  per swing. Flurry combos, chain-cleave, multishot projectiles. Cheapest of the four; fits inside
  existing per-weapon ability code with no framework change.
- **Multi-weapon combos** — cross-weapon synergy. Use ability A on one weapon, swap, use ability B
  on another within a window, get a bonus finisher. Would need a short-lived per-player "last
  ability used" trail; `ComboTracker` is the closest existing thing to build on.
- **Multi-target abilities** — upgrade existing single-target abilities to strike several targets at
  once instead of only the locked target.

Decide which one is actually wanted before building any of it — they share a name and nothing else.
