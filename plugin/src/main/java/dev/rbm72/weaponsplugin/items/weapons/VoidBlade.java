package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Space/void sword: wall-phasing teleport, a pulling rift, a phase-dash, and a collapsing black hole ultimate. */
public final class VoidBlade extends Weapon {

    private static final Color VOID_COLOR = Color.fromRGB(45, 0, 70);
    private static final Color DEEP_VOID_COLOR = Color.fromRGB(30, 0, 50);

    private final double armorBypassChance;
    private final double phaseRange;
    private final double riftPullRadius;
    private final double riftDamage;
    private final int riftDurationTicks;
    private final double slashDamage;
    private final int slashTicks;
    private final double slashHitRadius;
    private final double blackHolePullRadius;
    private final int blackHolePullDurationTicks;
    private final double blackHoleExplosionDamage;
    private final double blackHoleExplosionRadius;

    public VoidBlade(WeaponsPlugin plugin) {
        super(plugin);
        this.armorBypassChance = configDouble("armor-bypass-chance", 0.1);
        this.phaseRange = configDouble("phase-range", 6.0);
        this.riftPullRadius = configDouble("rift-pull-radius", 5.0);
        this.riftDamage = configDouble("rift-damage", 3.0);
        this.riftDurationTicks = configInt("rift-duration-ticks", 30);
        this.slashDamage = configDouble("slash-damage", 6.0);
        this.slashTicks = configInt("slash-ticks", 6);
        this.slashHitRadius = configDouble("slash-hit-radius", 1.6);
        this.blackHolePullRadius = configDouble("black-hole-pull-radius", 7.0);
        this.blackHolePullDurationTicks = configInt("black-hole-pull-duration-ticks", 40);
        this.blackHoleExplosionDamage = configDouble("black-hole-explosion-damage", 14.0);
        this.blackHoleExplosionRadius = configDouble("black-hole-explosion-radius", 5.0);
    }

    @Override
    public String id() {
        return "void_blade";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Void Blade";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 3.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 7.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 9.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 6.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Abyss")
                .accent(VOID_COLOR)
                .perMeleeHit(configDouble("abyss-per-hit", 6.0))
                .perDamageDealt(configDouble("abyss-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("abyss-per-ability", 8.0))
                .perKill(configDouble("abyss-per-kill", 12.0))
                .decay(configDouble("abyss-decay-per-second", 2.0), configDouble("abyss-decay-grace", 7.0))
                .cooldownFloor(configDouble("abyss-cooldown-floor", 50.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: teleport forward,", NamedTextColor.GRAY),
                Component.text("phasing straight through walls.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Warp Step";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: open a void rift", NamedTextColor.GRAY),
                Component.text("that pulls nearby enemies in.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Void Rift";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: phase-dash forward,", NamedTextColor.GRAY),
                Component.text("damaging enemies you pass through.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Phase Dash";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: collapse a black", NamedTextColor.GRAY),
                Component.text("hole that pulls enemies in, then", NamedTextColor.GRAY),
                Component.text("explodes.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Singularity";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ENDERMAN_TELEPORT;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_WARDEN_SONIC_BOOM;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_PORTAL_AMBIENT;
    }

    /**
     * {@code DamageModifier} is deprecated with no replacement that can zero one term of a damage
     * calculation already in flight — the new damage API only exposes the final number, and rewriting
     * the armour reduction by hand here would have to re-derive vanilla's own armour/toughness formula
     * and then drift from it on every version bump. Zeroing the ARMOR modifier is still the only way to
     * say "this hit ignores armour" and have the server do the arithmetic, so the call stays.
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (ThreadLocalRandom.current().nextDouble() < armorBypassChance) {
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0.0);
            Fx.burst(victim.getLocation().add(0, 1, 0), Particle.PORTAL, 10, 0.3);
            Fx.coloredBurst(victim.getLocation().add(0, 1, 0), VOID_COLOR, 1.0f, 8, 0.3);
        }
    }

    @Override
    public void ability1(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        Location best = player.getLocation();
        for (double d = 0.5; d <= phaseRange; d += 0.5) {
            Location candidate = player.getLocation().add(direction.clone().multiply(d));
            if (!candidate.getBlock().getType().isSolid() && !candidate.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                best = candidate;
            }
        }
        Fx.line(player.getLocation().add(0, 1, 0), best.clone().add(0, 1, 0), Particle.PORTAL, 24);
        player.teleport(best);
        Fx.burst(best, Particle.PORTAL, 45, 0.7);
        Fx.coloredBurst(best.clone().add(0, 1, 0), VOID_COLOR, 1.6f, 28, 0.7);
    }

    @Override
    public void ability2(Player player) {
        double damage = riftDamage * rarity().statMultiplier();
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(4));
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;
            double spiralRadius = riftPullRadius * 0.5;
            double spiralHeight = 1.2;

            @Override
            public void run() {
                if (ticks >= riftDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }

                // Particles spiral inward and downward toward the rift center instead of
                // sitting at one static point, so the rift actually reads as a pulling vortex.
                // A second counter-offset strand at a shrunk radius/height gives the spiral
                // real thickness instead of one thin line of points corkscrewing down.
                Fx.helixFrame(center, Particle.REVERSE_PORTAL, spiralRadius, 3, angle, spiralHeight);
                Fx.helixFrame(center, Particle.REVERSE_PORTAL, spiralRadius * 0.6, 3, angle + 1.2, spiralHeight * 0.6);
                Fx.point(center, Particle.REVERSE_PORTAL, 5);
                angle += 0.6;
                spiralRadius -= 0.12;
                spiralHeight -= 0.09;
                if (spiralRadius < 0.3 || spiralHeight < -0.6) {
                    spiralRadius = riftPullRadius * 0.5;
                    spiralHeight = 1.2;
                    Fx.coloredBurst(center, VOID_COLOR, 1.9f, 24, 0.5);
                }

                for (Entity entity : world.getNearbyEntities(center, riftPullRadius, riftPullRadius, riftPullRadius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                        continue;
                    }
                    Vector pull = center.toVector().subtract(living.getLocation().toVector()).normalize().multiply(0.2);
                    living.setVelocity(living.getVelocity().add(pull));
                    if (ticks % 15 == 0) {
                        living.damage(damage, player);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability3(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(1.8).setY(0.2));

        double damage = slashDamage * rarity().statMultiplier();
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= slashTicks) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation().add(0, 1, 0);
                Fx.trail(loc, Particle.REVERSE_PORTAL, 18, 0.4, 0.03);
                Fx.trail(loc.clone().add(0, 0.3, 0), Particle.REVERSE_PORTAL, 10, 0.3, 0.02);
                Fx.coloredBurst(loc, DEEP_VOID_COLOR, 1.4f, 16, 0.4);

                for (Entity nearby : player.getNearbyEntities(slashHitRadius, slashHitRadius, slashHitRadius)) {
                    if (!(nearby instanceof LivingEntity entity) || !alreadyHit.add(entity.getUniqueId())) {
                        continue;
                    }
                    entity.damage(damage, player);
                    Fx.bloodSpray(entity.getLocation().add(0, 1, 0));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ultimate(Player player) {
        Location center = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(5));
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double explosionDamage = blackHoleExplosionDamage * rarity().statMultiplier();

        // Consumed by the explosion: it's given exactly the pull-phase duration to live.
        Fx.spinningIcon(plugin, center.clone().add(0, 1, 0), Material.ECHO_SHARD, 1.0f, blackHolePullDurationTicks, 14);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (ticks < blackHolePullDurationTicks) {
                    // Three rings stacked above/below center form a real accretion-disk
                    // column instead of a single flat ring floating at one height.
                    Fx.ring(center, Particle.PORTAL, 2.0, 24, angle);
                    Fx.ring(center.clone().add(0, 0.6, 0), Particle.PORTAL, 1.6, 20, angle + 0.4);
                    Fx.ring(center.clone().add(0, -0.6, 0), Particle.PORTAL, 1.6, 20, angle - 0.4);
                    Fx.coloredBurst(center, DEEP_VOID_COLOR, 1.6f, 6, 0.3);
                    angle += 0.5;
                    for (Entity entity : world.getNearbyEntities(center, blackHolePullRadius, blackHolePullRadius, blackHolePullRadius)) {
                        if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                            continue;
                        }
                        Vector pull = center.toVector().subtract(living.getLocation().toVector()).normalize().multiply(0.25);
                        living.setVelocity(living.getVelocity().add(pull));
                    }
                    ticks++;
                    return;
                }

                Fx.burst(center, Particle.EXPLOSION_EMITTER, 1, 0.1);
                Fx.burst(center, Particle.PORTAL, 140, blackHoleExplosionRadius * 0.7);
                Fx.coloredBurst(center, DEEP_VOID_COLOR, 2.4f, 50, blackHoleExplosionRadius * 0.6);
                Fx.coloredBurst(center.clone().add(0, 1.0, 0), DEEP_VOID_COLOR, 2.0f, 30, blackHoleExplosionRadius * 0.5);
                Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);

                for (Entity entity : world.getNearbyEntities(center, blackHoleExplosionRadius, blackHoleExplosionRadius, blackHoleExplosionRadius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                        continue;
                    }
                    living.damage(explosionDamage, player);
                    Vector knockback = living.getLocation().toVector().subtract(center.toVector()).normalize().setY(0.6);
                    living.setVelocity(living.getVelocity().add(knockback.multiply(1.5)));
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                }
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
