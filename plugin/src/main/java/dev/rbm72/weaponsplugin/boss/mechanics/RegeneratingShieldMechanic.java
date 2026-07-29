package dev.rbm72.weaponsplugin.boss.mechanics;

import dev.rbm72.weaponsplugin.boss.BossInstance;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A shield that knits itself back together faster than most groups can cut it. Every <em>blow</em>
 * severs a little of it regardless of how hard that blow was, and it regrows every second — so the
 * only thing that gets through it is a high rate of hits, sustained.
 * <p>
 * This is the continuous cousin of the shield-check event, and it exists for the same reason: nothing
 * else in the plugin makes attack speed matter. The difference is that the event is a discrete race
 * you either pass or fail, while this runs for a whole phase and is never binary. A slow, heavy
 * weapon still contributes — it simply cannot open the boss up on its own, and the group discovers
 * mid-fight which of them is actually built for this.
 * <p>
 * The shield never blocks outright. At full strength it eats most of a hit but not all of it, so a
 * group with no fast weapons at all still finishes, just slowly — a soft wall, never a hard one.
 */
public final class RegeneratingShieldMechanic extends TickingMechanic {

    private static final long TICK_INTERVAL = 4L;
    /** Minimum gap between two counted blows from one player, so a multi-hit proc cannot strip it. */
    private static final long HIT_COOLDOWN_MS = 120;

    private final String label;
    private final Color color;
    private final double maxShield;
    private final double regenPerSecond;
    private final double lossPerHit;
    private final double maxDamageReduction;
    private final int severedTicks;
    private final double exposedMultiplier;
    private final int staggerTicks;

    private final Map<UUID, Long> lastCountedHit = new HashMap<>();
    private double shield;
    private int severedLeft;

    public RegeneratingShieldMechanic(BossInstance instance, String label, Color color, double maxShield,
                                       double regenPerSecond, double lossPerHit, double maxDamageReduction,
                                       int severedTicks, double exposedMultiplier, int staggerTicks) {
        super(instance, TICK_INTERVAL);
        this.label = label;
        this.color = color;
        this.maxShield = Math.max(1.0, maxShield);
        this.regenPerSecond = regenPerSecond;
        this.lossPerHit = Math.max(0.01, lossPerHit);
        this.maxDamageReduction = Math.max(0.0, Math.min(0.85, maxDamageReduction));
        this.severedTicks = Math.max(20, severedTicks);
        this.exposedMultiplier = exposedMultiplier;
        this.staggerTicks = staggerTicks;
    }

    @Override
    protected void onStart() {
        shield = maxShield;
        instance.showTitle(
                Component.text(label, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("It knits itself shut — only fast hands cut through", NamedTextColor.GRAY));
        Fx.sound(instance.entity().getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.2f, 0.6f);
    }

    @Override
    protected void onStop() {
        lastCountedHit.clear();
    }

    /**
     * Scales with how much shield is actually left, so cutting it down pays off continuously rather
     * than only at the moment it breaks. Floored well short of immunity on purpose.
     */
    @Override
    public double filterDamage(Player attacker, double damage) {
        if (stopped || severedLeft > 0 || shield <= 0) {
            return damage;
        }
        double reduction = maxDamageReduction * (shield / maxShield);
        return damage * (1.0 - reduction);
    }

    /**
     * Counts blows, not damage. Fires even on hits the shield reduced to nearly nothing, which is what
     * makes a fast, weak weapon the right tool here.
     */
    @Override
    public void onBossDamaged(Player attacker, double damageDealt) {
        if (stopped || severedLeft > 0 || attacker == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastCountedHit.get(attacker.getUniqueId());
        if (last != null && now - last < HIT_COOLDOWN_MS) {
            return;
        }
        lastCountedHit.put(attacker.getUniqueId(), now);

        shield = Math.max(0.0, shield - lossPerHit);
        Location at = instance.entity().getLocation();
        Fx.coloredBurst(at.clone().add(0, 1.4, 0), color, 0.9f, 6, 0.3);
        Fx.sound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f,
                1.4f - 0.6f * (float) (shield / maxShield));
        if (shield <= 0) {
            sever();
        }
    }

    @Override
    protected void tick() {
        if (severedLeft > 0) {
            severedLeft -= TICK_INTERVAL;
            if (severedLeft <= 0) {
                shield = maxShield * 0.4;
                instance.setDamageMultiplier(1.0);
                if (instance.entity().isValid()) {
                    instance.entity().setGlowing(false);
                }
                Fx.sound(instance.entity().getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);
            }
            showBars();
            return;
        }

        shield = Math.min(maxShield, shield + regenPerSecond * (TICK_INTERVAL / 20.0));
        if (elapsedTicks % 8 == 0) {
            Fx.coloredRing(instance.entity().getLocation().add(0, 1.2, 0), color, 1.1f,
                    1.0 + 1.6 * (shield / maxShield), 18, elapsedTicks * 0.12);
        }
        showBars();
    }

    private void showBars() {
        if (severedLeft > 0) {
            Component text = Component.text("SUTURES CUT  ", NamedTextColor.GREEN)
                    .append(Component.text(Math.max(0, severedLeft / 20) + "s of open flesh", NamedTextColor.WHITE));
            instance.mechanicBar().updateShared(instance.barViewers(), text,
                    severedLeft / (double) severedTicks, BossBar.Color.GREEN);
            return;
        }
        double fraction = shield / maxShield;
        Component text = Component.text(label + "  ", NamedTextColor.GOLD)
                .append(Component.text((int) Math.ceil(shield) + " / " + (int) maxShield, NamedTextColor.WHITE))
                .append(Component.text("   land hits fast — damage per swing does not matter",
                        NamedTextColor.DARK_GRAY));
        instance.mechanicBar().updateShared(instance.barViewers(), text, fraction,
                fraction > 0.6 ? BossBar.Color.RED : fraction > 0.25 ? BossBar.Color.YELLOW : BossBar.Color.GREEN);
    }

    private void sever() {
        severedLeft = severedTicks;
        instance.recordExposure();
        instance.setDamageMultiplier(exposedMultiplier);
        instance.stagger(staggerTicks);

        Location at = instance.entity().getLocation();
        if (instance.entity().isValid()) {
            instance.entity().setGlowing(true);
        }
        Fx.coloredBurst(at.clone().add(0, 1.4, 0), color, 2.6f, 70, 1.0);
        Fx.flash(at.clone().add(0, 1.4, 0), 2);
        Fx.sound(at, Sound.ITEM_SHIELD_BREAK, 1.4f, 0.9f);
        instance.showTitle(
                Component.text("SUTURES SEVERED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                Component.text("Open — everything lands now", NamedTextColor.GRAY));
    }
}
