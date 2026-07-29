package dev.rbm72.weaponsplugin.boss;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;

import java.util.Map;
import java.util.TreeMap;

/**
 * Every boss sound goes through a namespaced key rather than a direct {@link Sound} reference, so the
 * resource pack can define a real custom sound under that key without touching any boss or attack code.
 * <p>
 * That indirection existed from the start but was never connected to anything: the map it consulted was
 * always empty, so all 100-odd call sites quietly played their vanilla fallback and every mechanic in the
 * roster telegraphed visually only. Two things changed here.
 * <ul>
 *   <li>A key now resolves to {@code weaponsplugin:<key>} in the pack when
 *       {@code boss-audio.custom-sounds} is on, falling back to the vanilla {@link Sound} when it is off.
 *       Custom sounds are played by name, which Bukkit passes to the client verbatim — a client without
 *       the pack simply hears nothing for that key, which is why the toggle exists and why it should be
 *       turned off on a server that does not push the pack.</li>
 *   <li>Every key that plays is recorded with the vanilla sound it falls back to, and
 *       {@link #soundsJson()} emits a complete pack {@code sounds.json} from that. Each generated entry
 *       points at the fallback's own vanilla sound <em>event</em>, so a freshly generated pack sounds
 *       exactly like no pack at all — the point is to get every key declared and playable, after which
 *       individual entries can be replaced with real audio one at a time instead of all at once.
 *       Pair it with {@code /bosstest all}, which fires every attack in the roster and therefore touches
 *       every key, then {@code /bossaudio dump}.</li>
 * </ul>
 */
public final class BossAudio {

    /** Pack namespace custom keys resolve into. */
    private static final String NAMESPACE = "weaponsplugin";

    /**
     * Every key seen this session, mapped to the vanilla sound it falls back to. Sorted so a generated
     * {@code sounds.json} has a stable diff between runs.
     */
    private static final Map<String, Sound> SEEN = new TreeMap<>();

    private static WeaponsPlugin plugin;

    private BossAudio() {
    }

    /** Wired from {@code WeaponsPlugin#onEnable}, before any boss can spawn. */
    public static void init(WeaponsPlugin instance) {
        plugin = instance;
    }

    public static void play(Location loc, String key, Sound fallback, float volume, float pitch) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        SEEN.put(key, fallback);
        if (customEnabled()) {
            // Played by name: the client resolves it against its loaded packs. No server-side check is
            // possible, hence the config toggle rather than a silent guess.
            world.playSound(loc, NAMESPACE + ":" + key, SoundCategory.HOSTILE, volume, pitch);
            return;
        }
        world.playSound(loc, fallback, volume, pitch);
    }

    private static boolean customEnabled() {
        // Default off: a server that has not pushed the pack would otherwise go completely silent in
        // every boss fight, which is a far worse failure than not yet having custom audio.
        return plugin != null && plugin.getConfig().getBoolean("boss-audio.custom-sounds", false);
    }

    /** Keys seen this session, with their vanilla fallbacks — backs {@code /bossaudio list}. */
    public static Map<String, Sound> seen() {
        return Map.copyOf(SEEN);
    }

    /**
     * A pack {@code sounds.json} declaring every key seen so far, each delegating to its fallback's
     * vanilla sound event.
     * <p>
     * {@code "type": "event"} rather than a file path on purpose: it reuses the vanilla event including
     * all of its random variants and its subtitle, so a generated pack is audibly identical to none.
     * Replacing one entry with {@code {"name": "weaponsplugin:boss/whatever", "type": "file"}} and an
     * {@code .ogg} is then a one-line change per sound.
     */
    // Sound#key() is deprecated-for-removal in favour of a registry lookup, but it is the only route to a
    // sound's namespaced id that exists across the API versions this plugin is built against, and this is
    // a one-off generator run by an admin — not fight code.
    @SuppressWarnings("removal")
    public static String soundsJson() {
        StringBuilder json = new StringBuilder("{\n");
        boolean first = true;
        for (Map.Entry<String, Sound> entry : SEEN.entrySet()) {
            if (!first) {
                json.append(",\n");
            }
            first = false;
            json.append("  \"").append(entry.getKey()).append("\": {\n")
                    .append("    \"category\": \"hostile\",\n")
                    .append("    \"sounds\": [\n")
                    .append("      { \"name\": \"").append(entry.getValue().key().asString()).append("\", \"type\": \"event\" }\n")
                    .append("    ]\n")
                    .append("  }");
        }
        return json.append("\n}\n").toString();
    }
}
