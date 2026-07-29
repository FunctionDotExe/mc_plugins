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
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Overwhelming-force lightning maul: a two-wave ground slam, a self-charging
 * static overload that also blasts nearby foes, a lightning-trailed dash
 * that punishes anything in its path, and a devastating charged strike that
 * calls down one colossal bolt on the aim point. Melee hits have a chance to
 * arc a stray spark into the victim, needling them with a brief shock.
 */
public final class ThunderHammer extends Weapon {

    private static final Color STRIKE_COLOR = Color.fromRGB(225, 240, 255);

    private final double abilityDamage;
    private final double radius;
    private final double overchargeDamage;
    private final double overchargeRadius;
    private final int overchargeBuffTicks;
    private final int overchargeSlowTicks;
    private final double rushDamage;
    private final double rushRadius;
    private final double rushSpeed;
    private final int rushTicks;
    private final double thunderheadDamage;
    private final double thunderheadRadius;
    private final int thunderheadChargeTicks;
    private final int thunderheadRange;
    private final double passiveShockChance;
    private final int passiveShockSlowTicks;

    public ThunderHammer(WeaponsPlugin plugin) {
        super(plugin);
        this.abilityDamage = configDouble("ability-damage", 9.0);
        this.radius = configDouble("radius", 4.0);
        this.overchargeDamage = configDouble("overcharge-damage", 5.0);
        this.overchargeRadius = configDouble("overcharge-radius", 3.5);
        this.overchargeBuffTicks = configInt("overcharge-buff-ticks", 100);
        this.overchargeSlowTicks = configInt("overcharge-slow-ticks", 40);
        this.rushDamage = configDouble("rush-damage", 6.0);
        this.rushRadius = configDouble("rush-radius", 1.6);
        this.rushSpeed = configDouble("rush-speed", 1.3);
        this.rushTicks = configInt("rush-ticks", 10);
        this.thunderheadDamage = configDouble("thunderhead-damage", 14.0);
        this.thunderheadRadius = configDouble("thunderhead-radius", 4.5);
        this.thunderheadChargeTicks = configInt("thunderhead-charge-ticks", 25);
        this.thunderheadRange = configInt("thunderhead-range", 25);
        this.passiveShockChance = configDouble("passive-shock-chance", 0.25);
        this.passiveShockSlowTicks = configInt("passive-shock-slow-ticks", 30);
    }

    @Override
    public String id() {
        return "thunder_hammer";
    }

    @Override
    public Material material() {
        return Material.MACE;
    }

    @Override
    public String displayNameText() {
        return "Thunder Hammer";
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
        return configDouble("cooldown-seconds", 10.0);
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
        return configDouble("ultimate-cooldown-seconds", 45.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Voltage")
                .accent(STRIKE_COLOR)
                .perMeleeHit(configDouble("voltage-per-hit", 6.0))
                .perDamageDealt(configDouble("voltage-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("voltage-per-ability", 8.0))
                .perKill(configDouble("voltage-per-kill", 12.0))
                .decay(configDouble("voltage-decay-per-second", 2.0), configDouble("voltage-decay-grace", 7.0))
                .cooldownFloor(configDouble("voltage-cooldown-floor", 40.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: slam the ground, calling", NamedTextColor.GRAY),
                Component.text("down a storm of lightning and a", NamedTextColor.GRAY),
                Component.text("shockwave, followed by a weaker", NamedTextColor.GRAY),
                Component.text("aftershock a moment later.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Thunder Slam";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Overcharge — arc", NamedTextColor.GRAY),
                Component.text("current through yourself, blasting", NamedTextColor.GRAY),
                Component.text("nearby foes and surging with", NamedTextColor.GRAY),
                Component.text("Strength and Speed.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Overcharge";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Bolt Rush — surge forward", NamedTextColor.GRAY),
                Component.text("trailing lightning, shocking everything", NamedTextColor.GRAY),
                Component.text("caught in your path.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Bolt Rush";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Thunderhead — channel", NamedTextColor.GRAY),
                Component.text("the sky itself, then bring down one", NamedTextColor.GRAY),
                Component.text("colossal bolt on your target.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Thunderhead";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_EXPLODE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_ANVIL_LAND;
    }

    @Override
    public void ability1(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        slamWave(player, loc, world, 1.0);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                slamWave(player, player.getLocation(), world, 0.6);
            }
        }, 8L);
    }

    private void slamWave(Player player, Location loc, World world, double powerScale) {
        double effectiveDamage = abilityDamage * rarity().statMultiplier() * powerScale;
        int bolts = 2 + (int) Math.round(3 * powerScale);

        for (int i = 0; i < bolts; i++) {
            double angle = Math.random() * Math.PI * 2;
            double dist = Math.random() * radius;
            Location strikePoint = loc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            world.strikeLightningEffect(strikePoint);
            Fx.glowPillar(plugin, strikePoint, Material.LIGHT_BLUE_STAINED_GLASS, 0.3f, 4.0f, 10);
        }

        // A second, offset ring of expanding shockwaves stacked above the first gives the
        // slam actual vertical thickness instead of one flat expanding circle.
        Fx.expandingRings(plugin, loc, Particle.ELECTRIC_SPARK, radius * powerScale * 1.3, 6, 3L);
        Fx.expandingRings(plugin, loc.clone().add(0, 0.6, 0), Particle.ELECTRIC_SPARK, radius * powerScale * 1.1, 5, 3L);
        Fx.burst(loc.clone().add(0, 0.2, 0), Particle.CLOUD, (int) (50 * powerScale), 1.1);
        Fx.coloredBurst(loc.clone().add(0, 0.2, 0), STRIKE_COLOR, 2.2f, (int) (36 * powerScale), 1.2);
        Fx.coloredBurst(loc.clone().add(0, 1.0, 0), STRIKE_COLOR, 1.8f, (int) (20 * powerScale), 1.0);

        for (Entity nearby : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            entity.damage(effectiveDamage, player);
            Fx.point(entity.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 10);
            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
            Fx.sound(entity.getLocation(), hitSound(), 0.6f, 1.4f);

            Vector away = entity.getLocation().toVector().subtract(loc.toVector());
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            entity.setVelocity(away.normalize().multiply(1.3).setY(0.5));
        }
    }

    @Override
    public void ability2(Player player) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        double damage = overchargeDamage * rarity().statMultiplier();
        Location loc = player.getLocation();

        StatusEffectManager.apply(player, PotionEffectType.STRENGTH, overchargeBuffTicks, 0);
        StatusEffectManager.apply(player, PotionEffectType.SPEED, overchargeBuffTicks, 0);

        Fx.coloredRing(loc, STRIKE_COLOR, 1.6f, overchargeRadius, 24, 0);
        Fx.coloredBurst(loc.clone().add(0, 1, 0), STRIKE_COLOR, 2.0f, 30, overchargeRadius * 0.4);
        Fx.point(loc.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 20);
        Fx.sound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
        Fx.sound(player, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.2f);

        for (Entity nearby : world.getNearbyEntities(loc, overchargeRadius, overchargeRadius, overchargeRadius)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            entity.damage(damage, player);
            StatusEffectManager.apply(entity, PotionEffectType.SLOWNESS, overchargeSlowTicks, 1);
            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
            Fx.point(entity.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 8);
        }
    }

    @Override
    public void ability3(Player player) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        double damage = rushDamage * rarity().statMultiplier();
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        Set<UUID> alreadyHit = new HashSet<>();

        player.setVelocity(dir.clone().multiply(rushSpeed).setY(0.25));
        Fx.sound(player, castSound(), 0.8f, 1.6f);
        Fx.sound(player, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 1.4f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= rushTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                Fx.point(loc.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 6);
                Fx.coloredBurst(loc.clone().add(0, 0.5, 0), STRIKE_COLOR, 1.0f, 8, 0.2);

                for (Entity nearby : world.getNearbyEntities(loc, rushRadius, rushRadius, rushRadius)) {
                    if (nearby instanceof LivingEntity entity && !entity.getUniqueId().equals(player.getUniqueId())
                            && alreadyHit.add(entity.getUniqueId())) {
                        entity.damage(damage, player);
                        Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                        Fx.point(entity.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 10);
                        Fx.sound(entity.getLocation(), hitSound(), 0.5f, 1.5f);
                    }
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
        Block targetBlock = player.getTargetBlockExact(thunderheadRange);
        Location strike = targetBlock != null
                ? targetBlock.getLocation().add(0.5, 1, 0.5)
                : player.getEyeLocation().add(player.getLocation().getDirection().multiply(thunderheadRange));

        Fx.sound(player, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.4f);
        Fx.sound(player, Sound.WEATHER_RAIN, 1.0f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= thunderheadChargeTicks) {
                    cancel();
                    double damage = thunderheadDamage * rarity().statMultiplier();
                    world.strikeLightning(strike);
                    world.strikeLightningEffect(strike);
                    Fx.coloredBurst(strike.clone().add(0, 1, 0), STRIKE_COLOR, 3.0f, 60, thunderheadRadius * 0.5);
                    Fx.expandingRings(plugin, strike, Particle.ELECTRIC_SPARK, thunderheadRadius * 1.4, 8, 2L);
                    Fx.sound(strike, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.6f);
                    Fx.sound(strike, hitSound(), 1.2f, 0.7f);

                    for (Entity nearby : world.getNearbyEntities(strike, thunderheadRadius, thunderheadRadius, thunderheadRadius)) {
                        if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                            continue;
                        }
                        entity.damage(damage, player);
                        Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                        Vector away = entity.getLocation().toVector().subtract(strike.toVector());
                        if (away.lengthSquared() < 0.01) {
                            away = new Vector(1, 0, 0);
                        }
                        entity.setVelocity(away.normalize().multiply(1.4).setY(0.6));
                    }
                    return;
                }

                double t = ticks / (double) thunderheadChargeTicks;
                Fx.helixFrame(strike, Particle.ELECTRIC_SPARK, thunderheadRadius * 0.3 * (1 - t) + 0.3, 6, ticks * 0.6, 3.0 * (1 - t));
                Fx.point(strike.clone().add(0, 3.0 * (1 - t), 0), Particle.ELECTRIC_SPARK, 4);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (ThreadLocalRandom.current().nextDouble() > passiveShockChance) {
            return;
        }
        StatusEffectManager.apply(victim, PotionEffectType.SLOWNESS, passiveShockSlowTicks, 1);
        Fx.point(victim.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 8);
        Fx.sound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 1.6f);
    }
}
