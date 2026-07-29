package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;

/**
 * <b>P2 — The Freezing (88–76%) · removes reliable footing.</b> The arena flash-freezes the instant this
 * phase arms; sneaking (P1's survival tool) becomes slower and harder to control on ice, so the two
 * mechanics immediately interfere — that interference is the phase. Leather boots drop and matter again
 * in P6's powder snow.
 */
final class TheFreezingPhase extends WorldenderPhaseMechanic {

    TheFreezingPhase(BossInstance instance) {
        super(instance, "The Freezing");
    }

    @Override
    protected void onArm() {
        WorldenderSupplies.leatherBoots(fight);
        fight.ice().freeze();
        Fx.sound(instance.arena().center(), Sound.BLOCK_GLASS_BREAK, 1.3f, 0.5f);
        Fx.sound(instance.arena().center(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.7f);
    }

    @Override
    protected Component readoutText() {
        return Component.text("the floor is ice — mind your footing", NamedTextColor.AQUA);
    }
}
