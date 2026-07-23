package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.armor.ArmorManager;
import dev.rbm72.weaponsplugin.armor.sets.StormplateAegis;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * The two damage-reactive tiers of {@link StormplateAegis} that can't live on the set class
 * itself: 3pc Static Discharge (chance to zap whoever hits you) and the full-set Storm Surge
 * comeback nova. Both key off the wearer taking damage, so they share one listener, same shape as
 * {@link PenanceListener}.
 */
public final class StormplateListener implements Listener {

    private static final double STATIC_DISCHARGE_CHANCE = 0.3;
    private static final double STATIC_DISCHARGE_DAMAGE = 1.5;
    private static final long STORM_SURGE_COOLDOWN_MS = 45_000;
    private static final double STORM_SURGE_HEALTH_FRACTION = 0.3;
    private static final double STORM_SURGE_RADIUS = 5.0;
    private static final double STORM_SURGE_DAMAGE = 5.0;

    private final ArmorManager armor;
    private final StormplateAegis stormplateAegis;
    private final Map<UUID, Long> stormSurgeReadyAtMs = new HashMap<>();

    public StormplateListener(ArmorManager armor, StormplateAegis stormplateAegis) {
        this.armor = armor;
        this.stormplateAegis = stormplateAegis;
    }

    /** 3pc Static Discharge: chance to zap whoever lands a hit with a jolt of slowness and bonus damage. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (armor.wornCount(victim, stormplateAegis) < 3) {
            return;
        }
        if (!(event.getDamager() instanceof LivingEntity attacker) || attacker.equals(victim)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= STATIC_DISCHARGE_CHANCE) {
            return;
        }

        attacker.damage(STATIC_DISCHARGE_DAMAGE, victim);
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, true));
        Fx.coloredBurst(attacker.getLocation().add(0, 1, 0), StormplateAegis.STORM_COLOR, 1.2f, 14, 0.35);
        Fx.sound(victim, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.6f);
    }

    /** Full-set Storm Surge: once per cooldown, dipping below 30% health unleashes a lightning nova on nearby enemies. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!armor.fullSet(victim, stormplateAegis)) {
            return;
        }

        double maxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healthAfter = Math.max(0, victim.getHealth() - event.getFinalDamage());
        if (healthAfter / maxHealth > STORM_SURGE_HEALTH_FRACTION) {
            return;
        }

        long now = System.currentTimeMillis();
        Long readyAt = stormSurgeReadyAtMs.get(victim.getUniqueId());
        if (readyAt != null && now < readyAt) {
            return;
        }
        stormSurgeReadyAtMs.put(victim.getUniqueId(), now + STORM_SURGE_COOLDOWN_MS);

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, true, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, false, true, true));
        victim.sendMessage(Component.text("Storm Surge", NamedTextColor.AQUA)
                .append(Component.text(" — the sky answers.", NamedTextColor.GRAY)));

        Fx.sound(victim, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.2f);
        Fx.coloredBurst(victim.getLocation().add(0, 1, 0), StormplateAegis.STORM_COLOR, 2.0f, 40, 0.8);
        Fx.point(victim.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 30);

        for (Entity nearby : victim.getNearbyEntities(STORM_SURGE_RADIUS, STORM_SURGE_RADIUS, STORM_SURGE_RADIUS)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(victim)) {
                continue;
            }
            living.damage(STORM_SURGE_DAMAGE, victim);
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, true));
            Fx.coloredBurst(living.getLocation().add(0, 1, 0), StormplateAegis.STORM_COLOR, 1.0f, 10, 0.3);
        }
    }
}
