package dev.rbm72.weaponsplugin.consumable.consumables;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.consumable.Consumable;
import dev.rbm72.weaponsplugin.consumable.Healing;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/** The bread-and-butter heal: a quick, unconditional top-up with the most charges of any consumable. */
public final class LifebloomVial extends Consumable {

    private static final Color BLOOM_GREEN = Color.fromRGB(120, 230, 130);

    public LifebloomVial(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "lifebloom_vial";
    }

    @Override
    public Material material() {
        return Material.GHAST_TEAR;
    }

    @Override
    public String displayNameText() {
        return "Lifebloom Vial";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Right-click to restore ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%.0f", healAmount() / 2) + " hearts", NamedTextColor.GREEN)),
                Component.text("instantly.", NamedTextColor.GRAY));
    }

    @Override
    public int maxCharges() {
        return configInt("max-charges", 3);
    }

    @Override
    public double rechargeSeconds() {
        return configDouble("recharge-seconds", 40.0);
    }

    @Override
    public double useCooldownSeconds() {
        return configDouble("use-cooldown-seconds", 6.0);
    }

    private double healAmount() {
        return configDouble("heal", 8.0);
    }

    @Override
    public void onUse(Player player) {
        Healing.heal(player, healAmount());
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), BLOOM_GREEN, 1.2f, 24, 0.5);
        Fx.sound(player, Sound.ITEM_HONEY_BOTTLE_DRINK, 0.8f, 1.4f);
    }
}
