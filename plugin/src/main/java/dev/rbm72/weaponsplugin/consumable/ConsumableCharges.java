package dev.rbm72.weaponsplugin.consumable;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Charge bookkeeping for a {@link Consumable}, stored on the item stack itself.
 *
 * <p>Two values are persisted: the charges banked as of a moment, and that moment. Everything else
 * is derived on read, which is what makes recharge work with no ticking task anywhere — a vial in a
 * chest, on the ground, or in an offline player's inventory refills at exactly the same rate as one
 * in hand, because nothing is counting down; the elapsed time simply gets divided by the recharge
 * interval whenever someone looks.
 */
final class ConsumableCharges {

    private static final String CHARGES_KEY = "consumable_charges";
    private static final String ANCHOR_KEY = "consumable_charge_anchor";

    private ConsumableCharges() {
    }

    private static NamespacedKey chargesKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, CHARGES_KEY);
    }

    private static NamespacedKey anchorKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, ANCHOR_KEY);
    }

    /** Stamps a fresh item at full charges. */
    static void initialize(WeaponsPlugin plugin, Consumable consumable, ItemStack item) {
        write(plugin, item, consumable.maxCharges(), System.currentTimeMillis());
    }

    /**
     * Charges available right now: what was banked, plus one for every full recharge interval that
     * has elapsed since, capped at the maximum.
     */
    static int effective(WeaponsPlugin plugin, Consumable consumable, ItemStack item) {
        int max = consumable.maxCharges();
        int stored = storedCharges(plugin, item, max);
        if (stored >= max) {
            return max;
        }
        long interval = rechargeMs(consumable);
        long elapsed = Math.max(0L, System.currentTimeMillis() - anchor(plugin, item));
        return (int) Math.min(max, stored + elapsed / interval);
    }

    /** Seconds until the next charge lands, or 0 when already full. */
    static double secondsToNextCharge(WeaponsPlugin plugin, Consumable consumable, ItemStack item) {
        int max = consumable.maxCharges();
        if (effective(plugin, consumable, item) >= max) {
            return 0;
        }
        long interval = rechargeMs(consumable);
        long elapsed = Math.max(0L, System.currentTimeMillis() - anchor(plugin, item));
        return (interval - (elapsed % interval)) / 1000.0;
    }

    /**
     * Spends one charge if any are available, returning whether it succeeded.
     *
     * <p>The anchor is rewound by however much progress had accrued toward the next charge rather
     * than reset to now, so spending a charge never throws away partial recharge progress — sipping
     * at the wrong moment shouldn't silently cost you most of a refill.
     */
    static boolean consume(WeaponsPlugin plugin, Consumable consumable, ItemStack item) {
        int max = consumable.maxCharges();
        int available = effective(plugin, consumable, item);
        if (available <= 0) {
            return false;
        }
        long interval = rechargeMs(consumable);
        long now = System.currentTimeMillis();
        long carried = 0L;
        if (available < max) {
            long elapsed = Math.max(0L, now - anchor(plugin, item));
            carried = elapsed % interval;
        }
        write(plugin, item, available - 1, now - carried);
        return true;
    }

    private static long rechargeMs(Consumable consumable) {
        return Math.max(1L, Math.round(consumable.rechargeSeconds() * 1000));
    }

    private static int storedCharges(WeaponsPlugin plugin, ItemStack item, int max) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return max;
        }
        Integer stored = meta.getPersistentDataContainer().get(chargesKey(plugin), PersistentDataType.INTEGER);
        // A consumable that predates this data (or was cloned by a creative-mode pick) is treated as
        // full rather than broken — an item you can never use is a worse failure than a free refill.
        return stored == null ? max : Math.max(0, Math.min(max, stored));
    }

    private static long anchor(WeaponsPlugin plugin, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return System.currentTimeMillis();
        }
        Long anchor = meta.getPersistentDataContainer().get(anchorKey(plugin), PersistentDataType.LONG);
        return anchor == null ? System.currentTimeMillis() : anchor;
    }

    private static void write(WeaponsPlugin plugin, ItemStack item, int charges, long anchorMs) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(chargesKey(plugin), PersistentDataType.INTEGER, charges);
        data.set(anchorKey(plugin), PersistentDataType.LONG, anchorMs);
        item.setItemMeta(meta);
    }
}
