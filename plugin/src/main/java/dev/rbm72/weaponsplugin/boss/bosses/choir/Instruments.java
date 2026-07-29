package dev.rbm72.weaponsplugin.boss.bosses.choir;

import dev.rbm72.weaponsplugin.boss.grief.Grief;
import dev.rbm72.weaponsplugin.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The arena's ears and its instruments: a ring of real sculk sensors that visibly pulse at whatever was
 * last heard, and a fixed set of real note blocks and bells any player can strike to make noise
 * somewhere they are not (batch-3 §4.2).
 * <p>
 * Their count is fixed at every group size (§4.4) — a shared tool, never a scaling knob — and both the
 * retune interaction and breaking are refused, so the pitches stay stable across the fight. That matters
 * for more than tidiness: P3's phrase is played back <em>on these blocks</em>, and a group that had
 * retuned half of them would be solving a different puzzle than the one the Choir sang.
 */
final class Instruments {

    /** One note block's fixed pitch, as a playback multiplier — a simple ascending scale across the ring. */
    private static final float[] PITCHES = {0.6f, 0.75f, 0.9f, 1.06f, 1.26f, 1.5f, 1.68f, 2.0f};

    private final ChoirFight fight;
    private final List<Block> noteBlocks = new ArrayList<>();
    private final List<Block> bells = new ArrayList<>();
    private final List<Block> sensors = new ArrayList<>();

    private Handler handler;
    private Phrase phrase;

    Instruments(ChoirFight fight) {
        this.fight = fight;
    }

    void build() {
        if (!noteBlocks.isEmpty()) {
            return;
        }
        World world = fight.world();
        if (world == null) {
            return;
        }
        int count = Math.min(PITCHES.length, Math.max(3, fight.config().num("note-block-count", 6)));
        double fraction = fight.config().dbl("instrument-placement-fraction", 0.8);

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            Location spot = surfaceSpot(angle, fraction);
            Block base = world.getBlockAt(spot.getBlockX(), spot.getBlockY() - 1, spot.getBlockZ());
            Grief.setMechanicBlock(fight.griefContext(), base, Material.DARK_OAK_PLANKS);
            Block block = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            if (Grief.setMechanicBlock(fight.griefContext(), block, Material.NOTE_BLOCK)) {
                noteBlocks.add(block);
                Fx.coloredBurst(spot.clone().add(0, 1, 0), ChoirFight.PALE_VIOLET, 1.4f, 18, 0.4);
            }
            // A sensor beside every instrument: the thing that hears is visibly next to the thing that
            // makes noise, so the causal chain reads without a word of explanation.
            Block sensor = world.getBlockAt(spot.getBlockX() + 1, spot.getBlockY(), spot.getBlockZ());
            if (Grief.setMechanicBlock(fight.griefContext(), sensor, Material.SCULK_SENSOR)) {
                sensors.add(sensor);
            }
        }

        int bellCount = Math.max(1, fight.config().num("bell-count", 2));
        for (int i = 0; i < bellCount; i++) {
            double angle = Math.PI * 2 * i / bellCount + Math.PI / bellCount;
            Location spot = surfaceSpot(angle, fraction * 0.7);
            Block block = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            if (Grief.setMechanicBlock(fight.griefContext(), block, Material.BELL)) {
                bells.add(block);
                Fx.sound(spot, Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
            }
        }

        if (handler == null) {
            handler = new Handler();
            fight.plugin().getServer().getPluginManager().registerEvents(handler, fight.plugin());
        }
    }

    /** P3 hooks its call-and-response in here, because the note blocks are the puzzle's input device. */
    void listenFor(Phrase phrase) {
        this.phrase = phrase;
    }

    void stopListening() {
        this.phrase = null;
    }

    /** Sensors visibly pulse at whatever was last heard — the fight's targeting, drawn on the floor. */
    void pulse() {
        Location heard = fight.noise().lastHeardAt();
        if (heard == null) {
            return;
        }
        for (Block sensor : sensors) {
            Fx.line(sensor.getLocation().add(0.5, 0.6, 0.5), heard.clone().add(0, 0.6, 0),
                    Particle.SCULK_SOUL, 8);
        }
    }

    int noteCount() {
        return noteBlocks.size();
    }

    /** Plays note {@code index} exactly as a player striking that block would — the Choir's own singing voice. */
    void sing(int index) {
        if (index < 0 || index >= noteBlocks.size()) {
            return;
        }
        Block block = noteBlocks.get(index);
        Location at = block.getLocation().add(0.5, 1, 0.5);
        Fx.sound(at, Sound.BLOCK_NOTE_BLOCK_HARP, 2.0f, PITCHES[index]);
        Fx.burst(at, Particle.NOTE, 6, 0.4);
    }

    /** The Choir singing a note from its own body rather than from the ring — the phrase it wants back. */
    void singFromBoss(int index) {
        if (index < 0 || index >= PITCHES.length) {
            return;
        }
        Location at = fight.instance().entity().getLocation().add(0, 1.6, 0);
        Fx.sound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 2.0f, PITCHES[index]);
        Fx.burst(at, Particle.NOTE, 8, 0.5);
    }

    Location noteBlockAt(int index) {
        if (index < 0 || index >= noteBlocks.size()) {
            return null;
        }
        return noteBlocks.get(index).getLocation().add(0.5, 1, 0.5);
    }

    void discardAll() {
        if (handler != null) {
            HandlerList.unregisterAll(handler);
            handler = null;
        }
        noteBlocks.clear();
        bells.clear();
        sensors.clear();
        phrase = null;
    }

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

    private final class Handler implements Listener {

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onInteract(PlayerInteractEvent event) {
            Block block = event.getClickedBlock();
            if (block == null) {
                return;
            }
            int index = noteBlocks.indexOf(block);
            if (index >= 0) {
                // Right-click retunes a note block in vanilla, which would quietly rewrite the puzzle.
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
                    return;
                }
                sing(index);
                fight.noise().register(block.getLocation().add(0.5, 1, 0.5), event.getPlayer(),
                        fight.config().dbl("noise-note-block", 2.0));
                if (phrase != null) {
                    phrase.played(index, event.getPlayer());
                }
                return;
            }
            if (bells.contains(block) && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                fight.noise().register(block.getLocation().add(0.5, 1, 0.5), event.getPlayer(),
                        fight.config().dbl("noise-bell", 2.5));
            }
        }

        /** The instruments are the fight's shared tools; a group cannot mine the puzzle out of the arena. */
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            Block block = event.getBlock();
            if (noteBlocks.contains(block) || bells.contains(block) || sensors.contains(block)) {
                event.setCancelled(true);
            }
        }
    }
}
