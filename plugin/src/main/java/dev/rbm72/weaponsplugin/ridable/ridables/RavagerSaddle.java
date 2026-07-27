package dev.rbm72.weaponsplugin.ridable.ridables;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.ridable.Ridable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/** Rides any live Ravager. Trample Charge: a short forward dash that tramples anything in its way. */
public final class RavagerSaddle extends Ridable {

    private static final double CHARGE_RANGE = 4.0;
    private static final double CHARGE_DAMAGE = 6.0;

    public RavagerSaddle(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "ravager_saddle";
    }

    @Override
    public Material material() {
        return Material.SADDLE;
    }

    @Override
    public String displayNameText() {
        return "Ravager Saddle";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public EntityType targetEntityType() {
        return EntityType.RAVAGER;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Tames and lets you ride any live", NamedTextColor.GRAY),
                Component.text("Ravager you find.", NamedTextColor.GRAY));
    }

    @Override
    public String abilityName() {
        return "Trample Charge";
    }

    @Override
    public List<Component> abilityLore() {
        return List.of(Component.text("Dash forward, trampling anything in your path.", NamedTextColor.GRAY));
    }

    @Override
    public double abilityCooldownSeconds() {
        return 8.0;
    }

    @Override
    public void ability(Player rider, LivingEntity mount) {
        Vector direction = mount.getLocation().getDirection().setY(0).normalize();
        mount.setVelocity(direction.clone().multiply(2.2).setY(0.25));

        Fx.burst(mount.getLocation(), Particle.CLOUD, 20, 0.6);
        Fx.sound(mount.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.0f);
        Fx.sound(mount.getLocation(), Sound.ENTITY_RAVAGER_STEP, 1.2f, 0.7f);

        for (Entity nearby : mount.getNearbyEntities(CHARGE_RANGE, CHARGE_RANGE / 2, CHARGE_RANGE)) {
            if (!(nearby instanceof LivingEntity target) || nearby == rider) {
                continue;
            }
            Vector toTarget = target.getLocation().toVector().subtract(mount.getLocation().toVector()).setY(0);
            if (toTarget.lengthSquared() < 0.01 || toTarget.normalize().dot(direction) < 0.4) {
                continue;
            }
            target.damage(CHARGE_DAMAGE, mount);
            target.setVelocity(direction.clone().multiply(1.3).setY(0.4));
            Fx.coloredBurst(target.getLocation().add(0, 1, 0), Color.fromRGB(120, 90, 60), 1.2f, 12, 0.3);
        }
    }
}
