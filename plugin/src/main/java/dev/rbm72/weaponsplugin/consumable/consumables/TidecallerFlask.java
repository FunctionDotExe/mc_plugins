package dev.rbm72.weaponsplugin.consumable.consumables;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.consumable.Consumable;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * The underwater answer: breathe, see, swim fast and mine at full speed while submerged.
 * <p>
 * Exists because the flooded phases are the one place a fight can beat a player on logistics rather than on
 * damage — drowning, mining fatigue underwater and a swim speed that makes the arena twice as wide are all
 * survivable and all miserable. No heal on it deliberately: it buys mobility during a mechanic, and having to
 * choose it over a healing item in the same hotbar slot is the cost.
 */
public final class TidecallerFlask extends Consumable {

    private static final Color TIDE_BLUE = Color.fromRGB(80, 190, 235);

    public TidecallerFlask(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "tidecaller_flask";
    }

    @Override
    public Material material() {
        return Material.NAUTILUS_SHELL;
    }

    @Override
    public String displayNameText() {
        return "Tidecaller's Flask";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Right-click for Water Breathing,", NamedTextColor.GRAY),
                Component.text("Conduit Power and Dolphin's Grace.", NamedTextColor.GRAY),
                Component.text("Breathe, see and swim through a", NamedTextColor.GRAY),
                Component.text("flooded arena. No healing.", NamedTextColor.DARK_GRAY));
    }

    @Override
    public int maxCharges() {
        return configInt("max-charges", 3);
    }

    @Override
    public double rechargeSeconds() {
        return configDouble("recharge-seconds", 50.0);
    }

    @Override
    public double useCooldownSeconds() {
        return configDouble("use-cooldown-seconds", 6.0);
    }

    @Override
    public Sound useSound() {
        return Sound.ITEM_BUCKET_FILL;
    }

    @Override
    public void onUse(Player player) {
        int ticks = configInt("effect-ticks", 900);
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, ticks, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, ticks, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,
                configInt("dolphins-grace-ticks", 400), 0));
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), TIDE_BLUE, 1.2f, 26, 0.6);
        Fx.sound(player, Sound.BLOCK_CONDUIT_ACTIVATE, 0.5f, 1.4f);
    }
}
