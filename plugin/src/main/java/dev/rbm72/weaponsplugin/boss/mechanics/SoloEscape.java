package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import org.bukkit.entity.Player;

/**
 * The roster's one solo verb for every "an ally must come and free you" mechanic: <b>hold sneak to
 * struggle</b>.
 * <p>
 * Batch-1 rule 0.2 #7 asks that every two-player mechanic have an explicitly <em>designed</em> solo
 * substitute — not a disabled mechanic, and by the same argument not a free pass. Two of the captive
 * mechanics had drifted to opposite failure modes: the Frozen Heart gave a lone player no escape at all
 * (a guaranteed fail on every cast, which is a disabled mechanic wearing a timer), and the hostage gate
 * counted a solo captive as their own rescuer with no input required at all (a mechanic that costs a lone
 * player nothing, and so teaches them nothing before they bring a group into the same fight).
 * <p>
 * One verb across all of them rather than a bespoke escape per mechanic, for the same reason the telegraph
 * cue is one sound for the whole roster: a solo player learns "held means struggle" once. Sneak is the
 * right input because it is already free during a captive state (the holds crush movement and mining, not
 * the sneak key), it is unmistakably deliberate, and it visibly costs the struggling player their ability
 * to reposition — which is what makes escaping alone slower and more dangerous than being freed, without
 * ever making it impossible.
 */
public final class SoloEscape {

    private SoloEscape() {
    }

    /** True when {@code captive} is the only combatant left in this fight, so no ally could ever reach them. */
    public static boolean alone(BossInstance instance, Player captive) {
        return Arena.combatants(instance.entity().getLocation(), instance.arena().radius())
                .stream().noneMatch(player -> !player.equals(captive));
    }

    /** True when a lone captive is actively struggling — the solo stand-in for a rescuer standing on them. */
    public static boolean struggling(Player captive) {
        return captive.isSneaking();
    }

    /**
     * The line to show a lone captive so the escape is discoverable in the moment. A solo substitute
     * nobody is told about is the same as not having one — the first thing a held player does is look for
     * instructions, and this is the only place they will look.
     */
    public static net.kyori.adventure.text.Component prompt() {
        return net.kyori.adventure.text.Component.text("Nobody can reach you — hold SNEAK to struggle free",
                net.kyori.adventure.text.format.NamedTextColor.AQUA);
    }
}
