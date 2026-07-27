package dev.rbm72.weaponsplugin.listeners;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ComboTracker;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.ability.CooldownManager.Slot;
import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import dev.rbm72.weaponsplugin.boss.BossManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.concurrent.ThreadLocalRandom;

/** Fires each weapon's onMeleeDamage/onKill passive hooks on melee hits, plus every universal combat mechanic layered on top: crit, execute, combo finishers, PvP scaling, poise/guard-break, and floating damage numbers. */
public final class WeaponDamageListener implements Listener {

    /** Every Nth hit in a combo streak lands a bonus-damage finisher and refunds ability1's cooldown. */
    private static final int FINISHER_INTERVAL = 5;
    /** How long a combat notice owns the action bar before the merged cooldown line comes back. */
    private static final long COMBAT_NOTICE_MS = 1200;
    private static final double FINISHER_DAMAGE_MULTIPLIER = 1.4;

    private final WeaponsPlugin plugin;
    private final WeaponRegistry registry;
    private final AccessoryManager accessories;
    private final CooldownManager cooldowns;
    private final BossManager bossManager;
    private final ComboTracker combos = new ComboTracker();

    public WeaponDamageListener(WeaponsPlugin plugin, WeaponRegistry registry, AccessoryManager accessories,
                                 CooldownManager cooldowns, BossManager bossManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.accessories = accessories;
        this.cooldowns = cooldowns;
        this.bossManager = bossManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        // A multi-part boss (Ender Dragon) reports one of its hitbox parts as the damaged entity,
        // never the dragon itself — unwrap to the real LivingEntity before anything else runs, or
        // every one of this method's checks below silently misses for that boss.
        Entity damaged = event.getEntity() instanceof ComplexEntityPart part ? part.getParent() : event.getEntity();
        if (!(damaged instanceof LivingEntity victim)) {
            return;
        }
        Weapon weapon = registry.identify(attacker.getInventory().getItemInMainHand()).orElse(null);
        if (weapon == null) {
            return;
        }

        boolean crit = rollCrit(attacker, weapon, event);
        boolean execute = applyExecuteBonus(weapon, victim, event);
        int comboStreak = applyCombo(attacker, weapon, victim, event);
        applyPvpScaling(weapon, victim, event);

        Fx.coloredBurst(victim.getEyeLocation(), weapon.rarity().bukkitColor(), 0.8f, 6 + weapon.rarity().ordinal() * 2, 0.15);

        weapon.onMeleeDamage(attacker, victim, event);
        accessories.onWeaponHit(attacker, weapon, victim, event);

        applyPoise(weapon, victim);
        Fx.damageNumber(plugin, victim.getEyeLocation(), event.getDamage(), crit, execute);

        if (comboStreak > 0 && comboStreak % FINISHER_INTERVAL == 0) {
            plugin.actionBarHub().flash(attacker, Component.text("COMBO x" + comboStreak + "!", NamedTextColor.GOLD),
                    COMBAT_NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
        }

        if (victim.getHealth() - event.getFinalDamage() <= 0) {
            killFx(victim, weapon);
            weapon.onKill(attacker, victim);
        }
    }

    /** Universal "kill confirmed" beat, layered under whatever the weapon's own onKill hook adds. */
    private void killFx(LivingEntity victim, Weapon weapon) {
        Fx.sound(victim.getLocation(), Sound.ENTITY_WITHER_SKELETON_DEATH, 0.35f, 1.8f);
        Fx.coloredBurst(victim.getEyeLocation(), weapon.rarity().bukkitColor(), 1.1f, 18, 0.4);
        Fx.burst(victim.getLocation().add(0, 1, 0), Particle.SOUL, 10, 0.3);
    }

    private boolean rollCrit(Player attacker, Weapon weapon, EntityDamageByEntityEvent event) {
        if (ThreadLocalRandom.current().nextDouble() >= weapon.critChance()) {
            return false;
        }
        event.setDamage(event.getDamage() * weapon.critMultiplier());
        Fx.sound(attacker, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.1f);
        Fx.burst(event.getEntity().getLocation().add(0, 1, 0), Particle.CRIT, 14, 0.35);
        return true;
    }

    private boolean applyExecuteBonus(Weapon weapon, LivingEntity victim, EntityDamageByEntityEvent event) {
        AttributeInstance maxHealthAttr = victim.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : victim.getHealth();
        if (maxHealth <= 0) {
            return false;
        }
        if (victim.getHealth() / maxHealth <= weapon.executeThresholdFraction()) {
            event.setDamage(event.getDamage() * weapon.executeBonusMultiplier());
            return true;
        }
        return false;
    }

    private int applyCombo(Player attacker, Weapon weapon, LivingEntity victim, EntityDamageByEntityEvent event) {
        int streak = combos.registerHit(attacker.getUniqueId(), victim.getUniqueId());
        if (streak % FINISHER_INTERVAL == 0) {
            event.setDamage(event.getDamage() * FINISHER_DAMAGE_MULTIPLIER);
            cooldowns.reduce(attacker, weapon.id(), Slot.ABILITY1, weapon.ability1CooldownSeconds());
            Fx.sound(attacker, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
            Fx.coloredRing(victim.getLocation().add(0, 1, 0), weapon.rarity().bukkitColor(), 0.9f, 1.0, 20, 0);
        }
        return streak;
    }

    private void applyPvpScaling(Weapon weapon, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (victim instanceof Player) {
            event.setDamage(event.getDamage() * weapon.pvpDamageMultiplier());
        }
    }

    private void applyPoise(Weapon weapon, LivingEntity victim) {
        int staggerTicks = weapon.poiseStaggerTicks();
        if (staggerTicks <= 0) {
            return;
        }
        bossManager.instanceFor(victim.getUniqueId()).ifPresentOrElse(
                instance -> instance.stagger(staggerTicks),
                () -> {
                    if (victim instanceof Player blockingPlayer && blockingPlayer.isBlocking()) {
                        blockingPlayer.setCooldown(Material.SHIELD, staggerTicks);
                        plugin.actionBarHub().flash(blockingPlayer,
                                Component.text("Guard Broken!", NamedTextColor.RED),
                                COMBAT_NOTICE_MS, ActionBarHub.PRIORITY_NOTICE);
                    }
                });
    }
}
