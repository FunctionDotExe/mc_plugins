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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Plague Warden's signature: he doesn't hide behind wards, he infects the whole party at once.
 * He goes untouchable and everyone nearby catches a stacking plague; break the Cure Blossoms before
 * the outbreak peaks and everyone's cleansed (and he's left exposed) — let it run its course and
 * every infected player takes a scaled burst on top of whatever they already caught. A group
 * cure-or-suffer race, not another totem ring.
 */
public final class PandemicSurgeAttack extends BossAttack {

    private static final Color TOXIC = Color.fromRGB(80, 140, 40);

    private final int telegraphTicks;
    private final int durationTicks;
    private final int blossomCount;
    private final double blossomHealth;
    private final int poisonTicks;
    private final double burstDamagePerStack;
    private final int exposedStaggerTicks;
    private final double exposedMultiplier;
    private final int exposedTicks;

    public PandemicSurgeAttack(WeaponsPlugin plugin) {
        super(plugin, "plague_warden");
        this.telegraphTicks = configInt("pandemic-surge-telegraph-ticks", 26);
        this.durationTicks = configInt("pandemic-surge-duration-ticks", 100);
        this.blossomCount = configInt("pandemic-surge-blossom-count", 2);
        this.blossomHealth = configDouble("pandemic-surge-blossom-health", 22.0);
        this.poisonTicks = configInt("pandemic-surge-poison-ticks", 100);
        this.burstDamagePerStack = configDouble("pandemic-surge-burst-per-stack", 5.0);
        this.exposedStaggerTicks = configInt("pandemic-surge-stagger-ticks", 60);
        this.exposedMultiplier = configDouble("pandemic-surge-exposed-multiplier", 2.0);
        this.exposedTicks = configInt("pandemic-surge-exposed-ticks", 100);
    }

    @Override
    public String name() {
        return "Pandemic Surge";
    }

    @Override
    public double cooldownSeconds() {
        return configDouble("pandemic-surge-cooldown-seconds", 48.0);
    }

    @Override
    protected void castRun(AttackContext ctx, Runnable onComplete) {
        sequence(telegraphTicks,
                () -> {
                    Fx.coloredRing(ctx.bossLocation(), TOXIC, 1.4f, 3.0, 20, 0);
                    Fx.sound(ctx.bossLocation(), Sound.ENTITY_WITCH_AMBIENT, 0.9f, 0.5f);
                },
                () -> {
                    ctx.instance().setForcedInvulnerable(true);
                    Location bossLoc = ctx.bossLocation();
                    double radius = ctx.arena().radius();

                    Set<UUID> infected = new HashSet<>();
                    for (Player player : Arena.playersNear(bossLoc, radius)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonTicks, 1));
                        infected.add(player.getUniqueId());
                    }

                    ctx.instance().showTitle(
                            Component.text("OUTBREAK", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true),
                            Component.text("Break the Cure Blossoms before it peaks", NamedTextColor.GRAY));
                    BossAudio.play(bossLoc, "boss.pandemic_surge", Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 0.6f);

                    List<ArenaTotem> blossoms = new ArrayList<>(blossomCount);
                    for (int i = 0; i < blossomCount; i++) {
                        double angle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
                        double dist = radius * 0.4;
                        Location spot = bossLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
                        Fx.coloredBurst(spot.clone().add(0, 1, 0), TOXIC, 1.6f, 25, 0.4);
                        ArenaTotem blossom = ArenaTotem.spawn(plugin, ctx.instance(), spot, Material.SPORE_BLOSSOM,
                                Component.text("Cure Blossom", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                                blossomHealth, durationTicks + 20,
                                destroyed -> onBlossomBroken(bossLoc, radius, infected),
                                expired -> { });
                        blossoms.add(blossom);
                    }

                    ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), () -> {
                        for (ArenaTotem blossom : blossoms) {
                            blossom.discard();
                        }
                        resolve(ctx, infected);
                    }, durationTicks);
                },
                durationTicks + 20, onComplete);
    }

    private void onBlossomBroken(Location bossLoc, double radius, Set<UUID> infected) {
        for (Player player : Arena.playersNear(bossLoc, radius)) {
            if (infected.remove(player.getUniqueId())) {
                player.removePotionEffect(PotionEffectType.POISON);
                Fx.coloredBurst(player.getLocation().add(0, 1, 0), Color.fromRGB(150, 220, 150), 1.2f, 20, 0.4);
            }
        }
    }

    private void resolve(AttackContext ctx, Set<UUID> stillInfected) {
        ctx.instance().setForcedInvulnerable(false);
        Location loc = ctx.bossLocation();
        if (stillInfected.isEmpty()) {
            ctx.instance().recordExposure();
            ctx.instance().setDamageMultiplier(exposedMultiplier);
            ctx.instance().stagger(exposedStaggerTicks);
            ctx.instance().entity().setGlowing(true);
            Fx.coloredBurst(loc.clone().add(0, 1.2, 0), TOXIC, 2.2f, 50, 0.8);
            Fx.flash(loc.clone().add(0, 1.2, 0), 2);
            Fx.sound(loc, Sound.ENTITY_WITCH_HURT, 1.2f, 1.2f);
            ctx.instance().showTitle(
                    Component.text("OUTBREAK CONTAINED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
                    Component.text("The plague turns against him", NamedTextColor.GRAY));
            ctx.plugin().getServer().getScheduler().runTaskLater(ctx.plugin(), () -> {
                if (ctx.boss().isValid()) {
                    ctx.instance().entity().setGlowing(false);
                    ctx.instance().setDamageMultiplier(1.0);
                }
            }, exposedTicks);
        } else {
            for (UUID id : stillInfected) {
                Player player = ctx.plugin().getServer().getPlayer(id);
                if (player != null && player.isOnline()) {
                    player.damage(burstDamagePerStack, ctx.boss());
                    Fx.coloredBurst(player.getLocation().add(0, 1, 0), TOXIC, 1.8f, 30, 0.5);
                }
            }
            Fx.sound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 0.6f);
        }
    }
}
