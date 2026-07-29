package dev.rbm72.weaponsplugin.stone.stones;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.stone.Stone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Walks across water and lava by turning their surface into real blocks under the wielder's feet.
 * <p>
 * §0.1 in its most literal form: there is no "water walking" status effect to grant and no particle that
 * would make one, so the stone freezes the fluid instead. What holds the player up is a real
 * {@link Material#PACKED_ICE} block with real ice friction — you slide on your own bridge if you take a
 * corner too fast — and lava is answered with real {@link Material#BASALT}, the block lava actually becomes
 * when it meets something cold. Delete every particle in this file and it still works, which is the test.
 * <p>
 * Every write goes through {@link dev.rbm72.weaponsplugin.items.kit.TempTerrain}, so the crossing is gone
 * within a few seconds of the wielder leaving it: the stone is transport, not a terraforming tool, and a
 * permanent ice road across an ocean is the version of this that would have to be banned.
 */
public final class RimewalkStone extends Stone {

    public RimewalkStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "rimewalk_stone";
    }

    @Override
    public Material material() {
        return Material.BLUE_ICE;
    }

    @Override
    public String displayNameText() {
        return "Rimewalk Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Water freezes to real ice under", NamedTextColor.AQUA),
                Component.text("your feet, and lava chills to", NamedTextColor.GRAY),
                Component.text("basalt. Cross either on foot —", NamedTextColor.GRAY),
                Component.text("the crossing melts behind you.", NamedTextColor.DARK_GRAY),
                Component.text("It is real ice: it is slippery.", NamedTextColor.DARK_GRAY));
    }

    /** How far either side of the wielder the surface sets, in blocks. 1 gives a 3x3 pad. */
    private int radius() {
        return Math.max(0, Math.min(3, configInt("radius", 1)));
    }

    private int lifetimeTicks() {
        return Math.max(20, configInt("lifetime-ticks", 60));
    }

    private boolean chillsLava() {
        return configBoolean("chills-lava", true);
    }

    @Override
    public void onFastTick(Player player) {
        // Swimming means the player already went under; freezing the surface above them would trap them in
        // their own bridge. The stone catches you walking onto water, not diving off a cliff into it.
        if (player.isSwimming() || player.isFlying() || player.isInsideVehicle()) {
            return;
        }

        int radius = radius();
        int lifetime = lifetimeTicks();
        boolean froze = false;
        Block feet = player.getLocation().getBlock();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // One block down is the surface being stood on; the feet block itself catches the tick where
                // the player has already sunk in to ankle depth.
                for (int dy = -1; dy <= 0; dy++) {
                    Block block = feet.getRelative(dx, dy, dz);
                    Material replacement = replacementFor(block.getType());
                    if (replacement == null) {
                        continue;
                    }
                    if (plugin.tempTerrain().place(player, block, replacement, lifetime)) {
                        froze = true;
                    }
                }
            }
        }

        if (froze && player.getTicksLived() % 6 == 0) {
            Fx.sound(player, Sound.BLOCK_GLASS_PLACE, 0.35f, 1.7f);
        }
    }

    /** What a given fluid becomes, or null for anything that isn't a fluid this stone answers. */
    private Material replacementFor(Material type) {
        if (type == Material.WATER) {
            return Material.PACKED_ICE;
        }
        if (type == Material.LAVA && chillsLava()) {
            return Material.BASALT;
        }
        return null;
    }
}
