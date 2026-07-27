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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/** Rides any live Phantom. Sonic Swoop: a fast forward burst that damages and blinds anything hit. */
public final class PhantomSaddle extends Ridable {

    private static final double SWOOP_RANGE = 3.5;
    private static final double SWOOP_DAMAGE = 4.0;

    public PhantomSaddle(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "phantom_saddle";
    }

    @Override
    public Material material() {
        return Material.PHANTOM_MEMBRANE;
    }

    @Override
    public String displayNameText() {
        return "Phantom Saddle";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public EntityType targetEntityType() {
        return EntityType.PHANTOM;
    }

    @Override
    public boolean flies() {
        return true;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Tames and lets you ride any live", NamedTextColor.GRAY),
                Component.text("Phantom you find.", NamedTextColor.GRAY));
    }

    @Override
    public String abilityName() {
        return "Sonic Swoop";
    }

    @Override
    public List<Component> abilityLore() {
        return List.of(Component.text("Burst forward, damaging and blinding anything hit.", NamedTextColor.GRAY));
    }

    @Override
    public double abilityCooldownSeconds() {
        return 7.0;
    }

    @Override
    public void ability(Player rider, LivingEntity mount) {
        Vector direction = rider.getEyeLocation().getDirection();
        mount.setVelocity(direction.clone().multiply(2.4));

        Fx.trail(mount.getLocation(), Particle.CLOUD, 15, 0.3, 0.05);
        Fx.sound(mount.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1.2f, 1.0f);

        for (Entity nearby : mount.getNearbyEntities(SWOOP_RANGE, SWOOP_RANGE, SWOOP_RANGE)) {
            if (!(nearby instanceof LivingEntity target) || nearby == rider) {
                continue;
            }
            Vector toTarget = target.getLocation().toVector().subtract(mount.getLocation().toVector());
            if (toTarget.lengthSquared() < 0.01 || toTarget.normalize().dot(direction) < 0.5) {
                continue;
            }
            target.damage(SWOOP_DAMAGE, mount);
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
            Fx.burst(target.getLocation().add(0, 1, 0), Particle.CLOUD, 10, 0.3);
        }
    }
}
