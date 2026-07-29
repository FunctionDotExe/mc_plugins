package dev.rbm72.weaponsplugin.boss.bosses.sovereign;

import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * The other half of {@code MeterThresholds#banish}: that method deliberately holds and bleeds the
 * player without teleporting them anywhere or placing a rescue prop, on the documented grounds that a
 * "real pocket sub-space" is boss-scale work a shared framework method has no business half-implementing.
 * This is that boss-scale half — a real, hittable tether anchored on the banished player themselves.
 * <p>
 * Batch-1 §5.4/§5.6 is explicit and non-negotiable here: "allies destroy the tether crystal that
 * appears... solo: eat chorus fruit or break out by destroying the pocket wall" and "tether is always
 * breakable solo — never a hard 2-player requirement". A real {@link ArenaTotem} answers both halves at
 * once: it is meleeable by anyone standing near the banished player, including the banished player
 * themselves (blinded, not incapacitated — they can still swing at point-blank range), so there is no
 * scenario where the rescue depends on a second person being present.
 */
final class BanishPocket {

    private static final Material TETHER_ITEM = Material.END_CRYSTAL;

    private final SovereignFight fight;

    BanishPocket(SovereignFight fight) {
        this.fight = fight;
    }

    /** Called from the Void Echo meter's threshold the instant a player is banished. */
    void bind(Player victim) {
        if (fight.world() == null) {
            return;
        }
        Location at = victim.getLocation();
        double hp = fight.config().dbl("banish-tether-health", 14.0);
        int lifetimeTicks = fight.config().num("banish-hold-ticks", 140) + 20;

        Fx.coloredBurst(at.clone().add(0, 1.2, 0), SovereignFight.VOID_PURPLE, 2.0f, 40, 0.6);
        Fx.sound(at, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.5f);

        ArenaTotem.spawn(fight.plugin(), fight.instance(), at.clone().add(0, 1.0, 0), TETHER_ITEM,
                Component.text("Tether Crystal", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false),
                hp, lifetimeTicks,
                destroyed -> release(victim),
                expired -> { /* the hold's own natural expiry already releases them */ },
                false);
    }

    private void release(Player victim) {
        fight.voidEcho().afflictions().release(victim);
        if (victim.isOnline()) {
            Location at = victim.getLocation();
            Fx.coloredBurst(at.clone().add(0, 1.0, 0), SovereignFight.VOID_PURPLE, 2.2f, 50, 0.8);
            Fx.burst(at.clone().add(0, 1.0, 0), Particle.REVERSE_PORTAL, 40, 0.7);
            Fx.sound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.3f, 1.1f);
            fight.plugin().actionBarHub().flash(victim,
                    Component.text("TETHER BROKEN — you're free", NamedTextColor.LIGHT_PURPLE),
                    2200L, dev.rbm72.weaponsplugin.ui.ActionBarHub.PRIORITY_NOTICE);
        }
    }
}
