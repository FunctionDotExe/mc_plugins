package dev.rbm72.weaponsplugin.realm;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The item that carries a player into a realm. {@code RealmListener} intercepts the right-click
 * before any vanilla item-use logic runs and substitutes a teleport for it.
 */
public final class RealmCrystalItem {

    private static final String KEY = "realm_crystal_id";

    private RealmCrystalItem() {
    }

    public static NamespacedKey key(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    public static ItemStack create(WeaponsPlugin plugin, Realm realm) {
        ItemStack item = new ItemStack(realm.crystalMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Realm Crystal: ", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
                .append(realm.displayName().decoration(TextDecoration.ITALIC, false)));
        meta.lore(List.of(
                Component.text("Right-click to step into this realm.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("You'll arrive in its boss arena.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, realm.id());
        item.setItemMeta(meta);
        return item;
    }

    /** The realm id this crystal carries a player to, or {@code null} if {@code item} isn't one of ours. */
    public static String readRealmId(WeaponsPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
    }
}
