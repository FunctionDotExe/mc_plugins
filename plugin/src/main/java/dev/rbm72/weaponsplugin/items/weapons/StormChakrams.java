package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
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

    private static final Color STORM_BLUE = Color.fromRGB(190, 225, 255);
    /** Flat disc item so the "chakram" reads as an actual spinning ring instead of a particle-only ghost. */
    private static final Material CHAKRAM_ICON = Material.MUSIC_DISC_13;

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
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Cyclone")
                .accent(STORM_BLUE)
                .perMeleeHit(configDouble("cyclone-per-hit", 3.0))
                .perDamageDealt(configDouble("cyclone-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("cyclone-per-ability", 9.0))
                .perKill(configDouble("cyclone-per-kill", 10.0))
                .decay(configDouble("cyclone-decay-per-second", 2.0), configDouble("cyclone-decay-grace", 6.0))
                .cooldownFloor(configDouble("cyclone-cooldown-floor", 40.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: throw a chakram that", NamedTextColor.GRAY),
                Component.text("returns to you. Catching it reduces", NamedTextColor.GRAY),
                Component.text("your other cooldowns.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Returning Chakram";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: throw a chakram", NamedTextColor.GRAY),
                Component.text("that arcs lightning between enemies.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Chain Lightning";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: summon orbiting blades", NamedTextColor.GRAY),
                Component.text("that damage nearby enemies.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Orbiting Blades";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: unleash a storm of", NamedTextColor.GRAY),
                Component.text("spinning chakrams around you.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Chakram Storm";
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

        ItemDisplay icon = Fx.spinningIcon(plugin, start, CHAKRAM_ICON, 0.9f, 210, 26.0);

        new BukkitRunnable() {
            Location current = start.clone();
            boolean returning = false;
            int ticks = 0;
            final Set<UUID> alreadyHit = new HashSet<>();

            private void removeIcon() {
                if (icon != null && !icon.isDead()) {
                    icon.remove();
                }
            }

            @Override
            public void run() {
                if (!player.isOnline() || ticks > 200) {
                    removeIcon();
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
                        Fx.burst(current, Particle.ELECTRIC_SPARK, 28, 0.45);
                        Fx.coloredBurst(current, STORM_BLUE, 1.4f, 22, 0.45);
                        Fx.sound(player, readySound(), 0.8f, 1.4f);
                        removeIcon();
                        cancel();
                        return;
                    }
                }

                Fx.point(current, Particle.ELECTRIC_SPARK, 2);
                Fx.coloredBurst(current, STORM_BLUE, 0.5f, 1, 0.05);
                if (icon != null && !icon.isDead()) {
                    icon.teleport(current);
                }
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

        ItemDisplay icon = Fx.spinningIcon(plugin, projectile.getLocation(), CHAKRAM_ICON, 0.8f, 100, 28.0);

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
                Fx.point(projectile.getLocation(), Particle.ELECTRIC_SPARK, 2);
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
        double damage = chainDamage * rarity().statMultiplier();
        Fx.burst(loc, Particle.ELECTRIC_SPARK, 28, 0.45);
        Fx.coloredBurst(loc, STORM_BLUE, 1.4f, 22, 0.45);
        Fx.sound(loc, hitSound(), 1.0f, 1.2f);

        Set<UUID> hit = new HashSet<>();
        if (event.getHitEntity() instanceof LivingEntity direct) {
            direct.damage(damage, shooter);
            Fx.bloodSpray(direct.getLocation().add(0, 1, 0));
            hit.add(direct.getUniqueId());

            for (Entity entity : world.getNearbyEntities(direct.getLocation(), chainRadius, chainRadius, chainRadius)) {
                if (entity instanceof LivingEntity living && !entity.equals(shooter) && hit.add(living.getUniqueId())) {
                    living.damage(damage * 0.6, shooter);
                    Fx.line(direct.getLocation().add(0, 1, 0), living.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 12);
                    Fx.coloredBurst(living.getLocation().add(0, 1, 0), STORM_BLUE, 1.0f, 10, 0.3);
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                }
            }
        }
    }

    @Override
    public void ability3(Player player) {
        World world = player.getWorld();
        Location startCenter = player.getLocation().add(0, 1, 0);
        ItemDisplay[] icons = new ItemDisplay[orbitCount];
        for (int i = 0; i < orbitCount; i++) {
            icons[i] = Fx.spinningIcon(plugin, startCenter, CHAKRAM_ICON, 0.8f, orbitDurationTicks + 5, 30.0);
        }

        new BukkitRunnable() {
            int ticks = 0;
            final Set<UUID> recentlyHit = new HashSet<>();

            @Override
            public void run() {
                if (ticks >= orbitDurationTicks || !player.isOnline()) {
                    for (ItemDisplay icon : icons) {
                        if (icon != null && !icon.isDead()) {
                            icon.remove();
                        }
                    }
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
                    Fx.point(bladeLoc, Particle.ELECTRIC_SPARK, 4);
                    Fx.coloredBurst(bladeLoc, STORM_BLUE, 0.9f, 4, 0.08);
                    if (icons[i] != null && !icons[i].isDead()) {
                        icons[i].teleport(bladeLoc);
                    }

                    for (Entity entity : world.getNearbyEntities(bladeLoc, 0.8, 0.8, 0.8)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player) && recentlyHit.add(living.getUniqueId())) {
                            living.damage(orbitDamagePerTick * rarity().statMultiplier(), player);
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
        Fx.spinningIcon(plugin, player.getLocation().add(0, 2.2, 0), Material.LIGHTNING_ROD, 1.1f, stormDurationTicks, 16.0);
        ItemDisplay[] chakramIcons = new ItemDisplay[6];
        for (int i = 0; i < chakramIcons.length; i++) {
            chakramIcons[i] = Fx.spinningIcon(plugin, player.getLocation().add(0, 1, 0), CHAKRAM_ICON, 0.85f, stormDurationTicks + 5, 32.0);
        }

        new BukkitRunnable() {
            int ticks = 0;
            final Set<UUID> recentlyHit = new HashSet<>();

            @Override
            public void run() {
                if (ticks >= stormDurationTicks || !player.isOnline()) {
                    for (ItemDisplay icon : chakramIcons) {
                        if (icon != null && !icon.isDead()) {
                            icon.remove();
                        }
                    }
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
                    Fx.point(point, Particle.ELECTRIC_SPARK, 5);
                    Fx.coloredBurst(point, STORM_BLUE, 1.2f, 7, 0.16);
                    if (chakramIcons[i] != null && !chakramIcons[i].isDead()) {
                        chakramIcons[i].teleport(point);
                    }

                    for (Entity entity : world.getNearbyEntities(point, 1.0, 1.0, 1.0)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player) && recentlyHit.add(living.getUniqueId())) {
                            living.damage(stormDamagePerTick * rarity().statMultiplier(), player);
                            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                        }
                    }
                }

                // Cosmetic-only outer/inner vortex layers (no entity checks) so the storm reads
                // as a genuine multi-layer vortex with depth instead of one thin ring of points.
                for (int i = 0; i < 12; i++) {
                    double outerAngle = (2 * Math.PI * i / 12) - (ticks * 0.4);
                    double outerRadius = stormRadius * (0.75 + 0.6 * ((ticks % 30) / 30.0));
                    double innerRadius = Math.max(0.3, outerRadius - 1.3);
                    Location outerPoint = center.clone().add(outerRadius * Math.cos(outerAngle), 0.6, outerRadius * Math.sin(outerAngle));
                    Location innerPoint = center.clone().add(innerRadius * Math.cos(outerAngle * 1.4), -0.6, innerRadius * Math.sin(outerAngle * 1.4));
                    Fx.point(outerPoint, Particle.ELECTRIC_SPARK, 3);
                    Fx.coloredBurst(outerPoint, STORM_BLUE, 1.0f, 4, 0.12);
                    Fx.point(innerPoint, Particle.ELECTRIC_SPARK, 3);
                    Fx.coloredBurst(innerPoint, STORM_BLUE, 1.0f, 4, 0.12);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
