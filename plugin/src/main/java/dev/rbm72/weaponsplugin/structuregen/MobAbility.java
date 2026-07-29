package dev.rbm72.weaponsplugin.structuregen;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * One reusable periodic behavior a {@link DungeonMobProfile} can pick, ticked by
 * {@link DungeonInstance}'s watchdog rather than each mob running its own task — the same
 * small-shared-pool idea as {@code bosses/attacks/}, just for dungeon grunts instead of bosses.
 */
public enum MobAbility {

    /** No extra behavior beyond vanilla AI and whatever the base entity type already does. */
    NONE {
        @Override
        void pulse(LivingEntity mob, Player target) {
        }
    },

    /** Looses a real arrow at the nearest player — for grunts without a bow of their own. */
    RANGED_VOLLEY {
        @Override
        void pulse(LivingEntity mob, Player target) {
            var world = mob.getWorld();
            Location from = mob.getEyeLocation();
            Vector direction = target.getEyeLocation().toVector().subtract(from.toVector()).normalize();
            Arrow arrow = world.spawn(from, Arrow.class, a -> {
                a.setVelocity(direction.multiply(1.6));
                a.setShooter(mob);
                a.setDamage(3.0);
            });
            world.playSound(from, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.0f);
        }
    },

    /** Lobs a splash poison potion — the swamp/plague-flavored grunts' answer to melee kiting. */
    POTION_LOB {
        @Override
        void pulse(LivingEntity mob, Player target) {
            var world = mob.getWorld();
            Location from = mob.getEyeLocation();
            Vector direction = target.getLocation().toVector().subtract(from.toVector());
            double distance = direction.length();
            direction.normalize().multiply(Math.min(1.4, 0.35 + distance * 0.045));
            ItemStack potionItem = new ItemStack(Material.SPLASH_POTION);
            PotionMeta meta = (PotionMeta) potionItem.getItemMeta();
            meta.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 0), true);
            potionItem.setItemMeta(meta);
            ThrownPotion potion = world.spawn(from, ThrownPotion.class, p -> {
                p.setItem(potionItem);
                p.setVelocity(direction);
                p.setShooter(mob);
            });
            world.playSound(from, Sound.ENTITY_WITCH_THROW, 1.0f, 1.0f);
        }
    },

    /** A short speed burst toward the nearest player — the lunge-style grunts' answer to kiting. */
    CHARGE {
        @Override
        void pulse(LivingEntity mob, Player target) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.4f);
        }
    };

    abstract void pulse(LivingEntity mob, Player target);
}
