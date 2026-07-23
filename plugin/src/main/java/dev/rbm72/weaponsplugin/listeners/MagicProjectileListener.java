package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;

public final class MagicProjectileListener implements Listener {

    private final WeaponsPlugin plugin;
    private final WeaponRegistry registry;

    public MagicProjectileListener(WeaponsPlugin plugin, WeaponRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String weaponId = projectile.getPersistentDataContainer().get(Weapon.idKey(plugin), PersistentDataType.STRING);
        if (weaponId == null) {
            return;
        }
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }

        registry.get(weaponId).ifPresent(weapon -> weapon.onProjectileHit(shooter, event));
    }
}
