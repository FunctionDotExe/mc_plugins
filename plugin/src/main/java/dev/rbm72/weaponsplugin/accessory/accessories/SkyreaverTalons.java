package dev.rbm72.weaponsplugin.accessory.accessories;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.accessory.Accessory;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.util.Grounded;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Two-stage aerial-combat ability: the first trigger (grounded) launches the wielder into a
 * hovering aerial stance; a second trigger while airborne converts that into a dive-slam that
 * lands as an AOE nuke. The damage bonus for landing melee hits mid-air is applied separately by
 * {@code AerialStrikeListener}, since it needs the attacker's ground state at hit time rather than
 * anything this class tracks — same split {@link dev.rbm72.weaponsplugin.armor.sets.WyrmwingPlate}
 * uses with {@code WingDashListener}.
 */
public final class SkyreaverTalons extends Accessory {

    public static final double AERIAL_DAMAGE_BONUS = 0.25;

    private static final Color SKY_COLOR = Color.fromRGB(255, 140, 0);
    private static final double LAUNCH_UP_SPEED = 1.3;
    private static final double LAUNCH_FORWARD_SPEED = 0.5;
    private static final int HOVER_SLOW_FALL_TICKS = 70;
    private static final double DIVE_SPEED = 1.8;
    private static final int DIVE_TIMEOUT_TICKS = 100;
    private static final double SLAM_DAMAGE = 9.0;
    private static final double SLAM_RADIUS = 3.5;

    public SkyreaverTalons(WeaponsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "skyreaver_talons";
    }

    @Override
    public Material material() {
        return Material.ELYTRA;
    }

    @Override
    public String displayNameText() {
        return "Skyreaver Talons";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.text("+25% damage", NamedTextColor.GOLD)
                        .append(Component.text(" on melee hits landed", NamedTextColor.GRAY)),
                Component.text("while you're airborne.", NamedTextColor.GRAY));
    }

    @Override
    public boolean hasPersonalAbility() {
        return true;
    }

    @Override
    public String personalAbilityName() {
        return "Sky Dive";
    }

    @Override
    public List<Component> personalAbilityLore() {
        return List.of(
                Component.text("Grounded: launches you into a", NamedTextColor.GRAY),
                Component.text("hovering aerial stance to fight", NamedTextColor.GRAY),
                Component.text("from. Airborne: converts that", NamedTextColor.GRAY),
                Component.text("into a dive-slam, nuking", NamedTextColor.GRAY),
                Component.text("everything around your landing.", NamedTextColor.GRAY));
    }

    @Override
    public double personalAbilityCooldownSeconds() {
        return 8.0;
    }

    @Override
    public void personalAbility(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (Grounded.onGround(player)) {
            launchIntoAerialStance(player);
        } else {
            diveSlam(player);
        }
    }

    private void launchIntoAerialStance(Player player) {
        Vector velocity = player.getLocation().getDirection().normalize().multiply(LAUNCH_FORWARD_SPEED);
        velocity.setY(LAUNCH_UP_SPEED);
        player.setVelocity(velocity);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, HOVER_SLOW_FALL_TICKS, 0, false, true, true));

        Fx.sound(player, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.9f);
        Fx.sound(player, Sound.ITEM_ELYTRA_FLYING, 1.0f, 1.3f);
        Fx.coloredBurst(player.getLocation(), SKY_COLOR, 1.5f, 24, 0.5);
    }

    private void diveSlam(Player player) {
        player.setVelocity(new Vector(0, -DIVE_SPEED, 0));
        player.setFallDistance(0f);
        Fx.sound(player, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
        Fx.trail(player.getLocation(), Particle.CLOUD, 6, 0.2, 0.02);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (Grounded.onGround(player) || ticks >= DIVE_TIMEOUT_TICKS) {
                    cancel();
                    if (player.isOnline()) {
                        slamImpact(player);
                    }
                    return;
                }
                player.setFallDistance(0f);
                Fx.point(player.getLocation().add(0, 1, 0), Particle.FLAME, 2);
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void slamImpact(Player player) {
        Location loc = player.getLocation();
        Fx.expandingRings(plugin, loc, Particle.FLAME, SLAM_RADIUS, 3, 2L);
        Fx.coloredBurst(loc.clone().add(0, 0.3, 0), SKY_COLOR, 1.8f, 30, 1.1);
        Fx.sound(player, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

        for (Entity nearby : player.getNearbyEntities(SLAM_RADIUS, SLAM_RADIUS, SLAM_RADIUS)) {
            if (!(nearby instanceof LivingEntity entity) || entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            entity.damage(SLAM_DAMAGE * rarity().statMultiplier(), player);
            Fx.bloodSpray(entity.getLocation().add(0, 1, 0));

            Vector away = entity.getLocation().toVector().subtract(loc.toVector());
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            entity.setVelocity(away.normalize().multiply(0.9).setY(0.4));
        }
    }
}
