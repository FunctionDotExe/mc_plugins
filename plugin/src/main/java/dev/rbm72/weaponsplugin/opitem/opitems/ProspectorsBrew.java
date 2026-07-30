package dev.rbm72.weaponsplugin.opitem.opitems;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.opitem.PotionOpItem;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * The build-and-dig bottle: Haste V flattens obsidian, Night Vision removes the torch, Speed and Luck do the
 * walking and the loot tables.
 * <p>
 * No combat stat on it deliberately, same split as {@link ElementalWard} — the reason to hand this one out is
 * carving an arena or clearing a build site, and an operator doing that should not have to take Strength IV
 * along with it. Timed rather than endless because the job it serves ends.
 */
public final class ProspectorsBrew extends PotionOpItem {

    private static final Color BREW_AMBER = Color.fromRGB(235, 170, 60);

    public ProspectorsBrew(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "prospectors_brew";
    }

    @Override
    public String displayNameText() {
        return "Prospector's Brew";
    }

    @Override
    public NamedTextColor accentColor() {
        return NamedTextColor.YELLOW;
    }

    @Override
    protected Color bottleColor() {
        return BREW_AMBER;
    }

    @Override
    protected List<Infusion> infusions() {
        return List.of(
                new Infusion(PotionEffectType.HASTE, "haste-amplifier", 4, "Haste"),
                new Infusion(PotionEffectType.NIGHT_VISION, "night-vision-amplifier", 0, "Night Vision"),
                new Infusion(PotionEffectType.SPEED, "speed-amplifier", 1, "Speed"),
                new Infusion(PotionEffectType.LUCK, "luck-amplifier", 2, "Luck"));
    }

    private int durationTicks() {
        return Math.max(20, configInt("duration-ticks", 12000));
    }

    @Override
    public List<Component> description() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("For " + formatDuration(durationTicks()) + ":", NamedTextColor.YELLOW));
        lore.addAll(infusionLines(NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(Component.text("Digging and travel only —", NamedTextColor.GRAY));
        lore.add(Component.text("nothing here helps you fight.", NamedTextColor.GRAY));
        return lore;
    }

    @Override
    public boolean onUse(Player player) {
        int ticks = durationTicks();
        for (Infusion infusion : infusions()) {
            player.addPotionEffect(new PotionEffect(infusion.type(), ticks, amplifier(infusion), false, true, true));
        }

        plugin.actionBarHub().flash(player,
                Component.text("Prospector's Brew — " + formatDuration(ticks), NamedTextColor.YELLOW),
                2000, ActionBarHub.PRIORITY_NOTICE);
        Fx.coloredBurst(player.getEyeLocation(), BREW_AMBER, 1.6f, 26, 0.5);
        Fx.sound(player, Sound.BLOCK_ANVIL_LAND, 0.4f, 1.7f);
        return true;
    }
}
