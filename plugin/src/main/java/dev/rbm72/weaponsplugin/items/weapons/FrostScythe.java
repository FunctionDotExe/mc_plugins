package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pulses a freezing aura that leaves real ice standing where it lands. Hitting a target that's already frosted
 * from a previous cast triggers a bonus "shatter" — the reward for chaining casts on the same enemy.
 * <p>
 * Reworked per batch-1 §0.1 — see {@link #growSpike} for what the spikes used to be.
 */
public final class FrostScythe extends Weapon {

    private final double abilityDamage;
    private final double shatterBonusDamage;
    private final double radius;
    private final int slowTicks;
    private final int slowAmplifier;
    private final int spikeHeight;
    private final int spikeLifetimeTicks;

    /** Per-target frost expiry, used to detect a shatter-eligible re-hit. Weapon instances are singletons. */
    private final Map<UUID, Long> frostedUntil = new HashMap<>();

    public FrostScythe(WeaponsPlugin plugin) {
        super(plugin);
        this.abilityDamage = configDouble("ability-damage", 5.0);
        this.shatterBonusDamage = configDouble("shatter-bonus-damage", 4.0);
        this.radius = configDouble("radius", 3.5);
        this.slowTicks = configInt("slow-ticks", 100);
        this.slowAmplifier = configInt("slow-amplifier", 2);
        this.spikeHeight = configInt("spike-height", 2);
        this.spikeLifetimeTicks = configInt("spike-lifetime-ticks", 100);
    }

    @Override
    public String id() {
        return "frost_scythe";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_HOE;
    }

    @Override
    public String displayNameText() {
        return "Frost Scythe";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("cooldown-seconds", 8.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: unleash a freezing aura,", NamedTextColor.GRAY),
                Component.text("damaging and slowing nearby enemies", NamedTextColor.GRAY),
                Component.text("and growing real ice where they", NamedTextColor.GRAY),
                Component.text("stand. Re-casting on a frosted", NamedTextColor.GRAY),
                Component.text("target shatters both for bonus", NamedTextColor.GRAY),
                Component.text("damage.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Shatterfrost";
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_GLASS_BREAK;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_HURT_FREEZE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_GLASS_PLACE;
    }

    @Override
    public void ability1(Player player) {
        Location loc = player.getLocation();
        double effectiveDamage = abilityDamage * rarity().statMultiplier();
        double effectiveShatterBonus = shatterBonusDamage * rarity().statMultiplier();
        long now = System.currentTimeMillis();

        Fx.burst(loc.clone().add(0, 1, 0), Particle.SNOWFLAKE, 30, radius / 2);
        Fx.coloredBurst(loc.clone().add(0, 1, 0), Color.fromRGB(120, 200, 255), 2.1f, 28, radius / 2);
        Fx.expandingRings(plugin, loc, Particle.SNOWFLAKE, radius, 5, 2L);

        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            boolean shatter = frostedUntil.getOrDefault(entity.getUniqueId(), 0L) > now;
            double damage = effectiveDamage + (shatter ? effectiveShatterBonus : 0);

            entity.damage(damage, player);
            StatusEffectManager.apply(entity, PotionEffectType.SLOWNESS, slowTicks, slowAmplifier);
            StatusEffectManager.apply(entity, PotionEffectType.MINING_FATIGUE, slowTicks, 1);
            frostedUntil.put(entity.getUniqueId(), now + slowTicks * 50L);

            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
            Fx.sound(entity.getLocation(), hitSound(), 1.0f, 1.0f);
            growSpike(player, entity.getLocation());

            if (shatter) {
                // A shatter is the ice already standing there coming apart, so it takes the spike with it —
                // the terrain is part of the combo rather than scenery next to it.
                plugin.tempTerrain().revertNear(entity.getLocation(), 1.5);
                Fx.burst(entity.getLocation().add(0, 1, 0), Particle.ITEM_SNOWBALL, 38, 0.55);
                Fx.blockBurst(entity.getLocation().add(0, 1, 0), Material.PACKED_ICE, 24, 0.6);
                Fx.coloredBurst(entity.getLocation().add(0, 1, 0), Color.fromRGB(190, 230, 255), 2.2f, 30, 0.6);
                Fx.sound(entity.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 1.0f);
            }
        }
    }

    /**
     * Grows a real ice spike out of the floor at the target's feet.
     * <p>
     * This was a {@link Fx#glowPillar} — a {@code BlockDisplay} scaled into a thin column, with a code comment
     * calling it "a real ice-block spike prop". It was not a block: a display entity has no collision, no
     * friction, nothing can stand on it or be stopped by it, and it cannot be broken. It was a picture of a
     * spike, and §0.1's test is whether deleting the drawing changes the mechanic. It did not.
     * <p>
     * These are {@link Material#PACKED_ICE} written through
     * {@link dev.rbm72.weaponsplugin.items.kit.TempTerrain}, so the aura leaves the fight standing in actual
     * ice: it blocks line of fire, it is slippery to fight on, a melee player has to path around a cluster of
     * it, and it can be mined out of the way by anyone who would rather not. It reverts on its TTL, which is
     * what makes terrain this aggressive safe on an 8-second cooldown.
     * <p>
     * The spikes ring the target rather than filling the block it is standing in. Filling it is the version that
     * looks best in a screenshot and encases a player in solid blocks — suffocation damage plus a soft-lock they
     * cannot walk out of, which is §0.3's "no death handed out by terrain" broken on the weapon side. A ring
     * boxes them in with real cover they can still break, climb or shoot over.
     */
    private void growSpike(Player caster, Location base) {
        Block centre = base.getBlock();
        for (BlockFace side : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block column = centre.getRelative(side);
            for (int dy = 0; dy < Math.max(1, spikeHeight); dy++) {
                Block block = column.getRelative(0, dy, 0);
                if (!block.getType().isAir()) {
                    continue;
                }
                if (!plugin.tempTerrain().place(caster, block, Material.PACKED_ICE, spikeLifetimeTicks)) {
                    break;
                }
            }
        }
        Fx.sound(base, Sound.BLOCK_GLASS_PLACE, 0.8f, 0.8f);
    }
}
