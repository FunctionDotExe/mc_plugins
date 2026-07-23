package dev.rbm72.weaponsplugin.status;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Central chokepoint for every debuff a weapon applies to a victim (slow, poison, wither,
 * blindness, ...). Previously each weapon called {@code victim.addPotionEffect(...)} directly with
 * its own hardcoded amplifier/duration; since Bukkit's {@code addPotionEffect} simply overwrites
 * whatever effect of that type is already running, two weapons hitting the same target independently
 * could stomp each other — a strong slow from one weapon getting silently downgraded moments later
 * by a weaker slow from another, purely based on call order. Routing through {@link #apply} instead
 * takes the max of amplifier and remaining duration against whatever's already active, so stacking
 * multiple sources of the same effect only ever strengthens it, never regresses it.
 */
public final class StatusEffectManager {

    private StatusEffectManager() {
    }

    /** Applies a debuff, merging with any already-active effect of the same type instead of overwriting it. */
    public static void apply(LivingEntity target, PotionEffectType type, int durationTicks, int amplifier) {
        PotionEffect existing = target.getPotionEffect(type);
        int finalAmplifier = amplifier;
        int finalDuration = durationTicks;
        if (existing != null) {
            finalAmplifier = Math.max(existing.getAmplifier(), amplifier);
            finalDuration = Math.max(existing.getDuration(), durationTicks);
        }
        target.addPotionEffect(new PotionEffect(type, finalDuration, finalAmplifier, false, true));
    }
}
