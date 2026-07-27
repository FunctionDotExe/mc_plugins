package dev.rbm72.weaponsplugin.stone.stones;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.stone.Stone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

/**
 * Marker/config only — the continuous physics (detecting a wall, cancelling fall speed, tracking
 * stamina) needs a per-tick loop over movement rather than the twice-a-second passive-buff hook
 * every other stone uses, so it lives in {@code StoneWallRunTask} instead. This class just carries
 * the item identity/tooltip and the tunables that task reads.
 */
public final class CliffwalkerStone extends Stone {

    /** How long a continuous wall-run can be sustained before stamina runs out, in ticks. */
    public static final int MAX_STAMINA_TICKS = 40;

    public CliffwalkerStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "cliffwalker_stone";
    }

    @Override
    public Material material() {
        return Material.SPIDER_EYE;
    }

    @Override
    public String displayNameText() {
        return "Cliffwalker Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Look at a wall mid-air to catch", NamedTextColor.GRAY),
                Component.text("it and run along its surface —", NamedTextColor.GRAY),
                Component.text("steer with your camera, for up", NamedTextColor.GRAY),
                Component.text("to 2s before you drop off.", NamedTextColor.GRAY));
    }
}
