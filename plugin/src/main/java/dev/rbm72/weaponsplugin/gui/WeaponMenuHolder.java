package dev.rbm72.weaponsplugin.gui;

import dev.rbm72.weaponsplugin.items.Rarity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marker + filter state for the weapon menu, so the click listener can tell our GUI apart from any other. */
public final class WeaponMenuHolder implements InventoryHolder {

    private Inventory inventory;
    private Rarity filter;
    private int page;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Rarity filter() {
        return filter;
    }

    public void setFilter(Rarity filter) {
        this.filter = filter;
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
