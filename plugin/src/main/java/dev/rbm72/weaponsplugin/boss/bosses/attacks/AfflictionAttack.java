package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.fx.Fx;
import dev.rbm72.weaponsplugin.ui.ActionBarHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A stacking damage-over-time brand, reusable across bosses via the {@code bossId} constructor param
 * (same as the trial roster). On cast every player in range is branded; each existing brand adds a
 * stack, and every tick the brand deals damage scaled by how many stacks it's carrying, so getting
 * caught by cast after cast without letting it wear off snowballs. It isn't dodged once it's on you —
 * it's a "keep it from stacking" pressure, the slayer-style rot/bleed that punishes standing in the
 * boss's face indefinitely. Theme (name / colour / particle / sound) is supplied per boss.
 */
public final class AfflictionAttack extends BossAttack {

    private final String brandName;
    private final Color color;
    private final Sound applySound;

    private final int telegraphTicks;
    private final double radius;
    private final double tickDamage;
    private final int tickIntervalTicks;
    private final int durationTicks;
    private final int maxStacks;

    /** Per-player live stack count, keyed by UUID; entries clear themselves when a brand fully wears off. */
    private final Map<UUID, Integer> stacks = new HashMap<>();

    public AfflictionAttack(WeaponsPlugin plugin, String bossId, String brandName, Color color, Sound applySound) {
        super(plugin, bossId);
        this.brandName = brandName;
        this.color = color;
        this.applySound = applySound;
        this.telegraphTicks = configInt("affliction-telegraph-ticks", 24);
        this.radius = configDouble("affliction-radius", 8.0);
        this.tickDamage = configDouble("affliction-tick-damage", 1.5);
        this.tickIntervalTicks = configInt("affliction-tick-interval-ticks", 20);
        this.durationTicks = configInt("affliction-duration-ticks", 120);
        this.maxStacks = configInt("affliction-max-stacks", 5);
    }

    @Override
    public String name() {
        return brandName;
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("affliction-cooldown-seconds", 26.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(ctx.bossLocation(), color, 1.2f, 3.0, 18, 0);
                    Fx.sound(ctx.bossLocation(), applySound, 0.6f, 1.2f);
                },
                () -> {
                    for (Player player : Arena.combatants(ctx.bossLocation(), radius)) {
                        int now = Math.min(maxStacks, stacks.getOrDefault(player.getUniqueId(), 0) + 1);
                        stacks.put(player.getUniqueId(), now);
                        ctx.plugin().actionBarHub().flash(player,
                                Component.text(brandName + " x" + now, NamedTextColor.RED),
                                2000, ActionBarHub.PRIORITY_NOTICE);
                        Fx.coloredBurst(player.getLocation().add(0, 1, 0), color, 1.4f, 18, 0.4);
                        Fx.sound(player.getLocation(), applySound, 0.8f, 1.0f);
                    }
                    ctx.instance().showTitle(
                            Component.text(brandName.toUpperCase(java.util.Locale.ROOT), NamedTextColor.RED)
                                    .decoration(TextDecoration.BOLD, true),
                            Component.text("It festers while you crowd it — let it wear off", NamedTextColor.GRAY));
                    BossAudio.play(ctx.bossLocation(), "boss.affliction", applySound, 1.0f, 0.8f);

                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            if (elapsed >= durationTicks || !ctx.boss().isValid()) {
                                cancel();
                                return;
                            }
                            for (Player player : ctx.boss().getWorld().getPlayers()) {
                                Integer s = stacks.get(player.getUniqueId());
                                if (s == null) {
                                    continue;
                                }
                                if (!player.isValid() || player.isDead()) {
                                    stacks.remove(player.getUniqueId());
                                    continue;
                                }
                                player.damage(tickDamage * s, ctx.boss());
                                Fx.coloredBurst(player.getLocation().add(0, 1, 0), color, 1.0f, 6, 0.2);
                            }
                            elapsed += tickIntervalTicks;
                        }
                    }.runTaskTimer(plugin, tickIntervalTicks, tickIntervalTicks);

                    // The brand wears off after its full duration — one clear point, so stacks can't
                    // linger forever across the whole fight and hard-punish a single early mistake.
                    ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), stacks::clear, durationTicks);
                },
                0, onComplete);
    }
}
