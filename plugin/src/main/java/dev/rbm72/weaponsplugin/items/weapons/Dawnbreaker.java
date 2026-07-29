package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/** The Solar Colossus's blade: a radiant greatsword of beams, blinding flares, judgment pillars, and a healing daybreak nova. */
public final class Dawnbreaker extends Weapon {

    private static final Color SOLAR_GOLD = Color.fromRGB(255, 214, 120);
    private static final Color RADIANT_WHITE = Color.fromRGB(255, 250, 220);

    private final double daylightDamageBonus;
    private final double slashDamage;
    private final double slashRange;
    private final double flareDamage;
    private final double flareRange;
    private final double judgmentDamagePerTick;
    private final int judgmentDurationTicks;
    private final double judgmentRadius;
    private final double daybreakDamage;
    private final double daybreakRadius;
    private final double daybreakHeal;

    public Dawnbreaker(WeaponsPlugin plugin) {
        super(plugin);
        this.daylightDamageBonus = configDouble("daylight-damage-bonus", 0.4);
        this.slashDamage = configDouble("slash-damage", 9.0);
        this.slashRange = configDouble("slash-range", 15.0);
        this.flareDamage = configDouble("flare-damage", 7.0);
        this.flareRange = configDouble("flare-range", 6.0);
        this.judgmentDamagePerTick = configDouble("judgment-damage-per-tick", 2.5);
        this.judgmentDurationTicks = configInt("judgment-duration-ticks", 60);
        this.judgmentRadius = configDouble("judgment-radius", 2.0);
        this.daybreakDamage = configDouble("daybreak-damage", 8.0);
        this.daybreakRadius = configDouble("daybreak-radius", 5.0);
        this.daybreakHeal = configDouble("daybreak-heal", 8.0);
    }

    @Override
    public String id() {
        return "dawnbreaker";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    public String displayNameText() {
        return "Dawnbreaker";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 4.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 6.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 8.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 10.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 55.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Radiance")
                .accent(SOLAR_GOLD)
                .perMeleeHit(configDouble("radiance-per-hit", 6.0))
                .perDamageDealt(configDouble("radiance-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("radiance-per-ability", 8.0))
                .perKill(configDouble("radiance-per-kill", 12.0))
                .decay(configDouble("radiance-decay-per-second", 2.0), configDouble("radiance-decay-grace", 7.0))
                .cooldownFloor(configDouble("radiance-cooldown-floor", 50.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: cleave a radiant beam", NamedTextColor.GRAY),
                Component.text("of dawn straight ahead.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Radiant Slash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: unleash a solar", NamedTextColor.GRAY),
                Component.text("flare that blinds and burns foes ahead.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Sunburst";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: call down a pillar of", NamedTextColor.GRAY),
                Component.text("judgment light at your aim.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Judgment";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: erupt Daybreak — a", NamedTextColor.GRAY),
                Component.text("radiant nova that heals you.", NamedTextColor.GRAY),
                Component.text("Passive: deals bonus damage in daylight.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Daybreak";
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_BEACON_ACTIVATE;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_PLAYER_ATTACK_STRONG;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_BEACON_POWER_SELECT;
    }

    private boolean inDaylight(Player player) {
        World world = player.getWorld();
        if (world == null || !world.isDayTime()) {
            return false;
        }
        Block above = world.getHighestBlockAt(player.getLocation());
        return above.getY() <= player.getLocation().getBlockY();
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (inDaylight(attacker)) {
            event.setDamage(event.getDamage() * (1 + daylightDamageBonus));
            Fx.burst(victim.getLocation().add(0, 1, 0), Particle.END_ROD, 8, 0.3);
            Fx.coloredBurst(victim.getLocation().add(0, 1, 0), SOLAR_GOLD, 0.7f, 6, 0.25);
        }
    }

    // (1) Radiant Slash — a beam cleave straight ahead.
    @Override
    public void ability1(Player player) {
        double damage = slashDamage * rarity().statMultiplier();
        if (inDaylight(player)) {
            damage *= (1 + daylightDamageBonus);
        }
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), slashRange,
                entity -> entity instanceof LivingEntity && !entity.equals(player));
        Location end = result != null ? result.getHitPosition().toLocation(player.getWorld())
                : eye.clone().add(eye.getDirection().multiply(slashRange));

        Vector beamDir = eye.getDirection().normalize();
        Vector beamPerp = new Vector(-beamDir.getZ(), 0, beamDir.getX()).normalize().multiply(0.35);
        Fx.line(eye, end, Particle.END_ROD, 34);
        Fx.line(eye.clone().add(beamPerp), end.clone().add(beamPerp), Particle.END_ROD, 28);
        Fx.line(eye.clone().subtract(beamPerp), end.clone().subtract(beamPerp), Particle.END_ROD, 28);
        Fx.line(eye, end, Particle.FLAME, 18);
        Fx.coloredBurst(end.clone(), SOLAR_GOLD, 1.4f, 30, 0.45);
        Fx.sound(player, castSound(), 1.0f, 1.1f);

        World world = player.getWorld();
        for (Entity entity : world.getNearbyEntities(end, 2.5, 2.5, 2.5)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            living.damage(damage, player);
            living.setFireTicks(40);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    // (2) Solar Flare — blinding, burning cone.
    @Override
    public void ability2(Player player) {
        double damage = flareDamage * rarity().statMultiplier();
        if (inDaylight(player)) {
            damage *= (1 + daylightDamageBonus);
        }
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Fx.coloredBurst(origin.clone().add(0, 1, 0).add(direction.clone().multiply(flareRange * 0.5)), RADIANT_WHITE, 1.6f, 45, flareRange * 0.5);
        Fx.flash(origin.clone().add(0, 1.5, 0).add(direction.clone().multiply(2.0)), 2);
        Fx.sound(player, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);

        for (Entity entity : world.getNearbyEntities(origin, flareRange, flareRange, flareRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(origin.toVector()).normalize();
            if (direction.dot(toEntity) < 0.4) {
                continue;
            }
            living.damage(damage, player);
            living.setFireTicks(60);
            StatusEffectManager.apply(living, PotionEffectType.BLINDNESS, 60, 0);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
    }

    // (3) Judgment — a pillar of light at the aim point.
    @Override
    public void ability3(Player player) {
        double damagePerTick = judgmentDamagePerTick * rarity().statMultiplier();
        Location target = player.getTargetBlockExact(20) != null
                ? player.getTargetBlockExact(20).getLocation().add(0.5, 1, 0.5)
                : player.getLocation();
        World world = target.getWorld();
        if (world == null) {
            return;
        }
        Fx.sound(target, Sound.ENTITY_EVOKER_CAST_SPELL, 1.2f, 0.9f);
        Fx.glowPillar(plugin, target.clone(), Material.GLOWSTONE, 0.45f, 18f, judgmentDurationTicks);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= judgmentDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                angle += 0.45;
                double height = ticks % 14;
                Fx.helixFrame(target, Particle.END_ROD, judgmentRadius * 0.7, 12, angle, height);
                Fx.helixFrame(target, Particle.END_ROD, judgmentRadius * 1.2, 16, -angle * 0.8, height + 0.5);
                Fx.coloredBurst(target.clone().add(0, height, 0), SOLAR_GOLD, 1.3f, 12, 0.45);

                if (ticks % 10 == 0) {
                    for (Entity entity : world.getNearbyEntities(target, judgmentRadius, 5, judgmentRadius)) {
                        if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                            continue;
                        }
                        living.damage(damagePerTick, player);
                        living.setFireTicks(30);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // Ultimate: Daybreak — radiant nova that heals the wielder.
    @Override
    public void ultimate(Player player) {
        double damage = daybreakDamage * rarity().statMultiplier();
        if (inDaylight(player)) {
            damage *= (1 + daylightDamageBonus);
        }
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Fx.burst(center.clone().add(0, 1, 0), Particle.FLAME, 75, daybreakRadius * 0.55);
        Fx.burst(center.clone().add(0, 1, 0), Particle.END_ROD, 60, daybreakRadius * 0.55);
        Fx.coloredBurst(center.clone().add(0, 1, 0), RADIANT_WHITE, 1.8f, 55, daybreakRadius * 0.6);
        Fx.expandingRings(plugin, center.clone().add(0, 0.1, 0), Particle.END_ROD, daybreakRadius, 5, 2L);
        Fx.flash(center.clone().add(0, 1, 0), 2);
        Fx.sound(player, Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.7f);

        for (Entity entity : world.getNearbyEntities(center, daybreakRadius, daybreakRadius, daybreakRadius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            living.damage(damage, player);
            living.setFireTicks(80);
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }

        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHealth, player.getHealth() + daybreakHeal));
        Fx.coloredBurst(player.getLocation().add(0, 1, 0), SOLAR_GOLD, 1.2f, 20, 0.5);
    }
}
