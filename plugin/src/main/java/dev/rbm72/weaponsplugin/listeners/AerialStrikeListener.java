package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import dev.rbm72.weaponsplugin.accessory.accessories.SkyreaverTalons;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

/**
 * Skyreaver Talons' damage bonus needs the attacker's ground state at hit time, which
 * {@link dev.rbm72.weaponsplugin.accessory.Accessory#damageMultiplier} can't see (it only knows the
 * weapon). Runs at the same priority as {@code AccessoryDamageListener} so both damage-scaling passes
 * stack the same way regardless of registration order.
 */
public final class AerialStrikeListener implements Listener {

    private static final double KNOCKUP = 0.2;

    private final AccessoryManager accessories;
    private final SkyreaverTalons skyreaverTalons;

    public AerialStrikeListener(AccessoryManager accessories, SkyreaverTalons skyreaverTalons) {
        this.accessories = accessories;
        this.skyreaverTalons = skyreaverTalons;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (attacker.isOnGround() || !accessories.equipped(attacker).contains(skyreaverTalons)) {
            return;
        }

        event.setDamage(event.getDamage() * (1 + SkyreaverTalons.AERIAL_DAMAGE_BONUS));
        victim.setVelocity(victim.getVelocity().add(new Vector(0, KNOCKUP, 0)));

        Fx.sound(attacker, Sound.ENTITY_PHANTOM_BITE, 0.6f, 1.6f);
        Fx.point(victim.getEyeLocation(), Particle.CRIT, 8);
    }
}
