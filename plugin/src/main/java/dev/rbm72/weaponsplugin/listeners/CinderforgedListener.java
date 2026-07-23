package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.armor.ArmorManager;
import dev.rbm72.weaponsplugin.armor.sets.CinderforgedPlate;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
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
 * The two damage-reactive tiers of {@link CinderforgedPlate} that can't live on the set class
 * itself: 3pc Molten Retribution (ignite melee attackers) and the full-set Eruption comeback
 * nova. Both key off the wearer taking damage, so they share one listener, same shape as
 * {@link PenanceListener}.
 */
public final class CinderforgedListener implements Listener {

    private static final int RETRIBUTION_FIRE_TICKS = 60;
    private static final double RETRIBUTION_BURN_DAMAGE = 1.5;
    private static final long ERUPTION_COOLDOWN_MS = 45_000;
    private static final double ERUPTION_HEALTH_FRACTION = 0.3;
    private static final double ERUPTION_RADIUS = 5.0;
    private static final double ERUPTION_DAMAGE = 5.0;
    private static final int ERUPTION_FIRE_TICKS = 60;

    private final ArmorManager armor;
    private final CinderforgedPlate cinderforgedPlate;
    private final Map<UUID, Long> eruptionReadyAtMs = new HashMap<>();

    public CinderforgedListener(ArmorManager armor, CinderforgedPlate cinderforgedPlate) {
        this.armor = armor;
        this.cinderforgedPlate = cinderforgedPlate;
    }

    /** 3pc Molten Retribution: melee attackers are set alight and take a burn tick for the trouble. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (armor.wornCount(victim, cinderforgedPlate) < 3) {
            return;
        }
        if (!(event.getDamager() instanceof LivingEntity attacker) || attacker.equals(victim)) {
            return;
        }

        attacker.setFireTicks(Math.max(attacker.getFireTicks(), RETRIBUTION_FIRE_TICKS));
        attacker.damage(RETRIBUTION_BURN_DAMAGE, victim);
        Fx.coloredBurst(attacker.getLocation().add(0, 1, 0), CinderforgedPlate.CINDER_COLOR, 1.2f, 14, 0.35);
        Fx.sound(victim, Sound.ITEM_FIRECHARGE_USE, 0.5f, 1.1f);
    }

    /** Full-set Eruption: once per cooldown, dipping below 30% health detonates a cinder nova on nearby enemies. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!armor.fullSet(victim, cinderforgedPlate)) {
            return;
        }

        double maxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healthAfter = Math.max(0, victim.getHealth() - event.getFinalDamage());
        if (healthAfter / maxHealth > ERUPTION_HEALTH_FRACTION) {
            return;
        }

        long now = System.currentTimeMillis();
        Long readyAt = eruptionReadyAtMs.get(victim.getUniqueId());
        if (readyAt != null && now < readyAt) {
            return;
        }
        eruptionReadyAtMs.put(victim.getUniqueId(), now + ERUPTION_COOLDOWN_MS);

        victim.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, false, true, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, false, true, true));
        victim.sendMessage(Component.text("Eruption", NamedTextColor.GOLD)
                .append(Component.text(" — the forge answers.", NamedTextColor.GRAY)));

        Fx.sound(victim, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        Fx.coloredBurst(victim.getLocation().add(0, 1, 0), CinderforgedPlate.CINDER_COLOR, 2.0f, 40, 0.8);
        Fx.point(victim.getLocation().add(0, 1, 0), Particle.FLAME, 30);

        for (Entity nearby : victim.getNearbyEntities(ERUPTION_RADIUS, ERUPTION_RADIUS, ERUPTION_RADIUS)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(victim)) {
                continue;
            }
            living.damage(ERUPTION_DAMAGE, victim);
            living.setFireTicks(Math.max(living.getFireTicks(), ERUPTION_FIRE_TICKS));
            Fx.coloredBurst(living.getLocation().add(0, 1, 0), CinderforgedPlate.CINDER_COLOR, 1.0f, 10, 0.3);
        }
    }
}
