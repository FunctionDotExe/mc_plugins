# Weapon Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `WeaponsPlugin` from single-ability weapons to a 4-slot (ability1/ability2/ability3/ultimate) + passive framework, add 15 new/rebuilt weapons on it, resize the weapon menu to a double chest, and add an `/opcooldown` bypass toggle.

**Architecture:** `Weapon` base class grows 3 ability slots + 3 passive hooks (all default no-op so the 6 untouched weapons keep compiling). `CooldownManager` becomes slot-keyed. `WeaponInteractListener` routes right-click by hand+sneak state to a slot. Two new listeners (`WeaponDamageListener`, tick task) fire the passive hooks. Each weapon is one file extending `Weapon`, following the exact pattern already used by `Stormbreaker`/`ArcaneStaff`.

**Tech Stack:** Java 25, Paper API (`paper-api:26.2.build.+`), Adventure text components, Gradle (`./gradlew`). No test framework is present in this repo (no `src/test`, no JUnit/MockBukkit dependency) — verification is `./gradlew compileJava` (fast, catches all type errors) plus manual in-game checks via `/giveweapon <id>` on a local Paper server. Do not add a test framework; it's out of scope and not requested.

## Global Constraints

- Follow existing code style exactly: `final` classes, Adventure `Component`/`NamedTextColor`, config-driven numeric constants via `configDouble(key, default)` / `configInt(key, default)`, particle/sound helpers from `fx/Fx.java` (add new small static helpers there only if a genuinely new shape is needed — reuse existing ones first).
- Every weapon file lives in `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/`.
- Every new/changed weapon needs a `weapons.<id>` block added to `plugin/src/main/resources/config.yml` with every tunable the constructor reads, matching the existing block format.
- Package root: `dev.rbm72.weaponsplugin`.
- After every task: run `./gradlew compileJava` from `plugin/` and confirm `BUILD SUCCESSFUL`. This is the task's pass/fail gate in place of unit tests.
- Commit after each task (see Global Constraints in each task's steps for the exact message).

## File Structure

**Framework (modified):**
- `items/Weapon.java` — 4 ability slots + 3 passive hooks + lore assembly.
- `ability/CooldownManager.java` — slot-keyed cooldowns.
- `listeners/WeaponInteractListener.java` — hand/sneak routing, bypass check.

**Framework (new):**
- `listeners/WeaponDamageListener.java` — fires `onMeleeDamage`/`onKill`.
- `ability/WeaponTickTask.java` — fires `onTick` every 10 ticks.
- `commands/OpCooldownCommand.java` — `/opcooldown` toggle + bypass-set accessor.

**Existing weapons (mechanical rename only, no behavior change):**
- `items/weapons/{Stormbreaker,FlameKatana,ThunderHammer,FrostScythe,ShadowDaggers,WindSpear}.java`

**Rebuilt:**
- `items/weapons/ArcaneStaff.java` — full rewrite onto the new framework.

**New weapons (14 files):**
- `items/weapons/{TidalTrident,VoidBlade,SolarGreatsword,LunarBlade,PlagueScythe,DragonFang,CelestialBow,BloodReaper,ChronoBlade,StormChakrams,EarthbreakerAxe,NecromancerStaff,SakuraBlade,Starbreaker}.java`

**Menu / registration / config:**
- `gui/WeaponMenu.java` — double chest resize.
- `WeaponsPlugin.java` — register all new weapons + new listeners/task/command.
- `commands/GiveWeaponCommand.java` — no change needed (already generic).
- `plugin.yml` — add `/opcooldown` command + permission.
- `config.yml` — add a block per new/rebuilt weapon.

---

## Task 1: `Weapon` base class — 4 ability slots + passive hooks

**Files:**
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/Weapon.java`

**Interfaces:**
- Produces (used by every weapon file and by `WeaponInteractListener`/`WeaponDamageListener`/`WeaponTickTask`):
  - `abstract void ability1(Player)`, `abstract double ability1CooldownSeconds()`, `abstract List<Component> ability1Lore()`
  - `void ability2(Player)` / `double ability2CooldownSeconds()` / `List<Component> ability2Lore()` — default no-op/0/empty
  - `void ability3(Player)` / `double ability3CooldownSeconds()` / `List<Component> ability3Lore()` — default no-op/0/empty
  - `void ultimate(Player)` / `double ultimateCooldownSeconds()` / `List<Component> ultimateLore()` — default no-op/0/empty
  - `void onTick(Player)`, `void onMeleeDamage(Player, LivingEntity, EntityDamageByEntityEvent)`, `void onKill(Player, LivingEntity)` — default no-op
  - `abstract String id()`, `abstract Material material()`, `abstract String displayNameText()`, `abstract Rarity rarity()`, `abstract double baseMeleeDamage()`, `abstract Sound castSound()/hitSound()/readySound()` — unchanged from today
  - `final Component displayName()`, `final double effectiveMeleeDamage()`, `final ItemStack createItem()`, `final boolean matches(ItemStack)`, `static NamespacedKey idKey(WeaponsPlugin)` — unchanged behavior, `createItem()` lore assembly changes per below

- [ ] **Step 1: Replace the file**

Replace the entire contents of `Weapon.java` with:

```java
package dev.rbm72.weaponsplugin.items;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Base type every weapon extends. Owns all the shared, easy-to-get-wrong
 * mechanics (item construction, identification, rarity scaling, lore
 * assembly) so each concrete weapon only has to describe itself and
 * implement whichever ability slots and passive hooks it uses. Ability2,
 * ability3, ultimate, and every passive hook default to no-ops so weapons
 * that only use ability1 need not override anything else.
 */
public abstract class Weapon {

    private static final String WEAPON_ID_KEY = "weapon_id";

    protected final WeaponsPlugin plugin;

    protected Weapon(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract String id();

    public abstract Material material();

    public abstract String displayNameText();

    public abstract Rarity rarity();

    /** Flat melee damage bonus before rarity scaling. */
    public abstract double baseMeleeDamage();

    /** Right-click, main hand, not sneaking. */
    public abstract void ability1(Player player);

    public abstract double ability1CooldownSeconds();

    public abstract List<Component> ability1Lore();

    /** Right-click, main hand, sneaking. No-op unless overridden. */
    public void ability2(Player player) {
    }

    public double ability2CooldownSeconds() {
        return 0;
    }

    public List<Component> ability2Lore() {
        return List.of();
    }

    /** Right-click, off hand, not sneaking. No-op unless overridden. */
    public void ability3(Player player) {
    }

    public double ability3CooldownSeconds() {
        return 0;
    }

    public List<Component> ability3Lore() {
        return List.of();
    }

    /** Right-click, off hand, sneaking. No-op unless overridden. */
    public void ultimate(Player player) {
    }

    public double ultimateCooldownSeconds() {
        return 0;
    }

    public List<Component> ultimateLore() {
        return List.of();
    }

    public abstract Sound castSound();

    public abstract Sound hitSound();

    public abstract Sound readySound();

    /** Only overridden by weapons that fire a tagged projectile (see {@link #idKey}). */
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
    }

    /** Fires every 10 ticks while this weapon is held in the main hand. Drives passives. */
    public void onTick(Player player) {
    }

    /** Fires when this weapon (held main hand) lands a melee hit. Drives on-hit passives. */
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
    }

    /** Fires when an {@link #onMeleeDamage} hit was lethal. Drives on-kill passives. */
    public void onKill(Player attacker, LivingEntity victim) {
    }

    public final Component displayName() {
        return Component.text(displayNameText(), rarity().color())
                .decoration(TextDecoration.ITALIC, false);
    }

    public final double effectiveMeleeDamage() {
        return baseMeleeDamage() * rarity().statMultiplier();
    }

    protected final double configDouble(String key, double def) {
        return plugin.getConfig().getDouble("weapons." + id() + "." + key, def);
    }

    protected final int configInt(String key, int def) {
        return plugin.getConfig().getInt("weapons." + id() + "." + key, def);
    }

    public final ItemStack createItem() {
        ItemStack item = new ItemStack(material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(displayName());

        List<Component> lore = new ArrayList<>();
        lore.add(rarity().loreLine());
        lore.add(Component.empty());

        boolean firstBlock = true;
        for (List<Component> block : List.of(ability1Lore(), ability2Lore(), ability3Lore(), ultimateLore())) {
            if (block.isEmpty()) {
                continue;
            }
            if (!firstBlock) {
                lore.add(Component.empty());
            }
            for (Component line : block) {
                lore.add(line.decoration(TextDecoration.ITALIC, false));
            }
            firstBlock = false;
        }

        lore.add(Component.empty());
        lore.add(Component.text(String.format(Locale.ROOT, "Damage: +%.1f", effectiveMeleeDamage()), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(String.format(Locale.ROOT, "Cooldown: %.1fs", ability1CooldownSeconds()), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

        NamespacedKey damageKey = new NamespacedKey(plugin, id() + "_damage");
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                damageKey, effectiveMeleeDamage(), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, WEAPON_ID_KEY), PersistentDataType.STRING, id());

        item.setItemMeta(meta);
        return item;
    }

    public final boolean matches(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, WEAPON_ID_KEY), PersistentDataType.STRING);
        return id().equals(value);
    }

    public static NamespacedKey idKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, WEAPON_ID_KEY);
    }
}
```

- [ ] **Step 2: Compile (expected to fail)**

Run: `cd plugin && ./gradlew compileJava`
Expected: FAIL — the 7 existing weapon files still define `executeAbility`/`cooldownSeconds`/`abilityLore` instead of the new abstract `ability1`/`ability1CooldownSeconds`/`ability1Lore`, so they no longer satisfy the abstract class. This is expected; Task 4 fixes it. Do not fix it here.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/Weapon.java
git commit -m "feat: add 4-slot ability framework + passive hooks to Weapon base class"
```

---

## Task 2: `CooldownManager` — slot-keyed cooldowns

**Files:**
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/ability/CooldownManager.java`

**Interfaces:**
- Consumes: `Weapon.id()`, `Weapon.displayName()`, `Weapon.matches(ItemStack)`, `Weapon.rarity().particle()`, `Weapon.readySound()` — all unchanged from Task 1.
- Produces (used by `WeaponInteractListener` in Task 3):
  - `public enum Slot { ABILITY1, ABILITY2, ABILITY3, ULTIMATE }`
  - `boolean isOnCooldown(Player player, String weaponId, Slot slot)`
  - `void start(Player player, Weapon weapon, Slot slot, double durationSeconds)`

- [ ] **Step 1: Replace the file**

Replace the entire contents of `CooldownManager.java` with:

```java
package dev.rbm72.weaponsplugin.ability;

import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every active cooldown across every player, weapon, and ability slot.
 * A weapon never touches an action bar, boss bar, or durability meter
 * itself — it just calls {@link #start} and this class handles the rest.
 */
public final class CooldownManager {

    /** Cooldowns at or above this length also get a boss bar, not just the action bar. */
    private static final long BOSS_BAR_THRESHOLD_MS = 8000;
    private static final long TICK_INTERVAL = 2L;

    public enum Slot {
        ABILITY1(""),
        ABILITY2(" (2)"),
        ABILITY3(" (3)"),
        ULTIMATE(" (Ultimate)");

        final String suffix;

        Slot(String suffix) {
            this.suffix = suffix;
        }
    }

    private final Plugin plugin;
    private final Map<UUID, Map<String, Active>> active = new HashMap<>();

    public CooldownManager(Plugin plugin) {
        this.plugin = plugin;
    }

    private static final class Active {
        final long startMs;
        final long durationMs;
        final Slot slot;
        BukkitTask task;
        BossBar bossBar;

        Active(long startMs, long durationMs, Slot slot) {
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.slot = slot;
        }
    }

    private static String key(String weaponId, Slot slot) {
        return weaponId + ":" + slot.name();
    }

    public boolean isOnCooldown(Player player, String weaponId, Slot slot) {
        Map<String, Active> map = active.get(player.getUniqueId());
        return map != null && map.containsKey(key(weaponId, slot));
    }

    public void start(Player player, Weapon weapon, Slot slot, double durationSeconds) {
        UUID uuid = player.getUniqueId();
        long durationMs = Math.round(durationSeconds * 1000);
        Active a = new Active(System.currentTimeMillis(), durationMs, slot);
        active.computeIfAbsent(uuid, k -> new HashMap<>()).put(key(weapon.id(), slot), a);

        if (durationMs >= BOSS_BAR_THRESHOLD_MS) {
            a.bossBar = BossBar.bossBar(weapon.displayName().append(Component.text(slot.suffix)),
                    1.0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
            player.showBossBar(a.bossBar);
        }

        a.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player, weapon, a), 0L, TICK_INTERVAL);
    }

    private void tick(Player player, Weapon weapon, Active a) {
        if (!player.isOnline()) {
            finish(player, weapon, a, false);
            return;
        }

        long elapsed = System.currentTimeMillis() - a.startMs;
        double fraction = Math.min(1.0, elapsed / (double) a.durationMs);
        double remaining = Math.max(0, (a.durationMs - elapsed) / 1000.0);

        player.sendActionBar(weapon.displayName()
                .append(Component.text(a.slot.suffix + String.format(Locale.ROOT, " %.1fs", remaining), NamedTextColor.YELLOW)));

        if (a.bossBar != null) {
            a.bossBar.progress((float) (1.0 - fraction));
        }

        if (a.slot == Slot.ABILITY1) {
            updateDurability(player, weapon, 1.0 - fraction);
        }

        if (elapsed >= a.durationMs) {
            finish(player, weapon, a, true);
        }
    }

    private void updateDurability(Player player, Weapon weapon, double remainingFraction) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!weapon.matches(item)) {
            return;
        }
        short maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        damageable.setDamage((int) Math.round(maxDurability * remainingFraction));
        item.setItemMeta(meta);
    }

    private void finish(Player player, Weapon weapon, Active a, boolean ready) {
        a.task.cancel();
        if (a.bossBar != null) {
            player.hideBossBar(a.bossBar);
        }

        Map<String, Active> map = active.get(player.getUniqueId());
        if (map != null) {
            map.remove(key(weapon.id(), a.slot));
            if (map.isEmpty()) {
                active.remove(player.getUniqueId());
            }
        }

        if (ready && player.isOnline()) {
            if (a.slot == Slot.ABILITY1) {
                updateDurability(player, weapon, 0.0);
            }
            Fx.sound(player, weapon.readySound(), 0.7f, 1.6f);
            Fx.burst(player.getLocation().add(0, 1, 0), weapon.rarity().particle(), 12, 0.4);
            player.sendActionBar(weapon.displayName()
                    .append(Component.text(a.slot.suffix + " READY", NamedTextColor.GREEN)));
        }
    }
}
```

- [ ] **Step 2: Compile (still expected to fail, same reason as Task 1)**

Run: `cd plugin && ./gradlew compileJava`
Expected: FAIL — `WeaponInteractListener` (Task 3) still calls the old `isOnCooldown(player, weaponId)`/`start(player, weapon)` two-arg signatures. Expected; do not fix here.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/ability/CooldownManager.java
git commit -m "feat: key cooldowns by ability slot instead of just weapon id"
```

---

## Task 3: `/opcooldown` command

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/commands/OpCooldownCommand.java`
- Modify: `plugin/src/main/resources/plugin.yml`

**Interfaces:**
- Produces (used by `WeaponInteractListener` in Task 4):
  - `public boolean hasBypass(Player player)`

- [ ] **Step 1: Create `OpCooldownCommand.java`**

```java
package dev.rbm72.weaponsplugin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player, in-memory toggle (resets on restart, same as cooldowns
 * themselves) that lets ops skip every weapon cooldown entirely while
 * testing.
 */
public final class OpCooldownCommand implements CommandExecutor {

    private final Set<UUID> bypassing = new HashSet<>();

    public boolean hasBypass(Player player) {
        return bypassing.contains(player.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (bypassing.remove(uuid)) {
            player.sendMessage(Component.text("Cooldown bypass: OFF", NamedTextColor.YELLOW));
        } else {
            bypassing.add(uuid);
            player.sendMessage(Component.text("Cooldown bypass: ON", NamedTextColor.GREEN));
        }
        return true;
    }
}
```

- [ ] **Step 2: Add the command + permission to `plugin.yml`**

In `plugin/src/main/resources/plugin.yml`, change:

```yaml
commands:
  giveweapon:
    description: Gives you a custom weapon
    usage: /giveweapon <id>
    permission: weaponsplugin.give
  weapons:
    description: Opens the weapon menu
    usage: /weapons

permissions:
  weaponsplugin.give:
    description: Allows giving yourself custom weapons
    default: op
```

to:

```yaml
commands:
  giveweapon:
    description: Gives you a custom weapon
    usage: /giveweapon <id>
    permission: weaponsplugin.give
  weapons:
    description: Opens the weapon menu
    usage: /weapons
  opcooldown:
    description: Toggles cooldown bypass for every weapon
    usage: /opcooldown
    permission: weaponsplugin.opcooldown

permissions:
  weaponsplugin.give:
    description: Allows giving yourself custom weapons
    default: op
  weaponsplugin.opcooldown:
    description: Allows toggling cooldown bypass
    default: op
```

- [ ] **Step 3: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: still FAILS for the same pre-existing reasons as Tasks 1-2 (old weapon files, old `WeaponInteractListener`). `OpCooldownCommand.java` itself introduces no new errors — confirm the compiler output doesn't mention `OpCooldownCommand`.

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/commands/OpCooldownCommand.java plugin/src/main/resources/plugin.yml
git commit -m "feat: add /opcooldown bypass toggle command"
```

---

## Task 4: Ability-slot routing + passive-hook listeners + plugin wiring

**Files:**
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/listeners/WeaponInteractListener.java`
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/listeners/WeaponDamageListener.java`
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/ability/WeaponTickTask.java`
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java`

**Interfaces:**
- Consumes: `CooldownManager.Slot`, `CooldownManager.isOnCooldown(Player, String, Slot)`, `CooldownManager.start(Player, Weapon, Slot, double)` (Task 2); `OpCooldownCommand.hasBypass(Player)` (Task 3); `Weapon.ability1/2/3(Player)`, `Weapon.ultimate(Player)`, `Weapon.ability1/2/3CooldownSeconds()`, `Weapon.ultimateCooldownSeconds()`, `Weapon.onTick(Player)`, `Weapon.onMeleeDamage(...)`, `Weapon.onKill(...)` (Task 1).

- [ ] **Step 1: Replace `WeaponInteractListener.java`**

```java
package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.ability.CooldownManager.Slot;
import dev.rbm72.weaponsplugin.commands.OpCooldownCommand;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Routes right-click to one of four ability slots based on which hand
 * holds the weapon and whether the player is sneaking:
 * main+no-sneak = ability1, main+sneak = ability2,
 * off-hand+no-sneak = ability3, off-hand+sneak = ultimate.
 */
public final class WeaponInteractListener implements Listener {

    private final WeaponRegistry registry;
    private final CooldownManager cooldowns;
    private final OpCooldownCommand opCooldown;

    public WeaponInteractListener(WeaponRegistry registry, CooldownManager cooldowns, OpCooldownCommand opCooldown) {
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.opCooldown = opCooldown;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        boolean offHand = event.getHand() == EquipmentSlot.OFF_HAND;
        ItemStack heldItem = offHand
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        Weapon weapon = registry.identify(heldItem).orElse(null);
        if (weapon == null) {
            return;
        }

        event.setCancelled(true);

        boolean sneaking = player.isSneaking();
        Slot slot;
        double durationSeconds;
        if (!offHand && !sneaking) {
            slot = Slot.ABILITY1;
            durationSeconds = weapon.ability1CooldownSeconds();
        } else if (!offHand) {
            slot = Slot.ABILITY2;
            durationSeconds = weapon.ability2CooldownSeconds();
        } else if (!sneaking) {
            slot = Slot.ABILITY3;
            durationSeconds = weapon.ability3CooldownSeconds();
        } else {
            slot = Slot.ULTIMATE;
            durationSeconds = weapon.ultimateCooldownSeconds();
        }

        boolean bypass = opCooldown.hasBypass(player);

        if (!bypass && cooldowns.isOnCooldown(player, weapon.id(), slot)) {
            Fx.sound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        if (!bypass) {
            cooldowns.start(player, weapon, slot, durationSeconds);
        }
        Fx.sound(player, weapon.castSound(), 1.0f, 1.0f);

        switch (slot) {
            case ABILITY1 -> weapon.ability1(player);
            case ABILITY2 -> weapon.ability2(player);
            case ABILITY3 -> weapon.ability3(player);
            case ULTIMATE -> weapon.ultimate(player);
        }
    }
}
```

- [ ] **Step 2: Create `WeaponDamageListener.java`**

```java
package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Fires each weapon's onMeleeDamage/onKill passive hooks on melee hits. */
public final class WeaponDamageListener implements Listener {

    private final WeaponRegistry registry;

    public WeaponDamageListener(WeaponRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Weapon weapon = registry.identify(attacker.getInventory().getItemInMainHand()).orElse(null);
        if (weapon == null) {
            return;
        }

        weapon.onMeleeDamage(attacker, victim, event);

        if (victim.getHealth() - event.getFinalDamage() <= 0) {
            weapon.onKill(attacker, victim);
        }
    }
}
```

- [ ] **Step 3: Create `WeaponTickTask.java`**

```java
package dev.rbm72.weaponsplugin.ability;

import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Fires each held weapon's onTick passive hook every 10 ticks (0.5s). */
public final class WeaponTickTask extends BukkitRunnable {

    private final WeaponRegistry registry;

    public WeaponTickTask(WeaponRegistry registry) {
        this.registry = registry;
    }

    public void start(Plugin plugin) {
        runTaskTimer(plugin, 10L, 10L);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            registry.identify(player.getInventory().getItemInMainHand())
                    .ifPresent(weapon -> weapon.onTick(player));
        }
    }
}
```

- [ ] **Step 4: Wire it all into `WeaponsPlugin.java`**

Replace the entire contents of `WeaponsPlugin.java` with:

```java
package dev.rbm72.weaponsplugin;

import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.ability.WeaponTickTask;
import dev.rbm72.weaponsplugin.commands.GiveWeaponCommand;
import dev.rbm72.weaponsplugin.commands.OpCooldownCommand;
import dev.rbm72.weaponsplugin.commands.WeaponMenuCommand;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import dev.rbm72.weaponsplugin.items.weapons.ArcaneStaff;
import dev.rbm72.weaponsplugin.items.weapons.FlameKatana;
import dev.rbm72.weaponsplugin.items.weapons.FrostScythe;
import dev.rbm72.weaponsplugin.items.weapons.ShadowDaggers;
import dev.rbm72.weaponsplugin.items.weapons.Stormbreaker;
import dev.rbm72.weaponsplugin.items.weapons.ThunderHammer;
import dev.rbm72.weaponsplugin.items.weapons.WindSpear;
import dev.rbm72.weaponsplugin.listeners.MagicProjectileListener;
import dev.rbm72.weaponsplugin.listeners.WeaponDamageListener;
import dev.rbm72.weaponsplugin.listeners.WeaponInteractListener;
import dev.rbm72.weaponsplugin.listeners.WeaponMenuListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class WeaponsPlugin extends JavaPlugin {

    private WeaponRegistry weaponRegistry;
    private CooldownManager cooldownManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        weaponRegistry = new WeaponRegistry();
        cooldownManager = new CooldownManager(this);
        OpCooldownCommand opCooldownCommand = new OpCooldownCommand();

        weaponRegistry.register(new Stormbreaker(this));
        weaponRegistry.register(new FlameKatana(this));
        weaponRegistry.register(new ThunderHammer(this));
        weaponRegistry.register(new FrostScythe(this));
        weaponRegistry.register(new ShadowDaggers(this));
        weaponRegistry.register(new ArcaneStaff(this));
        weaponRegistry.register(new WindSpear(this));

        getServer().getPluginManager().registerEvents(
                new WeaponInteractListener(weaponRegistry, cooldownManager, opCooldownCommand), this);
        getServer().getPluginManager().registerEvents(
                new WeaponDamageListener(weaponRegistry), this);
        getServer().getPluginManager().registerEvents(
                new MagicProjectileListener(this, weaponRegistry), this);
        getServer().getPluginManager().registerEvents(new WeaponMenuListener(this), this);

        new WeaponTickTask(weaponRegistry).start(this);

        getCommand("giveweapon").setExecutor(new GiveWeaponCommand(weaponRegistry));
        getCommand("weapons").setExecutor(new WeaponMenuCommand(this));
        getCommand("opcooldown").setExecutor(opCooldownCommand);

        getLogger().info("WeaponsPlugin enabled with " + weaponRegistry.all().size() + " weapon(s)");
    }

    @Override
    public void onDisable() {
        getLogger().info("WeaponsPlugin disabled");
    }

    public WeaponRegistry weaponRegistry() {
        return weaponRegistry;
    }
}
```

- [ ] **Step 5: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: FAILS only on the 6 unrenamed weapon files (`Stormbreaker`, `FlameKatana`, `ThunderHammer`, `FrostScythe`, `ShadowDaggers`, `WindSpear`) and `ArcaneStaff` not yet implementing the new abstract `ability1`/`ability1CooldownSeconds`/`ability1Lore`. Confirm no other errors are reported.

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/listeners/WeaponInteractListener.java plugin/src/main/java/dev/rbm72/weaponsplugin/listeners/WeaponDamageListener.java plugin/src/main/java/dev/rbm72/weaponsplugin/ability/WeaponTickTask.java plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: route right-click by hand+sneak to ability slots, wire passive-hook listeners"
```

---

## Task 5: Rename the 6 existing weapons onto ability1 (mechanical, no behavior change)

**Files:**
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/Stormbreaker.java`
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/FlameKatana.java`
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/ThunderHammer.java`
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/FrostScythe.java`
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/ShadowDaggers.java`
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/WindSpear.java`

**Interfaces:** No new signatures — this task only satisfies `Weapon`'s abstract `ability1`/`ability1CooldownSeconds`/`ability1Lore` (Task 1) by renaming the existing overrides. Method bodies are untouched.

All 6 files have the identical 3-line signature pattern (confirmed by grep before writing this plan). Apply these 3 exact edits to **each** of the 6 files — only the method signature line changes, the body below each stays exactly as-is:

- [ ] **Step 1: Rename `cooldownSeconds()` → `ability1CooldownSeconds()` in all 6 files**

In each file, change:
```java
    public double cooldownSeconds() {
```
to:
```java
    public double ability1CooldownSeconds() {
```

- [ ] **Step 2: Rename `abilityLore()` → `ability1Lore()` in all 6 files**

In each file, change:
```java
    public List<Component> abilityLore() {
```
to:
```java
    public List<Component> ability1Lore() {
```

- [ ] **Step 3: Rename `executeAbility(Player player)` → `ability1(Player player)` in all 6 files**

In each file, change:
```java
    public void executeAbility(Player player) {
```
to:
```java
    public void ability1(Player player) {
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: FAILS only on `ArcaneStaff.java` now (same 3 old method names — it's rebuilt in Task 7, not renamed here, since its whole ability kit changes). Confirm the other 6 weapon files no longer appear in the error output.

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/Stormbreaker.java plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/FlameKatana.java plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/ThunderHammer.java plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/FrostScythe.java plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/ShadowDaggers.java plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/WindSpear.java
git commit -m "refactor: rename existing weapons' single ability onto the ability1 slot"
```

---

## Task 6: Weapon menu — double chest resize

**Files:**
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/gui/WeaponMenu.java`

**Interfaces:** No signature changes — `WeaponMenuHolder`, `WeaponMenuListener`, `WeaponMenuCommand` all read slot contents rather than fixed indices (except this file's own `FILTER_SLOTS` constant, which moves) so none of them need changes.

- [ ] **Step 1: Resize the grid and move the filter bar**

In `WeaponMenu.java`, change:
```java
    private static final int SIZE = 18;
    private static final int[] FILTER_SLOTS = {9, 10, 11, 12, 13, 14};
```
to:
```java
    private static final int SIZE = 54;
    private static final int[] FILTER_SLOTS = {36, 37, 38, 39, 40, 41};
```

- [ ] **Step 2: Raise the weapon-grid cap and widen the filler row**

Change:
```java
            if (slot >= 9) {
                break;
            }
```
to:
```java
            if (slot >= 36) {
                break;
            }
```

Change:
```java
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 15; i < 18; i++) {
            inventory.setItem(i, filler);
        }
```
to:
```java
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 42; i < 54; i++) {
            inventory.setItem(i, filler);
        }
```

- [ ] **Step 3: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: FAILS only on `ArcaneStaff.java` (Task 7 fixes it). Confirm `WeaponMenu.java` introduces no new errors.

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/gui/WeaponMenu.java
git commit -m "feat: resize weapon menu to a double chest so all weapons fit unfiltered"
```

---

## Task 7: Rebuild `ArcaneStaff` onto the new framework

**Files:**
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/ArcaneStaff.java`
- Modify: `plugin/src/main/resources/config.yml` (replace the `arcane_staff` block)

**Note on the passive:** the spec's "cooldowns tick down faster while standing still" would require adding a rate-shift API to `CooldownManager` (Task 2) that no other weapon needs. To avoid retrofitting the cooldown system for one weapon, this task implements the same "standing still rewards you" flavor as a **damage bonus while standing still** instead (checked locally inside this file, no framework changes) — same player-facing incentive, self-contained.

**Interfaces:**
- Consumes: `Weapon` abstract class (Task 1), `Fx` helpers (unchanged), `Weapon.idKey(plugin)` (unchanged), `MagicProjectileListener` (unchanged — already calls `weapon.onProjectileHit`).

- [ ] **Step 1: Replace the file**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Mage staff: rapid-fire missiles, a hitscan beam, a short blink, and a
 * channeled laser ultimate. Rewards standing still (a mage "channeling")
 * with bonus damage on every ability.
 */
public final class ArcaneStaff extends Weapon {

    private final double missileDamage;
    private final double missileSpeed;
    private final int missileCount;
    private final double beamDamage;
    private final double beamRange;
    private final double blinkDistance;
    private final double laserDamagePerTick;
    private final int laserDurationTicks;
    private final double laserRange;
    private final double standingStillBonus;

    public ArcaneStaff(WeaponsPlugin plugin) {
        super(plugin);
        this.missileDamage = configDouble("missile-damage", 4.0);
        this.missileSpeed = configDouble("missile-speed", 2.0);
        this.missileCount = configInt("missile-count", 3);
        this.beamDamage = configDouble("beam-damage", 9.0);
        this.beamRange = configDouble("beam-range", 15.0);
        this.blinkDistance = configDouble("blink-distance", 6.0);
        this.laserDamagePerTick = configDouble("laser-damage-per-tick", 3.0);
        this.laserDurationTicks = configInt("laser-duration-ticks", 40);
        this.laserRange = configDouble("laser-range", 18.0);
        this.standingStillBonus = configDouble("standing-still-damage-bonus", 0.2);
    }

    @Override
    public String id() {
        return "arcane_staff";
    }

    @Override
    public Material material() {
        return Material.BLAZE_ROD;
    }

    @Override
    public String displayNameText() {
        return "Arcane Staff";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 1.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 7.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 8.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 45.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: fire a rapid volley of", NamedTextColor.GRAY),
                Component.text("magic missiles.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: fire an instant", NamedTextColor.GRAY),
                Component.text("beam of arcane energy.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: blink forward a short", NamedTextColor.GRAY),
                Component.text("distance.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: channel a massive", NamedTextColor.GRAY),
                Component.text("arcane laser. Standing still boosts", NamedTextColor.GRAY),
                Component.text("every ability's damage.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ILLUSIONER_CAST_SPELL;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_DRAGON_FIREBALL_EXPLODE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_ENCHANTMENT_TABLE_USE;
    }

    private double standingStillMultiplier(Player player) {
        return player.getVelocity().lengthSquared() < 0.0025 ? 1.0 + standingStillBonus : 1.0;
    }

    @Override
    public void ability1(Player player) {
        double multiplier = standingStillMultiplier(player);
        for (int i = 0; i < missileCount; i++) {
            int delay = i * 3;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Snowball missile = player.launchProjectile(Snowball.class,
                        player.getLocation().getDirection().multiply(missileSpeed));
                missile.setGravity(false);
                missile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
                missile.getPersistentDataContainer().set(missileDamageKey(), PersistentDataType.DOUBLE, missileDamage * multiplier);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!missile.isValid()) {
                            cancel();
                            return;
                        }
                        Fx.point(missile.getLocation(), Particle.END_ROD, 2);
                        Fx.point(missile.getLocation(), Particle.WITCH, 1);
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }, delay);
        }
    }

    private org.bukkit.NamespacedKey missileDamageKey() {
        return new org.bukkit.NamespacedKey(plugin, "arcane_staff_missile_damage");
    }

    @Override
    public void ability2(Player player) {
        double damage = beamDamage * rarity().statMultiplier() * standingStillMultiplier(player);
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), beamRange,
                entity -> entity instanceof LivingEntity && !entity.equals(player));

        Location end = result != null ? result.getHitPosition().toLocation(player.getWorld())
                : eye.clone().add(eye.getDirection().multiply(beamRange));
        Fx.line(eye, end, Particle.END_ROD, 20);
        Fx.sound(player, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.4f);

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, player);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
            Fx.burst(target.getLocation().add(0, 1, 0), Particle.WITCH, 12, 0.3);
        }
    }

    @Override
    public void ability3(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        Location target = player.getLocation().clone();
        double step = 0.5;
        Location best = player.getLocation();
        for (double d = step; d <= blinkDistance; d += step) {
            Location candidate = player.getLocation().add(direction.clone().multiply(d));
            if (!candidate.getBlock().getType().isSolid() && !candidate.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                best = candidate;
            } else {
                break;
            }
        }
        Fx.line(player.getLocation().add(0, 1, 0), best.clone().add(0, 1, 0), Particle.PORTAL, 10);
        player.teleport(best);
        Fx.burst(best, Particle.PORTAL, 20, 0.4);
        Fx.sound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
    }

    @Override
    public void ultimate(Player player) {
        double damagePerTick = laserDamagePerTick * rarity().statMultiplier() * standingStillMultiplier(player);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= laserDurationTicks) {
                    cancel();
                    return;
                }
                Location eye = player.getEyeLocation();
                RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), laserRange,
                        entity -> entity instanceof LivingEntity && !entity.equals(player));
                Location end = result != null ? result.getHitPosition().toLocation(player.getWorld())
                        : eye.clone().add(eye.getDirection().multiply(laserRange));
                Fx.line(eye, end, Particle.END_ROD, 15);
                Fx.line(eye, end, Particle.DRAGON_BREATH, 8);

                if (result != null && result.getHitEntity() instanceof LivingEntity target) {
                    target.damage(damagePerTick, player);
                    Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        Fx.sound(player, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        Double taggedDamage = event.getEntity().getPersistentDataContainer()
                .get(missileDamageKey(), PersistentDataType.DOUBLE);
        double damage = taggedDamage != null ? taggedDamage : missileDamage * rarity().statMultiplier();

        Fx.burst(loc, Particle.WITCH, 14, 0.4);
        Fx.burst(loc, Particle.END_ROD, 10, 0.3);
        Fx.sound(loc, hitSound(), 0.8f, 1.3f);

        Entity hitEntity = event.getHitEntity();
        if (hitEntity instanceof LivingEntity target) {
            target.damage(damage, shooter);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
    }
}
```

- [ ] **Step 2: Replace the `arcane_staff` block in `config.yml`**

Change:
```yaml
  arcane_staff:
    cooldown-seconds: 5.0
    melee-damage-bonus: 1.0
    ability-damage: 10.0
    splash-damage: 4.0
    projectile-speed: 1.8
    splash-radius: 2.5
    fork-chance: 0.35
```
to:
```yaml
  arcane_staff:
    melee-damage-bonus: 1.0
    ability1-cooldown-seconds: 5.0
    ability2-cooldown-seconds: 7.0
    ability3-cooldown-seconds: 8.0
    ultimate-cooldown-seconds: 45.0
    missile-damage: 4.0
    missile-speed: 2.0
    missile-count: 3
    beam-damage: 9.0
    beam-range: 15.0
    blink-distance: 6.0
    laser-damage-per-tick: 3.0
    laser-duration-ticks: 40
    laser-range: 18.0
    standing-still-damage-bonus: 0.2
```

- [ ] **Step 3: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. This is the first fully green compile since Task 1 — all 7 original weapons plus the framework now compile clean.

- [ ] **Step 4: Manual verification**

Run a local Paper server with the built plugin jar, join, run `/giveweapon arcane_staff`, and check: right-click fires 3 missiles that explode on impact; shift+right-click fires a beam; F to swap to off-hand then right-click blinks you forward; F+shift+right-click channels the laser for ~2s. Standing still should visibly hit harder than moving (compare damage numbers or a hostile mob's health bar).

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/ArcaneStaff.java plugin/src/main/resources/config.yml
git commit -m "feat: rebuild Arcane Staff onto the 4-slot ability framework"
```

---

## Task 8: Tidal Trident

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/TidalTrident.java`
- Modify: `plugin/src/main/resources/config.yml` (append `tidal_trident` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Interfaces:** Extends `Weapon` (Task 1) only — no cross-weapon dependencies.

- [ ] **Step 1: Create `TidalTrident.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Water-themed trident: wave cone, whirlpool pull, riptide jet, tsunami ultimate. */
public final class TidalTrident extends Weapon {

    private final double waveDamage;
    private final double waveRange;
    private final double whirlpoolDamagePerTick;
    private final double whirlpoolRadius;
    private final int whirlpoolDurationTicks;
    private final double jetSpeed;
    private final double jetDamage;
    private final int jetTicks;
    private final double jetHitRadius;
    private final double tsunamiMaxRadius;
    private final double tsunamiDamage;
    private final int tsunamiRings;

    public TidalTrident(WeaponsPlugin plugin) {
        super(plugin);
        this.waveDamage = configDouble("wave-damage", 6.0);
        this.waveRange = configDouble("wave-range", 5.0);
        this.whirlpoolDamagePerTick = configDouble("whirlpool-damage-per-tick", 1.0);
        this.whirlpoolRadius = configDouble("whirlpool-radius", 4.0);
        this.whirlpoolDurationTicks = configInt("whirlpool-duration-ticks", 40);
        this.jetSpeed = configDouble("jet-speed", 1.8);
        this.jetDamage = configDouble("jet-damage", 5.0);
        this.jetTicks = configInt("jet-ticks", 12);
        this.jetHitRadius = configDouble("jet-hit-radius", 1.6);
        this.tsunamiMaxRadius = configDouble("tsunami-max-radius", 8.0);
        this.tsunamiDamage = configDouble("tsunami-damage", 10.0);
        this.tsunamiRings = configInt("tsunami-rings", 4);
    }

    @Override
    public String id() {
        return "tidal_trident";
    }

    @Override
    public Material material() {
        return Material.TRIDENT;
    }

    @Override
    public String displayNameText() {
        return "Tidal Trident";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 8.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 6.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 50.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: summon a wave that", NamedTextColor.GRAY),
                Component.text("damages and knocks back enemies", NamedTextColor.GRAY),
                Component.text("in front of you.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: spawn a whirlpool", NamedTextColor.GRAY),
                Component.text("that pulls nearby enemies in.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: launch yourself forward", NamedTextColor.GRAY),
                Component.text("on a jet of water, damaging enemies", NamedTextColor.GRAY),
                Component.text("in your path.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: summon a tidal", NamedTextColor.GRAY),
                Component.text("tsunami that expands outward.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_TRIDENT_RIPTIDE_2;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_SPLASH;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_WATER_AMBIENT;
    }

    @Override
    public void onTick(Player player) {
        if (player.isInWater()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 30, 0, true, false));
        }
    }

    @Override
    public void ability1(Player player) {
        double damage = waveDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        Fx.trail(origin.clone().add(0, 1, 0), Particle.SPLASH, 30, 0.8, 0.1);

        for (Entity entity : world.getNearbyEntities(origin, waveRange, waveRange, waveRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(origin.toVector()).normalize();
            if (direction.dot(toEntity) < 0.5) {
                continue;
            }
            living.damage(damage, player);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            living.setVelocity(living.getVelocity().add(direction.clone().multiply(1.2).setY(0.3)));
        }
    }

    @Override
    public void ability2(Player player) {
        double damagePerTick = whirlpoolDamagePerTick * rarity().statMultiplier();
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(5));
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= whirlpoolDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                Fx.ring(center, Particle.SPLASH, whirlpoolRadius * (1 - ticks / (double) whirlpoolDurationTicks), 16);

                for (Entity entity : world.getNearbyEntities(center, whirlpoolRadius, whirlpoolRadius, whirlpoolRadius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                        continue;
                    }
                    Vector pull = center.toVector().subtract(living.getLocation().toVector()).normalize().multiply(0.15);
                    living.setVelocity(living.getVelocity().add(pull));
                    if (ticks % 10 == 0) {
                        living.damage(damagePerTick, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability3(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(jetSpeed).setY(0.4));

        double damage = jetDamage * rarity().statMultiplier();
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= jetTicks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation().add(0, 1, 0);
                Fx.trail(loc, Particle.SPLASH, 8, 0.3, 0.05);

                for (Entity nearby : player.getNearbyEntities(jetHitRadius, jetHitRadius, jetHitRadius)) {
                    if (!(nearby instanceof LivingEntity entity) || !alreadyHit.add(entity.getUniqueId())) {
                        continue;
                    }
                    entity.damage(damage, player);
                    Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                    entity.setVelocity(entity.getVelocity().add(direction.clone().multiply(0.8).setY(0.3)));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        double damage = tsunamiDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 0.8f);
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ring = 0;

            @Override
            public void run() {
                if (ring >= tsunamiRings) {
                    cancel();
                    return;
                }
                double radius = tsunamiMaxRadius * (ring + 1) / (double) tsunamiRings;
                Fx.ring(center, Particle.SPLASH, radius, 24 + ring * 8);

                for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player) || !alreadyHit.add(living.getUniqueId())) {
                        continue;
                    }
                    living.damage(damage, player);
                    living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                    Vector knockback = living.getLocation().toVector().subtract(center.toVector()).normalize().setY(0.4);
                    living.setVelocity(living.getVelocity().add(knockback));
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                }
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }
}
```

- [ ] **Step 2: Append the config block**

Append to the end of `weapons:` in `plugin/src/main/resources/config.yml`:
```yaml
  tidal_trident:
    melee-damage-bonus: 2.0
    ability1-cooldown-seconds: 5.0
    ability2-cooldown-seconds: 8.0
    ability3-cooldown-seconds: 6.0
    ultimate-cooldown-seconds: 50.0
    wave-damage: 6.0
    wave-range: 5.0
    whirlpool-damage-per-tick: 1.0
    whirlpool-radius: 4.0
    whirlpool-duration-ticks: 40
    jet-speed: 1.8
    jet-damage: 5.0
    jet-ticks: 12
    jet-hit-radius: 1.6
    tsunami-max-radius: 8.0
    tsunami-damage: 10.0
    tsunami-rings: 4
```

- [ ] **Step 3: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.WindSpear;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.WindSpear;
import dev.rbm72.weaponsplugin.items.weapons.TidalTrident;
```

Change:
```java
        weaponRegistry.register(new WindSpear(this));
```
to:
```java
        weaponRegistry.register(new WindSpear(this));
        weaponRegistry.register(new TidalTrident(this));
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual verification**

`/giveweapon tidal_trident` — right-click hits a cone of enemies with knockback; shift+right-click pulls nearby mobs into a whirlpool; off-hand right-click launches you forward damaging what you pass through; off-hand+shift triggers the expanding tsunami. Standing in water should visibly speed you up (dolphin's grace bubbles).

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/TidalTrident.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Tidal Trident weapon"
```

---

## Task 9: Void Blade

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/VoidBlade.java`
- Modify: `plugin/src/main/resources/config.yml` (append `void_blade` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Interfaces:** Extends `Weapon` (Task 1) only.

- [ ] **Step 1: Create `VoidBlade.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Space/void sword: wall-phasing teleport, a pulling rift, a phase-dash, and a collapsing black hole ultimate. */
public final class VoidBlade extends Weapon {

    private final double armorBypassChance;
    private final double phaseRange;
    private final double riftPullRadius;
    private final double riftDamage;
    private final int riftDurationTicks;
    private final double slashDamage;
    private final int slashTicks;
    private final double slashHitRadius;
    private final double blackHolePullRadius;
    private final int blackHolePullDurationTicks;
    private final double blackHoleExplosionDamage;
    private final double blackHoleExplosionRadius;

    public VoidBlade(WeaponsPlugin plugin) {
        super(plugin);
        this.armorBypassChance = configDouble("armor-bypass-chance", 0.1);
        this.phaseRange = configDouble("phase-range", 6.0);
        this.riftPullRadius = configDouble("rift-pull-radius", 5.0);
        this.riftDamage = configDouble("rift-damage", 3.0);
        this.riftDurationTicks = configInt("rift-duration-ticks", 30);
        this.slashDamage = configDouble("slash-damage", 6.0);
        this.slashTicks = configInt("slash-ticks", 6);
        this.slashHitRadius = configDouble("slash-hit-radius", 1.6);
        this.blackHolePullRadius = configDouble("black-hole-pull-radius", 7.0);
        this.blackHolePullDurationTicks = configInt("black-hole-pull-duration-ticks", 40);
        this.blackHoleExplosionDamage = configDouble("black-hole-explosion-damage", 14.0);
        this.blackHoleExplosionRadius = configDouble("black-hole-explosion-radius", 5.0);
    }

    @Override
    public String id() {
        return "void_blade";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Void Blade";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 7.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 9.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 6.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: teleport forward,", NamedTextColor.GRAY),
                Component.text("phasing straight through walls.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: open a void rift", NamedTextColor.GRAY),
                Component.text("that pulls nearby enemies in.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: phase-dash forward,", NamedTextColor.GRAY),
                Component.text("damaging enemies you pass through.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: collapse a black", NamedTextColor.GRAY),
                Component.text("hole that pulls enemies in, then", NamedTextColor.GRAY),
                Component.text("explodes.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ENDERMAN_TELEPORT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_WARDEN_SONIC_BOOM;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_PORTAL_AMBIENT;
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (ThreadLocalRandom.current().nextDouble() < armorBypassChance) {
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0.0);
            Fx.burst(victim.getLocation().add(0, 1, 0), Particle.PORTAL, 10, 0.3);
        }
    }

    @Override
    public void ability1(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        Location best = player.getLocation();
        for (double d = 0.5; d <= phaseRange; d += 0.5) {
            Location candidate = player.getLocation().add(direction.clone().multiply(d));
            if (!candidate.getBlock().getType().isSolid() && !candidate.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                best = candidate;
            }
        }
        Fx.line(player.getLocation().add(0, 1, 0), best.clone().add(0, 1, 0), Particle.PORTAL, 12);
        player.teleport(best);
        Fx.burst(best, Particle.PORTAL, 25, 0.5);
    }

    @Override
    public void ability2(Player player) {
        double damage = riftDamage * rarity().statMultiplier();
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(4));
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= riftDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                Fx.point(center, Particle.REVERSE_PORTAL, 3);

                for (Entity entity : world.getNearbyEntities(center, riftPullRadius, riftPullRadius, riftPullRadius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                        continue;
                    }
                    Vector pull = center.toVector().subtract(living.getLocation().toVector()).normalize().multiply(0.2);
                    living.setVelocity(living.getVelocity().add(pull));
                    if (ticks % 15 == 0) {
                        living.damage(damage, player);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability3(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(1.8).setY(0.2));

        double damage = slashDamage * rarity().statMultiplier();
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= slashTicks) {
                    cancel();
                    return;
                }
                Fx.trail(player.getLocation().add(0, 1, 0), Particle.REVERSE_PORTAL, 6, 0.25, 0.02);

                for (Entity nearby : player.getNearbyEntities(slashHitRadius, slashHitRadius, slashHitRadius)) {
                    if (!(nearby instanceof LivingEntity entity) || !alreadyHit.add(entity.getUniqueId())) {
                        continue;
                    }
                    entity.damage(damage, player);
                    Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(5));
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double explosionDamage = blackHoleExplosionDamage * rarity().statMultiplier();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (ticks < blackHolePullDurationTicks) {
                    Fx.ring(center, Particle.PORTAL, 2.0, 12);
                    for (Entity entity : world.getNearbyEntities(center, blackHolePullRadius, blackHolePullRadius, blackHolePullRadius)) {
                        if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                            continue;
                        }
                        Vector pull = center.toVector().subtract(living.getLocation().toVector()).normalize().multiply(0.25);
                        living.setVelocity(living.getVelocity().add(pull));
                    }
                    ticks++;
                    return;
                }

                Fx.burst(center, Particle.EXPLOSION_EMITTER, 1, 0.1);
                Fx.burst(center, Particle.PORTAL, 60, blackHoleExplosionRadius * 0.5);
                Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);

                for (Entity entity : world.getNearbyEntities(center, blackHoleExplosionRadius, blackHoleExplosionRadius, blackHoleExplosionRadius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                        continue;
                    }
                    living.damage(explosionDamage, player);
                    Vector knockback = living.getLocation().toVector().subtract(center.toVector()).normalize().setY(0.6);
                    living.setVelocity(living.getVelocity().add(knockback.multiply(1.5)));
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                }
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
```

- [ ] **Step 2: Append the config block**

```yaml
  void_blade:
    melee-damage-bonus: 3.0
    ability1-cooldown-seconds: 7.0
    ability2-cooldown-seconds: 9.0
    ability3-cooldown-seconds: 6.0
    ultimate-cooldown-seconds: 55.0
    armor-bypass-chance: 0.1
    phase-range: 6.0
    rift-pull-radius: 5.0
    rift-damage: 3.0
    rift-duration-ticks: 30
    slash-damage: 6.0
    slash-ticks: 6
    slash-hit-radius: 1.6
    black-hole-pull-radius: 7.0
    black-hole-pull-duration-ticks: 40
    black-hole-explosion-damage: 14.0
    black-hole-explosion-radius: 5.0
```

- [ ] **Step 3: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.TidalTrident;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.TidalTrident;
import dev.rbm72.weaponsplugin.items.weapons.VoidBlade;
```

Change:
```java
        weaponRegistry.register(new TidalTrident(this));
```
to:
```java
        weaponRegistry.register(new TidalTrident(this));
        weaponRegistry.register(new VoidBlade(this));
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual verification**

`/giveweapon void_blade` — right-click teleports you through a wall; shift+right-click pulls nearby mobs toward a rift point; off-hand right-click phase-dashes through enemies; off-hand+shift pulls enemies into a point then explodes. Melee hits occasionally show a portal-particle "bypass" flash.

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/VoidBlade.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Void Blade weapon"
```

---

## Task 10: Solar Greatsword

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/SolarGreatsword.java`
- Modify: `plugin/src/main/resources/config.yml` (append `solar_greatsword` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Interfaces:** Extends `Weapon` (Task 1) only.

- [ ] **Step 1: Create `SolarGreatsword.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/** Holy greatsword: sunbeam, solar explosion, radiant cleave, and a light-pillar ultimate. Strong vs. undead. */
public final class SolarGreatsword extends Weapon {

    private final double undeadDamageBonus;
    private final double beamDamage;
    private final double beamRange;
    private final double explosionDamage;
    private final double explosionRadius;
    private final double cleaveDamage;
    private final double cleaveRange;
    private final double pillarDamagePerTick;
    private final int pillarDurationTicks;
    private final double pillarRadius;

    public SolarGreatsword(WeaponsPlugin plugin) {
        super(plugin);
        this.undeadDamageBonus = configDouble("undead-damage-bonus", 0.5);
        this.beamDamage = configDouble("beam-damage", 8.0);
        this.beamRange = configDouble("beam-range", 14.0);
        this.explosionDamage = configDouble("explosion-damage", 7.0);
        this.explosionRadius = configDouble("explosion-radius", 4.0);
        this.cleaveDamage = configDouble("cleave-damage", 6.0);
        this.cleaveRange = configDouble("cleave-range", 3.0);
        this.pillarDamagePerTick = configDouble("pillar-damage-per-tick", 2.5);
        this.pillarDurationTicks = configInt("pillar-duration-ticks", 60);
        this.pillarRadius = configDouble("pillar-radius", 2.0);
    }

    @Override
    public String id() {
        return "solar_greatsword";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Solar Greatsword";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 6.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 9.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 5.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: fire a beam of", NamedTextColor.GRAY),
                Component.text("sunlight forward.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: detonate a solar", NamedTextColor.GRAY),
                Component.text("explosion around you.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: radiant cleave, blinding", NamedTextColor.GRAY),
                Component.text("enemies in front of you.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: summon a pillar of", NamedTextColor.GRAY),
                Component.text("light. Extra effective vs. undead.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_BEACON_ACTIVATE;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_ATTACK_STRONG;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_BEACON_POWER_SELECT;
    }

    private boolean isUndead(LivingEntity entity) {
        return entity instanceof Zombie || entity instanceof AbstractSkeleton;
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (isUndead(victim)) {
            event.setDamage(event.getDamage() * (1 + undeadDamageBonus));
            Fx.burst(victim.getLocation().add(0, 1, 0), Particle.END_ROD, 8, 0.3);
        }
    }

    @Override
    public void ability1(Player player) {
        double damage = beamDamage * rarity().statMultiplier();
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), beamRange,
                entity -> entity instanceof LivingEntity && !entity.equals(player));
        Location end = result != null ? result.getHitPosition().toLocation(player.getWorld())
                : eye.clone().add(eye.getDirection().multiply(beamRange));

        Fx.line(eye, end, Particle.END_ROD, 20);
        Fx.line(eye, end, Particle.FLAME, 10);

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            double bonus = isUndead(target) ? 1 + undeadDamageBonus : 1.0;
            target.damage(damage * bonus, player);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = explosionDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.burst(center.clone().add(0, 1, 0), Particle.FLAME, 40, explosionRadius * 0.4);
        Fx.burst(center.clone().add(0, 1, 0), Particle.END_ROD, 25, explosionRadius * 0.4);

        for (Entity entity : world.getNearbyEntities(center, explosionRadius, explosionRadius, explosionRadius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            double bonus = isUndead(living) ? 1 + undeadDamageBonus : 1.0;
            living.damage(damage * bonus, player);
            living.setFireTicks(60);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability3(Player player) {
        double damage = cleaveDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Fx.trail(origin.clone().add(0, 1, 0), Particle.END_ROD, 20, 0.6, 0.05);

        for (Entity entity : world.getNearbyEntities(origin, cleaveRange, cleaveRange, cleaveRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(origin.toVector()).normalize();
            if (direction.dot(toEntity) < 0.4) {
                continue;
            }
            double bonus = isUndead(living) ? 1 + undeadDamageBonus : 1.0;
            living.damage(damage * bonus, player);
            living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ultimate(Player player) {
        double damagePerTick = pillarDamagePerTick * rarity().statMultiplier();
        Location target = player.getTargetBlockExact(20) != null
                ? player.getTargetBlockExact(20).getLocation().add(0.5, 1, 0.5)
                : player.getLocation();
        World world = target.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(target, Sound.ENTITY_EVOKER_CAST_SPELL, 1.2f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= pillarDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                Fx.trail(target.clone().add(0, ticks % 10, 0), Particle.END_ROD, 4, 0.3, 0.02);

                if (ticks % 10 == 0) {
                    for (Entity entity : world.getNearbyEntities(target, pillarRadius, 5, pillarRadius)) {
                        if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                            continue;
                        }
                        double bonus = isUndead(living) ? 1 + undeadDamageBonus : 1.0;
                        living.damage(damagePerTick * bonus, player);
                        if (isUndead(living)) {
                            living.setFireTicks(40);
                        }
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
```

- [ ] **Step 2: Append the config block**

```yaml
  solar_greatsword:
    melee-damage-bonus: 3.5
    ability1-cooldown-seconds: 6.0
    ability2-cooldown-seconds: 9.0
    ability3-cooldown-seconds: 5.0
    ultimate-cooldown-seconds: 55.0
    undead-damage-bonus: 0.5
    beam-damage: 8.0
    beam-range: 14.0
    explosion-damage: 7.0
    explosion-radius: 4.0
    cleave-damage: 6.0
    cleave-range: 3.0
    pillar-damage-per-tick: 2.5
    pillar-duration-ticks: 60
    pillar-radius: 2.0
```

- [ ] **Step 3: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.VoidBlade;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.VoidBlade;
import dev.rbm72.weaponsplugin.items.weapons.SolarGreatsword;
```

Change:
```java
        weaponRegistry.register(new VoidBlade(this));
```
to:
```java
        weaponRegistry.register(new VoidBlade(this));
        weaponRegistry.register(new SolarGreatsword(this));
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual verification**

`/giveweapon solar_greatsword` — right-click fires a beam; shift+right-click explodes around you and sets enemies on fire; off-hand right-click cleaves and blinds; off-hand+shift drops a damaging light pillar at your crosshair. Hit a zombie/skeleton to confirm the undead damage bonus (compare its health drop to a non-undead mob).

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/SolarGreatsword.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Solar Greatsword weapon"
```

---

## Task 11: Lunar Blade

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/LunarBlade.java`
- Modify: `plugin/src/main/resources/config.yml` (append `lunar_blade` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Interfaces:** Extends `Weapon` (Task 1) only. Uses the tagged-projectile pattern (`Weapon.idKey`, `onProjectileHit`) already established by `ArcaneStaff`/`TidalTrident`.

- [ ] **Step 1: Create `LunarBlade.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/** Moon-themed sword: crescent projectile, moon dash, gravity field, and an Eclipse self-buff ultimate. */
public final class LunarBlade extends Weapon {

    private final double nightDamageBonus;
    private final double crescentDamage;
    private final double crescentSpeed;
    private final double dashDamage;
    private final double dashDistance;
    private final double gravityDamage;
    private final double gravityRadius;
    private final int gravityDurationTicks;
    private final int eclipseDurationTicks;
    private final double eclipseDamageBonus;

    public LunarBlade(WeaponsPlugin plugin) {
        super(plugin);
        this.nightDamageBonus = configDouble("night-damage-bonus", 0.3);
        this.crescentDamage = configDouble("crescent-damage", 5.0);
        this.crescentSpeed = configDouble("crescent-speed", 1.8);
        this.dashDamage = configDouble("dash-damage", 5.0);
        this.dashDistance = configDouble("dash-distance", 5.0);
        this.gravityDamage = configDouble("gravity-damage", 4.0);
        this.gravityRadius = configDouble("gravity-radius", 4.0);
        this.gravityDurationTicks = configInt("gravity-duration-ticks", 30);
        this.eclipseDurationTicks = configInt("eclipse-duration-ticks", 160);
        this.eclipseDamageBonus = configDouble("eclipse-damage-bonus", 0.4);
    }

    private long eclipseActiveUntilMs = 0;

    @Override
    public String id() {
        return "lunar_blade";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Lunar Blade";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 6.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 7.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 50.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: launch a crescent", NamedTextColor.GRAY),
                Component.text("blade projectile.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: dash forward,", NamedTextColor.GRAY),
                Component.text("damaging enemies you pass through.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: pull nearby enemies", NamedTextColor.GRAY),
                Component.text("downward with a gravity field.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: enter an Eclipse,", NamedTextColor.GRAY),
                Component.text("boosting your damage and speed.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_PHANTOM_FLAP;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_ATTACK_SWEEP;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_AMETHYST_BLOCK_CHIME;
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        double multiplier = 1.0;
        if (!attacker.getWorld().isDayTime()) {
            multiplier += nightDamageBonus;
        }
        if (System.currentTimeMillis() < eclipseActiveUntilMs) {
            multiplier += eclipseDamageBonus;
        }
        if (multiplier > 1.0) {
            event.setDamage(event.getDamage() * multiplier);
            Fx.point(victim.getLocation().add(0, 1.5, 0), Particle.END_ROD, 3);
        }
    }

    @Override
    public void ability1(Player player) {
        Snowball projectile = player.launchProjectile(Snowball.class,
                player.getLocation().getDirection().multiply(crescentSpeed));
        projectile.setGravity(false);
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!projectile.isValid()) {
                    cancel();
                    return;
                }
                Fx.point(projectile.getLocation(), Particle.END_ROD, 2);
                Fx.point(projectile.getLocation(), Particle.SOUL_FIRE_FLAME, 1);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        double damage = crescentDamage * rarity().statMultiplier();
        Fx.burst(loc, Particle.END_ROD, 15, 0.4);
        Fx.sound(loc, hitSound(), 0.8f, 1.4f);

        if (event.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, shooter);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = dashDamage * rarity().statMultiplier();
        Location start = player.getLocation();
        Vector direction = start.getDirection().normalize();
        Location end = start.clone().add(direction.clone().multiply(dashDistance));
        World world = start.getWorld();
        if (world == null) {
            return;
        }

        Fx.line(start.clone().add(0, 1, 0), end.clone().add(0, 1, 0), Particle.END_ROD, 15);
        for (Entity entity : world.getNearbyEntities(start, dashDistance, 2, dashDistance)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(start.toVector());
            if (direction.dot(toEntity.clone().normalize()) < 0.6 || toEntity.length() > dashDistance) {
                continue;
            }
            living.damage(damage, player);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }

        Location safeEnd = end.getBlock().getType().isSolid() ? start : end;
        player.teleport(safeEnd.setDirection(direction));
        Fx.burst(safeEnd, Particle.END_ROD, 20, 0.3);
    }

    @Override
    public void ability3(Player player) {
        double damage = gravityDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= gravityDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                Fx.ring(center, Particle.REVERSE_PORTAL, gravityRadius, 16);

                for (Entity entity : world.getNearbyEntities(center, gravityRadius, gravityRadius, gravityRadius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                        continue;
                    }
                    living.setVelocity(living.getVelocity().setY(-0.3));
                    if (ticks % 10 == 0) {
                        living.damage(damage, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        eclipseActiveUntilMs = System.currentTimeMillis() + (eclipseDurationTicks * 50L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, eclipseDurationTicks, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, eclipseDurationTicks, 1));
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= eclipseDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (ticks % 5 == 0) {
                    Fx.ring(player.getLocation(), Particle.SOUL_FIRE_FLAME, 1.2, 10);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
```

- [ ] **Step 2: Append the config block**

```yaml
  lunar_blade:
    melee-damage-bonus: 2.5
    ability1-cooldown-seconds: 5.0
    ability2-cooldown-seconds: 6.0
    ability3-cooldown-seconds: 7.0
    ultimate-cooldown-seconds: 50.0
    night-damage-bonus: 0.3
    crescent-damage: 5.0
    crescent-speed: 1.8
    dash-damage: 5.0
    dash-distance: 5.0
    gravity-damage: 4.0
    gravity-radius: 4.0
    gravity-duration-ticks: 30
    eclipse-duration-ticks: 160
    eclipse-damage-bonus: 0.4
```

- [ ] **Step 3: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.SolarGreatsword;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.SolarGreatsword;
import dev.rbm72.weaponsplugin.items.weapons.LunarBlade;
```

Change:
```java
        weaponRegistry.register(new SolarGreatsword(this));
```
to:
```java
        weaponRegistry.register(new SolarGreatsword(this));
        weaponRegistry.register(new LunarBlade(this));
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual verification**

`/giveweapon lunar_blade` — right-click fires a crescent projectile; shift+right-click dashes through enemies; off-hand right-click pulls nearby mobs down; off-hand+shift triggers Eclipse (visible Strength/Speed icons + soul-fire ring). Hit something at night vs. during the day to confirm the damage difference; hit something during an active Eclipse to confirm the stacked bonus.

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/LunarBlade.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Lunar Blade weapon"
```

---

## Task 12: Plague Scythe

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/PlagueScythe.java`
- Modify: `plugin/src/main/resources/config.yml` (append `plague_scythe` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Interfaces:** Extends `Weapon` (Task 1) only. Uses the tagged-projectile pattern for its exploding spores.

- [ ] **Step 1: Create `PlagueScythe.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Poison-themed scythe (reskinned hoe): poison clouds, direct infection, exploding spores, and a chaining plague ultimate. */
public final class PlagueScythe extends Weapon {

    private final double cloudDamagePerTick;
    private final double cloudRadius;
    private final int cloudDurationTicks;
    private final double cloudRangeAhead;
    private final double infectRange;
    private final int infectPoisonTicks;
    private final double sporeSpeed;
    private final double sporeDamage;
    private final double sporeCloudRadius;
    private final double plagueInitialRadius;
    private final double plagueChainRadius;
    private final int plagueDurationTicks;
    private final double plagueDamagePerTick;
    private final double deathCloudRadius;
    private final int deathCloudDurationTicks;
    private final double deathCloudDamagePerTick;

    public PlagueScythe(WeaponsPlugin plugin) {
        super(plugin);
        this.cloudDamagePerTick = configDouble("cloud-damage-per-tick", 1.5);
        this.cloudRadius = configDouble("cloud-radius", 3.0);
        this.cloudDurationTicks = configInt("cloud-duration-ticks", 80);
        this.cloudRangeAhead = configDouble("cloud-range-ahead", 5.0);
        this.infectRange = configDouble("infect-range", 3.5);
        this.infectPoisonTicks = configInt("infect-poison-ticks", 100);
        this.sporeSpeed = configDouble("spore-speed", 1.6);
        this.sporeDamage = configDouble("spore-damage", 4.0);
        this.sporeCloudRadius = configDouble("spore-cloud-radius", 2.5);
        this.plagueInitialRadius = configDouble("plague-initial-radius", 5.0);
        this.plagueChainRadius = configDouble("plague-chain-radius", 4.0);
        this.plagueDurationTicks = configInt("plague-duration-ticks", 100);
        this.plagueDamagePerTick = configDouble("plague-damage-per-tick", 1.5);
        this.deathCloudRadius = configDouble("death-cloud-radius", 3.0);
        this.deathCloudDurationTicks = configInt("death-cloud-duration-ticks", 60);
        this.deathCloudDamagePerTick = configDouble("death-cloud-damage-per-tick", 1.0);
    }

    @Override
    public String id() {
        return "plague_scythe";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_HOE;
    }

    @Override
    public String displayNameText() {
        return "Plague Scythe";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 7.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 5.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 6.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 50.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: create a poison cloud", NamedTextColor.GRAY),
                Component.text("ahead of you.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: infect the", NamedTextColor.GRAY),
                Component.text("nearest enemy in front of you.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: throw an exploding", NamedTextColor.GRAY),
                Component.text("spore pod.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: infect every nearby", NamedTextColor.GRAY),
                Component.text("enemy; the plague jumps between", NamedTextColor.GRAY),
                Component.text("infected enemies.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_WITCH_THROW;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_WITCH_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_BREWING_STAND_BREW;
    }

    @Override
    public void onKill(Player attacker, LivingEntity victim) {
        Location loc = victim.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= deathCloudDurationTicks) {
                    cancel();
                    return;
                }
                Fx.burst(loc, Particle.ITEM_SLIME, 6, deathCloudRadius * 0.4);
                if (ticks % 10 == 0) {
                    for (Entity entity : world.getNearbyEntities(loc, deathCloudRadius, deathCloudRadius, deathCloudRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(attacker)) {
                            living.damage(deathCloudDamagePerTick, attacker);
                            living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability1(Player player) {
        double damage = cloudDamagePerTick * rarity().statMultiplier();
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(cloudRangeAhead));
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= cloudDurationTicks) {
                    cancel();
                    return;
                }
                Fx.burst(center, Particle.ITEM_SLIME, 10, cloudRadius * 0.5);
                if (ticks % 10 == 0) {
                    for (Entity entity : world.getNearbyEntities(center, cloudRadius, cloudRadius, cloudRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(damage, player);
                            living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability2(Player player) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        LivingEntity target = null;
        double closest = Double.MAX_VALUE;
        for (Entity entity : world.getNearbyEntities(origin, infectRange, infectRange, infectRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            double dot = origin.getDirection().normalize().dot(living.getLocation().toVector().subtract(origin.toVector()).normalize());
            if (dot < 0.5) {
                continue;
            }
            double distanceSquared = living.getLocation().distanceSquared(origin);
            if (distanceSquared < closest) {
                closest = distanceSquared;
                target = living;
            }
        }
        if (target == null) {
            return;
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, infectPoisonTicks, 2));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, infectPoisonTicks, 1));
        Fx.burst(target.getLocation().add(0, 1, 0), Particle.ITEM_SLIME, 20, 0.4);
        Fx.sound(target.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 1.2f);
    }

    @Override
    public void ability3(Player player) {
        Snowball projectile = player.launchProjectile(Snowball.class,
                player.getLocation().getDirection().multiply(sporeSpeed));
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!projectile.isValid()) {
                    cancel();
                    return;
                }
                Fx.point(projectile.getLocation(), Particle.ITEM_SLIME, 1);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        double damage = sporeDamage * rarity().statMultiplier();
        Fx.burst(loc, Particle.ITEM_SLIME, 25, sporeCloudRadius * 0.5);
        Fx.sound(loc, hitSound(), 1.0f, 1.0f);

        for (Entity entity : world.getNearbyEntities(loc, sporeCloudRadius, sporeCloudRadius, sporeCloudRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(shooter)) {
                living.damage(damage, shooter);
                living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ultimate(Player player) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        Fx.sound(player, Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 0.8f);
        Set<UUID> infected = new HashSet<>();

        for (Entity entity : world.getNearbyEntities(origin, plagueInitialRadius, plagueInitialRadius, plagueInitialRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                infected.add(living.getUniqueId());
                Fx.burst(living.getLocation().add(0, 1, 0), Particle.ITEM_SLIME, 15, 0.3);
            }
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= plagueDurationTicks || infected.isEmpty()) {
                    cancel();
                    return;
                }
                if (ticks % 10 == 0) {
                    Set<UUID> newlyInfected = new HashSet<>();
                    for (Entity entity : world.getEntities()) {
                        if (!(entity instanceof LivingEntity living) || !infected.contains(living.getUniqueId())) {
                            continue;
                        }
                        living.damage(plagueDamagePerTick, player);
                        living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0));
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));

                        for (Entity nearby : world.getNearbyEntities(living.getLocation(), plagueChainRadius, plagueChainRadius, plagueChainRadius)) {
                            if (nearby instanceof LivingEntity candidate && !candidate.equals(player)
                                    && !infected.contains(candidate.getUniqueId())) {
                                newlyInfected.add(candidate.getUniqueId());
                                Fx.line(living.getLocation().add(0, 1, 0), candidate.getLocation().add(0, 1, 0), Particle.ITEM_SLIME, 6);
                            }
                        }
                    }
                    infected.addAll(newlyInfected);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
```

- [ ] **Step 2: Append the config block**

```yaml
  plague_scythe:
    melee-damage-bonus: 2.0
    ability1-cooldown-seconds: 7.0
    ability2-cooldown-seconds: 5.0
    ability3-cooldown-seconds: 6.0
    ultimate-cooldown-seconds: 50.0
    cloud-damage-per-tick: 1.5
    cloud-radius: 3.0
    cloud-duration-ticks: 80
    cloud-range-ahead: 5.0
    infect-range: 3.5
    infect-poison-ticks: 100
    spore-speed: 1.6
    spore-damage: 4.0
    spore-cloud-radius: 2.5
    plague-initial-radius: 5.0
    plague-chain-radius: 4.0
    plague-duration-ticks: 100
    plague-damage-per-tick: 1.5
    death-cloud-radius: 3.0
    death-cloud-duration-ticks: 60
    death-cloud-damage-per-tick: 1.0
```

- [ ] **Step 3: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.LunarBlade;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.LunarBlade;
import dev.rbm72.weaponsplugin.items.weapons.PlagueScythe;
```

Change:
```java
        weaponRegistry.register(new LunarBlade(this));
```
to:
```java
        weaponRegistry.register(new LunarBlade(this));
        weaponRegistry.register(new PlagueScythe(this));
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual verification**

`/giveweapon plague_scythe` — right-click drops a lingering poison cloud ahead; shift+right-click infects the nearest enemy in front; off-hand right-click throws an exploding spore pod; off-hand+shift infects everything nearby and the plague visibly chains (green line particles) to new targets over time. Killing an infected mob should leave a small poison cloud behind.

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/PlagueScythe.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Plague Scythe weapon"
```

---

## Task 13: Dragon Fang

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/DragonFang.java`
- Modify: `plugin/src/main/resources/config.yml` (append `dragon_fang` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Interfaces:** Extends `Weapon` (Task 1) only.

- [ ] **Step 1: Create `DragonFang.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/** Dragon-hunter axe: roar knockback, fire breath cone, wing leap, and a brief Dragon Form ultimate. */
public final class DragonFang extends Weapon {

    private final double roarRadius;
    private final double roarKnockback;
    private final double breathDamage;
    private final double breathRange;
    private final double leapPower;
    private final int leapSlowFallTicks;
    private final int dragonFormDurationTicks;
    private final double dragonFormDamageBonus;

    public DragonFang(WeaponsPlugin plugin) {
        super(plugin);
        this.roarRadius = configDouble("roar-radius", 4.5);
        this.roarKnockback = configDouble("roar-knockback", 1.5);
        this.breathDamage = configDouble("breath-damage", 5.0);
        this.breathRange = configDouble("breath-range", 6.0);
        this.leapPower = configDouble("leap-power", 1.4);
        this.leapSlowFallTicks = configInt("leap-slow-fall-ticks", 60);
        this.dragonFormDurationTicks = configInt("dragon-form-duration-ticks", 180);
        this.dragonFormDamageBonus = configDouble("dragon-form-damage-bonus", 0.35);
    }

    private long dragonFormActiveUntilMs = 0;

    @Override
    public String id() {
        return "dragon_fang";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_AXE;
    }

    @Override
    public String displayNameText() {
        return "Dragon Fang";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 8.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 6.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 7.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: unleash a roar,", NamedTextColor.GRAY),
                Component.text("knocking back nearby enemies.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: breathe fire in", NamedTextColor.GRAY),
                Component.text("a cone ahead of you.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: leap forward with slow", NamedTextColor.GRAY),
                Component.text("falling.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: briefly take on", NamedTextColor.GRAY),
                Component.text("Dragon Form, boosting damage and", NamedTextColor.GRAY),
                Component.text("resetting your leap.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ENDER_DRAGON_GROWL;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_BURN;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_ENDER_DRAGON_FLAP;
    }

    @Override
    public void onTick(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30, 0, true, false));
        if (System.currentTimeMillis() < dragonFormActiveUntilMs && player.getVelocity().lengthSquared() > 0.01) {
            Fx.trail(player.getLocation(), Particle.FLAME, 3, 0.2, 0.01);
        }
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (System.currentTimeMillis() < dragonFormActiveUntilMs) {
            event.setDamage(event.getDamage() * (1 + dragonFormDamageBonus));
        }
    }

    @Override
    public void ability1(Player player) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Fx.ring(origin, Particle.CLOUD, roarRadius, 24);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.9f);

        for (Entity entity : world.getNearbyEntities(origin, roarRadius, roarRadius, roarRadius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector knockback = living.getLocation().toVector().subtract(origin.toVector()).normalize()
                    .multiply(roarKnockback).setY(0.4);
            living.setVelocity(living.getVelocity().add(knockback));
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = breathDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Fx.trail(origin.clone().add(direction.clone().multiply(2)).add(0, 1, 0), Particle.FLAME, 30, 0.6, 0.05);

        for (Entity entity : world.getNearbyEntities(origin, breathRange, breathRange, breathRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(origin.toVector()).normalize();
            if (direction.dot(toEntity) < 0.5) {
                continue;
            }
            living.damage(damage, player);
            living.setFireTicks(80);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability3(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(leapPower).setY(0.8));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, leapSlowFallTicks, 0));
        Fx.burst(player.getLocation(), Particle.CLOUD, 20, 0.4);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.1f);
    }

    @Override
    public void ultimate(Player player) {
        dragonFormActiveUntilMs = System.currentTimeMillis() + (dragonFormDurationTicks * 50L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, dragonFormDurationTicks, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dragonFormDurationTicks, 0));
        Fx.burst(player.getLocation(), Particle.FLAME, 40, 0.6);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.6f);
    }
}
```

- [ ] **Step 2: Append the config block**

```yaml
  dragon_fang:
    melee-damage-bonus: 3.0
    ability1-cooldown-seconds: 8.0
    ability2-cooldown-seconds: 6.0
    ability3-cooldown-seconds: 7.0
    ultimate-cooldown-seconds: 55.0
    roar-radius: 4.5
    roar-knockback: 1.5
    breath-damage: 5.0
    breath-range: 6.0
    leap-power: 1.4
    leap-slow-fall-ticks: 60
    dragon-form-duration-ticks: 180
    dragon-form-damage-bonus: 0.35
```

- [ ] **Step 3: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.PlagueScythe;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.PlagueScythe;
import dev.rbm72.weaponsplugin.items.weapons.DragonFang;
```

Change:
```java
        weaponRegistry.register(new PlagueScythe(this));
```
to:
```java
        weaponRegistry.register(new PlagueScythe(this));
        weaponRegistry.register(new DragonFang(this));
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual verification**

`/giveweapon dragon_fang` — right-click knocks back everything nearby; shift+right-click breathes a damaging fire cone; off-hand right-click leaps you forward with slow falling; off-hand+shift enters Dragon Form (Strength icon, fire trail while moving). Standing in lava/fire should no longer hurt you while holding it (fire resistance passive).

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/DragonFang.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Dragon Fang weapon"
```

---

## Task 14: Celestial Bow

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/CelestialBow.java`
- Modify: `plugin/src/main/resources/config.yml` (append `celestial_bow` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Interfaces:** Extends `Weapon` (Task 1) only. Every fired shot (ability2 and ability3) is a tagged `Snowball` (the same pattern `ArcaneStaff`/`LunarBlade`/`PlagueScythe` use) — vanilla `Arrow`s are deliberately not used, since a real arrow deals its own vanilla damage on hit in addition to whatever `onProjectileHit` applies, causing double damage. Ability1's "rain" strikes are direct location-based impacts (no projectile entities), matching the "meteor" pattern already used by `SolarGreatsword`'s ultimate.

- [ ] **Step 1: Create `CelestialBow.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Cosmic bow: raining star strikes, a homing shot, a comet shot, and a meteor shower ultimate. Every 5th shot explodes bigger. */
public final class CelestialBow extends Weapon {

    private final int rainStrikeCount;
    private final double rainDamagePerStrike;
    private final double rainRadius;
    private final double homingSpeed;
    private final double homingDamage;
    private final double cometSpeed;
    private final double cometDamage;
    private final double cometExplosionRadius;
    private final double bonusExplosionMultiplier;
    private final int meteorStrikeCount;
    private final double meteorDamagePerStrike;
    private final double meteorRadius;

    public CelestialBow(WeaponsPlugin plugin) {
        super(plugin);
        this.rainStrikeCount = configInt("rain-strike-count", 5);
        this.rainDamagePerStrike = configDouble("rain-damage-per-strike", 3.0);
        this.rainRadius = configDouble("rain-radius", 3.5);
        this.homingSpeed = configDouble("homing-speed", 1.6);
        this.homingDamage = configDouble("homing-damage", 6.0);
        this.cometSpeed = configDouble("comet-speed", 1.4);
        this.cometDamage = configDouble("comet-damage", 9.0);
        this.cometExplosionRadius = configDouble("comet-explosion-radius", 2.5);
        this.bonusExplosionMultiplier = configDouble("bonus-explosion-multiplier", 2.0);
        this.meteorStrikeCount = configInt("meteor-strike-count", 8);
        this.meteorDamagePerStrike = configDouble("meteor-damage-per-strike", 5.0);
        this.meteorRadius = configDouble("meteor-radius", 6.0);
    }

    private final Map<UUID, Integer> shotCounts = new HashMap<>();

    private NamespacedKey bonusKey() {
        return new NamespacedKey(plugin, "celestial_bow_bonus");
    }

    /** Returns true (and resets the streak) if this shot is the 5th and should explode bigger. */
    private boolean nextShotIsBonus(Player player) {
        int count = shotCounts.merge(player.getUniqueId(), 1, Integer::sum);
        if (count >= rainStrikeCount) {
            shotCounts.put(player.getUniqueId(), 0);
            return true;
        }
        return false;
    }

    @Override
    public String id() {
        return "celestial_bow";
    }

    @Override
    public Material material() {
        return Material.BOW;
    }

    @Override
    public String displayNameText() {
        return "Celestial Bow";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 1.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 9.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 4.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 7.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: call down a rain of", NamedTextColor.GRAY),
                Component.text("stars onto your target.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: fire a homing", NamedTextColor.GRAY),
                Component.text("shot that curves toward enemies.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: fire a heavy comet shot.", NamedTextColor.GRAY),
                Component.text("Every 5th shot explodes bigger.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: call down a", NamedTextColor.GRAY),
                Component.text("meteor shower across the area.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ARROW_SHOOT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_EXPLODE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_AMETHYST_CLUSTER_BREAK;
    }

    private Location targetLocation(Player player) {
        var block = player.getTargetBlockExact(30);
        return block != null ? block.getLocation().add(0.5, 1, 0.5)
                : player.getLocation().add(player.getLocation().getDirection().multiply(15));
    }

    @Override
    public void ability1(Player player) {
        double damage = rainDamagePerStrike * rarity().statMultiplier();
        Location center = targetLocation(player);
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        for (int i = 0; i < rainStrikeCount; i++) {
            int delay = i * 4;
            double offsetX = (Math.random() - 0.5) * rainRadius * 2;
            double offsetZ = (Math.random() - 0.5) * rainRadius * 2;
            boolean bonus = nextShotIsBonus(player);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location strike = center.clone().add(offsetX, 0, offsetZ);
                double strikeDamage = bonus ? damage * bonusExplosionMultiplier : damage;
                Fx.trail(strike.clone().add(0, 6, 0), Particle.END_ROD, 15, 0.1, 0.02);
                Fx.burst(strike, Particle.FIREWORK, bonus ? 30 : 15, 0.4);
                Fx.sound(strike, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);

                for (Entity entity : world.getNearbyEntities(strike, 1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player)) {
                        living.damage(strikeDamage, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
            }, delay);
        }
    }

    @Override
    public void ability2(Player player) {
        boolean bonus = nextShotIsBonus(player);
        Snowball projectile = player.launchProjectile(Snowball.class,
                player.getLocation().getDirection().multiply(homingSpeed));
        projectile.setGravity(false);
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        projectile.getPersistentDataContainer().set(bonusKey(), PersistentDataType.INTEGER, bonus ? 1 : 0);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!projectile.isValid() || ticks >= 60) {
                    cancel();
                    return;
                }
                LivingEntity nearest = null;
                double closest = 8.0 * 8.0;
                for (Entity entity : projectile.getNearbyEntities(8, 8, 8)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player)) {
                        double distanceSquared = living.getLocation().distanceSquared(projectile.getLocation());
                        if (distanceSquared < closest) {
                            closest = distanceSquared;
                            nearest = living;
                        }
                    }
                }
                if (nearest != null) {
                    Vector toTarget = nearest.getLocation().add(0, 1, 0).toVector()
                            .subtract(projectile.getLocation().toVector()).normalize();
                    Vector current = projectile.getVelocity();
                    projectile.setVelocity(current.multiply(0.85).add(toTarget.multiply(0.3 * homingSpeed)));
                }
                Fx.point(projectile.getLocation(), Particle.END_ROD, 2);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability3(Player player) {
        boolean bonus = nextShotIsBonus(player);
        Snowball projectile = player.launchProjectile(Snowball.class,
                player.getLocation().getDirection().multiply(cometSpeed));
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        projectile.getPersistentDataContainer().set(bonusKey(), PersistentDataType.INTEGER, bonus ? 2 : 0);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!projectile.isValid()) {
                    cancel();
                    return;
                }
                Fx.point(projectile.getLocation(), Particle.FLAME, 2);
                Fx.point(projectile.getLocation(), Particle.END_ROD, 1);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        int marker = event.getEntity().getPersistentDataContainer().getOrDefault(bonusKey(), PersistentDataType.INTEGER, 0);
        boolean isComet = marker == 2;
        boolean bonus = marker != 0;

        double damage = (isComet ? cometDamage : homingDamage) * rarity().statMultiplier();
        double radius = isComet ? cometExplosionRadius : 1.5;
        if (bonus) {
            damage *= bonusExplosionMultiplier;
            radius *= bonusExplosionMultiplier;
        }

        Fx.burst(loc, Particle.FIREWORK, bonus ? 35 : 18, radius * 0.4);
        Fx.sound(loc, hitSound(), bonus ? 1.3f : 0.9f, 1.0f);

        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !entity.equals(shooter)) {
                living.damage(damage, shooter);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ultimate(Player player) {
        double damage = meteorDamagePerStrike * rarity().statMultiplier();
        Location center = targetLocation(player);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.4f);

        for (int i = 0; i < meteorStrikeCount; i++) {
            int delay = i * 6;
            double offsetX = (Math.random() - 0.5) * meteorRadius * 2;
            double offsetZ = (Math.random() - 0.5) * meteorRadius * 2;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Location strike = center.clone().add(offsetX, 0, offsetZ);
                Fx.trail(strike.clone().add(0, 8, 0), Particle.FLAME, 20, 0.15, 0.03);
                Fx.burst(strike, Particle.EXPLOSION, 3, 0.3);
                Fx.sound(strike, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);

                for (Entity entity : world.getNearbyEntities(strike, 2.0, 2.0, 2.0)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player)) {
                        living.damage(damage, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
            }, delay);
        }
    }
}
```

- [ ] **Step 2: Append the config block**

```yaml
  celestial_bow:
    melee-damage-bonus: 1.0
    ability1-cooldown-seconds: 9.0
    ability2-cooldown-seconds: 4.0
    ability3-cooldown-seconds: 7.0
    ultimate-cooldown-seconds: 55.0
    rain-strike-count: 5
    rain-damage-per-strike: 3.0
    rain-radius: 3.5
    homing-speed: 1.6
    homing-damage: 6.0
    comet-speed: 1.4
    comet-damage: 9.0
    comet-explosion-radius: 2.5
    bonus-explosion-multiplier: 2.0
    meteor-strike-count: 8
    meteor-damage-per-strike: 5.0
    meteor-radius: 6.0
```

- [ ] **Step 3: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.DragonFang;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.DragonFang;
import dev.rbm72.weaponsplugin.items.weapons.CelestialBow;
```

Change:
```java
        weaponRegistry.register(new DragonFang(this));
```
to:
```java
        weaponRegistry.register(new DragonFang(this));
        weaponRegistry.register(new CelestialBow(this));
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual verification**

`/giveweapon celestial_bow` — right-click rains star strikes onto your crosshair target; shift+right-click fires a shot that visibly curves toward a nearby mob; off-hand right-click fires a comet that explodes on impact; off-hand+shift rains a wider meteor shower. Fire ability2/ability3 five times total and confirm the 5th explosion is visibly bigger.

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/CelestialBow.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Celestial Bow weapon"
```

---

## Task 15: Blood Reaper

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/BloodReaper.java`
- Modify: `plugin/src/main/resources/config.yml` (append `blood_reaper` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Interfaces:** Extends `Weapon` (Task 1) only.

- [ ] **Step 1: Create `BloodReaper.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/** Risk-vs-reward scythe (reskinned hoe): HP-cost burst, lifesteal slash, blood AoE, and a full-heal drain ultimate. */
public final class BloodReaper extends Weapon {

    private final double lowHpDamageBonusMax;
    private final double sacrificeHpCost;
    private final double sacrificeDamage;
    private final double sacrificeRadius;
    private final double lifestealDamage;
    private final double lifestealRange;
    private final double lifestealHealFraction;
    private final double explosionDamage;
    private final double explosionRadius;
    private final double explosionHealFraction;
    private final double drainRadius;
    private final int drainDurationTicks;
    private final double drainDamagePerTick;

    public BloodReaper(WeaponsPlugin plugin) {
        super(plugin);
        this.lowHpDamageBonusMax = configDouble("low-hp-damage-bonus-max", 0.5);
        this.sacrificeHpCost = configDouble("sacrifice-hp-cost", 3.0);
        this.sacrificeDamage = configDouble("sacrifice-damage", 9.0);
        this.sacrificeRadius = configDouble("sacrifice-radius", 4.0);
        this.lifestealDamage = configDouble("lifesteal-damage", 6.0);
        this.lifestealRange = configDouble("lifesteal-range", 3.0);
        this.lifestealHealFraction = configDouble("lifesteal-heal-fraction", 0.6);
        this.explosionDamage = configDouble("explosion-damage", 6.0);
        this.explosionRadius = configDouble("explosion-radius", 3.5);
        this.explosionHealFraction = configDouble("explosion-heal-fraction", 0.3);
        this.drainRadius = configDouble("drain-radius", 5.0);
        this.drainDurationTicks = configInt("drain-duration-ticks", 50);
        this.drainDamagePerTick = configDouble("drain-damage-per-tick", 1.0);
    }

    @Override
    public String id() {
        return "blood_reaper";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_HOE;
    }

    @Override
    public String displayNameText() {
        return "Blood Reaper";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 8.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 5.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 7.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: sacrifice your own", NamedTextColor.GRAY),
                Component.text("health for a damaging blood burst.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: lifesteal slash,", NamedTextColor.GRAY),
                Component.text("healing you for damage dealt.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: blood explosion around", NamedTextColor.GRAY),
                Component.text("you, damaging and healing you.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: drain nearby", NamedTextColor.GRAY),
                Component.text("enemies' health to heal yourself.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_HOSTILE_BIG_FALL;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_PLAYER_LEVELUP;
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        double missingFraction = 1 - (attacker.getHealth() / attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        double bonus = 1 + (missingFraction * lowHpDamageBonusMax);
        event.setDamage(event.getDamage() * bonus);
    }

    @Override
    public void ability1(Player player) {
        if (player.getHealth() <= sacrificeHpCost) {
            return;
        }
        player.damage(sacrificeHpCost);
        double damage = sacrificeDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.burst(center.clone().add(0, 1, 0), Particle.CRIMSON_SPORE, 40,
                sacrificeRadius * 0.4);
        Fx.sound(player, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);

        for (Entity entity : world.getNearbyEntities(center, sacrificeRadius, sacrificeRadius, sacrificeRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = lifestealDamage * rarity().statMultiplier();
        World world = player.getWorld();
        Location origin = player.getLocation();
        LivingEntity target = null;
        double closest = Double.MAX_VALUE;

        for (Entity entity : world.getNearbyEntities(origin, lifestealRange, lifestealRange, lifestealRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            double distanceSquared = living.getLocation().distanceSquared(origin);
            if (distanceSquared < closest) {
                closest = distanceSquared;
                target = living;
            }
        }
        if (target == null) {
            return;
        }

        target.damage(damage, player);
        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        double healAmount = damage * lifestealHealFraction;
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHealth, player.getHealth() + healAmount));
        Fx.burst(player.getLocation().add(0, 1, 0), Particle.CRIMSON_SPORE, 15, 0.3);
    }

    @Override
    public void ability3(Player player) {
        double damage = explosionDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.burst(center.clone().add(0, 1, 0), Particle.CRIMSON_SPORE, 35, explosionRadius * 0.4);

        double totalDamageDealt = 0;
        for (Entity entity : world.getNearbyEntities(center, explosionRadius, explosionRadius, explosionRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                totalDamageDealt += damage;
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }

        if (totalDamageDealt > 0) {
            double healAmount = totalDamageDealt * explosionHealFraction;
            double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.min(maxHealth, player.getHealth() + healAmount));
        }
    }

    @Override
    public void ultimate(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= drainDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (ticks % 5 == 0) {
                    double healed = 0;
                    for (Entity entity : world.getNearbyEntities(center, drainRadius, drainRadius, drainRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(drainDamagePerTick, player);
                            healed += drainDamagePerTick;
                            Fx.line(living.getLocation().add(0, 1, 0), player.getLocation().add(0, 1, 0), Particle.CRIMSON_SPORE, 8);
                        }
                    }
                    if (healed > 0) {
                        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                        player.setHealth(Math.min(maxHealth, player.getHealth() + healed));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
```

- [ ] **Step 2: Append the config block**

```yaml
  blood_reaper:
    melee-damage-bonus: 3.0
    ability1-cooldown-seconds: 8.0
    ability2-cooldown-seconds: 5.0
    ability3-cooldown-seconds: 7.0
    ultimate-cooldown-seconds: 55.0
    low-hp-damage-bonus-max: 0.5
    sacrifice-hp-cost: 3.0
    sacrifice-damage: 9.0
    sacrifice-radius: 4.0
    lifesteal-damage: 6.0
    lifesteal-range: 3.0
    lifesteal-heal-fraction: 0.6
    explosion-damage: 6.0
    explosion-radius: 3.5
    explosion-heal-fraction: 0.3
    drain-radius: 5.0
    drain-duration-ticks: 50
    drain-damage-per-tick: 1.0
```

- [ ] **Step 3: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.CelestialBow;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.CelestialBow;
import dev.rbm72.weaponsplugin.items.weapons.BloodReaper;
```

Change:
```java
        weaponRegistry.register(new CelestialBow(this));
```
to:
```java
        weaponRegistry.register(new CelestialBow(this));
        weaponRegistry.register(new BloodReaper(this));
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual verification**

`/giveweapon blood_reaper` — right-click costs you HP and damages nearby enemies; shift+right-click heals you on hit; off-hand right-click damages and heals you based on total damage dealt; off-hand+shift drains nearby enemies to heal you over a few seconds. Damage a mob at low HP vs. full HP and confirm the low-HP hit is stronger.

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/BloodReaper.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Blood Reaper weapon"
```

---

## Task 16: Chrono Blade

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/ChronoBlade.java`
- Modify: `plugin/src/main/resources/config.yml` (append `chrono_blade` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Note on the passive:** "cooldowns recover faster after perfect timing" would need a way to shrink an already-running `CooldownManager` cooldown, which no other weapon needs and which Task 2 doesn't expose. Like `ArcaneStaff`'s passive, this is implemented self-contained: the weapon locally predicts when each of its own slots' cooldowns will finish (recorded at cast time), and a melee hit landed within a short window after a predicted finish grants that hit bonus damage instead of an actual cooldown refund — same "reward good timing" flavor, no framework changes.

**Interfaces:** Extends `Weapon` (Task 1) only. Uses `onTick` (Task 1) to maintain a rolling position history for the rewind ability.

- [ ] **Step 1: Create `ChronoBlade.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Time-themed sword: an enemy-only slow field, an afterimage dash, a 5-second rewind, and a freeze-everyone-else ultimate. */
public final class ChronoBlade extends Weapon {

    private final long perfectTimingWindowMs;
    private final double perfectTimingDamageBonus;
    private final double slowFieldRadius;
    private final int slowFieldDurationTicks;
    private final int afterimageCount;
    private final double afterimageDistance;
    private final int afterimageLifetimeTicks;
    private final double rewindBufferSeconds;
    private final double freezeRadius;
    private final int freezeDurationTicks;

    public ChronoBlade(WeaponsPlugin plugin) {
        super(plugin);
        this.perfectTimingWindowMs = configInt("perfect-timing-window-ms", 1000);
        this.perfectTimingDamageBonus = configDouble("perfect-timing-damage-bonus", 0.3);
        this.slowFieldRadius = configDouble("slow-field-radius", 4.5);
        this.slowFieldDurationTicks = configInt("slow-field-duration-ticks", 80);
        this.afterimageCount = configInt("afterimage-count", 4);
        this.afterimageDistance = configDouble("afterimage-distance", 6.0);
        this.afterimageLifetimeTicks = configInt("afterimage-lifetime-ticks", 60);
        this.rewindBufferSeconds = configDouble("rewind-buffer-seconds", 5.0);
        this.freezeRadius = configDouble("freeze-radius", 6.0);
        this.freezeDurationTicks = configInt("freeze-duration-ticks", 70);
    }

    private final long[] predictedFinishMs = new long[4];
    private final Map<UUID, Deque<Location>> positionHistory = new HashMap<>();

    @Override
    public String id() {
        return "chrono_blade";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Chrono Blade";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 9.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 6.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 12.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 60.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: slow every enemy", NamedTextColor.GRAY),
                Component.text("nearby. You're unaffected.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: dash forward,", NamedTextColor.GRAY),
                Component.text("leaving fading afterimages behind.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: rewind to where you", NamedTextColor.GRAY),
                Component.text("were 5 seconds ago.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: freeze time for", NamedTextColor.GRAY),
                Component.text("everyone nearby except you.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_AMETHYST_BLOCK_CHIME;
    }

    @Override
    public Sound hitSound() {
        return Sound.BLOCK_CONDUIT_ACTIVATE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_BEACON_ACTIVATE;
    }

    @Override
    public void onTick(Player player) {
        Deque<Location> history = positionHistory.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        history.addLast(player.getLocation());
        int maxEntries = (int) Math.ceil((rewindBufferSeconds + 1) * 2);
        while (history.size() > maxEntries) {
            history.removeFirst();
        }
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < predictedFinishMs.length; i++) {
            if (predictedFinishMs[i] != 0 && now >= predictedFinishMs[i] && now - predictedFinishMs[i] <= perfectTimingWindowMs) {
                event.setDamage(event.getDamage() * (1 + perfectTimingDamageBonus));
                predictedFinishMs[i] = 0;
                Fx.point(victim.getLocation().add(0, 1.5, 0), Particle.END_ROD, 4);
                break;
            }
        }
    }

    @Override
    public void ability1(Player player) {
        predictedFinishMs[0] = System.currentTimeMillis() + Math.round(ability1CooldownSeconds() * 1000);
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.ring(center, Particle.END_ROD, slowFieldRadius, 20);

        for (Entity entity : world.getNearbyEntities(center, slowFieldRadius, slowFieldRadius, slowFieldRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowFieldDurationTicks, 2));
                living.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, slowFieldDurationTicks, 2));
                Fx.burst(living.getLocation().add(0, 1, 0), Particle.END_ROD, 10, 0.3);
            }
        }
    }

    @Override
    public void ability2(Player player) {
        predictedFinishMs[1] = System.currentTimeMillis() + Math.round(ability2CooldownSeconds() * 1000);
        Location start = player.getLocation();
        Vector direction = start.getDirection().normalize();
        World world = start.getWorld();
        if (world == null) {
            return;
        }

        for (int i = 1; i <= afterimageCount; i++) {
            Location imageLoc = start.clone().add(direction.clone().multiply(afterimageDistance * i / afterimageCount));
            ArmorStand image = world.spawn(imageLoc, ArmorStand.class, stand -> {
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setSmall(true);
            });
            Fx.burst(imageLoc.clone().add(0, 1, 0), Particle.END_ROD, 8, 0.2);
            plugin.getServer().getScheduler().runTaskLater(plugin, image::remove, afterimageLifetimeTicks);
        }

        Location end = start.clone().add(direction.clone().multiply(afterimageDistance));
        Location safeEnd = end.getBlock().getType().isSolid() ? start : end;
        player.teleport(safeEnd.setDirection(direction));
    }

    @Override
    public void ability3(Player player) {
        predictedFinishMs[2] = System.currentTimeMillis() + Math.round(ability3CooldownSeconds() * 1000);
        Deque<Location> history = positionHistory.get(player.getUniqueId());
        if (history == null || history.isEmpty()) {
            return;
        }
        Location rewindTarget = history.peekFirst();
        Fx.burst(player.getLocation(), Particle.REVERSE_PORTAL, 20, 0.4);
        player.teleport(rewindTarget);
        Fx.burst(rewindTarget, Particle.REVERSE_PORTAL, 20, 0.4);
        Fx.sound(player, Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.3f);
    }

    @Override
    public void ultimate(Player player) {
        predictedFinishMs[3] = System.currentTimeMillis() + Math.round(ultimateCooldownSeconds() * 1000);
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.6f);

        for (Entity entity : world.getNearbyEntities(center, freezeRadius, freezeRadius, freezeRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeDurationTicks, 6));
                living.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, freezeDurationTicks, 6));
                living.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, freezeDurationTicks, -10, false, false));
                Fx.burst(living.getLocation().add(0, 1, 0), Particle.END_ROD, 20, 0.4);
            }
        }
    }
}
```

- [ ] **Step 2: Append the config block**

```yaml
  chrono_blade:
    melee-damage-bonus: 3.5
    ability1-cooldown-seconds: 9.0
    ability2-cooldown-seconds: 6.0
    ability3-cooldown-seconds: 12.0
    ultimate-cooldown-seconds: 60.0
    perfect-timing-window-ms: 1000
    perfect-timing-damage-bonus: 0.3
    slow-field-radius: 4.5
    slow-field-duration-ticks: 80
    afterimage-count: 4
    afterimage-distance: 6.0
    afterimage-lifetime-ticks: 60
    rewind-buffer-seconds: 5.0
    freeze-radius: 6.0
    freeze-duration-ticks: 70
```

- [ ] **Step 3: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.BloodReaper;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.BloodReaper;
import dev.rbm72.weaponsplugin.items.weapons.ChronoBlade;
```

Change:
```java
        weaponRegistry.register(new BloodReaper(this));
```
to:
```java
        weaponRegistry.register(new BloodReaper(this));
        weaponRegistry.register(new ChronoBlade(this));
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual verification**

`/giveweapon chrono_blade` — right-click slows nearby mobs without affecting you; shift+right-click dashes and leaves fading marker afterimages along the path; off-hand right-click rewinds you to where you were ~5s ago (walk somewhere, use it, confirm you snap back); off-hand+shift freezes nearby mobs (near-zero movement/mining/jump) while you move normally. Land a melee hit within ~1s of one of this weapon's cooldowns finishing and confirm a visible bonus-damage particle flash.

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/ChronoBlade.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Chrono Blade weapon"
```

---

## Task 17: Storm Chakrams

**Files:**
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/ability/CooldownManager.java` (add a real `reduce` method — this weapon's passive is the first one that genuinely needs it)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (expose the `CooldownManager` to weapons; register the weapon)
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/StormChakrams.java`
- Modify: `plugin/src/main/resources/config.yml` (append `storm_chakrams` block)

**Interfaces:**
- Produces: `CooldownManager.reduce(Player, String weaponId, Slot slot, double seconds)`, `WeaponsPlugin.cooldownManager()`.
- Consumes: `Weapon` (Task 1).

- [ ] **Step 1: Add `reduce` to `CooldownManager.java`**

Change:
```java
    private static final class Active {
        final long startMs;
        final long durationMs;
        final Slot slot;
        BukkitTask task;
        BossBar bossBar;

        Active(long startMs, long durationMs, Slot slot) {
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.slot = slot;
        }
    }
```
to:
```java
    private static final class Active {
        long startMs;
        final long durationMs;
        final Slot slot;
        BukkitTask task;
        BossBar bossBar;

        Active(long startMs, long durationMs, Slot slot) {
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.slot = slot;
        }
    }
```

Then add this method anywhere inside the `CooldownManager` class (e.g. right after `start`):
```java
    /** Shifts an already-running cooldown's start time earlier, finishing it sooner. No-op if not active. */
    public void reduce(Player player, String weaponId, Slot slot, double seconds) {
        Map<String, Active> map = active.get(player.getUniqueId());
        if (map == null) {
            return;
        }
        Active a = map.get(key(weaponId, slot));
        if (a != null) {
            a.startMs -= Math.round(seconds * 1000);
        }
    }
```

- [ ] **Step 2: Expose `CooldownManager` from `WeaponsPlugin.java`**

Change:
```java
    public WeaponRegistry weaponRegistry() {
        return weaponRegistry;
    }
```
to:
```java
    public WeaponRegistry weaponRegistry() {
        return weaponRegistry;
    }

    public CooldownManager cooldownManager() {
        return cooldownManager;
    }
```

- [ ] **Step 3: Create `StormChakrams.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Boomerang chakrams: a returning throw, a chain-lightning throw, orbiting blades, and a spiraling storm ultimate. */
public final class StormChakrams extends Weapon {

    private final double returnDamage;
    private final double returnSpeed;
    private final double returnMaxDistance;
    private final double returnHitRadius;
    private final double catchCooldownReductionSeconds;
    private final double chainDamage;
    private final double chainRadius;
    private final double chainSpeed;
    private final int orbitCount;
    private final double orbitRadius;
    private final int orbitDurationTicks;
    private final double orbitDamagePerTick;
    private final int stormDurationTicks;
    private final double stormRadius;
    private final double stormDamagePerTick;

    public StormChakrams(WeaponsPlugin plugin) {
        super(plugin);
        this.returnDamage = configDouble("return-damage", 5.0);
        this.returnSpeed = configDouble("return-speed", 1.2);
        this.returnMaxDistance = configDouble("return-max-distance", 8.0);
        this.returnHitRadius = configDouble("return-hit-radius", 1.4);
        this.catchCooldownReductionSeconds = configDouble("catch-cooldown-reduction-seconds", 1.5);
        this.chainDamage = configDouble("chain-damage", 5.0);
        this.chainRadius = configDouble("chain-radius", 3.0);
        this.chainSpeed = configDouble("chain-speed", 1.8);
        this.orbitCount = configInt("orbit-count", 3);
        this.orbitRadius = configDouble("orbit-radius", 2.5);
        this.orbitDurationTicks = configInt("orbit-duration-ticks", 60);
        this.orbitDamagePerTick = configDouble("orbit-damage-per-tick", 1.5);
        this.stormDurationTicks = configInt("storm-duration-ticks", 50);
        this.stormRadius = configDouble("storm-radius", 4.5);
        this.stormDamagePerTick = configDouble("storm-damage-per-tick", 2.0);
    }

    @Override
    public String id() {
        return "storm_chakrams";
    }

    @Override
    public Material material() {
        return Material.GOLDEN_HOE;
    }

    @Override
    public String displayNameText() {
        return "Storm Chakrams";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 6.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 5.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 8.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 45.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: throw a chakram that", NamedTextColor.GRAY),
                Component.text("returns to you. Catching it reduces", NamedTextColor.GRAY),
                Component.text("your other cooldowns.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: throw a chakram", NamedTextColor.GRAY),
                Component.text("that arcs lightning between enemies.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: summon orbiting blades", NamedTextColor.GRAY),
                Component.text("that damage nearby enemies.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: unleash a storm of", NamedTextColor.GRAY),
                Component.text("spinning chakrams around you.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_TRIDENT_THROW;
    }

    @Override
    public Sound hitSound() {
        return Sound.BLOCK_ANVIL_LAND;
    }

    @Override
    public Sound readySound() {
        return Sound.ITEM_TRIDENT_RETURN;
    }

    @Override
    public void ability1(Player player) {
        double damage = returnDamage * rarity().statMultiplier();
        Location start = player.getLocation().add(0, 1, 0);
        Vector direction = start.getDirection().normalize();
        World world = start.getWorld();
        if (world == null) {
            return;
        }

        new BukkitRunnable() {
            Location current = start.clone();
            boolean returning = false;
            int ticks = 0;
            final Set<UUID> alreadyHit = new HashSet<>();

            @Override
            public void run() {
                if (!player.isOnline() || ticks > 200) {
                    cancel();
                    return;
                }
                if (!returning) {
                    current.add(direction.clone().multiply(returnSpeed));
                    if (current.distance(start) >= returnMaxDistance) {
                        returning = true;
                    }
                } else {
                    Vector toPlayer = player.getLocation().add(0, 1, 0).toVector().subtract(current.toVector()).normalize();
                    current.add(toPlayer.multiply(returnSpeed));
                    if (current.distance(player.getLocation().add(0, 1, 0)) < 1.0) {
                        cooldowns().reduce(player, id(), CooldownManager.Slot.ABILITY2, catchCooldownReductionSeconds);
                        cooldowns().reduce(player, id(), CooldownManager.Slot.ABILITY3, catchCooldownReductionSeconds);
                        cooldowns().reduce(player, id(), CooldownManager.Slot.ULTIMATE, catchCooldownReductionSeconds);
                        Fx.burst(current, Particle.CRIT, 15, 0.3);
                        Fx.sound(player, readySound(), 0.8f, 1.4f);
                        cancel();
                        return;
                    }
                }

                Fx.point(current, Particle.CRIT, 2);
                for (Entity entity : world.getNearbyEntities(current, returnHitRadius, returnHitRadius, returnHitRadius)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player) && alreadyHit.add(living.getUniqueId())) {
                        living.damage(damage, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private CooldownManager cooldowns() {
        return plugin.cooldownManager();
    }

    @Override
    public void ability2(Player player) {
        Snowball projectile = player.launchProjectile(Snowball.class,
                player.getLocation().getDirection().multiply(chainSpeed));
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!projectile.isValid()) {
                    cancel();
                    return;
                }
                Fx.point(projectile.getLocation(), Particle.ELECTRIC_SPARK, 2);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        double damage = chainDamage * rarity().statMultiplier();
        Fx.burst(loc, Particle.ELECTRIC_SPARK, 15, 0.3);
        Fx.sound(loc, hitSound(), 1.0f, 1.2f);

        Set<UUID> hit = new HashSet<>();
        if (event.getHitEntity() instanceof LivingEntity direct) {
            direct.damage(damage, shooter);
            Fx.bloodSpray(direct.getLocation().add(0, 1, 0));
            hit.add(direct.getUniqueId());

            for (Entity entity : world.getNearbyEntities(direct.getLocation(), chainRadius, chainRadius, chainRadius)) {
                if (entity instanceof LivingEntity living && !entity.equals(shooter) && hit.add(living.getUniqueId())) {
                    living.damage(damage * 0.6, shooter);
                    Fx.line(direct.getLocation().add(0, 1, 0), living.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 6);
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                }
            }
        }
    }

    @Override
    public void ability3(Player player) {
        World world = player.getWorld();

        new BukkitRunnable() {
            int ticks = 0;
            final Set<UUID> recentlyHit = new HashSet<>();

            @Override
            public void run() {
                if (ticks >= orbitDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location center = player.getLocation().add(0, 1, 0);
                if (ticks % 20 == 0) {
                    recentlyHit.clear();
                }

                for (int i = 0; i < orbitCount; i++) {
                    double angle = (2 * Math.PI * i / orbitCount) + (ticks * 0.3);
                    Location bladeLoc = center.clone().add(orbitRadius * Math.cos(angle), 0, orbitRadius * Math.sin(angle));
                    Fx.point(bladeLoc, Particle.CRIT, 2);

                    for (Entity entity : world.getNearbyEntities(bladeLoc, 0.8, 0.8, 0.8)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player) && recentlyHit.add(living.getUniqueId())) {
                            living.damage(orbitDamagePerTick, player);
                            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        World world = player.getWorld();
        Fx.sound(player, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 1.0f);

        new BukkitRunnable() {
            int ticks = 0;
            final Set<UUID> recentlyHit = new HashSet<>();

            @Override
            public void run() {
                if (ticks >= stormDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location center = player.getLocation().add(0, 1, 0);
                if (ticks % 15 == 0) {
                    recentlyHit.clear();
                }

                for (int i = 0; i < 6; i++) {
                    double angle = (2 * Math.PI * i / 6) + (ticks * 0.5);
                    double radius = stormRadius * (0.4 + 0.6 * ((ticks % 30) / 30.0));
                    Location point = center.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
                    Fx.point(point, Particle.CRIT, 2);

                    for (Entity entity : world.getNearbyEntities(point, 1.0, 1.0, 1.0)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player) && recentlyHit.add(living.getUniqueId())) {
                            living.damage(stormDamagePerTick, player);
                            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
```

- [ ] **Step 4: Append the config block**

```yaml
  storm_chakrams:
    melee-damage-bonus: 2.0
    ability1-cooldown-seconds: 6.0
    ability2-cooldown-seconds: 5.0
    ability3-cooldown-seconds: 8.0
    ultimate-cooldown-seconds: 45.0
    return-damage: 5.0
    return-speed: 1.2
    return-max-distance: 8.0
    return-hit-radius: 1.4
    catch-cooldown-reduction-seconds: 1.5
    chain-damage: 5.0
    chain-radius: 3.0
    chain-speed: 1.8
    orbit-count: 3
    orbit-radius: 2.5
    orbit-duration-ticks: 60
    orbit-damage-per-tick: 1.5
    storm-duration-ticks: 50
    storm-radius: 4.5
    storm-damage-per-tick: 2.0
```

- [ ] **Step 5: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.ChronoBlade;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.ChronoBlade;
import dev.rbm72.weaponsplugin.items.weapons.StormChakrams;
```

Change:
```java
        weaponRegistry.register(new ChronoBlade(this));
```
to:
```java
        weaponRegistry.register(new ChronoBlade(this));
        weaponRegistry.register(new StormChakrams(this));
```

- [ ] **Step 6: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Manual verification**

`/giveweapon storm_chakrams` — right-click throws a chakram that flies out and returns, damaging what it passes on both legs; catching it should visibly shorten your other cooldowns (watch the action bar timers jump down). Shift+right-click's chakram arcs lightning to nearby enemies on impact. Off-hand right-click summons orbiting blades that hit anything that gets close. Off-hand+shift unleashes the spiraling storm.

- [ ] **Step 8: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/ability/CooldownManager.java plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/StormChakrams.java plugin/src/main/resources/config.yml
git commit -m "feat: add Storm Chakrams weapon, add real cooldown-reduction support"
```

---

## Task 18: Earthbreaker Axe

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/EarthbreakerAxe.java`
- Modify: `plugin/src/main/resources/config.yml` (append `earthbreaker_axe` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Note on the passive:** knockback resistance is a real player attribute (`Attribute.KNOCKBACK_RESISTANCE`), not something `onMeleeDamage`/`onKill` (attacker-side hooks) can grant. `onTick` adds a fixed-key `AttributeModifier` to the player's attribute instance the first time it sees them holding this weapon (checked idempotently so it's safe to call every tick); it is not removed when they switch weapons away. This is a deliberate, minor simplification — a genuinely correct implementation would need a new equip/unequip-aware hook this plan doesn't add for one weapon's passive.

**Interfaces:** Extends `Weapon` (Task 1) only. Uses the tagged-projectile pattern for Boulder Throw.

- [ ] **Step 1: Create `EarthbreakerAxe.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Earth-themed axe: ground-crack line, temporary stone wall, boulder throw, and an expanding-earthquake ultimate. */
public final class EarthbreakerAxe extends Weapon {

    private final double knockbackResistanceAmount;
    private final double crackRange;
    private final double crackDamage;
    private final int crackSlowTicks;
    private final int wallLength;
    private final int wallHeight;
    private final int wallDurationTicks;
    private final double boulderSpeed;
    private final double boulderDamage;
    private final double boulderRadius;
    private final double quakeMaxRadius;
    private final double quakeDamage;
    private final int quakeRings;

    public EarthbreakerAxe(WeaponsPlugin plugin) {
        super(plugin);
        this.knockbackResistanceAmount = configDouble("knockback-resistance-amount", 0.3);
        this.crackRange = configDouble("crack-range", 6.0);
        this.crackDamage = configDouble("crack-damage", 4.0);
        this.crackSlowTicks = configInt("crack-slow-ticks", 60);
        this.wallLength = configInt("wall-length", 3);
        this.wallHeight = configInt("wall-height", 2);
        this.wallDurationTicks = configInt("wall-duration-ticks", 100);
        this.boulderSpeed = configDouble("boulder-speed", 1.4);
        this.boulderDamage = configDouble("boulder-damage", 7.0);
        this.boulderRadius = configDouble("boulder-radius", 2.5);
        this.quakeMaxRadius = configDouble("quake-max-radius", 7.0);
        this.quakeDamage = configDouble("quake-damage", 6.0);
        this.quakeRings = configInt("quake-rings", 4);
    }

    private NamespacedKey knockbackKey() {
        return new NamespacedKey(plugin, "earthbreaker_axe_kb_resist");
    }

    @Override
    public String id() {
        return "earthbreaker_axe";
    }

    @Override
    public Material material() {
        return Material.IRON_AXE;
    }

    @Override
    public String displayNameText() {
        return "Earthbreaker Axe";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 6.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 10.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 6.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 45.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: split the ground", NamedTextColor.GRAY),
                Component.text("ahead, damaging and slowing", NamedTextColor.GRAY),
                Component.text("enemies caught in the crack.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: raise a", NamedTextColor.GRAY),
                Component.text("temporary stone wall in front", NamedTextColor.GRAY),
                Component.text("of you.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: throw a boulder that", NamedTextColor.GRAY),
                Component.text("explodes on impact.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: trigger an", NamedTextColor.GRAY),
                Component.text("earthquake with expanding cracks.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_STONE_BREAK;
    }

    @Override
    public Sound hitSound() {
        return Sound.BLOCK_STONE_HIT;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_ANVIL_LAND;
    }

    @Override
    public void onTick(Player player) {
        var instance = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (instance == null) {
            return;
        }
        boolean alreadyApplied = instance.getModifiers().stream().anyMatch(m -> m.getKey().equals(knockbackKey()));
        if (!alreadyApplied) {
            instance.addModifier(new AttributeModifier(knockbackKey(), knockbackResistanceAmount,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
    }

    @Override
    public void ability1(Player player) {
        double damage = crackDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize().setY(0).normalize();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Set<UUID> alreadyHit = new HashSet<>();

        for (double d = 1; d <= crackRange; d += 1) {
            Location point = origin.clone().add(direction.clone().multiply(d));
            Fx.point(point, Particle.CRIT, 3);

            for (Entity entity : world.getNearbyEntities(point, 1.2, 1.2, 1.2)) {
                if (entity instanceof LivingEntity living && !entity.equals(player) && alreadyHit.add(living.getUniqueId())) {
                    living.damage(damage, player);
                    living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, crackSlowTicks, 3));
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                }
            }
        }
        Fx.sound(player, Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
    }

    @Override
    public void ability2(Player player) {
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize().setY(0).normalize();
        BlockFace face = directionToFace(direction);
        BlockFace side = (face == BlockFace.NORTH || face == BlockFace.SOUTH) ? BlockFace.EAST : BlockFace.NORTH;
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        List<Block> placed = new ArrayList<>();
        List<BlockData> originalData = new ArrayList<>();
        Block base = origin.getBlock().getRelative(face, 2);
        int halfLength = wallLength / 2;

        for (int length = -halfLength; length <= halfLength; length++) {
            for (int height = 0; height < wallHeight; height++) {
                Block block = base.getRelative(side, length).getRelative(0, height, 0);
                if (block.getType().isAir()) {
                    originalData.add(block.getBlockData());
                    block.setType(Material.STONE);
                    placed.add(block);
                }
            }
        }
        Fx.sound(player, Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < placed.size(); i++) {
                placed.get(i).setBlockData(originalData.get(i));
            }
        }, wallDurationTicks);
    }

    private BlockFace directionToFace(Vector direction) {
        if (Math.abs(direction.getX()) > Math.abs(direction.getZ())) {
            return direction.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return direction.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    @Override
    public void ability3(Player player) {
        Snowball projectile = player.launchProjectile(Snowball.class,
                player.getLocation().getDirection().multiply(boulderSpeed));
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!projectile.isValid()) {
                    cancel();
                    return;
                }
                Fx.point(projectile.getLocation(), Particle.CLOUD, 3);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        double damage = boulderDamage * rarity().statMultiplier();
        Fx.burst(loc, Particle.EXPLOSION, 2, 0.2);
        Fx.burst(loc, Particle.CLOUD, 30, boulderRadius * 0.4);
        Fx.sound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.9f);

        for (Entity entity : world.getNearbyEntities(loc, boulderRadius, boulderRadius, boulderRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(shooter)) {
                living.damage(damage, shooter);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ultimate(Player player) {
        double damage = quakeDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.8f);
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ring = 0;

            @Override
            public void run() {
                if (ring >= quakeRings) {
                    cancel();
                    return;
                }
                double radius = quakeMaxRadius * (ring + 1) / (double) quakeRings;
                Fx.ring(center, Particle.CLOUD, radius, 20 + ring * 6);

                for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player) && alreadyHit.add(living.getUniqueId())) {
                        living.damage(damage, player);
                        living.setVelocity(living.getVelocity().setY(0.5));
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }
}
```

- [ ] **Step 2: Append the config block**

```yaml
  earthbreaker_axe:
    melee-damage-bonus: 2.5
    ability1-cooldown-seconds: 6.0
    ability2-cooldown-seconds: 10.0
    ability3-cooldown-seconds: 6.0
    ultimate-cooldown-seconds: 45.0
    knockback-resistance-amount: 0.3
    crack-range: 6.0
    crack-damage: 4.0
    crack-slow-ticks: 60
    wall-length: 3
    wall-height: 2
    wall-duration-ticks: 100
    boulder-speed: 1.4
    boulder-damage: 7.0
    boulder-radius: 2.5
    quake-max-radius: 7.0
    quake-damage: 6.0
    quake-rings: 4
```

- [ ] **Step 3: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.StormChakrams;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.StormChakrams;
import dev.rbm72.weaponsplugin.items.weapons.EarthbreakerAxe;
```

Change:
```java
        weaponRegistry.register(new StormChakrams(this));
```
to:
```java
        weaponRegistry.register(new StormChakrams(this));
        weaponRegistry.register(new EarthbreakerAxe(this));
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual verification**

`/giveweapon earthbreaker_axe` — right-click cracks the ground ahead, damaging/slowing anything standing on it; shift+right-click raises a temporary stone wall that disappears (restoring whatever was there) after ~5s; off-hand right-click throws a boulder that explodes on impact; off-hand+shift triggers expanding earthquake rings that knock enemies up. Get hit by something and compare knockback distance to a weaponless hit to confirm the resistance passive.

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/EarthbreakerAxe.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Earthbreaker Axe weapon"
```

---

## Task 19: Necromancer Staff

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/ability/SummonManager.java`
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/NecromancerStaff.java`
- Modify: `plugin/src/main/resources/config.yml` (append `necromancer_staff` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Note on ally targeting:** Bukkit's public API doesn't expose removing a hostile mob's default "attack the nearest player" AI goal without Paper's separate, version-sensitive Mob Goal API. Instead, each summon gets a lightweight repeating check (every 10 ticks, alongside its despawn timer) that clears its target if it's currently targeting its owner — simple, stable, uses only `Mob.getTarget()`/`setTarget()`, and is good enough that summons functionally never attack their owner even though they're otherwise normal hostile mobs to everyone else.

**Interfaces:**
- `SummonManager` produces: `void add(Player owner, Mob mob)`, `List<Mob> activeSummons(Player owner)` (auto-prunes dead/removed entries).
- Consumes: `Weapon` (Task 1).

- [ ] **Step 1: Create `SummonManager.java`**

```java
package dev.rbm72.weaponsplugin.ability;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tracks each player's active summoned allies so passives can find and buff them. */
public final class SummonManager {

    private final Map<UUID, List<Mob>> summons = new HashMap<>();

    public void add(Player owner, Mob mob) {
        summons.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>()).add(mob);
    }

    /** Live summons only — dead or removed entities are pruned before returning. */
    public List<Mob> activeSummons(Player owner) {
        List<Mob> list = summons.get(owner.getUniqueId());
        if (list == null) {
            return List.of();
        }
        list.removeIf(mob -> !mob.isValid());
        return list;
    }
}
```

- [ ] **Step 2: Create `NecromancerStaff.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.SummonManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/** Summoner staff: raises skeletons, summons harassing vexes, throws a bone barrage, and calls a giant undead guardian ultimate. */
public final class NecromancerStaff extends Weapon {

    private final int skeletonCount;
    private final int skeletonDurationTicks;
    private final double skeletonHealthBonus;
    private final double skeletonDamageBonus;
    private final int ghostCount;
    private final int ghostDurationTicks;
    private final int barrageProjectileCount;
    private final double barrageDamage;
    private final double barrageSpeed;
    private final double barrageSpreadDegrees;
    private final int guardianDurationTicks;
    private final double guardianHealthMultiplier;
    private final double guardianDamageMultiplier;
    private final double guardianScale;
    private final int killBuffDurationTicks;

    public NecromancerStaff(WeaponsPlugin plugin) {
        super(plugin);
        this.skeletonCount = configInt("skeleton-count", 2);
        this.skeletonDurationTicks = configInt("skeleton-duration-ticks", 400);
        this.skeletonHealthBonus = configDouble("skeleton-health-bonus", 10.0);
        this.skeletonDamageBonus = configDouble("skeleton-damage-bonus", 2.0);
        this.ghostCount = configInt("ghost-count", 2);
        this.ghostDurationTicks = configInt("ghost-duration-ticks", 300);
        this.barrageProjectileCount = configInt("barrage-projectile-count", 5);
        this.barrageDamage = configDouble("barrage-damage", 3.0);
        this.barrageSpeed = configDouble("barrage-speed", 1.6);
        this.barrageSpreadDegrees = configDouble("barrage-spread-degrees", 8.0);
        this.guardianDurationTicks = configInt("guardian-duration-ticks", 600);
        this.guardianHealthMultiplier = configDouble("guardian-health-multiplier", 3.0);
        this.guardianDamageMultiplier = configDouble("guardian-damage-multiplier", 2.5);
        this.guardianScale = configDouble("guardian-scale", 1.6);
        this.killBuffDurationTicks = configInt("kill-buff-duration-ticks", 100);
    }

    private final SummonManager summonManager = new SummonManager();

    @Override
    public String id() {
        return "necromancer_staff";
    }

    @Override
    public Material material() {
        return Material.STICK;
    }

    @Override
    public String displayNameText() {
        return "Necromancer Staff";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 1.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 20.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 15.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 6.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 60.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: raise skeleton allies", NamedTextColor.GRAY),
                Component.text("to fight for you.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: summon ghosts", NamedTextColor.GRAY),
                Component.text("to harass nearby enemies.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: throw a barrage of", NamedTextColor.GRAY),
                Component.text("bones.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: summon a giant", NamedTextColor.GRAY),
                Component.text("undead guardian.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ZOMBIE_VILLAGER_CURE;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_SKELETON_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_WITHER_AMBIENT;
    }

    @Override
    public void onKill(Player attacker, LivingEntity victim) {
        for (Mob summon : summonManager.activeSummons(attacker)) {
            summon.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, killBuffDurationTicks, 0));
            summon.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, killBuffDurationTicks, 0));
            Fx.burst(summon.getLocation().add(0, 1, 0), Particle.SOUL, 10, 0.3);
        }
    }

    private void protectOwnerAndDespawn(Player owner, Mob mob, int durationTicks) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!mob.isValid() || ticks >= durationTicks) {
                    mob.remove();
                    cancel();
                    return;
                }
                if (mob.getTarget() != null && mob.getTarget().equals(owner)) {
                    mob.setTarget(null);
                }
                ticks += 10;
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    @Override
    public void ability1(Player player) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        double healthBonus = skeletonHealthBonus * rarity().statMultiplier();
        double damageBonus = skeletonDamageBonus * rarity().statMultiplier();

        for (int i = 0; i < skeletonCount; i++) {
            Location spawnLoc = origin.clone().add((Math.random() - 0.5) * 3, 0, (Math.random() - 0.5) * 3);
            Skeleton skeleton = world.spawn(spawnLoc, Skeleton.class, mob -> {
                mob.setCanPickupItems(false);
                mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue() + healthBonus);
                mob.setHealth(mob.getAttribute(Attribute.MAX_HEALTH).getValue());
                mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(damageBonus);
            });
            summonManager.add(player, skeleton);
            protectOwnerAndDespawn(player, skeleton, skeletonDurationTicks);
            Fx.burst(spawnLoc.clone().add(0, 1, 0), Particle.SOUL, 20, 0.3);
        }
        Fx.sound(player, Sound.ENTITY_SKELETON_AMBIENT, 1.0f, 0.7f);
    }

    @Override
    public void ability2(Player player) {
        World world = player.getWorld();
        Location origin = player.getLocation();

        for (int i = 0; i < ghostCount; i++) {
            Location spawnLoc = origin.clone().add((Math.random() - 0.5) * 3, 1, (Math.random() - 0.5) * 3);
            Vex ghost = world.spawn(spawnLoc, Vex.class);
            summonManager.add(player, ghost);
            protectOwnerAndDespawn(player, ghost, ghostDurationTicks);
            Fx.burst(spawnLoc, Particle.SOUL, 20, 0.3);
        }
        Fx.sound(player, Sound.ENTITY_VEX_AMBIENT, 1.0f, 1.0f);
    }

    @Override
    public void ability3(Player player) {
        double damage = barrageDamage * rarity().statMultiplier();
        Location eye = player.getEyeLocation();

        for (int i = 0; i < barrageProjectileCount; i++) {
            double offsetDegrees = (i - (barrageProjectileCount - 1) / 2.0) * barrageSpreadDegrees;
            Vector direction = eye.getDirection().clone();
            Vector axis = new Vector(0, 1, 0);
            double radians = Math.toRadians(offsetDegrees);
            Vector rotated = rotateAroundY(direction, radians);

            Snowball projectile = player.launchProjectile(Snowball.class, rotated.multiply(barrageSpeed));
            projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!projectile.isValid()) {
                        cancel();
                        return;
                    }
                    Fx.point(projectile.getLocation(), Particle.SOUL, 2);
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    private Vector rotateAroundY(Vector v, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = v.getX() * cos + v.getZ() * sin;
        double z = -v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        double damage = barrageDamage * rarity().statMultiplier();
        Fx.burst(loc, Particle.SOUL, 10, 0.3);
        Fx.sound(loc, hitSound(), 0.8f, 1.2f);

        if (event.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, shooter);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ultimate(Player player) {
        World world = player.getWorld();
        Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().multiply(2));

        Zombie guardian = world.spawn(spawnLoc, Zombie.class, mob -> {
            mob.setCanPickupItems(false);
            mob.setBaby(false);
            double baseHealth = mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
            mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseHealth * guardianHealthMultiplier);
            mob.setHealth(mob.getAttribute(Attribute.MAX_HEALTH).getValue());
            double baseDamage = mob.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
            mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(baseDamage * guardianDamageMultiplier);
            if (mob.getAttribute(Attribute.SCALE) != null) {
                mob.getAttribute(Attribute.SCALE).setBaseValue(guardianScale);
            }
        });
        summonManager.add(player, guardian);
        protectOwnerAndDespawn(player, guardian, guardianDurationTicks);
        Fx.burst(spawnLoc.clone().add(0, 1, 0), Particle.SOUL, 40, 0.5);
        Fx.sound(player, Sound.ENTITY_ZOMBIE_AMBIENT, 1.5f, 0.5f);
    }
}
```

- [ ] **Step 3: Append the config block**

```yaml
  necromancer_staff:
    melee-damage-bonus: 1.5
    ability1-cooldown-seconds: 20.0
    ability2-cooldown-seconds: 15.0
    ability3-cooldown-seconds: 6.0
    ultimate-cooldown-seconds: 60.0
    skeleton-count: 2
    skeleton-duration-ticks: 400
    skeleton-health-bonus: 10.0
    skeleton-damage-bonus: 2.0
    ghost-count: 2
    ghost-duration-ticks: 300
    barrage-projectile-count: 5
    barrage-damage: 3.0
    barrage-speed: 1.6
    barrage-spread-degrees: 8.0
    guardian-duration-ticks: 600
    guardian-health-multiplier: 3.0
    guardian-damage-multiplier: 2.5
    guardian-scale: 1.6
    kill-buff-duration-ticks: 100
```

- [ ] **Step 4: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.EarthbreakerAxe;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.EarthbreakerAxe;
import dev.rbm72.weaponsplugin.items.weapons.NecromancerStaff;
```

Change:
```java
        weaponRegistry.register(new EarthbreakerAxe(this));
```
to:
```java
        weaponRegistry.register(new EarthbreakerAxe(this));
        weaponRegistry.register(new NecromancerStaff(this));
```

- [ ] **Step 5: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. If `Attribute.SCALE` doesn't exist on the resolved Paper API version, the build error will name it — if so, delete the `if (mob.getAttribute(Attribute.SCALE) != null) { ... }` block in the ultimate method (it's a purely cosmetic size boost; the guardian still gets its health/damage multipliers without it).

- [ ] **Step 6: Manual verification**

`/giveweapon necromancer_staff` — right-click raises 2 skeleton allies that fight nearby hostiles but never attack you; shift+right-click summons vexes; off-hand right-click throws a 5-bone fan spread; off-hand+shift summons one large, tougher-hitting zombie guardian. Kill something near your active summons and confirm they briefly glow with Strength/Speed.

- [ ] **Step 7: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/ability/SummonManager.java plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/NecromancerStaff.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Necromancer Staff weapon"
```

---

## Task 20: Sakura Blade

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/SakuraBlade.java`
- Modify: `plugin/src/main/resources/config.yml` (append `sakura_blade` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Interfaces:** Extends `Weapon` (Task 1) only.

- [ ] **Step 1: Create `SakuraBlade.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Elegant sword: cone slash, petal dash, bloom AoE, and a lingering petal-field ultimate. Combo hits stack movement speed. */
public final class SakuraBlade extends Weapon {

    private final long comboWindowMs;
    private final int comboMaxStacks;
    private final double cleaveDamage;
    private final double cleaveRange;
    private final double dashDamage;
    private final double dashDistance;
    private final double bloomDamage;
    private final double bloomRadius;
    private final double fieldRadius;
    private final int fieldDurationTicks;
    private final double fieldDamagePerTick;
    private final double fieldRangeAhead;

    public SakuraBlade(WeaponsPlugin plugin) {
        super(plugin);
        this.comboWindowMs = configInt("combo-window-ms", 2500);
        this.comboMaxStacks = configInt("combo-max-stacks", 3);
        this.cleaveDamage = configDouble("cleave-damage", 4.0);
        this.cleaveRange = configDouble("cleave-range", 3.0);
        this.dashDamage = configDouble("dash-damage", 4.5);
        this.dashDistance = configDouble("dash-distance", 5.0);
        this.bloomDamage = configDouble("bloom-damage", 5.0);
        this.bloomRadius = configDouble("bloom-radius", 3.5);
        this.fieldRadius = configDouble("field-radius", 3.0);
        this.fieldDurationTicks = configInt("field-duration-ticks", 80);
        this.fieldDamagePerTick = configDouble("field-damage-per-tick", 1.2);
        this.fieldRangeAhead = configDouble("field-range-ahead", 4.0);
    }

    private final Map<UUID, Long> lastComboHitMs = new HashMap<>();
    private final Map<UUID, Integer> comboStacks = new HashMap<>();

    @Override
    public String id() {
        return "sakura_blade";
    }

    @Override
    public Material material() {
        return Material.IRON_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Sakura Blade";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 6.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 7.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 40.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: cherry blossom slash", NamedTextColor.GRAY),
                Component.text("in a cone ahead of you.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: petal dash", NamedTextColor.GRAY),
                Component.text("through enemies ahead.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: bloom explosion around", NamedTextColor.GRAY),
                Component.text("you.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: fill an area with", NamedTextColor.GRAY),
                Component.text("petals that damage enemies over", NamedTextColor.GRAY),
                Component.text("time.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_GRASS_BREAK;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_ATTACK_SWEEP;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_CHERRY_LEAVES_BREAK;
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        long lastHit = lastComboHitMs.getOrDefault(uuid, 0L);
        int stacks = (now - lastHit <= comboWindowMs) ? Math.min(comboMaxStacks, comboStacks.getOrDefault(uuid, 0) + 1) : 1;
        lastComboHitMs.put(uuid, now);
        comboStacks.put(uuid, stacks);
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) (comboWindowMs / 50), stacks - 1, true, false));
        Fx.point(attacker.getLocation().add(0, 1, 0), Particle.CHERRY_LEAVES, 3);
    }

    @Override
    public void ability1(Player player) {
        double damage = cleaveDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Fx.trail(origin.clone().add(0, 1, 0), Particle.CHERRY_LEAVES, 25, 0.6, 0.05);

        for (Entity entity : world.getNearbyEntities(origin, cleaveRange, cleaveRange, cleaveRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(origin.toVector()).normalize();
            if (direction.dot(toEntity) < 0.4) {
                continue;
            }
            living.damage(damage, player);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = dashDamage * rarity().statMultiplier();
        Location start = player.getLocation();
        Vector direction = start.getDirection().normalize();
        World world = start.getWorld();
        if (world == null) {
            return;
        }
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= 8) {
                    cancel();
                    return;
                }
                player.setVelocity(direction.clone().multiply(0.9).setY(0.15));
                Fx.trail(player.getLocation().add(0, 1, 0), Particle.CHERRY_LEAVES, 6, 0.25, 0.02);

                for (Entity nearby : player.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (nearby instanceof LivingEntity entity && !entity.equals(player) && alreadyHit.add(entity.getUniqueId())) {
                        entity.damage(damage, player);
                        Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability3(Player player) {
        double damage = bloomDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.burst(center.clone().add(0, 1, 0), Particle.CHERRY_LEAVES, 40, bloomRadius * 0.4);
        Fx.sound(player, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.2f, 1.0f);

        for (Entity entity : world.getNearbyEntities(center, bloomRadius, bloomRadius, bloomRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                Vector knockback = living.getLocation().toVector().subtract(center.toVector()).normalize().setY(0.3);
                living.setVelocity(living.getVelocity().add(knockback));
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ultimate(Player player) {
        double damagePerTick = fieldDamagePerTick * rarity().statMultiplier();
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(fieldRangeAhead));
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.BLOCK_CHERRY_LEAVES_PLACE, 1.0f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= fieldDurationTicks) {
                    cancel();
                    return;
                }
                Fx.burst(center, Particle.CHERRY_LEAVES, 8, fieldRadius * 0.5);
                if (ticks % 10 == 0) {
                    for (Entity entity : world.getNearbyEntities(center, fieldRadius, fieldRadius, fieldRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(damagePerTick, player);
                            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
```

- [ ] **Step 2: Append the config block**

```yaml
  sakura_blade:
    melee-damage-bonus: 2.0
    ability1-cooldown-seconds: 5.0
    ability2-cooldown-seconds: 6.0
    ability3-cooldown-seconds: 7.0
    ultimate-cooldown-seconds: 40.0
    combo-window-ms: 2500
    combo-max-stacks: 3
    cleave-damage: 4.0
    cleave-range: 3.0
    dash-damage: 4.5
    dash-distance: 5.0
    bloom-damage: 5.0
    bloom-radius: 3.5
    field-radius: 3.0
    field-duration-ticks: 80
    field-damage-per-tick: 1.2
    field-range-ahead: 4.0
```

- [ ] **Step 3: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.NecromancerStaff;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.NecromancerStaff;
import dev.rbm72.weaponsplugin.items.weapons.SakuraBlade;
```

Change:
```java
        weaponRegistry.register(new NecromancerStaff(this));
```
to:
```java
        weaponRegistry.register(new NecromancerStaff(this));
        weaponRegistry.register(new SakuraBlade(this));
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual verification**

`/giveweapon sakura_blade` — right-click cone-slashes ahead; shift+right-click dashes through enemies; off-hand right-click bursts petals around you; off-hand+shift leaves a damaging petal field ahead. Land 3 melee hits in quick succession and confirm your movement speed visibly increases with each hit (and resets if you wait past the combo window).

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/SakuraBlade.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Sakura Blade weapon"
```

---

## Task 21: Starbreaker

**Files:**
- Create: `plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/Starbreaker.java`
- Modify: `plugin/src/main/resources/config.yml` (append `starbreaker` block)
- Modify: `plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java` (register it)

**Interfaces:** Extends `Weapon` (Task 1) only. This is the last weapon in the batch.

- [ ] **Step 1: Create `Starbreaker.java`**

```java
package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Endgame legendary sword: star spread, warp strike, supernova, and a galaxy-collapse ultimate. Chaining different abilities buffs the next one. */
public final class Starbreaker extends Weapon {

    private final long momentumWindowMs;
    private final double momentumDamageBonus;
    private final int spreadCount;
    private final double spreadDamage;
    private final double spreadSpeed;
    private final double spreadSpreadDegrees;
    private final double warpRange;
    private final double warpDamage;
    private final double supernovaDamage;
    private final double supernovaRadius;
    private final double galaxyPullRadius;
    private final int galaxyPullDurationTicks;
    private final double galaxyExplosionDamage;
    private final double galaxyExplosionRadius;

    public Starbreaker(WeaponsPlugin plugin) {
        super(plugin);
        this.momentumWindowMs = configInt("momentum-window-ms", 3000);
        this.momentumDamageBonus = configDouble("momentum-damage-bonus", 0.35);
        this.spreadCount = configInt("spread-count", 5);
        this.spreadDamage = configDouble("spread-damage", 4.0);
        this.spreadSpeed = configDouble("spread-speed", 1.8);
        this.spreadSpreadDegrees = configDouble("spread-spread-degrees", 10.0);
        this.warpRange = configDouble("warp-range", 10.0);
        this.warpDamage = configDouble("warp-damage", 9.0);
        this.supernovaDamage = configDouble("supernova-damage", 8.0);
        this.supernovaRadius = configDouble("supernova-radius", 4.5);
        this.galaxyPullRadius = configDouble("galaxy-pull-radius", 7.0);
        this.galaxyPullDurationTicks = configInt("galaxy-pull-duration-ticks", 50);
        this.galaxyExplosionDamage = configDouble("galaxy-explosion-damage", 16.0);
        this.galaxyExplosionRadius = configDouble("galaxy-explosion-radius", 6.0);
    }

    private final Map<UUID, Integer> lastSlot = new HashMap<>();
    private final Map<UUID, Long> momentumWindowEndMs = new HashMap<>();

    private double momentumMultiplier(Player player, int slot) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long windowEnd = momentumWindowEndMs.get(uuid);
        Integer previousSlot = lastSlot.get(uuid);
        boolean triggered = windowEnd != null && now <= windowEnd && previousSlot != null && previousSlot != slot;

        lastSlot.put(uuid, slot);
        momentumWindowEndMs.put(uuid, now + momentumWindowMs);

        if (triggered) {
            Fx.point(player.getLocation().add(0, 1.5, 0), Particle.END_ROD, 6);
            return 1 + momentumDamageBonus;
        }
        return 1.0;
    }

    @Override
    public String id() {
        return "starbreaker";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Starbreaker";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 4.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 6.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 7.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 9.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 65.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: throw a spread of", NamedTextColor.GRAY),
                Component.text("miniature stars.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: warp to the", NamedTextColor.GRAY),
                Component.text("nearest enemy ahead and strike.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: trigger a supernova", NamedTextColor.GRAY),
                Component.text("around you.", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: collapse a galaxy", NamedTextColor.GRAY),
                Component.text("onto your enemies. Chaining", NamedTextColor.GRAY),
                Component.text("different abilities boosts damage.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ENDER_DRAGON_FLAP;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_EXPLODE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
    }

    @Override
    public void ability1(Player player) {
        double damage = spreadDamage * rarity().statMultiplier() * momentumMultiplier(player, 1);
        Location eye = player.getEyeLocation();

        for (int i = 0; i < spreadCount; i++) {
            double offsetDegrees = (i - (spreadCount - 1) / 2.0) * spreadSpreadDegrees;
            double radians = Math.toRadians(offsetDegrees);
            Vector direction = eye.getDirection().clone();
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            Vector rotated = new Vector(direction.getX() * cos + direction.getZ() * sin, direction.getY(),
                    -direction.getX() * sin + direction.getZ() * cos);

            Snowball projectile = player.launchProjectile(Snowball.class, rotated.multiply(spreadSpeed));
            projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
            projectile.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "starbreaker_damage"), PersistentDataType.DOUBLE, damage);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!projectile.isValid()) {
                        cancel();
                        return;
                    }
                    Fx.point(projectile.getLocation(), Particle.END_ROD, 2);
                    Fx.point(projectile.getLocation(), Particle.PORTAL, 1);
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        Double taggedDamage = event.getEntity().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "starbreaker_damage"), PersistentDataType.DOUBLE);
        double damage = taggedDamage != null ? taggedDamage : spreadDamage * rarity().statMultiplier();

        Fx.burst(loc, Particle.END_ROD, 15, 0.3);
        Fx.burst(loc, Particle.PORTAL, 10, 0.3);
        Fx.sound(loc, hitSound(), 0.7f, 1.3f);

        if (event.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, shooter);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = warpDamage * rarity().statMultiplier() * momentumMultiplier(player, 2);
        World world = player.getWorld();
        Location origin = player.getLocation();
        LivingEntity target = null;
        double closest = Double.MAX_VALUE;

        for (Entity entity : world.getNearbyEntities(origin, warpRange, warpRange, warpRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            double dot = origin.getDirection().normalize().dot(living.getLocation().toVector().subtract(origin.toVector()).normalize());
            if (dot < 0.3) {
                continue;
            }
            double distanceSquared = living.getLocation().distanceSquared(origin);
            if (distanceSquared < closest) {
                closest = distanceSquared;
                target = living;
            }
        }
        if (target == null) {
            return;
        }

        Vector behind = target.getLocation().getDirection().normalize().multiply(-1.5);
        Location warpTo = target.getLocation().add(behind).add(0, 0, 0);
        Fx.line(origin.add(0, 1, 0), warpTo.clone().add(0, 1, 0), Particle.END_ROD, 15);
        player.teleport(warpTo.setDirection(target.getLocation().subtract(warpTo).toVector()));
        target.damage(damage, player);
        Fx.burst(target.getLocation().add(0, 1, 0), Particle.END_ROD, 20, 0.3);
        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
    }

    @Override
    public void ability3(Player player) {
        double damage = supernovaDamage * rarity().statMultiplier() * momentumMultiplier(player, 3);
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.burst(center.clone().add(0, 1, 0), Particle.END_ROD, 40, supernovaRadius * 0.4);
        Fx.burst(center.clone().add(0, 1, 0), Particle.PORTAL, 30, supernovaRadius * 0.4);
        Fx.sound(player, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.1f);

        for (Entity entity : world.getNearbyEntities(center, supernovaRadius, supernovaRadius, supernovaRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                Vector knockback = living.getLocation().toVector().subtract(center.toVector()).normalize().setY(0.4);
                living.setVelocity(living.getVelocity().add(knockback));
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ultimate(Player player) {
        double explosionDamage = galaxyExplosionDamage * rarity().statMultiplier() * momentumMultiplier(player, 4);
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(5));
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (ticks < galaxyPullDurationTicks) {
                    Fx.ring(center, Particle.PORTAL, 2.5, 16);
                    Fx.point(center, Particle.DRAGON_BREATH, 3);
                    for (Entity entity : world.getNearbyEntities(center, galaxyPullRadius, galaxyPullRadius, galaxyPullRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            Vector pull = center.toVector().subtract(living.getLocation().toVector()).normalize().multiply(0.25);
                            living.setVelocity(living.getVelocity().add(pull));
                        }
                    }
                    ticks++;
                    return;
                }

                Fx.burst(center, Particle.END_ROD, 60, galaxyExplosionRadius * 0.5);
                Fx.burst(center, Particle.PORTAL, 60, galaxyExplosionRadius * 0.5);
                Fx.burst(center, Particle.DRAGON_BREATH, 40, galaxyExplosionRadius * 0.4);
                Fx.sound(center, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.5f, 0.9f);

                for (Entity entity : world.getNearbyEntities(center, galaxyExplosionRadius, galaxyExplosionRadius, galaxyExplosionRadius)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player) && alreadyHit.add(living.getUniqueId())) {
                        living.damage(explosionDamage, player);
                        Vector knockback = living.getLocation().toVector().subtract(center.toVector()).normalize().setY(0.6);
                        living.setVelocity(living.getVelocity().add(knockback.multiply(1.5)));
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
```

- [ ] **Step 2: Append the config block**

```yaml
  starbreaker:
    melee-damage-bonus: 4.0
    ability1-cooldown-seconds: 6.0
    ability2-cooldown-seconds: 7.0
    ability3-cooldown-seconds: 9.0
    ultimate-cooldown-seconds: 65.0
    momentum-window-ms: 3000
    momentum-damage-bonus: 0.35
    spread-count: 5
    spread-damage: 4.0
    spread-speed: 1.8
    spread-spread-degrees: 10.0
    warp-range: 10.0
    warp-damage: 9.0
    supernova-damage: 8.0
    supernova-radius: 4.5
    galaxy-pull-radius: 7.0
    galaxy-pull-duration-ticks: 50
    galaxy-explosion-damage: 16.0
    galaxy-explosion-radius: 6.0
```

- [ ] **Step 3: Register it in `WeaponsPlugin.java`**

Change:
```java
import dev.rbm72.weaponsplugin.items.weapons.SakuraBlade;
```
to:
```java
import dev.rbm72.weaponsplugin.items.weapons.SakuraBlade;
import dev.rbm72.weaponsplugin.items.weapons.Starbreaker;
```

Change:
```java
        weaponRegistry.register(new SakuraBlade(this));
```
to:
```java
        weaponRegistry.register(new SakuraBlade(this));
        weaponRegistry.register(new Starbreaker(this));
```

- [ ] **Step 4: Compile**

Run: `cd plugin && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. This should now be all 22 weapons (7 original + rebuilt Arcane Staff + 14 new) registered and compiling clean.

- [ ] **Step 5: Manual verification**

`/giveweapon starbreaker` — right-click throws a 5-star spread; shift+right-click warps you to and strikes the nearest enemy ahead; off-hand right-click triggers a supernova around you; off-hand+shift pulls enemies in then detonates a large galaxy-themed explosion. Use two *different* ability slots back-to-back within ~3s and confirm the second one shows the momentum bonus particle flash and hits harder than using the same slot twice in a row.

Also open `/weapons` and confirm all 22 weapons are visible in the "All Weapons" filter without needing to scroll (double-chest resize from Task 6).

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/dev/rbm72/weaponsplugin/items/weapons/Starbreaker.java plugin/src/main/resources/config.yml plugin/src/main/java/dev/rbm72/weaponsplugin/WeaponsPlugin.java
git commit -m "feat: add Starbreaker weapon"
```
