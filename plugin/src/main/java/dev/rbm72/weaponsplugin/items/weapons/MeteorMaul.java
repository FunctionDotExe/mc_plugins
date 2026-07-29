package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Two-stage impact hammer: every ability is built on real, gravity-driven physics objects
 * ({@link FallingBlock} meteors, yanked-up ground debris) instead of particle-only shockwaves —
 * the signature feel is "launch violently up, crash violently back down."
 */
public final class MeteorMaul extends Weapon {

    private static final Color EMBER = Color.fromRGB(255, 90, 20);

    private final double skyCrashUpVelocity;
    private final double skyCrashDamage;
    private final double skyCrashRadius;
    private final int meteorCount;
    private final double meteorDamage;
    private final double meteorRadius;
    private final double meteorSpawnHeight;
    private final double aftershockDamage;
    private final double aftershockRadius;
    private final double ultimateUpVelocity;
    private final int ultimateMeteorCount;
    private final double ultimateDamage;
    private final double ultimateRadius;

    public MeteorMaul(WeaponsPlugin plugin) {
        super(plugin);
        this.skyCrashUpVelocity = configDouble("sky-crash-up-velocity", 1.5);
        this.skyCrashDamage = configDouble("sky-crash-damage", 9.0);
        this.skyCrashRadius = configDouble("sky-crash-radius", 4.5);
        this.meteorCount = configInt("meteor-count", 4);
        this.meteorDamage = configDouble("meteor-damage", 7.0);
        this.meteorRadius = configDouble("meteor-radius", 2.5);
        this.meteorSpawnHeight = configDouble("meteor-spawn-height", 14.0);
        this.aftershockDamage = configDouble("aftershock-damage", 5.0);
        this.aftershockRadius = configDouble("aftershock-radius", 3.0);
        this.ultimateUpVelocity = configDouble("ultimate-up-velocity", 2.2);
        this.ultimateMeteorCount = configInt("ultimate-meteor-count", 8);
        this.ultimateDamage = configDouble("ultimate-damage", 15.0);
        this.ultimateRadius = configDouble("ultimate-radius", 7.0);
    }

    @Override
    public String id() {
        return "meteor_maul";
    }

    @Override
    public Material material() {
        return Material.MACE;
    }

    @Override
    public String displayNameText() {
        return "Meteor Maul";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 5.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 8.0);
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
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Impact")
                .accent(EMBER)
                .perMeleeHit(configDouble("impact-per-hit", 6.0))
                .perDamageDealt(configDouble("impact-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("impact-per-ability", 8.0))
                .perKill(configDouble("impact-per-kill", 12.0))
                .decay(configDouble("impact-decay-per-second", 2.0), configDouble("impact-decay-grace", 7.0))
                .cooldownFloor(configDouble("impact-cooldown-floor", 50.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: launch straight up,", NamedTextColor.GRAY),
                Component.text("then crash back down, cratering", NamedTextColor.GRAY),
                Component.text("the ground beneath you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Sky Crash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: call meteors", NamedTextColor.GRAY),
                Component.text("down from the sky onto wherever", NamedTextColor.GRAY),
                Component.text("you're looking.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Meteor Call";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: short forward lunge", NamedTextColor.GRAY),
                Component.text("that ends in a quick ground slam.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Aftershock";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: launch violently", NamedTextColor.GRAY),
                Component.text("skyward while meteors rain down", NamedTextColor.GRAY),
                Component.text("below, then crash back into a", NamedTextColor.GRAY),
                Component.text("world-ending shockwave.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "World Ender Descent";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_TRIDENT_RIPTIDE_1;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_EXPLODE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_ANVIL_LAND;
    }

    /**
     * Yanks {@code count} nearby ground blocks into the air as real, non-dropping
     * {@link FallingBlock} debris that arcs up then falls back — the "crash throws real rubble"
     * beat that pure particles can't sell. Self-reverting, so it never leaves stray terrain.
     */
    private void debrisBurst(World world, Location center, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(0.5, radius);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            Block ground = world.getHighestBlockAt(x, z);
            Material type = ground.getType();
            if (type.isAir() || type == Material.BEDROCK || type == Material.BARRIER || ground.isLiquid()) {
                continue;
            }
            BlockData data = ground.getBlockData();
            FallingBlock debris = world.spawnFallingBlock(ground.getLocation().add(0.5, 0.3, 0.5), data);
            debris.setDropItem(false);
            debris.setCancelDrop(true);
            debris.setHurtEntities(false);
            debris.setPersistent(false);
            debris.setVelocity(new Vector(
                    ThreadLocalRandom.current().nextDouble(-0.2, 0.2),
                    ThreadLocalRandom.current().nextDouble(0.5, 0.9),
                    ThreadLocalRandom.current().nextDouble(-0.2, 0.2)));
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (debris.isValid()) {
                        debris.remove();
                    }
                }
            }.runTaskLater(plugin, 40L);
        }
    }

    private void slam(Player player, Location loc, double damage, double radius) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
        Fx.expandingRings(plugin, loc, Particle.CLOUD, radius * 1.2, 4, 3L);
        Fx.coloredBurst(loc.clone().add(0, 0.2, 0), EMBER, 2.2f, 40, 1.2);
        Fx.blockBurst(loc, Material.BLACKSTONE, 26, 1.0);
        debrisBurst(world, loc, 7, radius);

        for (Entity nearby : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            entity.damage(damage, player);
            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
            Fx.sound(entity.getLocation(), hitSound(), 0.6f, 1.4f);

            Vector away = entity.getLocation().toVector().subtract(loc.toVector());
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            entity.setVelocity(away.normalize().multiply(1.2).setY(0.6));
        }
    }

    private void leapThenSlam(Player player, Vector launchVelocity, double damage, double radius, int maxTicks) {
        player.setVelocity(launchVelocity);
        Fx.sound(player, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 0.6f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                Fx.point(player.getLocation(), Particle.FLAME, 3);
                if ((ticks > 3 && player.isOnGround()) || ticks >= maxTicks) {
                    cancel();
                    slam(player, player.getLocation(), damage, radius);
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability1(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        Vector launch = direction.multiply(0.3).setY(skyCrashUpVelocity);
        leapThenSlam(player, launch, skyCrashDamage * rarity().statMultiplier(), skyCrashRadius, 40);
    }

    /** Falls under real gravity via {@link FallingBlock}'s own physics; this just polls for landing. */
    private void meteor(World world, Location target, double damage, double radius) {
        double ox = ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
        double oz = ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
        Location spawnAt = target.clone().add(ox, meteorSpawnHeight, oz);
        FallingBlock meteor = world.spawnFallingBlock(spawnAt, Material.MAGMA_BLOCK.createBlockData());
        meteor.setDropItem(false);
        meteor.setCancelDrop(true);
        meteor.setHurtEntities(false);
        meteor.setPersistent(false);
        meteor.setVelocity(new Vector(0, -0.2, 0));

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!meteor.isValid() || meteor.isOnGround() || ticks >= 100) {
                    if (meteor.isValid()) {
                        Location impact = meteor.getLocation();
                        Fx.coloredBurst(impact, EMBER, 2.0f, 34, radius * 0.5);
                        Fx.expandingRings(plugin, impact, Particle.FLAME, radius, 3, 2L);
                        Fx.sound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.0f);
                        for (Entity nearby : world.getNearbyEntities(impact, radius, radius, radius)) {
                            if (nearby instanceof LivingEntity living) {
                                living.damage(damage, meteor);
                                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                            }
                        }
                        meteor.remove();
                    }
                    cancel();
                    return;
                }
                Fx.trail(meteor.getLocation(), Particle.FLAME, 3, 0.1, 0.01);
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void ability2(Player player) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        Block targetBlock = player.getTargetBlockExact(40, FluidCollisionMode.NEVER);
        Location target = targetBlock != null
                ? targetBlock.getLocation().add(0.5, 1, 0.5)
                : player.getLocation().add(player.getLocation().getDirection().normalize().multiply(10));

        Fx.sound(player, Sound.AMBIENT_CAVE, 1.0f, 0.6f);
        double damage = meteorDamage * rarity().statMultiplier();
        for (int i = 0; i < meteorCount; i++) {
            int delay = i * 4;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> meteor(world, target, damage, meteorRadius), delay);
        }
    }

    @Override
    public void ability3(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        Vector launch = direction.multiply(1.1).setY(0.35);
        player.setVelocity(launch);
        Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.1f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                Fx.point(player.getLocation(), Particle.CRIT, 4);
                if ((ticks > 2 && player.isOnGround()) || ticks >= 14) {
                    cancel();
                    slam(player, player.getLocation(), aftershockDamage * rarity().statMultiplier(), aftershockRadius);
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        Location takeoff = player.getLocation();
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.7f);

        double meteorDamageEach = ultimateDamage * 0.4 * rarity().statMultiplier();
        for (int i = 0; i < ultimateMeteorCount; i++) {
            int delay = 12 + i * 3;
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> meteor(world, takeoff, meteorDamageEach, meteorRadius), delay);
        }

        Vector launch = new Vector(0, ultimateUpVelocity, 0);
        leapThenSlam(player, launch, ultimateDamage * rarity().statMultiplier(), ultimateRadius, 60);
    }
}
