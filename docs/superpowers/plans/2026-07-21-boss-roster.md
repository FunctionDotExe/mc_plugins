# Boss Roster (Bosses 2–10 + Worldender Capstone) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **PREREQUISITE:** The grief engine plan (`docs/superpowers/plans/2026-07-21-boss-grief-engine.md`) MUST be fully implemented and merged first. Every boss here depends on `Grief` and `Movement`. Each task below is independent of the others — run each in its own session.

**Goal:** Build 9 new bosses at Fallen-King parity (4 phases, ~12–14 attacks each, a new Legendary weapon drop each) plus the 8-phase Worldender capstone dropping the Mythic Apotheosis weapon.

**Architecture:** Every boss is a `Boss` subclass in `boss/bosses/` with its attacks as one-class-per-file `BossAttack` subclasses in `boss/bosses/attacks/`, mirroring the existing `FallenKing` + its 14 attack files exactly. Each boss drops a new `Weapon` subclass in `items/weapons/`. Bosses and weapons are registered in `WeaponsPlugin.onEnable()`. The capstone reuses the finished bosses' attack classes for its channeled phases.

**Tech Stack:** Java 21, Paper API (Bukkit), Gradle. Package root `dev.rbm72.weaponsplugin`.

## Global Constraints

- **No test framework.** Verification per task: `./gradlew compileJava` (must succeed) + the manual `/bossspawn <id>` check named in the task. Do not scaffold tests.
- **Grief:** every destructive effect goes through the `Grief` helper (never call `world.createExplosion`/`setType` directly in an attack). Movement lunges/leaps go through `Movement`. Both are gated/capped already.
- **No leaks:** adds via `ctx.instance().addManager()`; thrown blocks / display props via `Grief` (which tracks them). Never spawn a raw tracked-less entity. Every `BukkitRunnable` must self-`cancel()` on `!boss.isValid()`.
- **Config convention:** every tunable via `configDouble`/`configInt`/`configBoolean` with an inline default; the boss must be fully playable with no config block.
- **Attack contract:** every attack extends `BossAttack`, uses `sequence(telegraphTicks, perTelegraphTick, execute, recoveryTicks, onComplete)`, telegraph ≥ 10 ticks (the base class clamps anyway), excludes the boss itself and its own adds from AoE (`!nearby.equals(ctx.boss()) && !ctx.instance().addManager().isTracked(id)`), and applies damage via `target.damage(amount, ctx.boss())`.
- **Maximal VFX:** every attack layers ≥3 `Fx`/particle effects (a colored telegraph, an impact burst, an expanding ring or helix or spinning icon) plus ≥2 stacked sounds via `BossAudio.play(...)` + `Fx.sound(...)`. Keep per-tick particle counts under `ctx.instance().boss().maxParticlesPerTick()`. Study `EnrageSlashAttack`, `ShockwaveSlamAttack`, `DarkExplosionAttack` for the house VFX style.
- **Weapons:** each new weapon extends `Weapon` on the existing 4-slot framework (ability1/2/3 + ultimate + passive). Mirror an existing themed weapon of the same archetype (see each task) and the fully-worked templates in `docs/superpowers/plans/2026-07-21-weapon-expansion.md`.

---

## Boss-authoring recipe (read once, applies to every boss task)

Every boss task is the same shape. The reference implementation is `FallenKing.java` + its attack files — **open them and copy the structure.** The steps below are identical for each boss; individual tasks give only the boss-specific data (entity, biome, phases, attack specs, weapon).

**A. The attack skeleton.** Every attack file looks like this generic AoE-nova exemplar — copy it, rename, and swap the telegraph shape, effect, numbers, and VFX per the attack spec:

```java
package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.boss.telegraph.Telegraph;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** <one-line description from the attack spec>. */
public final class ExampleNovaAttack extends BossAttack {

    private final double damage;
    private final double radius;
    private final int telegraphTicks;

    public ExampleNovaAttack(WeaponsPlugin plugin) {
        super(plugin, "<boss_id>");
        this.damage = configDouble("example-nova-damage", 10.0);
        this.radius = configDouble("example-nova-radius", 5.0);
        this.telegraphTicks = configInt("example-nova-telegraph-ticks", 16);
    }

    @Override
    public String name() {
        return "Example Nova";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("example-nova-cooldown-seconds", 9.0);
    }

    @Override
    public void run(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.dangerZone(origin, radius);
                    Fx.coloredRing(origin, Color.fromRGB(60, 200, 255), 1.5f, radius, 28, 0);
                },
                () -> {
                    Fx.expandingRings(plugin, origin, Particle.SNOWFLAKE, radius, 3, 3L);
                    origin.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, origin.clone().add(0, 0.2, 0), 1, 0, 0, 0, 0);
                    BossAudio.play(origin, "boss.<boss_id>.example_nova", Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.8f);
                    Fx.sound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                    for (Entity nearby : ctx.boss().getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof LivingEntity target && !nearby.equals(ctx.boss())
                                && !ctx.instance().addManager().isTracked(nearby.getUniqueId())) {
                            target.damage(damage, ctx.boss());
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                    // Grief hook when the attack spec calls for it, e.g.:
                    // Grief.breakCrater(ctx, origin, radius * 0.6);
                },
                12, onComplete);
    }
}
```

Concrete variants to copy instead of the generic skeleton when the spec matches:
- **Projectile / bolt:** copy `DashSlashAttack`'s inner per-tick `BukkitRunnable` pattern, or throw via `Grief.throwBlock`.
- **Block-throw signature:** copy `SiegeHurlAttack` (from the engine plan).
- **Summon adds:** copy `SummonRoyalGuardsAttack`.
- **Leap/relocate slam:** copy `JumpSlamAttack`.
- **Dash:** `Movement.dash(ctx.boss(), ctx.target().getLocation(), speed)`.
- **Knock target airborne:** `Movement.launchTarget(target, up)`.
- **Real explosion:** `Grief.explosion(ctx, loc, power)`.
- **Raise columns (ice/bone):** `Grief.raiseColumns(ctx, base, material, height, count, spread, durationTicks)`.
- **Corruption spread:** `Grief.spread(ctx, center, material, radius)`.

**B. The boss class.** Copy `FallenKing.java`. Change `id()`, `displayName()`, `baseEntityType()`, `ambiance()`, the four entrance/defeat titles, the four `onEnter*` cinematics (per the task's phase table), the `phases` list (4 `BossPhase`s with thresholds `1.0 / 0.75 / 0.40 / 0.15`, last `isEnrage=true`), and the `lootTable` (guaranteed new weapon + weighted armor/materials + sub-1% cosmetic, copy the Fallen King loot methods and rename).

**C. Register.** In `WeaponsPlugin.onEnable()`: add `weaponRegistry.register(new <Weapon>(this));` in the weapon block and `bossManager.register(new <Boss>(this));` in the boss block. Add the two imports.

**D. Verify.** `./gradlew compileJava`, then `/bossspawn <id>` on a Paper server: confirm all 4 phases trigger at the HP thresholds, the signature grief move visibly wrecks terrain, adds despawn on phase change and death, the weapon drops on kill, and `/bossdespawn <id>` leaves nothing behind.

**E. Commit** once per boss: `git add plugin/src/main/java/dev/rbm72/weaponsplugin/ && git commit -m "feat(boss): add <Boss> + <Weapon>"`.

---

### Task 1: Frost Queen

**Files:** Create `boss/bosses/FrostQueen.java`, `items/weapons/GlacialScepter.java`, and the attack files below in `boss/bosses/attacks/`. Modify `WeaponsPlugin.java`.

**Boss:** `id() = "frost_queen"`, `baseEntityType() = EntityType.STRAY`, `ambiance() = BossAmbiance.of(Particle.SNOWFLAKE, "boss.frost_queen.ambient", Sound.WEATHER_RAIN, true, Biome.FROZEN_PEAKS)`, palette icy blue/white. Titles themed "The Frost Queen / Winter's cruel sovereign". Equipment (onEnterPhase1): diamond sword + light-blue leather armor (dyed) for a frozen-royalty look.

**Phases & attacks** (sig grief = **Glacier Spikes**):
- **P1 (1.0):** *Frost Nova* (nova skeleton; dmg 9, radius 5, + `SLOW II` 4s to those hit), *Ice Lance* (3-projectile volley at target using `Grief.throwBlock` with `Material.PACKED_ICE`, dmg 7 each, impactPower 0), *Glacier Spikes* (`Grief.raiseColumns(ctx, ctx.target().getLocation(), Material.PACKED_ICE, 3, 4, 2.5, 100)` + dmg 8 to players within 2 of each spike), *Chilling Touch* (melee lunge via `Movement.dash` + dmg 10 + `SLOW III` 3s), *Frost Armor* (self-buff: `onEnter`-style FX, grants the queen `Absorption` for 6s — read as a defensive tempo move).
- **P2 (0.75, onEnter: "Winter Deepens" cosmetic — snow storm burst):** *Blizzard* (arena-wide: 8s repeating task applying `SLOW I` + 2 dmg/s to all players in arena; layered snowflake particles; self-cancels on `!boss.isValid()`), *Avalanche* (throw 5 `Material.SNOW_BLOCK` blocks via `Grief.throwBlock`, dmg 6, impactPower 1.5), *Ice Lance* (reuse), *Glacier Spikes* (reuse).
- **P3 (0.40, onEnter: "Heart of Ice"):** *Frozen Prison* (encase target: `Grief.raiseColumns` a 1-wide 3-tall ring of `Material.ICE` around the target's location + `SLOW V` 2s + 6 dmg), *Frost Nova* (reuse, radius 6), *Ice Lance* (reuse, 5 projectiles), *Blizzard* (reuse).
- **P4 enrage (0.15, onEnter: "Absolute Winter"):** pool = Frost Nova, Ice Lance, Glacier Spikes, Blizzard, Avalanche, + **Absolute Zero** (enrage-only: 24-tick telegraph danger-ring at max radius, then arena-wide freeze — dmg 16 + `SLOW VI` 3s to every player in arena, huge snowflake/EXPLOSION_EMITTER burst, `Grief.breakCrater(ctx, origin, 4)` for a shattered-ground look).

Attack files to create: `FrostNovaAttack`, `IceLanceAttack`, `GlacierSpikesAttack`, `ChillingTouchAttack`, `FrostArmorAttack`, `BlizzardAttack`, `AvalancheAttack`, `FrozenPrisonAttack`, `AbsoluteZeroAttack` (9 classes; several reused across phases).

**Weapon — Glacial Scepter** (`items/weapons/GlacialScepter.java`, `Material.BLAZE_ROD`, `Rarity.LEGENDARY`, id `glacial_scepter`). Archetype: ice mage — mirror `ArcaneStaff`/`FrostScythe`. Abilities: (1) Frostbolt — ranged projectile, dmg + slow; (2) Ice Nova — self-centered AoE freeze; (3) Glacier — raise a short ice wall in front (cosmetic `Grief`-free: place temporary blocks via BlockDisplay); ultimate: Blizzard — cone storm rooting/slowing enemies; passive: melee hits apply brief slow.

- [ ] **Step 1:** Create the 9 attack classes (copy the skeleton/variants per the recipe; use `"frost_queen"` as bossId, the specs above for numbers).
- [ ] **Step 2:** Create `GlacialScepter.java` (copy `FrostScythe.java`, rename, retheme the 4 abilities per the weapon spec).
- [ ] **Step 3:** Create `FrostQueen.java` (copy `FallenKing.java`; wire the phase table above; loot = guaranteed `new GlacialScepter(plugin).createItem()` + weighted dyed-leather "Frostforged Regalia" 0.35 + `Material.BLUE_ICE` materials 0.6 + sub-1% cosmetic "Crown of Winter" player-head).
- [ ] **Step 4:** Register in `WeaponsPlugin.onEnable()` (weapon + boss + 2 imports).
- [ ] **Step 5:** `./gradlew compileJava` → `BUILD SUCCESSFUL`.
- [ ] **Step 6:** Manual: `/bossspawn frost_queen`, verify all phases, Glacier Spikes raises ice, Absolute Zero at <15%, weapon drops, clean despawn.
- [ ] **Step 7:** Commit `feat(boss): add Frost Queen + Glacial Scepter`.

---

### Task 2: Storm Tyrant

**Files:** Create `boss/bosses/StormTyrant.java`, `items/weapons/TempestMaul.java`, attack files. Modify `WeaponsPlugin.java`.

**Boss:** `id() = "storm_tyrant"`, `baseEntityType() = EntityType.WITHER_SKELETON`, `ambiance() = BossAmbiance.of(Particle.ELECTRIC_SPARK, "boss.storm_tyrant.ambient", Sound.ENTITY_LIGHTNING_BOLT_THUNDER, true, Biome.JAGGED_PEAKS)`. On spawn, `onEnterPhase1` sets `world.setStorm(true)` + `world.setThundering(true)` (record prior weather is out of scope — leave weather as set; note this in a code comment). Palette electric yellow/white. Equipment: iron sword.

**Phases & attacks** (sig grief = **Thunderstrike** — real lightning via `world.strikeLightning(loc)`, which naturally sets fire; gate the strike behind `Grief.enabled(ctx)` and fall back to `strikeLightningEffect` + code damage when off):
- **P1 (1.0):** *Chain Lightning* (hit target + arc to up to 3 nearest players within 6 of the last hit, dmg 8 each, spark-line VFX between them), *Thunderstrike* (telegraph a target-marker for 20t on the target's position, then strike lightning there, dmg 12 in radius 3), *Gale Push* (`Movement.launchTarget` all players within 6 upward + 5 dmg on landing zone), *Static Field* (nova skeleton, dmg 7 radius 5, + `SLOW I`).
- **P2 (0.75, onEnter: "Eye of the Storm"):** *Tornado* (spawn a moving vortex at the target: repeating task pulls players inward + tosses 3 `Material.DIRT` blocks via `Grief.throwBlock`; 6s; self-cancel), *Ball Lightning* (slow-moving homing orb — reuse the projectile pattern, dmg 10 on contact + small `Grief.explosion` power 1.5), *Chain Lightning* (reuse), *Thunderstrike* (reuse).
- **P3 (0.40, onEnter: "Wrath of the Sky"):** *Thunderstorm* (repeating: strike lightning at 4 random arena points over 5s, dmg 10 each), *Gale Push* (reuse), *Ball Lightning* (reuse, 2 orbs), *Tornado* (reuse).
- **P4 enrage (0.15, onEnter: "Maelstrom of Wrath"):** pool = Chain Lightning, Thunderstrike, Gale Push, Ball Lightning, Tornado, + **Stormcall** (enrage-only: rolling barrage — 30-tick sequence striking lightning in an expanding ring across the whole arena, dmg 14 per strike, screen-shake via repeated `Movement.launchTarget` small nudges).

Attack files: `ChainLightningAttack`, `ThunderstrikeAttack`, `GalePushAttack`, `StaticFieldAttack`, `TornadoAttack`, `BallLightningAttack`, `ThunderstormAttack`, `StormcallAttack` (8 classes).

**Weapon — Tempest Maul** (`Material.MACE` if available on the target API version, else `Material.NETHERITE_AXE`; `Rarity.LEGENDARY`, id `tempest_maul`). Archetype: lightning bruiser — mirror `ThunderHammer`. Abilities: (1) Thunderclap — slam, AoE dmg + knockback; (2) Call Lightning — strike at aim point; (3) Gust — dash + launch self; ultimate: Tempest — summon a lightning storm around the wielder; passive: melee crits chain a small spark to a nearby enemy.

- [ ] **Step 1:** Create the 8 attack classes (bossId `"storm_tyrant"`). For lightning, use `origin.getWorld().strikeLightning(loc)` when `Grief.enabled(ctx)` else `strikeLightningEffect(loc)` + manual `target.damage`.
- [ ] **Step 2:** Create `TempestMaul.java` (copy `ThunderHammer.java`, retheme).
- [ ] **Step 3:** Create `StormTyrant.java` (copy `FallenKing.java`; loot = Tempest Maul guaranteed + "Stormforged" iron armor 0.35 + `Material.COPPER_INGOT` materials 0.6 + sub-1% "Thunderer's Crown").
- [ ] **Step 4:** Register in `WeaponsPlugin.onEnable()`.
- [ ] **Step 5:** `./gradlew compileJava`.
- [ ] **Step 6:** Manual `/bossspawn storm_tyrant`.
- [ ] **Step 7:** Commit `feat(boss): add Storm Tyrant + Tempest Maul`.

---

### Task 3: Inferno Warlord

**Files:** Create `boss/bosses/InfernoWarlord.java`, `items/weapons/CinderCleaver.java`, attack files. Modify `WeaponsPlugin.java`.

**Boss:** `id() = "inferno_warlord"`, `baseEntityType() = EntityType.WITHER_SKELETON`, `ambiance() = BossAmbiance.of(Particle.FLAME, "boss.inferno_warlord.ambient", Sound.BLOCK_FIRE_AMBIENT, true, Biome.BASALT_DELTAS)`. Palette orange/red. Equipment: golden sword + netherite armor. `entity.setFireTicks(Integer.MAX_VALUE)` in onEnterPhase1 so it visibly burns (bosses are damage-immune to fire via the listener).

**Phases & attacks** (sig grief = **Meteor** — flaming `Grief.throwBlock` with `Material.MAGMA_BLOCK`, impactPower ~3, which explodes + fires on land):
- **P1 (1.0):** *Flame Breath* (cone: `Telegraph.cone` for telegraph, then dmg 9 + `setFireTicks(80)` to players in a 60° cone range 7), *Fire Trail* (dash via `Movement.dash` leaving a line of fire — set fire to players crossing; grief-on also ignites blocks via `Grief.explosion` power 0 won't fire — instead place `Material.FIRE` gated by `Grief.enabled`), *Meteor Rain* (3 meteors at target area), *Magma Throw* (single `Grief.throwBlock` `Material.MAGMA_BLOCK` dmg 10).
- **P2 (0.75, onEnter: "Molten Fury"):** *Eruption* (crater + launch: `Grief.breakCrater(ctx, target, 3)` + `Movement.launchTarget` players + 12 dmg + fire), *Cinder Nova* (nova, dmg 10 radius 6, + fire), *Flame Breath* (reuse), *Meteor Rain* (reuse, 5 meteors).
- **P3 (0.40, onEnter: "Hellfire Awakens"):** *Lava Wave* (advancing line of fire across arena from the boss toward target, dmg 11 + fire), *Meteor Rain* (reuse, 7), *Eruption* (reuse), *Magma Throw* (reuse, 3 blocks).
- **P4 enrage (0.15, onEnter: "Infernal Cataclysm"):** pool = Flame Breath, Meteor Rain, Eruption, Cinder Nova, Magma Throw, + **Firestorm** (enrage-only: arena-wide meteor barrage — 40-tick repeating task dropping meteors at random arena points, each `Grief.throwBlock` MAGMA_BLOCK from high above target's Y+20, dmg 10 + fire).

Attack files: `FlameBreathAttack`, `FireTrailAttack`, `MeteorRainAttack`, `MagmaThrowAttack`, `EruptionAttack`, `CinderNovaAttack`, `LavaWaveAttack`, `FirestormAttack` (8 classes).

**Weapon — Cinder Cleaver** (`Material.NETHERITE_AXE`, `Rarity.LEGENDARY`, id `cinder_cleaver`). Archetype: fire melee — mirror `FlameKatana`. Abilities: (1) Flame Slash — cone burn; (2) Meteor — hurl a fireball at aim; (3) Blazing Dash — dash igniting enemies; ultimate: Inferno — ring of fire + burn aura; passive: melee sets targets on fire.

- [ ] **Step 1–7:** Same shape as prior tasks (bossId `"inferno_warlord"`; loot guaranteed Cinder Cleaver + "Emberforged" netherite armor 0.35 + `Material.BLAZE_POWDER` 0.6 + sub-1% "Crown of Cinders"). Compile, manual `/bossspawn inferno_warlord`, commit `feat(boss): add Inferno Warlord + Cinder Cleaver`.

---

### Task 4: Plague Warden

**Files:** Create `boss/bosses/PlagueWarden.java`, `items/weapons/Rotscourge.java`, attack files. Modify `WeaponsPlugin.java`.

**Boss:** `id() = "plague_warden"`, `baseEntityType() = EntityType.HUSK`, `ambiance() = BossAmbiance.of(Particle.SPORE_BLOSSOM_AIR, "boss.plague_warden.ambient", Sound.BLOCK_SCULK_SPREAD, true, Biome.SWAMP)`. Palette sickly green. Equipment: none (rotting look).

**Phases & attacks** (sig grief = **Corruption Spread** — `Grief.spread(ctx, center, Material.MUD, radius)` growing each cast):
- **P1 (1.0):** *Poison Cloud* (lingering DoT zone: repeating task at target for 5s, `POISON II` + 2 dmg/s to players inside radius 4; self-cancel), *Summon Undead* (copy `SummonRoyalGuardsAttack`; spawn 3 `EntityType.ZOMBIE` adds, capped 5), *Plague Bolt* (`Grief.throwBlock` `Material.SLIME_BLOCK` dmg 7 + `POISON I`), *Corruption Spread* (radius grows 3 → cap).
- **P2 (0.75, onEnter: "Rot Takes Hold"):** *Miasma* (place 3 lingering poison zones around arena), *Rotting Grasp* (pull target toward boss + `SLOW III` + 8 dmg), *Summon Undead* (reuse, spawn HUSK), *Poison Cloud* (reuse).
- **P3 (0.40, onEnter: "Pestilence"):** *Plague Swarm* (nova, dmg 9 radius 6, + `POISON III` + `HUNGER`), *Corruption Spread* (reuse, larger), *Miasma* (reuse), *Summon Undead* (reuse, spawn 4).
- **P4 enrage (0.15, onEnter: "The Great Plague"):** pool = Poison Cloud, Plague Bolt, Corruption Spread, Rotting Grasp, Plague Swarm, + **Pandemic** (enrage-only: arena-wide — every player gets `POISON IV` 4s + `WEAKNESS` + no natural regen window, dmg 14, green EXPLOSION_EMITTER burst).

Attack files: `PoisonCloudAttack`, `SummonUndeadAttack`, `PlagueBoltAttack`, `CorruptionSpreadAttack`, `MiasmaAttack`, `RottingGraspAttack`, `PlagueSwarmAttack`, `PandemicAttack` (8 classes).

**Weapon — Rotscourge** (`Material.NETHERITE_HOE`, `Rarity.LEGENDARY`, id `rotscourge`). Archetype: poison scythe — mirror `PlagueScythe`. Abilities: (1) Venom Slash — cone poison; (2) Toxic Cloud — throw a lingering poison zone; (3) Life Leech — dmg + heal; ultimate: Outbreak — expanding plague nova; passive: hits apply stacking poison.

- [ ] **Step 1–7:** Same shape (bossId `"plague_warden"`; loot guaranteed Rotscourge + "Blightplate" armor 0.35 + `Material.ROTTEN_FLESH`/`FERMENTED_SPIDER_EYE` 0.6 + sub-1% "Plaguebearer's Crown"). Compile, manual `/bossspawn plague_warden`, commit `feat(boss): add Plague Warden + Rotscourge`.

---

### Task 5: Void Sovereign

**Files:** Create `boss/bosses/VoidSovereign.java`, `items/weapons/Nullblade.java`, attack files. Modify `WeaponsPlugin.java`.

**Boss:** `id() = "void_sovereign"`, `baseEntityType() = EntityType.ENDERMAN`, `ambiance() = BossAmbiance.of(Particle.PORTAL, "boss.void_sovereign.ambient", Sound.BLOCK_PORTAL_AMBIENT, true, Biome.THE_END)`. Palette purple/black. Note: Enderman teleports on damage naturally — call `entity.setAI(true)` and accept vanilla teleport; the tick loop still leashes it. Enderman takes damage from water/rain — the arena isn't rainy in THE_END biome, and the boss damage listener cancels non-player damage anyway.

**Phases & attacks** (sig grief = **Void Rift** — break blocks straight down to create pits: `Grief.breakCrater` variant, or loop downward `setType(AIR)` gated by `Grief.enabled`; use `Grief.breakCrater(ctx, loc.subtract(0,3,0), 2.5)` to hollow a pit):
- **P1 (1.0):** *Blink Strike* (teleport boss behind target via `entity.teleport`, then melee dmg 10), *Arcane Missiles* (3-projectile volley homing-ish, dmg 7 each, purple VFX), *Void Rift* (open a pit under target + 8 dmg + brief `LEVITATION`), *Banish* (teleport target 8 blocks away + 5 dmg).
- **P2 (0.75, onEnter: "Reality Fractures"):** *Gravity Flip* (`LEVITATION II` 3s to all players in arena + toss 3 `Material.END_STONE` blocks via `Grief.throwBlock`), *Singularity* (pull all players toward a point + 10 dmg nova on collapse), *Blink Strike* (reuse), *Arcane Missiles* (reuse, 5).
- **P3 (0.40, onEnter: "The Void Beckons"):** *Void Zone* (place 3 growing rifts around arena, standing in one = 3 dmg/s + pull down), *Singularity* (reuse), *Void Rift* (reuse, bigger), *Banish* (reuse).
- **P4 enrage (0.15, onEnter: "Unmaking"):** pool = Blink Strike, Arcane Missiles, Void Rift, Gravity Flip, Singularity, + **Collapse** (enrage-only: arena-wide — pull every player to center, `LEVITATION` then slam down, dmg 16, multiple `Grief.breakCrater` pits open across the arena).

Attack files: `BlinkStrikeAttack`, `ArcaneMissilesAttack`, `VoidRiftAttack`, `BanishAttack`, `GravityFlipAttack`, `SingularityAttack`, `VoidZoneAttack`, `CollapseAttack` (8 classes).

**Weapon — Nullblade** (`Material.NETHERITE_SWORD`, `Rarity.LEGENDARY`, id `nullblade`). Archetype: void/blink sword — mirror `VoidBlade`. Abilities: (1) Blink — short teleport dash + slash; (2) Void Bolt — ranged rift projectile; (3) Banish — knock/teleport target; ultimate: Singularity — pull + implode AoE; passive: kills briefly grant invisibility/speed.

- [ ] **Step 1–7:** Same shape (bossId `"void_sovereign"`; loot guaranteed Nullblade + "Voidwoven" armor 0.35 + `Material.ENDER_PEARL`/`CHORUS_FRUIT` 0.6 + sub-1% "Crown of the Void"). Compile, manual `/bossspawn void_sovereign`, commit `feat(boss): add Void Sovereign + Nullblade`.

---

### Task 6: Solar Colossus

**Files:** Create `boss/bosses/SolarColossus.java`, `items/weapons/Dawnbreaker.java`, attack files. Modify `WeaponsPlugin.java`.

**Boss:** `id() = "solar_colossus"`, `baseEntityType() = EntityType.IRON_GOLEM`, `ambiance() = BossAmbiance.of(Particle.END_ROD, "boss.solar_colossus.ambient", Sound.BLOCK_BEACON_AMBIENT, true, Biome.DESERT)`. Palette gold/white. Big, slow, heavy-hitting. Higher default `max-health` (override `maxHealth()` → `configDouble("max-health", 500.0)`) and larger `max-crater-radius`.

**Phases & attacks** (sig grief = **Seismic Slam** — huge `Grief.breakCrater(ctx, origin, 6)` + shockwave):
- **P1 (1.0):** *Solar Beam* (`Telegraph.line` for 24t, then a line-AoE beam via `Fx.glowPillar`/line dmg 14 to players along it), *Seismic Slam* (crater + nova dmg 13 radius 6 + knockup), *Fist Throw* (melee `Movement.dash` + heavy dmg 15 + `Movement.launchTarget`), *Radiant Nova* (nova dmg 10 radius 6, blinding `Fx`).
- **P2 (0.75, onEnter: "Sunforged"):** *Meteor* (single heavy `Grief.throwBlock` `Material.MAGMA_BLOCK` from Y+25 over target, impactPower 3.5, dmg 16), *Solar Flare* (`BLINDNESS` 2s to players in front + 8 dmg), *Solar Beam* (reuse, rotating sweep), *Seismic Slam* (reuse).
- **P3 (0.40, onEnter: "Zenith"):** *Sunfire Rain* (5 meteors over arena), *Radiant Beam Cross* (two perpendicular beams), *Seismic Slam* (reuse, radius 7), *Fist Throw* (reuse).
- **P4 enrage (0.15, onEnter: "Supernova Imminent"):** pool = Solar Beam, Seismic Slam, Fist Throw, Meteor, Radiant Nova, + **Supernova** (enrage-only: 30-tick charge telegraph, then a massive `Grief.explosion(ctx, origin, maxExplosionPower)` + arena-wide dmg 20 + huge `Grief.breakCrater(ctx, origin, 6)`).

Attack files: `SolarBeamAttack`, `SeismicSlamAttack`, `FistThrowAttack`, `RadiantNovaAttack`, `MeteorAttack`, `SolarFlareAttack`, `SunfireRainAttack`, `RadiantBeamCrossAttack`, `SupernovaAttack` (9 classes).

**Weapon — Dawnbreaker** (`Material.NETHERITE_SWORD`, `Rarity.LEGENDARY`, id `dawnbreaker`). Archetype: light greatsword — mirror `SolarGreatsword`. Abilities: (1) Radiant Slash — beam cleave; (2) Solar Flare — blind + dmg cone; (3) Judgment — call a light pillar on aim; ultimate: Daybreak — expanding radiant nova + heal self; passive: deals bonus dmg in daylight.

- [ ] **Step 1–7:** Same shape (bossId `"solar_colossus"`; override `maxHealth()`; loot guaranteed Dawnbreaker + "Sunplate" gold armor 0.35 + `Material.GOLD_INGOT`/`GLOWSTONE_DUST` 0.6 + sub-1% "Radiant Crown"). Compile, manual `/bossspawn solar_colossus`, commit `feat(boss): add Solar Colossus + Dawnbreaker`.

---

### Task 7: Tide Leviathan

**Files:** Create `boss/bosses/TideLeviathan.java`, `items/weapons/MaelstromTrident.java`, attack files. Modify `WeaponsPlugin.java`.

**Boss:** `id() = "tide_leviathan"`, `baseEntityType() = EntityType.DROWNED`, `ambiance() = BossAmbiance.of(Particle.BUBBLE, "boss.tide_leviathan.ambient", Sound.AMBIENT_UNDERWATER_LOOP, true, Biome.DEEP_OCEAN)`. Palette teal. Equipment: trident.

**Phases & attacks** (sig grief = **Tidal Surge** — flood: `Grief.spread(ctx, center, Material.WATER, radius)` places water; grief-off = bubble particles):
- **P1 (1.0):** *Water Jet* (`Telegraph.line`, then line dmg 10 + knockback), *Whirlpool* (pull players toward a point for 4s + 2 dmg/s), *Tidal Surge* (flood arena floor + `SLOW II` to players standing in new water), *Trident Throw* (`Grief.throwBlock` `Material.PRISMARINE` dmg 9, or a real trident projectile).
- **P2 (0.75, onEnter: "The Depths Rise"):** *Bubble Trap* (encase target in a bubble column launching them up + 6 dmg), *Tsunami* (advancing wall of thrown `Material.PRISMARINE` blocks across arena, dmg 12), *Water Jet* (reuse, 2 jets), *Whirlpool* (reuse).
- **P3 (0.40, onEnter: "Abyssal Wrath"):** *Ice Shard* (freeze surface water then shatter it: `Grief.throwBlock` `Material.BLUE_ICE`, dmg 11 + `SLOW`), *Tidal Surge* (reuse, bigger), *Tsunami* (reuse), *Bubble Trap* (reuse).
- **P4 enrage (0.15, onEnter: "Maelstrom"):** pool = Water Jet, Whirlpool, Tidal Surge, Tsunami, Ice Shard, + **Maelstrom** (enrage-only: arena-wide whirlpool — continuous pull to center + `SLOW III` + 3 dmg/s + drowning pressure `setRemainingAir` drain on players in the flooded zone, for 8s).

Attack files: `WaterJetAttack`, `WhirlpoolAttack`, `TidalSurgeAttack`, `TridentThrowAttack`, `BubbleTrapAttack`, `TsunamiAttack`, `IceShardAttack`, `MaelstromAttack` (8 classes).

**Weapon — Maelstrom Trident** (`Material.TRIDENT`, `Rarity.LEGENDARY`, id `maelstrom_trident`). Archetype: water trident — mirror `TidalTrident`. Abilities: (1) Tidal Thrust — lunge + knockback; (2) Water Jet — ranged beam; (3) Whirlpool — pull AoE; ultimate: Maelstrom — vortex storm; passive: bonus dmg + speed in water/rain.

- [ ] **Step 1–7:** Same shape (bossId `"tide_leviathan"`; loot guaranteed Maelstrom Trident + "Tideplate" prismarine-themed armor 0.35 + `Material.PRISMARINE_SHARD`/`HEART_OF_THE_SEA` weighted lower 0.6/0.05 + sub-1% "Crown of Tides"). Compile, manual `/bossspawn tide_leviathan`, commit `feat(boss): add Tide Leviathan + Maelstrom Trident`.

---

### Task 8: Dragon Elder (aerial — highest risk, do after the grounded bosses)

**Files:** Create `boss/bosses/DragonElder.java`, `items/weapons/WyrmscaleBow.java`, attack files. Modify `WeaponsPlugin.java`.

**Boss:** `id() = "dragon_elder"`, `baseEntityType() = EntityType.PHANTOM`, `ambiance() = BossAmbiance.of(Particle.FLAME, "boss.dragon_elder.ambient", Sound.ENTITY_ENDER_DRAGON_AMBIENT, true, Biome.WINDSWEPT_HILLS)`. Palette dark red/black. Larger scale — override `maxHealth()` → 400. Phantom flies natively.

**Flight model (boss-specific — the one novel mechanic).** Add a `DragonFlightController` helper OR handle in the boss's `onEnterPhase1` a repeating task tracking a `hovering` boolean on the instance: the dragon hovers ~8 blocks above the target most of the time (set velocity toward `target.getLocation().add(0, 8, 0)`), and every ~10s performs a **ground dive** (Dive Bomb) landing near the target and staying grounded/vulnerable ~4s before relifting. Because `BossInstance.tick` calls `mob.getPathfinder().moveTo(target)`, Phantom aerial pathing already chases; the hover task only nudges vertical position. Keep the hover task tracked (`ctx.instance().trackTask` via `instance.trackTask` — note it's package-private; instead start the repeating task inside an attack's runnable that self-cancels, or expose a public `trackTask` — see Step 1 note). **Step 1 note:** if the hover loop needs to live for the whole fight, add a public `trackTask(BukkitTask)` overload usage — `BossInstance.trackTask` is currently package-private; run the hover loop from within `onEnterPhase1` using `instance.plugin()` and store it via a new tracked task. Simplest compliant approach: make the hover behavior an always-available attack (`HoverReposition`) with a short cooldown so it re-issues through the normal attack loop and needs no long-lived task.

**Phases & attacks** (sig grief = **Dive Bomb** — swoop to ground + `Grief.explosion(ctx, landing, 3)` + `Grief.breakCrater`):
- **P1 (1.0):** *Fireball Barrage* (aerial: throw 4 `Material.MAGMA_BLOCK` via `Grief.throwBlock` at target from above, dmg 8), *Wing Gust* (downward blast: `Movement.launchTarget` all nearby players + `SLOW` + 7 dmg), *Dive Bomb* (dive + crater dmg 14), *Tail Sweep* (only when grounded: cone dmg 12 + knockback).
- **P2 (0.75, onEnter: "Wings of Ruin"):** *Firestorm Breath* (cone of fire from the air, dmg 10 + ignite), *Grab-and-Drop* (swoop, `LEVITATION`+carry target up, then remove levitation to drop for fall dmg — cap fall so it's ~10 dmg), *Fireball Barrage* (reuse, 6), *Dive Bomb* (reuse).
- **P3 (0.40, onEnter: "Elder Fury"):** *Meteor Wings* (rain 5 fireballs while circling), *Firestorm Breath* (reuse, wider), *Grab-and-Drop* (reuse), *Tail Sweep* (reuse).
- **P4 enrage (0.15, onEnter: "Cataclysm"):** pool = Fireball Barrage, Wing Gust, Dive Bomb, Firestorm Breath, Tail Sweep, + **Cataclysm** (enrage-only: strafing runs — dragon flies straight lines across the arena dropping fire + meteors along the path, 3 passes, dmg 12/pass + ignite).

Attack files: `FireballBarrageAttack`, `WingGustAttack`, `DiveBombAttack`, `TailSweepAttack`, `FirestormBreathAttack`, `GrabAndDropAttack`, `MeteorWingsAttack`, `CataclysmAttack`, and (if used) `HoverRepositionAttack` (8–9 classes).

**Weapon — Wyrmscale Bow** (`Material.BOW` (or `CROSSBOW`), `Rarity.LEGENDARY`, id `wyrmscale_bow`). Archetype: draconic ranged — mirror `CelestialBow`. Abilities: (1) Dragonfire Arrow — explosive fire arrow; (2) Barrage — multi-shot spread; (3) Wing Dash — backflip dash + gust; ultimate: Elder's Wrath — meteor volley at aim; passive: charged shots ignite + pierce.

- [ ] **Step 1:** Decide the flight approach (recommended: `HoverRepositionAttack` with ~2s cooldown so no long-lived task is needed; if you instead need a fight-long loop, first add a `public void trackTask(BukkitTask)` accessor on `BossInstance` in a tiny separate commit). Create the attack classes (bossId `"dragon_elder"`).
- [ ] **Step 2:** Create `WyrmscaleBow.java` (copy `CelestialBow.java`, retheme).
- [ ] **Step 3:** Create `DragonElder.java` (copy `FallenKing.java`; override `maxHealth()`; wire phases; loot guaranteed Wyrmscale Bow + "Wyrmscale" armor 0.35 + `Material.DRAGON_BREATH`/`BLAZE_ROD` 0.6 + sub-1% "Elder Dragon Skull" `Material.DRAGON_HEAD`).
- [ ] **Step 4:** Register in `WeaponsPlugin.onEnable()`.
- [ ] **Step 5:** `./gradlew compileJava`.
- [ ] **Step 6:** Manual `/bossspawn dragon_elder` — pay special attention to flight: confirm it hovers, dives to ground periodically (vulnerable window), doesn't fly out of the arena permanently (leash nudges it back), and doesn't get stuck in terrain. Tune hover height / dive cadence if it feels off.
- [ ] **Step 7:** Commit `feat(boss): add Dragon Elder + Wyrmscale Bow`.

---

### Task 9: Necro Overlord

**Files:** Create `boss/bosses/NecroOverlord.java`, `items/weapons/Soulharvester.java`, attack files. Modify `WeaponsPlugin.java`.

**Boss:** `id() = "necro_overlord"`, `baseEntityType() = EntityType.WITHER_SKELETON`, `ambiance() = BossAmbiance.of(Particle.SCULK_SOUL, "boss.necro_overlord.ambient", Sound.PARTICLE_SOUL_ESCAPE, true, Biome.DARK_FOREST)`. Palette bone-white/necrotic green. Equipment: bone (stick) or netherite sword.

**Phases & attacks** (sig grief = **Bone Spikes** — `Grief.raiseColumns(ctx, target, Material.BONE_BLOCK, 3, 5, 3, 100)` + dmg near each):
- **P1 (1.0):** *Raise Undead* (copy `SummonRoyalGuardsAttack`; spawn 4 `EntityType.SKELETON` adds, capped 6), *Bone Spikes* (columns + dmg 9), *Death Bolt* (`Grief.throwBlock` `Material.BONE_BLOCK` dmg 8 + `WITHER I`), *Grave Grasp* (root: `SLOW VI` 2s to players in radius 5 + 6 dmg).
- **P2 (0.75, onEnter: "The Dead Rise"):** *Soul Drain* (self-heal: dmg 10 nova radius 6, heal boss for half the total dealt), *Wither Cloud* (lingering `WITHER II` zone 5s), *Raise Undead* (reuse, spawn ZOMBIE + SKELETON mix), *Bone Spikes* (reuse).
- **P3 (0.40, onEnter: "Necropolis"):** *Bone Storm* (nova of bone shrapnel, dmg 11 radius 6 + brief root), *Soul Drain* (reuse, stronger heal), *Wither Cloud* (reuse, 2 zones), *Grave Grasp* (reuse).
- **P4 enrage (0.15, onEnter: "Army of the Dead"):** pool = Bone Spikes, Death Bolt, Grave Grasp, Soul Drain, Bone Storm, + **Army of the Dead** (enrage-only: mass summon 6 adds at once (respecting a raised cap ~10) + relentless `WITHER` aura on all arena players + dmg 12 nova).

Attack files: `RaiseUndeadAttack`, `BoneSpikesAttack`, `DeathBoltAttack`, `GraveGraspAttack`, `SoulDrainAttack`, `WitherCloudAttack`, `BoneStormAttack`, `ArmyOfTheDeadAttack` (8 classes).

**Weapon — Soulharvester** (`Material.NETHERITE_HOE`, `Rarity.LEGENDARY`, id `soulharvester`). Archetype: death scythe — mirror `BloodReaper`/`NecromancerStaff`. Abilities: (1) Reaping Slash — cone dmg + lifesteal; (2) Summon Wraith — spawn a temporary friendly minion; (3) Soul Bolt — wither projectile; ultimate: Harvest — AoE drain healing per enemy hit; passive: kills heal + grant a soul stack (bonus dmg).

- [ ] **Step 1–7:** Same shape (bossId `"necro_overlord"`; loot guaranteed Soulharvester + "Gravebound" armor 0.35 + `Material.BONE`/`SOUL_SAND` 0.6 + sub-1% "Lich's Crown"). Compile, manual `/bossspawn necro_overlord`, commit `feat(boss): add Necro Overlord + Soulharvester`.

---

### Task 10: The Worldender (8-phase capstone)

**Files:** Create `boss/bosses/Worldender.java`, `items/weapons/Apotheosis.java`, and the Worldender-only attack files. Modify `WeaponsPlugin.java`. **Depends on Tasks 1–9 being complete** (reuses their attack classes).

**Boss:** `id() = "worldender"`, `baseEntityType() = EntityType.WARDEN`, `maxHealth()` → `configDouble("max-health", 1000.0)`, `arenaRadius()` → 25. `ambiance()` starts void-themed; each `onEnter` swaps biome/ambiance to match the channeled boss (call a small private `warpArena(instance, Biome, Particle, Sound)` that restarts a themed ambient loop — simplest: use `instance.showTitle` + a big particle cinematic per phase and set the arena biome via the same mechanism `BossAmbiance` uses; a full per-phase ambiance restart is optional polish, the phase title + cinematic is the required part).

**8 phases** (thresholds are 8 evenly-spaced bands; last is enrage). Channeled phases **reuse existing attack classes** — instantiate them with `new <Attack>(plugin)` (they read their own boss-id config, which is fine; the capstone gets that boss's tuning). Worldender-only attacks are new.

| Phase | `entryThresholdFraction` | onEnter title | Attack pool |
|---|---|---|---|
| 1 Awakening | 1.0 | "The Worldender Awakens" | `WardenSonicBoomAttack` (new), `VoidSlamAttack` (new), a dash attack |
| 2 Frostbound | 0.87 | "Frostbound" | `BlizzardAttack`, `GlacierSpikesAttack`, `AbsoluteZeroAttack` |
| 3 Stormforged | 0.75 | "Stormforged" | `ThunderstrikeAttack`, `TornadoAttack`, `StormcallAttack` |
| 4 Infernal | 0.62 | "Infernal" | `MeteorRainAttack`, `FireTrailAttack`, `FirestormAttack` |
| 5 Plaguebound | 0.50 | "Plaguebound" | `CorruptionSpreadAttack`, `SummonUndeadAttack`, `SoulDrainAttack` |
| 6 Voidtouched | 0.37 | "Voidtouched" | `VoidRiftAttack`, `SingularityAttack`, `BlinkStrikeAttack` |
| 7 Cataclysm | 0.25 | "Cataclysm" | `MeteorRainAttack`, `ThunderstrikeAttack`, `AbsoluteZeroAttack`, `VoidRiftAttack` (mixed) |
| 8 The Unmaking (enrage) | 0.12 | "The Unmaking" | `WorldenderFinaleAttack` (new), + Supernova/Collapse/Stormcall/Firestorm mixed, short cooldowns |

`BossPhase.select` requires strictly descending thresholds — the table already is (1.0 > 0.87 > ... > 0.12). Mark only phase 8 `isEnrage=true`.

**New Worldender-only attacks:** `WardenSonicBoomAttack` (Warden-style directional sonic blast: `Telegraph.line`, then line dmg 16 + knockback + real `Grief.breakCrater` along the line), `VoidSlamAttack` (leap + `Grief.explosion` power maxed + crater), `WorldenderFinaleAttack` (arena-wide destruction: repeating 6s barrage cycling every signature grief move — meteors, lightning, rifts, ice — dmg 20 aura, heavy `Grief` on the whole arena).

**Weapon — Apotheosis** (`items/weapons/Apotheosis.java`, `Material.NETHERITE_SWORD`, `Rarity.MYTHIC`, id `apotheosis`). Best-in-slot. Mirror the strongest existing Mythic weapon (`Starbreaker` or `Stormbreaker` — check which is Mythic). Abilities should feel like a fusion: (1) Worldcleaver — massive cone + explosion; (2) Elemental Burst — cycles ice/fire/lightning per use; (3) Void Rift — pull + implode; ultimate: Apotheosis — screen-filling multi-element cataclysm; passive: bonus dmg vs bosses, and every hit procs a random small elemental effect.

- [ ] **Step 1:** Create the 3 new Worldender attack classes (bossId `"worldender"`).
- [ ] **Step 2:** Create `Apotheosis.java` (copy the strongest existing Mythic weapon, retheme; `Rarity.MYTHIC`).
- [ ] **Step 3:** Create `Worldender.java` (copy `FallenKing.java`; override `maxHealth()`/`arenaRadius()`; build the 8-phase list per the table, importing the reused attack classes from the other bosses; 8 `onEnter*` cinematics; loot = guaranteed Apotheosis + weighted top-tier materials + sub-1% "Crown of Creation").
- [ ] **Step 4:** Register in `WeaponsPlugin.onEnable()` (weapon + boss + imports).
- [ ] **Step 5:** `./gradlew compileJava` → `BUILD SUCCESSFUL`.
- [ ] **Step 6:** Manual `/bossspawn worldender` — confirm all 8 phase transitions fire at the right HP bands, each phase plays its channeled kit, the arena visibly warps per phase, Apotheosis drops on kill, clean despawn.
- [ ] **Step 7:** Commit `feat(boss): add The Worldender capstone + Apotheosis`.

---

## Self-Review

- **Spec coverage:** roster bosses 2–10 → Tasks 1–9; Worldender capstone → Task 10; new weapons → folded into each boss task; grief signatures → each boss's sig attack uses a `Grief` primitive; maximal VFX → global constraint + per-attack VFX notes; flight → Task 8 flight model; per-boss config/grief flag → inherited from the engine plan. Registration/wiring → step in every task.
- **Placeholder scan:** no "TBD"/"implement later". Each attack has a concrete telegraph/effect/number spec and a named template to copy; the generic skeleton is fully coded in the recipe. This plan specifies bosses at behavior-spec + template-reference altitude (not 130 hand-written classes) because the repo already contains 14 working attack files as living templates and the engine plan provides the grief exemplars — an implementer copies a template and applies the spec.
- **Type/name consistency:** capstone (Task 10) reuses attack class names exactly as created in Tasks 1–9 (`BlizzardAttack`, `GlacierSpikesAttack`, `AbsoluteZeroAttack`, `ThunderstrikeAttack`, `TornadoAttack`, `StormcallAttack`, `MeteorRainAttack`, `FireTrailAttack`, `FirestormAttack`, `CorruptionSpreadAttack`, `SummonUndeadAttack`, `SoulDrainAttack`, `VoidRiftAttack`, `SingularityAttack`, `BlinkStrikeAttack`). If any boss task renames one of these, update Task 10's imports to match.
- **Ordering:** engine plan first (hard prerequisite), then Tasks 1–7 and 9 in any order/parallel, Task 8 (flight) after a grounded boss is proven, Task 10 last (needs 1–9).
