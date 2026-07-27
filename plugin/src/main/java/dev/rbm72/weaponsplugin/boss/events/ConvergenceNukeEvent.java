package dev.rbm72.weaponsplugin.boss.events;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.boss.MechanicBar;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Everything the boss has, at once, in a single expanding wave. There is no cover, no interrupt and
 * no shield — the only answer is to be far enough away when it lands, and the distance required is
 * far enough that the whole group has to abandon the boss to get there.
 * <p>
 * Its job in a fight is punctuation. A long phase full of ongoing, manageable demands needs one beat
 * where everything stops and the correct play is simply "leave, now, all of you", and then a
 * scramble back into position afterwards. That reset is what stops a boss's middle stretch turning
 * into a war of attrition where everyone stands still.
 * <p>
 * Short, total-attention, and the boss holds still for it — a sword combo competing for focus here
 * would just be noise.
 */
public final class ConvergenceNukeEvent extends ScriptedEvent {

    private static final Color NUKE_COLOR = Color.fromRGB(255, 140, 60);

    private final int windowTicks;
    private final double safeDistance;
    private final double centreDamage;
    private final double edgeDamage;
    private final double knockback;
    private final String titleText;
    private final String subtitleText;

    public ConvergenceNukeEvent(WeaponsPlugin plugin, String bossId, double[] triggers) {
        this(plugin, bossId, triggers, "WITHERING CONVERGENCE", "Get out — all of you, now");
    }

    public ConvergenceNukeEvent(WeaponsPlugin plugin, String bossId, double[] triggers,
                                 String titleText, String subtitleText) {
        super(plugin, bossId, triggers);
        this.windowTicks = configInt("convergence-window-ticks", 120);
        this.safeDistance = configDouble("convergence-safe-distance", 16.0);
        this.centreDamage = configDouble("convergence-centre-damage", 40.0);
        this.edgeDamage = configDouble("convergence-edge-damage", 14.0);
        this.knockback = configDouble("convergence-knockback", 1.1);
        this.titleText = titleText;
        this.subtitleText = subtitleText;
    }

    @Override
    public String id() {
        return "convergence_nuke";
    }

    @Override
    protected int durationTicks() {
        return windowTicks;
    }

    @Override
    protected boolean begin(BossInstance instance) {
        if (combatants(instance).isEmpty()) {
            return false;
        }
        instance.setForcedInvulnerable(true);
        instance.entity().setGlowing(true);
        Location at = instance.entity().getLocation();
        Fx.coloredBurst(at.clone().add(0, 1.4, 0), NUKE_COLOR, 2.8f, 70, 1.0);
        Fx.sound(at, Sound.ENTITY_WITHER_SPAWN, 1.6f, 0.6f);
        instance.showTitle(
                Component.text(titleText, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text(subtitleText, NamedTextColor.GRAY));
        return true;
    }

    @Override
    protected void tick(BossInstance instance, int ticks) {
        Location at = instance.entity().getLocation();
        double charge = ticks / (double) windowTicks;

        // The danger ring closes inward as it charges, so "how far is far enough" is always visible.
        Fx.coloredRing(at, NUKE_COLOR, 1.6f, safeDistance, 40, ticks * 0.05);
        if (ticks % 3 == 0) {
            Fx.coloredRing(at.clone().add(0, 1.0, 0), NUKE_COLOR, 1.2f,
                    safeDistance * (1.0 - charge) + 1.0, 26, -ticks * 0.12);
        }
        if (ticks % 20 == 0) {
            Fx.sound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1.4f, 0.5f + 1.2f * (float) charge);
        }
        if (ticks % 4 == 0) {
            showBars(instance, ticks, charge);
        }
    }

    private void showBars(BossInstance instance, int ticks, double charge) {
        Location at = instance.entity().getLocation();
        int secondsLeft = Math.max(0, (windowTicks - ticks) / 20);
        instance.mechanicBar().update(MechanicBar.Owner.EVENT, instance.barViewers(), viewer -> {
            double dist = flatDistance(viewer.getLocation(), at);
            boolean safe = dist >= safeDistance;
            Component text = Component.text(titleText + "  ", NamedTextColor.GOLD)
                    .append(safe
                            ? Component.text("far enough", NamedTextColor.GREEN)
                            : Component.text("RUN — " + (int) Math.ceil(safeDistance - dist) + " blocks short",
                                    NamedTextColor.RED))
                    .append(Component.text("   " + secondsLeft + "s", NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text, charge, safe ? BossBar.Color.GREEN : BossBar.Color.RED);
        });
    }

    @Override
    protected void expire(BossInstance instance) {
        Location at = instance.entity().getLocation();
        Fx.expandingRings(plugin, at, Particle.SOUL_FIRE_FLAME, safeDistance, 6, 2L);
        Fx.coloredBurst(at.clone().add(0, 1.4, 0), NUKE_COLOR, 3.2f, 110, 1.6);
        Fx.flash(at.clone().add(0, 1.4, 0), 4);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.4f);

        boolean anyoneClear = false;
        for (Player player : combatants(instance)) {
            double dist = flatDistance(player.getLocation(), at);
            if (dist >= safeDistance) {
                anyoneClear = true;
                continue;
            }
            // Full damage at the boss's feet, tapering to a survivable clip at the ring's edge.
            double closeness = 1.0 - Math.min(1.0, dist / safeDistance);
            hurt(instance, player, edgeDamage + (centreDamage - edgeDamage) * closeness);
            if (player.isValid() && !player.isDead() && knockback > 0) {
                Vector away = player.getLocation().toVector().subtract(at.toVector()).setY(0);
                if (away.lengthSquared() > 0.001) {
                    player.setVelocity(player.getVelocity().add(away.normalize().multiply(knockback).setY(0.35)));
                }
            }
        }
        if (anyoneClear) {
            instance.recordExposure();
        }
        instance.showTitle(
                anyoneClear
                        ? Component.text("OUT OF REACH", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true)
                        : Component.text("IT CAUGHT YOU ALL", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text(anyoneClear ? "Get back on it" : "That was avoidable", NamedTextColor.GRAY));
    }
}
