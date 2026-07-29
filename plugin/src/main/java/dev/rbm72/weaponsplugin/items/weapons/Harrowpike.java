package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.kit.Counterplay;
import dev.rbm72.weaponsplugin.items.kit.LungeStrike;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pinning pike: a lunge that nails what it hits into a cage of real cobweb and real pointed dripstone,
 * and a braced stance that grows a real spike thicket in front of the wielder.
 * <p>
 * Every effect here is a block, not a status. §0.1's whole argument is in the comparison: "slow the target"
 * is a potion nobody can see and nothing can interact with, whereas a ring of cobweb is a physical thing the
 * target has to walk out of, that its friends walk into, that a sword can cut, and that the wielder can be
 * caught in themselves if they follow up carelessly. The dripstone is there for the same reason — it does
 * nothing at all until something falls onto it, which is exactly what a boss's own knockback produces.
 * <p>
 * Terrain goes through {@link dev.rbm72.weaponsplugin.items.kit.TempTerrain}, so the cage reverts to the
 * floor it replaced a few seconds later and no ability here can be farmed for cobweb or dripstone.
 */
public final class Harrowpike extends Weapon {

    private static final Color COLD_IRON = Color.fromRGB(178, 188, 198);

    /** Cage walls: the four blocks around the victim, never the one they occupy. */
    private static final int[][] CAGE_SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Spike sockets: the diagonals, so the cage's walls and its floor spikes never fight for a position. */
    private static final int[][] CAGE_CORNERS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private final double nailDamage;
    private final double contactRadius;
    private final int contactTicks;
    private final int webLifetimeTicks;
    private final int spikeLifetimeTicks;
    private final double braceDamage;
    private final int braceRange;
    private final int braceSpikeTicks;
    private final int braceSlowFallTicks;

    public Harrowpike(WeaponsPlugin plugin) {
        super(plugin);
        this.nailDamage = configDouble("nail-damage", 6.0);
        this.contactRadius = configDouble("contact-radius", 2.2);
        this.contactTicks = configInt("contact-ticks", 12);
        this.webLifetimeTicks = configInt("web-lifetime-ticks", 90);
        this.spikeLifetimeTicks = configInt("spike-lifetime-ticks", 110);
        this.braceDamage = configDouble("brace-damage", 4.5);
        this.braceRange = configInt("brace-range", 4);
        this.braceSpikeTicks = configInt("brace-spike-ticks", 120);
        this.braceSlowFallTicks = configInt("brace-slow-fall-ticks", 30);
    }

    @Override
    public String id() {
        return "harrowpike";
    }

    @Override
    public Material material() {
        return Material.IRON_SPEAR;
    }

    @Override
    public String displayNameText() {
        return "Harrowpike";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.5);
    }

    @Override
    public boolean ability1OnLunge() {
        return true;
    }

    @Override
    public int lungePowerBonus() {
        return configInt("lunge-power-bonus", 1);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 6.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 9.0);
    }

    @Override
    public String ability1Name() {
        return "Nail Down";
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Hold right-click, then release to", NamedTextColor.GRAY),
                Component.text("lunge. The first thing you reach is", NamedTextColor.GRAY),
                Component.text("caged in real cobweb, with dripstone", NamedTextColor.GRAY),
                Component.text("spikes set at the corners.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Brace";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: set the butt of the", NamedTextColor.GRAY),
                Component.text("pike and grow a dripstone thicket", NamedTextColor.GRAY),
                Component.text("ahead. You cannot be thrown while", NamedTextColor.GRAY),
                Component.text("the stance is taken.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_SPEAR_LUNGE_2;
    }

    @Override
    public Sound hitSound() {
        return Sound.ITEM_SPEAR_HIT;
    }

    @Override
    public Sound readySound() {
        return Sound.ITEM_SPEAR_ATTACK;
    }

    @Override
    public Map<CooldownManager.Slot, Double> damageProfile() {
        return Map.of(CooldownManager.Slot.ABILITY1, nailDamage,
                CooldownManager.Slot.ABILITY2, braceDamage);
    }

    /**
     * <b>Counterplay.</b> Bracing answers being thrown. The stance zeroes whatever velocity a boss just put
     * on the wielder before the thicket goes in, which is what makes it the tool for a gust or slam phase
     * rather than a stance that gets cancelled by one.
     */
    @Override
    public Set<CounterVerb> counterVerbs() {
        return Set.of(CounterVerb.DISPLACEMENT);
    }

    @Override
    public void ability1(Player player) {
        double damage = nailDamage * rarity().statMultiplier();

        LungeStrike.onFirstContact(plugin, player, contactRadius, contactTicks, (victim, at) -> {
            victim.damage(damage, player);
            Fx.sound(at, hitSound(), 1.0f, 0.9f);
            Fx.bloodSpray(at.clone().add(0, 1, 0));
            Fx.coloredBurst(at.clone().add(0, 1, 0), COLD_IRON, 1.1f, 14, 0.4);
            cage(player, at);
        });
    }

    /**
     * Four cobweb walls at body height and four dripstone tips at the corners.
     * <p>
     * The victim's own block is never written — terrain placed inside an entity suffocates and soft-locks
     * it, which is a bug dressed as a mechanic (see {@code FrostScythe.growSpike} for the same rule). Only
     * passable blocks are taken, so the cage never carves a hole in a wall it happens to be standing near.
     */
    private void cage(Player caster, Location centre) {
        for (int[] side : CAGE_SIDES) {
            for (int dy = 0; dy <= 1; dy++) {
                Block block = centre.clone().add(side[0], dy, side[1]).getBlock();
                if (block.isPassable()) {
                    plugin.tempTerrain().place(caster, block, Material.COBWEB, webLifetimeTicks);
                }
            }
        }
        for (int[] corner : CAGE_CORNERS) {
            Block block = centre.clone().add(corner[0], 0, corner[1]).getBlock();
            // A tip needs a floor under it to read as grown rather than floating, and a floating spike is
            // also one that never gets fallen onto — which is the whole reason it is there.
            if (block.isPassable() && block.getRelative(0, -1, 0).getType().isSolid()) {
                plugin.tempTerrain().place(caster, block, Material.POINTED_DRIPSTONE, spikeLifetimeTicks);
            }
        }
        Fx.sound(centre, Sound.BLOCK_POINTED_DRIPSTONE_PLACE, 1.0f, 0.8f);
    }

    @Override
    public void ability2(Player player) {
        Counterplay.plant(plugin, player, braceSlowFallTicks);

        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1.0e-4) {
            return;
        }
        forward.normalize();
        Vector across = new Vector(-forward.getZ(), 0, forward.getX());
        double damage = braceDamage * rarity().statMultiplier();

        for (int step = 1; step <= braceRange; step++) {
            for (int lateral = -1; lateral <= 1; lateral++) {
                Location spot = player.getLocation()
                        .add(forward.clone().multiply(step))
                        .add(across.clone().multiply(lateral));
                Block block = spot.getBlock();
                if (!block.isPassable() || !block.getRelative(0, -1, 0).getType().isSolid()) {
                    continue;
                }
                if (!plugin.tempTerrain().place(player, block, Material.POINTED_DRIPSTONE, braceSpikeTicks)) {
                    continue;
                }
                // The spikes grow through whatever is standing there. Vanilla dripstone only punishes a
                // fall, so the impaling on the way up is the ability's own hit and is paid for once per
                // target per cast.
                for (Entity nearby : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 0.7, 1.0, 0.7)) {
                    if (nearby instanceof LivingEntity living && !living.equals(player)) {
                        living.damage(damage, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
            }
        }
        Fx.sound(player, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.0f, 0.7f);
        Fx.coloredRing(player.getLocation().add(0, 0.2, 0), COLD_IRON, 1.0f, 1.6, 20, 0);
    }
}
