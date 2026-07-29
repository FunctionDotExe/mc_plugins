package dev.rbm72.weaponsplugin.boss.bosses.choir;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * <b>P4 — All Voices.</b> Every note block in the arena fires continuously, so misdirection stops
 * working — everything is loud — and the Choir reverts to hunting the <em>nearest</em> player. The tools
 * are gone and the group simply fights it in the open dark (batch-3 §4.3): a deliberate, brutal
 * simplification, and the payoff for a complicated fight is a clean finish.
 * <p>
 * The target override is replaced here rather than removed, because "nearest body" is still a rule the
 * blind thing follows — it has just stopped being able to tell one sound from another.
 * <p>
 * Objective is satisfied the instant the phase starts, like every roster boss's final phase.
 */
final class AllVoicesPhase extends ChoirPhaseMechanic {

    private int singCooldownTicks;
    private int wailCooldownTicks;
    private int fangCooldownTicks;

    AllVoicesPhase(BossInstance instance) {
        super(instance, "All Voices", 0.0);
    }

    @Override
    protected void onArm() {
        fight.phrase().stop();
        fight.attacks().dismissIllusions();
        instance.setTargetOverride(this::nearest);
        instance.showTitle(
                Component.text("♫ ALL VOICES ♫", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text("Everything is loud now — it hunts the nearest of you", NamedTextColor.GRAY));
    }

    private Player nearest() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : fight.combatants()) {
            double distance = player.getLocation().distance(instance.entity().getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    @Override
    protected void onPulse(int intervalTicks) {
        singCooldownTicks -= intervalTicks;
        wailCooldownTicks -= intervalTicks;
        fangCooldownTicks -= intervalTicks;
        if (singCooldownTicks <= 0) {
            singCooldownTicks = fight.config().num("all-voices-interval-ticks", 60);
            fight.attacks().allVoices();
        }
        if (wailCooldownTicks <= 0) {
            wailCooldownTicks = fight.config().num("wail-interval-ticks", 180);
            fight.attacks().wail();
        }
        if (fangCooldownTicks <= 0) {
            fangCooldownTicks = fight.config().num("fang-interval-ticks", 120);
            fight.attacks().fangLine();
        }
    }

    @Override
    protected boolean objectiveMet() {
        return true;
    }

    @Override
    protected boolean announcesCompletion() {
        return false;
    }

    @Override
    protected Component readoutText() {
        return Component.text("no more misdirection — stand off it and finish it", NamedTextColor.RED);
    }

    @Override
    protected double readoutProgress() {
        return 1.0 - healthFraction();
    }
}
