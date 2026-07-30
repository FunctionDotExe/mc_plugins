package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

/**
 * Damage costs the wearer health and nothing else. No camera roll, no hop, no movement stall, no hurt
 * sound, no shove — the health bar moves and that is the whole tell.
 * <p>
 * This is deliberately total rather than tick-only. Every one of those reactions is vanilla's
 * {@code hurt()} responding to a damage event, they cannot be suppressed individually, and a source
 * pulsing faster than the i-frame window means one lands roughly every 10 ticks — which is a player who
 * is stunned, unaimable and unable to hold a line for as long as the pressure lasts. An item called an
 * anchor either answers that or it does not; "all but the first hit of each exchange" was not an answer.
 * See {@code AccessoryFlinchListener} for the interception and the i-frame gate it has to carry, and
 * {@code AccessoryKnockbackListener} for the three separate places knockback has to be stopped.
 */
public final class SteadfastAnchor extends Accessory {

    public SteadfastAnchor(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "steadfast_anchor";
    }

    @Override
    public Material material() {
        return Material.LODESTONE;
    }

    @Override
    public String displayNameText() {
        return "Steadfast Anchor";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Immune to knockback.", NamedTextColor.GREEN),
                Component.text("Nothing that hurts you can move you,", NamedTextColor.GREEN),
                Component.text("turn your head or break your stride —", NamedTextColor.GREEN),
                Component.text("no tilt, no stagger, no hurt sound.", NamedTextColor.GREEN),
                Component.text("It still costs exactly as much health.", NamedTextColor.GRAY));
    }

    @Override
    public boolean negatesKnockback() {
        return true;
    }

    @Override
    public boolean negatesTickFlinch() {
        return true;
    }
}
