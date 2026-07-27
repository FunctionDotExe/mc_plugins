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
 * The boss hauls the whole group into its lap and then detonates at point-blank range. The pull is
 * strong but not absolute: a player who commits to running can drag themselves back out before it
 * goes off, and a player who keeps swinging cannot.
 * <p>
 * That is the entire decision, and it is a good one because it is genuinely close. The pull window
 * is long enough that the damage lost by disengaging is real, and the blast is severe enough that
 * eating it is real too — so different players in the same group will correctly make different
 * calls depending on how much health they have. Nothing about it is a lockout: the boss keeps
 * fighting, everyone keeps their abilities, and the only question is which way you are facing.
 * <p>
 * Failure is loud and self-explanatory: you were in the circle, the circle exploded.
 */
public final class PullNukeEvent extends ScriptedEvent {

    private static final Color CRUCIBLE_COLOR = Color.fromRGB(255, 110, 40);

    private final int windowTicks;
    private final double pullPerTick;
    private final double blastRadius;
    private final double blastDamage;
    private final int burnTicks;
    private final String titleText;
    private final String subtitleText;

    public PullNukeEvent(WeaponsPlugin plugin, String bossId, double[] triggers) {
        this(plugin, bossId, triggers, "THE CRUCIBLE", "It is pulling you in — break away");
    }

    public PullNukeEvent(WeaponsPlugin plugin, String bossId, double[] triggers,
                          String titleText, String subtitleText) {
        super(plugin, bossId, triggers);
        this.windowTicks = configInt("crucible-window-ticks", 110);
        this.pullPerTick = configDouble("crucible-pull-per-tick", 0.075);
        this.blastRadius = configDouble("crucible-blast-radius", 8.0);
        this.blastDamage = configDouble("crucible-blast-damage", 34.0);
        this.burnTicks = configInt("crucible-burn-ticks", 60);
        this.titleText = titleText;
        this.subtitleText = subtitleText;
    }

    @Override
    public String id() {
        return "pull_nuke";
    }

    /** Ten seconds of struggling against a pull is only tense while the boss is still a threat. */
    @Override
    public boolean blocksCombat() {
        return false;
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
        Location at = instance.entity().getLocation();
        Fx.coloredBurst(at.clone().add(0, 1.4, 0), CRUCIBLE_COLOR, 2.6f, 60, 0.9);
        Fx.sound(at, Sound.ENTITY_RAVAGER_ROAR, 1.6f, 0.6f);
        instance.showTitle(
                Component.text(titleText, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text(subtitleText, NamedTextColor.GRAY));
        return true;
    }

    @Override
    protected void tick(BossInstance instance, int ticks) {
        Location at = instance.entity().getLocation();
        double charge = ticks / (double) windowTicks;

        Fx.coloredRing(at, CRUCIBLE_COLOR, 1.7f, blastRadius, 30, ticks * 0.08);
        if (ticks % 3 == 0) {
            Fx.coloredRing(at.clone().add(0, 0.8, 0), CRUCIBLE_COLOR, 1.2f,
                    blastRadius * (1.4 - charge * 0.6), 22, -ticks * 0.15);
        }
        if (ticks % 20 == 0) {
            Fx.sound(at, Sound.BLOCK_LAVA_POP, 1.4f, 0.5f + 1.0f * (float) charge);
        }

        // Added to the player's own velocity, never replacing it — running is always still possible,
        // it just costs the time they would rather be spending on the boss.
        double strength = pullPerTick * (0.6 + charge);
        for (Player player : combatants(instance)) {
            Vector toward = at.toVector().subtract(player.getLocation().toVector()).setY(0);
            if (toward.lengthSquared() < 0.01) {
                continue;
            }
            player.setVelocity(player.getVelocity().add(toward.normalize().multiply(strength)));
            if (ticks % 6 == 0) {
                Fx.line(player.getLocation().add(0, 1.0, 0), at.clone().add(0, 1.2, 0), Particle.FLAME, 8);
            }
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
            boolean safe = dist >= blastRadius;
            Component text = Component.text(titleText + "  ", NamedTextColor.GOLD)
                    .append(safe
                            ? Component.text("clear of the blast", NamedTextColor.GREEN)
                            : Component.text("IN THE CIRCLE — " + (int) Math.ceil(blastRadius - dist)
                                    + " blocks to go", NamedTextColor.RED))
                    .append(Component.text("   " + secondsLeft + "s", NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text, charge, safe ? BossBar.Color.GREEN : BossBar.Color.RED);
        });
    }

    @Override
    protected void expire(BossInstance instance) {
        Location at = instance.entity().getLocation();
        Fx.expandingRings(plugin, at, Particle.FLAME, blastRadius, 4, 2L);
        Fx.coloredBurst(at.clone().add(0, 1.2, 0), CRUCIBLE_COLOR, 3.0f, 100, 1.4);
        Fx.flash(at.clone().add(0, 1.2, 0), 3);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.9f, 0.5f);

        boolean anyoneEscaped = false;
        for (Player player : combatants(instance)) {
            if (flatDistance(player.getLocation(), at) >= blastRadius) {
                anyoneEscaped = true;
                continue;
            }
            hurt(instance, player, blastDamage);
            if (player.isValid() && !player.isDead()) {
                player.setFireTicks(Math.max(player.getFireTicks(), burnTicks));
            }
        }
        if (anyoneEscaped) {
            instance.recordExposure();
        }
        instance.showTitle(
                anyoneEscaped
                        ? Component.text("BROKE THE PULL", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true)
                        : Component.text("POINT BLANK", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text(anyoneEscaped ? "Get back in" : "Everyone was still standing in it",
                        NamedTextColor.GRAY));
    }
}
