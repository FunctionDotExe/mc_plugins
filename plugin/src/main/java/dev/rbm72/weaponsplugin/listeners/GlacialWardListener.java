package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.armor.ArmorManager;
import dev.rbm72.weaponsplugin.armor.sets.GlacialWard;
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
 * The two damage-reactive tiers of {@link GlacialWard} that can't live on the set class itself:
 * 3pc Frostbite (chance to slow/fatigue melee attackers) and the full-set Absolute Zero comeback
 * root. Both key off the wearer taking damage, so they share one listener, same shape as
 * {@link PenanceListener}.
 */
public final class GlacialWardListener implements Listener {

    private static final double FROSTBITE_CHANCE = 0.35;
    private static final long ABSOLUTE_ZERO_COOLDOWN_MS = 45_000;
    private static final double ABSOLUTE_ZERO_HEALTH_FRACTION = 0.25;
    private static final double ABSOLUTE_ZERO_RADIUS = 5.0;

    private final WeaponsPlugin plugin;
    private final ArmorManager armor;
    private final GlacialWard glacialWard;
    private final Map<UUID, Long> absoluteZeroReadyAtMs = new HashMap<>();

    public GlacialWardListener(WeaponsPlugin plugin, ArmorManager armor, GlacialWard glacialWard) {
        this.plugin = plugin;
        this.armor = armor;
        this.glacialWard = glacialWard;
    }

    /** 3pc Frostbite: chance to afflict a melee attacker with slowness and mining fatigue. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (armor.wornCount(victim, glacialWard) < 3) {
            return;
        }
        if (!(event.getDamager() instanceof LivingEntity attacker) || attacker.equals(victim)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= FROSTBITE_CHANCE) {
            return;
        }

        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true));
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 0, false, true));
        Fx.coloredBurst(attacker.getLocation().add(0, 1, 0), GlacialWard.FROST_COLOR, 1.2f, 14, 0.35);
        Fx.sound(victim, Sound.BLOCK_GLASS_BREAK, 0.5f, 0.8f);
    }

    /** Full-set Absolute Zero: once per cooldown, dipping below 25% health roots nearby enemies in place. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!armor.fullSet(victim, glacialWard)) {
            return;
        }

        double maxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healthAfter = Math.max(0, victim.getHealth() - event.getFinalDamage());
        if (healthAfter / maxHealth > ABSOLUTE_ZERO_HEALTH_FRACTION) {
            return;
        }

        long now = System.currentTimeMillis();
        Long readyAt = absoluteZeroReadyAtMs.get(victim.getUniqueId());
        if (readyAt != null && now < readyAt) {
            return;
        }
        absoluteZeroReadyAtMs.put(victim.getUniqueId(), now + ABSOLUTE_ZERO_COOLDOWN_MS);

        victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, false, true, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, false, true, true));
        victim.sendMessage(Component.text("Absolute Zero", NamedTextColor.AQUA)
                .append(Component.text(" — the frost holds its ground.", NamedTextColor.GRAY)));

        Fx.sound(victim, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.6f);
        Fx.coloredBurst(victim.getLocation().add(0, 1, 0), GlacialWard.FROST_COLOR, 2.0f, 40, 0.8);
        Fx.point(victim.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 30);
        Fx.expandingRings(plugin, victim.getLocation(), Particle.SNOWFLAKE, ABSOLUTE_ZERO_RADIUS, 4, 2L);

        for (Entity nearby : victim.getNearbyEntities(ABSOLUTE_ZERO_RADIUS, ABSOLUTE_ZERO_RADIUS, ABSOLUTE_ZERO_RADIUS)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(victim)) {
                continue;
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3, false, true));
            living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1, false, true));
            Fx.coloredBurst(living.getLocation().add(0, 1, 0), GlacialWard.FROST_COLOR, 1.0f, 10, 0.3);
        }
    }
}
