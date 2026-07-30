package dev.rbm72.weaponsplugin.opitem.opitems;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.kit.Props;
import dev.rbm72.weaponsplugin.opitem.OpItem;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Throws a real storm at wherever you are looking: {@value #BOLT_COUNT_KEY} actual
 * {@link org.bukkit.entity.LightningStrike} entities walking outward from the aim point, one every few ticks.
 * <p>
 * §0.1 applies to the op shelf too — this is not {@code strikeLightningEffect} with a hand-written damage loop,
 * it is the vanilla object, so it converts pigs to zombified piglins, charges creepers, answers lightning rods
 * and feeds channeling tridents exactly as weather-born lightning does. It goes through
 * {@link Props#lightning(WeaponsPlugin, String, Player, Location)} for the one thing a bolt must not keep: the
 * Bukkit interface has no "causes fire" switch, so the prop tag is what makes {@code WeaponPropListener} refuse
 * the ignite, and a bare {@code world.spawn} here would set the map alight.
 * <p>
 * The bolts land where the operator is looking, not on the operator, and the walk outward is staggered so a
 * miss is survivable rather than one instant column of damage under your own feet.
 */
public final class BottledStorm extends OpItem {

    static final String BOLT_COUNT_KEY = "bolt-count";
    /** How far to trace for an aim point before falling back to a fixed distance along the look vector. */
    private static final int AIM_RANGE = 96;

    public BottledStorm(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "bottled_storm";
    }

    @Override
    public Material material() {
        return Material.LIGHTNING_ROD;
    }

    @Override
    public String displayNameText() {
        return "Bottled Storm";
    }

    @Override
    public NamedTextColor accentColor() {
        return NamedTextColor.AQUA;
    }

    @Override
    public Sound useSound() {
        return Sound.ITEM_TRIDENT_THUNDER;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("Calls " + boltCount() + " real lightning bolts", NamedTextColor.AQUA),
                Component.text("where you are looking, spread over", NamedTextColor.AQUA),
                Component.text(radius() + " blocks.", NamedTextColor.AQUA),
                Component.empty(),
                Component.text("Real bolts: they charge creepers,", NamedTextColor.GRAY),
                Component.text("turn pigs, and answer lightning rods.", NamedTextColor.GRAY),
                Component.text("They start no fires.", NamedTextColor.DARK_GRAY));
    }

    private int boltCount() {
        return Math.max(1, configInt(BOLT_COUNT_KEY, 6));
    }

    private int radius() {
        return Math.max(0, configInt("radius", 5));
    }

    private int boltIntervalTicks() {
        return Math.max(1, configInt("bolt-interval-ticks", 4));
    }

    @Override
    public boolean onUse(Player player) {
        Location aim = aimPoint(player);

        int count = boltCount();
        int radius = radius();
        int interval = boltIntervalTicks();

        for (int i = 0; i < count; i++) {
            // Bolt 0 lands on the aim point itself; the rest spiral outward so the pattern reads as a storm
            // arriving rather than as a single column, and so the operator can see where the next one is going.
            double angle = i * (Math.PI * 2 / Math.max(1, count - 1));
            double distance = count <= 1 ? 0 : radius * ((double) i / (count - 1));
            Location target = aim.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Location grounded = groundAt(target);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    Props.lightning(plugin, id(), player, grounded);
                }
            }, (long) i * interval);
        }

        plugin.actionBarHub().flash(player, Component.text("Bottled Storm — " + count + " bolts", NamedTextColor.AQUA),
                1500, ActionBarHub.PRIORITY_NOTICE);
        Fx.sound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.2f);
        return true;
    }

    /** Where the player is looking, or a fixed distance along the look vector when that is open sky. */
    private static Location aimPoint(Player player) {
        Block targeted = player.getTargetBlockExact(AIM_RANGE);
        if (targeted != null) {
            return targeted.getLocation().add(0.5, 1, 0.5);
        }
        return player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(24));
    }

    /**
     * Drops the strike point onto whatever is under it. A bolt spawned in mid-air strikes there and hits
     * nothing on the ground below, which reads as the item having misfired.
     */
    private static Location groundAt(Location location) {
        Location ground = location.clone();
        ground.setY(location.getWorld().getHighestBlockYAt(location) + 1.0);
        // Only snap downward: inside a cave the highest block is the surface far overhead, and the operator
        // meant the spot they were looking at.
        return ground.getY() > location.getY() ? location : ground;
    }
}
