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
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/** Fishing rod reskin: a silly-but-fun utility weapon that hooks whatever's in your sights and reels it straight to you. */
public final class AnglersHook extends Weapon {

    private static final Color WATER_BLUE = Color.fromRGB(70, 130, 180);

    private final double hookRange;
    private final double hookDamage;
    private final double hookPullStrength;

    public AnglersHook(WeaponsPlugin plugin) {
        super(plugin);
        this.hookRange = configDouble("hook-range", 20.0);
        this.hookDamage = configDouble("hook-damage", 1.0);
        this.hookPullStrength = configDouble("hook-pull-strength", 1.6);
    }

    @Override
    public String id() {
        return "anglers_hook";
    }

    @Override
    public Material material() {
        return Material.FISHING_ROD;
    }

    @Override
    public String displayNameText() {
        return "Angler's Hook";
    }

    @Override
    public Rarity rarity() {
        return Rarity.RARE;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 0.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 6.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: hook whatever you're", NamedTextColor.GRAY),
                Component.text("looking at and reel it straight to you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Reel It In";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_FISHING_BOBBER_THROW;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_FISHING_BOBBER_RETRIEVE;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_FISHING_BOBBER_RETRIEVE;
    }

    @Override
    public void ability1(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        RayTraceResult trace = player.getWorld().rayTraceEntities(eye, direction, hookRange, 0.5,
                entity -> entity instanceof LivingEntity && !entity.equals(player));

        if (trace == null || !(trace.getHitEntity() instanceof LivingEntity target)) {
            // A real bobber flies out and off into the distance instead of a particle line on a miss.
            FishHook missedHook = player.launchProjectile(FishHook.class, direction.clone().multiply(1.4));
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (missedHook.isValid()) {
                        missedHook.remove();
                    }
                }
            }.runTaskLater(plugin, 20L);
            return;
        }

        Location targetLoc = target.getLocation();
        // A real FishHook entity lodges at the target instead of a particle line standing in for one.
        FishHook hook = player.launchProjectile(FishHook.class, direction.clone().multiply(0.01));
        hook.teleport(targetLoc.clone().add(0, 1, 0));
        Fx.coloredBurst(targetLoc.clone().add(0, 1, 0), WATER_BLUE, 1.2f, 16, 0.35);
        Fx.sound(player, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 1.0f);

        target.damage(hookDamage * rarity().statMultiplier(), player);
        Vector pull = eye.toVector().subtract(targetLoc.toVector()).normalize()
                .multiply(hookPullStrength).setY(0.35);
        target.setVelocity(target.getVelocity().add(pull));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (hook.isValid()) {
                    hook.remove();
                }
            }
        }.runTaskLater(plugin, 10L);
    }
}
