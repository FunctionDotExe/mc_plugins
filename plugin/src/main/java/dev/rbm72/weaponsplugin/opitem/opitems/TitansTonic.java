package dev.rbm72.weaponsplugin.opitem.opitems;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.opitem.PotionOpItem;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * The offensive half of the shelf: everything that makes a player hit harder, mine faster and move further,
 * for minutes at a time.
 * <p>
 * Timed rather than endless on purpose. The infinite bottles are defensive — being unkillable while you build
 * is a state, not an event — but Strength IV is a thing you turn on for a fight, and a duration is what makes
 * it read as one. Absorption comes along as the fifth so the burst window has its own health bar rather than
 * eating into the real one.
 */
public final class TitansTonic extends PotionOpItem {

    private static final Color TONIC_CRIMSON = Color.fromRGB(220, 60, 40);

    public TitansTonic(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "titans_tonic";
    }

    @Override
    public String displayNameText() {
        return "Titan's Tonic";
    }

    @Override
    public NamedTextColor accentColor() {
        return NamedTextColor.RED;
    }

    @Override
    protected Color bottleColor() {
        return TONIC_CRIMSON;
    }

    @Override
    public Sound useSound() {
        return Sound.ENTITY_RAVAGER_ROAR;
    }

    @Override
    protected List<Infusion> infusions() {
        return List.of(
                new Infusion(PotionEffectType.STRENGTH, "strength-amplifier", 3, "Strength"),
                new Infusion(PotionEffectType.HASTE, "haste-amplifier", 2, "Haste"),
                new Infusion(PotionEffectType.SPEED, "speed-amplifier", 2, "Speed"),
                new Infusion(PotionEffectType.JUMP_BOOST, "jump-amplifier", 2, "Jump Boost"),
                new Infusion(PotionEffectType.ABSORPTION, "absorption-amplifier", 3, "Absorption"));
    }

    private int durationTicks() {
        return Math.max(20, configInt("duration-ticks", 4800));
    }

    @Override
    public List<Component> description() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("For " + formatDuration(durationTicks()) + ":", NamedTextColor.RED));
        lore.addAll(infusionLines(NamedTextColor.RED));
        lore.add(Component.empty());
        lore.add(Component.text("Refreshes from zero each time —", NamedTextColor.GRAY));
        lore.add(Component.text("no stacking, no waiting it out.", NamedTextColor.GRAY));
        return lore;
    }

    @Override
    public boolean onUse(Player player) {
        int ticks = durationTicks();
        for (Infusion infusion : infusions()) {
            player.addPotionEffect(new PotionEffect(infusion.type(), ticks, amplifier(infusion), false, true, true));
        }

        plugin.actionBarHub().flash(player,
                Component.text("Titan's Tonic — " + formatDuration(ticks), NamedTextColor.RED),
                2000, ActionBarHub.PRIORITY_NOTICE);
        Fx.coloredBurst(player.getEyeLocation(), TONIC_CRIMSON, 1.8f, 30, 0.6);
        Fx.point(player.getLocation().add(0, 1, 0), Particle.ANGRY_VILLAGER, 4);
        Fx.sound(player, Sound.ENTITY_RAVAGER_STUNNED, 0.7f, 0.8f);
        return true;
    }
}
