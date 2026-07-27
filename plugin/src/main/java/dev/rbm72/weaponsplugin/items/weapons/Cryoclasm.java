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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Terrain-control ice weapon: every ability is a real, temporary block edit — an ice path skated
 * across, a powder-snow patch that actually freezes whoever stands in it, a raised ice wall, and an
 * ultimate that boxes the target inside real packed-ice walls until they shatter free.
 */
public final class Cryoclasm extends Weapon {

    private static final Color ICE_BLUE = Color.fromRGB(160, 220, 255);

    private final double dashDamage;
    private final double dashSpeed;
    private final int dashDurationTicks;
    private final int iceRevertTicks;
    private final double freezeRadius;
    private final int freezeRevertTicks;
    private final int freezeSlowTicks;
    private final int wallLength;
    private final int wallHeight;
    private final int wallDurationTicks;
    private final double prisonDamage;
    private final int prisonDurationTicks;
    private final double prisonRadius;

    public Cryoclasm(WeaponsPlugin plugin) {
        super(plugin);
        this.dashDamage = configDouble("dash-damage", 4.0);
        this.dashSpeed = configDouble("dash-speed", 1.1);
        this.dashDurationTicks = configInt("dash-duration-ticks", 14);
        this.iceRevertTicks = configInt("ice-revert-ticks", 80);
        this.freezeRadius = configDouble("freeze-radius", 1.0);
        this.freezeRevertTicks = configInt("freeze-revert-ticks", 100);
        this.freezeSlowTicks = configInt("freeze-slow-ticks", 80);
        this.wallLength = configInt("wall-length", 3);
        this.wallHeight = configInt("wall-height", 3);
        this.wallDurationTicks = configInt("wall-duration-ticks", 100);
        this.prisonDamage = configDouble("prison-damage", 12.0);
        this.prisonDurationTicks = configInt("prison-duration-ticks", 70);
        this.prisonRadius = configDouble("prison-radius", 3.0);
    }

    @Override
    public String id() {
        return "cryoclasm";
    }

    @Override
    public Material material() {
        return Material.PACKED_ICE;
    }

    @Override
    public String displayNameText() {
        return "Cryoclasm";
    }

    @Override
    public Rarity rarity() {
        return Rarity.EPIC;
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
        return configDouble("ability3-cooldown-seconds", 10.0);
    }

    @Override
    public double ultimateCooldownSeconds() {
        return configDouble("ultimate-cooldown-seconds", 50.0);
    }

    @Override
    public List<Component> ability1Lore() {
        return List.of(
                Component.text("Right-click: dash forward laying", NamedTextColor.GRAY),
                Component.text("a real sheet of ice beneath you,", NamedTextColor.GRAY),
                Component.text("slashing through anything in the way.", NamedTextColor.GRAY));
    }

    @Override
    public String ability1Name() {
        return "Frostwalk Dash";
    }

    @Override
    public List<Component> ability2Lore() {
        return List.of(
                Component.text("Shift+right-click: bury the nearest", NamedTextColor.GRAY),
                Component.text("enemy's feet in real powder snow,", NamedTextColor.GRAY),
                Component.text("freezing and slowing them.", NamedTextColor.GRAY));
    }

    @Override
    public String ability2Name() {
        return "Deep Freeze";
    }

    @Override
    public List<Component> ability3Lore() {
        return List.of(
                Component.text("Off-hand: raise a real wall of", NamedTextColor.GRAY),
                Component.text("packed ice in front of you.", NamedTextColor.GRAY));
    }

    @Override
    public String ability3Name() {
        return "Ice Wall";
    }

    @Override
    public List<Component> ultimateLore() {
        return List.of(
                Component.text("Off-hand+Shift: entomb your target", NamedTextColor.GRAY),
                Component.text("in real packed ice. They're trapped", NamedTextColor.GRAY),
                Component.text("until it shatters into a burst.", NamedTextColor.GRAY));
    }

    @Override
    public String ultimateName() {
        return "Glacial Prison";
    }

    @Override
    public Sound castSound() {
        return Sound.BLOCK_GLASS_BREAK;
    }

    @Override
    public Sound hitSound() {
        return Sound.BLOCK_GLASS_HIT;
    }

    @Override
    public Sound readySound() {
        return Sound.BLOCK_GLASS_PLACE;
    }

    /** Fills {@code block} with {@code material} only if it's currently empty air, tracking the original for revert. Used anywhere a wall/cage/patch should never eat existing terrain. */
    private void placeIfAir(Block block, Material material, List<Block> placed, List<BlockData> originals) {
        if (!block.getType().isAir()) {
            return;
        }
        originals.add(block.getBlockData());
        block.setType(material, false);
        placed.add(block);
    }

    /** Resurfaces an existing solid, non-liquid ground block with {@code material}, tracking the original for revert. Used for the dash's ice trail, which needs to swap the ground itself rather than fill a gap. */
    private void resurface(Block block, Material material, List<Block> placed, List<BlockData> originals) {
        Material current = block.getType();
        if (current.isAir() || block.isLiquid() || current == Material.BEDROCK || current == Material.BARRIER || current == material) {
            return;
        }
        originals.add(block.getBlockData());
        block.setType(material, false);
        placed.add(block);
    }

    private void revertAfter(List<Block> placed, List<BlockData> originals, int delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < placed.size(); i++) {
                Block block = placed.get(i);
                if (block.getType() == Material.PACKED_ICE || block.getType() == Material.ICE || block.getType() == Material.POWDER_SNOW) {
                    block.setBlockData(originals.get(i), false);
                    Fx.blockBurst(block.getLocation().add(0.5, 0.5, 0.5), Material.PACKED_ICE, 10, 0.3);
                }
            }
        }, delayTicks);
    }

    @Override
    public void ability1(Player player) {
        double damage = dashDamage * rarity().statMultiplier();
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        player.setVelocity(direction.clone().multiply(dashSpeed).setY(0.1));
        Fx.sound(player, castSound(), 1.0f, 1.2f);

        List<Block> placed = new ArrayList<>();
        List<BlockData> originals = new ArrayList<>();
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= dashDurationTicks || !player.isOnline()) {
                    revertAfter(placed, originals, iceRevertTicks);
                    cancel();
                    return;
                }
                Block underfoot = player.getLocation().clone().subtract(0, 1, 0).getBlock();
                resurface(underfoot, Material.PACKED_ICE, placed, originals);
                Fx.coloredBurst(player.getLocation(), ICE_BLUE, 1.2f, 8, 0.3);
                for (Entity entity : world.getNearbyEntities(player.getLocation(), 1.3, 1.3, 1.3)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player) && alreadyHit.add(living.getUniqueId())) {
                        living.damage(damage, player);
                        Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void ability2(Player player) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        LivingEntity target = null;
        double closest = 10.0;
        for (Entity entity : world.getNearbyEntities(player.getLocation(), 10, 10, 10)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                double distance = living.getLocation().distanceSquared(player.getLocation());
                if (distance < closest) {
                    closest = distance;
                    target = living;
                }
            }
        }
        if (target == null) {
            Fx.sound(player, Sound.BLOCK_GLASS_BREAK, 0.6f, 0.6f);
            return;
        }

        Location feet = target.getLocation();
        List<Block> placed = new ArrayList<>();
        List<BlockData> originals = new ArrayList<>();
        BlockFace[] plus = {BlockFace.SELF, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : plus) {
            Block block = feet.getBlock().getRelative(face);
            placeIfAir(block, Material.POWDER_SNOW, placed, originals);
        }
        Fx.sound(target.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 0.8f);
        Fx.coloredBurst(feet.clone().add(0, 0.3, 0), ICE_BLUE, 1.6f, 24, freezeRadius);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeSlowTicks, 2, false, true));
        revertAfter(placed, originals, freezeRevertTicks);
    }

    @Override
    public void ability3(Player player) {
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().setY(0).normalize();
        BlockFace face = Math.abs(direction.getX()) > Math.abs(direction.getZ())
                ? (direction.getX() > 0 ? BlockFace.EAST : BlockFace.WEST)
                : (direction.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
        BlockFace side = (face == BlockFace.NORTH || face == BlockFace.SOUTH) ? BlockFace.EAST : BlockFace.NORTH;

        List<Block> placed = new ArrayList<>();
        List<BlockData> originals = new ArrayList<>();
        Block base = origin.getBlock().getRelative(face, 2);
        int half = wallLength / 2;

        for (int length = -half; length <= half; length++) {
            for (int height = 0; height < wallHeight; height++) {
                Block block = base.getRelative(side, length).getRelative(0, height, 0);
                placeIfAir(block, Material.PACKED_ICE, placed, originals);
            }
        }
        Fx.coloredBurst(base.getLocation().add(0.5, wallHeight * 0.5, 0.5), ICE_BLUE, 2.0f, 30, wallLength * 0.5);
        Fx.sound(player, castSound(), 1.0f, 0.9f);
        revertAfter(placed, originals, wallDurationTicks);
    }

    @Override
    public void ultimate(Player player) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        double damage = prisonDamage * rarity().statMultiplier();
        LivingEntity target = null;
        double closest = 12.0;
        for (Entity entity : world.getNearbyEntities(player.getLocation(), 12, 12, 12)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                double distance = living.getLocation().distanceSquared(player.getLocation());
                if (distance < closest) {
                    closest = distance;
                    target = living;
                }
            }
        }
        if (target == null) {
            Fx.sound(player, Sound.BLOCK_GLASS_BREAK, 0.6f, 0.6f);
            return;
        }
        final LivingEntity victim = target;

        Location center = victim.getLocation();
        List<Block> placed = new ArrayList<>();
        List<BlockData> originals = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    boolean shell = x == -1 || x == 1 || z == -1 || z == 1 || y == 0 || y == 2;
                    if (!shell) {
                        continue;
                    }
                    Block block = center.getBlock().getRelative(x, y, z);
                    placeIfAir(block, Material.PACKED_ICE, placed, originals);
                }
            }
        }
        Fx.sound(victim.getLocation(), Sound.BLOCK_GLASS_PLACE, 1.2f, 0.6f);
        Fx.coloredBurst(center.clone().add(0, 1, 0), ICE_BLUE, 2.4f, 40, 1.4);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, prisonDurationTicks, 6, false, true));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < placed.size(); i++) {
                Block block = placed.get(i);
                if (block.getType() == Material.PACKED_ICE) {
                    block.setBlockData(originals.get(i), false);
                }
            }
            if (!victim.isValid()) {
                return;
            }
            Location shatterCenter = victim.getLocation().add(0, 1, 0);
            Fx.sound(shatterCenter, Sound.BLOCK_GLASS_BREAK, 1.4f, 0.7f);
            Fx.coloredBurst(shatterCenter, ICE_BLUE, 2.6f, 50, prisonRadius * 0.6);
            Fx.blockBurst(shatterCenter, Material.PACKED_ICE, 40, 1.0);
            for (Entity nearby : world.getNearbyEntities(shatterCenter, prisonRadius, prisonRadius, prisonRadius)) {
                if (nearby instanceof LivingEntity living) {
                    living.damage(damage, player);
                    Fx.bloodSpray(living.getLocation().add(0, 1, 0));
                    Vector away = living.getLocation().toVector().subtract(shatterCenter.toVector());
                    if (away.lengthSquared() < 0.01) {
                        away = new Vector(1, 0, 0);
                    }
                    living.setVelocity(away.normalize().multiply(1.0).setY(0.4));
                }
            }
        }, prisonDurationTicks);
    }
}
