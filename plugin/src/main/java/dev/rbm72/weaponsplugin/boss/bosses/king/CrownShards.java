package dev.rbm72.weaponsplugin.boss.bosses.king;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The crown, in pieces, on the floor.
 * <p>
 * <b>Real dropped items</b> (§0.1) rather than an abstract "carry state": they land with a sound, they
 * glow, anyone can pick one up, and — the part that makes the phase work — anyone can <em>throw</em> one
 * to a teammate, because throwing an item is something Minecraft already does. Nothing here implements
 * hand-offs; hand-offs implement themselves, and this only has to notice where the shards ended up.
 * <p>
 * While any shard is loose the King <b>reflects</b> a share of what he takes back at whoever swung, which
 * is the phase's whole argument with the group's DPS reflex: the answer to a reflect is to stop hitting
 * and go do the objective, and the objective is on the floor behind you. Reflect is queued and paid out
 * on the next pulse rather than applied inside the damage event, so a hit can never recurse into another
 * hit mid-event.
 * <p>
 * Carrying costs: Slowness, a visible glow that tells the King exactly who to chase, and the shard taking
 * up a slot you would rather have a weapon in.
 */
final class CrownShards {

    private static final Material SHARD_ITEM = Material.GOLD_INGOT;
    /** What a seated shard becomes on the dais — the throne visibly re-forming, one third at a time. */
    private static final Material SEATED_BLOCK = Material.GOLD_BLOCK;

    private final KingFight fight;
    private final NamespacedKey shardKey;

    private final List<UUID> looseItems = new ArrayList<>();
    private final List<Block> seatedBlocks = new ArrayList<>();
    /** Damage owed back to each attacker, banked during the damage event and paid on the next pulse. */
    private final Map<UUID, Double> pendingReflect = new HashMap<>();

    private int total;
    private int seated;
    private boolean scattered;

    CrownShards(KingFight fight) {
        this.fight = fight;
        this.shardKey = new NamespacedKey(fight.plugin(), "fallen_king_crown_shard");
    }

    // ---------------------------------------------------------------- state

    int seated() {
        return seated;
    }

    int total() {
        return total;
    }

    boolean allSeated() {
        return scattered && seated >= total;
    }

    /** True while at least one shard is still off the throne — the condition the reflect runs on. */
    boolean anyLoose() {
        return scattered && seated < total;
    }

    // ---------------------------------------------------------------- arming

    /**
     * The crown physically comes apart. Shards land at evenly-spaced points around the arena rather than
     * at his feet: the phase is a relay, and a relay needs distance to be run over.
     */
    void scatter() {
        if (scattered) {
            return;
        }
        scattered = true;
        World world = fight.world();
        if (world == null) {
            return;
        }
        total = Math.max(1, fight.config().num("shard-count", 3));
        Location crown = fight.instance().entity().getLocation().add(0, 2.2, 0);
        Fx.coloredBurst(crown, KingFight.KING_GOLD, 2.6f, 70, 1.0);
        Fx.burst(crown, Particle.END_ROD, 50, 0.9);
        Fx.sound(crown, Sound.BLOCK_ANVIL_DESTROY, 1.4f, 1.2f);

        for (int i = 0; i < total; i++) {
            double angle = (Math.PI * 2 * i) / total;
            Location spot = surfaceSpot(angle, fight.config().dbl("shard-scatter-fraction", 0.7));
            Item item = world.dropItem(spot.clone().add(0, 1, 0), shardStack());
            item.setGlowing(true);
            item.setUnlimitedLifetime(true);
            item.setPersistent(false);
            item.customName(Component.text("Crown Shard", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            item.setCustomNameVisible(true);
            fight.instance().trackEntity(item);
            looseItems.add(item.getUniqueId());
            KingFight.royalFlourish(spot, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f);
        }
    }

    private ItemStack shardStack() {
        ItemStack stack = new ItemStack(SHARD_ITEM);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Crown Shard", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Seat it on the throne.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        // The tag, not the name, is what identifies a shard. A player can rename anything; only this
        // fight can write this key, so nobody carries a counterfeit crown to the dais.
        meta.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isShard(ItemStack stack) {
        if (stack == null || stack.getType() != SHARD_ITEM) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(shardKey, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------- pulse

    void pulse(int intervalTicks) {
        payReflect();
        if (!scattered) {
            return;
        }
        markLoose();
        for (Player player : fight.combatants()) {
            if (!carrying(player)) {
                continue;
            }
            burden(player, intervalTicks);
            if (atDais(player)) {
                seat(player);
            }
        }
    }

    /** Everyone currently holding a shard — the people the King should be chasing. */
    List<Player> carriers() {
        List<Player> found = new ArrayList<>();
        for (Player player : fight.combatants()) {
            if (carrying(player)) {
                found.add(player);
            }
        }
        return found;
    }

    private boolean carrying(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isShard(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cost of holding one: slowed, and lit up for the whole arena to see. The glow is not decoration
     * — it is how the rest of the group knows who to peel for, and how the King knows who to run down.
     */
    private void burden(Player player, int intervalTicks) {
        int amplifier = Math.max(0, fight.config().num("shard-slowness-amplifier", 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, intervalTicks + 20, amplifier, false, false));
        Fx.coloredBurst(player.getLocation().add(0, 1.4, 0), KingFight.KING_GOLD, 1.0f, 4, 0.3);
        Fx.line(player.getLocation().add(0, 1.0, 0), fight.dais().add(0.5, 1.0, 0.5), Particle.WAX_OFF, 10);
    }

    private boolean atDais(Player player) {
        Location dais = fight.dais();
        Location at = player.getLocation();
        if (at.getWorld() == null || dais.getWorld() == null || !at.getWorld().equals(dais.getWorld())) {
            return false;
        }
        double reach = fight.config().dbl("shard-seat-radius", 3.0);
        double dx = at.getX() - dais.getX();
        double dz = at.getZ() - dais.getZ();
        return dx * dx + dz * dz <= reach * reach;
    }

    /** Takes exactly one shard off the carrier and puts it on the throne. */
    private void seat(Player player) {
        if (!removeOne(player)) {
            return;
        }
        seated++;
        Location dais = fight.dais();
        World world = dais.getWorld();
        if (world != null) {
            // The throne visibly re-forms, one block per shard, so progress is readable from across the
            // arena without anyone having to look at a bar.
            Block block = world.getBlockAt(dais.getBlockX() + seated - 1, dais.getBlockY(), dais.getBlockZ());
            if (Grief.setMechanicBlock(fight.griefContext(), block, SEATED_BLOCK)) {
                seatedBlocks.add(block);
            }
        }
        KingFight.royalFlourish(dais, Sound.BLOCK_BEACON_ACTIVATE, 1.2f);
        Fx.expandingRings(fight.plugin(), dais, Particle.END_ROD, 4.0, 3, 2L);
        notice(player, Component.text("SHARD SEATED  " + seated + "/" + total, NamedTextColor.GOLD));
    }

    private boolean removeOne(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (!isShard(stack)) {
                continue;
            }
            if (stack.getAmount() > 1) {
                stack.setAmount(stack.getAmount() - 1);
            } else {
                player.getInventory().setItem(slot, null);
            }
            return true;
        }
        return false;
    }

    /** Keeps a beacon of particles over every shard still lying on the floor, so none is ever lost. */
    private void markLoose() {
        looseItems.removeIf(id -> {
            Entity entity = fight.plugin().getServer().getEntity(id);
            if (entity == null || !entity.isValid()) {
                return true;
            }
            Fx.coloredBurst(entity.getLocation().add(0, 0.8, 0), KingFight.KING_GOLD, 1.2f, 5, 0.2);
            return false;
        });
    }

    // ---------------------------------------------------------------- reflect

    /**
     * Banks the reflect owed for one resolved hit. Called from inside the damage event, so it does
     * nothing but arithmetic — see {@link #payReflect()} for why the hit itself waits.
     */
    void onBossDamaged(Player attacker, double dealt) {
        if (!anyLoose() || attacker == null || dealt <= 0) {
            return;
        }
        double share = fight.config().dbl("shard-reflect-fraction",
                fight.playerCount() <= 1 ? 0.18 : 0.35);
        pendingReflect.merge(attacker.getUniqueId(), dealt * share, Double::sum);
    }

    /**
     * Pays out everything banked since the last pulse, as one hit per attacker.
     * <p>
     * Deliberately not applied inline. Damaging a player from inside the boss's own
     * {@code EntityDamageEvent} handler fires a second damage event while the first is still resolving,
     * which every mitigation listener in the plugin then sees out of order — and one of those listeners
     * (a thorns-style accessory) could reflect it straight back. Paying on the pulse keeps the whole
     * thing to one event per player per quarter-second, and lets the burst read as a single clear
     * consequence rather than a stutter of tiny hits.
     */
    private void payReflect() {
        if (pendingReflect.isEmpty()) {
            return;
        }
        Map<UUID, Double> owed = Map.copyOf(pendingReflect);
        pendingReflect.clear();
        for (Map.Entry<UUID, Double> entry : owed.entrySet()) {
            Player player = fight.plugin().getServer().getPlayer(entry.getKey());
            if (player == null || !player.isValid() || player.isDead() || entry.getValue() <= 0.5) {
                continue;
            }
            player.damage(entry.getValue(), fight.instance().entity());
            Fx.coloredBurst(player.getLocation().add(0, 1.2, 0), KingFight.KING_SHADOW, 1.6f, 24, 0.5);
            Fx.sound(player.getLocation(), Sound.ENCHANT_THORNS_HIT, 1.0f, 0.8f);
            notice(player, Component.text("REFLECTED — seat the shards", NamedTextColor.DARK_PURPLE));
        }
    }

    // ---------------------------------------------------------------- teardown

    /**
     * Fight end: loose shards leave the world and carried ones leave inventories. A boss must not hand
     * out a gold ingot as a souvenir of a mechanic, and a tagged item surviving the fight would be a
     * counterfeit shard the next fight would happily accept at its own dais.
     */
    void discardAll() {
        for (UUID id : looseItems) {
            Entity entity = fight.plugin().getServer().getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        looseItems.clear();
        for (Player player : fight.combatants()) {
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (isShard(contents[slot])) {
                    player.getInventory().setItem(slot, null);
                }
            }
        }
        // The seated blocks are in the arena ledger and are restored with everything else; clearing the
        // list only stops this object pointing at blocks that are about to be something else.
        seatedBlocks.clear();
        pendingReflect.clear();
    }

    // ---------------------------------------------------------------- helpers

    private Location surfaceSpot(double angleRadians, double fraction) {
        Location centre = fight.instance().arena().center();
        World world = fight.world();
        if (world == null) {
            return centre;
        }
        double distance = fight.instance().arena().radius() * Math.max(0.0, Math.min(1.0, fraction));
        Location spot = centre.clone().add(Math.cos(angleRadians) * distance, 0, Math.sin(angleRadians) * distance);
        spot.setY(world.getHighestBlockYAt(spot.getBlockX(), spot.getBlockZ()) + 1);
        return spot;
    }

    private void notice(Player player, Component message) {
        fight.plugin().actionBarHub().flash(player, message, 2200L, ActionBarHub.PRIORITY_NOTICE);
    }
}
