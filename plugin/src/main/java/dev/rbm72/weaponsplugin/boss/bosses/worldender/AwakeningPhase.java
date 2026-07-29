package dev.rbm72.weaponsplugin.boss.bosses.worldender;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;

/**
 * <b>P1 — Awakening (100–88%) · removes distance as safety.</b> It wakes: Sonic Boom (already ignoring
 * blocks and cover by construction — see {@code WardenSonicBoomAttack}) is the one attack in the roster
 * nothing protects you from, and vibration hunting — the constant that threads every remaining phase
 * together — starts right here and never turns off. There is no tool to gain and no earlier boss's verb
 * to channel; this phase is entirely its own (batch-5 §3).
 */
final class AwakeningPhase extends WorldenderPhaseMechanic {

    private int heartbeatCooldown;

    AwakeningPhase(BossInstance instance) {
        super(instance, "Awakening");
    }

    @Override
    protected void onArm() {
        heartbeatCooldown = fight.config().num("awakening-heartbeat-interval-ticks", 100);
    }

    @Override
    protected void onPulse(int intervalTicks) {
        heartbeatCooldown -= intervalTicks;
        if (heartbeatCooldown > 0) {
            return;
        }
        heartbeatCooldown = fight.config().num("awakening-heartbeat-interval-ticks", 100);
        Fx.sound(instance.entity().getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.6f);
    }

    @Override
    protected Component readoutText() {
        return Component.text("it is listening for you", NamedTextColor.GRAY);
    }
}
