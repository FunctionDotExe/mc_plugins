package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Standalone active ability: raycasts along the wielder's look direction and launches them toward
 * whatever solid block it hits (or, if nothing's in range, straight along their facing) — a
 * hookshot without a physical grapple entity, closing the same gap as a normal grapple but with far
 * fewer moving parts to desync. Grants a short Slow Falling window afterward so a big pull doesn't
 * just trade fall damage for the mobility.
 */
public final class GrappleHook extends Accessory {

    private static final double MAX_RANGE = 30.0;
    private static final double SPEED_FACTOR = 0.22;
    private static final double MIN_SPEED = 1.1;
    private static final double SPEED_CAP = 3.2;
    private static final Color CHAIN_COLOR = Color.fromRGB(90, 90, 100);

    public GrappleHook(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "grapple_hook";
    }

    @Override
    public Material material() {
        return Material.IRON_CHAIN;
    }

    @Override
    public String displayNameText() {
        return "Grapple Hook";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("A standalone active ability.", NamedTextColor.DARK_GRAY),
                Component.text("Grants a standalone active", NamedTextColor.GRAY),
                Component.text("ability of its own.", NamedTextColor.GRAY));
    }

    @Override
    public boolean hasPersonalAbility() {
        return true;
    }

    @Override
    public String personalAbilityName() {
        return "Grapple";
    }

    @Override
    public List<Component> personalAbilityLore() {
        return List.of(
                Component.text("Launches you toward whatever", NamedTextColor.GRAY),
                Component.text("block you're looking at (up to", NamedTextColor.GRAY),
                Component.text("30 blocks away), with a brief", NamedTextColor.GRAY),
                Component.text("Slow Falling to land safely.", NamedTextColor.GRAY));
    }

    @Override
    public double personalAbilityCooldownSeconds() {
        return 5.0;
    }

    @Override
    public void personalAbility(Player player) {
        Location eye = player.getEyeLocation();
        Vector facing = eye.getDirection().normalize();
        RayTraceResult trace = player.getWorld().rayTraceBlocks(
                eye, facing, MAX_RANGE, FluidCollisionMode.NEVER, true);

        Location target = trace != null && trace.getHitPosition() != null
                ? trace.getHitPosition().toLocation(player.getWorld())
                : eye.clone().add(facing.clone().multiply(MAX_RANGE));

        double distance = eye.distance(target);
        Vector direction = target.toVector().subtract(eye.toVector()).normalize();
        double speed = Math.min(SPEED_CAP, Math.max(MIN_SPEED, distance * SPEED_FACTOR));

        player.setVelocity(direction.multiply(speed));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, true, true));

        Fx.sound(player, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 1.5f);
        Fx.sound(player, Sound.BLOCK_CHAIN_PLACE, 0.8f, 0.7f);
        Fx.line(eye, target, Particle.CRIT, 24);
        Fx.coloredBurst(eye, CHAIN_COLOR, 1.1f, 14, 0.3);
        Fx.coloredBurst(target, CHAIN_COLOR, 1.4f, 20, 0.4);
    }
}
