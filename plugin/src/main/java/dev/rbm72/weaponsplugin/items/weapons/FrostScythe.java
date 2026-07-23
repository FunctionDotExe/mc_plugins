package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pulses a freezing aura with animated, growing ice spikes. Hitting a
 * target that's already frosted from a previous cast triggers a bonus
 * "shatter" — the reward for chaining casts on the same enemy.
 */
public final class FrostScythe extends Weapon {

    private final double abilityDamage;
    private final double shatterBonusDamage;
    private final double radius;
    private final int slowTicks;
    private final int slowAmplifier;

    /** Per-target frost expiry, used to detect a shatter-eligible re-hit. Weapon instances are singletons. */
    private final Map<UUID, Long> frostedUntil = new HashMap<>();

    public FrostScythe(WeaponsPlugin plugin) {
        super(plugin);
        this.abilityDamage = configDouble("ability-damage", 5.0);
        this.shatterBonusDamage = configDouble("shatter-bonus-damage", 4.0);
        this.radius = configDouble("radius", 3.5);
        this.slowTicks = configInt("slow-ticks", 100);
        this.slowAmplifier = configInt("slow-amplifier", 2);
    }

    @Override
    public String id() {
        return "frost_scythe";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_HOE;
    }

    @Override
    public String displayNameText() {
        return "Frost Scythe";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("cooldown-seconds", 8.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: unleash a freezing aura,", NamedTextColor.GRAY),
                Component.text("damaging and slowing nearby enemies.", NamedTextColor.GRAY),
                Component.text("Re-casting on a frosted target", NamedTextColor.GRAY),
                Component.text("shatters it for bonus damage.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Shatterfrost";
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

    @Override
    public void ability1(Player player) {
        Location loc = player.getLocation();
        double effectiveDamage = abilityDamage * rarity().statMultiplier();
        double effectiveShatterBonus = shatterBonusDamage * rarity().statMultiplier();
        long now = System.currentTimeMillis();

        Fx.burst(loc.clone().add(0, 1, 0), Particle.SNOWFLAKE, 30, radius / 2);
        Fx.coloredBurst(loc.clone().add(0, 1, 0), Color.fromRGB(120, 200, 255), 2.1f, 28, radius / 2);
        Fx.expandingRings(plugin, loc, Particle.SNOWFLAKE, radius, 5, 2L);

        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            boolean shatter = frostedUntil.getOrDefault(entity.getUniqueId(), 0L) > now;
            double damage = effectiveDamage + (shatter ? effectiveShatterBonus : 0);

            entity.damage(damage, player);
            StatusEffectManager.apply(entity, PotionEffectType.SLOWNESS, slowTicks, slowAmplifier);
            StatusEffectManager.apply(entity, PotionEffectType.MINING_FATIGUE, slowTicks, 1);
            frostedUntil.put(entity.getUniqueId(), now + slowTicks * 50L);

            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
            Fx.sound(entity.getLocation(), hitSound(), 1.0f, 1.0f);
            growSpike(entity.getLocation());

            if (shatter) {
                Fx.burst(entity.getLocation().add(0, 1, 0), Particle.ITEM_SNOWBALL, 38, 0.55);
                Fx.coloredBurst(entity.getLocation().add(0, 1, 0), Color.fromRGB(190, 230, 255), 2.2f, 30, 0.6);
                Fx.sound(entity.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 1.0f);
            }
        }
    }

    private void growSpike(Location base) {
        // A real ice-block spike prop instead of a column of loose particles.
        Fx.glowPillar(plugin, base, Material.PACKED_ICE, 0.3f, 3.0f, 16);
    }
}
