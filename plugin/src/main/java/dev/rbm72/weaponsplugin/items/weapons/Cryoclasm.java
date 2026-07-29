package dev.rbm72.weaponsplugin.items.weapons;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.ability.ChargeSpec;
import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.items.Rarity;
import dev.rbm72.weaponsplugin.items.Weapon;
import dev.rbm72.weaponsplugin.items.kit.Counterplay;
import dev.rbm72.weaponsplugin.items.kit.TempTerrain;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Terrain-control ice weapon: every ability is a real, temporary block edit — an ice path skated
 * across, a powder-snow patch that actually freezes whoever stands in it, a raised ice wall, and an
 * ultimate that boxes the target inside real packed-ice walls until they shatter free.
 * <p>
 * <b>This one was already §0.1-shaped and still had to be reworked</b>, for a reason worth recording: it
 * carried its own block ledger. Three private helpers ({@code placeIfAir}, {@code resurface},
 * {@code revertAfter}) tracked parallel lists of blocks and their original data, and their refusal rule was
 * a hand-written check for bedrock and barriers. That missed tile entities entirely — so
 * {@code resurface} would happily overwrite a chest, a sign or a spawner with packed ice, and the "revert"
 * afterwards restored a chest-shaped block with none of its contents, because {@link BlockData} does not
 * carry an inventory. A player's storage could be silently emptied by someone dashing past it.
 * <p>
 * Every write now goes through {@link TempTerrain}, which shares one refusal rule with the boss ledger via
 * {@link dev.rbm72.weaponsplugin.util.BlockGuard} and coordinates with the arena ledger when the ice lands
 * inside a live fight. The abilities are unchanged in feel; they just cannot eat a chest any more.
 * <p>
 * <b>Counterplay.</b> The ice specialist is the roster's answer to being frozen: it clears the stacks a
 * boss is piling on (Chill above all) and restores footing on a floor that has been turned to blue ice —
 * the two things the Frost Queen does that no amount of damage answers.
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

    /**
     * Glacial Prison is earned. It is a single-target hard lock plus a shatter, which is the most swing-heavy
     * thing in the kit — on a flat timer it was simply "available", and a lock that strong should be the
     * result of a fight going your way rather than of forty seconds elapsing.
     */
    @Override
    public ChargeSpec ultimateChargeSpec() {
        return ChargeSpec.builder("Rime")
                .accent(ICE_BLUE)
                .perMeleeHit(configDouble("rime-per-hit", 7.0))
                .perDamageDealt(configDouble("rime-per-damage-dealt", 0.4))
                .perAbilityCast(configDouble("rime-per-ability", 10.0))
                .perKill(configDouble("rime-per-kill", 12.0))
                .decay(configDouble("rime-decay-per-second", 2.0), configDouble("rime-decay-grace", 8.0))
                .cooldownFloor(configDouble("rime-cooldown-floor", 18.0))
                .build();
    }

    @Override
    public Map<CooldownManager.Slot, Double> damageProfile() {
        // ability2 (the powder-snow patch) and ability3 (the wall) deal no direct damage — they are terrain,
        // and pricing them as attacks would make the sheet read them as two dead slots.
        return Map.of(
                CooldownManager.Slot.ABILITY1, dashDamage,
                CooldownManager.Slot.ULTIMATE, prisonDamage);
    }

    @Override
    public Set<CounterVerb> counterVerbs() {
        return Set.of(CounterVerb.METER, CounterVerb.FOOTING);
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

    /**
     * Fills {@code block} only if it is currently empty air — for a wall, cage or patch that must never eat
     * standing terrain. The lifetime and the undo are {@link TempTerrain}'s.
     */
    private boolean fillAir(Player caster, Block block, Material material, int lifetimeTicks) {
        return block.getType().isAir()
                && plugin.tempTerrain().place(caster, block, material, lifetimeTicks);
    }

    /**
     * Swaps the surface of an existing solid ground block — for the dash trail, which has to convert the
     * floor rather than fill a gap above it.
     * <p>
     * Liquids are skipped because ice on water is a bridge, and a bridge that vanishes on a timer drops
     * whoever is standing on it. The tile-entity and protected-block refusals that used to be missing here
     * now come from {@link TempTerrain} rather than from a list this method maintains.
     */
    private boolean resurface(Player caster, Block block, Material material, int lifetimeTicks) {
        Material current = block.getType();
        if (current.isAir() || block.isLiquid() || current == material) {
            return false;
        }
        return plugin.tempTerrain().place(caster, block, material, lifetimeTicks);
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

        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= dashDurationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                // Each block carries its own lifetime now, so the trail melts from the back as the player
                // runs rather than all at once when the dash ends — which is also how it reads.
                Block underfoot = player.getLocation().clone().subtract(0, 1, 0).getBlock();
                resurface(player, underfoot, Material.PACKED_ICE, iceRevertTicks);
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
        BlockFace[] plus = {BlockFace.SELF, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : plus) {
            fillAir(player, feet.getBlock().getRelative(face), Material.POWDER_SNOW, freezeRevertTicks);
        }
        // The cold has to come from somewhere. Freezing a target also pulls the boss's stacks off the
        // caster, which is the whole reason this is the Frost Queen's answer rather than just her theme:
        // the ability the player was already pressing is the one that clears their Chill.
        Counterplay.relieveMeters(plugin, player, 0);
        Fx.sound(target.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 0.8f);
        Fx.coloredBurst(feet.clone().add(0, 0.3, 0), ICE_BLUE, 1.6f, 24, freezeRadius);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeSlowTicks, 2, false, true));
    }

    @Override
    public void ability3(Player player) {
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().setY(0).normalize();
        BlockFace face = Math.abs(direction.getX()) > Math.abs(direction.getZ())
                ? (direction.getX() > 0 ? BlockFace.EAST : BlockFace.WEST)
                : (direction.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
        BlockFace side = (face == BlockFace.NORTH || face == BlockFace.SOUTH) ? BlockFace.EAST : BlockFace.NORTH;

        Block base = origin.getBlock().getRelative(face, 2);
        int half = wallLength / 2;

        for (int length = -half; length <= half; length++) {
            for (int height = 0; height < wallHeight; height++) {
                Block block = base.getRelative(side, length).getRelative(0, height, 0);
                fillAir(player, block, Material.PACKED_ICE, wallDurationTicks);
            }
        }
        // Planting a wall means planting your feet: the same cast firms up the ground under the caster, so
        // an ice-floor phase has an answer that costs a cooldown rather than one that costs the fight.
        Counterplay.fixFooting(plugin, player, configDouble("footing-radius", 2.5),
                configInt("footing-ticks", 100));
        Fx.coloredBurst(base.getLocation().add(0.5, wallHeight * 0.5, 0.5), ICE_BLUE, 2.0f, 30, wallLength * 0.5);
        Fx.sound(player, castSound(), 1.0f, 0.9f);
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
        List<Block> cage = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    boolean shell = x == -1 || x == 1 || z == -1 || z == 1 || y == 0 || y == 2;
                    if (!shell) {
                        continue;
                    }
                    Block block = center.getBlock().getRelative(x, y, z);
                    // Lifetime set past the shatter so the cage cannot melt out from under its own payoff;
                    // the shatter below reverts it early, and TempTerrain reaping it later is a no-op.
                    if (fillAir(player, block, Material.PACKED_ICE, prisonDurationTicks + 20)) {
                        cage.add(block);
                    }
                }
            }
        }
        Fx.sound(victim.getLocation(), Sound.BLOCK_GLASS_PLACE, 1.2f, 0.6f);
        Fx.coloredBurst(center.clone().add(0, 1, 0), ICE_BLUE, 2.4f, 40, 1.4);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, prisonDurationTicks, 6, false, true));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // The cage comes down at the shatter, not on its own timer: reverting through the ledger keeps
            // the undo log and the world in agreement, where breaking the blocks would leave entries behind
            // for positions that no longer hold ice.
            for (Block block : cage) {
                plugin.tempTerrain().revertNear(block.getLocation().add(0.5, 0.5, 0.5), 0.6);
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
