package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Short-cooldown burst dash usable both grounded and mid-air — the aerial-mobility counterpart to
 * {@link GrappleHook}'s single long pull. No native "double jump" key exists in the Bukkit API, so
 * this reuses the same double-tap-sneak trigger every personal ability uses; the short 3s cooldown
 * is what makes it read as chainable aerial repositioning rather than a one-off gap-closer.
 */
public final class GaleStepBoots extends Accessory {

    private static final double DASH_SPEED = 1.6;
    private static final double GROUND_LIFT = 0.35;
    private static final double AIR_LIFT = 0.15;
    private static final Color GALE_COLOR = Color.fromRGB(210, 235, 245);

    public GaleStepBoots(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "galestep_boots";
    }

    @Override
    public Material material() {
        return Material.PHANTOM_MEMBRANE;
    }

    @Override
    public String displayNameText() {
        return "Gale-Step Boots";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
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
        return "Gale Dash";
    }

    @Override
    public List<Component> personalAbilityLore() {
        return List.of(
                Component.text("Burst forward in whatever", NamedTextColor.GRAY),
                Component.text("direction you're facing. Works", NamedTextColor.GRAY),
                Component.text("mid-air — short cooldown means", NamedTextColor.GRAY),
                Component.text("you can chain dashes in a fight.", NamedTextColor.GRAY));
    }

    @Override
    public double personalAbilityCooldownSeconds() {
        return 3.0;
    }

    @Override
    public void personalAbility(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        Vector velocity = direction.multiply(DASH_SPEED);
        boolean airborne = !player.isOnGround();
        velocity.setY(Math.max(velocity.getY(), airborne ? AIR_LIFT : GROUND_LIFT));
        player.setVelocity(velocity);

        Location origin = player.getLocation().add(0, 1, 0);
        Fx.sound(player, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.6f);
        Fx.coloredBurst(origin, GALE_COLOR, 1.2f, 18, 0.3);
        Fx.trail(origin, Particle.CLOUD, 10, 0.25, 0.02);
    }
}
