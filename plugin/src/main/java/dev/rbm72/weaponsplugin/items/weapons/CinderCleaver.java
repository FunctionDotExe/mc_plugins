package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fire-melee axe: a scorching cone cleave, a hurled meteor fireball, a
 * dash that ignites everything it passes, and a swirling inferno ring that
 * burns nearby foes. Every melee hit sets the victim ablaze. All fire is
 * code-driven ignite ({@code setFireTicks}) and never places fire blocks,
 * so the weapon can't grief terrain.
 */
public final class CinderCleaver extends Weapon {

    private static final Color EMBER = Color.fromRGB(255, 120, 0);
    private static final Color DEEP_FIRE = Color.fromRGB(200, 40, 0);

    private final double slashDamage;
    private final double slashRange;
    private final double slashAngleDegrees;
    private final double meteorDamage;
    private final double meteorRadius;
    private final int meteorRange;
    private final double dashSpeed;
    private final double dashDamage;
    private final double dashHitRadius;
    private final int dashTicks;
    private final double infernoDamage;
    private final double infernoRadius;
    private final int infernoDurationTicks;
    private final int infernoTickInterval;
    private final int burnTicks;

    public CinderCleaver(WeaponsPlugin plugin) {
        super(plugin);
        this.slashDamage = configDouble("slash-damage", 8.0);
        this.slashRange = configDouble("slash-range", 5.0);
        this.slashAngleDegrees = configDouble("slash-angle-degrees", 70.0);
        this.meteorDamage = configDouble("meteor-damage", 10.0);
        this.meteorRadius = configDouble("meteor-radius", 3.0);
        this.meteorRange = configInt("meteor-range", 30);
        this.dashSpeed = configDouble("dash-speed", 1.6);
        this.dashDamage = configDouble("dash-damage", 7.0);
        this.dashHitRadius = configDouble("dash-hit-radius", 1.7);
        this.dashTicks = configInt("dash-ticks", 10);
        this.infernoDamage = configDouble("inferno-damage", 4.0);
        this.infernoRadius = configDouble("inferno-radius", 5.0);
        this.infernoDurationTicks = configInt("inferno-duration-ticks", 100);
        this.infernoTickInterval = configInt("inferno-tick-interval", 10);
        this.burnTicks = configInt("burn-ticks", 80);
    }

    @Override
    public String id() {
        return "cinder_cleaver";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_AXE;
    }

    @Override
    public String displayNameText() {
        return "Cinder Cleaver";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 4.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 7.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 10.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 9.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 40.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Blaze")
                .accent(EMBER)
                .perMeleeHit(configDouble("blaze-per-hit", 7.0))
                .perDamageDealt(configDouble("blaze-per-damage-dealt", 0.45))
                .perAbilityCast(configDouble("blaze-per-ability", 8.0))
                .perKill(configDouble("blaze-per-kill", 11.0))
                .decay(configDouble("blaze-decay-per-second", 2.0), configDouble("blaze-decay-grace", 7.0))
                .cooldownFloor(configDouble("blaze-cooldown-floor", 38.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: Flame Slash — cleave a", NamedTextColor.GRAY),
                Component.text("cone of fire, burning all it strikes.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Flame Slash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Meteor — hurl a", NamedTextColor.GRAY),
                Component.text("fireball that explodes on impact.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Meteor";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Blazing Dash — charge", NamedTextColor.GRAY),
                Component.text("forward, igniting everyone you hit.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Blazing Dash";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Inferno — wreath", NamedTextColor.GRAY),
                Component.text("yourself in a ring of burning fire.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Inferno";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_FIRECHARGE_USE;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_BURN;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_FIRE_AMBIENT;
    }

    @Override
    public void ability1(Player player) {
        Location origin = player.getLocation().add(0, 1, 0);
        Vector facing = player.getLocation().getDirection().setY(0).normalize();
        double damage = slashDamage * rarity().statMultiplier();
        double halfAngle = slashAngleDegrees / 2.0;

        Fx.sound(player, castSound(), 1.0f, 0.8f);
        Fx.sound(player, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.9f);
        for (double d = 1; d <= slashRange; d += 1.0) {
            Location p = origin.clone().add(facing.clone().multiply(d));
            Fx.burst(p, Particle.FLAME, 12, 0.5);
            Fx.coloredBurst(p, EMBER, 1.3f, 8, 0.5);
        }

        for (Entity nearby : player.getNearbyEntities(slashRange, slashRange, slashRange)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            Vector to = entity.getLocation().toVector().subtract(origin.toVector()).setY(0);
            if (to.lengthSquared() > slashRange * slashRange || to.lengthSquared() < 0.01) {
                continue;
            }
            if (Math.toDegrees(to.angle(facing)) <= halfAngle) {
                entity.damage(damage, player);
                entity.setFireTicks(burnTicks);
                Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                Fx.coloredBurst(entity.getLocation().add(0, 1, 0), DEEP_FIRE, 1.6f, 16, 0.4);
                Fx.sound(entity.getLocation(), hitSound(), 1.0f, 1.0f);
            }
        }
    }

    @Override
    public void ability2(Player player) {
        World world = player.getWorld();
        double damage = meteorDamage * rarity().statMultiplier();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location start = player.getEyeLocation().add(direction.clone().multiply(1.0));

        Fx.sound(player, castSound(), 1.0f, 0.6f);
        Fx.sound(player, Sound.ENTITY_GHAST_SHOOT, 1.0f, 0.8f);

        // A real SmallFireball entity carries the visual instead of a hand-rolled particle raycast.
        // It's set non-incendiary and never given real velocity/gravity — we drive its position
        // ourselves each tick — so it can never ignite blocks or explode on its own.
        SmallFireball fireball = world.spawn(start, SmallFireball.class, entity -> {
            entity.setIsIncendiary(false);
            entity.setYield(0f);
            entity.setGravity(false);
            entity.setVelocity(new Vector(0, 0, 0));
        });

        new BukkitRunnable() {
            final Location cursor = start.clone();
            int steps = 0;

            @Override
            public void run() {
                if (!player.isOnline() || steps >= meteorRange) {
                    if (!fireball.isDead()) {
                        fireball.remove();
                    }
                    cancel();
                    return;
                }
                cursor.add(direction.clone().multiply(1.0));
                steps++;
                if (!fireball.isDead()) {
                    fireball.teleport(cursor);
                }
                Fx.point(cursor, Particle.FLAME, 6);
                Fx.point(cursor, Particle.LAVA, 1);
                Fx.coloredBurst(cursor, EMBER, 1.2f, 4, 0.15);

                boolean blocked = cursor.getBlock().getType().isSolid();
                LivingEntity hit = null;
                for (Entity nearby : world.getNearbyEntities(cursor, 1.2, 1.2, 1.2)) {
                    if (nearby instanceof LivingEntity entity && !entity.getUniqueId().equals(player.getUniqueId())) {
                        hit = entity;
                        break;
                    }
                }
                if (blocked || hit != null) {
                    if (!fireball.isDead()) {
                        fireball.remove();
                    }
                    detonate(player, cursor, damage);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void detonate(Player player, Location loc, double damage) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3, 0, 0, 0, 0);
        Fx.burst(loc, Particle.FLAME, 40, meteorRadius * 0.4);
        Fx.coloredBurst(loc, DEEP_FIRE, 2.0f, 30, meteorRadius * 0.4);
        Fx.sound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
        Fx.sound(loc, hitSound(), 1.0f, 1.1f);
        for (Entity nearby : world.getNearbyEntities(loc, meteorRadius, meteorRadius, meteorRadius)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            entity.damage(damage, player);
            entity.setFireTicks(burnTicks);
            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability3(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(dashSpeed).setY(0.25));
        double damage = dashDamage * rarity().statMultiplier();
        Set<UUID> alreadyHit = new HashSet<>();

        Fx.sound(player, castSound(), 1.0f, 1.0f);
        Fx.sound(player, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.2f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= dashTicks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation().add(0, 1, 0);
                Fx.trail(loc, Particle.FLAME, 14, 0.4, 0.03);
                Fx.trail(loc, Particle.LAVA, 3, 0.3, 0.01);
                for (Entity nearby : player.getNearbyEntities(dashHitRadius, dashHitRadius, dashHitRadius)) {
                    if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())
                            || !alreadyHit.add(entity.getUniqueId())) {
                        continue;
                    }
                    entity.damage(damage, player);
                    entity.setFireTicks(burnTicks);
                    Fx.coloredBurst(entity.getLocation().add(0, 1, 0), EMBER, 1.6f, 18, 0.5);
                    Fx.bloodSpray(entity.getLocation().add(0, 1.2, 0));
                    Fx.sound(entity.getLocation(), hitSound(), 1.0f, 1.0f);
                    entity.setVelocity(direction.clone().multiply(1.1).setY(0.35));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        double damage = infernoDamage * rarity().statMultiplier();
        Fx.sound(player, castSound(), 1.2f, 0.5f);
        Fx.sound(player, Sound.ENTITY_BLAZE_AMBIENT, 1.0f, 0.6f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= infernoDurationTicks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                Fx.coloredRing(loc, EMBER, 1.3f, infernoRadius, 28, ticks * 0.2);
                Fx.helixFrame(loc, Particle.FLAME, infernoRadius * 0.6, 4, ticks * 0.5, (ticks % 20) * 0.1);
                if (ticks % infernoTickInterval == 0) {
                    Fx.ring(loc, Particle.FLAME, infernoRadius, 24);
                    Fx.sound(loc, Sound.ENTITY_BLAZE_BURN, 0.8f, 0.8f);
                    for (Entity nearby : player.getNearbyEntities(infernoRadius, infernoRadius, infernoRadius)) {
                        if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                            continue;
                        }
                        entity.damage(damage, player);
                        entity.setFireTicks(burnTicks);
                        Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        // Every strike from the cleaver sets the target ablaze.
        victim.setFireTicks(burnTicks);
        Fx.coloredBurst(victim.getLocation().add(0, 1, 0), EMBER, 1.2f, 8, 0.3);
        Fx.sound(victim.getLocation(), hitSound(), 0.6f, 1.2f);
    }
}
