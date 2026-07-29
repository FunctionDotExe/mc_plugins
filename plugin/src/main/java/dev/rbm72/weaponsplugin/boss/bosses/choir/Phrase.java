package dev.rbm72.weaponsplugin.boss.bosses.choir;

import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * P3's call-and-response: the Choir sings three real note-block tones, repeating, and playing the same
 * three back on the arena's note blocks shatters its ward (batch-3 §4.3).
 * <p>
 * Deliberately <b>not a memory test</b>. The phrase repeats continuously for as long as it takes, so this
 * is a listening-and-execution challenge in the dark rather than a gotcha — the spec is explicit about
 * that, and it is what keeps a puzzle fair while the group is blind and being hunted.
 * <p>
 * Fixed at three notes at every group size (§4.4). A bigger group cannot brute-force it either: a wrong
 * note is punished at the block that played it and throws the attempt away, so spamming every block is
 * strictly worse than one clear caller.
 */
final class Phrase {

    private final ChoirFight fight;

    private final List<Integer> wanted = new ArrayList<>();
    private int progress;
    private int singCooldownTicks;
    private int singIndex;
    private int completed;
    private int wrongNotes;
    private boolean active;

    Phrase(ChoirFight fight) {
        this.fight = fight;
    }

    /** Picks a fresh phrase from the note blocks that actually exist, and starts singing it. */
    void begin() {
        wanted.clear();
        progress = 0;
        singIndex = 0;
        singCooldownTicks = 0;
        active = true;

        int available = Math.max(3, fight.instruments().noteCount());
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < available; i++) {
            pool.add(i);
        }
        Collections.shuffle(pool);
        wanted.addAll(pool.subList(0, Math.min(3, pool.size())));
        fight.instruments().listenFor(this);

        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("IT IS SINGING — play it back", NamedTextColor.LIGHT_PURPLE),
                    3000L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    /** Repeats the phrase forever, one note at a time, with a rest between repetitions. */
    void pulse(int intervalTicks) {
        if (!active || wanted.isEmpty()) {
            return;
        }
        singCooldownTicks -= intervalTicks;
        if (singCooldownTicks > 0) {
            return;
        }
        if (singIndex >= wanted.size()) {
            singIndex = 0;
            singCooldownTicks = fight.config().num("phrase-rest-ticks", 60);
            return;
        }
        fight.instruments().singFromBoss(wanted.get(singIndex));
        singIndex++;
        singCooldownTicks = fight.config().num("phrase-note-ticks", 16);
    }

    /** A player struck a note block. Right note advances the answer; wrong note costs them the attempt. */
    void played(int index, Player player) {
        if (!active || wanted.isEmpty()) {
            return;
        }
        if (wanted.get(progress) == index) {
            progress++;
            if (progress >= wanted.size()) {
                complete();
            }
            return;
        }
        wrongNotes++;
        progress = 0;
        Location at = fight.instruments().noteBlockAt(index);
        if (at != null) {
            Fx.burst(at, Particle.SMOKE, 20, 0.4);
            Fx.sound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.4f, 0.6f);
            for (Player nearby : dev.rbm72.weaponsplugin.boss.Arena.combatants(at,
                    fight.config().dbl("phrase-wrong-radius", 4.0))) {
                nearby.damage(fight.config().dbl("phrase-wrong-damage", 8.0), fight.instance().entity());
            }
        }
        if (player != null) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("WRONG NOTE", NamedTextColor.RED),
                    1600L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    private void complete() {
        completed++;
        active = false;
        progress = 0;
        fight.instruments().stopListening();
        Location at = fight.instance().entity().getLocation();
        Fx.coloredBurst(at.clone().add(0, 1.5, 0), ChoirFight.PALE_VIOLET, 2.6f, 60, 0.9);
        Fx.sound(at, Sound.BLOCK_BELL_RESONATE, 1.8f, 1.0f);
        for (Player player : fight.combatants()) {
            fight.plugin().actionBarHub().flash(player,
                    Component.text("THE WARD SHATTERS", NamedTextColor.GREEN),
                    2600L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }

    boolean active() {
        return active;
    }

    int completed() {
        return completed;
    }

    int progress() {
        return progress;
    }

    int length() {
        return Math.max(1, wanted.size());
    }

    int wrongNotes() {
        return wrongNotes;
    }

    void stop() {
        active = false;
        fight.instruments().stopListening();
    }
}
