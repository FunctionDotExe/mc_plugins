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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** The Mythic capstone weapon dropped by The Worldender: a fusion blade that cleaves worlds, cycles the elements, tears void rifts, and — at its peak — ascends into a screen-filling cataclysm. Deals bonus damage to bosses and procs a random element on every hit. */
public final class Apotheosis extends Weapon {

    private static final Color CREATION = Color.fromRGB(255, 90, 60);
    private static final Color VOID = Color.fromRGB(120, 20, 160);

    private final double cleaveDamage;
    private final double cleaveRange;
    private final double cleaveAngleDegrees;
    private final double burstDamage;
    private final double burstRadius;
    private final double riftPullRadius;
    private final int riftPullDurationTicks;
    private final double riftImplodeDamage;
    private final double riftImplodeRadius;
    private final double apotheosisDamage;
    private final double apotheosisRadius;
    private final double bossBonusDamage;
    private final double procChance;
    private final double procDamage;

    public Apotheosis(WeaponsPlugin plugin) {
        super(plugin);
        this.cleaveDamage = configDouble("cleave-damage", 14.0);
        this.cleaveRange = configDouble("cleave-range", 7.0);
        this.cleaveAngleDegrees = configDouble("cleave-angle-degrees", 90.0);
        this.burstDamage = configDouble("burst-damage", 9.0);
        this.burstRadius = configDouble("burst-radius", 6.0);
        this.riftPullRadius = configDouble("rift-pull-radius", 8.0);
        this.riftPullDurationTicks = configInt("rift-pull-duration-ticks", 40);
        this.riftImplodeDamage = configDouble("rift-implode-damage", 12.0);
        this.riftImplodeRadius = configDouble("rift-implode-radius", 5.0);
        this.apotheosisDamage = configDouble("apotheosis-damage", 20.0);
        this.apotheosisRadius = configDouble("apotheosis-radius", 9.0);
        this.bossBonusDamage = configDouble("boss-bonus-damage", 10.0);
        this.procChance = configDouble("proc-chance", 0.35);
        this.procDamage = configDouble("proc-damage", 3.0);
    }

    private final Map<UUID, Integer> elementCycle = new HashMap<>();

    @Override
    public String id() {
        return "apotheosis";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Apotheosis";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 5.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 7.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 6.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 10.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 75.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Ascension")
                .accent(CREATION)
                .perMeleeHit(configDouble("ascension-per-hit", 7.0))
                .perDamageDealt(configDouble("ascension-per-damage-dealt", 0.5))
                .perDamageTaken(configDouble("ascension-per-damage-taken", 1.0))
                .perAbilityCast(configDouble("ascension-per-ability", 10.0))
                .perKill(configDouble("ascension-per-kill", 15.0))
                .decay(configDouble("ascension-decay-per-second", 2.5), configDouble("ascension-decay-grace", 6.0))
                .cooldownFloor(configDouble("ascension-cooldown-floor", 70.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: Worldcleaver — a massive", NamedTextColor.GRAY),
                Component.text("cone cleave that detonates on impact.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Worldcleaver";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Elemental Burst —", NamedTextColor.GRAY),
                Component.text("cycles ice, fire, then lightning.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Elemental Cycle";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Void Rift — pull enemies", NamedTextColor.GRAY),
                Component.text("in, then implode them.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Gravity Collapse";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Apotheosis — a", NamedTextColor.GRAY),
                Component.text("screen-filling multi-element cataclysm.", NamedTextColor.GRAY),
                Component.text("Passive: bonus damage to bosses; every", NamedTextColor.GRAY),
                Component.text("hit procs a random element.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Apotheosis";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_TRIDENT_THUNDER;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_EXPLODE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_BEACON_ACTIVATE;
    }

    @Override
    public void ability1(Player player) {
        double damage = cleaveDamage * rarity().statMultiplier();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().clone().normalize();
        World world = player.getWorld();

        Fx.sound(player, castSound(), 1.0f, 0.8f);
        for (double d = 1; d <= cleaveRange; d += 1.0) {
            Location arc = eye.clone().add(direction.clone().multiply(d));
            Fx.coloredBurst(arc, CREATION, 1.2f, 6, 0.6);
            Fx.burst(arc, Particle.SWEEP_ATTACK, 2, 0.4);
        }

        for (Entity entity : world.getNearbyEntities(player.getLocation(), cleaveRange, cleaveRange, cleaveRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toTarget = living.getLocation().toVector().subtract(eye.toVector());
            if (toTarget.lengthSquared() > cleaveRange * cleaveRange) {
                continue;
            }
            double angle = Math.toDegrees(direction.angle(toTarget.normalize()));
            if (angle > cleaveAngleDegrees / 2) {
                continue;
            }
            living.damage(damage, player);
            Location impact = living.getLocation().add(0, 1, 0);
            world.spawnParticle(Particle.EXPLOSION, impact, 9, 0.64, 0.64, 0.64, 0);
            Fx.coloredBurst(impact, CREATION, 1.6f, 20, 0.5);
            Fx.bloodSpray(impact);
        }
        Fx.sound(player, hitSound(), 0.9f, 1.1f);
    }

    @Override
    public void ability2(Player player) {
        int index = elementCycle.merge(player.getUniqueId(), 1, (a, b) -> (a + 1) % 3);
        int element = index % 3;
        double damage = burstDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = player.getWorld();

        Color color;
        Particle particle;
        Sound sound;
        switch (element) {
            case 0 -> {
                color = Color.fromRGB(150, 230, 255);
                particle = Particle.SNOWFLAKE;
                sound = Sound.BLOCK_GLASS_BREAK;
            }
            case 1 -> {
                color = Color.fromRGB(255, 130, 40);
                particle = Particle.FLAME;
                sound = Sound.ENTITY_BLAZE_SHOOT;
            }
            default -> {
                color = Color.fromRGB(255, 245, 120);
                particle = Particle.ELECTRIC_SPARK;
                sound = Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
            }
        }

        Fx.burst(center.clone().add(0, 1, 0), particle, 60, burstRadius * 0.55);
        Fx.coloredBurst(center.clone().add(0, 1, 0), color, 1.6f, 40, burstRadius * 0.6);
        Fx.expandingRings(plugin, center.clone().add(0, 0.1, 0), particle, burstRadius, 4, 2L);
        Fx.sound(player, sound, 1.1f, 1.0f);

        for (Entity entity : world.getNearbyEntities(center, burstRadius, burstRadius, burstRadius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            living.damage(damage, player);
            switch (element) {
                case 0 -> StatusEffectManager.apply(living, PotionEffectType.SLOWNESS, 60, 2);
                case 1 -> living.setFireTicks(80);
                default -> {
                    if (world.getEntitiesByClass(org.bukkit.entity.LightningStrike.class).isEmpty()) {
                        world.strikeLightningEffect(living.getLocation());
                    }
                }
            }
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability3(Player player) {
        double damage = riftImplodeDamage * rarity().statMultiplier();
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(4));
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 0.9f);
        Fx.spinningIcon(plugin, center.clone().add(0, 1.2, 0), Material.CRYING_OBSIDIAN, 1.0f, riftPullDurationTicks, 20.0);
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks > riftPullDurationTicks) {
                    // Implosion.
                    Fx.burst(center, Particle.SQUID_INK, 60, riftImplodeRadius * 0.55);
                    Fx.coloredBurst(center, VOID, 1.8f, 50, riftImplodeRadius * 0.6);
                    Fx.expandingRings(plugin, center, Particle.PORTAL, riftImplodeRadius, 4, 2L);
                    Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
                    for (Entity entity : world.getNearbyEntities(center, riftImplodeRadius, riftImplodeRadius, riftImplodeRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player) && alreadyHit.add(living.getUniqueId())) {
                            living.damage(damage, player);
                            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                        }
                    }
                    cancel();
                    return;
                }
                angle += 0.4;
                Fx.ring(center, Particle.PORTAL, 2.5, 30, angle);
                Fx.ring(center.clone().add(0, 0.5, 0), Particle.REVERSE_PORTAL, 3.2, 34, -angle);
                Fx.coloredBurst(center, VOID, 1.2f, 8, 1.2);
                for (Entity entity : world.getNearbyEntities(center, riftPullRadius, riftPullRadius, riftPullRadius)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player)) {
                        Vector pull = center.toVector().subtract(living.getLocation().toVector()).normalize().multiply(0.3);
                        living.setVelocity(living.getVelocity().add(pull));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        double damage = apotheosisDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_WITHER_SPAWN, 1.4f, 0.7f);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.6f);
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= 60) {
                    cancel();
                    return;
                }
                Location core = player.getLocation();
                // Layered multi-element storm: fire, ice, void and light all at once.
                Fx.helixFrame(core, Particle.FLAME, apotheosisRadius * 0.5, 4, ticks * 0.4, ticks * 0.12);
                Fx.helixFrame(core, Particle.SNOWFLAKE, apotheosisRadius * 0.5, 4, -ticks * 0.4 + 1.5, ticks * 0.12);
                Fx.ring(core.clone().add(0, 0.2, 0), Particle.ELECTRIC_SPARK, apotheosisRadius * 0.8, 30, ticks * 0.3);
                Fx.coloredBurst(core.clone().add(0, 1, 0), CREATION, 1.4f, 12, apotheosisRadius * 0.5);

                if (ticks % 12 == 0) {
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, core.clone().add(0, 1, 0), 6, apotheosisRadius * 0.64, 1.6, apotheosisRadius * 0.64, 0);
                    Fx.sound(core, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);
                    for (Entity entity : world.getNearbyEntities(core, apotheosisRadius, apotheosisRadius, apotheosisRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(damage, player);
                            living.setFireTicks(60);
                            StatusEffectManager.apply(living, PotionEffectType.SLOWNESS, 40, 1);
                            Vector knockback = living.getLocation().toVector().subtract(core.toVector()).normalize().setY(0.4);
                            living.setVelocity(living.getVelocity().add(knockback));
                            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                            alreadyHit.add(living.getUniqueId());
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        // Bonus damage to bosses.
        if (plugin.bossManager().instanceFor(victim.getUniqueId()).isPresent()) {
            event.setDamage(event.getDamage() + bossBonusDamage * rarity().statMultiplier());
            Fx.coloredBurst(victim.getLocation().add(0, 1, 0), CREATION, 1.6f, 14, 0.4);
        }

        // Random small elemental proc on every hit.
        if (ThreadLocalRandom.current().nextDouble() >= procChance) {
            return;
        }
        double proc = procDamage * rarity().statMultiplier();
        Location loc = victim.getLocation().add(0, 1, 0);
        switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> {
                StatusEffectManager.apply(victim, PotionEffectType.SLOWNESS, 40, 1);
                Fx.coloredBurst(loc, Color.fromRGB(150, 230, 255), 1.2f, 12, 0.4);
            }
            case 1 -> {
                victim.setFireTicks(60);
                victim.damage(proc, attacker);
                Fx.burst(loc, Particle.FLAME, 12, 0.4);
            }
            default -> {
                victim.damage(proc, attacker);
                Fx.point(loc, Particle.ELECTRIC_SPARK, 12);
                Fx.sound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.4f);
            }
        }
    }
}
