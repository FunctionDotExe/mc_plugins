package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * His court: real armoured skeleton knights, and the chains they throw at whoever he is duelling.
 * <p>
 * The knights are not a damage tax — they are a <em>timer</em>. Each one that survives long enough
 * throws a Chain at the current Challenger, and a chained Challenger cannot step out of a cleave line or
 * strafe a thrust. So the non-duelling half of the group has a job with a deadline attached to it, and
 * ignoring the knights does not chip away at anyone's health bar, it kills the person in front.
 * <p>
 * The chain itself is <b>real chain blocks in the world</b> (§0.1), not a status effect wearing a chain
 * icon: they are placed around the rooted player's feet, they are audible and visible from across the
 * arena, and <em>any</em> player with any tool can break them to cut somebody loose. Breaking is polled
 * rather than listened for, deliberately — a block can leave for reasons no listener sees (another boss
 * mechanic, an explosion, a player-placed piston), and reading the world back is the only check that
 * cannot drift out of sync with it.
 */
final class Court {

    /** The block the chains are made of — a real, breakable, unmistakably-audible tether. */
    private static final Material CHAIN = Material.IRON_CHAIN;

    private final KingFight fight;
    private final Duel duel;

    private final List<UUID> knights = new ArrayList<>();
    /** Ticks each live knight still owes before it throws a chain. Keyed by the knight's entity id. */
    private final Map<UUID, Integer> throwCountdown = new HashMap<>();
    private final Map<UUID, Tether> tethers = new HashMap<>();

    private int reinforceCountdown;
    private int chainsBroken;

    Court(KingFight fight, Duel duel) {
        this.fight = fight;
        this.duel = duel;
    }

    /** One rooted player: the blocks holding them and how long the root may last unaided. */
    private static final class Tether {

        private final List<Block> blocks;
        private int ticksLeft;

        private Tether(List<Block> blocks, int ticksLeft) {
            this.blocks = blocks;
            this.ticksLeft = ticksLeft;
        }
    }

    // ---------------------------------------------------------------- readout

    int aliveCount() {
        return knights.size();
    }

    /** Chains players have cut this fight. A monotonic count of real headway, for the floor-lock valve. */
    int chainsBroken() {
        return chainsBroken;
    }

    boolean isChained(Player player) {
        return player != null && tethers.containsKey(player.getUniqueId());
    }

    // ---------------------------------------------------------------- pulse

    /**
     * @param wanted how many knights should be walking the floor right now. Zero retires the court
     *               without cutting anybody loose — the chains already thrown are still the group's
     *               problem, which is the correct read: the knight is gone, the chain is not.
     */
    void pulse(int intervalTicks, int wanted) {
        reap();
        tickTethers(intervalTicks);
        tickThrows(intervalTicks);
        if (knights.size() >= wanted) {
            return;
        }
        reinforceCountdown -= intervalTicks;
        if (reinforceCountdown <= 0) {
            reinforceCountdown = Math.max(40, fight.config().num("knight-arrival-ticks", 90));
            summon();
        }
    }

    private void reap() {
        for (Iterator<UUID> it = knights.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            Entity entity = fight.plugin().getServer().getEntity(id);
            if (entity == null || !entity.isValid()) {
                it.remove();
                throwCountdown.remove(id);
            }
        }
    }

    /**
     * A knight that has been alive too long throws its chain. The countdown is per knight and starts
     * when it arrives, so killing them promptly is the whole counterplay — and a knight killed at
     * fourteen seconds costs the group nothing at all.
     */
    private void tickThrows(int intervalTicks) {
        Player marked = duel.challenger();
        for (UUID id : List.copyOf(knights)) {
            int left = throwCountdown.getOrDefault(id, 0) - intervalTicks;
            if (left > 0) {
                throwCountdown.put(id, left);
                continue;
            }
            throwCountdown.put(id, Math.max(80, fight.config().num("knight-chain-interval-ticks", 300)));
            Entity knight = fight.plugin().getServer().getEntity(id);
            if (knight == null || !knight.isValid() || marked == null || isChained(marked)) {
                continue;
            }
            throwChain(knight.getLocation(), marked);
        }
    }

    private void tickTethers(int intervalTicks) {
        for (Iterator<Map.Entry<UUID, Tether>> it = tethers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Tether> entry = it.next();
            Player player = fight.plugin().getServer().getPlayer(entry.getKey());
            Tether tether = entry.getValue();
            if (player == null || !player.isOnline() || !Arena.isCombatant(player)) {
                clearBlocks(tether);
                it.remove();
                continue;
            }
            if (!anyBlockStanding(tether)) {
                // Somebody cut them loose. This is the only outcome that counts as progress: a chain
                // that merely timed out was the boss letting go, not the group solving anything.
                chainsBroken++;
                clearBlocks(tether);
                it.remove();
                releaseRoot(player);
                notice(player, Component.text("CUT LOOSE", NamedTextColor.GREEN));
                Fx.sound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.2f, 1.2f);
                continue;
            }
            tether.ticksLeft -= intervalTicks;
            if (tether.ticksLeft <= 0) {
                clearBlocks(tether);
                it.remove();
                releaseRoot(player);
                continue;
            }
            holdRoot(player, intervalTicks);
        }
    }

    // ---------------------------------------------------------------- knights

    /**
     * A knight walks in from the arena edge in real armour. Netherite for one in three so the court
     * reads as a court and not a wave of identical mobs — and so a group that has learned to focus the
     * heavy one is being rewarded for looking.
     */
    private void summon() {
        World world = fight.world();
        if (world == null) {
            return;
        }
        Location spot = edgeSpot();
        LivingEntity knight = fight.instance().addManager().spawn(world, spot, EntityType.SKELETON, entity -> {
            entity.customName(Component.text("Court Knight", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            entity.setCustomNameVisible(true);
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(fight.config().dbl("knight-health", 40.0));
                entity.setHealth(health.getValue());
            }
            EntityEquipment gear = entity.getEquipment();
            if (gear != null) {
                boolean heavy = ThreadLocalRandom.current().nextInt(3) == 0;
                gear.setItemInMainHand(new ItemStack(heavy ? Material.NETHERITE_SWORD : Material.IRON_SWORD));
                gear.setHelmet(new ItemStack(heavy ? Material.NETHERITE_HELMET : Material.IRON_HELMET));
                gear.setChestplate(new ItemStack(heavy ? Material.NETHERITE_CHESTPLATE : Material.IRON_CHESTPLATE));
                // Nothing a knight wears may drop: the court is a mechanic, not a netherite dispenser.
                gear.setItemInMainHandDropChance(0f);
                gear.setHelmetDropChance(0f);
                gear.setChestplateDropChance(0f);
            }
        });
        if (knight instanceof Mob mob) {
            Player marked = duel.challenger();
            mob.setTarget(marked != null ? marked : nearestCombatant(spot));
        }
        knights.add(knight.getUniqueId());
        throwCountdown.put(knight.getUniqueId(),
                Math.max(80, fight.config().num("knight-chain-delay-ticks", 300)));
        KingFight.royalFlourish(spot, Sound.ENTITY_SKELETON_AMBIENT, 0.6f);
    }

    private Location edgeSpot() {
        Location centre = fight.instance().arena().center();
        World world = fight.world();
        if (world == null) {
            return centre;
        }
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double distance = fight.instance().arena().radius() * fight.config().dbl("knight-arrival-fraction", 0.9);
        Location spot = centre.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);
        return spot;
    }

    private Player nearestCombatant(Location from) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : fight.combatants()) {
            double distance = player.getLocation().distanceSquared(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- chains

    /**
     * Places the tether. Chains rather than any solid block on purpose: a chain has no collision box, so
     * a rooted player is held by the root effect and not by being walled in — nobody suffocates, and a
     * rescuer can stand in the same square to break them.
     */
    private void throwChain(Location from, Player victim) {
        World world = victim.getWorld();
        Location feet = victim.getLocation();
        Fx.line(from.clone().add(0, 1.2, 0), feet.clone().add(0, 1.0, 0), Particle.CRIT, 18);
        Fx.sound(feet, Sound.BLOCK_CHAIN_PLACE, 1.4f, 0.6f);

        List<Block> placed = new ArrayList<>(4);
        for (int dy = 0; dy <= 1; dy++) {
            Block block = world.getBlockAt(feet.getBlockX(), feet.getBlockY() + dy, feet.getBlockZ());
            if (block.getType().isAir() && Grief.setMechanicBlock(fight.griefContext(), block, CHAIN)) {
                placed.add(block);
            }
        }
        if (placed.isEmpty()) {
            // Nowhere to anchor it (they are inside a block, or the ledger is out of budget). Refusing
            // to root someone we cannot show a physical tether for keeps the rule honest: if there is no
            // chain in the world, there is nothing for an ally to break, and the root would be arbitrary.
            return;
        }
        tethers.put(victim.getUniqueId(),
                new Tether(placed, Math.max(40, fight.config().num("chain-duration-ticks", 200))));
        notice(victim, Component.text("CHAINED — break the chains", NamedTextColor.RED));
        Fx.coloredBurst(feet.clone().add(0, 1.0, 0), KingFight.KING_SHADOW, 1.8f, 34, 0.6);
    }

    private boolean anyBlockStanding(Tether tether) {
        for (Block block : tether.blocks) {
            if (block.getType() == CHAIN) {
                return true;
            }
        }
        return false;
    }

    private void clearBlocks(Tether tether) {
        for (Block block : tether.blocks) {
            if (block.getType() == CHAIN) {
                Grief.setMechanicBlock(fight.griefContext(), block, Material.AIR);
            }
        }
        tether.blocks.clear();
    }

    /**
     * Holds the player with short, constantly re-asserted effects rather than one long application —
     * the same reasoning {@code MeterAfflictions} documents at length: a player who disconnects mid-root
     * cannot be reached by any cleanup, and a one-shot eight-second Slowness 250 written into their
     * profile means logging back in unable to move.
     */
    private void holdRoot(Player player, int intervalTicks) {
        int duration = intervalTicks + 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 250, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 128, false, false));
        Location feet = player.getLocation();
        Fx.coloredRing(feet, KingFight.KING_SHADOW, 1.2f, 1.0, 10, 0);
    }

    /**
     * Takes back only what {@link #holdRoot} put on, and only while it still looks like ours — the
     * amplifier plus a duration no longer than one re-assertion. Anything else on those types belongs to
     * the player (a potion, a beacon) and deleting it would be reaching well past this mechanic.
     */
    private void releaseRoot(Player player) {
        removeIfOurs(player, PotionEffectType.SLOWNESS, 250);
        removeIfOurs(player, PotionEffectType.JUMP_BOOST, 128);
    }

    private void removeIfOurs(Player player, PotionEffectType type, int amplifier) {
        PotionEffect active = player.getPotionEffect(type);
        if (active != null && active.getAmplifier() == amplifier
                && active.getDuration() >= 0 && active.getDuration() <= 40) {
            player.removePotionEffect(type);
        }
    }

    /** Fight teardown: chains come off the world and off the players holding them. */
    void discardAll() {
        for (Map.Entry<UUID, Tether> entry : tethers.entrySet()) {
            clearBlocks(entry.getValue());
            Player player = fight.plugin().getServer().getPlayer(entry.getKey());
            if (player != null) {
                releaseRoot(player);
            }
        }
        tethers.clear();
        for (UUID id : knights) {
            Entity knight = fight.plugin().getServer().getEntity(id);
            if (knight != null) {
                knight.remove();
            }
        }
        knights.clear();
        throwCountdown.clear();
    }

    private void notice(Player player, Component message) {
        fight.plugin().actionBarHub().flash(player, message, 2200L, ActionBarHub.PRIORITY_NOTICE);
    }
}
