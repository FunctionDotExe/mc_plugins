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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Ranged poison specialist: bolts sting for a stacking venom mark, then a resonance pulse detonates every stack nearby at once. */
public final class SerpentfangCrossbow extends Weapon {

    private static final Color VENOM_COLOR = Color.fromRGB(80, 200, 60);

    private final double meleeDamageBonus;
    private final double boltSpeed;
    private final double boltDamage;
    private final int poisonDurationTicks;
    private final int poisonAmplifier;
    private final int maxStacks;
    private final double detonateRadius;
    private final double detonatePerStackDamage;

    public SerpentfangCrossbow(WeaponsPlugin plugin) {
        super(plugin);
        this.meleeDamageBonus = configDouble("melee-damage-bonus", 1.0);
        this.boltSpeed = configDouble("bolt-speed", 2.6);
        this.boltDamage = configDouble("bolt-damage", 3.0);
        this.poisonDurationTicks = configInt("poison-duration-ticks", 60);
        this.poisonAmplifier = configInt("poison-amplifier", 0);
        this.maxStacks = configInt("max-stacks", 5);
        this.detonateRadius = configDouble("detonate-radius", 12.0);
        this.detonatePerStackDamage = configDouble("detonate-per-stack-damage", 2.5);
    }

    @Override
    public String id() {
        return "serpentfang_crossbow";
    }

    @Override
    public Material material() {
        return Material.CROSSBOW;
    }

    @Override
    public String displayNameText() {
        return "Serpentfang Crossbow";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return meleeDamageBonus;
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 4.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 9.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: loose a venom bolt that", NamedTextColor.GRAY),
                Component.text("poisons its target and stacks a", NamedTextColor.GRAY),
                Component.text("resonant venom mark on them.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Venom Bolt";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: detonate every", NamedTextColor.GRAY),
                Component.text("venom mark on nearby enemies at", NamedTextColor.GRAY),
                Component.text("once, dealing damage per stack.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Toxic Resonance";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_CROSSBOW_SHOOT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_SPIDER_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.ITEM_CROSSBOW_LOADING_END;
    }

    private NamespacedKey stackKey() {
        return new NamespacedKey(plugin, "serpentfang_stacks");
    }

    @Override
    public void ability1(Player player) {
        Fx.sound(player, castSound(), 1.0f, 1.0f);
        Arrow projectile = player.launchProjectile(Arrow.class, player.getLocation().getDirection().multiply(boltSpeed));
        projectile.setDamage(0);
        projectile.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        projectile.setPersistent(false);
        projectile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
        Fx.coloredBurst(projectile.getLocation(), VENOM_COLOR, 1.0f, 6, 0.1);
    }

    @Override
    public void ability2(Player player) {
        double damage = detonatePerStackDamage * rarity().statMultiplier();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(player, Sound.ENTITY_WITCH_THROW, 1.0f, 0.8f);
        Fx.coloredBurst(center.clone().add(0, 1, 0), VENOM_COLOR, 1.4f, 20, detonateRadius * 0.4);

        for (Entity nearby : world.getNearbyEntities(center, detonateRadius, detonateRadius, detonateRadius)) {
            if (!(nearby instanceof LivingEntity living)) {
                continue;
            }
            int stacks = living.getPersistentDataContainer().getOrDefault(stackKey(), PersistentDataType.INTEGER, 0);
            if (stacks <= 0) {
                continue;
            }
            living.damage(damage * stacks, player);
            living.getPersistentDataContainer().set(stackKey(), PersistentDataType.INTEGER, 0);
            StatusEffectManager.apply(living, PotionEffectType.POISON, poisonDurationTicks, poisonAmplifier);
            Fx.coloredBurst(living.getLocation().add(0, 1, 0), VENOM_COLOR, 1.3f, 14, 0.4);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            Fx.sound(living.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        }
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        double damage = boltDamage * rarity().statMultiplier();

        if (event.getHitEntity() instanceof LivingEntity living && !living.equals(shooter)) {
            living.damage(damage, shooter);
            StatusEffectManager.apply(living, PotionEffectType.POISON, poisonDurationTicks, poisonAmplifier);
            int stacks = living.getPersistentDataContainer().getOrDefault(stackKey(), PersistentDataType.INTEGER, 0);
            living.getPersistentDataContainer().set(stackKey(), PersistentDataType.INTEGER, Math.min(maxStacks, stacks + 1));
            Fx.coloredBurst(living.getLocation().add(0, 1, 0), VENOM_COLOR, 1.1f, 10, 0.25);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            Fx.sound(loc, hitSound(), 0.9f, 1.0f);
        }

        if (event.getHitBlock() != null) {
            Fx.burst(loc, Particle.CRIT, 10, 0.2);
            if (event.getEntity().isValid()) {
                event.getEntity().remove();
            }
        }
    }
}
