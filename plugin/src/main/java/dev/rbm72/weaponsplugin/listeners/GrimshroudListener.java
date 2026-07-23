package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.armor.ArmorManager;
import dev.rbm72.weaponsplugin.armor.sets.GrimshroudPlate;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
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
 * The two damage-reactive tiers of {@link GrimshroudPlate} that can't live on the set class
 * itself: 3pc Soul Drain (heal the wearer on melee hits dealt) and the full-set Death's Bargain
 * cheat-death comeback. One keys off dealing damage, the other off taking it, same shape as
 * {@link CinderforgedListener}.
 */
public final class GrimshroudListener implements Listener {

    private static final double SOUL_DRAIN_FRACTION = 0.15;
    private static final long BARGAIN_COOLDOWN_MS = 60_000;
    private static final double BARGAIN_SURVIVE_HEALTH_FRACTION = 0.2;

    private final ArmorManager armor;
    private final GrimshroudPlate grimshroudPlate;
    private final Map<UUID, Long> bargainReadyAtMs = new HashMap<>();

    public GrimshroudListener(ArmorManager armor, GrimshroudPlate grimshroudPlate) {
        this.armor = armor;
        this.grimshroudPlate = grimshroudPlate;
    }

    /** 3pc Soul Drain: the wearer's melee hits heal them for a fraction of the damage dealt. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (armor.wornCount(attacker, grimshroudPlate) < 3) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim) || victim.equals(attacker)) {
            return;
        }

        double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healed = event.getFinalDamage() * SOUL_DRAIN_FRACTION;
        attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + healed));

        Fx.coloredBurst(victim.getLocation().add(0, 1, 0), GrimshroudPlate.GRIM_COLOR, 1.0f, 10, 0.3);
        Fx.point(attacker.getLocation().add(0, 1, 0), Particle.SOUL, 8);
        Fx.sound(attacker, Sound.ENTITY_WITHER_HURT, 0.3f, 1.6f);
    }

    /** Full-set Death's Bargain: once per cooldown, a fatal hit instead leaves the wearer alive at a health fraction. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!armor.fullSet(victim, grimshroudPlate)) {
            return;
        }
        if (event.getFinalDamage() < victim.getHealth()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long readyAt = bargainReadyAtMs.get(victim.getUniqueId());
        if (readyAt != null && now < readyAt) {
            return;
        }
        bargainReadyAtMs.put(victim.getUniqueId(), now + BARGAIN_COOLDOWN_MS);

        event.setCancelled(true);
        double maxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
        victim.setHealth(Math.max(1.0, maxHealth * BARGAIN_SURVIVE_HEALTH_FRACTION));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, false, true, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true, true));
        victim.sendMessage(Component.text("Death's Bargain", NamedTextColor.DARK_GREEN)
                .append(Component.text(" — the grave isn't ready for you yet.", NamedTextColor.GRAY)));

        Fx.point(victim.getLocation().add(0, 1, 0), Particle.SOUL, 40);
        Fx.point(victim.getLocation().add(0, 1, 0), Particle.SMOKE, 30);
        Fx.coloredBurst(victim.getLocation().add(0, 1, 0), GrimshroudPlate.GRIM_COLOR, 2.2f, 45, 0.8);
        Fx.sound(victim, Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.6f);
    }
}
