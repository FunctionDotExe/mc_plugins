package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.SpearWeapon;
import dev.rbm72.weaponsplugin.items.kit.ChargeStrike;
import dev.rbm72.weaponsplugin.items.kit.Counterplay;
import dev.rbm72.weaponsplugin.items.kit.Props;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The copper pike: it plants real lightning rods and drops real bolts onto them.
 * <p>
 * The §0.1 case for this weapon is unusually literal. Copper is the lightning-rod metal, a lightning rod is a
 * real block whose entire behaviour is "the strike lands here instead of there", and
 * {@link Props#lightning} spawns the real bolt rather than the cosmetic flash that was the roster's most
 * common §0.1 violation. So nothing here is a metaphor: the ability places the object, the object attracts
 * the strike, and the strike does what a strike does — converts pigs, powers redstone, charges creepers, and
 * hurts whatever is standing at the rod's foot.
 * <p>
 * The rods are also real to the enemy's advantage. During a thunderstorm a placed rod pulls natural strikes
 * onto itself for as long as it stands, and it stands for a handful of seconds, in the open, where anything
 * can break it — the ability is a piece of terrain, not a targeted spell.
 */
public final class Arcpike extends SpearWeapon {

    private static final Color COPPER = Color.fromRGB(216, 128, 72);

    /** How far down a spot is allowed to be before it counts as "no floor here". */
    private static final int FLOOR_SEARCH_DEPTH = 4;

    private final double strikeDamage;
    private final double strikeRadius;
    private final int rodLifetimeTicks;
    private final int lungeSettleTicks;
    private final double groundingRelief;
    private final int lineRods;
    private final double lineSpacing;
    private final int lineIntervalTicks;
    private final double lineDamage;

    public Arcpike(WeaponsPlugin plugin) {
        super(plugin);
        this.strikeDamage = configDouble("strike-damage", 8.0);
        this.strikeRadius = configDouble("strike-radius", 3.0);
        this.rodLifetimeTicks = configInt("rod-lifetime-ticks", 120);
        this.lungeSettleTicks = configInt("lunge-settle-ticks", 10);
        this.groundingRelief = configDouble("grounding-relief", 0.5);
        this.lineRods = configInt("line-rods", 3);
        this.lineSpacing = configDouble("line-spacing", 3.0);
        this.lineIntervalTicks = configInt("line-interval-ticks", 6);
        this.lineDamage = configDouble("line-damage", 6.0);
    }

    @Override
    public String id() {
        return "arcpike";
    }

    @Override
    public Material material() {
        return Material.COPPER_SPEAR;
    }

    @Override
    public String displayNameText() {
        return "Arcpike";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.0);
    }


    @Override
    public int lungePowerBonus() {
        return configInt("lunge-power-bonus", 1);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 8.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 12.0);
    }

    @Override
    public String ability1Name() {
        return "Grounding Strike";
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Hold right-click and run something", NamedTextColor.GRAY),
                Component.text("down. Where you stop, a real", NamedTextColor.GRAY),
                Component.text("lightning rod goes in and takes a", NamedTextColor.GRAY),
                Component.text("real bolt. Grounding clears the", NamedTextColor.GRAY),
                Component.text("boss's stacks off you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Rod Line";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: set a line of rods", NamedTextColor.GRAY),
                Component.text("ahead of you and walk a bolt down", NamedTextColor.GRAY),
                Component.text("them, one after another.", NamedTextColor.GRAY));
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_SPEAR_LUNGE_3;
    }

    @Override
    public Sound hitSound() {
        return Sound.ITEM_TRIDENT_THUNDER;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_COPPER_PLACE;
    }

    @Override
    public Map<CooldownManager.Slot, Double> damageProfile() {
        return Map.of(CooldownManager.Slot.ABILITY1, strikeDamage,
                CooldownManager.Slot.ABILITY2, lineDamage * lineRods);
    }

    /**
     * <b>Counterplay.</b> Grounding is the meter answer, and it is the one verb this weapon can claim
     * honestly: the rod going into the floor is what discharges the wielder, so the same cast that starts the
     * strike is the cast that takes Static Charge, Chill or Void Echo back off them.
     */
    @Override
    public Set<CounterVerb> counterVerbs() {
        return Set.of(CounterVerb.METER);
    }

    @Override
    public void ability1(Player player, LivingEntity victim) {
        ChargeStrike.afterCharge(plugin, player, lungeSettleTicks, landing -> {
            Vector forward = landing.getDirection().setY(0);
            Location target = forward.lengthSquared() < 1.0e-4
                    ? landing.clone()
                    : landing.clone().add(forward.normalize().multiply(1.5));

            strikeRod(player, target, strikeDamage * rarity().statMultiplier());
            // The rod is earthed through the wielder's own weapon, so it is also what takes the charge off
            // them. Thematically the same action; mechanically the meter answer this weapon exists for.
            Counterplay.relieveMeters(plugin, player, groundingRelief);
        });
    }

    @Override
    public void ability2(Player player) {
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1.0e-4) {
            return;
        }
        forward.normalize();
        Location origin = player.getLocation();
        double damage = lineDamage * rarity().statMultiplier();

        new BukkitRunnable() {
            int placed;

            @Override
            public void run() {
                if (placed >= lineRods || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location spot = origin.clone().add(forward.clone().multiply(lineSpacing * (placed + 1)));
                strikeRod(player, spot, damage);
                placed++;
            }
        }.runTaskTimer(plugin, 0L, Math.max(1, lineIntervalTicks));
    }

    /**
     * Plants a rod at {@code around} and drops a real bolt on it.
     * <p>
     * Refusal is handled rather than ignored: {@link dev.rbm72.weaponsplugin.items.kit.TempTerrain} says no
     * inside an arena that is out of ledger budget, or where the block cannot be put back, and a strike with
     * no rod under it is still a strike. Losing the rod costs the terrain, never the ability.
     */
    private void strikeRod(Player caster, Location around, double damage) {
        Block floor = floorUnder(around);
        Location boltAt = floor != null ? floor.getLocation().add(0.5, 1, 0.5) : around.clone();

        if (floor != null) {
            Block rod = floor.getRelative(0, 1, 0);
            if (rod.isPassable()) {
                plugin.tempTerrain().place(caster, rod, Material.LIGHTNING_ROD, rodLifetimeTicks);
            }
        }

        Props.lightning(plugin, this, caster, boltAt);
        Fx.sound(boltAt, hitSound(), 1.0f, 1.1f);
        Fx.coloredRing(boltAt, COPPER, 1.0f, strikeRadius, 24, 0);

        for (Entity nearby : boltAt.getWorld().getNearbyEntities(boltAt, strikeRadius, strikeRadius + 1, strikeRadius)) {
            if (nearby instanceof LivingEntity living && !living.equals(caster)) {
                // The bolt's own damage is vanilla's and cannot be tuned; this is the weapon's number, and
                // it is the one the balance sheet reports.
                living.damage(damage, caster);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    /** The nearest solid block at or below {@code at}, or null if there is nothing to stand a rod on. */
    private Block floorUnder(Location at) {
        Block block = at.getBlock();
        for (int step = 0; step <= FLOOR_SEARCH_DEPTH; step++) {
            Block candidate = block.getRelative(0, -step, 0);
            if (candidate.getType().isSolid()) {
                return candidate;
            }
        }
        return null;
    }
}
