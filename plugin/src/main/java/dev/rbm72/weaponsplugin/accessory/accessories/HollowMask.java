package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Blasphemous-flavored penance trinket: grants a wholly new active ability (not a buff to an
 * existing one) triggered by double-tapping sneak. Pays in your own blood to punish everyone
 * around you, then profits off the wound — guilt made into a weapon.
 */
public final class HollowMask extends Accessory {

    private static final double HEALTH_COST = 4.0;
    private static final double DAMAGE_PER_HP_SPENT = 2.5;
    private static final double RADIUS = 5.0;
    private static final double LIFESTEAL_FRACTION = 0.5;
    private static final Color GUILT_COLOR = Color.fromRGB(120, 10, 40);

    public HollowMask(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "hollow_mask";
    }

    @Override
    public Material material() {
        return Material.WITHER_SKELETON_SKULL;
    }

    @Override
    public String displayNameText() {
        return "Hollow Mask";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("A relic of penance.", NamedTextColor.DARK_GRAY),
                Component.text("Grants a standalone active", NamedTextColor.GRAY),
                Component.text("ability of its own.", NamedTextColor.GRAY));
    }

    @Override
    public boolean hasPersonalAbility() {
        return true;
    }

    @Override
    public String personalAbilityName() {
        return "Guilt Nova";
    }

    @Override
    public List<Component> personalAbilityLore() {
        return List.of(
                Component.text("Sacrifice 2 hearts to unleash a", NamedTextColor.GRAY),
                Component.text("nova of penance around you,", NamedTextColor.GRAY),
                Component.text("healing you for half the damage", NamedTextColor.GRAY),
                Component.text("it deals to nearby enemies.", NamedTextColor.GRAY));
    }

    @Override
    public double personalAbilityCooldownSeconds() {
        return 20.0;
    }

    @Override
    public void personalAbility(Player player) {
        if (player.getHealth() <= HEALTH_COST) {
            Fx.sound(player, Sound.ENTITY_VILLAGER_NO, 0.6f, 0.8f);
            return;
        }

        player.setHealth(player.getHealth() - HEALTH_COST);
        Fx.sound(player, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.0f, 0.6f);
        Fx.sound(player, Sound.ITEM_TOTEM_USE, 0.7f, 1.6f);

        Location center = player.getLocation();
        Fx.coloredBurst(center.clone().add(0, 1, 0), GUILT_COLOR, 2.0f, 40, 0.8);
        Fx.coloredRing(center.clone().add(0, 0.1, 0), GUILT_COLOR, 1.4f, RADIUS, 32, 0);
        Fx.point(center.clone().add(0, 1, 0), Particle.SOUL, 20);

        double damage = HEALTH_COST * DAMAGE_PER_HP_SPENT;
        double healed = 0;
        for (Entity nearby : player.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            entity.damage(damage, player);
            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
            healed += damage * LIFESTEAL_FRACTION;
        }

        if (healed > 0) {
            double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.min(max, player.getHealth() + healed));
            Fx.point(player.getLocation().add(0, 1, 0), Particle.HEART, 3);
        }
    }
}
