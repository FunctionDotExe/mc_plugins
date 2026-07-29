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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/** Holy greatsword: sunbeam, solar explosion, radiant cleave, and a light-pillar ultimate. Strong vs. undead. */
public final class SolarGreatsword extends Weapon {

    private static final Color SOLAR_GOLD = Color.fromRGB(255, 214, 120);

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
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Zenith")
                .accent(SOLAR_GOLD)
                .perMeleeHit(configDouble("zenith-per-hit", 6.0))
                .perDamageDealt(configDouble("zenith-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("zenith-per-ability", 8.0))
                .perKill(configDouble("zenith-per-kill", 12.0))
                .decay(configDouble("zenith-decay-per-second", 2.0), configDouble("zenith-decay-grace", 7.0))
                .cooldownFloor(configDouble("zenith-cooldown-floor", 50.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: fire a beam of", NamedTextColor.GRAY),
                Component.text("sunlight forward.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Sunbeam";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: detonate a solar", NamedTextColor.GRAY),
                Component.text("explosion around you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Solar Flare";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: radiant cleave, blinding", NamedTextColor.GRAY),
                Component.text("enemies in front of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Radiant Cleave";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: summon a pillar of", NamedTextColor.GRAY),
                Component.text("light. Extra effective vs. undead.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Pillar Of Light";
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
            Fx.coloredBurst(victim.getLocation().add(0, 1, 0), SOLAR_GOLD, 0.7f, 6, 0.25);
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

        Vector beamDir = eye.getDirection().normalize();
        Vector beamPerp = new Vector(-beamDir.getZ(), 0, beamDir.getX()).normalize().multiply(0.35);
        Fx.line(eye, end, Particle.END_ROD, 34);
        Fx.line(eye.clone().add(beamPerp), end.clone().add(beamPerp), Particle.END_ROD, 28);
        Fx.line(eye.clone().subtract(beamPerp), end.clone().subtract(beamPerp), Particle.END_ROD, 28);
        Fx.line(eye, end, Particle.FLAME, 18);
        Fx.coloredBurst(end.clone(), SOLAR_GOLD, 1.4f, 30, 0.45);
        Fx.coloredBurst(eye.clone(), SOLAR_GOLD, 1.0f, 16, 0.3);

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
        Fx.burst(center.clone().add(0, 1, 0), Particle.FLAME, 75, explosionRadius * 0.55);
        Fx.burst(center.clone().add(0, 1, 0), Particle.END_ROD, 50, explosionRadius * 0.55);
        Fx.coloredBurst(center.clone().add(0, 1, 0), SOLAR_GOLD, 1.6f, 55, explosionRadius * 0.6);
        Fx.expandingRings(plugin, center.clone().add(0, 0.1, 0), Particle.END_ROD, explosionRadius, 5, 2L);

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
        Fx.trail(origin.clone().add(0, 1, 0), Particle.END_ROD, 40, 0.75, 0.06);
        Fx.coloredBurst(origin.clone().add(0, 1, 0).add(direction.clone().multiply(cleaveRange * 0.5)), SOLAR_GOLD, 1.4f, 32, cleaveRange * 0.45);

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
            StatusEffectManager.apply(living, PotionEffectType.BLINDNESS, 60, 0);
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
        Fx.glowPillar(plugin, target.clone(), Material.GLOWSTONE, 0.45f, 18f, pillarDurationTicks);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= pillarDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                angle += 0.45;
                double height = ticks % 14;
                Fx.helixFrame(target, Particle.END_ROD, pillarRadius * 0.7, 12, angle, height);
                Fx.helixFrame(target, Particle.END_ROD, pillarRadius * 1.2, 16, -angle * 0.8, height + 0.5);
                Fx.trail(target.clone().add(0, height, 0), Particle.END_ROD, 10, 0.4, 0.02);
                Fx.coloredBurst(target.clone().add(0, height, 0), SOLAR_GOLD, 1.3f, 12, 0.45);

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
