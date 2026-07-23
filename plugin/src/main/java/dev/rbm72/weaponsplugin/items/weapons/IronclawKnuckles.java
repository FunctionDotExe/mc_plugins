package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Fist weapon: short, punchy cooldowns built around knockback rather than raw damage — every hit shoves, Haymaker shoves hard, Flurry chains into a final burst. */
public final class IronclawKnuckles extends Weapon {

    private static final Color DULL_IRON = Color.fromRGB(140, 140, 150);

    private final double meleeDamageBonus;
    private final double punchKnockbackStrength;
    private final double haymakerDamage;
    private final double haymakerRadius;
    private final double haymakerKnockback;
    private final int flurryHits;
    private final int flurryHitDelayTicks;
    private final double flurryDamagePerHit;
    private final double flurryRadius;

    public IronclawKnuckles(WeaponsPlugin plugin) {
        super(plugin);
        this.meleeDamageBonus = configDouble("melee-damage-bonus", 1.5);
        this.punchKnockbackStrength = configDouble("punch-knockback-strength", 0.35);
        this.haymakerDamage = configDouble("haymaker-damage", 6.0);
        this.haymakerRadius = configDouble("haymaker-radius", 2.5);
        this.haymakerKnockback = configDouble("haymaker-knockback", 1.4);
        this.flurryHits = configInt("flurry-hits", 3);
        this.flurryHitDelayTicks = configInt("flurry-hit-delay-ticks", 5);
        this.flurryDamagePerHit = configDouble("flurry-damage-per-hit", 2.5);
        this.flurryRadius = configDouble("flurry-radius", 2.0);
    }

    @Override
    public String id() {
        return "ironclaw_knuckles";
    }

    @Override
    public Material material() {
        return Material.SHEARS;
    }

    @Override
    public String displayNameText() {
        return "Ironclaw Knuckles";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return meleeDamageBonus;
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 3.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 6.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: wind up a bone-breaking", NamedTextColor.GRAY),
                Component.text("haymaker that sends nearby foes reeling.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Haymaker";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: unload three", NamedTextColor.GRAY),
                Component.text("lightning punches, ending in a", NamedTextColor.GRAY),
                Component.text("knockout blow.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Flurry";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_IRON_GOLEM_ATTACK;
    }

    @Override
    public Sound readySound() {
        return Sound.ITEM_ARMOR_EQUIP_IRON;
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        Vector knockback = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0.1);
        if (knockback.lengthSquared() < 0.001) {
            return;
        }
        knockback = knockback.normalize().multiply(punchKnockbackStrength);
        victim.setVelocity(victim.getVelocity().add(knockback));
    }

    @Override
    public void ability1(Player player) {
        double damage = haymakerDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        World world = origin.getWorld();
        Fx.sound(player, castSound(), 1.0f, 0.8f);
        if (world == null) {
            return;
        }

        for (Entity entity : world.getNearbyEntities(origin, haymakerRadius, haymakerRadius, haymakerRadius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            living.damage(damage, player);
            Vector push = living.getLocation().toVector().subtract(origin.toVector()).setY(0.35);
            if (push.lengthSquared() > 0.001) {
                living.setVelocity(push.normalize().multiply(haymakerKnockback).setY(0.35));
            }
            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
        }
        Fx.sound(origin, hitSound(), 1.0f, 0.9f);
        Fx.coloredBurst(origin.clone().add(0, 1, 0), DULL_IRON, 1.4f, 18, 0.4);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 4 || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation().add(0, 1, 0);
                Fx.point(loc, Particle.CRIT, 6);
                Fx.point(loc, Particle.SWEEP_ATTACK, 1);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability2(Player player) {
        double damagePerHit = flurryDamagePerHit * rarity().statMultiplier();
        Fx.sound(player, castSound(), 1.0f, 1.2f);

        new BukkitRunnable() {
            int hit = 0;

            @Override
            public void run() {
                if (hit >= flurryHits || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location origin = player.getLocation();
                World world = origin.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }
                boolean lastHit = hit == flurryHits - 1;
                Set<UUID> struck = new HashSet<>();
                Fx.coloredBurst(origin.clone().add(0, 1, 0), DULL_IRON, 1.0f, 10, 0.3);

                for (Entity entity : world.getNearbyEntities(origin, flurryRadius, flurryRadius, flurryRadius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player) || !struck.add(living.getUniqueId())) {
                        continue;
                    }
                    living.damage(damagePerHit, player);
                    if (lastHit) {
                        Vector push = living.getLocation().toVector().subtract(origin.toVector()).setY(0.4);
                        if (push.lengthSquared() > 0.001) {
                            living.setVelocity(living.getVelocity().add(push.normalize().multiply(haymakerKnockback)));
                        }
                    }
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                }
                Fx.sound(origin, hitSound(), 0.8f, 1.3f + hit * 0.1f);
                hit++;
            }
        }.runTaskTimer(plugin, 0L, flurryHitDelayTicks);
    }
}
