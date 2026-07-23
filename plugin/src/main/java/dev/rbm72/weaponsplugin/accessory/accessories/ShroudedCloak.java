package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Standalone active ability: a short burst of Invisibility and Speed to slip away from a fight,
 *  wrapped in a soft smoke poof instead of a loud escape cue. */
public final class ShroudedCloak extends Accessory {

    public ShroudedCloak(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "shrouded_cloak";
    }

    @Override
    public Material material() {
        return Material.PHANTOM_MEMBRANE;
    }

    @Override
    public String displayNameText() {
        return "Shrouded Cloak";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Stitched from shadow and phantom", NamedTextColor.GRAY),
                Component.text("wing — the world forgets to look.", NamedTextColor.GRAY));
    }

    @Override
    public boolean hasPersonalAbility() {
        return true;
    }

    @Override
    public String personalAbilityName() {
        return "Vanish";
    }

    @Override
    public List<Component> personalAbilityLore() {
        return List.of(
                Component.text("Grants brief Invisibility and", NamedTextColor.GRAY),
                Component.text("Speed to slip away from a fight.", NamedTextColor.GRAY));
    }

    @Override
    public double personalAbilityCooldownSeconds() {
        return 25.0;
    }

    @Override
    public void personalAbility(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, true, false));

        Fx.burst(player.getLocation(), Particle.CLOUD, 16, 0.4);
        Fx.point(player.getLocation(), Particle.SMOKE, 20);
        Fx.sound(player, Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.1f);
    }
}
