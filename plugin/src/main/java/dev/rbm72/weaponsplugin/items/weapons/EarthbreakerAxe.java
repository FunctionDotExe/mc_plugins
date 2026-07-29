package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
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
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Earth-themed axe: ground-crack line, temporary stone wall, boulder throw, and an expanding-earthquake ultimate. */
public final class EarthbreakerAxe extends Weapon {

    private static final Color EARTH_BROWN = Color.fromRGB(120, 80, 45);

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
    private final int eruptDurationTicks;

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
        this.eruptDurationTicks = configInt("erupt-duration-ticks", 30);
    }

    private NamespacedKey knockbackKey() {
        return new NamespacedKey(plugin, "earthbreaker_axe_kb_resist");
    }

    /** FALLING_DUST needs a BlockData argument, so it bypasses the Fx helpers (see Fx.bloodSpray's direct spawnParticle for the same pattern). */
    private void earthDust(Location loc, Material material, int count, double spreadX, double spreadY, double spreadZ) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.FALLING_DUST, loc, (int) Math.ceil(count * 3.0), spreadX * 1.6, spreadY * 1.6, spreadZ * 1.6, 0, material.createBlockData());
    }

    /**
     * Real, physical ground-up: yanks the top block of the {@code (x, z)} column into the air as an
     * actual {@link FallingBlock} (no drop, no fall damage), leaving the column briefly empty, then
     * restores the original block after {@code durationTicks} — the same "temporary real edit, auto
     * revert" trick {@link #ability2} already uses for the stone wall. Skips air/bedrock/barrier/liquid
     * so it never eats the world or launches water/lava.
     */
    private void erupt(World world, int x, int z, int durationTicks) {
        Block ground = world.getHighestBlockAt(x, z);
        Material type = ground.getType();
        if (type.isAir() || type == Material.BEDROCK || type == Material.BARRIER || ground.isLiquid()) {
            return;
        }
        BlockData data = ground.getBlockData();
        ground.setType(Material.AIR, false);

        FallingBlock debris = world.spawn(ground.getLocation().add(0.5, 0.2, 0.5), FallingBlock.class,
                fb -> fb.setBlockData(data));
        debris.setDropItem(false);
        debris.setCancelDrop(true);
        debris.setHurtEntities(false);
        debris.setPersistent(false);
        double vx = ThreadLocalRandom.current().nextDouble(-0.15, 0.15);
        double vz = ThreadLocalRandom.current().nextDouble(-0.15, 0.15);
        debris.setVelocity(new Vector(vx, ThreadLocalRandom.current().nextDouble(0.45, 0.75), vz));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (debris.isValid()) {
                debris.remove();
            }
            if (ground.getType().isAir()) {
                ground.setBlockData(data, false);
            }
        }, durationTicks);
    }

    /** Same ring math as Fx.ring, but for FALLING_DUST which needs BlockData and can't go through Fx.ring. */
    private void earthRing(Location center, double radius, int points, double angleOffsetRadians, Material material) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        BlockData data = material.createBlockData();
        for (int i = 0; i < points; i++) {
            double angle = angleOffsetRadians + (2 * Math.PI * i) / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            world.spawnParticle(Particle.FALLING_DUST, x, center.getY(), z, 1, 0, 0, 0, 0, data);
        }
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
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Tremor")
                .accent(EARTH_BROWN)
                .perMeleeHit(configDouble("tremor-per-hit", 6.0))
                .perDamageDealt(configDouble("tremor-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("tremor-per-ability", 8.0))
                .perKill(configDouble("tremor-per-kill", 10.0))
                .decay(configDouble("tremor-decay-per-second", 2.0), configDouble("tremor-decay-grace", 7.0))
                .cooldownFloor(configDouble("tremor-cooldown-floor", 42.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: split the ground", NamedTextColor.GRAY),
                Component.text("ahead, damaging and slowing", NamedTextColor.GRAY),
                Component.text("enemies caught in the crack.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Fissure";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: raise a", NamedTextColor.GRAY),
                Component.text("temporary stone wall in front", NamedTextColor.GRAY),
                Component.text("of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Stone Wall";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: throw a boulder that", NamedTextColor.GRAY),
                Component.text("explodes on impact.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Boulder Toss";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: trigger an", NamedTextColor.GRAY),
                Component.text("earthquake with expanding cracks.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Earthquake";
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
        Vector horizontal = new Vector(direction.getX(), 0, direction.getZ());
        Vector side = horizontal.lengthSquared() > 1e-6
                ? new Vector(-horizontal.getZ(), 0, horizontal.getX()).normalize().multiply(0.35)
                : new Vector(0.35, 0, 0);

        for (double d = 1; d <= crackRange; d += 1) {
            Location point = origin.clone().add(direction.clone().multiply(d));
            earthDust(point, Material.STONE, 14, 0.35, 0.25, 0.35);
            earthDust(point, Material.DIRT, 8, 0.3, 0.2, 0.3);
            earthDust(point.clone().add(side), Material.STONE, 6, 0.2, 0.15, 0.2);
            earthDust(point.clone().subtract(side), Material.STONE, 6, 0.2, 0.15, 0.2);
            Fx.coloredBurst(point, Color.fromRGB(101, 67, 33), 1.8f, 10, 0.35);
            erupt(world, point.getBlockX(), point.getBlockZ(), eruptDurationTicks);

            for (Entity entity : world.getNearbyEntities(point, 1.2, 1.2, 1.2)) {
                if (entity instanceof LivingEntity living && !entity.equals(player) && alreadyHit.add(living.getUniqueId())) {
                    living.damage(damage, player);
                    StatusEffectManager.apply(living, PotionEffectType.SLOWNESS, crackSlowTicks, 3);
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
                    earthDust(block.getLocation().add(0.5, 0.5, 0.5), Material.STONE, 22, 0.4, 0.55, 0.4);
                }
            }
        }
        Fx.coloredBurst(base.getLocation().add(0.5, wallHeight * 0.5, 0.5), Color.fromRGB(120, 120, 120), 2.2f, 34, wallLength * 0.5);
        Fx.sound(player, Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < placed.size(); i++) {
                Block block = placed.get(i);
                earthDust(block.getLocation().add(0.5, 0.5, 0.5), Material.STONE, 16, 0.35, 0.35, 0.35);
                block.setBlockData(originalData.get(i));
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

        var icon = Fx.spinningIcon(plugin, projectile.getLocation(), Material.COBBLESTONE, 1.3f, 60, 14.0);

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
                earthDust(projectile.getLocation(), Material.STONE, 9, 0.2, 0.2, 0.2);
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
        double damage = boulderDamage * rarity().statMultiplier();
        earthDust(loc, Material.STONE, 85, boulderRadius * 0.45, boulderRadius * 0.4, boulderRadius * 0.45);
        earthDust(loc, Material.DIRT, 45, boulderRadius * 0.4, boulderRadius * 0.3, boulderRadius * 0.4);
        Fx.coloredBurst(loc, Color.fromRGB(101, 67, 33), 2.4f, 42, boulderRadius * 0.55);
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

        int crackLines = 20;
        for (int i = 0; i < crackLines; i++) {
            double a = (2 * Math.PI * i) / crackLines;
            Location end = center.clone().add(Math.cos(a) * quakeMaxRadius, 0, Math.sin(a) * quakeMaxRadius);
            Fx.line(center, end, Particle.CRIT, 26);
            Fx.line(center, end, Particle.LAVA, 12);
            earthDust(end, Material.STONE, 14, 0.35, 0.4, 0.35);
        }

        new BukkitRunnable() {
            int ring = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ring >= quakeRings || !player.isOnline()) {
                    cancel();
                    return;
                }
                double radius = quakeMaxRadius * (ring + 1) / (double) quakeRings;
                earthRing(center, radius, 40 + ring * 10, angle, Material.STONE);
                earthRing(center, radius + 0.4, 30 + ring * 8, angle + 0.15, Material.COBBLESTONE);
                earthRing(center, radius * 0.85, 26 + ring * 8, -angle, Material.DIRT);
                earthRing(center, radius * 0.85 - 0.4, 20 + ring * 6, -angle - 0.15, Material.COARSE_DIRT);
                Fx.coloredBurst(center.clone().add(0, 0.1, 0), Color.fromRGB(101, 67, 33), 2.4f, 26, radius * 0.7);
                Fx.coloredBurst(center.clone().add(0, 0.4, 0), Color.fromRGB(140, 90, 40), 1.8f, 16, radius * 0.5);

                // Eruption columns punching upward around the current ring so the ground genuinely
                // erupts across the growing area instead of just tracing a thin dust outline.
                int eruptions = 8 + ring * 2;
                for (int i = 0; i < eruptions; i++) {
                    double eruptAngle = angle + (2 * Math.PI * i) / eruptions;
                    Location eruptLoc = center.clone().add(Math.cos(eruptAngle) * radius, 0, Math.sin(eruptAngle) * radius);
                    earthDust(eruptLoc, Material.STONE, 12, 0.3, 0.7, 0.3);
                    erupt(world, eruptLoc.getBlockX(), eruptLoc.getBlockZ(), eruptDurationTicks);
                }

                for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player) && alreadyHit.add(living.getUniqueId())) {
                        living.damage(damage, player);
                        living.setVelocity(living.getVelocity().setY(0.5));
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
                angle += 0.4;
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }
}
