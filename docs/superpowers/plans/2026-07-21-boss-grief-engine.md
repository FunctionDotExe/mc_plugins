# Boss Grief Engine + Fallen King Retrofit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add griefable combat primitives to the boss framework (real explosions, block breaking, thrown blocks, block placement, dash/leap movement), gated per boss by a config flag, and retrofit The Fallen King to use them.

**Architecture:** One stateless `Grief` helper (destructive block/explosion primitives) and one stateless `Movement` helper (velocity moves), both called from `BossAttack` subclasses via `AttackContext`. Every destructive path is gated by `Boss.griefEnabled()` (config `bosses.<id>.grief`, default `true`) and falls back to a pure particle/sound cosmetic when off. Spawned `FallingBlock`s are tracked in `BossInstance` and force-removed on fight end, reusing the existing leak-safe cleanup.

**Tech Stack:** Java 21, Paper API (Bukkit), Gradle. Package root `dev.rbm72.weaponsplugin`.

## Global Constraints

- **No test framework exists in this repo.** Verification for every task is `./gradlew compileJava` (must succeed) plus the manual in-game check named in the task. There are no unit tests to write; do not scaffold a test framework.
- **No entity/task leaks.** Every spawned entity (adds, FallingBlocks, Display props) and every `BukkitTask` must be tracked and removed/cancelled on fight end. New grief entities go through `BossInstance.trackEntity`.
- **Main-thread only.** All Bukkit calls run on the main thread (scheduler tasks). No async world/entity access.
- **Grief is gated + capped.** Destructive primitives run only when `Boss.griefEnabled()` is true; grief scope is unbounded (may affect blocks outside the arena) but per-call churn is bounded by the boss's stability caps (`max-explosion-power`, `max-crater-radius`, `max-falling-blocks`).
- **Config convention:** all tunables read via `bosses.<id>.<key>` with an inline default, exactly like existing attacks. Config entries are optional — defaults must make the boss fully playable with no config block.
- **Follow existing style:** attacks are one class per file under `boss/bosses/attacks/`, extend `BossAttack`, use `sequence(...)`, `Telegraph`, `Fx`, `BossAudio`. Match import ordering and formatting of neighboring files.

---

### Task 1: Boss + BossInstance grief plumbing

Adds the per-boss grief flag, the stability-cap accessors, and grief-entity tracking that `Grief` (Task 2) depends on. Compiles and changes no behavior on its own.

**Files:**
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/boss/Boss.java`
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/boss/BossInstance.java`

**Interfaces:**
- Produces (on `Boss`): `boolean griefEnabled()`, `float maxExplosionPower()`, `double maxCraterRadius()`, `int maxFallingBlocks()`, `int maxParticlesPerTick()`, `protected boolean configBoolean(String, boolean)`.
- Produces (on `BossInstance`): `void trackEntity(org.bukkit.entity.Entity)`, `int liveFallingBlockCount()`.

- [ ] **Step 1: Add config accessors to `Boss.java`**

Add these methods to `Boss` (below the existing `configInt`):

```java
    protected final boolean configBoolean(String key, boolean def) {
        return plugin.getConfig().getBoolean("bosses." + id() + "." + key, def);
    }

    /** Master grief switch for this boss. Default on — bosses are destructive unless disabled. */
    public boolean griefEnabled() {
        return configBoolean("grief", true);
    }

    /** Hard ceiling on any explosion power this boss requests (server-stability cap, not a grief-scope cap). */
    public float maxExplosionPower() {
        return (float) configDouble("max-explosion-power", 4.0);
    }

    /** Hard ceiling on crater break radius per call (bounds block churn). */
    public double maxCraterRadius() {
        return configDouble("max-crater-radius", 6.0);
    }

    /** Hard ceiling on live thrown FallingBlocks per fight. */
    public int maxFallingBlocks() {
        return configInt("max-falling-blocks", 24);
    }

    /** Soft ceiling attacks should keep per-tick particle counts under. */
    public int maxParticlesPerTick() {
        return configInt("max-particles-per-tick", 400);
    }
```

- [ ] **Step 2: Add grief-entity tracking to `BossInstance.java`**

Add these imports (keep alphabetical grouping with existing imports):

```java
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
```

Add this field alongside the other `private final` fields (near `tasks`):

```java
    private final Set<UUID> griefEntities = new HashSet<>();
```

Add these methods (public, near `trackTask`):

```java
    /** Register a grief-spawned entity (thrown block, display prop) for force-removal on fight end. */
    public void trackEntity(Entity entity) {
        griefEntities.add(entity.getUniqueId());
    }

    /** Count of this fight's still-alive thrown blocks — used to enforce the falling-block cap. */
    public int liveFallingBlockCount() {
        int count = 0;
        for (UUID id : griefEntities) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof FallingBlock && entity.isValid()) {
                count++;
            }
        }
        return count;
    }
```

- [ ] **Step 3: Remove grief entities in `end(...)` cleanup**

In `BossInstance.end(...)`, inside the `try` block, immediately after the existing `addManager.despawnAll();` line, add:

```java
            for (UUID id : griefEntities) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            }
            griefEntities.clear();
```

- [ ] **Step 4: Compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. No behavior change yet — this is plumbing consumed by Task 2.

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/boss/Boss.java plugin/src/main/java/dev/rbm72/weaponsplugin/boss/BossInstance.java
git commit -m "feat(boss): add per-boss grief flag, stability caps, and grief-entity tracking"
```

---

### Task 2: `Grief` helper

The shared destructive primitives every griefable attack calls.

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/boss/grief/Grief.java`

**Interfaces:**
- Consumes: `AttackContext` (`ctx.instance()`, `ctx.boss()`, `ctx.plugin()`), `Boss.griefEnabled()/maxExplosionPower()/maxCraterRadius()/maxFallingBlocks()` (Task 1), `BossInstance.trackEntity/liveFallingBlockCount` (Task 1), `Fx`.
- Produces (all `public static`):
  - `boolean enabled(AttackContext ctx)`
  - `void explosion(AttackContext ctx, Location loc, float power)`
  - `void breakCrater(AttackContext ctx, Location center, double radius)`
  - `void throwBlock(AttackContext ctx, Location from, LivingEntity target, Material material, double damage, float impactPower)`
  - `void raiseColumns(AttackContext ctx, Location base, Material material, int height, int count, double spread, int durationTicks)`
  - `void spread(AttackContext ctx, Location center, Material to, double radius)`

- [ ] **Step 1: Create `Grief.java`**

```java
package dev.rbm72.weaponsplugin.boss.grief;

import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Stateless destructive primitives shared by every griefable boss attack.
 * Each method runs its real block/explosion effect only when the boss's
 * grief flag is on; when off it falls back to a pure particle/sound cosmetic
 * so the boss stays fully playable grief-disabled. Server-stability caps
 * (explosion power, crater radius, live falling-block count) always apply.
 */
public final class Grief {

    private Grief() {
    }

    public static boolean enabled(AttackContext ctx) {
        return ctx.instance().boss().griefEnabled();
    }

    /**
     * Real explosion (fire + block break) when grief on; a cosmetic explosion burst + sound when off.
     * The boss is already immune to explosion damage via {@code BossDamageListener} (non-player damage
     * to a boss is cancelled), so it never blows itself up.
     */
    public static void explosion(AttackContext ctx, Location loc, float power) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        if (enabled(ctx)) {
            float capped = Math.min(power, ctx.instance().boss().maxExplosionPower());
            world.createExplosion(loc, capped, true, true);
        } else {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2, 0.5, 0.5, 0.5, 0);
            world.spawnParticle(Particle.EXPLOSION, loc, 24, 1.4, 1.4, 1.4, 0.1);
            Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        }
    }

    /**
     * Break every block in a sphere of {@code radius} around {@code center} (clamped to the boss's
     * crater cap). Grief off -> block-crack particles + sound only. Skips air, bedrock, and barriers.
     */
    public static void breakCrater(AttackContext ctx, Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double r = Math.min(radius, ctx.instance().boss().maxCraterRadius());
        if (!enabled(ctx)) {
            world.spawnParticle(Particle.BLOCK, center, 60, r * 0.5, 0.5, r * 0.5, 0.1, Material.STONE.createBlockData());
            Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.9f);
            return;
        }
        int ir = (int) Math.ceil(r);
        double r2 = r * r;
        for (int x = -ir; x <= ir; x++) {
            for (int y = -ir; y <= ir; y++) {
                for (int z = -ir; z <= ir; z++) {
                    if (x * x + y * y + z * z > r2) {
                        continue;
                    }
                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    Material type = block.getType();
                    if (type.isAir() || type == Material.BEDROCK || type == Material.BARRIER) {
                        continue;
                    }
                    block.setType(Material.AIR, false);
                }
            }
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2, 0.5, 0.5, 0.5, 0);
        Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
    }

    /**
     * Launch a FallingBlock projectile from {@code from} at {@code target}, damaging any player within
     * 2 blocks of impact and (grief on) cratering on landing. The block never places or drops
     * ({@code setCancelDrop(true)}), so it leaves no stray terrain; it is tracked for cleanup and
     * honors the boss's live-falling-block cap.
     */
    public static void throwBlock(AttackContext ctx, Location from, LivingEntity target, Material material,
                                  double damage, float impactPower) {
        World world = from.getWorld();
        if (world == null || ctx.instance().liveFallingBlockCount() >= ctx.instance().boss().maxFallingBlocks()) {
            return;
        }
        FallingBlock block = world.spawnFallingBlock(from, material.createBlockData());
        block.setDropItem(false);
        block.setCancelDrop(true);
        block.setHurtEntities(false);
        block.setPersistent(false);
        ctx.instance().trackEntity(block);

        Vector velocity = target.getLocation().add(0, 1, 0).toVector()
                .subtract(from.toVector()).normalize().multiply(1.4);
        velocity.setY(0.5);
        block.setVelocity(velocity);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!block.isValid() || block.isOnGround() || ticks >= 60) {
                    if (block.isValid()) {
                        Location impact = block.getLocation();
                        for (Player player : world.getPlayers()) {
                            if (player.getLocation().distanceSquared(impact) <= 4.0) {
                                player.damage(damage, ctx.boss());
                            }
                        }
                        if (enabled(ctx)) {
                            explosion(ctx, impact, impactPower);
                        } else {
                            Fx.burst(impact, Particle.CLOUD, 20, 0.4);
                            Fx.sound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.0f);
                        }
                        block.remove();
                    }
                    cancel();
                    return;
                }
                Fx.trail(block.getLocation(), Particle.SMOKE, 3, 0.1, 0.01);
                ticks++;
            }
        }.runTaskTimer(ctx.plugin(), 1L, 1L);
    }

    /**
     * Raise {@code count} vertical columns of {@code material}, {@code height} tall, scattered within
     * {@code spread} blocks of {@code base}. Grief on -> real placed blocks (permanent). Grief off ->
     * self-removing {@link BlockDisplay} props (tracked, auto-removed after {@code durationTicks}).
     */
    public static void raiseColumns(AttackContext ctx, Location base, Material material, int height, int count,
                                    double spread, int durationTicks) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        boolean grief = enabled(ctx);
        for (int i = 0; i < count; i++) {
            double ox = ThreadLocalRandom.current().nextDouble(-spread, spread);
            double oz = ThreadLocalRandom.current().nextDouble(-spread, spread);
            Location col = base.clone().add(ox, 0, oz);
            if (grief) {
                for (int y = 0; y < height; y++) {
                    Block block = world.getBlockAt(col.getBlockX(), col.getBlockY() + y, col.getBlockZ());
                    if (block.getType().isAir() || block.isLiquid()) {
                        block.setType(material, false);
                    }
                }
            } else {
                BlockData data = material.createBlockData();
                BlockDisplay display = world.spawn(col, BlockDisplay.class, entity -> {
                    entity.setBlock(data);
                    entity.setBillboard(Display.Billboard.FIXED);
                    entity.setBrightness(new Display.Brightness(15, 15));
                    entity.setTransformation(new Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f(0, 0, 1, 0),
                            new Vector3f(1, height, 1),
                            new AxisAngle4f(0, 0, 1, 0)));
                    entity.setPersistent(false);
                });
                ctx.instance().trackEntity(display);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!display.isDead()) {
                            display.remove();
                        }
                    }
                }.runTaskLater(ctx.plugin(), durationTicks);
            }
            Fx.burst(col.clone().add(0, height / 2.0, 0), Particle.BLOCK, 15, 0.3);
        }
    }

    /**
     * Convert the top ground block of every column within {@code radius} of {@code center} to
     * {@code to} (creeping corruption). Grief-gated; grief off -> tinting particles only.
     */
    public static void spread(AttackContext ctx, Location center, Material to, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        boolean grief = enabled(ctx);
        double r = Math.min(radius, ctx.instance().boss().maxCraterRadius());
        int ir = (int) Math.ceil(r);
        double r2 = r * r;
        for (int x = -ir; x <= ir; x++) {
            for (int z = -ir; z <= ir; z++) {
                if (x * x + z * z > r2) {
                    continue;
                }
                Block ground = world.getHighestBlockAt(center.getBlockX() + x, center.getBlockZ() + z);
                if (grief) {
                    if (!ground.getType().isAir() && ground.getType() != Material.BEDROCK) {
                        ground.setType(to, false);
                    }
                } else {
                    Fx.burst(ground.getLocation().add(0.5, 1, 0.5), Particle.SPORE_BLOSSOM_AIR, 2, 0.2);
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/boss/grief/Grief.java
git commit -m "feat(boss): add Grief helper (explosion, crater, throw-block, columns, spread)"
```

---

### Task 3: `Movement` helper

Velocity-driven moves shared by dash/leap/knock-up attacks. Grief-independent (never touches blocks).

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/boss/ai/Movement.java`

**Interfaces:**
- Produces (all `public static`): `void dash(LivingEntity self, Location toward, double speed)`, `void leap(LivingEntity self, Location toward, double up, double forward)`, `void launchTarget(LivingEntity target, double up)`.

- [ ] **Step 1: Create `Movement.java`**

```java
package dev.rbm72.weaponsplugin.boss.ai;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** Velocity-driven boss movement — lunges, leaps, and target knock-ups. Never touches blocks. */
public final class Movement {

    private Movement() {
    }

    /** Horizontal lunge toward {@code toward} at {@code speed} blocks/tick. */
    public static void dash(LivingEntity self, Location toward, double speed) {
        Vector direction = toward.toVector().subtract(self.getLocation().toVector()).setY(0).normalize();
        self.setVelocity(direction.multiply(speed));
    }

    /** Arcing jump toward {@code toward}: {@code up} vertical, {@code forward} horizontal. */
    public static void leap(LivingEntity self, Location toward, double up, double forward) {
        Vector direction = toward.toVector().subtract(self.getLocation().toVector()).setY(0).normalize().multiply(forward);
        self.setVelocity(new Vector(direction.getX(), up, direction.getZ()));
    }

    /** Knock a target straight up (Storm/Void airborne kits). */
    public static void launchTarget(LivingEntity target, double up) {
        target.setVelocity(target.getVelocity().add(new Vector(0, up, 0)));
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/boss/ai/Movement.java
git commit -m "feat(boss): add Movement helper (dash, leap, launch-target)"
```

---

### Task 4: Retrofit The Fallen King to grief + add Siege Hurl

Proves the grief engine on the known-good boss: real craters, a real dark explosion, a dash routed through `Movement`, and one new block-throw attack.

**Files:**
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/boss/bosses/attacks/ShockwaveSlamAttack.java`
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/boss/bosses/attacks/JumpSlamAttack.java`
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/boss/bosses/attacks/DarkExplosionAttack.java`
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/boss/bosses/attacks/DashSlashAttack.java`
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/boss/bosses/attacks/SiegeHurlAttack.java`
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/boss/bosses/FallenKing.java`

**Interfaces:**
- Consumes: `Grief` (Task 2), `Movement` (Task 3).
- Produces: `SiegeHurlAttack(WeaponsPlugin plugin)` — a `BossAttack` named `"Siege Hurl"`.

- [ ] **Step 1: ShockwaveSlam — add a real crater on impact**

In `ShockwaveSlamAttack.java`, add import `import dev.rbm72.weaponsplugin.boss.grief.Grief;`. In the `execute` lambda (the second argument to `sequence`), after the `for (Entity nearby ...)` damage loop closes, add:

```java
                    Grief.breakCrater(ctx, origin, radius * 0.6);
```

- [ ] **Step 2: JumpSlam — add a real crater on landing**

In `JumpSlamAttack.java`, add import `import dev.rbm72.weaponsplugin.boss.grief.Grief;`. Inside the inner landing `BukkitRunnable`'s `run()`, after the `for (Entity nearby ...)` damage loop closes and before `return;`, add:

```java
                                Grief.breakCrater(ctx, boss.getLocation(), radius * 0.6);
```

- [ ] **Step 3: DarkExplosion — become a real explosion**

In `DarkExplosionAttack.java`, add import `import dev.rbm72.weaponsplugin.boss.grief.Grief;`. In the `execute` lambda, immediately after the `BossAudio.play(...)`/`Fx.sound(...)` calls and before the `for (Entity nearby ...)` loop, add:

```java
                    Grief.explosion(ctx, origin.clone().add(0, 1, 0), (float) configDouble("dark-explosion-power", 3.0));
```

(The existing particle flavor stays; this adds the terrain damage.)

- [ ] **Step 4: DashSlash — route the lunge through `Movement`**

In `DashSlashAttack.java`, add import `import dev.rbm72.weaponsplugin.boss.ai.Movement;`. Replace these two lines in the `execute` lambda:

```java
                    Vector direction = targetLoc.toVector().subtract(boss.getLocation().toVector()).setY(0).normalize();
                    boss.setVelocity(direction.multiply(dashSpeed));
```

with:

```java
                    Movement.dash(boss, targetLoc, dashSpeed);
```

Remove the now-unused `import org.bukkit.util.Vector;` only if no other `Vector` reference remains in the file (the file still uses `Vector` elsewhere — leave the import).

- [ ] **Step 5: Create `SiegeHurlAttack.java`**

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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

/** The king rips chunks of ground from beneath him and hurls them at the target. */
public final class SiegeHurlAttack extends BossAttack {

    private final double damage;
    private final float impactPower;
    private final int projectiles;
    private final int telegraphTicks;

    public SiegeHurlAttack(WeaponsPlugin plugin) {
        super(plugin, "fallen_king");
        this.damage = configDouble("siege-hurl-damage", 9.0);
        this.impactPower = (float) configDouble("siege-hurl-impact-power", 2.0);
        this.projectiles = configInt("siege-hurl-projectiles", 3);
        this.telegraphTicks = configInt("siege-hurl-telegraph-ticks", 18);
    }

    @Override
    public String name() {
        return "Siege Hurl";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("siege-hurl-cooldown-seconds", 11.0);
    }

    @Override
    public void run(AttackContext ctx, Runnable onComplete) {
        Location origin = ctx.bossLocation();
        sequence(telegraphTicks,
                () -> {
                    Telegraph.targetMarker(ctx.target());
                    Fx.coloredBurst(origin.clone().add(0, 0.3, 0), Color.fromRGB(90, 60, 30), 1.4f, 8, 0.5);
                },
                () -> {
                    BossAudio.play(origin, "boss.fallen_king.siege_hurl", Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.8f);
                    Fx.burst(origin.clone().add(0, 0.5, 0), Particle.BLOCK, 30, 0.6);
                    for (int i = 0; i < projectiles; i++) {
                        Grief.throwBlock(ctx, origin.clone().add(0, 1.2, 0), ctx.target(), Material.DEEPSLATE, damage, impactPower);
                    }
                },
                12, onComplete);
    }
}
```

- [ ] **Step 6: Register Siege Hurl in `FallenKing.java`**

Add import `import dev.rbm72.weaponsplugin.boss.bosses.attacks.SiegeHurlAttack;`. In the constructor, alongside the other phase-1 attack instantiations, add:

```java
        SiegeHurlAttack siegeHurl = new SiegeHurlAttack(plugin);
```

Add `siegeHurl` to the Phase 1 attack list and to the Enraged phase attack list. New Phase 1 line:

```java
                new BossPhase("Phase 1", 1.0,
                        List.of(heavySwing, tripleCombo, dashSlash, shockwaveSlam, summonRoyalGuards, siegeHurl),
                        false, FallenKing::onEnterPhase1),
```

Add `siegeHurl` to the end of the Enraged phase's `List.of(...)` attack list.

- [ ] **Step 7: Compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Manual verification**

On a Paper test server with the built jar: `/bossspawn fallen_king`, fight it, and confirm: Shockwave Slam and Jump Slam leave craters; Dark Explosion breaks blocks and lights fire; Siege Hurl throws deepslate blocks that explode on impact; setting `bosses.fallen_king.grief: false` in `config.yml` and `/reload` makes all four cosmetic (no block damage). Confirm no floating blocks remain after `/bossdespawn fallen_king`.

- [ ] **Step 9: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/boss/bosses/
git commit -m "feat(boss): retrofit Fallen King to griefable attacks + add Siege Hurl"
```

---

## Self-Review

- **Spec coverage:** Grief primitives (explosion/throwBlock/breakCrater/raiseColumns/spread) → Task 2. Movement (dash/leap/launchTarget) → Task 3. Per-boss grief flag + caps → Task 1. Grief-entity tracking/cleanup → Task 1. Fallen King retrofit → Task 4. All Section-1 engine items covered.
- **Placeholder scan:** none — every step shows the exact code or exact edit site.
- **Type consistency:** `griefEnabled()`/`maxExplosionPower()`/`maxCraterRadius()`/`maxFallingBlocks()` defined in Task 1, consumed by identical names in Task 2. `trackEntity`/`liveFallingBlockCount` defined in Task 1, consumed in Task 2. `Grief.*` and `Movement.*` signatures defined in Tasks 2–3, consumed by identical names in Task 4.
- **Note:** `Boss.maxExplosionPower()` returns `float`; `Grief.explosion` uses `Math.min(float, float)` → `float`, no cast needed. `configDouble("...-power", 3.0)` is cast to `float` at the call site in DarkExplosion/SiegeHurl.
