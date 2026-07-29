package dev.rbm72.weaponsplugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marker + catalog page state for the accessory equip menu. */
public final class AccessoryMenuHolder implements InventoryHolder {

    private Inventory inventory;
    private int page;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
