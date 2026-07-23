package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Scales weapon damage by the attacker's equipped accessories. Runs before the
 * MONITOR-priority passive hooks so lifesteal and kill checks see the boosted
 * number. Covers both melee swings and the ability self-damage calls weapons
 * make via {@code entity.damage(amount, player)} (damager is the player), plus
 * projectiles the player fired.
 */
public final class AccessoryDamageListener implements Listener {

    private final WeaponRegistry registry;
    private final AccessoryManager accessories;

    public AccessoryDamageListener(WeaponRegistry registry, AccessoryManager accessories) {
        this.registry = registry;
        this.accessories = accessories;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        Weapon weapon = registry.identify(attacker.getInventory().getItemInMainHand()).orElse(null);
        if (weapon == null) {
            return;
        }
        double multiplier = accessories.damageMultiplier(attacker, weapon);
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    private Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
