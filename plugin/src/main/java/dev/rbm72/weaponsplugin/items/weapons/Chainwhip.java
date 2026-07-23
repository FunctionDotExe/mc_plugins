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

import java.util.List;

/** Chain-reach weapon: every hit tugs enemies toward the wielder, with a lashing single-target yank and a channeled area drag. */
public final class Chainwhip extends Weapon {

    private static final Color CHAIN_COLOR = Color.fromRGB(90, 90, 100);

    private final double meleeDamageBonus;
    private final double onHitPullStrength;
    private final double chainLashRange;
    private final double chainLashDamage;
    private final double chainLashPullStrength;
    private final double chainSweepRadius;
    private final double chainSweepDamage;
    private final int chainSweepDurationTicks;

    public Chainwhip(WeaponsPlugin plugin) {
        super(plugin);
        this.meleeDamageBonus = configDouble("melee-damage-bonus", 2.0);
        this.onHitPullStrength = configDouble("on-hit-pull-strength", 0.15);
        this.chainLashRange = configDouble("chain-lash-range", 12.0);
        this.chainLashDamage = configDouble("chain-lash-damage", 5.0);
        this.chainLashPullStrength = configDouble("chain-lash-pull-strength", 1.1);
        this.chainSweepRadius = configDouble("chain-sweep-radius", 5.0);
        this.chainSweepDamage = configDouble("chain-sweep-damage", 4.0);
        this.chainSweepDurationTicks = configInt("chain-sweep-duration-ticks", 20);
    }

    @Override
    public String id() {
        return "chainwhip";
    }

    @Override
    public Material material() {
        return Material.IRON_CHAIN;
    }

    @Override
    public String displayNameText() {
        return "Chainwhip";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return meleeDamageBonus;
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 7.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 10.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: lash the nearest foe", NamedTextColor.GRAY),
                Component.text("ahead of you and yank them in.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Chain Lash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: drag every nearby", NamedTextColor.GRAY),
                Component.text("enemy toward you, then strike.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Chain Sweep";
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_CHAIN_STEP;
    }

    @Override
    public Sound hitSound() {
        return Sound.BLOCK_CHAIN_HIT;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_CHAIN_PLACE;
    }

    @Override
    public void onMeleeDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        Vector pullDirection = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0.1);
        if (pullDirection.lengthSquared() < 0.001) {
            return;
        }
        pullDirection = pullDirection.normalize().multiply(onHitPullStrength);
        victim.setVelocity(victim.getVelocity().add(pullDirection));
    }

    @Override
    public void ability1(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        LivingEntity target = null;
        double closest = chainLashRange;
        for (Entity entity : world.getNearbyEntities(eye, chainLashRange, chainLashRange, chainLashRange)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            Vector toEntity = living.getLocation().toVector().subtract(eye.toVector());
            double distance = toEntity.length();
            if (distance > chainLashRange || distance < 0.001) {
                continue;
            }
            double alignment = toEntity.normalize().dot(direction);
            if (alignment > 0.9 && distance < closest) {
                closest = distance;
                target = living;
            }
        }

        Fx.sound(player, castSound(), 1.0f, 0.9f);
        if (target == null) {
            Fx.line(eye, eye.clone().add(direction.clone().multiply(chainLashRange)), Particle.CRIT, 20);
            return;
        }

        double damage = chainLashDamage * rarity().statMultiplier();
        Fx.line(eye, target.getLocation().add(0, 1, 0), Particle.CRIT, 20);
        Fx.coloredBurst(target.getLocation().add(0, 1, 0), CHAIN_COLOR, 1.4f, 18, 0.4);
        Fx.sound(target.getLocation(), hitSound(), 1.0f, 0.9f);

        target.damage(damage, player);
        Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0.25);
        if (pull.lengthSquared() > 0.001) {
            target.setVelocity(pull.normalize().multiply(chainLashPullStrength).setY(0.25));
        }
        Fx.bloodSpray(target.getLocation().add(0, 1, 0));
    }

    @Override
    public void ability2(Player player) {
        double damage = chainSweepDamage * rarity().statMultiplier();
        Fx.sound(player, castSound(), 1.0f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= chainSweepDurationTicks || !player.isOnline()) {
                    Location center = player.getLocation();
                    World world = center.getWorld();
                    if (world != null) {
                        Fx.coloredBurst(center.clone().add(0, 1, 0), CHAIN_COLOR, 1.8f, 24, 0.5);
                        Fx.sound(center, hitSound(), 1.1f, 0.8f);
                        for (Entity entity : world.getNearbyEntities(center, chainSweepRadius, chainSweepRadius, chainSweepRadius)) {
                            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                                living.damage(damage, player);
                                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                            }
                        }
                    }
                    cancel();
                    return;
                }

                Location center = player.getLocation();
                World world = center.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }
                Fx.coloredRing(center, CHAIN_COLOR, 1.1f, chainSweepRadius * 0.8, 20, angle);
                angle += 0.5;

                for (Entity entity : world.getNearbyEntities(center, chainSweepRadius, chainSweepRadius, chainSweepRadius)) {
                    if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                        continue;
                    }
                    Vector pull = center.toVector().subtract(living.getLocation().toVector()).setY(0.15);
                    if (pull.lengthSquared() > 0.001) {
                        living.setVelocity(living.getVelocity().add(pull.normalize().multiply(0.22)));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
