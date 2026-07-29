package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dashes through enemies, igniting whatever it hits, and leaves a burning trail of real fire behind it.
 * <p>
 * Reworked per batch-1 §0.1 — see {@link #igniteTrail} for what the old "scorched trail" actually was and
 * why real {@link Material#FIRE} replaces it rather than decorating it.
 */
public final class FlameKatana extends Weapon {

    /** How much of the trail nearest the wielder's landing spot is left unlit, in blocks. */
    private static final double SELF_CLEARANCE = 2.0;

    private final double dashSpeed;
    private final double abilityDamage;
    private final double hitRadius;
    private final int dashTicks;
    private final int burnTicks;
    private final int trailDurationTicks;

    public FlameKatana(WeaponsPlugin plugin) {
        super(plugin);
        this.dashSpeed = configDouble("dash-speed", 1.5);
        this.abilityDamage = configDouble("ability-damage", 7.0);
        this.hitRadius = configDouble("hit-radius", 1.6);
        this.dashTicks = configInt("dash-ticks", 10);
        this.burnTicks = configInt("burn-ticks", 60);
        this.trailDurationTicks = configInt("trail-duration-ticks", 60);
    }

    @Override
    public String id() {
        return "flame_katana";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Flame Katana";
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
    public double ability1CooldownSeconds() {
        return configDouble("cooldown-seconds", 7.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: dash through enemies,", NamedTextColor.GRAY),
                Component.text("burning everything you strike, and", NamedTextColor.GRAY),
                Component.text("leave real fire burning along the", NamedTextColor.GRAY),
                Component.text("path until it burns out.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Scorching Rush";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_FIRECHARGE_USE;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_BURN;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_FIRE_AMBIENT;
    }

    @Override
    public Map<CooldownManager.Slot, Double> damageProfile() {
        // The trail's damage is vanilla's burn tick, not a number this weapon picks, so the slot is worth the
        // pass-through alone — attributing the fire's damage here would double-count it.
        return Map.of(CooldownManager.Slot.ABILITY1, abilityDamage);
    }

    /**
     * <b>Counterplay.</b> Real fire on the floor is the answer to floors that have been taken away: a fire
     * block replaces the ice, powder snow or magma a boss laid down for as long as it burns, so the dash is
     * both an escape and a way to give the group somewhere to stand.
     */
    @Override
    public Set<CounterVerb> counterVerbs() {
        return Set.of(CounterVerb.FOOTING);
    }

    @Override
    public void ability1(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(dashSpeed).setY(0.25));

        double effectiveDamage = abilityDamage * rarity().statMultiplier();
        Set<UUID> alreadyHit = new HashSet<>();
        List<Location> trailPoints = new ArrayList<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= dashTicks) {
                    cancel();
                    igniteTrail(player, trailPoints);
                    return;
                }

                Location loc = player.getLocation().add(0, 1, 0);
                Fx.trail(loc, Particle.FLAME, 16, 0.4, 0.03);
                Fx.trail(loc, Particle.LAVA, 4, 0.35, 0.01);
                if (ticks % 2 == 0) {
                    trailPoints.add(loc.clone());
                }

                for (Entity nearby : player.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                    if (!(nearby instanceof LivingEntity entity)) {
                        continue;
                    }
                    if (entity.getUniqueId().equals(player.getUniqueId())) {
                        continue;
                    }
                    if (!alreadyHit.add(entity.getUniqueId())) {
                        continue;
                    }

                    entity.damage(effectiveDamage, player);
                    entity.setFireTicks(burnTicks);
                    Fx.burst(entity.getLocation().add(0, 1, 0), Particle.FLAME, 26, 0.45);
                    Fx.coloredBurst(entity.getLocation().add(0, 1, 0), Color.fromRGB(255, 90, 0), 1.9f, 22, 0.5);
                    Fx.bloodSpray(entity.getLocation().add(0, 1.2, 0));
                    Fx.sound(entity.getLocation(), hitSound(), 1.0f, 1.0f);

                    Vector knockback = direction.clone().multiply(1.1).setY(0.35);
                    entity.setVelocity(entity.getVelocity().add(knockback));
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Lays real fire along the path the dash took.
     * <p>
     * The old version of this was the §0.1 anti-pattern with a comment defending itself: it drew
     * {@code Particle.SMALL_FLAME} at each trail point and ran a {@code damage()} loop over anything within a
     * metre of them, on a 10-tick timer, and the class javadoc called that "entirely code-driven damage
     * ticks, never real fire blocks" as though it were the safe choice. What it produced was a corridor of
     * invisible damage: fire you cannot see the edge of, that does not light what walks into it, that is not
     * blocked by standing on a different block, and that vanishes if the particle call is deleted.
     * <p>
     * It is real {@link Material#FIRE} now, through {@link dev.rbm72.weaponsplugin.items.kit.TempTerrain},
     * which holds a revert for every block and puts it back after {@code trail-duration-ticks}. The half the
     * old comment was right to worry about — fire spreading out of the trail into someone's build, which the
     * ledger has no revert for because it never wrote those blocks — is handled where it belongs, in
     * {@code WeaponPropListener}, which refuses spread and burn out of any block the ledger is holding. So the
     * trail griefs nothing while being a thing players can see, path around, and be driven into. Burning is
     * vanilla's, from the block. No damage loop.
     */
    private void igniteTrail(Player caster, List<Location> trailPoints) {
        if (trailPoints.isEmpty()) {
            return;
        }

        Location end = caster.getLocation();
        for (Location point : trailPoints) {
            World world = point.getWorld();
            if (world == null) {
                continue;
            }
            // Not under the wielder's own feet. The trail is sampled from where they were, and the last samples
            // are where they still are, so lighting all of them means every cast sets the caster on fire —
            // which is how you find out the hard way that real fire is real.
            if (point.distanceSquared(end) < SELF_CLEARANCE * SELF_CLEARANCE) {
                continue;
            }
            // Fire only burns where it has something to sit on; the trail is sampled at chest height, so the
            // block to light is the one at foot level, and only if the floor beneath it is solid.
            Block floor = point.clone().subtract(0, 1, 0).getBlock();
            Block target = floor.getRelative(0, 1, 0);
            if (!floor.getType().isSolid() || !target.getType().isAir()) {
                continue;
            }
            plugin.tempTerrain().place(caster, target, Material.FIRE, trailDurationTicks);
            Fx.point(target.getLocation().add(0.5, 0.2, 0.5), Particle.SMALL_FLAME, 4);
        }
        Fx.sound(trailPoints.get(0), Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.7f);
    }
}
