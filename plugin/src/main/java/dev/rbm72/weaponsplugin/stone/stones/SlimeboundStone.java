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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a long fall into a bounce by dropping a real slime block on the spot you were about to land on.
 * <p>
 * Note what this stone deliberately does <em>not</em> do: cancel fall damage. Vanilla already decides that a
 * player who lands on slime and isn't sneaking takes none and bounces instead, so the stone's whole job is to
 * make sure there is slime there — the physics, the bounce height, the "sneak to land flat" escape hatch and
 * the fall-damage waiver all come from the block, free and already tuned. An event-cancelling version of this
 * would be one line shorter and would give you a soft landing with no bounce, no way to opt out, and nothing
 * anyone watching could see.
 * <p>
 * One pad per airtime, so it catches a fall rather than becoming a trampoline you ride upward forever.
 */
public final class SlimeboundStone extends Stone {

    /** Players who have already spent their pad this airtime; cleared when they touch ground again. */
    private final Set<UUID> spent = new HashSet<>();

    public SlimeboundStone(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "slimebound_stone";
    }

    @Override
    public Material material() {
        return Material.SLIME_BALL;
    }

    @Override
    public String displayNameText() {
        return "Slimebound Stone";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("A real slime block lands where", NamedTextColor.GREEN),
                Component.text("you were going to — so a long", NamedTextColor.GRAY),
                Component.text("fall bounces instead of hurting.", NamedTextColor.GRAY),
                Component.text("Sneak on the way down to land", NamedTextColor.DARK_GRAY),
                Component.text("flat. One pad per fall.", NamedTextColor.DARK_GRAY));
    }

    /** Downward speed the fall has to reach before a pad is worth spending. */
    private double triggerFallSpeed() {
        return configDouble("trigger-fall-speed", -0.55);
    }

    /** How far ahead of the player the landing spot is looked for. */
    private int lookAheadBlocks() {
        return Math.max(1, configInt("look-ahead-blocks", 3));
    }

    private int lifetimeTicks() {
        return Math.max(20, configInt("lifetime-ticks", 40));
    }

    @Override
    public void onFastTick(Player player) {
        UUID uuid = player.getUniqueId();

        if (player.isOnGround()) {
            spent.remove(uuid);
            return;
        }
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle() || player.isInWater()) {
            return;
        }
        if (player.getVelocity().getY() > triggerFallSpeed() || spent.contains(uuid)) {
            return;
        }

        Block landing = landingBlock(player);
        if (landing == null) {
            return;
        }
        if (!plugin.tempTerrain().place(player, landing, Material.SLIME_BLOCK, lifetimeTicks())) {
            return;
        }

        spent.add(uuid);
        Fx.sound(player, Sound.BLOCK_SLIME_BLOCK_PLACE, 0.7f, 1.2f);
        Fx.blockBurst(landing.getLocation().add(0.5, 1.0, 0.5), Material.SLIME_BLOCK, 10, 0.3);
    }

    @Override
    public void onIdleTick(Player player) {
        if (!spent.isEmpty()) {
            spent.remove(player.getUniqueId());
        }
    }

    /**
     * The block the player is about to hit, or null if the ground is still further away than the pad is
     * allowed to reach.
     * <p>
     * The pad replaces the surface itself rather than sitting on top of it: a slime block placed in the air
     * above the floor is one the player clips straight past at fall speed.
     */
    private Block landingBlock(Player player) {
        Block feet = player.getLocation().getBlock();
        for (int dy = 1; dy <= lookAheadBlocks(); dy++) {
            Block candidate = feet.getRelative(0, -dy, 0);
            if (!candidate.getType().isAir() && !candidate.isLiquid()) {
                return candidate;
            }
        }
        return null;
    }
}
