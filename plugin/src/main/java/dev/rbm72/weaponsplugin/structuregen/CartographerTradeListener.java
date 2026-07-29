package dev.rbm72.weaponsplugin.structuregen;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Boss;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gives cartographer villagers a chance to acquire a dungeon-map trade instead of their normal one.
 * Which boss the map is for is picked here, at acquire time — the actual dungeon isn't generated
 * until the map is bought <em>and</em> right-clicked (see {@link DungeonMapRevealListener}), so
 * restocking villagers nobody trades with never costs the world any blocks.
 */
public final class CartographerTradeListener implements Listener {

    private final WeaponsPlugin plugin;

    public CartographerTradeListener(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAcquire(VillagerAcquireTradeEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || villager.getProfession() != Villager.Profession.CARTOGRAPHER) {
            return;
        }
        double chance = plugin.getConfig().getDouble("structures.cartographer-trade-chance", 0.5);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        List<Boss> eligible = plugin.bossManager().all().stream()
                .filter(boss -> plugin.realmRegistry().byBossId(boss.id()).isPresent())
                .toList();
        if (eligible.isEmpty()) {
            return;
        }
        Boss boss = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));

        ItemStack sealedMap = DungeonMapItem.createSealed(plugin, boss.id(), boss.displayName());
        int emeraldCost = plugin.getConfig().getInt("structures.cartographer-trade-cost", 14);
        MerchantRecipe recipe = new MerchantRecipe(sealedMap, 3);
        recipe.addIngredient(new ItemStack(Material.COMPASS));
        recipe.addIngredient(new ItemStack(Material.EMERALD, emeraldCost));
        recipe.setExperienceReward(true);
        recipe.setVillagerExperience(5);
        event.setRecipe(recipe);
    }
}
