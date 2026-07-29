package dev.rbm72.weaponsplugin.structuregen;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The two states a dungeon treasure map exists in: sealed (bought from a cartographer, tagged with
 * which boss it's for but no location yet) and revealed (right-clicked — {@link DungeonMapRevealListener}
 * has since generated the actual dungeon and baked its entrance into a real {@link MapView}).
 */
public final class DungeonMapItem {

    private static final String SEALED_BOSS_KEY = "sealed_dungeon_map_boss";

    private DungeonMapItem() {
    }

    public static NamespacedKey sealedBossKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, SEALED_BOSS_KEY);
    }

    public static ItemStack createSealed(WeaponsPlugin plugin, String bossId, Component bossDisplayName) {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Sealed Dungeon Map: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(bossDisplayName.decoration(TextDecoration.ITALIC, false)));
        meta.lore(List.of(
                Component.text("Right-click to break the seal.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("A dungeon will be dug out somewhere underground,", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("and this map will show you where.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(sealedBossKey(plugin), PersistentDataType.STRING, bossId);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSealed(WeaponsPlugin plugin, ItemStack item) {
        return sealedBossId(plugin, item) != null;
    }

    public static String sealedBossId(WeaponsPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.MAP || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(sealedBossKey(plugin), PersistentDataType.STRING);
    }

    /** Bakes {@code entrance} into a real filled map: normal terrain rendering plus a fixed red X on top. */
    public static ItemStack createRevealed(Component bossDisplayName, Location entrance) {
        World world = entrance.getWorld();
        MapView view = Bukkit.createMap(world);
        view.setCenterX(entrance.getBlockX());
        view.setCenterZ(entrance.getBlockZ());
        view.setScale(MapView.Scale.FARTHEST);
        view.setTrackingPosition(true);
        view.setUnlimitedTracking(true);
        view.addRenderer(new TargetMarkerRenderer());

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.displayName(Component.text("Dungeon Map: ", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .append(bossDisplayName.decoration(TextDecoration.ITALIC, false)));
        meta.lore(List.of(Component.text("Marks a dig site — the entrance is a ladder shaft down.",
                NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    /** Draws one fixed red X at the map's center — added once, since {@code render} fires every tick per viewer. */
    private static final class TargetMarkerRenderer extends MapRenderer {
        private boolean placed = false;

        TargetMarkerRenderer() {
            super(false);
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (placed) {
                return;
            }
            canvas.getCursors().addCursor(new MapCursor((byte) 0, (byte) 0, (byte) 8, MapCursor.Type.RED_X, true));
            placed = true;
        }
    }
}
