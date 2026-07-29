package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Shield;
import dev.rbm72.weaponsplugin.items.ShieldRegistry;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the shield's block/parry mechanic on top of vanilla's raise-shield blocking: a hit that
 * lands within {@link Shield#parryWindowMs()} of the shield being raised is a full parry (negated,
 * attacker briefly stunned); anything later is a plain damage-reduced block. Vanilla's own
 * BLOCKING damage modifier is zeroed out and replaced with the shield's own numbers so the two
 * reductions don't stack unpredictably.
 */
public final class ShieldBlockListener implements Listener {

    /** How long a parry notice owns the action bar before the merged cooldown line comes back. */
    private static final long PARRY_NOTICE_MS = 1200;

    private final ShieldRegistry registry;
    private final ActionBarHub actionBar;
    private final Map<UUID, Long> blockStartMs = new HashMap<>();

    public ShieldBlockListener(ShieldRegistry registry, ActionBarHub actionBar) {
        this.registry = registry;
        this.actionBar = actionBar;
    }

    @EventHandler
    public void onRaiseShield(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (registry.identify(event.getPlayer().getInventory().getItemInOffHand()).isEmpty()) {
            return;
        }
        blockStartMs.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    /**
     * {@code DamageModifier} is deprecated with no replacement — the new damage API exposes only the
     * final number, and this handler needs to remove vanilla's own shield reduction specifically so a
     * custom shield's numbers are the only ones that apply. Recomputing that term by hand would mean
     * re-deriving vanilla's blocking formula and drifting from it on every version bump, so the call
     * stays and the deprecation is acknowledged here.
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !victim.isBlocking()) {
            return;
        }
        Shield shield = registry.identify(victim.getInventory().getItemInOffHand()).orElse(null);
        if (shield == null) {
            return;
        }

        if (event.isApplicable(DamageModifier.BLOCKING)) {
            event.setDamage(DamageModifier.BLOCKING, 0.0);
        }

        Long startedAt = blockStartMs.get(victim.getUniqueId());
        long elapsed = startedAt == null ? Long.MAX_VALUE : System.currentTimeMillis() - startedAt;

        if (elapsed <= shield.parryWindowMs()) {
            event.setCancelled(true);
            blockStartMs.remove(victim.getUniqueId());
            parry(victim, event.getDamager(), shield);
        } else {
            double rawDamage = event.getDamage();
            event.setDamage(rawDamage * (1 - shield.blockDamageReduction()));
            reflect(victim, event.getDamager(), shield, rawDamage);
        }
    }

    private void reflect(Player victim, org.bukkit.entity.Entity damager, Shield shield, double rawDamage) {
        if (shield.reflectFraction() <= 0 || !(damager instanceof LivingEntity attacker) || attacker.equals(victim)) {
            return;
        }
        attacker.damage(rawDamage * shield.reflectFraction(), victim);
        Fx.burst(attacker.getLocation().add(0, 1, 0), Particle.CRIT, 10, 0.3);
    }

    private void parry(Player victim, org.bukkit.entity.Entity damager, Shield shield) {
        Fx.sound(victim, Sound.ITEM_SHIELD_BLOCK, 1.4f, 1.6f);
        Fx.burst(victim.getEyeLocation(), Particle.CRIT, 20, 0.4);
        actionBar.flash(victim, Component.text("Parried!", NamedTextColor.AQUA), PARRY_NOTICE_MS,
                ActionBarHub.PRIORITY_NOTICE);

        if (damager instanceof LivingEntity attacker) {
            int ticks = shield.parryStunTicks();
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 4, false, true));
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks, 2, false, true));
            if (attacker instanceof Player attackerPlayer) {
                actionBar.flash(attackerPlayer, Component.text("Parried!", NamedTextColor.RED), PARRY_NOTICE_MS,
                        ActionBarHub.PRIORITY_NOTICE);
            }
        }
    }
}
