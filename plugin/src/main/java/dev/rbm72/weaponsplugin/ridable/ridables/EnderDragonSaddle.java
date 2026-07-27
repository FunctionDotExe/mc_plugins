package dev.rbm72.weaponsplugin.ridable.ridables;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.ridable.Ridable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/** Rides any live Ender Dragon. Dragon's Breath: hurls a dragon fireball where you're looking. */
public final class EnderDragonSaddle extends Ridable {

    public EnderDragonSaddle(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "ender_dragon_saddle";
    }

    @Override
    public Material material() {
        return Material.DRAGON_HEAD;
    }

    @Override
    public String displayNameText() {
        return "Ender Dragon Saddle";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public EntityType targetEntityType() {
        return EntityType.ENDER_DRAGON;
    }

    @Override
    public boolean flies() {
        return true;
    }

    @Override
    public double yawOffsetDegrees() {
        return 180;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Tames and lets you ride any live", NamedTextColor.GRAY),
                Component.text("Ender Dragon you find.", NamedTextColor.GRAY));
    }

    @Override
    public String abilityName() {
        return "Dragon's Breath";
    }

    @Override
    public List<Component> abilityLore() {
        return List.of(Component.text("Hurl a dragon fireball where you're looking.", NamedTextColor.GRAY));
    }

    @Override
    public double abilityCooldownSeconds() {
        return 10.0;
    }

    @Override
    public void ability(Player rider, LivingEntity mount) {
        Location eye = mount.getLocation().add(0, 2.5, 0);
        Vector direction = rider.getEyeLocation().getDirection();

        DragonFireball fireball = eye.getWorld().spawn(eye, DragonFireball.class);
        fireball.setShooter(mount);
        fireball.setVelocity(direction.clone().multiply(1.6));

        Fx.dragonBreathBurst(eye, 25, 0.4);
        Fx.sound(mount.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 1.0f);
    }
}
