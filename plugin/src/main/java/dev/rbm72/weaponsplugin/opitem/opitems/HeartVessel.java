package dev.rbm72.weaponsplugin.opitem.opitems;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.opitem.OpItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Right-click to add a permanent heart to your health bar.
 * <p>
 * The grant goes through {@link dev.rbm72.weaponsplugin.opitem.HeartManager} rather than touching the
 * attribute here, which is what makes the item and {@code /hearts} two views of one number: a heart taken with
 * the command is the same heart a vessel gave, so the two can never disagree about how many a player has. The
 * manager also owns the cap, so a player holding a stack of these stops gaining at the ceiling instead of
 * ending up with a health bar that wraps off the screen.
 * <p>
 * Refuses rather than wastes: at the cap {@link #onUse} returns false and the listener leaves the item in the
 * player's hand.
 */
public final class HeartVessel extends OpItem {

    private static final Color HEART_RED = Color.fromRGB(255, 70, 90);

    public HeartVessel(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "heart_vessel";
    }

    @Override
    public Material material() {
        return Material.GLISTERING_MELON_SLICE;
    }

    @Override
    public String displayNameText() {
        return "Heart Vessel";
    }

    @Override
    public NamedTextColor accentColor() {
        return NamedTextColor.RED;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Adds " + heartsPerUse() + " permanent heart"
                        + (heartsPerUse() == 1 ? "" : "s"), NamedTextColor.RED),
                Component.text("to your health bar — it survives", NamedTextColor.GRAY),
                Component.text("death, relogging and restarts.", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Cap: " + plugin.heartManager().maxHearts() + " bonus hearts.", NamedTextColor.DARK_GRAY),
                Component.text("/hearts shows or edits your tally.", NamedTextColor.DARK_GRAY));
    }

    private int heartsPerUse() {
        return Math.max(1, configInt("hearts-per-use", 1));
    }

    @Override
    public Sound useSound() {
        return Sound.ENTITY_PLAYER_LEVELUP;
    }

    @Override
    public boolean onUse(Player player) {
        int before = plugin.heartManager().hearts(player.getUniqueId());
        int after = plugin.heartManager().add(player, heartsPerUse());
        if (after == before) {
            plugin.actionBarHub().flash(player,
                    Component.text("Already at the bonus-heart cap (" + after + ")", NamedTextColor.RED),
                    1500, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
            Fx.sound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return false;
        }

        // Handed out full: a bonus heart that arrives empty reads as the item having done nothing.
        org.bukkit.attribute.AttributeInstance maxHealth =
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(Math.min(player.getHealth() + (after - before) * 2.0, maxHealth.getValue()));
        }

        plugin.actionBarHub().flash(player,
                Component.text("♥ " + after + " bonus heart" + (after == 1 ? "" : "s"), NamedTextColor.RED),
                1500, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        Fx.coloredBurst(player.getEyeLocation(), HEART_RED, 1.6f, 26, 0.5);
        Fx.point(player.getLocation().add(0, 1, 0), Particle.HEART, 6);
        return true;
    }
}
