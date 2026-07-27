package dev.rbm72.weaponsplugin.boss.bosses.attacks;

import dev.rbm72.weaponsplugin.WeaponsPlugin;
import dev.rbm72.weaponsplugin.boss.Arena;
import dev.rbm72.weaponsplugin.boss.AttackContext;
import dev.rbm72.weaponsplugin.boss.BossAttack;
import dev.rbm72.weaponsplugin.boss.BossAudio;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotem;
import dev.rbm72.weaponsplugin.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Necro Overlord's signature: he splits into false reflections instead of hiding behind a
 * static ward. He goes untouchable and two soul-mirrors flicker into being nearby — feeding on
 * anyone who attacks them (curse stacks). Ignore the decoys and let the window pass clean and he's
 * left wide open; get baited into hitting them and he heals off the fed souls instead. A deception
 * check, not another totem ring.
 */
public final class MirrorOfTheDamnedAttack extends BossAttack {

    private static final Color NECROTIC = Color.fromRGB(120, 200, 110);

    private final int telegraphTicks;
    private final int durationTicks;
    private final double mirrorHealth;
    private final int curseFailThreshold;
    private final int curseTicks;
    private final double failHealBurst;
    private final int exposedStaggerTicks;
    private final double exposedMultiplier;
    private final int exposedTicks;

    public MirrorOfTheDamnedAttack(WeaponsPlugin plugin) {
        super(plugin, "necro_overlord");
        this.telegraphTicks = configInt("mirror-of-the-damned-telegraph-ticks", 26);
        this.durationTicks = configInt("mirror-of-the-damned-duration-ticks", 90);
        this.mirrorHealth = configDouble("mirror-of-the-damned-health", 4.0);
        this.curseFailThreshold = configInt("mirror-of-the-damned-fail-threshold", 2);
        this.curseTicks = configInt("mirror-of-the-damned-curse-ticks", 60);
        this.failHealBurst = configDouble("mirror-of-the-damned-fail-heal", 45.0);
        this.exposedStaggerTicks = configInt("mirror-of-the-damned-stagger-ticks", 60);
        this.exposedMultiplier = configDouble("mirror-of-the-damned-exposed-multiplier", 2.0);
        this.exposedTicks = configInt("mirror-of-the-damned-exposed-ticks", 100);
    }

    @Override
    public String name() {
        return "Mirror of the Damned";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("mirror-of-the-damned-cooldown-seconds", 45.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(ctx.bossLocation(), NECROTIC, 1.4f, 3.0, 20, 0);
                    Fx.sound(ctx.bossLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.9f, 0.5f);
                },
                () -> {
                    ctx.instance().setForcedInvulnerable(true);
                    ctx.instance().showTitle(
                            Component.text("MIRROR OF THE DAMNED", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                            Component.text("The false ones feed on whoever strikes them", NamedTextColor.GRAY));
                    BossAudio.play(ctx.bossLocation(), "boss.mirror_of_the_damned", Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.6f);

                    AtomicInteger curses = new AtomicInteger(0);
                    List<ArenaTotem> mirrors = new ArrayList<>(2);
                    for (int i = 0; i < 2; i++) {
                        double angle = Math.PI / 2 + (i == 0 ? -1 : 1) * (Math.PI / 3);
                        Location spot = ctx.bossLocation().clone().add(Math.cos(angle) * 3.0, 0, Math.sin(angle) * 3.0);
                        Fx.coloredBurst(spot.clone().add(0, 1, 0), NECROTIC, 1.6f, 25, 0.4);
                        ArenaTotem mirror = ArenaTotem.spawn(plugin, ctx.instance(), spot, Material.WITHER_SKELETON_SKULL,
                                Component.text("Soul Mirror", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false),
                                mirrorHealth, durationTicks + 20,
                                destroyed -> onMirrorStruck(ctx, curses),
                                expired -> { });
                        mirrors.add(mirror);
                    }

                    ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), () -> {
                        for (ArenaTotem mirror : mirrors) {
                            mirror.discard();
                        }
                        resolve(ctx, curses.get());
                    }, durationTicks);
                },
                durationTicks + 20, onComplete);
    }

    private void onMirrorStruck(AttackContext ctx, AtomicInteger curses) {
        curses.incrementAndGet();
        Location loc = ctx.bossLocation();
        for (Player player : Arena.combatants(loc, ctx.arena().radius())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, curseTicks, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, curseTicks, 1));
        }
        Fx.coloredBurst(loc.clone().add(0, 1, 0), NECROTIC, 1.2f, 20, 0.3);
        Fx.sound(loc, Sound.ENTITY_WITHER_HURT, 0.8f, 1.3f);
    }

    private void resolve(AttackContext ctx, int curses) {
        ctx.instance().setForcedInvulnerable(false);
        Location loc = ctx.bossLocation();
        if (curses < curseFailThreshold) {
            ctx.instance().recordExposure();
            ctx.instance().setDamageMultiplier(exposedMultiplier);
            ctx.instance().stagger(exposedStaggerTicks);
            ctx.instance().entity().setGlowing(true);
            Fx.coloredBurst(loc.clone().add(0, 1.2, 0), NECROTIC, 2.2f, 50, 0.8);
            Fx.flash(loc.clone().add(0, 1.2, 0), 2);
            Fx.sound(loc, Sound.ENTITY_WITHER_HURT, 1.2f, 1.2f);
            ctx.instance().showTitle(
                    Component.text("UNMASKED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
                    Component.text("The deception failed him", NamedTextColor.GRAY));
            ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), () -> {
                if (ctx.boss().isValid()) {
                    ctx.instance().entity().setGlowing(false);
                    ctx.instance().setDamageMultiplier(1.0);
                }
            }, exposedTicks);
        } else {
            var maxHealthAttr = ctx.boss().getAttribute(Attribute.MAX_HEALTH);
            double cap = maxHealthAttr != null ? maxHealthAttr.getValue() : ctx.boss().getHealth();
            ctx.boss().setHealth(Math.min(cap, ctx.boss().getHealth() + failHealBurst));
            Fx.coloredBurst(loc.clone().add(0, 1.2, 0), NECROTIC, 2.0f, 40, 0.6);
            Fx.sound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        }
    }
}
