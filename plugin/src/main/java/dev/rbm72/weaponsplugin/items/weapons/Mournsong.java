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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Mournsong — a totem stitched from every voice the Hollow Choir ever swallowed. A wailing fear
 * pulse, a summoned wraith ally, a short fang burst, and an ultimate that calls a whole choir to
 * fight alongside the wielder for a few seconds.
 */
public final class Mournsong extends Weapon {

    private static final Color PALE_VIOLET = Color.fromRGB(160, 130, 220);

    private final double wailRadius;
    private final double wailDamage;
    private final int wailWeaknessTicks;
    private final double fangRange;
    private final double fangDamage;
    private final int fangCount;
    private final int wraithDurationTicks;
    private final double wraithHealth;
    private final int requiemDurationTicks;
    private final double requiemHealth;

    public Mournsong(WeaponsPlugin plugin) {
        super(plugin);
        this.wailRadius = configDouble("wail-radius", 5.0);
        this.wailDamage = configDouble("wail-damage", 4.5);
        this.wailWeaknessTicks = configInt("wail-weakness-ticks", 80);
        this.fangRange = configDouble("fang-range", 5.0);
        this.fangDamage = configDouble("fang-damage", 6.0);
        this.fangCount = configInt("fang-count", 3);
        this.wraithDurationTicks = configInt("wraith-duration-ticks", 200);
        this.wraithHealth = configDouble("wraith-health", 10.0);
        this.requiemDurationTicks = configInt("requiem-duration-ticks", 240);
        this.requiemHealth = configDouble("requiem-health", 14.0);
    }

    @Override
    public String id() {
        return "mournsong";
    }

    @Override
    public Material material() {
        return Material.TOTEM_OF_UNDYING;
    }

    @Override
    public String displayNameText() {
        return "Mournsong";
    }

    @Override
    public Rarity rarity() {
        return Rarity.LEGENDARY;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 5.5);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 10.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 20.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 7.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 60.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Chorus")
                .accent(PALE_VIOLET)
                .perMeleeHit(configDouble("chorus-per-hit", 6.0))
                .perDamageDealt(configDouble("chorus-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("chorus-per-ability", 9.0))
                .perKill(configDouble("chorus-per-kill", 12.0))
                .decay(configDouble("chorus-decay-per-second", 2.2), configDouble("chorus-decay-grace", 7.0))
                .cooldownFloor(configDouble("chorus-cooldown-floor", 55.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: Wailing Note — a pulse of", NamedTextColor.GRAY),
                Component.text("dissonance weakens everyone nearby.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Wailing Note";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: Summon Wraith — a", NamedTextColor.GRAY),
                Component.text("spectral ally fights at your side briefly.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Summon Wraith";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: Fang Burst — a short line of", NamedTextColor.GRAY),
                Component.text("spectral fangs erupts ahead of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Fang Burst";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: Full Requiem — a small", NamedTextColor.GRAY),
                Component.text("choir of allies answers your call.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Full Requiem";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_ILLUSIONER_CAST_SPELL;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_VEX_HURT;
    }

    @Override
    public Sound readySound() {
        return Sound.ENTITY_VEX_AMBIENT;
    }

    @Override
    public void ability1(Player player) {
        double damage = wailDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        World world = player.getWorld();
        Fx.coloredBurst(origin.clone().add(0, 1.2, 0), PALE_VIOLET, 2.0f, 40, 0.7);
        Fx.sound(player, castSound(), 1.0f, 0.6f);
        for (Entity entity : world.getNearbyEntities(origin, wailRadius, wailRadius, wailRadius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.damage(damage, player);
                StatusEffectManager.apply(living, PotionEffectType.WEAKNESS, wailWeaknessTicks, 0);
                Fx.bloodSpray(living.getLocation().add(0, 1, 0));
            }
        }
    }

    @Override
    public void ability2(Player player) {
        Location spot = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(2));
        World world = spot.getWorld();
        if (world == null) {
            return;
        }
        Fx.burst(spot.clone().add(0, 1, 0), Particle.WITCH, 20, 0.4);
        Fx.sound(player, Sound.ENTITY_VEX_CHARGE, 1.0f, 1.0f);
        if (world.spawnEntity(spot, org.bukkit.entity.EntityType.VEX) instanceof Vex vex) {
            vex.customName(Component.text("Mournwraith", NamedTextColor.LIGHT_PURPLE));
            vex.setCustomNameVisible(true);
            var maxHealthAttr = vex.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(wraithHealth);
                vex.setHealth(wraithHealth);
            }
            retargetToNearestHostile(vex, player);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (vex.isValid()) {
                        vex.remove();
                    }
                }
            }.runTaskLater(plugin, wraithDurationTicks);
        }
    }

    @Override
    public void ability3(Player player) {
        double damage = fangDamage * rarity().statMultiplier();
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize();
        Fx.sound(player, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.9f);
        for (int i = 1; i <= fangCount; i++) {
            Location spot = origin.clone().add(direction.clone().multiply(i * 1.6));
            new BukkitRunnable() {
                @Override
                public void run() {
                    Fx.coloredBurst(spot.clone().add(0, 0.3, 0), Color.fromRGB(230, 230, 240), 1.6f, 18, 0.3);
                    // A real evoker fang erupting from the ground instead of a particle-only burst.
                    EvokerFangs fangs = player.getWorld().spawn(spot, EvokerFangs.class);
                    fangs.setOwner(player);
                    for (Entity entity : player.getWorld().getNearbyEntities(spot, 1.4, 1.4, 1.4)) {
                        if (entity instanceof LivingEntity living && !entity.equals(player)) {
                            living.damage(damage, player);
                            Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                        }
                    }
                }
            }.runTaskLater(plugin, (i - 1) * 3L);
        }
    }

    @Override
    public void ultimate(Player player) {
        Location origin = player.getLocation();
        World world = player.getWorld();
        Fx.coloredBurst(origin.clone().add(0, 1, 0), PALE_VIOLET, 2.4f, 60, 0.9);
        Fx.sound(player, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.3f, 0.5f);
        for (int i = 0; i < 3; i++) {
            double angle = 2 * Math.PI * i / 3;
            Location spot = origin.clone().add(Math.cos(angle) * 2.0, 1.0, Math.sin(angle) * 2.0);
            if (world.spawnEntity(spot, org.bukkit.entity.EntityType.VEX) instanceof Vex vex) {
                vex.customName(Component.text("Requiem Wraith", NamedTextColor.LIGHT_PURPLE));
                vex.setCustomNameVisible(true);
                var maxHealthAttr = vex.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    maxHealthAttr.setBaseValue(requiemHealth);
                    vex.setHealth(requiemHealth);
                }
                retargetToNearestHostile(vex, player);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (vex.isValid()) {
                            vex.remove();
                        }
                    }
                }.runTaskLater(plugin, requiemDurationTicks);
            }
        }
    }

    private void retargetToNearestHostile(Mob ally, Player player) {
        LivingEntity nearestHostile = null;
        double closest = 16.0;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 16, 16, 16)) {
            if (entity instanceof Monster monster) {
                double dist = monster.getLocation().distance(player.getLocation());
                if (dist < closest) {
                    closest = dist;
                    nearestHostile = monster;
                }
            }
        }
        if (nearestHostile != null) {
            ally.setTarget(nearestHostile);
        }
    }
}
