package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.kit.Counterplay;
import dev.rbm72.weaponsplugin.items.kit.Props;
import dev.rbm72.weaponsplugin.status.StatusEffectManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Launches the player forward in a rising tornado arc, briefly launching anything it slashes into the air,
 * then bursts outward on landing.
 * <p>
 * Rebuilt on real {@link org.bukkit.entity.WindCharge}s per batch-1 §0.1 — see {@link #ability1} for what the
 * particle version was doing and why the charges replace it rather than decorate it.
 */
public final class WindSpear extends Weapon {

    private static final Color GUST_COLOR = Color.fromRGB(225, 225, 230);

    private final double launchSpeed;
    private final double abilityDamage;
    private final double hitRadius;
    private final int flightTicks;

    public WindSpear(WeaponsPlugin plugin) {
        super(plugin);
        this.launchSpeed = configDouble("launch-speed", 2.0);
        this.abilityDamage = configDouble("ability-damage", 4.0);
        this.hitRadius = configDouble("hit-radius", 1.6);
        this.flightTicks = configInt("flight-ticks", 14);
    }

    @Override
    public String id() {
        return "wind_spear";
    }

    @Override
    public Material material() {
        return Material.TRIDENT;
    }

    @Override
    public String displayNameText() {
        return "Wind Spear";
    }

    @Override
    public Rarity rarity() {
        return Rarity.COMMON;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 1.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("cooldown-seconds", 5.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: launch forward in a", NamedTextColor.GRAY),
                Component.text("rising tornado, slashing and", NamedTextColor.GRAY),
                Component.text("launching anything in your path,", NamedTextColor.GRAY),
                Component.text("then burst outward on landing.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Cyclone Leap";
    }

    @Override
    public Sound castSound() {
        return Sound.ITEM_ELYTRA_FLYING;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_ATTACK_SWEEP;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_PHANTOM_FLAP;
    }

    @Override
    public Map<CooldownManager.Slot, Double> damageProfile() {
        // The landing burst is half the flight's damage and shares its cooldown, so the slot is worth 1.5x
        // the configured figure — costing only the pass-through would under-report the ability by a third.
        return Map.of(CooldownManager.Slot.ABILITY1, abilityDamage * 1.5);
    }

    /**
     * <b>Counterplay.</b> A wind weapon answers being thrown. The vault cancels whatever velocity a boss
     * just put on the player before adding its own, so it is the tool you reach for during a gust or
     * wing-buffet phase rather than a mobility ability that gets overridden by one.
     */
    @Override
    public Set<CounterVerb> counterVerbs() {
        return Set.of(CounterVerb.DISPLACEMENT);
    }

    /**
     * Gale Vault — a real wind charge under the player, and a trail of real wind charges behind them.
     * <p>
     * §0.1's wind row names the object outright: "real wind charges, velocity shoves, falling blocks blown
     * across the arena". This ability was the opposite of that — four stacked {@code Particle.CLOUD} helix
     * frames drawn every tick to make "a genuine tornado column", with a {@code damage()} loop and a manual
     * {@code setVelocity} doing the actual work behind them. Deleting the four helix calls left the ability
     * fully functional, which is the §0.1 test failed.
     * <p>
     * Now the column is wind charges dropped along the flight path. Each one bursts on its own, shoves
     * whatever is near it with vanilla's own falloff, and is audible and visible without a particle budget —
     * and the tornado is something bystanders can be caught by rather than something only the caster's
     * client draws. The damage loop stays, because a spear passing through someone should cut them; what it
     * no longer does is supply the knockback the charges are already producing.
     */
    @Override
    public void ability1(Player player) {
        // The boss's throw is cancelled before ours is added; two shoves composing is an uncontrolled launch.
        Counterplay.plant(plugin, player, 0);

        Vector direction = player.getLocation().getDirection().normalize();
        // The launch itself is a wind charge fired down at the player's feet — vanilla's own launcher, which
        // composes correctly with anything else writing their velocity this tick.
        Props.windCharge(plugin, this, player, player.getLocation().clone().add(0, 0.1, 0),
                direction.clone().multiply(launchSpeed * 0.25).setY(-0.7));

        double effectiveDamage = abilityDamage * rarity().statMultiplier();
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= flightTicks) {
                    cancel();
                    if (player.isOnline()) {
                        landingBurst(player);
                    }
                    return;
                }

                Location center = player.getLocation();
                // A charge every third tick, thrown out sideways on a rotating angle: the column is made of
                // real bursts spiralling out behind the flight, not of a spiral drawn around it. Throttled
                // because one wind charge per tick is both an entity flood and a wall of burst sounds.
                if (ticks % 3 == 0) {
                    Vector out = new Vector(Math.cos(angle), -0.25, Math.sin(angle)).normalize().multiply(0.35);
                    Props.windCharge(plugin, WindSpear.this, player, center.clone().add(0, 0.5, 0), out);
                    angle += Math.PI / 3;
                }
                Fx.coloredBurst(center.clone().add(0, 0.4, 0), GUST_COLOR, 1.6f, 10, 0.8);

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
                    StatusEffectManager.apply(entity, PotionEffectType.LEVITATION, 20, 0);
                    Fx.point(entity.getLocation().add(0, 1, 0), Particle.SWEEP_ATTACK, 1);
                    Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                    Fx.sound(entity.getLocation(), hitSound(), 1.0f, 1.0f);

                    // No manual knockback: the wind charges spiralling out of the column are already doing
                    // it, and stacking a hand-written shove on top produced the launch that made this
                    // ability throw enemies further than the spear ever travelled.
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * The touchdown: one real wind charge straight into the ground, which is what the four expanding
     * particle rings were standing in for.
     */
    private void landingBurst(Player player) {
        Location loc = player.getLocation();
        Props.windCharge(plugin, this, player, loc.clone().add(0, 1.2, 0), new Vector(0, -1.2, 0));
        Fx.coloredBurst(loc.clone().add(0, 0.3, 0), GUST_COLOR, 1.7f, 30, 1.2);
        Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);

        double burstDamage = abilityDamage * rarity().statMultiplier() * 0.5;
        for (Entity nearby : player.getNearbyEntities(2.5, 2.5, 2.5)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            entity.damage(burstDamage, player);
            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));

        }
    }
}
