package dev.rbm72.weaponsplugin.ridable.ridables;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.ridable.Ridable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.util.Vector;

import java.util.List;

/** Rides any live Wither. Skull Volley: launches three wither skulls at your crosshair. */
public final class WitherSaddle extends Ridable {

    public WitherSaddle(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "wither_saddle";
    }

    @Override
    public Material material() {
        return Material.WITHER_SKELETON_SKULL;
    }

    @Override
    public String displayNameText() {
        return "Wither Saddle";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public EntityType targetEntityType() {
        return EntityType.WITHER;
    }

    @Override
    public boolean flies() {
        return true;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Tames and lets you ride any live", NamedTextColor.GRAY),
                Component.text("Wither you find.", NamedTextColor.GRAY));
    }

    @Override
    public String abilityName() {
        return "Skull Volley";
    }

    @Override
    public List<Component> abilityLore() {
        return List.of(Component.text("Launch three wither skulls at your crosshair.", NamedTextColor.GRAY));
    }

    @Override
    public double abilityCooldownSeconds() {
        return 9.0;
    }

    @Override
    public void ability(Player rider, LivingEntity mount) {
        Vector base = rider.getEyeLocation().getDirection();
        Location origin = mount.getLocation().add(0, 1.8, 0);

        for (int i = -1; i <= 1; i++) {
            Vector spread = base.clone().add(new Vector(i * 0.15, 0, i * 0.15));
            Location spawnAt = origin.clone().add(base.getX() * i * 0.5, 0, base.getZ() * i * 0.5);
            WitherSkull skull = spawnAt.getWorld().spawn(spawnAt, WitherSkull.class);
            skull.setShooter(mount);
            skull.setVelocity(spread.multiply(1.4));
        }

        Fx.burst(origin, Particle.SMOKE, 15, 0.3);
        Fx.sound(mount.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.0f);
    }
}
