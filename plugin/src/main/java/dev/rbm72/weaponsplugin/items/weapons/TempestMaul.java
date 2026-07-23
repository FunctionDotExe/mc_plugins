package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
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
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Lightning bruiser maul: a shockwave thunderclap, a called-down bolt at the
 * aim point, a launching gust dash, and a storm summoned around the wielder.
 * Every melee hit forks a spark to a second nearby enemy.
 */
public final class TempestMaul extends Weapon {

    private static final Color STRIKE_COLOR = Color.fromRGB(255, 240, 120);
    private static final Color STORM_WHITE = Color.fromRGB(235, 245, 255);

    private final double clapDamage;
    private final double clapRadius;
    private final double callDamage;
    private final double callRadius;
    private final int callRange;
    private final double gustForward;
    private final double gustUp;
    private final double tempestDamage;
    private final double tempestRadius;
    private final int tempestDurationTicks;
    private final int tempestStrikeInterval;
    private final double passiveDamage;
    private final double passiveRange;

    public TempestMaul(WeaponsPlugin plugin) {
        super(plugin);
        this.clapDamage = configDouble("clap-damage", 9.0);
        this.clapRadius = configDouble("clap-radius", 4.0);
        this.callDamage = configDouble("call-damage", 12.0);
        this.callRadius = configDouble("call-radius", 3.0);
        this.callRange = configInt("call-range", 30);
        this.gustForward = configDouble("gust-forward", 1.4);
        this.gustUp = configDouble("gust-up", 0.7);
        this.tempestDamage = configDouble("tempest-damage", 8.0);
        this.tempestRadius = configDouble("tempest-radius", 6.0);
        this.tempestDurationTicks = configInt("tempest-duration-ticks", 100);
        this.tempestStrikeInterval = configInt("tempest-strike-interval", 15);
        this.passiveDamage = configDouble("passive-damage", 4.0);
        this.passiveRange = configDouble("passive-range", 5.0);
    }

    @Override
    public String id() {
        return "tempest_maul";
    }

    @Override
    public Material material() {
        return Material.MACE;
    }

    @Override
    public String displayNameText() {
        return "Tempest Maul";
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
        return configDouble("ability1-cooldown-seconds", 8.0);
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
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: Thunderclap — slam the", NamedTextColor.GRAY),
                Component.text("ground for AoE damage and knockback.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Thunderclap";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Call Lightning —", NamedTextColor.GRAY),
                Component.text("strike a bolt down at your aim point.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Call Lightning";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Gust — dash forward and", NamedTextColor.GRAY),
                Component.text("launch yourself on a burst of wind.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Gust";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Tempest — summon a", NamedTextColor.GRAY),
                Component.text("lightning storm around yourself.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Tempest";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_LIGHTNING_BOLT_IMPACT;
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
        double damage = clapDamage * rarity().statMultiplier();

        for (int i = 0; i < 4; i++) {
            double angle = Math.random() * Math.PI * 2;
            double dist = Math.random() * clapRadius;
            Location strikePoint = loc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            world.strikeLightningEffect(strikePoint);
            Fx.glowPillar(plugin, strikePoint, Material.LIGHT_BLUE_STAINED_GLASS, 0.3f, 4.0f, 10);
        }
        Fx.expandingRings(plugin, loc, Particle.ELECTRIC_SPARK, clapRadius * 1.3, 6, 3L);
        Fx.coloredBurst(loc.clone().add(0, 0.2, 0), STRIKE_COLOR, 2.2f, 36, 1.2);
        Fx.sound(player, castSound(), 1.0f, 0.8f);
        Fx.sound(player, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.1f);

        for (Entity nearby : world.getNearbyEntities(loc, clapRadius, clapRadius, clapRadius)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            entity.damage(damage, player);
            Fx.point(entity.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 10);
            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
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
        double damage = callDamage * rarity().statMultiplier();
        Block target = player.getTargetBlockExact(callRange);
        Location strike = target != null
                ? target.getLocation().add(0.5, 1, 0.5)
                : player.getEyeLocation().add(player.getLocation().getDirection().multiply(callRange));
        world.strikeLightning(strike);
        Fx.coloredBurst(strike.clone().add(0, 1, 0), STRIKE_COLOR, 2.0f, 30, callRadius * 0.4);
        Fx.point(strike.clone().add(0, 1, 0), Particle.EXPLOSION_EMITTER, 1);
        Fx.sound(player, castSound(), 1.0f, 0.9f);
        Fx.sound(strike, hitSound(), 1.0f, 1.0f);
        for (Entity nearby : world.getNearbyEntities(strike, callRadius, callRadius, callRadius)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            entity.damage(damage, player);
            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability3(Player player) {
        Vector dir = player.getLocation().getDirection().normalize();
        Vector velocity = dir.multiply(gustForward).setY(gustUp);
        player.setVelocity(velocity);
        Fx.burst(player.getLocation().add(0, 0.5, 0), Particle.CLOUD, 30, 0.5);
        Fx.coloredBurst(player.getLocation().add(0, 0.5, 0), STORM_WHITE, 1.6f, 20, 0.5);
        Fx.sound(player, Sound.ITEM_ELYTRA_FLYING, 1.0f, 1.2f);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.0f);
    }

    @Override
    public void ultimate(Player player) {
        double damage = tempestDamage * rarity().statMultiplier();
        Fx.sound(player, castSound(), 1.2f, 0.5f);
        Fx.sound(player, Sound.WEATHER_RAIN, 1.0f, 0.6f);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= tempestDurationTicks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                World world = loc.getWorld();
                Fx.helixFrame(loc, Particle.ELECTRIC_SPARK, tempestRadius * 0.6, 4, ticks * 0.5, (ticks % 30) * 0.1);
                Fx.coloredRing(loc, STRIKE_COLOR, 1.2f, tempestRadius, 24, ticks * 0.2);
                if (world != null && ticks % tempestStrikeInterval == 0) {
                    double angle = Math.random() * Math.PI * 2;
                    double dist = Math.random() * tempestRadius;
                    Location strike = loc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
                    world.strikeLightningEffect(strike);
                    Fx.coloredBurst(strike.clone().add(0, 1, 0), STRIKE_COLOR, 1.8f, 20, 0.5);
                    Fx.sound(strike, hitSound(), 0.9f, 1.1f);
                    for (Entity nearby : world.getNearbyEntities(strike, 2.5, 2.5, 2.5)) {
                        if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                            continue;
                        }
                        entity.damage(damage, player);
                        Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        // Spark-chain passive: the strike forks to a second nearby enemy for a small jolt.
        double damage = passiveDamage * rarity().statMultiplier();
        LivingEntity fork = null;
        double bestDistSq = passiveRange * passiveRange;
        for (Entity nearby : victim.getNearbyEntities(passiveRange, passiveRange, passiveRange)) {
            if (!(nearby instanceof LivingEntity other) || other.getUniqueId().equals(attacker.getUniqueId())
                    || other.getUniqueId().equals(victim.getUniqueId())) {
                continue;
            }
            double distSq = other.getLocation().distanceSquared(victim.getLocation());
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                fork = other;
            }
        }
        if (fork != null) {
            Fx.line(victim.getLocation().add(0, 1, 0), fork.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 10);
            fork.damage(damage, attacker);
            Fx.coloredBurst(fork.getLocation().add(0, 1, 0), STRIKE_COLOR, 1.4f, 10, 0.3);
            Fx.sound(fork.getLocation(), hitSound(), 0.5f, 1.5f);
        }
    }
}
