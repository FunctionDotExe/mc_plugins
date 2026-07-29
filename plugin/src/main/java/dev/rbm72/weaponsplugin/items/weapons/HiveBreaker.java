package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.ability.SummonManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.listeners.PlayerSummonTargetListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Beekeeper's war mace: every ability shatters a real hive prop and releases genuinely aggressive,
 * AI-driven {@link Bee} entities (targeted and angered on spawn, not just cosmetic) instead of a
 * damage-number gimmick — plus a real, temporary {@link Material#HONEY_BLOCK} slow patch for the trap.
 */
public final class HiveBreaker extends Weapon {

    private static final Color HONEY_GOLD = Color.fromRGB(230, 170, 40);

    private final double smashDamage;
    private final double smashRange;
    private final int singleBeeDurationTicks;
    private final int swarmCount;
    private final int swarmDurationTicks;
    private final int honeyDurationTicks;
    private final int ultimateCount;
    private final int ultimateDurationTicks;
    private final double ultimateBurstDamage;
    private final double ultimateBurstRadius;

    public HiveBreaker(WeaponsPlugin plugin) {
        super(plugin);
        this.smashDamage = configDouble("smash-damage", 4.0);
        this.smashRange = configDouble("smash-range", 4.0);
        this.singleBeeDurationTicks = configInt("single-bee-duration-ticks", 200);
        this.swarmCount = configInt("swarm-count", 4);
        this.swarmDurationTicks = configInt("swarm-duration-ticks", 240);
        this.honeyDurationTicks = configInt("honey-duration-ticks", 100);
        this.ultimateCount = configInt("ultimate-count", 8);
        this.ultimateDurationTicks = configInt("ultimate-duration-ticks", 300);
        this.ultimateBurstDamage = configDouble("ultimate-burst-damage", 6.0);
        this.ultimateBurstRadius = configDouble("ultimate-burst-radius", 3.5);
    }

    private final SummonManager summonManager = new SummonManager();
    private NamespacedKey playerSummonKey;

    private NamespacedKey summonKey() {
        if (playerSummonKey == null) {
            playerSummonKey = new NamespacedKey(plugin, PlayerSummonTargetListener.KEY_NAME);
        }
        return playerSummonKey;
    }

    @Override
    public String id() {
        return "hive_breaker";
    }

    @Override
    public Material material() {
        return Material.BEEHIVE;
    }

    @Override
    public String displayNameText() {
        return "Hive Breaker";
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
        return configDouble("ability2-cooldown-seconds", 12.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 9.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Swarm")
                .accent(HONEY_GOLD)
                .perMeleeHit(configDouble("swarm-per-hit", 5.0))
                .perDamageDealt(configDouble("swarm-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("swarm-per-ability", 9.0))
                .perKill(configDouble("swarm-per-kill", 11.0))
                .decay(configDouble("swarm-decay-per-second", 2.0), configDouble("swarm-decay-grace", 7.0))
                .cooldownFloor(configDouble("swarm-cooldown-floor", 50.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: smash the ground,", NamedTextColor.GRAY),
                Component.text("shattering a hive that releases", NamedTextColor.GRAY),
                Component.text("an angry bee onto your target.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Smash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: shatter multiple", NamedTextColor.GRAY),
                Component.text("hives, releasing a swarm of bees", NamedTextColor.GRAY),
                Component.text("to hunt down nearby enemies.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Swarm Call";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: throw a hive that bursts", NamedTextColor.GRAY),
                Component.text("into real sticky honey, slowing", NamedTextColor.GRAY),
                Component.text("anyone caught in it, plus bees.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Honey Trap";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: shatter every hive", NamedTextColor.GRAY),
                Component.text("you carry at once, unleashing a", NamedTextColor.GRAY),
                Component.text("full swarm on everything nearby.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Hive Cataclysm";
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_BEEHIVE_SHEAR;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_BEE_STING;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_BEEHIVE_WORK;
    }

    private LivingEntity closestInFront(Player player, double range) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        LivingEntity best = null;
        double closest = range;
        for (Entity entity : world.getNearbyEntities(eye, range, range, range)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(eye.toVector());
            double distance = toEntity.length();
            if (distance > range || distance < 0.001) {
                continue;
            }
            if (toEntity.normalize().dot(direction) > 0.8 && distance < closest) {
                closest = distance;
                best = living;
            }
        }
        return best;
    }

    private void tagAsPlayerSummon(Mob mob) {
        mob.getPersistentDataContainer().set(summonKey(), PersistentDataType.BYTE, (byte) 1);
    }

    private void despawnAfter(Mob mob, int durationTicks) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!mob.isValid() || ticks >= durationTicks) {
                    mob.remove();
                    cancel();
                    return;
                }
                ticks += 10;
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    /** Spawns one real, immediately-aggressive Bee tagged as a player summon and pointed at {@code target} (or left to roam if null). */
    private Bee releaseBee(Player owner, Location loc, LivingEntity target, int durationTicks) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        Bee bee = world.spawn(loc, Bee.class, mob -> {
            mob.setAnger(400);
            mob.setCannotEnterHiveTicks(durationTicks + 20);
            tagAsPlayerSummon(mob);
            if (target != null) {
                mob.setTarget(target);
            }
        });
        summonManager.add(owner, bee);
        despawnAfter(bee, durationTicks);
        Fx.coloredBurst(loc, HONEY_GOLD, 1.4f, 16, 0.35);
        Fx.sound(loc, Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 1.0f, 1.1f);
        return bee;
    }

    @Override
    public void ability1(Player player) {
        double damage = smashDamage * rarity().statMultiplier();
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        LivingEntity target = closestInFront(player, smashRange);
        Location impact = target != null
                ? target.getLocation()
                : player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(2.5));

        Fx.sound(player, castSound(), 1.0f, 1.0f);
        Fx.blockBurst(impact, Material.BEEHIVE, 16, 0.5);
        Fx.coloredBurst(impact.clone().add(0, 0.5, 0), HONEY_GOLD, 1.6f, 20, 1.0);

        for (Entity entity : world.getNearbyEntities(impact, 1.6, 1.6, 1.6)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
        releaseBee(player, impact.clone().add(0, 1, 0), target, singleBeeDurationTicks);
    }

    @Override
    public void ability2(Player player) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        List<LivingEntity> nearby = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(player.getLocation(), 12, 12, 12)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                nearby.add(living);
            }
        }
        Fx.sound(player, castSound(), 1.1f, 0.9f);

        for (int i = 0; i < swarmCount; i++) {
            Location spawnLoc = player.getLocation().add((Math.random() - 0.5) * 3, 1, (Math.random() - 0.5) * 3);
            LivingEntity target = nearby.isEmpty() ? null : nearby.get(i % nearby.size());
            releaseBee(player, spawnLoc, target, swarmDurationTicks);
        }
    }

    @Override
    public void ability3(Player player) {
        Snowball projectile = player.launchProjectile(Snowball.class, player.getLocation().getDirection().multiply(1.3));
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        var icon = Fx.spinningIcon(plugin, projectile.getLocation(), Material.BEEHIVE, 0.8f, 40, 20.0);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!projectile.isValid()) {
                    if (icon != null && !icon.isDead()) {
                        icon.remove();
                    }
                    cancel();
                    return;
                }
                Fx.point(projectile.getLocation(), Particle.FALLING_HONEY, 2);
                if (icon != null && !icon.isDead()) {
                    icon.teleport(projectile.getLocation());
                }
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
        Fx.sound(loc, Sound.BLOCK_BEEHIVE_SHEAR, 1.0f, 0.9f);
        Fx.coloredBurst(loc, HONEY_GOLD, 1.8f, 26, 0.5);

        List<Block> placed = new ArrayList<>();
        List<BlockData> originals = new ArrayList<>();
        Block ground = world.getHighestBlockAt(loc.getBlockX(), loc.getBlockZ()).getRelative(0, 1, 0);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = ground.getRelative(x, 0, z);
                if (block.getType().isAir()) {
                    originals.add(block.getBlockData());
                    block.setType(Material.HONEY_BLOCK, false);
                    placed.add(block);
                }
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < placed.size(); i++) {
                Block block = placed.get(i);
                if (block.getType() == Material.HONEY_BLOCK) {
                    block.setBlockData(originals.get(i), false);
                }
            }
        }, honeyDurationTicks);

        LivingEntity nearestTarget = null;
        double closest = 8.0;
        for (Entity entity : world.getNearbyEntities(loc, 8, 8, 8)) {
            if (entity instanceof LivingEntity living && !entity.equals(shooter)) {
                double distance = living.getLocation().distanceSquared(loc);
                if (distance < closest) {
                    closest = distance;
                    nearestTarget = living;
                }
            }
        }
        releaseBee(shooter, loc.clone().add(0, 1, 0), nearestTarget, swarmDurationTicks);
        if (!event.getEntity().isDead()) {
            event.getEntity().remove();
        }
    }

    @Override
    public void ultimate(Player player) {
        double damage = ultimateBurstDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 1.4f, 0.7f);
        Fx.sound(player, castSound(), 1.4f, 0.8f);
        Fx.expandingRings(plugin, center, Particle.FALLING_HONEY, ultimateBurstRadius, 4, 3L);
        Fx.coloredBurst(center.clone().add(0, 1, 0), HONEY_GOLD, 2.4f, 40, ultimateBurstRadius * 0.5);

        List<LivingEntity> nearby = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, ultimateBurstRadius, ultimateBurstRadius, ultimateBurstRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                nearby.add(living);
            }
        }

        for (Entity entity : world.getNearbyEntities(center, 14, 14, 14)) {
            if (entity instanceof LivingEntity living && !entity.equals(player) && !nearby.contains(living)) {
                nearby.add(living);
            }
        }

        for (int i = 0; i < ultimateCount; i++) {
            Location spawnLoc = center.clone().add((Math.random() - 0.5) * 4, 1, (Math.random() - 0.5) * 4);
            LivingEntity target = nearby.isEmpty() ? null : nearby.get(i % nearby.size());
            releaseBee(player, spawnLoc, target, ultimateDurationTicks);
        }
    }
}
