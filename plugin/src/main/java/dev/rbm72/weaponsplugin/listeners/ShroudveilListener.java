package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.armor.ArmorManager;
import dev.rbm72.weaponsplugin.armor.sets.ShroudveilMantle;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
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
 * The two damage-reactive tiers of {@link ShroudveilMantle} that can't live on the set class
 * itself: 3pc Shadowstep Evasion (chance to blink out of an incoming hit entirely) and the
 * full-set Umbral Collapse comeback vanish. Both key off the wearer taking damage, so they share
 * one listener, same shape as {@link CinderforgedListener}.
 */
public final class ShroudveilListener implements Listener {

    private static final double SHADOWSTEP_CHANCE = 0.15;
    private static final double SHADOWSTEP_DISTANCE = 4.0;
    private static final int SHADOWSTEP_ATTEMPTS = 6;
    private static final long UMBRAL_COLLAPSE_COOLDOWN_MS = 40_000;
    private static final double UMBRAL_COLLAPSE_HEALTH_FRACTION = 0.25;
    private static final int UMBRAL_COLLAPSE_DURATION_TICKS = 60;

    private final ArmorManager armor;
    private final ShroudveilMantle shroudveilMantle;
    private final Map<UUID, Long> umbralCollapseReadyAtMs = new HashMap<>();

    public ShroudveilListener(ArmorManager armor, ShroudveilMantle shroudveilMantle) {
        this.armor = armor;
        this.shroudveilMantle = shroudveilMantle;
    }

    /** 3pc Shadowstep Evasion: a chance to fully avoid an incoming hit by blinking a short distance away. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (armor.wornCount(victim, shroudveilMantle) < 3) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= SHADOWSTEP_CHANCE) {
            return;
        }

        event.setCancelled(true);
        Location origin = victim.getLocation();
        Fx.point(origin.clone().add(0, 1, 0), Particle.PORTAL, 20);
        Fx.sound(victim, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);

        Location dodge = findDodgeSpot(origin);
        if (dodge != null) {
            victim.teleport(dodge);
            Fx.point(dodge.clone().add(0, 1, 0), Particle.PORTAL, 20);
            Fx.sound(dodge, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
        }
    }

    /** Full-set Umbral Collapse: once per cooldown, dipping below 25% health slips the wearer into hiding. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!armor.fullSet(victim, shroudveilMantle)) {
            return;
        }

        double maxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healthAfter = Math.max(0, victim.getHealth() - event.getFinalDamage());
        if (healthAfter / maxHealth > UMBRAL_COLLAPSE_HEALTH_FRACTION) {
            return;
        }

        long now = System.currentTimeMillis();
        Long readyAt = umbralCollapseReadyAtMs.get(victim.getUniqueId());
        if (readyAt != null && now < readyAt) {
            return;
        }
        umbralCollapseReadyAtMs.put(victim.getUniqueId(), now + UMBRAL_COLLAPSE_COOLDOWN_MS);

        victim.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, UMBRAL_COLLAPSE_DURATION_TICKS, 0, true, false, false));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, UMBRAL_COLLAPSE_DURATION_TICKS, 1, false, true, true));
        victim.sendMessage(Component.text("Umbral Collapse", NamedTextColor.DARK_PURPLE)
                .append(Component.text(" — the shadows swallow you whole.", NamedTextColor.GRAY)));

        Fx.sound(victim, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        Fx.coloredBurst(victim.getLocation().add(0, 1, 0), ShroudveilMantle.VOID_COLOR, 2.0f, 40, 0.8);
        Fx.point(victim.getLocation().add(0, 1, 0), Particle.SMOKE, 30);
        Fx.point(victim.getLocation().add(0, 1, 0), Particle.PORTAL, 30);
    }

    /** Tries a handful of random horizontal spots at a fixed radius, returning the first one that isn't inside a block. */
    private Location findDodgeSpot(Location origin) {
        for (int i = 0; i < SHADOWSTEP_ATTEMPTS; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            Location candidate = origin.clone().add(Math.cos(angle) * SHADOWSTEP_DISTANCE, 0, Math.sin(angle) * SHADOWSTEP_DISTANCE);
            if (!candidate.getBlock().getType().isSolid() && !candidate.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                candidate.setYaw(origin.getYaw());
                candidate.setPitch(origin.getPitch());
                return candidate;
            }
        }
        return null;
    }
}
