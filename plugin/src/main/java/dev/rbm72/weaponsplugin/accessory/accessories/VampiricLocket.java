package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;

/** On-hit lifesteal: a share of the damage you deal is returned as health. */
public final class VampiricLocket extends Accessory {

    private static final double LIFESTEAL_FRACTION = 0.15;
    private static final Color BLOOD = Color.fromRGB(140, 0, 0);

    public VampiricLocket(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "vampiric_locket";
    }

    @Override
    public Material material() {
        return Material.GHAST_TEAR;
    }

    @Override
    public String displayNameText() {
        return "Vampiric Locket";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Heal 15%", NamedTextColor.GREEN)
                        .append(Component.text(" of the damage you deal", NamedTextColor.GRAY)),
                Component.text("with any weapon.", NamedTextColor.GRAY));
    }

    @Override
    public void onWeaponHit(Player player, Weapon weapon, LivingEntity victim, EntityDamageByEntityEvent event) {
        double heal = event.getFinalDamage() * LIFESTEAL_FRACTION;
        if (heal <= 0) {
            return;
        }
        double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(max, player.getHealth() + heal));
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), BLOOD, 1.0f, 6, 0.2);
        Fx.point(player.getLocation().add(0, 1, 0), Particle.HEART, 1);
    }
}
