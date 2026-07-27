package dev.rbm72.weaponsplugin.consumable;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Runs a consumable use end to end: spend a charge, fire the effect, start the sip cooldown, and
 * put the updated item back in the player's hand. Also the {@link ActionBarHub.Source} that keeps a
 * live charge readout on screen while one is held, so the item's own tooltip never has to be the
 * only place the state is visible.
 */
public final class ConsumableManager implements ActionBarHub.Source {

    private final WeaponsPlugin plugin;

    public ConsumableManager(WeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Spends a charge and applies {@code consumable}'s effect, returning false (and changing
     * nothing) if none are banked. The caller has already checked the use cooldown.
     */
    public boolean use(Player player, Consumable consumable, ItemStack item) {
        if (!ConsumableCharges.consume(plugin, consumable, item)) {
            return false;
        }
        consumable.onUse(player);
        consumable.refreshLore(item);
        // getItemInMainHand can hand back a copy depending on the server implementation, so the
        // charge write above isn't guaranteed to be visible until the stack is put back explicitly.
        player.getInventory().setItemInMainHand(item);
        player.setCooldown(consumable.material(), (int) Math.round(consumable.useCooldownSeconds() * 20));
        Fx.sound(player, consumable.useSound(), 0.8f, 1.2f);
        return true;
    }

    public int charges(Consumable consumable, ItemStack item) {
        return ConsumableCharges.effective(plugin, consumable, item);
    }

    public double secondsToNextCharge(Consumable consumable, ItemStack item) {
        return ConsumableCharges.secondsToNextCharge(plugin, consumable, item);
    }

    /**
     * The held consumable's charge meter, and the countdown to its next charge when it isn't full.
     * Only while it's actually in hand — a pocketful of vials would otherwise bury the cooldown
     * timers this line exists to show.
     */
    @Override
    public List<Component> segments(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return plugin.consumableRegistry().identify(item)
                .map(consumable -> {
                    int max = consumable.maxCharges();
                    int charges = charges(consumable, item);
                    Component meter = consumable.displayName()
                            .append(Component.text(" ", NamedTextColor.GRAY))
                            .append(Component.text("●".repeat(charges), NamedTextColor.GREEN))
                            .append(Component.text("○".repeat(Math.max(0, max - charges)), NamedTextColor.DARK_GRAY));
                    if (charges < max) {
                        meter = meter.append(Component.text(String.format(Locale.ROOT, " %.0fs",
                                Math.ceil(secondsToNextCharge(consumable, item))), NamedTextColor.YELLOW));
                    }
                    return List.of(meter);
                })
                .orElse(List.of());
    }
}
