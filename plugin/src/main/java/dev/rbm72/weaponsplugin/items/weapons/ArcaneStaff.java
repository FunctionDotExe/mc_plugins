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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Mage staff: rapid-fire missiles, a hitscan beam, a short blink, and a
 * channeled laser ultimate. Rewards standing still (a mage "channeling")
 * with bonus damage on every ability.
 */
public final class ArcaneStaff extends Weapon {

    private static final Color ARCANE_PURPLE = Color.fromRGB(168, 85, 247);

    private final double missileDamage;
    private final double missileSpeed;
    private final int missileCount;
    private final double beamDamage;
    private final double beamRange;
    private final double blinkDistance;
    private final double laserDamagePerTick;
    private final int laserDurationTicks;
    private final double laserRange;
    private final double standingStillBonus;

    public ArcaneStaff(WeaponsPlugin plugin) {
        super(plugin);
        this.missileDamage = configDouble("missile-damage", 4.0);
        this.missileSpeed = configDouble("missile-speed", 2.0);
        this.missileCount = configInt("missile-count", 3);
        this.beamDamage = configDouble("beam-damage", 9.0);
        this.beamRange = configDouble("beam-range", 15.0);
        this.blinkDistance = configDouble("blink-distance", 6.0);
        this.laserDamagePerTick = configDouble("laser-damage-per-tick", 3.0);
        this.laserDurationTicks = configInt("laser-duration-ticks", 40);
        this.laserRange = configDouble("laser-range", 18.0);
        this.standingStillBonus = configDouble("standing-still-damage-bonus", 0.2);
    }

    @Override
    public String id() {
        return "arcane_staff";
    }

    @Override
    public Material material() {
        return Material.BLAZE_ROD;
    }

    @Override
    public String displayNameText() {
        return "Arcane Staff";
    }

    @Override
    public Rarity rarity() {
        return Rarity.MYTHIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 1.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 7.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 8.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 45.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Focus")
                .accent(ARCANE_PURPLE)
                .perMeleeHit(configDouble("focus-per-hit", 3.0))
                .perDamageDealt(configDouble("focus-per-damage-dealt", 0.5))
                .perAbilityCast(configDouble("focus-per-ability", 9.0))
                .perKill(configDouble("focus-per-kill", 10.0))
                .decay(configDouble("focus-decay-per-second", 2.0), configDouble("focus-decay-grace", 6.0))
                .cooldownFloor(configDouble("focus-cooldown-floor", 40.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: fire a rapid volley of", NamedTextColor.GRAY),
                Component.text("magic missiles.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Arcane Volley";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: fire an instant", NamedTextColor.GRAY),
                Component.text("beam of arcane energy.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Piercing Beam";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: blink forward a short", NamedTextColor.GRAY),
                Component.text("distance.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Phase Blink";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: channel a massive", NamedTextColor.GRAY),
                Component.text("arcane laser. Standing still boosts", NamedTextColor.GRAY),
                Component.text("every ability's damage.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Astral Ray";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ILLUSIONER_CAST_SPELL;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_DRAGON_FIREBALL_EXPLODE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_ENCHANTMENT_TABLE_USE;
    }

    private double standingStillMultiplier(Player player) {
        return player.getVelocity().lengthSquared() < 0.0025 ? 1.0 + standingStillBonus : 1.0;
    }

    @Override
    public void ability1(Player player) {
        double multiplier = rarity().statMultiplier() * standingStillMultiplier(player);
        Fx.spinningIcon(plugin, player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.8)).add(0, -0.2, 0),
                Material.END_CRYSTAL, 0.8f, missileCount * 3 + 15, 20);
        for (int i = 0; i < missileCount; i++) {
            int delay = i * 3;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Snowball missile = player.launchProjectile(Snowball.class,
                        player.getLocation().getDirection().multiply(missileSpeed));
                missile.setGravity(false);
                missile.getPersistentDataContainer().set(Weapon.idKey(plugin), PersistentDataType.STRING, id());
                missile.getPersistentDataContainer().set(missileDamageKey(), PersistentDataType.DOUBLE, missileDamage * multiplier);
                var missileIcon = Fx.spinningIcon(plugin, missile.getLocation(), Material.AMETHYST_SHARD, 0.6f, 40, 26.0);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!missile.isValid()) {
                            if (missileIcon != null && !missileIcon.isDead()) {
                                missileIcon.remove();
                            }
                            cancel();
                            return;
                        }
                        Fx.point(missile.getLocation(), Particle.END_ROD, 4);
                        Fx.point(missile.getLocation(), Particle.WITCH, 2);
                        Fx.coloredBurst(missile.getLocation(), ARCANE_PURPLE, 1.3f, 5, 0.06);
                        if (missileIcon != null && !missileIcon.isDead()) {
                            missileIcon.teleport(missile.getLocation());
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }, delay);
        }
    }

    private org.bukkit.NamespacedKey missileDamageKey() {
        return new org.bukkit.NamespacedKey(plugin, "arcane_staff_missile_damage");
    }

    @Override
    public void ability2(Player player) {
        double damage = beamDamage * rarity().statMultiplier() * standingStillMultiplier(player);
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), beamRange,
                entity -> entity instanceof LivingEntity && !entity.equals(player));

        Location end = result != null ? result.getHitPosition().toLocation(player.getWorld())
                : eye.clone().add(eye.getDirection().multiply(beamRange));
        Fx.line(eye, end, Particle.END_ROD, 45);
        purpleBeamCore(eye, end, 18);
        Fx.sound(player, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.4f);

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, player);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
            Fx.burst(target.getLocation().add(0, 1, 0), Particle.WITCH, 28, 0.5);
            Fx.coloredBurst(target.getLocation().add(0, 1, 0), ARCANE_PURPLE, 2.0f, 30, 0.55);
        }
    }

    /**
     * Layers a violet dust core under an END_ROD beam line so it reads as "arcane" instead of a generic
     * white line. Draws three parallel offset copies (center + both sides) so the beam has real width
     * instead of being one thin wire of dust.
     */
    private void purpleBeamCore(Location from, Location to, int points) {
        Vector step = to.toVector().subtract(from.toVector());
        Vector dir = step.lengthSquared() > 0.0001 ? step.clone().normalize() : new Vector(1, 0, 0);
        Vector horizontal = new Vector(-dir.getZ(), 0, dir.getX());
        Vector perp = (horizontal.lengthSquared() > 0.0001 ? horizontal.normalize() : new Vector(1, 0, 0)).multiply(0.35);
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            Location point = from.clone().add(step.clone().multiply(t));
            Fx.coloredBurst(point, ARCANE_PURPLE, 1.3f, 3, 0.05);
            Fx.coloredBurst(point.clone().add(perp), ARCANE_PURPLE, 1.1f, 2, 0.04);
            Fx.coloredBurst(point.clone().subtract(perp), ARCANE_PURPLE, 1.1f, 2, 0.04);
        }
    }

    @Override
    public void ability3(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        Location target = player.getLocation().clone();
        double step = 0.5;
        Location best = player.getLocation();
        for (double d = step; d <= blinkDistance; d += step) {
            Location candidate = player.getLocation().add(direction.clone().multiply(d));
            if (!candidate.getBlock().getType().isSolid() && !candidate.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                best = candidate;
            } else {
                break;
            }
        }
        Location startPoint = player.getLocation().add(0, 1, 0);
        Location endPoint = best.clone().add(0, 1, 0);
        purpleBeamCore(startPoint, endPoint, 18);
        Fx.burst(startPoint, Particle.WITCH, 24, 0.5);
        player.teleport(best);
        Fx.coloredBurst(best, ARCANE_PURPLE, 2.2f, 45, 0.7);
        Fx.burst(best, Particle.PORTAL, 24, 0.5);
        Fx.sound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
    }

    @Override
    public void ultimate(Player player) {
        double damagePerTick = laserDamagePerTick * rarity().statMultiplier() * standingStillMultiplier(player);
        Fx.spinningIcon(plugin, player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.0)),
                Material.NETHER_STAR, 1.1f, laserDurationTicks, 18);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= laserDurationTicks) {
                    cancel();
                    return;
                }
                Location eye = player.getEyeLocation();
                RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), laserRange,
                        entity -> entity instanceof LivingEntity && !entity.equals(player));
                Location end = result != null ? result.getHitPosition().toLocation(player.getWorld())
                        : eye.clone().add(eye.getDirection().multiply(laserRange));
                boolean flicker = ticks % 2 == 0;
                Fx.line(eye, end, Particle.END_ROD, flicker ? 40 : 28);
                Fx.dragonBreathLine(eye, end, flicker ? 20 : 14);
                purpleBeamCore(eye, end, flicker ? 18 : 12);

                if (result != null && result.getHitEntity() instanceof LivingEntity target) {
                    target.setNoDamageTicks(0);
                    target.damage(damagePerTick, player);
                    Fx.bloodSpray(target.getLocation().add(0, 1, 0));
                    if (ticks % 5 == 0) {
                        Fx.coloredBurst(target.getLocation().add(0, 1, 0), ARCANE_PURPLE, 2.2f, 30, 0.55);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        Fx.sound(player, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);
    }

    @Override
    public void onProjectileHit(Player shooter, ProjectileHitEvent event) {
        Location loc = event.getEntity().getLocation();
        Double taggedDamage = event.getEntity().getPersistentDataContainer()
                .get(missileDamageKey(), PersistentDataType.DOUBLE);
        double damage = taggedDamage != null ? taggedDamage : missileDamage * rarity().statMultiplier();

        Fx.burst(loc, Particle.WITCH, 32, 0.6);
        Fx.burst(loc, Particle.END_ROD, 24, 0.45);
        Fx.coloredBurst(loc, ARCANE_PURPLE, 2.0f, 36, 0.6);
        Fx.sound(loc, hitSound(), 0.8f, 1.3f);

        Entity hitEntity = event.getHitEntity();
        if (hitEntity instanceof LivingEntity target) {
            target.damage(damage, shooter);
            Fx.bloodSpray(target.getLocation().add(0, 1, 0));
        }
    }
}
