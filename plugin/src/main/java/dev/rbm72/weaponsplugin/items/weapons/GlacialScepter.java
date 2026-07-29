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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Ice-mage scepter: a frostbolt, a self-centered freeze nova, a conjured ice
 * wall, and a channeled blizzard cone. Every melee hit leaves a brief chill.
 */
public final class GlacialScepter extends Weapon {

    private static final Color FROST = Color.fromRGB(150, 220, 255);

    private final double frostboltDamage;
    private final double frostboltSpeed;
    private final int frostboltSlowTicks;
    private final int frostboltSlowAmplifier;
    private final double novaDamage;
    private final double novaRadius;
    private final int novaSlowTicks;
    private final int novaSlowAmplifier;
    private final double wallDistance;
    private final int wallLength;
    private final int wallHeight;
    private final int wallDurationTicks;
    private final double blizzardDamagePerTick;
    private final double blizzardRange;
    private final double blizzardConeDegrees;
    private final int blizzardDurationTicks;
    private final int blizzardSlowTicks;
    private final int passiveSlowTicks;
    private final int passiveSlowAmplifier;

    public GlacialScepter(WeaponsPlugin plugin) {
        super(plugin);
        this.frostboltDamage = configDouble("frostbolt-damage", 7.0);
        this.frostboltSpeed = configDouble("frostbolt-speed", 2.0);
        this.frostboltSlowTicks = configInt("frostbolt-slow-ticks", 80);
        this.frostboltSlowAmplifier = configInt("frostbolt-slow-amplifier", 1);
        this.novaDamage = configDouble("nova-damage", 8.0);
        this.novaRadius = configDouble("nova-radius", 4.5);
        this.novaSlowTicks = configInt("nova-slow-ticks", 100);
        this.novaSlowAmplifier = configInt("nova-slow-amplifier", 2);
        this.wallDistance = configDouble("wall-distance", 3.0);
        this.wallLength = configInt("wall-length", 5);
        this.wallHeight = configInt("wall-height", 2);
        this.wallDurationTicks = configInt("wall-duration-ticks", 100);
        this.blizzardDamagePerTick = configDouble("blizzard-damage-per-tick", 2.0);
        this.blizzardRange = configDouble("blizzard-range", 8.0);
        this.blizzardConeDegrees = configDouble("blizzard-cone-degrees", 60.0);
        this.blizzardDurationTicks = configInt("blizzard-duration-ticks", 60);
        this.blizzardSlowTicks = configInt("blizzard-slow-ticks", 60);
        this.passiveSlowTicks = configInt("passive-slow-ticks", 40);
        this.passiveSlowAmplifier = configInt("passive-slow-amplifier", 0);
    }

    @Override
    public String id() {
        return "glacial_scepter";
    }

    @Override
    public Material material() {
        return Material.BLAZE_ROD;
    }

    @Override
    public String displayNameText() {
        return "Glacial Scepter";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 4.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 8.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 12.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 40.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Chill")
                .accent(FROST)
                .perMeleeHit(configDouble("chill-per-hit", 4.0))
                .perDamageDealt(configDouble("chill-per-damage-dealt", 0.45))
                .perAbilityCast(configDouble("chill-per-ability", 9.0))
                .perKill(configDouble("chill-per-kill", 10.0))
                .decay(configDouble("chill-decay-per-second", 2.0), configDouble("chill-decay-grace", 6.0))
                .cooldownFloor(configDouble("chill-cooldown-floor", 36.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: fire a frostbolt that", NamedTextColor.GRAY),
                Component.text("damages and slows on impact.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Frostbolt";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: erupt a freezing", NamedTextColor.GRAY),
                Component.text("nova around you, chilling all nearby.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Frost Nova";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: conjure a wall of ice", NamedTextColor.GRAY),
                Component.text("in front of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Ice Wall";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: channel a blizzard", NamedTextColor.GRAY),
                Component.text("cone that roots and shreds enemies.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Blizzard";
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_GLASS_BREAK;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_HURT_FREEZE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_GLASS_PLACE;
    }

    private NamespacedKey frostboltDamageKey() {
        return new NamespacedKey(plugin, "glacial_scepter_frostbolt_damage");
    }

    @Override
    public void ability1(Player player) {
        double damage = frostboltDamage * rarity().statMultiplier();
        Snowball bolt = player.launchProjectile(Snowball.class, player.getLocation().getDirection().multiply(frostboltSpeed));
        bolt.setGravity(false);
        bolt.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        bolt.getPersistentDataContainer().set(frostboltDamageKey(), PersistentDataType.DOUBLE, damage);
        Fx.coloredBurst(player.getEyeLocation(), FROST, 1.6f, 16, 0.3);
        Fx.sound(player, castSound(), 1.0f, 1.3f);
        var icon = Fx.spinningIcon(plugin, bolt.getLocation(), Material.PACKED_ICE, 0.5f, 60, 26.0);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!bolt.isValid()) {
                    if (icon != null && !icon.isDead()) {
                        icon.remove();
                    }
                    cancel();
                    return;
                }
                Fx.point(bolt.getLocation(), Particle.SNOWFLAKE, 4);
                Fx.coloredBurst(bolt.getLocation(), FROST, 1.2f, 4, 0.06);
                if (icon != null && !icon.isDead()) {
                    icon.teleport(bolt.getLocation());
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        Double tagged = event.getEntity().getPersistentDataContainer().get(frostboltDamageKey(), PersistentDataType.DOUBLE);
        double damage = tagged != null ? tagged : frostboltDamage * rarity().statMultiplier();
        Fx.burst(loc, Particle.SNOWFLAKE, 30, 0.5);
        Fx.coloredBurst(loc, FROST, 2.0f, 30, 0.6);
        Fx.sound(loc, hitSound(), 0.9f, 1.2f);
        if (event.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, shooter);
            StatusEffectManager.apply(target, PotionEffectType.SLOWNESS, frostboltSlowTicks, frostboltSlowAmplifier);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = novaDamage * rarity().statMultiplier();
        Location loc = player.getLocation();
        Fx.expandingRings(plugin, loc, Particle.SNOWFLAKE, novaRadius, 4, 2L);
        Fx.coloredBurst(loc.clone().add(0, 1, 0), FROST, 2.0f, 40, novaRadius / 2);
        Fx.burst(loc.clone().add(0, 1, 0), Particle.ITEM_SNOWBALL, 30, novaRadius / 2);
        Fx.sound(player, castSound(), 1.0f, 0.7f);
        Fx.sound(player, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.8f, 1.0f);
        for (Entity nearby : player.getNearbyEntities(novaRadius, novaRadius, novaRadius)) {
            if (nearby instanceof LivingEntity target && !target.getUniqueId().equals(player.getUniqueId())) {
                target.damage(damage, player);
                StatusEffectManager.apply(target, PotionEffectType.SLOWNESS, novaSlowTicks, novaSlowAmplifier);
                Fx.bloodSpray(target.getLocation().add(0, 1, 0));
            }
        }
    }

    private BlockFace directionToFace(Vector direction) {
        if (Math.abs(direction.getX()) > Math.abs(direction.getZ())) {
            return direction.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return direction.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    @Override
    public void ability3(Player player) {
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
        Block base = origin.getBlock().getRelative(face, (int) Math.round(wallDistance));
        int halfLength = wallLength / 2;

        Fx.sound(player, Sound.BLOCK_GLASS_PLACE, 1.0f, 0.6f);
        Fx.sound(player, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.7f, 1.2f);

        // Real, solid ice blocks (not a cosmetic prop) so the wall actually stops movement and
        // projectiles, then reverts to whatever was there after wallDurationTicks — same
        // temporary-real-edit trick EarthbreakerAxe's Stone Wall uses.
        for (int length = -halfLength; length <= halfLength; length++) {
            for (int height = 0; height < wallHeight; height++) {
                Block block = base.getRelative(side, length).getRelative(0, height, 0);
                if (block.getType().isAir()) {
                    originalData.add(block.getBlockData());
                    block.setType(Material.PACKED_ICE);
                    placed.add(block);
                    Fx.coloredBurst(block.getLocation().add(0.5, 0.5, 0.5), FROST, 1.4f, 10, 0.3);
                    Fx.point(block.getLocation().add(0.5, 1.0, 0.5), Particle.SNOWFLAKE, 6);
                }
            }
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < placed.size(); i++) {
                Block block = placed.get(i);
                Fx.point(block.getLocation().add(0.5, 0.5, 0.5), Particle.SNOWFLAKE, 10);
                block.setBlockData(originalData.get(i));
            }
        }, wallDurationTicks);
    }

    @Override
    public void ultimate(Player player) {
        double damagePerTick = blizzardDamagePerTick * rarity().statMultiplier();
        double cosHalf = Math.cos(Math.toRadians(blizzardConeDegrees / 2));
        Fx.sound(player, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.5f);
        Fx.sound(player, Sound.WEATHER_RAIN, 1.0f, 0.6f);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= blizzardDurationTicks) {
                    cancel();
                    return;
                }
                Vector dir = player.getLocation().getDirection().setY(0).normalize();
                Location eye = player.getEyeLocation();
                // Sweeping snow cone in front of the caster.
                for (double d = 1; d <= blizzardRange; d += 1.5) {
                    Location point = eye.clone().add(dir.clone().multiply(d));
                    Fx.point(point, Particle.SNOWFLAKE, 6);
                    Fx.coloredBurst(point, FROST, 1.2f, 4, 0.4);
                }
                // A couple of real (cosmetic) ice props riding the cone every few ticks.
                if (ticks % 6 == 0) {
                    for (double d = 3.0; d <= blizzardRange; d += 4.0) {
                        Fx.glowPillar(plugin, eye.clone().add(dir.clone().multiply(d)), Material.PACKED_ICE, 1.0f, 2.0f, 10);
                    }
                }
                if (ticks % 5 == 0) {
                    for (Entity nearby : player.getNearbyEntities(blizzardRange, blizzardRange, blizzardRange)) {
                        if (!(nearby instanceof LivingEntity target) || target.getUniqueId().equals(player.getUniqueId())) {
                            continue;
                        }
                        Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                        if (toTarget.lengthSquared() < 0.0001 || toTarget.normalize().dot(dir) >= cosHalf) {
                            target.damage(damagePerTick, player);
                            StatusEffectManager.apply(target, PotionEffectType.SLOWNESS, blizzardSlowTicks, 3);
                            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        StatusEffectManager.apply(victim, PotionEffectType.SLOWNESS, passiveSlowTicks, passiveSlowAmplifier);
        Fx.coloredBurst(victim.getLocation().add(0, 1, 0), FROST, 1.2f, 8, 0.3);
        Fx.point(victim.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 5);
    }
}
