package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.armor.ArmorManager;
import dev.rbm72.weaponsplugin.armor.sets.PenitentsVestments;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The two damage-reactive tiers of {@link PenitentsVestments} that can't live on the set class
 * itself: 3pc thorns (reflect damage taken back at whoever dealt it) and the full-set Martyrdom
 * cheat-death. Both key off the wearer taking damage, so they share one listener.
 */
public final class PenanceListener implements Listener {

    private static final long MARTYRDOM_COOLDOWN_MS = 90_000;
    private static final double SHOCKWAVE_RADIUS = 5.0;
    private static final double SHOCKWAVE_DAMAGE = 6.0;

    private final WeaponsPlugin plugin;
    private final ArmorManager armor;
    private final PenitentsVestments vestments;
    private final Map<UUID, Long> martyrdomReadyAtMs = new HashMap<>();

    public PenanceListener(WeaponsPlugin plugin, ArmorManager armor, PenitentsVestments vestments) {
        this.plugin = plugin;
        this.armor = armor;
        this.vestments = vestments;
    }

    /** 3pc thorns: reflect a fraction of the final damage taken back at whoever (or whatever) dealt it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (armor.wornCount(victim, vestments) < 3) {
            return;
        }
        LivingEntity attacker = resolveDamager(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        double reflected = event.getFinalDamage() * PenitentsVestments.THORNS_FRACTION;
        if (reflected <= 0) {
            return;
        }
        attacker.damage(reflected, victim);
        Fx.coloredBurst(attacker.getLocation().add(0, 1, 0), PenitentsVestments.VESTMENT_COLOR, 1.2f, 14, 0.35);
        Fx.sound(victim, Sound.ENTITY_PLAYER_HURT, 0.5f, 0.7f);
    }

    /** Full-set Martyrdom: once per cooldown, a fatal hit instead drops the wearer to 1 HP and detonates a guilt shockwave. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (event.getFinalDamage() < victim.getHealth()) {
            return;
        }
        if (!armor.fullSet(victim, vestments)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long readyAt = martyrdomReadyAtMs.get(victim.getUniqueId());
        if (readyAt != null && now < readyAt) {
            return;
        }
        martyrdomReadyAtMs.put(victim.getUniqueId(), now + MARTYRDOM_COOLDOWN_MS);

        event.setCancelled(true);
        victim.setHealth(1.0);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, false, true, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, false, true, true));
        victim.sendMessage(Component.text("Martyrdom", NamedTextColor.DARK_RED)
                .append(Component.text(" — your sins refuse to let you die.", NamedTextColor.GRAY)));

        Fx.sound(victim, Sound.ITEM_TOTEM_USE, 1.0f, 0.7f);
        Fx.coloredBurst(victim.getLocation().add(0, 1, 0), PenitentsVestments.VESTMENT_COLOR, 2.2f, 45, 0.9);
        Fx.point(victim.getLocation().add(0, 1, 0), Particle.SOUL, 24);
        Fx.expandingRings(plugin, victim.getLocation(), Particle.SOUL_FIRE_FLAME, SHOCKWAVE_RADIUS, 4, 2L);

        for (Entity nearby : victim.getNearbyEntities(SHOCKWAVE_RADIUS, SHOCKWAVE_RADIUS, SHOCKWAVE_RADIUS)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(victim)) {
                continue;
            }
            living.damage(SHOCKWAVE_DAMAGE, victim);
            Vector away = living.getLocation().toVector().subtract(victim.getLocation().toVector());
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            living.setVelocity(away.normalize().multiply(1.2).setY(0.4));
        }
    }

    private LivingEntity resolveDamager(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }
}
