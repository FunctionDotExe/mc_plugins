package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import dev.rbm72.weaponsplugin.boss.BossManager;
import dev.rbm72.weaponsplugin.boss.LootTable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The boss catalog: one icon per registered boss, hover for its health/arena/phase/loot rundown,
 * click to spawn it at your location, shift-click to flip its hard mode. Paginated the same way as
 * {@link WeaponMenu} so the roster can keep growing without ever needing a second menu.
 */
public final class BossMenu {

    public static final Component TITLE = Component.text("Bosses", NamedTextColor.DARK_RED, TextDecoration.BOLD);
    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int PAGE_INDICATOR_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;

    /** Per-boss flavor icon — falls back to a generic skull for anything not listed here. */
    private static final Map<String, Material> ICONS = new HashMap<>();

    static {
        ICONS.put("fallen_king", Material.NETHERITE_SWORD);
        ICONS.put("frost_queen", Material.PACKED_ICE);
        ICONS.put("storm_tyrant", Material.TRIDENT);
        ICONS.put("inferno_warlord", Material.MAGMA_BLOCK);
        ICONS.put("plague_warden", Material.FERMENTED_SPIDER_EYE);
        ICONS.put("void_sovereign", Material.ENDER_EYE);
        ICONS.put("solar_colossus", Material.GOLD_BLOCK);
        ICONS.put("tide_leviathan", Material.HEART_OF_THE_SEA);
        ICONS.put("dragon_elder", Material.PHANTOM_MEMBRANE);
        ICONS.put("necro_overlord", Material.WITHER_ROSE);
        ICONS.put("worldender", Material.NETHER_STAR);
        ICONS.put("grafted_horror", Material.ZOMBIE_HEAD);
        ICONS.put("threefold_bane", Material.WITHER_SKELETON_SKULL);
        ICONS.put("voidwyrm", Material.DRAGON_HEAD);
        ICONS.put("amalgamated_bulk", Material.SLIME_BALL);
        ICONS.put("hollow_choir", Material.AMETHYST_CLUSTER);
        ICONS.put("weeping_colossus", Material.GHAST_TEAR);
    }

    private static NamespacedKey bossIdKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, "boss_menu_id");
    }

    private static NamespacedKey pageDeltaKey(WeaponsPlugin plugin) {
        return new NamespacedKey(plugin, "boss_menu_page_delta");
    }

    private BossMenu() {
    }

    public static Inventory open(WeaponsPlugin plugin, Player viewer) {
        BossMenuHolder holder = new BossMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);
        render(plugin, viewer, holder);
        return inventory;
    }

    public static void render(WeaponsPlugin plugin, Player viewer, BossMenuHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();

        List<Boss> bosses = new ArrayList<>(plugin.bossManager().all());
        int totalPages = Math.max(1, (bosses.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(holder.page(), totalPages - 1));
        holder.setPage(page);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, bosses.size());
        for (int i = start; i < end; i++) {
            inventory.setItem(i - start, icon(plugin, viewer, bosses.get(i)));
        }

        ItemStack filler = MenuStyle.filler();
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(PREV_PAGE_SLOT, page > 0 ? MenuStyle.prevPageButton(pageDeltaKey(plugin)) : filler);
        inventory.setItem(PAGE_INDICATOR_SLOT, MenuStyle.pageIndicator(page, totalPages));
        inventory.setItem(NEXT_PAGE_SLOT, page < totalPages - 1 ? MenuStyle.nextPageButton(pageDeltaKey(plugin)) : filler);
    }

    public static boolean isPageButton(WeaponsPlugin plugin, ItemStack clicked) {
        return clicked != null && clicked.hasItemMeta()
                && clicked.getItemMeta().getPersistentDataContainer().has(pageDeltaKey(plugin), PersistentDataType.INTEGER);
    }

    public static int readPageDelta(WeaponsPlugin plugin, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) {
            return 0;
        }
        Integer delta = clicked.getItemMeta().getPersistentDataContainer()
                .get(pageDeltaKey(plugin), PersistentDataType.INTEGER);
        return delta == null ? 0 : delta;
    }

    /** Reads back the boss id an icon represents, if {@code clicked} is one of ours. */
    public static String readBossId(WeaponsPlugin plugin, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) {
            return null;
        }
        return clicked.getItemMeta().getPersistentDataContainer().get(bossIdKey(plugin), PersistentDataType.STRING);
    }

    private static ItemStack icon(WeaponsPlugin plugin, Player viewer, Boss boss) {
        BossManager manager = plugin.bossManager();
        boolean live = manager.isLive(boss.id());
        List<String> affixes = manager.modifiers().names(boss.id());

        ItemStack item = new ItemStack(ICONS.getOrDefault(boss.id(), Material.SKELETON_SKULL));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(boss.displayName().decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(MenuStyle.border(NamedTextColor.DARK_RED));
        lore.add(Component.text("Health: ", NamedTextColor.GRAY)
                .append(Component.text((long) boss.maxHealth() + " HP (base)", NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Arena radius: ", NamedTextColor.GRAY)
                .append(Component.text(boss.arenaRadius() + " blocks", NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Phases: ", NamedTextColor.GRAY)
                .append(Component.text(boss.phases().size(), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Affixes: ", NamedTextColor.GRAY)
                .append(affixes.isEmpty()
                        ? Component.text("none", NamedTextColor.WHITE)
                        : Component.text(String.join(", ", affixes), NamedTextColor.RED))
                .decoration(TextDecoration.ITALIC, false));

        // The viewer's own history and the clear gate, in the place they are already looking before a
        // pull. A gate that only announces itself by refusing you at the door tells you nothing about
        // what to go and do instead.
        var record = plugin.bossProgress().record(viewer.getUniqueId(), boss.id());
        lore.add(Component.text("Your kills: ", NamedTextColor.GRAY)
                .append(Component.text(record.kills() == 0 ? "never beaten" : String.valueOf(record.kills()),
                        record.kills() == 0 ? NamedTextColor.WHITE : NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        int required = boss.requiredClears();
        if (required > 0) {
            int have = plugin.bossProgress().progress(viewer.getUniqueId()).distinctClearsExcluding(boss.id());
            lore.add((have >= required
                    ? Component.text("Clear gate: open (" + have + "/" + required + ")", NamedTextColor.GREEN)
                    : Component.text("Clear gate: LOCKED (" + have + "/" + required + " bosses beaten)",
                            NamedTextColor.RED))
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Loot:", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        LootTable lootTable = boss.lootTable();
        if (lootTable.hasGuaranteed()) {
            lore.add(Component.text("  Always drops its signature item", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        for (LootTable.RolledDrop drop : lootTable.describeWeighted()) {
            Component name = itemLabel(drop.item());
            lore.add(Component.text("  ", NamedTextColor.WHITE).append(name)
                    .append(Component.text(": " + String.format(Locale.ROOT, "%.2f%%", drop.chance() * 100), NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        if (live) {
            lore.add(Component.text("Already live — cannot spawn another", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Click to spawn at your location", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("Shift-click to toggle hard mode · /bossaffix for the rest", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(MenuStyle.border(NamedTextColor.DARK_RED));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(bossIdKey(plugin), PersistentDataType.STRING, boss.id());
        item.setItemMeta(meta);
        return item;
    }

    private static Component itemLabel(ItemStack item) {
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.displayName();
        }
        return Component.text(item.getType().toString());
    }

}
