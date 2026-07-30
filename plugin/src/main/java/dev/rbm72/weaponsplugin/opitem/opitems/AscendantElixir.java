package dev.rbm72.weaponsplugin.opitem.opitems;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.opitem.PotionOpItem;
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
 * The god potion: Resistance, Regeneration and Speed all at once, at the top of their range, for minutes.
 * <p>
 * Every level and duration is config-backed, and the defaults sit at the vanilla brewing ceiling for the two
 * that have one (Regeneration II, Speed II) with Resistance at IV — one short of the immunity that amplifier 4
 * grants, because "cannot be damaged at all" is a different item and should be asked for on purpose.
 * <p>
 * The endless counterpart is {@link EternalDraught}; {@link TitansTonic} is the offensive one.
 */
public final class AscendantElixir extends PotionOpItem {

    private static final Color ELIXIR_GOLD = Color.fromRGB(255, 215, 90);

    public AscendantElixir(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "ascendant_elixir";
    }

    @Override
    public String displayNameText() {
        return "Ascendant Elixir";
    }

    @Override
    public NamedTextColor accentColor() {
        return NamedTextColor.GOLD;
    }

    @Override
    protected Color bottleColor() {
        return ELIXIR_GOLD;
    }

    @Override
    protected List<Infusion> infusions() {
        return List.of(
                new Infusion(PotionEffectType.RESISTANCE, "resistance-amplifier", 3, "Resistance"),
                new Infusion(PotionEffectType.REGENERATION, "regeneration-amplifier", 1, "Regeneration"),
                new Infusion(PotionEffectType.SPEED, "speed-amplifier", 1, "Speed"));
    }

    @Override
    public List<Component> description() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("For " + formatDuration(durationTicks()) + ":", NamedTextColor.GOLD));
        lore.addAll(infusionLines(NamedTextColor.GOLD));
        lore.add(Component.empty());
        lore.add(Component.text("Refreshes from zero each time —", NamedTextColor.GRAY));
        lore.add(Component.text("no stacking, no waiting it out.", NamedTextColor.GRAY));
        return lore;
    }

    private int durationTicks() {
        return Math.max(20, configInt("duration-ticks", 6000));
    }

    @Override
    public boolean onUse(Player player) {
        int ticks = durationTicks();
        for (Infusion infusion : infusions()) {
            // ambient=false, particles=true: an op buff this large should be visible on the player wearing it,
            // both to them and to everyone else in the fight.
            player.addPotionEffect(new PotionEffect(infusion.type(), ticks, amplifier(infusion), false, true, true));
        }

        plugin.actionBarHub().flash(player,
                Component.text("Ascendant — " + formatDuration(ticks), NamedTextColor.GOLD),
                2000, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        Fx.coloredBurst(player.getEyeLocation(), ELIXIR_GOLD, 1.8f, 30, 0.6);
        Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
        return true;
    }
}
