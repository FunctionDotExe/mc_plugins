package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.kit.Props;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Demolitions weapon built entirely on the real {@link TNTPrimed} entity instead of a fake AoE
 * timer: every charge is genuine vanilla TNT with a real fuse, real gravity, and a real explosion —
 * only the block destruction is stripped out (via {@code WeaponPropListener}) so entity damage,
 * knockback, and the blinking-fuse look stay 100% authentic without cratering the map.
 */
public final class Blastcaller extends Weapon {

    private static final Color FUSE_ORANGE = Color.fromRGB(255, 90, 20);

    private final float tossPower;
    private final int tossFuseTicks;
    private final double tossSpeed;
    private final float satchelPower;
    private final int satchelFuseTicks;
    private final int ultimateCount;
    private final float ultimatePower;
    private final int ultimateFuseBase;
    private final int ultimateFuseStagger;
    private final double ultimateSpreadDegrees;

    private final Map<UUID, TNTPrimed> activeCharge = new HashMap<>();

    public Blastcaller(WeaponsPlugin plugin) {
        super(plugin);
        this.tossPower = (float) configDouble("toss-power", 3.0);
        this.tossFuseTicks = configInt("toss-fuse-ticks", 50);
        this.tossSpeed = configDouble("toss-speed", 1.1);
        this.satchelPower = (float) configDouble("satchel-power", 3.5);
        this.satchelFuseTicks = configInt("satchel-fuse-ticks", 70);
        this.ultimateCount = configInt("ultimate-count", 5);
        this.ultimatePower = (float) configDouble("ultimate-power", 2.5);
        this.ultimateFuseBase = configInt("ultimate-fuse-base", 30);
        this.ultimateFuseStagger = configInt("ultimate-fuse-stagger", 6);
        this.ultimateSpreadDegrees = configDouble("ultimate-spread-degrees", 40.0);
    }

    @Override
    public String id() {
        return "blastcaller";
    }

    @Override
    public Material material() {
        return Material.TNT;
    }

    @Override
    public String displayNameText() {
        return "Blastcaller";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
    }

    @Override
    public double baseMeleeDamage() {
        return configDouble("melee-damage-bonus", 2.0);
    }

    @Override
    public double ability1CooldownSeconds() {
        return configDouble("ability1-cooldown-seconds", 5.0);
    }

    @Override
    public double ability2CooldownSeconds() {
        return configDouble("ability2-cooldown-seconds", 3.0);
    }

    @Override
    public double ability3CooldownSeconds() {
        return configDouble("ability3-cooldown-seconds", 9.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 45.0);
    }

    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Payload")
                .accent(FUSE_ORANGE)
                .perMeleeHit(configDouble("payload-per-hit", 4.0))
                .perDamageDealt(configDouble("payload-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("payload-per-ability", 10.0))
                .perKill(configDouble("payload-per-kill", 12.0))
                .decay(configDouble("payload-decay-per-second", 2.0), configDouble("payload-decay-grace", 7.0))
                .cooldownFloor(configDouble("payload-cooldown-floor", 42.0))
                .build();
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: prime and toss a real", NamedTextColor.GRAY),
                Component.text("stick of TNT with a burning fuse.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Prime & Toss";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: detonate your", NamedTextColor.GRAY),
                Component.text("last thrown charge early, wherever", NamedTextColor.GRAY),
                Component.text("it currently is.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Remote Detonate";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: plant a stationary charge", NamedTextColor.GRAY),
                Component.text("where you're looking — a long fuse", NamedTextColor.GRAY),
                Component.text("you can also remote-detonate.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Satchel Charge";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: hurl a spread of", NamedTextColor.GRAY),
                Component.text("charges that go off in a rolling", NamedTextColor.GRAY),
                Component.text("sequence of explosions.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Carpet Bomb";
    }

    @Override
    public Sound castSound() {
        return Sound.ENTITY_TNT_PRIMED;
    }

    @Override
    public Sound hitSound() {
        return Sound.ENTITY_GENERIC_EXPLODE;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_NOTE_BLOCK_PLING;
    }

    private TNTPrimed spawnCharge(Player owner, Location at, Vector velocity, int fuseTicks, float power) {
        if (at.getWorld() == null) {
            return null;
        }
        TNTPrimed tnt = Props.tnt(plugin, this, owner, at, velocity, fuseTicks);
        tnt.setYield(power * (float) rarity().statMultiplier());
        tnt.setIsIncendiary(false);
        activeCharge.put(owner.getUniqueId(), tnt);
        Fx.sound(at, castSound(), 1.0f, 1.0f);
        return tnt;
    }

    @Override
    public void ability1(Player player) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize().multiply(tossSpeed).setY(0.25);
        spawnCharge(player, start, direction, tossFuseTicks, tossPower);
    }

    @Override
    public void ability2(Player player) {
        TNTPrimed charge = activeCharge.get(player.getUniqueId());
        if (charge == null || !charge.isValid()) {
            Fx.sound(player, Sound.BLOCK_LEVER_CLICK, 0.7f, 1.4f);
            return;
        }
        Fx.sound(charge.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.2f, 2.0f);
        charge.setFuseTicks(0);
        activeCharge.remove(player.getUniqueId());
    }

    @Override
    public void ability3(Player player) {
        Block targetBlock = player.getTargetBlockExact(20, FluidCollisionMode.NEVER);
        Location at = targetBlock != null
                ? targetBlock.getLocation().add(0.5, 1, 0.5)
                : player.getLocation().add(player.getLocation().getDirection().normalize().multiply(3));
        spawnCharge(player, at, new Vector(0, 0, 0), satchelFuseTicks, satchelPower);
    }

    @Override
    public void ultimate(Player player) {
        Vector base = player.getLocation().getDirection().normalize();
        int half = ultimateCount / 2;
        Fx.sound(player, Sound.ENTITY_TNT_PRIMED, 1.3f, 0.7f);

        for (int i = 0; i < ultimateCount; i++) {
            double offset = (i - half) * (ultimateSpreadDegrees / Math.max(1, ultimateCount - 1)) - ultimateSpreadDegrees / 2;
            Vector direction = rotateY(base, Math.toRadians(offset)).multiply(tossSpeed * (0.9 + ThreadLocalRandom.current().nextDouble(0, 0.3)));
            direction.setY(0.3 + ThreadLocalRandom.current().nextDouble(0, 0.2));
            int fuse = ultimateFuseBase + i * ultimateFuseStagger;
            TNTPrimed charge = spawnCharge(player, player.getEyeLocation(), direction, fuse, ultimatePower);
            if (charge != null) {
                // Ultimate charges are fire-and-forget; don't let the last one steal the ability2 remote-detonate slot from a deliberate toss.
                activeCharge.remove(player.getUniqueId());
            }
        }
    }

    private static Vector rotateY(Vector v, double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        double x = v.getX() * cos - v.getZ() * sin;
        double z = v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }
}
