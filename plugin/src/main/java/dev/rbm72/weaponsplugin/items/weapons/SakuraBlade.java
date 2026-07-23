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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Elegant sword: cone slash, petal dash, bloom AoE, and a lingering petal-field ultimate. Combo hits stack movement speed. */
public final class SakuraBlade extends Weapon {

    private static final Color PETAL_PINK = Color.fromRGB(255, 183, 206);

    private final long comboWindowMs;
    private final int comboMaxStacks;
    private final double cleaveDamage;
    private final double cleaveRange;
    private final double dashDamage;
    private final double dashDistance;
    private final double bloomDamage;
    private final double bloomRadius;
    private final double fieldRadius;
    private final int fieldDurationTicks;
    private final double fieldDamagePerTick;
    private final double fieldRangeAhead;

    public SakuraBlade(WeaponsPlugin plugin) {
        super(plugin);
        this.comboWindowMs = configInt("combo-window-ms", 2500);
        this.comboMaxStacks = configInt("combo-max-stacks", 3);
        this.cleaveDamage = configDouble("cleave-damage", 4.0);
        this.cleaveRange = configDouble("cleave-range", 3.0);
        this.dashDamage = configDouble("dash-damage", 4.5);
        this.dashDistance = configDouble("dash-distance", 5.0);
        this.bloomDamage = configDouble("bloom-damage", 5.0);
        this.bloomRadius = configDouble("bloom-radius", 3.5);
        this.fieldRadius = configDouble("field-radius", 3.0);
        this.fieldDurationTicks = configInt("field-duration-ticks", 80);
        this.fieldDamagePerTick = configDouble("field-damage-per-tick", 1.2);
        this.fieldRangeAhead = configDouble("field-range-ahead", 4.0);
    }

    private final Map<UUID, Long> lastComboHitMs = new HashMap<>();
    private final Map<UUID, Integer> comboStacks = new HashMap<>();

    @Override
    public String id() {
        return "sakura_blade";
    }

    @Override
    public Material material() {
        return Material.IRON_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Sakura Blade";
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
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 6.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 7.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 40.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: cherry blossom slash", NamedTextColor.GRAY),
                Component.text("in a cone ahead of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Blossom Slash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: petal dash", NamedTextColor.GRAY),
                Component.text("through enemies ahead.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Petal Dash";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: bloom explosion around", NamedTextColor.GRAY),
                Component.text("you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Full Bloom";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: fill an area with", NamedTextColor.GRAY),
                Component.text("petals that damage enemies over", NamedTextColor.GRAY),
                Component.text("time.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Petal Storm";
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_GRASS_BREAK;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_ATTACK_SWEEP;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_CHERRY_LEAVES_BREAK;
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        long lastHit = lastComboHitMs.getOrDefault(uuid, 0L);
        int stacks = (now - lastHit <= comboWindowMs) ? Math.min(comboMaxStacks, comboStacks.getOrDefault(uuid, 0) + 1) : 1;
        lastComboHitMs.put(uuid, now);
        comboStacks.put(uuid, stacks);
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) (comboWindowMs / 50), stacks - 1, true, false));
        Fx.point(attacker.getLocation().add(0, 1, 0), Particle.CHERRY_LEAVES, 3);
        Fx.coloredBurst(attacker.getLocation().add(0, 1, 0), PETAL_PINK, 0.7f, 3 + stacks, 0.2);
    }

    @Override
    public void ability1(Player player) {
        double damage = cleaveDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Fx.trail(origin.clone().add(0, 1, 0), Particle.CHERRY_LEAVES, 45, 0.75, 0.06);
        Fx.coloredBurst(origin.clone().add(0, 1, 0).add(direction.clone().multiply(cleaveRange * 0.5)), PETAL_PINK, 1.4f, 32, cleaveRange * 0.45);

        for (Entity entity : world.getNearbyEntities(origin, cleaveRange, cleaveRange, cleaveRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(origin.toVector()).normalize();
            if (direction.dot(toEntity) < 0.4) {
                continue;
            }
            living.damage(damage, player);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void ability2(Player player) {
        double damage = dashDamage * rarity().statMultiplier();
        Location start = player.getLocation();
        Vector direction = start.getDirection().normalize();
        World world = start.getWorld();
        if (world == null) {
            return;
        }
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= 8) {
                    cancel();
                    return;
                }
                player.setVelocity(direction.clone().multiply(0.9).setY(0.15));
                Fx.trail(player.getLocation().add(0, 1, 0), Particle.CHERRY_LEAVES, 14, 0.35, 0.03);
                Fx.coloredBurst(player.getLocation().add(0, 1, 0), PETAL_PINK, 1.0f, 10, 0.4);

                for (Entity nearby : player.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (nearby instanceof LivingEntity entity && !entity.equals(player) && alreadyHit.add(entity.getUniqueId())) {
                        entity.damage(damage, player);
                        Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability3(Player player) {
        double damage = bloomDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.burst(center.clone().add(0, 1, 0), Particle.CHERRY_LEAVES, 70, bloomRadius * 0.55);
        Fx.coloredBurst(center.clone().add(0, 1, 0), PETAL_PINK, 1.6f, 55, bloomRadius * 0.6);
        Fx.expandingRings(plugin, center.clone().add(0, 0.1, 0), Particle.CHERRY_LEAVES, bloomRadius, 3, 2L);
        Fx.spinningIcon(plugin, center.clone().add(0, 1.2, 0), Material.CHERRY_SAPLING, 1.1f, 20, 15.0);
        Fx.sound(player, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.2f, 1.0f);

        for (Entity entity : world.getNearbyEntities(center, bloomRadius, bloomRadius, bloomRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                Vector knockback = living.getLocation().toVector().subtract(center.toVector()).normalize().setY(0.3);
                living.setVelocity(living.getVelocity().add(knockback));
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ultimate(Player player) {
        double damagePerTick = fieldDamagePerTick * rarity().statMultiplier();
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(fieldRangeAhead));
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.BLOCK_CHERRY_LEAVES_PLACE, 1.0f, 0.8f);
        Fx.spinningIcon(plugin, center.clone().add(0, 1.4, 0), Material.PINK_PETALS, 1.25f, fieldDurationTicks, 10.0);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= fieldDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                angle += 0.3;
                double bob = 0.4 + 0.5 * Math.sin(ticks * 0.1);
                Fx.helixFrame(center, Particle.CHERRY_LEAVES, fieldRadius * 0.65 - 0.4, 14, angle, bob);
                Fx.helixFrame(center, Particle.CHERRY_LEAVES, fieldRadius * 0.65, 18, -angle * 0.85, bob + 0.35);
                Fx.helixFrame(center, Particle.CHERRY_LEAVES, fieldRadius * 0.65 + 0.4, 14, angle * 1.2, bob + 0.7);
                Fx.coloredBurst(center.clone().add(0, bob + 0.5, 0), PETAL_PINK, 1.4f, 12, fieldRadius * 0.45);
                Fx.coloredBurst(center.clone().add(0, bob * 0.4, 0), PETAL_PINK, 1.1f, 8, fieldRadius * 0.6);
                if (ticks % 10 == 0) {
                    for (Entity entity : world.getNearbyEntities(center, fieldRadius, fieldRadius, fieldRadius)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(damagePerTick, player);
                            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
