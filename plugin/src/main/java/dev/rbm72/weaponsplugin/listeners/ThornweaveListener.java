package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.armor.ArmorManager;
import dev.rbm72.weaponsplugin.armor.sets.ThornweaveVestments;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The two damage-reactive tiers of {@link ThornweaveVestments} that can't live on the set class
 * itself: 3pc Thorned Retaliation (reflect damage and poison melee attackers) and the full-set
 * healing comeback. Both key off the wearer taking damage, so they share one listener, same shape
 * as {@link CinderforgedListener}.
 */
public final class ThornweaveListener implements Listener {

    private static final double THORN_REFLECT_FRACTION = 0.3;
    private static final int THORN_POISON_TICKS = 60;
    private static final long HEAL_COOLDOWN_MS = 20_000;
    private static final double HEAL_AMOUNT = 4.0;

    private final ArmorManager armor;
    private final ThornweaveVestments thornweaveVestments;
    private final Map<UUID, Long> healReadyAtMs = new HashMap<>();

    public ThornweaveListener(ArmorManager armor, ThornweaveVestments thornweaveVestments) {
        this.armor = armor;
        this.thornweaveVestments = thornweaveVestments;
    }

    /** 3pc Thorned Retaliation: melee attackers take a fraction of their damage back and are poisoned. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (armor.wornCount(victim, thornweaveVestments) < 3) {
            return;
        }
        if (!(event.getDamager() instanceof LivingEntity attacker) || attacker.equals(victim)) {
            return;
        }

        double reflected = event.getFinalDamage() * THORN_REFLECT_FRACTION;
        if (reflected <= 0) {
            return;
        }
        attacker.damage(reflected, victim);
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.POISON, THORN_POISON_TICKS, 0));
        Fx.coloredBurst(attacker.getLocation().add(0, 1, 0), ThornweaveVestments.THORN_COLOR, 1.2f, 14, 0.35);
        Fx.sound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 0.7f);
    }

    /** Full-set healing comeback: once per cooldown, taking damage heals the wearer a flat chunk of health. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!armor.fullSet(victim, thornweaveVestments)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long readyAt = healReadyAtMs.get(victim.getUniqueId());
        if (readyAt != null && now < readyAt) {
            return;
        }
        healReadyAtMs.put(victim.getUniqueId(), now + HEAL_COOLDOWN_MS);

        double maxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
        victim.setHealth(Math.min(maxHealth, victim.getHealth() + HEAL_AMOUNT));

        Fx.coloredBurst(victim.getLocation().add(0, 1, 0), ThornweaveVestments.THORN_COLOR, 1.5f, 24, 0.5);
        Fx.sound(victim, Sound.BLOCK_GRASS_BREAK, 1.0f, 1.2f);
    }
}
