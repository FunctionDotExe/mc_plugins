package dev.rbm72.weaponsplugin.ridable.ridables;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.ridable.Ridable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Rides any live Strider. Steering is left entirely to vanilla (Striders already implement
 * {@code Steerable} and drive their own controls once mounted), so this saddle only adds taming
 * and the Warm Aura ability.
 */
public final class StriderSaddle extends Ridable {

    public StriderSaddle(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "strider_saddle";
    }

    @Override
    public Material material() {
        return Material.WARPED_FUNGUS_ON_A_STICK;
    }

    @Override
    public String displayNameText() {
        return "Strider Saddle";
    }

    @Override
    public Rarity rarity() {
        return Rarity.COMMON;
    }

    @Override
    public EntityType targetEntityType() {
        return EntityType.STRIDER;
    }

    @Override
    public boolean customSteering() {
        return false;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Tames and lets you ride any live", NamedTextColor.GRAY),
                Component.text("Strider you find, no fungus needed.", NamedTextColor.GRAY));
    }

    @Override
    public String abilityName() {
        return "Warm Aura";
    }

    @Override
    public List<Component> abilityLore() {
        return List.of(Component.text("Brief Fire Resistance and a burst of Speed.", NamedTextColor.GRAY));
    }

    @Override
    public double abilityCooldownSeconds() {
        return 15.0;
    }

    @Override
    public void ability(Player rider, LivingEntity mount) {
        rider.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0));
        rider.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));

        Fx.burst(mount.getLocation(), Particle.FLAME, 20, 0.5);
        Fx.sound(mount.getLocation(), Sound.ENTITY_STRIDER_HAPPY, 1.0f, 1.0f);
    }
}
