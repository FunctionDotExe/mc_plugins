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
 * The repositioning one: Slow Falling and Jump Boost, and it ends the fall you are already in.
 * <p>
 * Answers the knock-up and the pit rather than the damage — the arenas that throw you skyward or open the
 * floor are asking a movement question, and drinking a heal at the top of an arc does not answer it. Clearing
 * fall distance on use is the part that matters: the effect alone would still let you land at whatever speed
 * you had already built up before you drank.
 */
public final class FeatherfallDraft extends Consumable {

    private static final Color FEATHER_WHITE = Color.fromRGB(230, 235, 245);

    public FeatherfallDraft(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "featherfall_draft";
    }

    /**
     * Not {@code PHANTOM_MEMBRANE}, which would read better and be wrong: the per-use cooldown is vanilla's own
     * item cooldown, keyed by material, so sharing one with the Warding Poultice would have each drink lock the
     * other out.
     */
    @Override
    public Material material() {
        return Material.FEATHER;
    }

    @Override
    public String displayNameText() {
        return "Featherfall Draft";
    }

    @Override
    public Rarity rarity() {
        return Rarity.COMMON;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Right-click for Slow Falling and", NamedTextColor.GRAY),
                Component.text("Jump Boost II, and to cancel the", NamedTextColor.GRAY),
                Component.text("fall you are already in.", NamedTextColor.GRAY),
                Component.text("Cheap, and always in a pocket.", NamedTextColor.DARK_GRAY));
    }

    @Override
    public int maxCharges() {
        return configInt("max-charges", 4);
    }

    @Override
    public double rechargeSeconds() {
        return configDouble("recharge-seconds", 30.0);
    }

    @Override
    public double useCooldownSeconds() {
        return configDouble("use-cooldown-seconds", 4.0);
    }

    @Override
    public Sound useSound() {
        return Sound.ENTITY_PHANTOM_FLAP;
    }

    @Override
    public void onUse(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                configInt("slow-falling-ticks", 300), 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,
                configInt("jump-ticks", 300), 1));
        // The speed already banked is what would have hurt — the effect only governs what happens next.
        player.setFallDistance(0.0f);
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), FEATHER_WHITE, 1.1f, 22, 0.5);
        Fx.sound(player, Sound.ENTITY_PHANTOM_FLAP, 0.5f, 1.5f);
    }
}
