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

/**
 * The boss charges something that cannot be dodged, blocked or healed through, and the only defence
 * is to put something solid between you and it.
 * <p>
 * This is the one mechanic in the roster that makes the <em>arena itself</em> matter. Every other
 * demand is satisfied somewhere on the open floor; this one sends the whole group hunting for
 * geometry — a pillar, a rock, the lip of a ridge — which means players finally look at the terrain
 * they have been fighting on for ten minutes. It also cleanly separates a group that knows the room
 * from one that does not, without punishing either for gear or damage.
 * <p>
 * The window is short and the boss holds still and untouchable for it: this is a total-attention
 * interruption, not something to be handled while fighting, and eight seconds is the ceiling on how
 * long that is ever allowed to last.
 */
public final class LineOfSightEvent extends ScriptedEvent {

    private static final Color BEAM_COLOR = Color.fromRGB(255, 240, 160);

    private final int windowTicks;
    private final double damage;
    private final int blindTicks;
    private final String titleText;
    private final String subtitleText;

    public LineOfSightEvent(WeaponsPlugin plugin, String bossId, double[] triggers) {
        this(plugin, bossId, triggers, "SOLAR FLARE", "Break line of sight — hide behind something");
    }

    public LineOfSightEvent(WeaponsPlugin plugin, String bossId, double[] triggers,
                             String titleText, String subtitleText) {
        super(plugin, bossId, triggers);
        this.windowTicks = configInt("flare-window-ticks", 110);
        this.damage = configDouble("flare-damage", 34.0);
        this.blindTicks = configInt("flare-blind-ticks", 80);
        this.titleText = titleText;
        this.subtitleText = subtitleText;
    }

    @Override
    public String id() {
        return "line_of_sight";
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
        // Untouchable for the charge: the answer to this is cover, never damage. Released on every
        // path by ScriptedEvent's own cleanup.
        instance.setForcedInvulnerable(true);
        instance.entity().setGlowing(true);

        Location at = instance.entity().getLocation();
        Fx.coloredBurst(at.clone().add(0, 1.6, 0), BEAM_COLOR, 2.6f, 60, 0.8);
        Fx.sound(at, Sound.BLOCK_BEACON_ACTIVATE, 1.6f, 0.6f);
        instance.showTitle(
                Component.text(titleText, NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
                Component.text(subtitleText, NamedTextColor.GRAY));
        return true;
    }

    @Override
    protected void tick(BossInstance instance, int ticks) {
        Location at = instance.entity().getLocation().add(0, 1.6, 0);
        double charge = ticks / (double) windowTicks;

        Fx.coloredRing(at, BEAM_COLOR, 1.6f, 3.0 * (1.0 - charge) + 0.6, 24, ticks * 0.25);
        if (ticks % 3 == 0) {
            // A thread of light to everyone it can currently see: the cue is per player and unmissable.
            for (Player player : combatants(instance)) {
                if (player.hasLineOfSight(instance.entity())) {
                    Fx.line(at, player.getEyeLocation(), Particle.END_ROD, 10);
                }
            }
        }
        if (ticks % 20 == 0) {
            Fx.sound(at, Sound.BLOCK_BEACON_AMBIENT, 1.4f, 0.6f + 1.0f * (float) charge);
        }
        if (ticks % 4 == 0) {
            showBars(instance, ticks);
        }
    }

    private void showBars(BossInstance instance, int ticks) {
        double charge = Math.min(1.0, ticks / (double) windowTicks);
        int secondsLeft = Math.max(0, (windowTicks - ticks) / 20);
        instance.mechanicBar().update(MechanicBar.Owner.EVENT, instance.barViewers(), viewer -> {
            boolean exposed = viewer.hasLineOfSight(instance.entity());
            Component text = Component.text(titleText + "  ", NamedTextColor.YELLOW)
                    .append(exposed
                            ? Component.text("IT CAN SEE YOU", NamedTextColor.RED)
                            : Component.text("in cover", NamedTextColor.GREEN))
                    .append(Component.text("   " + secondsLeft + "s", NamedTextColor.GRAY));
            return MechanicBar.Readout.of(text, charge,
                    exposed ? BossBar.Color.RED : BossBar.Color.GREEN);
        });
    }

    @Override
    protected void expire(BossInstance instance) {
        Location at = instance.entity().getLocation().add(0, 1.6, 0);
        Fx.coloredBurst(at, BEAM_COLOR, 3.0f, 90, 1.4);
        Fx.flash(at, 3);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.5f);

        boolean anyoneCovered = false;
        for (Player player : combatants(instance)) {
            if (!player.hasLineOfSight(instance.entity())) {
                anyoneCovered = true;
                continue;
            }
            Fx.line(at, player.getEyeLocation(), Particle.END_ROD, 16);
            hurt(instance, player, damage);
            if (player.isValid() && !player.isDead()) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.BLINDNESS, blindTicks, 0));
            }
        }
        if (anyoneCovered) {
            // Getting behind cover is the whole ask; crediting it keeps the phase floor honest.
            instance.recordExposure();
        }
        instance.showTitle(
                anyoneCovered
                        ? Component.text("SHIELDED BY STONE", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true)
                        : Component.text("NOWHERE TO HIDE", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                Component.text(anyoneCovered ? "The light passed over you" : "It burned straight through",
                        NamedTextColor.GRAY));
    }
}
