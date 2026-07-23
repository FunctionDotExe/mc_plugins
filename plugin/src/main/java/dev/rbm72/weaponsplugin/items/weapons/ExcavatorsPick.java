package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Pickaxe reskin for non-combat players: ambient Haste while held, and a small safe AoE that breaks matching ore in the immediate neighborhood. */
public final class ExcavatorsPick extends Weapon {

    private static final Color GOLD_BROWN = Color.fromRGB(160, 120, 60);
    private static final int MAX_EXTRA_BLOCKS = 8;

    private final int hasteAmplifier;
    private final int aoeRadius;

    public ExcavatorsPick(WeaponsPlugin plugin) {
        super(plugin);
        this.hasteAmplifier = configInt("haste-amplifier", 1);
        this.aoeRadius = configInt("aoe-radius", 1);
    }

    @Override
    public String id() {
        return "excavators_pick";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_PICKAXE;
    }

    @Override
    public String displayNameText() {
        return "Excavator's Pick";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 1.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 15.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: a quick burst of", NamedTextColor.GRAY),
                Component.text("extra Haste to power through stone.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Prospector's Boost";
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_STONE_BREAK;
    }

    @Override
    public Sound hitSound() {
        return Sound.BLOCK_STONE_HIT;
    }

    @Override
    public Sound readySound() {
        return Sound.ITEM_ARMOR_EQUIP_IRON;
    }

    @Override
    public void onTick(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 30, hasteAmplifier, true, false, false));
    }

    @Override
    public void ability1(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, hasteAmplifier + 1, true, true, true));
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), GOLD_BROWN, 1.2f, 16, 0.4);
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent event) {
        Block origin = event.getBlock();
        Material type = origin.getType();
        if (!type.name().endsWith("_ORE")) {
            return;
        }

        BlockData blockData = origin.getBlockData();
        World world = origin.getWorld();
        Location originLoc = origin.getLocation();
        int broken = 0;

        for (int dx = -aoeRadius; dx <= aoeRadius && broken < MAX_EXTRA_BLOCKS; dx++) {
            for (int dy = -aoeRadius; dy <= aoeRadius && broken < MAX_EXTRA_BLOCKS; dy++) {
                for (int dz = -aoeRadius; dz <= aoeRadius && broken < MAX_EXTRA_BLOCKS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Block neighbor = origin.getRelative(dx, dy, dz);
                    if (neighbor.getType() != type) {
                        continue;
                    }
                    Location neighborLoc = neighbor.getLocation();
                    neighbor.breakNaturally(event.getPlayer().getInventory().getItemInMainHand());
                    world.spawnParticle(org.bukkit.Particle.BLOCK, neighborLoc.add(0.5, 0.5, 0.5), 12, 0.2, 0.2, 0.2, blockData);
                    broken++;
                }
            }
        }

        if (broken > 0) {
            Fx.sound(originLoc, Sound.BLOCK_STONE_BREAK, 1.0f, 1.1f);
        }
    }
}
