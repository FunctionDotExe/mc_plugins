# Weapon Expansion: Multi-Ability Framework + 15 Weapons + /opcooldown

## Context

`WeaponsPlugin` (Paper plugin, `plugin/src/main/java/dev/rbm72/weaponsplugin`) currently has 7 weapons,
each with exactly one right-click ability plus melee. `Weapon` is the abstract base every weapon
extends; `WeaponRegistry` looks weapons up by id or by matching an `ItemStack`'s PDC tag;
`CooldownManager` owns all cooldown timers, action bar/boss bar display, and durability-as-cooldown
visualization; `Fx` holds shared particle/sound helpers.

This spec covers:
1. Extending the framework to support 3 abilities + 1 ultimate + 1 passive per weapon.
2. 15 new/rebuilt weapons built on that framework.
3. A `/opcooldown` toggle command for cooldown-free testing.

## 1. Multi-ability framework

### Input routing

Right-click is routed to one of four slots based on hand + sneak state:

| Hand | Sneaking | Slot |
|---|---|---|
| Main hand | No | Ability 1 |
| Main hand | Yes | Ability 2 |
| Off hand | No | Ability 3 |
| Off hand | Yes | Ultimate |

Off-hand access uses vanilla F (swap hands) — no new keybinds. `WeaponInteractListener` handles
`PlayerInteractEvent` for both `EquipmentSlot.HAND` and `EquipmentSlot.OFF_HAND`, identifies the
weapon in whichever hand triggered, and routes to the matching slot.

### `Weapon` base class changes

Replace the single `executeAbility(Player)` + `cooldownSeconds()` pair with:

```java
public abstract void ability1(Player player);
public abstract double ability1CooldownSeconds();
public abstract List<Component> ability1Lore();

public void ability2(Player player) {}                 // no-op default
public double ability2CooldownSeconds() { return 0; }
public List<Component> ability2Lore() { return List.of(); }

public void ability3(Player player) {}
public double ability3CooldownSeconds() { return 0; }
public List<Component> ability3Lore() { return List.of(); }

public void ultimate(Player player) {}
public double ultimateCooldownSeconds() { return 0; }
public List<Component> ultimateLore() { return List.of(); }
```

Existing 7 weapons: their current `executeAbility` becomes `ability1`; `cooldownSeconds()` becomes
`ability1CooldownSeconds()`. They don't implement ability2/3/ultimate — defaults are no-ops, so they
keep working exactly as before with no behavior change.

Passive hooks (all default no-op, overridden selectively):

```java
public void onTick(Player player) {}
    // called every 10 ticks while this weapon is held in main hand

public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {}
    // called on melee hits landed with this weapon in main hand

public void onKill(Player attacker, LivingEntity victim) {}
    // called when onMeleeDamage's hit was lethal
```

A new `WeaponTickListener`-style repeating task (owned by `WeaponsPlugin`, runs every 10 ticks over
all online players) calls `onTick` for whoever is holding a matching weapon main-hand. Melee hooks are
wired into a new `WeaponDamageListener` on `EntityDamageByEntityEvent`, firing `onMeleeDamage` (and
`onKill` if the hit was lethal) when the damager is a player whose main-hand item matches a weapon.

`abilityLore()` (used for the item's lore text) becomes the concatenation of ability1Lore/ability2Lore
/ability3Lore/ultimateLore, each prefixed appropriately (e.g. "Right-click:", "Shift+right-click:",
"Off-hand:", "Off-hand+Shift (Ultimate):") — `createItem()` in `Weapon` builds this instead of each
weapon hand-writing the trigger phrasing.

### Cooldowns

`CooldownManager` keys active cooldowns by `(UUID, weaponId, slot)` instead of `(UUID, weaponId)`,
where `slot` is an enum `{ABILITY1, ABILITY2, ABILITY3, ULTIMATE}`. `start(player, weapon, slot)`
looks up the right duration/lore via the slot. Durability-as-cooldown visualization only tracks
ABILITY1 (the primary, most-used action) — other slots get action-bar countdown text only. ULTIMATE
additionally gets the boss bar (existing `BOSS_BAR_THRESHOLD_MS` logic still applies per-slot, so any
slot whose duration crosses the threshold gets one — in practice only ultimates will, given their
longer cooldowns).

### Files touched

- `items/Weapon.java` — ability slots, passive hooks, lore assembly.
- `items/weapons/*.java` (existing 7) — rename `executeAbility`→`ability1`, `cooldownSeconds()`→
  `ability1CooldownSeconds()`. No behavior change.
- `ability/CooldownManager.java` — slot-aware keying.
- `listeners/WeaponInteractListener.java` — hand/sneak routing.
- New `listeners/WeaponDamageListener.java` — melee passive hooks.
- New `ability/WeaponTickListener.java` (or a scheduled task in `WeaponsPlugin`) — tick passive hook.
- `WeaponsPlugin.java` — register new listener/task.

## 2. The 15 weapons

All follow the framework above: ability1/2/3 + ultimate + one passive. Rarity sets the stat
multiplier and lore color per the existing `Rarity` enum (Common/Rare/Epic/Legendary/Mythic).

Where the user's original idea specified only 2 pre-ultimate abilities (Void Blade, Solar
Greatsword), a third complementary ability was added to fill ability3 and keep every weapon
consistent with the 4-slot framework (noted per-weapon below).

Lunar Blade's ultimate was reworked from "change world time to night" (which would affect every
other online player) into a self-buff, "Eclipse," so it stays single-player-scoped.

| # | Weapon | Rarity | Item |
|---|---|---|---|
| 1 | Tidal Trident | Epic | Trident |
| 2 | Void Blade | Legendary | Netherite Sword |
| 3 | Solar Greatsword | Legendary | Netherite Sword |
| 4 | Lunar Blade | Epic | Diamond Sword |
| 5 | Plague Scythe | Rare | Diamond Hoe |
| 6 | Dragon Fang | Epic | Netherite Axe |
| 7 | Celestial Bow | Legendary | Bow |
| 8 | Blood Reaper | Epic | Netherite Hoe |
| 9 | Chrono Blade | Mythic | Diamond Sword |
| 10 | Storm Chakrams | Rare | Golden Hoe |
| 11 | Earthbreaker Axe | Rare | Iron Axe |
| 12 | Necromancer Staff | Epic | Stick |
| 13 | Arcane Staff *(rebuild of existing)* | Mythic | Blaze Rod |
| 14 | Sakura Blade | Rare | Iron Sword |
| 15 | Starbreaker | Mythic | Netherite Sword |

### 1. Tidal Trident (Epic, Trident)
- **Passive**: Dolphin's-Grace-style swim speed boost while in water (`onTick`, apply/refresh potion effect when `player.isInWater()`).
- **Ability1** — Tidal Wave: cone-shaped burst of water particles in front of player; damages + knocks back entities in the cone.
- **Ability2** — Whirlpool: spawns a pull point ~5 blocks ahead; over ~2s, nearby entities are pulled toward its center and take tick damage.
- **Ability3** — Water Jet: riptide-style self-launch (forward + upward velocity), damaging/knocking back anything in the player's path during flight.
- **Ultimate** — Tidal Tsunami: large expanding wave (reuse `Fx.expandingRings`-style growth) outward from the player; heavy damage, strong knockback, and brief Slowness to anything caught, on a long cooldown.

### 2. Void Blade (Legendary, Netherite Sword)
- **Passive**: on melee hit, small chance (~10%) to zero the `DamageModifier.ARMOR` reduction for that hit (bypasses armor).
- **Ability1** — Phase Step: raytrace forward through blocks up to a max range, teleport player to first safe (non-solid, non-lava) location found.
- **Ability2** — Void Rift: spawns a pull point in front of the player; nearby entities pulled toward it over ~1.5s with small damage (shorter/weaker than Tidal Trident's whirlpool — sword-scale, not staff-scale).
- **Ability3** — Void Slash *(filled-in slot)*: short phase-dash forward, damaging all entities passed through.
- **Ultimate** — Black Hole: pulls all entities within radius toward a center point over ~2s, then detonates for heavy AoE damage + knockback.

### 3. Solar Greatsword (Legendary, Netherite Sword)
- **Passive**: +50% melee damage vs. undead (`onMeleeDamage`, check `victim` type against Zombie/Skeleton/etc. family).
- **Ability1** — Sunbeam: forward hitscan line (particle beam), damages all entities struck.
- **Ability2** — Solar Explosion: AoE burst centered on player; damage + brief Fire to nearby enemies.
- **Ability3** — Radiant Cleave *(filled-in slot)*: forward cone melee swing that also applies brief Blindness (holy light) to enemies hit.
- **Ultimate** — Pillar of Light: summons a vertical light-particle pillar at the targeted location; repeated damage ticks for its duration, extra effective (bonus damage/ignite) against undead.

### 4. Lunar Blade (Epic, Diamond Sword)
- **Passive**: bonus melee damage when `!player.getWorld().isDayTime()` (`onMeleeDamage`).
- **Ability1** — Crescent Slash: launches a crescent-shaped projectile (reuse Snowball-tag pattern like `ArcaneStaff`) dealing damage on hit.
- **Ability2** — Moon Dash: short teleport-dash forward, damaging entities passed through.
- **Ability3** — Gravity Field: pulls nearby entities' velocity downward/inward briefly (mild levitation-reversal), light damage.
- **Ultimate** — Eclipse *(reworked from "change world time")*: self-buff for ~8s — Strength, bonus crit-style melee damage, Speed — with a dark-particle aura around the player. No world-time change.

### 5. Plague Scythe (Rare, Diamond Hoe)
- **Passive**: on `onKill`, spawn a lingering poison cloud at the victim's location that damages/poisons nearby hostile entities and other players for a few seconds.
- **Ability1** — Poison Cloud: creates a stationary gas cloud at target-ahead location; entities inside take poison + periodic damage for its duration.
- **Ability2** — Infect: melee-range strike applying strong Poison + Weakness directly to target.
- **Ability3** — Exploding Spores: thrown projectile that bursts into a small poison cloud on impact.
- **Ultimate** — Plague Spread: infects all enemies in radius; poison "jumps" between infected targets within a chain radius, repeating for the ultimate's duration.

### 6. Dragon Fang (Epic, Netherite Axe)
- **Passive**: Fire Resistance maintained while holding (`onTick`, refresh effect).
- **Ability1** — Dragon Roar: AoE knockback burst around/in front of player (minimal damage, big pushback).
- **Ability2** — Fire Breath: cone of fire particles in front of player; damages + ignites entities hit.
- **Ability3** — Wing Leap: upward/forward leap with Slow Falling for a few seconds.
- **Ultimate** — Dragon Form: brief (~8-10s) self-buff — bonus melee damage, fire immunity, Wing Leap cooldown reset, fire-trail particles on movement.

### 7. Celestial Bow (Legendary, Bow)
- **Passive**: track shots fired (PDC counter on player or in-memory map); every 5th shot explodes on impact.
- **Ability1** — Rain of Stars: fires a volley of arrow-projectiles arcing down onto a target area.
- **Ability2** — Homing Shot: fires a projectile with a short tracking runnable that nudges velocity toward the nearest enemy in front each tick.
- **Ability3** — Comet Shot: single heavy projectile, larger explosion/knockback on impact than a normal shot.
- **Ultimate** — Meteor Shower: repeated projectile/particle "meteor" impacts across a target area over several seconds.
- *(Arrows fired by abilities are instant, tagged projectiles like `ArcaneStaff`'s Snowball — not drawn/held vanilla bow shots — so `onProjectileHit` in `MagicProjectileListener`'s pattern handles impact logic.)*

### 8. Blood Reaper (Epic, Netherite Hoe)
- **Passive**: melee damage bonus scales up as the attacker's own HP drops (`onMeleeDamage`, e.g. up to +50% bonus at low HP).
- **Ability1** — Blood Sacrifice: costs the caster HP, deals an AoE damage burst around them.
- **Ability2** — Lifesteal Slash: melee-range strike, heals the caster a percentage of damage dealt.
- **Ability3** — Blood Explosion: AoE burst around player; damage to enemies, heal to caster based on damage dealt.
- **Ultimate** — Drain: pulls health from all enemies in radius over ~2-3s, healing the caster (up to max HP).

### 9. Chrono Blade (Mythic, Diamond Sword)
- **Passive**: melee hits landed within ~1s of one of this weapon's cooldowns finishing grant that cooldown a flat reduction on its next use ("perfect timing").
- **Ability1** — Slow Field: applies Slowness + Mining Fatigue to enemies (not the caster) in radius.
- **Ability2** — Afterimage Dash: dash forward, leaving stationary particle/armor-stand afterimages along the path that fade after a few seconds.
- **Ability3** — Rewind: teleports the caster back to a tracked position from ~5 seconds ago (rolling position buffer per player).
- **Ultimate** — Freeze Time: applies Slowness IV + Jump Boost-negation + Mining Fatigue to all nearby entities except the caster, for a few seconds.

### 10. Storm Chakrams (Rare, Golden Hoe)
- **Passive**: catching a returning chakram (i.e. it completes its return-to-player flight) grants a flat cooldown reduction to this weapon's other abilities.
- **Ability1** — Returning Blade: throws a projectile that flies out, then arcs back to the player, damaging on both legs.
- **Ability2** — Chain Lightning Throw: thrown chakram, on hit, arcs lightning-style bonus damage to nearby enemies (reuse the chain-lightning pattern from `Stormbreaker`).
- **Ability3** — Orbiting Blades: spawns 2-3 particle "blades" that orbit the player for a duration, damaging enemies that get close.
- **Ultimate** — Chakram Storm: bursts multiple chakrams that spiral outward from the player, repeatedly hitting anything nearby for a few seconds.

### 11. Earthbreaker Axe (Rare, Iron Axe)
- **Passive**: knockback resistance attribute bonus while held.
- **Ability1** — Ground Split: a crack extends forward from the player; damages + briefly slows enemies standing along it.
- **Ability2** — Stone Wall: temporarily places a small wall of stone blocks in front of the player, auto-removed after a fixed duration (tracked block list, restored on removal or plugin disable).
- **Ability3** — Boulder Throw: throws a falling-block-style projectile dealing AoE damage on impact.
- **Ultimate** — Earthquake: expanding rings of cracks around the player (`Fx.expandingRings`); each ring damages + knocks up entities it reaches.

### 12. Necromancer Staff (Epic, Stick)
- **Passive**: on `onKill`, briefly buff the caster's currently-active summoned minions' damage/speed.
- **Ability1** — Raise Skeletons: summons 1-2 allied skeletons (targeting non-owner entities) that despawn after a duration.
- **Ability2** — Summon Ghosts: summons a couple of harassment-focused allied mobs (e.g. invisible/vex-style) for a duration.
- **Ability3** — Bone Barrage: throws a spread of projectiles dealing damage.
- **Ultimate** — Undead Guardian: summons one strong, larger-scaled ally (boosted health/damage attributes) for an extended duration.
- *(Needs a small `SummonManager` helper to track owned summons per player and enforce their despawn timers/cleanup on plugin disable — new file `ability/SummonManager.java`.)*

### 13. Arcane Staff — rebuild (Mythic, Blaze Rod)
Existing `ArcaneStaff.java` is rewritten to use the new framework instead of its current single-bolt ability.
- **Passive**: while the player's velocity is ~0 ("standing still"), this weapon's active cooldowns tick down ~10% faster (`onTick`).
- **Ability1** — Magic Missiles: fires 3 quick tagged projectiles in short succession, each dealing moderate damage.
- **Ability2** — Energy Beam: instant hitscan line, damages all entities struck.
- **Ability3** — Blink: short-range forward teleport (arcane-themed version of Void Blade's phase step, shorter range, no wall-phasing).
- **Ultimate** — Arcane Laser: channeled beam (repeated hitscan ticks over ~2s) dealing heavy cumulative damage to anything in the line.

### 14. Sakura Blade (Rare, Iron Sword)
- **Passive**: consecutive melee hits within a short time window stack a Speed effect (caps at a few stacks), resets if the player doesn't land a hit within the window.
- **Ability1** — Cherry Blossom Slash: forward cone melee strike with petal particles.
- **Ability2** — Petal Dash: dash forward through enemies, damaging them, leaving a petal particle trail.
- **Ability3** — Bloom Explosion: AoE petal burst around the player, damage + knockback.
- **Ultimate** — Petal Field: fills the target area with petals that deal damage-over-time to enemies standing in it for its duration.

### 15. Starbreaker (Mythic, Netherite Sword)
- **Passive**: landing any ability grants a short "Momentum" window; using a *different* ability slot within it deals bonus damage, encouraging chaining across all 4 slots.
- **Ability1** — Star Spread: throws a spread of small star projectiles, damage on hit.
- **Ability2** — Warp Strike: teleports the caster to just behind/at their target and strikes for bonus damage.
- **Ability3** — Supernova: AoE burst around the player, heavy damage + knockback, large particle nova.
- **Ultimate** — Galaxy's End: pulls nearby entities toward a center point over ~2.5s, then collapses into a large multi-stage explosion with a "galaxy" particle showcase (Portal/Dragon Breath/End Rod/colored Dust).

## 3. `/opcooldown` command

- Usage: `/opcooldown` — toggles cooldown-bypass on/off for the sender, no args.
- New permission `weaponsplugin.opcooldown` (default: `op`), registered in `plugin.yml` alongside the existing two permissions.
- New `commands/OpCooldownCommand.java` implementing `CommandExecutor`, holding an in-memory `Set<UUID>` of players with bypass enabled (not persisted — matches the existing cooldown state, which is also in-memory and resets on restart).
- `WeaponInteractListener` checks this set before the existing `cooldowns.isOnCooldown` check for any slot: if the player has bypass on, skip both the cooldown check and `cooldowns.start(...)` entirely, so every ability/ultimate fires with no cooldown at all.
- Sends a confirmation message on toggle ("Cooldown bypass: ON/OFF").

## Config

Each new weapon gets a `weapons.<id>` block in `config.yml` following the existing pattern (damage/radius/duration numbers as configurable doubles/ints with in-code defaults), one block per weapon, consistent with the 7 existing entries.

## 4. Weapon menu: double chest

`WeaponMenu` grows from a 2-row (18-slot) inventory to a double chest (54 slots, `SIZE = 54`) so all
22 weapons fit in the "All" filter view without paging:

- Rows 0-3 (slots 0-35): weapon grid — up to 36 weapons, plenty of headroom over the current 22.
- Row 4 (slots 36-44): rarity filter bar (same buttons as today: All + 5 rarities), left-aligned.
- Row 5 (slots 45-53): filler glass panes, same as the current bottom-row treatment.

`WeaponMenuHolder` and `WeaponMenuListener` need no logic changes — they're index/size-agnostic
(filter click handling reads slot contents, not fixed indices, aside from the `FILTER_SLOTS` array
in `WeaponMenu` itself, which shifts to row 4's slot numbers). `WeaponMenuCommand` is unaffected.

## Out of scope

- No changes to `WeaponMenuCommand` beyond it continuing to work unmodified against the larger registry.
