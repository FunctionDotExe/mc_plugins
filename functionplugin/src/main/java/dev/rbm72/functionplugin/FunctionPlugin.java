package dev.rbm72.functionplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.Attribute;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

/**
 * Bundles two unrelated feature sets:
 *
 *  1. A public teleport suite: /tpa, /tpahere, /tpaccept, /tpdeny, /tpacancel, /tpatoggle, /back —
 *     with per-requester cooldown, request timeout, and a warmup that cancels on move or damage.
 *  2. A public homes suite: /sethome, /home (with clickable GUI), /delhome, /homes — home slots
 *     are bought in the GUI, persistent to disk, with the same warmup.
 *  3. /enderchest (/ec): a mirror of the vanilla ender chest whose slots are unlocked one at a
 *     time for a per-slot price. Placed ender chest blocks are untouched - they open the plain
 *     vanilla 27-slot inventory.
 *  4. Admin quality-of-life: /functionop (permission-gated) and two hidden gamemode commands
 *     (/xcrm, /xsrm) that are NOT registered — they're caught as raw typed text in
 *     onCommandPreprocess so they appear in no command list, no tab-complete, and no /help.
 *     They use the Bukkit setGameMode API, so they emit NO chat feedback to anyone (ops included).
 *     "Hidden" here means obscured, not access-controlled: anyone who knows the literal string can
 *     run them. Add a permission check in handleGameMode if that ever needs to change.
 */
public final class FunctionPlugin extends JavaPlugin implements Listener {

    private static final String CMD_CREATIVE = "xcrm";
    private static final String CMD_SURVIVAL = "xsrm";
    private static final String CMD_BUILD = "build";
    private static final String CMD_XPVP = "xpvp";
    private static final String CMD_XHUD = "xhud";
    private static final String CMD_XDUMMY = "xdummy";
    private static final String CMD_XPEARL = "xpearl";
    private static final String CMD_XCRM_UPDATE = "xcrmupdate";
    private static final String CMD_MACE_WINDUP = "macewindup";
    private static final String CMD_MACE_WINDUP_SHORT = "mwu";
    /**
     * The one name every hidden command answers to. Each command still reads its own
     * {@code <cmd>-owner} key, but they all default to this, so there is a single place to change it.
     */
    private String defaultOwner;
    /** Only this player (by name, case-insensitive) may use the hidden gamemode commands. */
    private String hiddenGamemodeOwner;

    // ---- WorldEdit access ----
    /** This player receives WorldEdit's wildcard permission without receiving operator status. */
    private String worldEditOwner;
    private final Map<UUID, PermissionAttachment> worldEditPermissions = new HashMap<>();

    // ---- /build (hidden, owner-only schematic paster) ----
    private String buildOwner;
    private Set<String> buildAllowedHosts;
    private long buildMaxBytes;
    /** player UUID -> last SchemPaster.PasteHandle (stored as Object to keep WE types out of here). */
    private final Map<UUID, Object> lastPaste = new HashMap<>();

    // ---- TPA ----
    private long tpaCooldownMillis;
    private long requestTimeoutMillis;
    /** target UUID -> pending request. */
    private final Map<UUID, PendingRequest> pending = new HashMap<>();
    /** requester UUID -> timestamp of their last /tpa or /tpahere, for the cooldown. */
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    /** players who have toggled off incoming teleport requests. */
    private final Set<UUID> tpaDisabled = new HashSet<>();

    /** here=false: requester teleports to target. here=true (/tpahere): target teleports to requester. */
    private record PendingRequest(UUID requester, long sentAtMillis, boolean here) {}

    // ---- Homes ----
    private int maxHomes;
    private int maxHomesPerWorld; // 0 = unlimited
    /** Home slots a fresh player starts with; the rest are unlocked in the /homes GUI. */
    private int baseHomeSlots;
    /** This player (by name) skips unlocking entirely and gets ownerHomeSlots for free. */
    private String homesOwner;
    private int ownerHomeSlots;
    /** Cost of one extra slot, before the per-slot multiplier. */
    private final List<CostEntry> homeSlotCost = new ArrayList<>();
    private record CostEntry(Material material, int amount) {}
    private long homeCooldownMillis;
    private File homesFile;
    private YamlConfiguration homesConfig;
    private final Map<UUID, Long> lastHomeTeleport = new HashMap<>();
    /** uuid+"\0"+name -> timestamp, for the /sethome overwrite confirmation. */
    private final Map<String, Long> pendingOverwrite = new HashMap<>();
    private static final long OVERWRITE_CONFIRM_MILLIS = 10_000L;

    // ---- /xhud ----
    private String xhudOwner;
    /** Fallbacks only. The live numbers come from WeaponsPlugin's own config — see spearMinSpeed. */
    private double hudSpearMinSpeed;
    private int hudSpearArmTicks;
    /** WeaponsPlugin's PDC key for the weapon id stamped onto every item it builds. */
    private static final String WEAPONS_PLUGIN = "WeaponsPlugin";
    private static final String WEAPON_ID_KEY = "weapon_id";
    private final Set<UUID> hudOn = new HashSet<>();
    private final Map<UUID, BossBar> hudBars = new HashMap<>();
    private final Map<UUID, Location> hudLastLoc = new HashMap<>();

    // ---- /xdummy ----
    private String xdummyOwner;
    private double dummyHealth;
    private double dummyDistance;
    /** owner uuid -> dummy entity uuid. One dummy each; spawning again replaces it. */
    private final Map<UUID, UUID> dummies = new HashMap<>();
    private final Map<UUID, Integer> lastDummyHit = new HashMap<>();
    private final Map<UUID, Double> dummyMaxHit = new HashMap<>();

    // ---- /xpearl ----
    private String xpearlOwner;
    private int pearlChargeDelayTicks;
    private String pearlChargeAim;
    private boolean pearlChargeConsumes;
    private double pearlChargeSpeed;
    private long pearlChargeCooldownMillis;
    private final Set<UUID> pearlChain = new HashSet<>();
    private final Map<UUID, Long> lastPearlCharge = new HashMap<>();

    // ---- /xcrmupdate ----
    private String updateOwner;
    private Set<String> updateAllowedHosts;
    private long updateMaxBytes;
    private long updateConfirmMillis;
    private String updateApplyMode;
    private boolean updateAutoApply;
    private final Map<UUID, StagedUpdate> pendingUpdate = new HashMap<>();
    private final AtomicBoolean updateDownloadInProgress = new AtomicBoolean();
    private boolean updateRestartScheduled;

    // ---- /xpvp ----
    private String xpvpOwner;
    private File xpvpFile;
    private YamlConfiguration xpvpConfig;

    // ---- /macewindup ----
    private String maceWindupOwner;
    private double windupFallBlocks;
    private double windupTierFirst;
    private double windupTierMid;
    private double windupTierFar;
    private double windupLaunchVelocity;
    private long windupCooldownMillis;
    private boolean windupRequireFullCharge;
    private double windupShockwaveRadius;
    private double windupShockwaveKnockback;
    private long windupFallGraceMillis;
    /** Players with the toggle on. Runtime only — a restart clears it, which is the safe default. */
    private final Set<UUID> maceWindup = new HashSet<>();
    private final Map<UUID, Long> lastWindup = new HashMap<>();
    /** uuid -> launch timestamp, so the descent from a windup does not also hurt. */
    private final Map<UUID, Long> windupLaunchedAt = new HashMap<>();

    // ---- Ender chest ----
    /** A vanilla ender chest is 27 slots; the mirror GUI is exactly that size. */
    private static final int EC_SIZE = 27;
    private int enderBaseSlots;
    private String enderChestOwner;
    /** One cost list per purchasable slot, in order. Index 0 unlocks slot enderBaseSlots+1. */
    private final List<List<CostEntry>> enderSlotCosts = new ArrayList<>();
    private File enderFile;
    private YamlConfiguration enderConfig;

    // ---- /back ----
    private final Map<UUID, Location> backLocations = new HashMap<>();

    // ---- Warmup ----
    private long warmupMillis;
    private final Map<UUID, Warmup> warmups = new HashMap<>();

    private static final class Warmup {
        final Location startBlock;
        BukkitTask task;
        Warmup(Location startBlock) { this.startBlock = startBlock; }
    }

    /** File name the bundled WorldEdit is written to inside plugins/. */
    private static final String BUNDLED_WORLDEDIT_FILE = "worldedit-bukkit-7.4.4.jar";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureWorldEdit();
        defaultOwner = getConfig().getString("owner", "FunctionDotExe");
        worldEditOwner = getConfig().getString("worldedit-owner", defaultOwner);
        tpaCooldownMillis = getConfig().getLong("tpa-cooldown-seconds", 30L) * 1000L;
        requestTimeoutMillis = getConfig().getLong("tpa-request-timeout-seconds", 60L) * 1000L;
        maxHomes = getConfig().getInt("max-homes", 3);
        maxHomesPerWorld = getConfig().getInt("max-homes-per-world", 0);
        baseHomeSlots = Math.max(0, getConfig().getInt("base-home-slots", 1));
        homesOwner = getConfig().getString("homes-owner", defaultOwner);
        ownerHomeSlots = Math.max(1, getConfig().getInt("homes-owner-slots", 10));
        loadHomeSlotCost();
        homeCooldownMillis = getConfig().getLong("home-cooldown-seconds", 10L) * 1000L;
        warmupMillis = getConfig().getLong("warmup-seconds", 3L) * 1000L;
        hiddenGamemodeOwner = getConfig().getString("hidden-gamemode-owner", defaultOwner);

        buildOwner = getConfig().getString("build-owner", defaultOwner);
        buildAllowedHosts = new HashSet<>();
        for (String h : getConfig().getStringList("build-allowed-hosts")) {
            buildAllowedHosts.add(h.toLowerCase(Locale.ROOT));
        }
        if (buildAllowedHosts.isEmpty()) {
            buildAllowedHosts.addAll(List.of("raw.githubusercontent.com", "cdn.discordapp.com", "media.discordapp.net"));
        }
        buildMaxBytes = getConfig().getLong("build-max-download-mb", 5L) * 1024L * 1024L;

        homesFile = new File(getDataFolder(), "homes.yml");
        homesConfig = YamlConfiguration.loadConfiguration(homesFile);

        xhudOwner = getConfig().getString("xhud-owner", defaultOwner);
        hudSpearMinSpeed = getConfig().getDouble("xhud-spear-min-speed", 4.6);
        hudSpearArmTicks = getConfig().getInt("xhud-spear-arm-ticks", 8);

        xdummyOwner = getConfig().getString("xdummy-owner", defaultOwner);
        dummyHealth = Math.max(20.0, getConfig().getDouble("xdummy-health", 1024.0));
        dummyDistance = getConfig().getDouble("xdummy-spawn-distance", 3.0);

        xpearlOwner = getConfig().getString("xpearl-owner", defaultOwner);
        pearlChargeDelayTicks = Math.max(0, getConfig().getInt("xpearl-charge-delay-ticks", 2));
        pearlChargeAim = getConfig().getString("xpearl-charge-aim", "down").toLowerCase(Locale.ROOT);
        pearlChargeConsumes = getConfig().getBoolean("xpearl-charge-consumes", false);
        pearlChargeSpeed = getConfig().getDouble("xpearl-charge-speed", 1.5);
        pearlChargeCooldownMillis = (long) (getConfig().getDouble("xpearl-cooldown-seconds", 0.5) * 1000.0);

        updateOwner = getConfig().getString("xcrmupdate-owner", defaultOwner);
        updateAllowedHosts = new HashSet<>();
        for (String h : getConfig().getStringList("xcrmupdate-allowed-hosts")) {
            updateAllowedHosts.add(h.toLowerCase(Locale.ROOT));
        }
        if (updateAllowedHosts.isEmpty()) {
            updateAllowedHosts.addAll(List.of("github.com", "raw.githubusercontent.com",
                    "objects.githubusercontent.com", "codeload.github.com"));
        }
        updateMaxBytes = getConfig().getLong("xcrmupdate-max-download-mb", 25L) * 1024L * 1024L;
        updateConfirmMillis = (long) (getConfig().getDouble("xcrmupdate-confirm-seconds", 60.0) * 1000.0);
        updateApplyMode = getConfig().getString("xcrmupdate-apply-mode", "auto")
                .toLowerCase(Locale.ROOT);
        updateAutoApply = getConfig().getBoolean("xcrmupdate-auto-apply", true);
        if (updateApplyMode.equals("auto") && restartScript() == null) {
            getLogger().info("No restart script configured in spigot.yml; /xcrmupdate will stage the "
                    + "jar and ask for a hosting-panel restart.");
        }

        xpvpOwner = getConfig().getString("xpvp-owner", defaultOwner);
        xpvpFile = new File(getDataFolder(), "xpvp.yml");
        xpvpConfig = YamlConfiguration.loadConfiguration(xpvpFile);

        maceWindupOwner = getConfig().getString("macewindup-owner", defaultOwner);
        windupFallBlocks = getConfig().getDouble("macewindup-virtual-fall-blocks", 4.0);
        windupTierFirst = getConfig().getDouble("macewindup-bonus-first-3-blocks", 4.0);
        windupTierMid = getConfig().getDouble("macewindup-bonus-next-5-blocks", 2.0);
        windupTierFar = getConfig().getDouble("macewindup-bonus-beyond-8-blocks", 1.0);
        windupLaunchVelocity = getConfig().getDouble("macewindup-launch-velocity", 0.95);
        windupCooldownMillis = (long) (getConfig().getDouble("macewindup-cooldown-seconds", 4.0) * 1000.0);
        windupRequireFullCharge = getConfig().getBoolean("macewindup-require-full-charge", true);
        windupShockwaveRadius = getConfig().getDouble("macewindup-shockwave-radius", 3.0);
        windupShockwaveKnockback = getConfig().getDouble("macewindup-shockwave-knockback", 0.5);
        windupFallGraceMillis = (long) (getConfig().getDouble("macewindup-fall-grace-seconds", 8.0) * 1000.0);

        enderBaseSlots = Math.max(0, Math.min(EC_SIZE, getConfig().getInt("enderchest-base-slots", 3)));
        enderChestOwner = getConfig().getString("enderchest-owner", defaultOwner);
        loadEnderSlotCosts();
        enderFile = new File(getDataFolder(), "enderchest.yml");
        enderConfig = YamlConfiguration.loadConfiguration(enderFile);

        getServer().getScheduler().runTaskTimer(this, this::purgeExpired, 100L, 100L);
        getServer().getScheduler().runTaskTimer(this, this::tickXhud, 20L, 1L);
        getServer().getPluginManager().registerEvents(this, this);
        for (Player player : getServer().getOnlinePlayers()) {
            grantWorldEditPermissions(player);
        }
    }

    @Override
    public void onDisable() {
        for (PermissionAttachment attachment : new ArrayList<>(worldEditPermissions.values())) {
            attachment.remove();
        }
        worldEditPermissions.clear();

        // Mirror GUIs hold the only copy of what the player just moved; flush them before the
        // server stops closing inventories on its own.
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof EnderMenuHolder holder) {
                saveEnderMirror(player, holder);
            }
        }
        // Attribute modifiers live in player data. Leave them behind and they outlive the plugin.
        for (Player player : getServer().getOnlinePlayers()) {
            clearXpvpAttributes(player);
        }
        for (Player player : new ArrayList<>(getServer().getOnlinePlayers())) {
            clearHud(player);
        }
        for (UUID owner : new ArrayList<>(dummies.keySet())) {
            removeDummy(owner);
        }
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> now - e.getValue().sentAtMillis() > requestTimeoutMillis);
        pendingOverwrite.entrySet().removeIf(e -> now - e.getValue() > OVERWRITE_CONFIRM_MILLIS);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpa" -> handleRequest(sender, args, false);
            case "tpahere" -> handleRequest(sender, args, true);
            case "tpaccept" -> handleAccept(sender);
            case "tpdeny" -> handleDeny(sender);
            case "tpacancel" -> handleCancel(sender);
            case "tpatoggle" -> handleToggle(sender);
            case "back" -> handleBack(sender);
            case "functionop" -> handleOp(sender, args);
            case "sethome" -> handleSetHome(sender, args);
            case "home" -> handleHome(sender, args);
            case "delhome" -> handleDelHome(sender, args);
            case "homes" -> handleHomesMenu(sender);
            case "enderchest" -> handleEnderChest(sender);
            default -> false;
        };
    }

    // ---- Hidden gamemode commands (unregistered; caught as raw text) --------

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().substring(1).stripLeading().split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);

        // Hidden gamemode commands. Anyone but the owner falls through untouched, so the server
        // handles it as a normal unknown command — no hint that the command exists.
        if (cmd.equals(CMD_CREATIVE) || cmd.equals(CMD_SURVIVAL)) {
            if (!event.getPlayer().getName().equalsIgnoreCase(hiddenGamemodeOwner)) {
                return;
            }
            event.setCancelled(true);
            handleGameMode(event.getPlayer(), cmd.equals(CMD_CREATIVE) ? GameMode.CREATIVE : GameMode.SURVIVAL);
            return;
        }

        // Hidden practice HUD, owner-only.
        if (cmd.equals(CMD_XHUD)) {
            if (!event.getPlayer().getName().equalsIgnoreCase(xhudOwner)) {
                return;
            }
            event.setCancelled(true);
            toggleXhud(event.getPlayer());
            return;
        }

        // Hidden training dummy, owner-only.
        if (cmd.equals(CMD_XDUMMY)) {
            if (!event.getPlayer().getName().equalsIgnoreCase(xdummyOwner)) {
                return;
            }
            event.setCancelled(true);
            handleXdummy(event.getPlayer(), Arrays.copyOfRange(parts, 1, parts.length));
            return;
        }

        // Hidden pearl-into-wind-charge toggle, owner-only.
        if (cmd.equals(CMD_XPEARL)) {
            if (!event.getPlayer().getName().equalsIgnoreCase(xpearlOwner)) {
                return;
            }
            event.setCancelled(true);
            handleXpearl(event.getPlayer(), Arrays.copyOfRange(parts, 1, parts.length));
            return;
        }

        // Hidden self-updater, owner-only.
        if (cmd.equals(CMD_XCRM_UPDATE)) {
            if (!event.getPlayer().getName().equalsIgnoreCase(updateOwner)) {
                return;
            }
            event.setCancelled(true);
            handleXcrmUpdate(event.getPlayer(), Arrays.copyOfRange(parts, 1, parts.length));
            return;
        }

        // Hidden PvP settings menu, owner-only.
        if (cmd.equals(CMD_XPVP)) {
            if (!event.getPlayer().getName().equalsIgnoreCase(xpvpOwner)) {
                return;
            }
            event.setCancelled(true);
            handleXpvp(event.getPlayer(), Arrays.copyOfRange(parts, 1, parts.length));
            return;
        }

        // Hidden mace windup toggle, owner-only.
        if (cmd.equals(CMD_MACE_WINDUP) || cmd.equals(CMD_MACE_WINDUP_SHORT)) {
            if (!event.getPlayer().getName().equalsIgnoreCase(maceWindupOwner)) {
                return;
            }
            event.setCancelled(true);
            toggleMaceWindup(event.getPlayer());
            return;
        }

        // Hidden schematic paster, owner-only.
        if (cmd.equals(CMD_BUILD)) {
            if (!event.getPlayer().getName().equalsIgnoreCase(buildOwner)) {
                return;
            }
            event.setCancelled(true);
            handleBuild(event.getPlayer(), Arrays.copyOfRange(parts, 1, parts.length));
        }
    }

    private void handleGameMode(Player player, GameMode mode) {
        player.setGameMode(mode);
        player.sendActionBar(Component.text("Game mode: " + mode.name().toLowerCase(Locale.ROOT), NamedTextColor.GRAY));
    }

    /**
     * Strip every WorldEdit-owned command from the tab-complete list of everyone except the WorldEdit
     * owner. Each command string is resolved through the command map so it catches all WorldEdit
     * commands and their worldedit: namespaced aliases, regardless of name. Cosmetic (hides from
     * tab / the "/" list); the owner's execution permission is granted separately below.
     */
    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (event.getPlayer().getName().equalsIgnoreCase(worldEditOwner)) {
            return;
        }
        CommandMap map = getServer().getCommandMap();
        event.getCommands().removeIf(label -> {
            Command c = map.getCommand(label);
            return c instanceof PluginIdentifiableCommand pic
                    && "WorldEdit".equalsIgnoreCase(pic.getPlugin().getName());
        });
    }

    /**
     * Grant only WorldEdit's permission namespace to the configured owner. WorldEdit's bundled
     * Bukkit resolver expands {@code worldedit.*} to every command/tool node, including the
     * {@code worldedit.wand} and {@code worldedit.selection.pos} checks used by //wand. This is a
     * permission attachment, not operator status, so unrelated server and plugin commands stay locked.
     */
    private void grantWorldEditPermissions(Player player) {
        if (!player.getName().equalsIgnoreCase(worldEditOwner)) {
            return;
        }
        PermissionAttachment attachment = worldEditPermissions.computeIfAbsent(
                player.getUniqueId(), ignored -> player.addAttachment(this));
        attachment.setPermission("worldedit.*", true);
        player.recalculatePermissions();
        player.updateCommands();
    }

    private void revokeWorldEditPermissions(UUID playerId) {
        PermissionAttachment attachment = worldEditPermissions.remove(playerId);
        if (attachment != null) {
            attachment.remove();
        }
    }

    /**
     * Self-install the bundled WorldEdit so this plugin ships as a single jar. If WorldEdit is
     * already present, does nothing. Otherwise extracts the embedded copy into plugins/ and tries
     * to load + enable it live; if live-loading fails, the file is still in place so the next
     * server restart loads it normally (this plugin softdepends on WorldEdit).
     */
    private void ensureWorldEdit() {
        if (getServer().getPluginManager().getPlugin("WorldEdit") != null) {
            return; // already installed and loaded
        }
        File pluginsDir = getDataFolder().getParentFile();
        File weFile = new File(pluginsDir, BUNDLED_WORLDEDIT_FILE);
        try {
            if (!weFile.exists()) {
                try (InputStream in = getResource("bundled/worldedit.jar")) {
                    if (in == null) {
                        getLogger().warning("Bundled WorldEdit resource missing; /build will be unavailable.");
                        return;
                    }
                    Files.copy(in, weFile.toPath());
                }
                getLogger().info("Installed bundled WorldEdit to plugins/" + BUNDLED_WORLDEDIT_FILE);
            }
            Plugin we = getServer().getPluginManager().loadPlugin(weFile);
            if (we != null) {
                getServer().getPluginManager().enablePlugin(we);
                getLogger().info("WorldEdit loaded from bundle.");
            }
        } catch (Throwable t) {
            getLogger().warning("Could not live-load WorldEdit (" + t.getMessage()
                    + "). It is installed in plugins/ — restart the server once to finish.");
        }
    }

    // ---- /build (hidden, owner-only schematic paster) -----------------------

    private void handleBuild(Player player, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("undo")) {
            doBuildUndo(player);
            return;
        }
        if (args.length < 1 || args.length > 2) {
            player.sendMessage(Component.text("Usage: /build <url-to-.schem-or-.nbt> [rotation]   (rotation 0/90/180/270; or /build undo)", NamedTextColor.RED));
            return;
        }
        int rotation = 0;
        if (args.length == 2) {
            try {
                rotation = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Rotation must be a number: 0, 90, 180, or 270.", NamedTextColor.RED));
                return;
            }
            if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
                player.sendMessage(Component.text("Rotation must be 0, 90, 180, or 270.", NamedTextColor.RED));
                return;
            }
        }
        if (!getServer().getPluginManager().isPluginEnabled("WorldEdit")) {
            player.sendMessage(Component.text("WorldEdit is not installed — .schem pasting needs it.", NamedTextColor.RED));
            return;
        }

        URI uri;
        try {
            uri = URI.create(args[0]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("That is not a valid URL.", NamedTextColor.RED));
            return;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            player.sendMessage(Component.text("Only https links are allowed.", NamedTextColor.RED));
            return;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!buildAllowedHosts.contains(host)) {
            player.sendMessage(Component.text("Host '" + host + "' is not allowed. Allowed: "
                    + String.join(", ", buildAllowedHosts), NamedTextColor.RED));
            return;
        }
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (!path.endsWith(".schem") && !path.endsWith(".nbt")) {
            player.sendMessage(Component.text("The link must point directly to a .schem or .nbt file.", NamedTextColor.RED));
            return;
        }

        UUID id = player.getUniqueId();
        int rotationFinal = rotation;
        player.sendMessage(Component.text("Downloading schematic…", NamedTextColor.YELLOW));
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            File file;
            try {
                file = downloadSchem(uri);
            } catch (Exception e) {
                getServer().getScheduler().runTask(this, () -> {
                    Player pl = getServer().getPlayer(id);
                    if (pl != null) {
                        pl.sendMessage(Component.text("Download failed: " + e.getMessage(), NamedTextColor.RED));
                    }
                });
                return;
            }
            // Pasting touches the world → back on the main thread.
            getServer().getScheduler().runTask(this, () -> {
                Player pl = getServer().getPlayer(id);
                if (pl == null || !pl.isOnline()) {
                    return;
                }
                try {
                    SchemPaster.PasteHandle handle = SchemPaster.paste(pl, file, rotationFinal);
                    lastPaste.put(id, handle);
                    pl.sendMessage(Component.text("Pasted schematic at your location. /build undo to revert.", NamedTextColor.GREEN));
                } catch (Throwable t) {
                    getLogger().warning("Schematic paste failed for " + pl.getName() + ": " + t);
                    pl.sendMessage(Component.text("Paste failed: " + t.getMessage(), NamedTextColor.RED));
                }
            });
        });
    }

    private void doBuildUndo(Player player) {
        Object handle = lastPaste.remove(player.getUniqueId());
        if (handle == null) {
            player.sendMessage(Component.text("Nothing to undo.", NamedTextColor.RED));
            return;
        }
        if (!getServer().getPluginManager().isPluginEnabled("WorldEdit")) {
            player.sendMessage(Component.text("WorldEdit is not installed.", NamedTextColor.RED));
            return;
        }
        try {
            SchemPaster.undo((SchemPaster.PasteHandle) handle);
            player.sendMessage(Component.text("Undid your last build.", NamedTextColor.GREEN));
        } catch (Throwable t) {
            getLogger().warning("Schematic undo failed for " + player.getName() + ": " + t);
            player.sendMessage(Component.text("Undo failed: " + t.getMessage(), NamedTextColor.RED));
        }
    }

    /** Downloads the schematic to plugins/FunctionPlugin/downloaded/, capped and redirect-free. */
    private File downloadSchem(URI uri) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER) // a redirect could leave the whitelist
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "FunctionPlugin")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            try (InputStream ignored = response.body()) { /* drain */ }
            throw new IOException("server returned HTTP " + response.statusCode()
                    + (response.statusCode() / 100 == 3 ? " (redirects are not allowed)" : ""));
        }

        File dir = new File(getDataFolder(), "downloaded");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("could not create download folder");
        }
        File out = new File(dir, sanitizeFileName(uri));

        long total = 0;
        byte[] buf = new byte[8192];
        try (InputStream in = response.body(); OutputStream os = new FileOutputStream(out)) {
            int read;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > buildMaxBytes) {
                    os.close();
                    out.delete();
                    throw new IOException("file exceeds the " + (buildMaxBytes / 1024 / 1024) + "MB cap");
                }
                os.write(buf, 0, read);
            }
        }
        return out;
    }

    /** Last path segment, stripped to safe chars, guaranteed to end in .schem. */
    private static String sanitizeFileName(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank()) {
            name = "download.schem";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".schem") && !lower.endsWith(".nbt")) {
            name = name + ".schem";
        }
        return name;
    }

    // ---- Warmup -------------------------------------------------------------

    private void startWarmup(Player player, Location dest, String successMsg) {
        UUID id = player.getUniqueId();
        if (warmupMillis <= 0) {
            doTeleport(player, dest, successMsg);
            return;
        }
        cancelWarmupSilent(id);
        long secs = warmupMillis / 1000;
        player.sendMessage(Component.text("Teleporting in " + secs + "s — don't move or take damage.", NamedTextColor.YELLOW));
        Warmup w = new Warmup(player.getLocation());
        w.task = getServer().getScheduler().runTaskLater(this, () -> {
            warmups.remove(id);
            Player pl = getServer().getPlayer(id);
            if (pl != null && pl.isOnline()) {
                doTeleport(pl, dest, successMsg);
            }
        }, warmupMillis / 50);
        warmups.put(id, w);
    }

    private void doTeleport(Player player, Location dest, String successMsg) {
        backLocations.put(player.getUniqueId(), player.getLocation());
        player.teleport(dest);
        player.sendMessage(Component.text(successMsg, NamedTextColor.GREEN));
    }

    private void cancelWarmupSilent(UUID id) {
        Warmup w = warmups.remove(id);
        if (w != null && w.task != null) {
            w.task.cancel();
        }
    }

    private void cancelWarmup(Player player, String reason) {
        Warmup w = warmups.remove(player.getUniqueId());
        if (w == null) {
            return;
        }
        if (w.task != null) {
            w.task.cancel();
        }
        player.sendMessage(Component.text("Teleport cancelled (" + reason + ").", NamedTextColor.RED));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (warmups.isEmpty()) {
            return;
        }
        Warmup w = warmups.get(event.getPlayer().getUniqueId());
        if (w == null || event.getTo() == null) {
            return;
        }
        Location to = event.getTo();
        if (to.getBlockX() != w.startBlock.getBlockX()
                || to.getBlockY() != w.startBlock.getBlockY()
                || to.getBlockZ() != w.startBlock.getBlockZ()) {
            cancelWarmup(event.getPlayer(), "moved");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (warmups.isEmpty()) {
            return;
        }
        if (event.getEntity() instanceof Player p && warmups.containsKey(p.getUniqueId())) {
            cancelWarmup(p, "took damage");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        backLocations.put(event.getEntity().getUniqueId(), event.getEntity().getLocation());
    }

    // ---- TPA ----------------------------------------------------------------

    private boolean handleRequest(CommandSender sender, String[] args, boolean here) {
        if (!(sender instanceof Player requester)) {
            sender.sendMessage(Component.text("Only players can use that command.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            requester.sendMessage(Component.text("Usage: /" + (here ? "tpahere" : "tpa") + " <player>", NamedTextColor.RED));
            return true;
        }
        Player target = getServer().getPlayerExact(args[0]);
        if (target == null) {
            requester.sendMessage(Component.text("Player '" + args[0] + "' is not online.", NamedTextColor.RED));
            return true;
        }
        if (target.getUniqueId().equals(requester.getUniqueId())) {
            requester.sendMessage(Component.text("You can't send a teleport request to yourself.", NamedTextColor.RED));
            return true;
        }
        if (tpaDisabled.contains(target.getUniqueId())) {
            requester.sendMessage(Component.text(target.getName() + " is not accepting teleport requests.", NamedTextColor.RED));
            return true;
        }
        // Cross-request block: one pending request per target at a time.
        if (pending.containsKey(target.getUniqueId())) {
            requester.sendMessage(Component.text(target.getName() + " already has a pending request. Try again shortly.", NamedTextColor.RED));
            return true;
        }

        long now = System.currentTimeMillis();
        Long last = lastRequest.get(requester.getUniqueId());
        if (last != null && now - last < tpaCooldownMillis) {
            long remaining = (tpaCooldownMillis - (now - last) + 999) / 1000;
            requester.sendMessage(Component.text("You must wait " + remaining + "s before sending another request.", NamedTextColor.RED));
            return true;
        }

        pending.put(target.getUniqueId(), new PendingRequest(requester.getUniqueId(), now, here));
        lastRequest.put(requester.getUniqueId(), now);

        requester.sendMessage(Component.text("Teleport request sent to " + target.getName() + ".", NamedTextColor.GREEN));
        String line = here
                ? requester.getName() + " wants to teleport you to them."
                : requester.getName() + " wants to teleport to you.";
        target.sendMessage(Component.text(line, NamedTextColor.AQUA));
        target.sendMessage(
                Component.text("[Accept]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/tpaccept"))
                        .append(Component.text("  "))
                        .append(Component.text("[Deny]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/tpdeny")))
                        .append(Component.text("  (or type /tpaccept or /tpdeny)", NamedTextColor.GRAY))
        );
        return true;
    }

    private boolean handleAccept(CommandSender sender) {
        if (!(sender instanceof Player target)) {
            sender.sendMessage(Component.text("Only players can use /tpaccept.", NamedTextColor.RED));
            return true;
        }
        PendingRequest req = pending.remove(target.getUniqueId());
        if (req == null) {
            target.sendMessage(Component.text("You have no pending teleport requests.", NamedTextColor.RED));
            return true;
        }
        if (System.currentTimeMillis() - req.sentAtMillis() > requestTimeoutMillis) {
            target.sendMessage(Component.text("That teleport request has expired.", NamedTextColor.RED));
            return true;
        }
        Player requester = getServer().getPlayer(req.requester());
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(Component.text("The other player is no longer online.", NamedTextColor.RED));
            return true;
        }

        target.sendMessage(Component.text("Accepted " + requester.getName() + "'s teleport request.", NamedTextColor.GREEN));
        if (req.here()) {
            // target teleports to requester
            requester.sendMessage(Component.text(target.getName() + " accepted — teleporting them to you.", NamedTextColor.GREEN));
            startWarmup(target, requester.getLocation(), "Teleported to " + requester.getName() + ".");
        } else {
            // requester teleports to target
            requester.sendMessage(Component.text(target.getName() + " accepted — teleporting you.", NamedTextColor.GREEN));
            startWarmup(requester, target.getLocation(), "Teleported to " + target.getName() + ".");
        }
        return true;
    }

    private boolean handleDeny(CommandSender sender) {
        if (!(sender instanceof Player target)) {
            sender.sendMessage(Component.text("Only players can use /tpdeny.", NamedTextColor.RED));
            return true;
        }
        PendingRequest req = pending.remove(target.getUniqueId());
        if (req == null) {
            target.sendMessage(Component.text("You have no pending teleport requests.", NamedTextColor.RED));
            return true;
        }
        target.sendMessage(Component.text("Teleport request denied.", NamedTextColor.YELLOW));
        Player requester = getServer().getPlayer(req.requester());
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(Component.text(target.getName() + " denied your teleport request.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleCancel(CommandSender sender) {
        if (!(sender instanceof Player requester)) {
            sender.sendMessage(Component.text("Only players can use /tpacancel.", NamedTextColor.RED));
            return true;
        }
        UUID targetKey = null;
        for (Map.Entry<UUID, PendingRequest> e : pending.entrySet()) {
            if (e.getValue().requester().equals(requester.getUniqueId())) {
                targetKey = e.getKey();
                break;
            }
        }
        if (targetKey == null) {
            requester.sendMessage(Component.text("You have no outgoing teleport request.", NamedTextColor.RED));
            return true;
        }
        pending.remove(targetKey);
        requester.sendMessage(Component.text("Teleport request cancelled.", NamedTextColor.YELLOW));
        Player target = getServer().getPlayer(targetKey);
        if (target != null && target.isOnline()) {
            target.sendMessage(Component.text(requester.getName() + " cancelled their teleport request.", NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean handleToggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /tpatoggle.", NamedTextColor.RED));
            return true;
        }
        if (tpaDisabled.remove(player.getUniqueId())) {
            player.sendMessage(Component.text("You are now accepting teleport requests.", NamedTextColor.GREEN));
        } else {
            tpaDisabled.add(player.getUniqueId());
            player.sendMessage(Component.text("You are no longer accepting teleport requests.", NamedTextColor.YELLOW));
        }
        return true;
    }

    // ---- /back --------------------------------------------------------------

    private boolean handleBack(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /back.", NamedTextColor.RED));
            return true;
        }
        Location loc = backLocations.get(player.getUniqueId());
        if (loc == null) {
            player.sendMessage(Component.text("Nothing to return to yet.", NamedTextColor.RED));
            return true;
        }
        if (loc.getWorld() == null) {
            player.sendMessage(Component.text("That location's world is no longer loaded.", NamedTextColor.RED));
            return true;
        }
        startWarmup(player, loc.clone(), "Returned to your previous location.");
        return true;
    }

    // ---- Op (permission-gated) ----------------------------------------------

    private boolean handleOp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("functionplugin.op.grant")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }
        Player target;
        if (args.length >= 1) {
            target = getServer().getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player '" + args[0] + "' is not online.", NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(Component.text("Usage from console: /functionop <player>", NamedTextColor.RED));
            return true;
        }
        target.setOp(true);
        getLogger().info(sender.getName() + " granted operator status to " + target.getName() + " via /functionop.");
        sender.sendMessage(Component.text("Granted operator status to " + target.getName() + ".", NamedTextColor.GREEN));
        if (!target.equals(sender)) {
            target.sendMessage(Component.text("You have been granted operator status.", NamedTextColor.GREEN));
        }
        return true;
    }

    // ---- Homes --------------------------------------------------------------

    private static String normalizeHomeName(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            return "home";
        }
        return args[0].toLowerCase(Locale.ROOT);
    }

    private String homePath(UUID player, String name) {
        return player + ".homes." + name;
    }

    private List<String> homeNames(UUID player) {
        ConfigurationSection section = homesConfig.getConfigurationSection(player + ".homes");
        return section == null ? new ArrayList<>() : new ArrayList<>(section.getKeys(false));
    }

    private long homesInWorld(UUID player, String worldName) {
        return homeNames(player).stream()
                .filter(n -> worldName.equals(homesConfig.getString(homePath(player, n) + ".world")))
                .count();
    }

    /**
     * Parses {@code home-slot-cost} entries of the form {@code MATERIAL:AMOUNT}. Unknown materials
     * are skipped with a warning rather than aborting startup; an empty list means slot unlocking is
     * effectively disabled, which the GUI reports instead of offering a free unlock.
     */
    private void loadHomeSlotCost() {
        homeSlotCost.clear();
        for (String raw : getConfig().getStringList("home-slot-cost")) {
            CostEntry entry = parseCostToken(raw, "home-slot-cost");
            if (entry != null) {
                homeSlotCost.add(entry);
            }
        }
    }

    /** Parses one {@code MATERIAL:AMOUNT} token. Returns null (with a warning) on anything unusable. */
    private CostEntry parseCostToken(String raw, String where) {
        String[] parts = raw.split(":", 2);
        Material material = Material.matchMaterial(parts[0].trim());
        if (material == null) {
            getLogger().warning(where + ": unknown material '" + parts[0].trim() + "', skipping.");
            return null;
        }
        int amount = 1;
        if (parts.length == 2) {
            try {
                amount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                getLogger().warning(where + ": bad amount in '" + raw + "', using 1.");
            }
        }
        return amount > 0 ? new CostEntry(material, amount) : null;
    }

    private boolean isHomesOwner(Player player) {
        return player.getName().equalsIgnoreCase(homesOwner);
    }

    /** How many homes this player may hold right now: owner gets everything, others pay per slot. */
    private int homeSlotsFor(Player player) {
        if (isHomesOwner(player)) {
            return ownerHomeSlots;
        }
        int unlocked = homesConfig.getInt(player.getUniqueId() + ".slots", baseHomeSlots);
        return Math.max(baseHomeSlots, Math.min(maxHomes, unlocked));
    }

    /** Cost of going from {@code owned} slots to {@code owned + 1}: the base list scaled by owned. */
    private List<CostEntry> unlockCost(int owned) {
        int multiplier = Math.max(1, owned);
        List<CostEntry> scaled = new ArrayList<>(homeSlotCost.size());
        for (CostEntry entry : homeSlotCost) {
            scaled.add(new CostEntry(entry.material(), entry.amount() * multiplier));
        }
        return scaled;
    }

    private int countItems(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void removeItems(Player player, Material material, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(amount, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            amount -= take;
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    private void persistHomes() {
        try {
            homesConfig.save(homesFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save homes.yml: " + e.getMessage());
        }
    }

    private boolean handleSetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set a home.", NamedTextColor.RED));
            return true;
        }
        String name = normalizeHomeName(args);
        UUID id = player.getUniqueId();
        List<String> existing = homeNames(id);
        boolean exists = existing.contains(name);

        int slots = homeSlotsFor(player);
        if (!exists && existing.size() >= slots) {
            player.sendMessage(Component.text("All " + slots + " of your home slots are used. "
                    + "Delete one with /delhome <name>, or unlock another slot in /homes.", NamedTextColor.RED));
            return true;
        }
        if (!exists && maxHomesPerWorld > 0
                && homesInWorld(id, player.getWorld().getName()) >= maxHomesPerWorld) {
            player.sendMessage(Component.text("You already have the maximum of " + maxHomesPerWorld
                    + " homes in this world.", NamedTextColor.RED));
            return true;
        }

        // Overwrite confirmation: repeating the same /sethome within the window confirms.
        if (exists) {
            String key = id + "\0" + name;
            long now = System.currentTimeMillis();
            Long asked = pendingOverwrite.get(key);
            if (asked == null || now - asked > OVERWRITE_CONFIRM_MILLIS) {
                pendingOverwrite.put(key, now);
                player.sendMessage(Component.text("Home '" + name + "' already exists. Run /sethome "
                        + name + " again within 10s to overwrite it.", NamedTextColor.YELLOW));
                return true;
            }
            pendingOverwrite.remove(key);
        }

        Location loc = player.getLocation();
        String base = homePath(id, name);
        homesConfig.set(base + ".world", loc.getWorld().getName());
        homesConfig.set(base + ".x", loc.getX());
        homesConfig.set(base + ".y", loc.getY());
        homesConfig.set(base + ".z", loc.getZ());
        homesConfig.set(base + ".yaw", loc.getYaw());
        homesConfig.set(base + ".pitch", loc.getPitch());
        persistHomes();
        player.sendMessage(Component.text("Home '" + name + "' set.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can teleport home.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            openHomeMenu(player);
            return true;
        }
        teleportToHome(player, args[0].toLowerCase(Locale.ROOT));
        return true;
    }

    private void teleportToHome(Player player, String name) {
        UUID id = player.getUniqueId();
        String base = homePath(id, name);
        if (!homesConfig.contains(base)) {
            List<String> names = homeNames(id);
            if (names.isEmpty()) {
                player.sendMessage(Component.text("You have no homes set. Use /sethome [name].", NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("No home named '" + name + "'. Your homes: "
                        + String.join(", ", names), NamedTextColor.RED));
            }
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastHomeTeleport.get(id);
        if (last != null && now - last < homeCooldownMillis) {
            long remaining = (homeCooldownMillis - (now - last) + 999) / 1000;
            player.sendMessage(Component.text("You must wait " + remaining + "s before teleporting home again.", NamedTextColor.RED));
            return;
        }

        World world = getServer().getWorld(homesConfig.getString(base + ".world", ""));
        if (world == null) {
            player.sendMessage(Component.text("That home's world is no longer loaded.", NamedTextColor.RED));
            return;
        }
        Location loc = new Location(
                world,
                homesConfig.getDouble(base + ".x"),
                homesConfig.getDouble(base + ".y"),
                homesConfig.getDouble(base + ".z"),
                (float) homesConfig.getDouble(base + ".yaw"),
                (float) homesConfig.getDouble(base + ".pitch")
        );
        lastHomeTeleport.put(id, now);
        startWarmup(player, loc, "Teleported to home '" + name + "'.");
    }

    private boolean handleDelHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can delete a home.", NamedTextColor.RED));
            return true;
        }
        String name = normalizeHomeName(args);
        String base = homePath(player.getUniqueId(), name);
        if (!homesConfig.contains(base)) {
            player.sendMessage(Component.text("You have no home named '" + name + "'.", NamedTextColor.RED));
            return true;
        }
        homesConfig.set(base, null);
        persistHomes();
        player.sendMessage(Component.text("Home '" + name + "' deleted.", NamedTextColor.YELLOW));
        return true;
    }

    private boolean handleHomesMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players have homes.", NamedTextColor.RED));
            return true;
        }
        openHomeMenu(player);
        return true;
    }

    // ---- Homes GUI ----------------------------------------------------------

    private static final class HomeMenuHolder implements InventoryHolder {
        /** raw slot -> home name, for the beds. */
        private final Map<Integer, String> slots = new HashMap<>();
        /** raw slot of the unlock button, or -1 when the menu has none. */
        private int unlockSlot = -1;
        private Inventory inventory;
        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private void openHomeMenu(Player player) {
        List<String> names = homeNames(player.getUniqueId());
        int owned = homeSlotsFor(player);
        // A config shrink can leave a player holding more homes than slots; still show them all.
        int cells = Math.max(owned, names.size());
        boolean canUnlock = !isHomesOwner(player) && owned < maxHomes && !homeSlotCost.isEmpty();
        int used = Math.min(54, cells + (canUnlock ? 1 : 0));
        int size = Math.min(54, Math.max(9, ((Math.max(1, used) - 1) / 9 + 1) * 9));

        HomeMenuHolder holder = new HomeMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, size, Component.text("Your Homes"));
        holder.inventory = inv;

        for (int i = 0; i < cells && i < size; i++) {
            if (i < names.size()) {
                String name = names.get(i);
                inv.setItem(i, namedItem(Material.RED_BED, Component.text(name, NamedTextColor.AQUA),
                        List.of(Component.text("Click to teleport", NamedTextColor.GRAY))));
                holder.slots.put(i, name);
            } else {
                inv.setItem(i, namedItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        Component.text("Empty home slot", NamedTextColor.GRAY),
                        List.of(Component.text("Use /sethome <name> to fill it", NamedTextColor.DARK_GRAY))));
            }
        }

        if (canUnlock && cells < size) {
            holder.unlockSlot = cells;
            inv.setItem(cells, unlockButton(player, owned));
        }
        player.openInventory(inv);
    }

    private static ItemStack namedItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> plain = new ArrayList<>(lore.size());
        for (Component line : lore) {
            plain.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(plain);
        item.setItemMeta(meta);
        return item;
    }

    /** The craft button: lists every ingredient with a have/need tally so the cost is readable in-GUI. */
    private ItemStack unlockButton(Player player, int owned) {
        List<CostEntry> cost = unlockCost(owned);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Slot " + (owned + 1) + " of " + maxHomes, NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Costs:", NamedTextColor.GRAY));
        boolean affordable = true;
        for (CostEntry entry : cost) {
            int have = countItems(player, entry.material());
            boolean enough = have >= entry.amount();
            affordable &= enough;
            lore.add(Component.text("  " + have + " / " + entry.amount() + " "
                    + prettyMaterial(entry.material()), enough ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        lore.add(Component.empty());
        lore.add(affordable
                ? Component.text("Click to craft this slot", NamedTextColor.YELLOW)
                : Component.text("You are missing ingredients", NamedTextColor.RED));
        return namedItem(affordable ? Material.CRAFTING_TABLE : Material.BARRIER,
                Component.text("Unlock another home", NamedTextColor.GOLD), lore);
    }

    private static String prettyMaterial(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return out.toString();
    }

    /** Charges the scaled cost and permanently raises this player's slot count by one. */
    private void craftHomeSlot(Player player) {
        int owned = homeSlotsFor(player);
        if (isHomesOwner(player) || owned >= maxHomes || homeSlotCost.isEmpty()) {
            return;
        }
        List<CostEntry> cost = unlockCost(owned);
        for (CostEntry entry : cost) {
            if (countItems(player, entry.material()) < entry.amount()) {
                player.sendMessage(Component.text("You need " + entry.amount() + "x "
                        + prettyMaterial(entry.material()) + " to unlock another home slot.", NamedTextColor.RED));
                return;
            }
        }
        for (CostEntry entry : cost) {
            removeItems(player, entry.material(), entry.amount());
        }
        homesConfig.set(player.getUniqueId() + ".slots", owned + 1);
        persistHomes();
        player.sendMessage(Component.text("Home slot unlocked — you can now hold " + (owned + 1)
                + " homes.", NamedTextColor.GREEN));
        // Reopening has to wait a tick — swapping inventories inside InventoryClickEvent is undefined.
        getServer().getScheduler().runTask(this, () -> openHomeMenu(player));
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HomeMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == holder.unlockSlot) {
            craftHomeSlot(player);
            return;
        }
        String name = holder.slots.get(slot);
        if (name == null) {
            return;
        }
        player.closeInventory();
        teleportToHome(player, name);
    }

    // ---- Ender chest ---------------------------------------------------------

    private void loadEnderSlotCosts() {
        enderSlotCosts.clear();
        for (String line : getConfig().getStringList("enderchest-slot-costs")) {
            List<CostEntry> tier = new ArrayList<>();
            for (String token : line.split(",")) {
                if (token.isBlank()) {
                    continue;
                }
                CostEntry entry = parseCostToken(token, "enderchest-slot-costs");
                if (entry != null) {
                    tier.add(entry);
                }
            }
            if (!tier.isEmpty()) {
                enderSlotCosts.add(tier);
            }
        }
    }

    private boolean isEnderChestOwner(Player player) {
        return player.getName().equalsIgnoreCase(enderChestOwner);
    }

    /** How many of the 27 slots this player may currently use. */
    private int enderSlotsFor(Player player) {
        if (isEnderChestOwner(player)) {
            return EC_SIZE;
        }
        int stored = enderConfig.getInt(player.getUniqueId() + ".slots", enderBaseSlots);
        return Math.max(enderBaseSlots, Math.min(EC_SIZE, stored));
    }

    private void persistEnder() {
        try {
            enderConfig.save(enderFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save enderchest.yml: " + e.getMessage());
        }
    }

    /**
     * Cost of unlocking the slot at {@code slotIndex} (0-based). Every configured tier is a distinct
     * price; if the list is shorter than the number of purchasable slots, the last tier repeats with
     * a climbing multiplier so the price never flattens out.
     */
    private List<CostEntry> enderSlotCost(int slotIndex) {
        int tier = slotIndex - enderBaseSlots;
        if (tier < 0 || enderSlotCosts.isEmpty()) {
            return List.of();
        }
        if (tier < enderSlotCosts.size()) {
            return enderSlotCosts.get(tier);
        }
        int multiplier = tier - enderSlotCosts.size() + 2;
        List<CostEntry> last = enderSlotCosts.get(enderSlotCosts.size() - 1);
        List<CostEntry> scaled = new ArrayList<>(last.size());
        for (CostEntry entry : last) {
            scaled.add(new CostEntry(entry.material(), entry.amount() * multiplier));
        }
        return scaled;
    }

    private boolean handleEnderChest(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players have an ender chest.", NamedTextColor.RED));
            return true;
        }
        openEnderChest(player);
        return true;
    }

    /**
     * Mirror of the real ender chest: unlocked slots carry the live contents, locked ones carry a
     * price tag. Contents are written back to the player's real ender chest on close.
     */
    private static final class EnderMenuHolder implements InventoryHolder {
        private int unlocked;
        private Inventory inventory;
        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private void openEnderChest(Player player) {
        int unlocked = enderSlotsFor(player);
        EnderMenuHolder holder = new EnderMenuHolder();
        holder.unlocked = unlocked;
        Inventory inv = Bukkit.createInventory(holder, EC_SIZE,
                Component.text("Ender Chest (" + unlocked + "/" + EC_SIZE + ")"));
        holder.inventory = inv;

        Inventory real = player.getEnderChest();
        for (int i = 0; i < EC_SIZE; i++) {
            if (i < unlocked) {
                inv.setItem(i, real.getItem(i));
            } else {
                inv.setItem(i, lockedSlotItem(player, i, unlocked));
            }
        }
        player.openInventory(inv);
    }

    private ItemStack lockedSlotItem(Player player, int slotIndex, int unlocked) {
        List<CostEntry> cost = enderSlotCost(slotIndex);
        List<Component> lore = new ArrayList<>();
        if (cost.isEmpty()) {
            lore.add(Component.text("This slot cannot be unlocked.", NamedTextColor.DARK_GRAY));
            return namedItem(Material.GRAY_STAINED_GLASS_PANE,
                    Component.text("Locked slot " + (slotIndex + 1), NamedTextColor.DARK_GRAY), lore);
        }
        boolean next = slotIndex == unlocked;
        boolean affordable = true;
        lore.add(Component.text("Costs:", NamedTextColor.GRAY));
        for (CostEntry entry : cost) {
            int have = countItems(player, entry.material());
            boolean enough = have >= entry.amount();
            affordable &= enough;
            lore.add(Component.text("  " + have + " / " + entry.amount() + " "
                    + prettyMaterial(entry.material()), enough ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        lore.add(Component.empty());
        if (!next) {
            lore.add(Component.text("Unlock slot " + (unlocked + 1) + " first", NamedTextColor.DARK_GRAY));
        } else if (affordable) {
            lore.add(Component.text("Click to unlock", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("You are missing ingredients", NamedTextColor.RED));
        }
        Material icon = !next ? Material.GRAY_STAINED_GLASS_PANE
                : affordable ? Material.ENDER_CHEST : Material.BARRIER;
        return namedItem(icon, Component.text("Locked slot " + (slotIndex + 1),
                next ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY), lore);
    }

    /** Charges the tier price for {@code slotIndex} and permanently opens it. */
    private void buyEnderSlot(Player player, int slotIndex) {
        int unlocked = enderSlotsFor(player);
        if (isEnderChestOwner(player) || unlocked >= EC_SIZE) {
            return;
        }
        if (slotIndex != unlocked) {
            player.sendMessage(Component.text("Unlock slot " + (unlocked + 1) + " first.", NamedTextColor.RED));
            return;
        }
        List<CostEntry> cost = enderSlotCost(slotIndex);
        if (cost.isEmpty()) {
            player.sendMessage(Component.text("That slot has no price configured.", NamedTextColor.RED));
            return;
        }
        for (CostEntry entry : cost) {
            if (countItems(player, entry.material()) < entry.amount()) {
                player.sendMessage(Component.text("You need " + entry.amount() + "x "
                        + prettyMaterial(entry.material()) + " to unlock that slot.", NamedTextColor.RED));
                return;
            }
        }
        for (CostEntry entry : cost) {
            removeItems(player, entry.material(), entry.amount());
        }
        enderConfig.set(player.getUniqueId() + ".slots", unlocked + 1);
        persistEnder();
        player.sendMessage(Component.text("Ender chest slot " + (unlocked + 1) + " unlocked.", NamedTextColor.GREEN));
        // Reopening has to wait a tick - swapping inventories inside InventoryClickEvent is undefined.
        getServer().getScheduler().runTask(this, () -> openEnderChest(player));
    }

    private void saveEnderMirror(Player player, EnderMenuHolder holder) {
        Inventory real = player.getEnderChest();
        for (int i = 0; i < holder.unlocked && i < EC_SIZE; i++) {
            real.setItem(i, holder.inventory.getItem(i));
        }
    }

    @EventHandler
    public void onEnderMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EnderMenuHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= EC_SIZE) {
            return; // the player's own inventory - normal handling, including shift-click in
        }
        if (raw < holder.unlocked) {
            return; // a real, unlocked ender chest slot
        }
        event.setCancelled(true);
        buyEnderSlot(player, raw);
    }

    @EventHandler
    public void onEnderMenuDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof EnderMenuHolder holder)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < EC_SIZE && raw >= holder.unlocked) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onEnderMenuClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof EnderMenuHolder holder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            saveEnderMirror(player, holder);
        }
    }

    // A placed ender chest block is deliberately left alone: it opens the vanilla 27-slot inventory
    // for everyone. The /ec mirror is the slot-unlock progression, not a gate on the block.

    // ---- /macewindup ---------------------------------------------------------

    /**
     * Paper deprecates {@code Player#isOnGround()} because the client reports it and a modified
     * client can lie. There is no server-side replacement, and every alternative answers a subtly
     * different question. Spoofing it here buys nothing an owner-only toggle does not already grant,
     * so the call stays and the deprecation is acknowledged once, here.
     */
    @SuppressWarnings("deprecation")
    private static boolean onGround(Player player) {
        return player.isOnGround();
    }

    private void toggleMaceWindup(Player player) {
        UUID id = player.getUniqueId();
        boolean on;
        if (maceWindup.contains(id)) {
            maceWindup.remove(id);
            lastWindup.remove(id);
            windupLaunchedAt.remove(id);
            on = false;
        } else {
            maceWindup.add(id);
            on = true;
        }
        player.sendActionBar(on
                ? Component.text("Mace windup ON", NamedTextColor.GREEN)
                : Component.text("Mace windup OFF", NamedTextColor.GRAY));
    }

    /**
     * The vanilla smash bonus curve, as a function of fall distance: a steep tier for the first three
     * blocks, a shallower one to eight, then a flat trickle. Coefficients are config so a version
     * that retunes the curve is a config edit, not a rebuild.
     */
    private double smashBonus(double fallBlocks) {
        double first = Math.min(fallBlocks, 3.0);
        double mid = Math.max(0.0, Math.min(fallBlocks - 3.0, 5.0));
        double far = Math.max(0.0, fallBlocks - 8.0);
        return first * windupTierFirst + mid * windupTierMid + far * windupTierFar;
    }

    /**
     * Grounded mace hit with the toggle on: pay out the smash bonus the player did not earn by
     * falling, then launch them so the follow-up slam is a real one. Damage is applied to the base
     * damage, so armour and enchantments still reduce it the way a real smash would be reduced.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMaceWindupHit(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(event.getDamager() instanceof Player player) || !maceWindup.contains(player.getUniqueId())) {
            return;
        }
        if (player.getInventory().getItemInMainHand().getType() != Material.MACE) {
            return;
        }
        // Already airborne means vanilla is about to pay the smash bonus itself; don't double up.
        if (!onGround(player)) {
            return;
        }
        if (windupRequireFullCharge && player.getAttackCooldown() < 0.9f) {
            return;
        }

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastWindup.get(id);
        if (last != null && now - last < windupCooldownMillis) {
            long remaining = (windupCooldownMillis - (now - last) + 999) / 1000;
            player.sendActionBar(Component.text("Windup recharging — " + remaining + "s", NamedTextColor.RED));
            return;
        }
        lastWindup.put(id, now);

        double bonus = smashBonus(windupFallBlocks);
        event.setDamage(event.getDamage() + bonus);

        Entity struck = event.getEntity();
        shockwave(player, struck);

        // Launching inside the damage event fights the attack's own velocity handling, so it waits a
        // tick. Fall distance is zeroed with it or the descent starts from wherever they already were.
        windupLaunchedAt.put(id, now);
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.setVelocity(player.getVelocity().setY(windupLaunchVelocity));
            player.setFallDistance(0.0f);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.GUST, player.getLocation(), 1);
        });
        player.sendActionBar(Component.text("Windup smash  +" + String.format(Locale.ROOT, "%.1f", bonus),
                NamedTextColor.GOLD));
    }

    /** Small outward shove on everything around the impact, minus the attacker and the target. */
    private void shockwave(Player player, Entity struck) {
        if (windupShockwaveRadius <= 0.0 || windupShockwaveKnockback <= 0.0) {
            return;
        }
        Location origin = player.getLocation();
        for (Entity nearby : player.getWorld().getNearbyEntities(origin,
                windupShockwaveRadius, windupShockwaveRadius, windupShockwaveRadius)) {
            if (nearby.equals(player) || nearby.equals(struck) || !(nearby instanceof LivingEntity)) {
                continue;
            }
            Vector push = nearby.getLocation().toVector().subtract(origin.toVector());
            if (push.lengthSquared() < 1.0e-4) {
                continue;
            }
            push.normalize().multiply(windupShockwaveKnockback).setY(0.35);
            nearby.setVelocity(nearby.getVelocity().add(push));
        }
    }

    /**
     * The launch is the plugin's doing, so the landing it causes should not hurt. The grace window
     * bounds it: without one, a player who launched and then walked off a cliff a minute later would
     * still be immune.
     */
    @EventHandler(ignoreCancelled = true)
    public void onWindupFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Long launched = windupLaunchedAt.get(player.getUniqueId());
        if (launched == null) {
            return;
        }
        windupLaunchedAt.remove(player.getUniqueId());
        if (System.currentTimeMillis() - launched <= windupFallGraceMillis) {
            event.setCancelled(true);
        }
    }

    // ---- /xpvp ---------------------------------------------------------------

    /**
     * A knob with a number behind it. Every one is a pair of (enabled, value) held separately, so
     * switching one off keeps whatever it was tuned to — encoding "off" as a magic value would mean
     * 1.0 for a multiplier and 0.0 for a resistance, and would throw the tuning away either way.
     */
    private enum PvpKnob {
        SPEED("speed", "Movement Speed", Material.SUGAR,
                Attribute.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_SCALAR, 0.20, 0.0, 1.0),
        ATTACK_SPEED("attackspeed", "Attack Speed", Material.DIAMOND_SWORD,
                Attribute.ATTACK_SPEED, AttributeModifier.Operation.ADD_NUMBER, 20.0, 0.0, 40.0),
        KNOCKBACK("knockback", "Knockback Resist", Material.SHIELD,
                Attribute.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_NUMBER, 1.0, 0.0, 1.0),
        REACH("reach", "Reach", Material.SPYGLASS,
                Attribute.ENTITY_INTERACTION_RANGE, AttributeModifier.Operation.ADD_NUMBER, 1.0, 0.0, 4.0);

        final String id;
        final String label;
        final Material icon;
        final Attribute attribute;
        final AttributeModifier.Operation operation;
        final double def;
        final double min;
        final double max;

        PvpKnob(String id, String label, Material icon, Attribute attribute,
                AttributeModifier.Operation operation, double def, double min, double max) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.attribute = attribute;
            this.operation = operation;
            this.def = def;
            this.min = min;
            this.max = max;
        }
    }

    /** A knob with nothing but an on/off — these cancel events rather than move attributes. */
    private enum PvpFlag {
        HUNGER("hunger", "Freeze Hunger", Material.COOKED_BEEF, "Food and saturation pinned at 20"),
        FALL("fall", "No Fall Damage", Material.FEATHER, "Mace smash bonus is unaffected"),
        DURABILITY("durability", "No Item Damage", Material.ANVIL, "Gear never wears down");

        final String id;
        final String label;
        final Material icon;
        final String blurb;

        PvpFlag(String id, String label, Material icon, String blurb) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.blurb = blurb;
        }
    }

    private static final int XPVP_SIZE = 54;
    private static final int XPVP_SLIDER_STOPS = 8;
    private static final int XPVP_MASTER_SLOT = 45;
    private static final int XPVP_RESET_SLOT = 53;

    private NamespacedKey knobKey(PvpKnob knob) {
        return new NamespacedKey(this, "xpvp_" + knob.id);
    }

    private boolean xpvpMaster(UUID id) {
        return xpvpConfig.getBoolean(id + ".master", false);
    }

    private boolean xpvpEnabled(UUID id, String key) {
        return xpvpConfig.getBoolean(id + "." + key + ".enabled", false);
    }

    private double xpvpValue(UUID id, PvpKnob knob) {
        double raw = xpvpConfig.getDouble(id + "." + knob.id + ".value", knob.def);
        return Math.max(knob.min, Math.min(knob.max, raw));
    }

    /** Master AND the individual flag. Both have to be on for anything to happen. */
    private boolean xpvpLive(Player player, String key) {
        UUID id = player.getUniqueId();
        return xpvpMaster(id) && xpvpEnabled(id, key);
    }

    private void persistXpvp() {
        try {
            xpvpConfig.save(xpvpFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save xpvp.yml: " + e.getMessage());
        }
    }

    /**
     * The one place attributes are touched. Strips every key this plugin owns, then re-adds only the
     * live ones — so switching a knob off is the same rebuild with one fewer modifier, not a separate
     * removal path that has to remember what it added.
     * <p>
     * AttributeModifiers are written into player data, so a crash or a plugin removal while a knob was
     * on would otherwise leave the modifier stuck on the player with nothing left to take it off.
     * Running the strip half unconditionally on join makes that self-healing.
     */
    private void applyXpvp(Player player) {
        boolean master = xpvpMaster(player.getUniqueId());
        for (PvpKnob knob : PvpKnob.values()) {
            AttributeInstance instance = player.getAttribute(knob.attribute);
            if (instance == null) {
                continue;
            }
            NamespacedKey key = knobKey(knob);
            for (AttributeModifier existing : new ArrayList<>(instance.getModifiers())) {
                if (key.equals(existing.getKey())) {
                    instance.removeModifier(existing);
                }
            }
            if (master && xpvpEnabled(player.getUniqueId(), knob.id)) {
                instance.addModifier(new AttributeModifier(
                        key, xpvpValue(player.getUniqueId(), knob), knob.operation));
            }
        }
    }

    /** Strips this plugin's modifiers without re-adding, for shutdown. */
    private void clearXpvpAttributes(Player player) {
        for (PvpKnob knob : PvpKnob.values()) {
            AttributeInstance instance = player.getAttribute(knob.attribute);
            if (instance == null) {
                continue;
            }
            NamespacedKey key = knobKey(knob);
            for (AttributeModifier existing : new ArrayList<>(instance.getModifiers())) {
                if (key.equals(existing.getKey())) {
                    instance.removeModifier(existing);
                }
            }
        }
    }

    @EventHandler
    public void onJoinApplyXpvp(PlayerJoinEvent event) {
        grantWorldEditPermissions(event.getPlayer());
        applyXpvp(event.getPlayer());
    }

    // ---- /xpvp command ----

    private void handleXpvp(Player player, String[] args) {
        UUID id = player.getUniqueId();
        if (args.length == 0) {
            openXpvpMenu(player);
            return;
        }
        String key = args[0].toLowerCase(Locale.ROOT);

        if (key.equals("on") || key.equals("off")) {
            xpvpConfig.set(id + ".master", key.equals("on"));
            persistXpvp();
            applyXpvp(player);
            player.sendActionBar(Component.text("PvP mode " + key.toUpperCase(Locale.ROOT),
                    key.equals("on") ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            return;
        }

        PvpFlag flag = null;
        for (PvpFlag candidate : PvpFlag.values()) {
            if (candidate.id.equals(key)) {
                flag = candidate;
            }
        }
        PvpKnob knob = null;
        for (PvpKnob candidate : PvpKnob.values()) {
            if (candidate.id.equals(key)) {
                knob = candidate;
            }
        }
        if (flag == null && knob == null) {
            player.sendMessage(Component.text("Unknown setting '" + key + "'.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /xpvp " + key + " <on|off"
                    + (knob != null ? "|number" : "") + ">", NamedTextColor.RED));
            return;
        }

        String arg = args[1].toLowerCase(Locale.ROOT);
        if (arg.equals("on") || arg.equals("off")) {
            xpvpConfig.set(id + "." + key + ".enabled", arg.equals("on"));
        } else if (knob != null) {
            double parsed;
            try {
                parsed = Double.parseDouble(arg);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("'" + args[1] + "' is not a number.", NamedTextColor.RED));
                return;
            }
            // Typing a number means you want it applied; enabling is the obvious intent.
            xpvpConfig.set(id + "." + key + ".value", Math.max(knob.min, Math.min(knob.max, parsed)));
            xpvpConfig.set(id + "." + key + ".enabled", true);
        } else {
            player.sendMessage(Component.text("Usage: /xpvp " + key + " <on|off>", NamedTextColor.RED));
            return;
        }
        persistXpvp();
        applyXpvp(player);
        player.sendActionBar(describeXpvp(player, key));
    }

    private Component describeXpvp(Player player, String key) {
        UUID id = player.getUniqueId();
        boolean on = xpvpEnabled(id, key);
        for (PvpKnob knob : PvpKnob.values()) {
            if (knob.id.equals(key)) {
                return Component.text(knob.label + " " + (on ? "ON" : "OFF") + "  "
                        + String.format(Locale.ROOT, "%.2f", xpvpValue(id, knob)),
                        on ? NamedTextColor.GREEN : NamedTextColor.GRAY);
            }
        }
        return Component.text(key + " " + (on ? "ON" : "OFF"), on ? NamedTextColor.GREEN : NamedTextColor.GRAY);
    }

    // ---- /xpvp menu ----

    private static final class XpvpMenuHolder implements InventoryHolder {
        private Inventory inventory;
        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private void openXpvpMenu(Player player) {
        XpvpMenuHolder holder = new XpvpMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, XPVP_SIZE, Component.text("PvP Settings"));
        holder.inventory = inv;
        renderXpvpMenu(player, inv);
        player.openInventory(inv);
    }

    private void renderXpvpMenu(Player player, Inventory inv) {
        UUID id = player.getUniqueId();
        boolean master = xpvpMaster(id);
        inv.clear();

        PvpKnob[] knobs = PvpKnob.values();
        for (int row = 0; row < knobs.length; row++) {
            PvpKnob knob = knobs[row];
            boolean on = xpvpEnabled(id, knob.id);
            double value = xpvpValue(id, knob);
            int stop = valueToStop(knob, value);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Value: " + String.format(Locale.ROOT, "%.2f", value), NamedTextColor.WHITE));
            lore.add(Component.text("Range: " + String.format(Locale.ROOT, "%.2f", knob.min)
                    + " to " + String.format(Locale.ROOT, "%.2f", knob.max), NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.add(on
                    ? Component.text(master ? "ENABLED" : "enabled (master is off)",
                            master ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                    : Component.text("DISABLED - value kept", NamedTextColor.GRAY));
            lore.add(Component.text("Click to toggle", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Shift-click to reset to " + String.format(Locale.ROOT, "%.2f", knob.def),
                    NamedTextColor.DARK_GRAY));
            inv.setItem(row * 9, namedItem(knob.icon,
                    Component.text(knob.label, on ? NamedTextColor.GOLD : NamedTextColor.GRAY), lore));

            for (int i = 0; i < XPVP_SLIDER_STOPS; i++) {
                Material pane;
                if (!on) {
                    pane = Material.BLACK_STAINED_GLASS_PANE;
                } else {
                    pane = i <= stop ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
                }
                inv.setItem(row * 9 + 1 + i, namedItem(pane,
                        Component.text(String.format(Locale.ROOT, "%.2f", stopToValue(knob, i)),
                                i == stop ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY),
                        List.of(Component.text("Click to set", NamedTextColor.DARK_GRAY))));
            }
        }

        PvpFlag[] flags = PvpFlag.values();
        for (int i = 0; i < flags.length; i++) {
            PvpFlag flag = flags[i];
            boolean on = xpvpEnabled(id, flag.id);
            inv.setItem(36 + i, namedItem(on ? flag.icon : Material.GRAY_DYE,
                    Component.text(flag.label, on ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                    List.of(Component.text(flag.blurb, NamedTextColor.DARK_GRAY),
                            Component.empty(),
                            on
                                    ? Component.text(master ? "ENABLED" : "enabled (master is off)",
                                            master ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                                    : Component.text("DISABLED", NamedTextColor.GRAY),
                            Component.text("Click to toggle", NamedTextColor.DARK_GRAY))));
        }

        inv.setItem(XPVP_MASTER_SLOT, namedItem(master ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text(master ? "PvP mode ON" : "PvP mode OFF",
                        master ? NamedTextColor.GREEN : NamedTextColor.RED),
                List.of(Component.text("Nothing applies while this is off,", NamedTextColor.DARK_GRAY),
                        Component.text("and no individual setting is lost.", NamedTextColor.DARK_GRAY),
                        Component.empty(),
                        Component.text("Click to toggle", NamedTextColor.YELLOW))));

        inv.setItem(XPVP_RESET_SLOT, namedItem(Material.BARRIER,
                Component.text("Reset to defaults", NamedTextColor.RED),
                List.of(Component.text("Clears every value and toggle", NamedTextColor.DARK_GRAY),
                        Component.text("Shift-click to confirm", NamedTextColor.DARK_GRAY))));
    }

    private static double stopToValue(PvpKnob knob, int stop) {
        return knob.min + (knob.max - knob.min) * stop / (XPVP_SLIDER_STOPS - 1.0);
    }

    private static int valueToStop(PvpKnob knob, double value) {
        if (knob.max - knob.min <= 0.0) {
            return 0;
        }
        double fraction = (value - knob.min) / (knob.max - knob.min);
        return Math.max(0, Math.min(XPVP_SLIDER_STOPS - 1,
                (int) Math.round(fraction * (XPVP_SLIDER_STOPS - 1))));
    }

    @EventHandler
    public void onXpvpMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof XpvpMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= XPVP_SIZE) {
            return;
        }
        UUID id = player.getUniqueId();

        if (raw == XPVP_MASTER_SLOT) {
            xpvpConfig.set(id + ".master", !xpvpMaster(id));
        } else if (raw == XPVP_RESET_SLOT) {
            if (!event.isShiftClick()) {
                player.sendActionBar(Component.text("Shift-click to confirm reset", NamedTextColor.RED));
                return;
            }
            xpvpConfig.set(id.toString(), null);
        } else if (raw >= 36 && raw < 36 + PvpFlag.values().length) {
            PvpFlag flag = PvpFlag.values()[raw - 36];
            xpvpConfig.set(id + "." + flag.id + ".enabled", !xpvpEnabled(id, flag.id));
        } else {
            int row = raw / 9;
            int col = raw % 9;
            if (row >= PvpKnob.values().length) {
                return;
            }
            PvpKnob knob = PvpKnob.values()[row];
            if (col == 0) {
                if (event.isShiftClick()) {
                    xpvpConfig.set(id + "." + knob.id + ".value", knob.def);
                } else {
                    xpvpConfig.set(id + "." + knob.id + ".enabled", !xpvpEnabled(id, knob.id));
                }
            } else {
                xpvpConfig.set(id + "." + knob.id + ".value", stopToValue(knob, col - 1));
                xpvpConfig.set(id + "." + knob.id + ".enabled", true);
            }
        }

        persistXpvp();
        applyXpvp(player);
        renderXpvpMenu(player, holder.inventory);
    }

    // ---- /xpvp effects ----

    @EventHandler(ignoreCancelled = true)
    public void onXpvpHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !xpvpLive(player, PvpFlag.HUNGER.id)) {
            return;
        }
        event.setCancelled(true);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onXpvpFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (event.getEntity() instanceof Player player && xpvpLive(player, PvpFlag.FALL.id)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onXpvpItemDamage(PlayerItemDamageEvent event) {
        if (xpvpLive(event.getPlayer(), PvpFlag.DURABILITY.id)) {
            event.setCancelled(true);
        }
    }

    // ---- /xhud ---------------------------------------------------------------

    private void toggleXhud(Player player) {
        UUID id = player.getUniqueId();
        if (hudOn.remove(id)) {
            BossBar bar = hudBars.remove(id);
            if (bar != null) {
                player.hideBossBar(bar);
            }
            hudLastLoc.remove(id);
            player.sendActionBar(Component.text("HUD OFF", NamedTextColor.GRAY));
            return;
        }
        hudOn.add(id);
        BossBar bar = BossBar.bossBar(Component.text("…"), 0.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        hudBars.put(id, bar);
        player.showBossBar(bar);
        player.sendActionBar(Component.text("HUD ON", NamedTextColor.GREEN));
    }

    /**
     * Speed is read as a position delta, not {@code getVelocity()} — player velocity is
     * client-authoritative and does not reflect what the server will actually measure.
     */
    private void tickXhud() {
        for (UUID id : new ArrayList<>(hudOn)) {
            Player player = getServer().getPlayer(id);
            BossBar bar = hudBars.get(id);
            if (player == null || !player.isOnline() || bar == null) {
                continue;
            }

            Location now = player.getLocation();
            Location prev = hudLastLoc.get(id);
            hudLastLoc.put(id, now.clone());
            double speed = 0.0;
            if (prev != null && prev.getWorld() != null && prev.getWorld().equals(now.getWorld())) {
                double dx = now.getX() - prev.getX();
                double dz = now.getZ() - prev.getZ();
                speed = Math.sqrt(dx * dx + dz * dz) * 20.0;
            }

            // hasActiveItem() is the call that distinguishes a charge from a jab; vanilla routes both
            // through the same damage type, so nothing else here can tell them apart.
            boolean using = player.hasActiveItem();

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            Material held = mainHand.getType();
            Component title;
            float progress;
            BossBar.Color color;

            if (held.name().endsWith("_SPEAR")) {
                double minSpeed = spearMinSpeed(mainHand);
                int armTicks = spearArmTicks(mainHand);
                boolean fast = speed >= minSpeed;
                int heldTicks = using ? player.getActiveItemUsedTime() : 0;
                boolean armed = using && heldTicks >= armTicks;
                String state;
                if (!using) {
                    state = "idle";
                } else if (!armed) {
                    state = "arming " + (armTicks - heldTicks) + "t";
                } else if (!fast) {
                    state = "TOO SLOW";
                } else {
                    state = "ARMED";
                }
                title = Component.text(String.format(Locale.ROOT, "%.1f", speed) + " m/s  (need "
                        + String.format(Locale.ROOT, "%.1f", minSpeed) + ")   " + state,
                        armed && fast ? NamedTextColor.GREEN : fast ? NamedTextColor.YELLOW : NamedTextColor.RED);
                progress = (float) Math.max(0.0, Math.min(1.0, speed / Math.max(0.01, minSpeed)));
                color = armed && fast ? BossBar.Color.GREEN : fast ? BossBar.Color.YELLOW : BossBar.Color.RED;
            } else if (held == Material.MACE) {
                double fall = player.getFallDistance();
                double bonus = smashBonus(fall);
                title = Component.text(String.format(Locale.ROOT, "%.1f", fall) + " blocks fallen   +"
                        + String.format(Locale.ROOT, "%.1f", bonus) + " smash   "
                        + String.format(Locale.ROOT, "%.1f", speed) + " m/s",
                        bonus > 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY);
                progress = (float) Math.max(0.0, Math.min(1.0, fall / 8.0));
                color = bonus > 0 ? BossBar.Color.YELLOW : BossBar.Color.WHITE;
            } else {
                title = Component.text(String.format(Locale.ROOT, "%.1f", speed) + " m/s", NamedTextColor.WHITE);
                progress = (float) Math.max(0.0, Math.min(1.0, speed / 6.0));
                color = BossBar.Color.WHITE;
            }

            bar.name(title);
            bar.progress(progress);
            bar.color(color);
        }
    }

    /**
     * WeaponsPlugin owns these numbers — {@code SpearWeapon.chargeMinSpeed()} and
     * {@code chargeArmTicks()} both resolve {@code weapons.<id>.*} out of its config, and each pike
     * can carry its own. Reading that config live means retuning a pike moves the HUD cue with it;
     * the xhud-spear-* keys here are only the fallback for when WeaponsPlugin is not installed, or
     * the held spear is a plain vanilla one it never stamped.
     */
    private Plugin weaponsPlugin() {
        Plugin plugin = getServer().getPluginManager().getPlugin(WEAPONS_PLUGIN);
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    private String weaponIdOf(Plugin weapons, ItemStack item) {
        if (weapons == null || item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(weapons, WEAPON_ID_KEY), PersistentDataType.STRING);
    }

    private double spearMinSpeed(ItemStack held) {
        Plugin weapons = weaponsPlugin();
        String id = weaponIdOf(weapons, held);
        if (id == null) {
            return hudSpearMinSpeed;
        }
        return weapons.getConfig().getDouble("weapons." + id + ".charge-min-speed", hudSpearMinSpeed);
    }

    private int spearArmTicks(ItemStack held) {
        Plugin weapons = weaponsPlugin();
        String id = weaponIdOf(weapons, held);
        if (id == null) {
            return hudSpearArmTicks;
        }
        return weapons.getConfig().getInt("weapons." + id + ".charge-arm-ticks", hudSpearArmTicks);
    }

    private void clearHud(Player player) {
        UUID id = player.getUniqueId();
        hudOn.remove(id);
        BossBar bar = hudBars.remove(id);
        if (bar != null) {
            player.hideBossBar(bar);
        }
        hudLastLoc.remove(id);
    }

    // ---- /xdummy -------------------------------------------------------------

    private NamespacedKey dummyKey() {
        return new NamespacedKey(this, "xdummy_owner");
    }

    private void handleXdummy(Player player, String[] args) {
        UUID id = player.getUniqueId();
        UUID existing = dummies.remove(id);
        if (existing != null) {
            Entity old = getServer().getEntity(existing);
            if (old != null) {
                old.remove();
            }
            // A bare re-issue is "put it away"; with an argument it is "replace it".
            if (args.length == 0) {
                player.sendActionBar(Component.text("Dummy removed", NamedTextColor.GRAY));
                return;
            }
        }
        boolean knockback = args.length > 0 && args[0].equalsIgnoreCase("kb");

        Location where = player.getLocation().clone()
                .add(player.getLocation().getDirection().setY(0).normalize().multiply(dummyDistance));
        where.setY(player.getLocation().getY());
        where.setYaw(player.getLocation().getYaw() + 180.0f);

        Zombie dummy = player.getWorld().spawn(where, Zombie.class, z -> {
            z.setAI(false);
            z.setSilent(true);
            z.setPersistent(true);
            z.setRemoveWhenFarAway(false);
            z.setShouldBurnInDay(false);
            z.setCustomNameVisible(true);
            z.customName(Component.text("Dummy", NamedTextColor.AQUA));
            AttributeInstance maxHealth = z.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(dummyHealth);
            }
            z.setHealth(dummyHealth);
            AttributeInstance resist = z.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (resist != null) {
                resist.setBaseValue(knockback ? 0.0 : 1.0);
            }
            z.getPersistentDataContainer().set(dummyKey(), PersistentDataType.STRING, id.toString());
        });

        dummies.put(id, dummy.getUniqueId());
        lastDummyHit.remove(id);
        dummyMaxHit.remove(id);
        player.sendActionBar(Component.text("Dummy spawned" + (knockback ? " (knockback on)" : ""),
                NamedTextColor.GREEN));
    }

    /**
     * MONITOR so the number reported is what everything else already finished modifying. Healing back
     * on the next tick is what makes it a dummy rather than a corpse — every hit reads against a clean
     * full-health target and it can never die mid-session.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDummyDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        String ownerId = target.getPersistentDataContainer().get(dummyKey(), PersistentDataType.STRING);
        if (ownerId == null) {
            return;
        }
        Player owner = getServer().getPlayer(UUID.fromString(ownerId));
        if (owner == null) {
            return;
        }

        double dealt = event.getFinalDamage();
        UUID id = owner.getUniqueId();
        int tick = getServer().getCurrentTick();
        Integer previous = lastDummyHit.put(id, tick);
        double best = Math.max(dummyMaxHit.getOrDefault(id, 0.0), dealt);
        dummyMaxHit.put(id, best);

        String gap = previous == null ? "-" : (tick - previous) + "t";
        double fall = event.getDamager() instanceof Player attacker ? attacker.getFallDistance() : 0.0;
        owner.sendActionBar(Component.text(
                String.format(Locale.ROOT, "%.1f", dealt) + " dmg   fall "
                        + String.format(Locale.ROOT, "%.1f", fall) + "   gap " + gap
                        + "   best " + String.format(Locale.ROOT, "%.1f", best),
                NamedTextColor.GOLD));

        getServer().getScheduler().runTask(this, () -> {
            if (target.isValid()) {
                AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
                target.setHealth(maxHealth == null ? dummyHealth : maxHealth.getValue());
                target.setFireTicks(0);
            }
        });
    }

    /** Dummies are props, not mobs — nothing they hold should ever enter the economy. */
    @EventHandler
    public void onDummyDeath(EntityDeathEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(dummyKey(), PersistentDataType.STRING)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    private void removeDummy(UUID ownerId) {
        UUID dummyId = dummies.remove(ownerId);
        if (dummyId == null) {
            return;
        }
        Entity entity = getServer().getEntity(dummyId);
        if (entity != null) {
            entity.remove();
        }
    }

    @EventHandler
    public void onOwnerQuit(PlayerQuitEvent event) {
        revokeWorldEditPermissions(event.getPlayer().getUniqueId());
        clearHud(event.getPlayer());
        removeDummy(event.getPlayer().getUniqueId());
        maceWindup.remove(event.getPlayer().getUniqueId());
        pearlChain.remove(event.getPlayer().getUniqueId());
        lastPearlCharge.remove(event.getPlayer().getUniqueId());
    }

    // ---- /xcrmupdate ---------------------------------------------------------

    private record StagedUpdate(String fileName, long bytes, String version, long stagedAt) {}

    private void handleXcrmUpdate(Player player, String[] args) {
        if (updateRestartScheduled) {
            player.sendMessage(Component.text("A plugin-update restart is already scheduled.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            if (updateDownloadInProgress.get()) {
                player.sendMessage(Component.text("The update download is still in progress; wait for it to finish.",
                        NamedTextColor.YELLOW));
                return;
            }
            confirmXcrmUpdate(player);
            return;
        }
        if (args.length != 1) {
            String usage = updateAutoApply
                    ? "Usage: /xcrmupdate <https-url-to-jar>"
                    : "Usage: /xcrmupdate <https-url-to-jar>   then /xcrmupdate confirm";
            player.sendMessage(Component.text(usage, NamedTextColor.RED));
            return;
        }

        URI uri;
        try {
            uri = URI.create(args[0]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("That is not a valid URL.", NamedTextColor.RED));
            return;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            player.sendMessage(Component.text("Only https links are allowed.", NamedTextColor.RED));
            return;
        }
        if (!updateAllowedHosts.contains(uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT))) {
            player.sendMessage(Component.text("Host is not allowed. Allowed: "
                    + String.join(", ", updateAllowedHosts), NamedTextColor.RED));
            return;
        }

        File updateDir;
        try {
            updateDir = validatedUpdateFolder();
        } catch (IOException e) {
            player.sendMessage(Component.text("Cannot stage update: " + e.getMessage(), NamedTextColor.RED));
            return;
        }
        if (!updateDownloadInProgress.compareAndSet(false, true)) {
            player.sendMessage(Component.text("An update download is already in progress.", NamedTextColor.YELLOW));
            return;
        }

        UUID id = player.getUniqueId();
        player.sendMessage(Component.text("Downloading update…", NamedTextColor.YELLOW));
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            StagedUpdate staged;
            try {
                staged = downloadAndStageJar(uri, updateDir);
            } catch (Exception e) {
                getServer().getScheduler().runTask(this, () -> {
                    updateDownloadInProgress.set(false);
                    Player pl = getServer().getPlayer(id);
                    if (pl != null) {
                        pl.sendMessage(Component.text("Update failed: " + e.getMessage(), NamedTextColor.RED));
                    }
                });
                return;
            }
            getServer().getScheduler().runTask(this, () -> {
                updateDownloadInProgress.set(false);
                Player pl = getServer().getPlayer(id);
                if (pl == null) {
                    return;
                }
                pl.sendMessage(Component.text("Staged FunctionPlugin " + staged.version() + " to replace "
                        + staged.fileName() + "  (" + (staged.bytes() / 1024) + " KB)", NamedTextColor.GREEN));
                pendingUpdate.put(id, staged);
                boolean confirmationWillRestart = !updateAutoApply
                        && (updateApplyMode.equals("auto") || updateApplyMode.equals("restart"))
                        && restartScript() != null;
                if (!confirmationWillRestart) {
                    // The jar was already proved to be this plugin before it was staged, so a bad link
                    // fails during download and never reaches here. Nothing is left to confirm.
                    if (updateAutoApply) {
                        pl.sendMessage(Component.text("Auto-apply is enabled; no /xcrmupdate confirm is needed.",
                                NamedTextColor.GRAY));
                    }
                    confirmXcrmUpdate(pl);
                } else {
                    pl.sendMessage(Component.text("/xcrmupdate confirm to approve the automatic restart (expires in "
                            + (updateConfirmMillis / 1000) + "s). The verified jar is already staged.",
                            NamedTextColor.YELLOW));
                }
            });
        });
    }

    private void confirmXcrmUpdate(Player player) {
        StagedUpdate staged = pendingUpdate.remove(player.getUniqueId());
        if (staged == null) {
            String message = updateAutoApply
                    ? "No update is awaiting confirmation. Auto-apply is enabled, so confirm is not needed."
                    : "No update is awaiting confirmation. If a jar was already staged, follow the restart "
                            + "message shown after its download.";
            player.sendMessage(Component.text(message, NamedTextColor.RED));
            return;
        }
        if (System.currentTimeMillis() - staged.stagedAt() > updateConfirmMillis) {
            player.sendMessage(Component.text("The restart confirmation expired, but the verified jar remains "
                    + "staged. Restart the server manually to apply it.", NamedTextColor.YELLOW));
            return;
        }
        // A full restart is Paper's supported update-folder workflow and is also required when this
        // plugin has just extracted a new dependency such as WorldEdit. Never stop a server that cannot
        // launch itself again: with no restart script, keep the validated jar staged and ask the owner
        // to restart it through the hosting panel.
        String mode = updateApplyMode;
        boolean needsPanelRestart = false;
        if (mode.equals("auto")) {
            if (restartScript() != null) {
                mode = "restart";
            } else {
                mode = "stage";
                needsPanelRestart = true;
            }
        } else if (mode.equals("restart") && restartScript() == null) {
            player.sendMessage(Component.text("No restart script is set in spigot.yml — restarting would "
                    + "stop the server for good. Set settings.restart-script, or use "
                    + "xcrmupdate-apply-mode: auto.", NamedTextColor.RED));
            player.sendMessage(Component.text("The jar is still staged and applies on your next start.",
                    NamedTextColor.YELLOW));
            return;
        } else if (mode.equals("reload")) {
            mode = "stage";
            needsPanelRestart = true;
            player.sendMessage(Component.text("Plugin reloads are not a reliable update path; "
                    + "reload mode now stages the update instead.", NamedTextColor.YELLOW));
        }

        if (mode.equals("stage")) {
            player.sendMessage(Component.text(needsPanelRestart
                    ? "Update is staged. Restart the server from your hosting panel to apply it."
                    : "Update is staged. Restart the server to apply it.", NamedTextColor.YELLOW));
            return;
        }

        if (!mode.equals("restart")) {
            player.sendMessage(Component.text("Unknown xcrmupdate-apply-mode '" + updateApplyMode
                    + "'. The update remains staged; restart the server to apply it.", NamedTextColor.RED));
            return;
        }
        getLogger().info("Restarting to apply staged update "
                + staged.fileName() + " requested by " + player.getName());
        for (Player online : getServer().getOnlinePlayers()) {
            online.sendMessage(Component.text("Server restarting to apply a plugin update…",
                    NamedTextColor.YELLOW));
        }

        // A tick of grace lets the notice reach clients before Paper restarts and consumes plugins/update.
        updateRestartScheduled = true;
        getServer().getScheduler().runTaskLater(this, () -> getServer().restart(), 20L);
    }

    /**
     * The script Paper re-launches on {@code restart()}, or null when there is none. Without one,
     * a restart is just a shutdown — so this is the difference between "the server comes back on the
     * new version" and "the server is off and you are walking to the machine".
     */
    private File restartScript() {
        String path = "./start.sh";
        File spigotYml = new File("spigot.yml");
        if (spigotYml.isFile()) {
            path = YamlConfiguration.loadConfiguration(spigotYml)
                    .getString("settings.restart-script", path);
        }
        File script = new File(path);
        if (!script.isAbsolute()) {
            script = new File(".", path);
        }
        return script.isFile() ? script : null;
    }

    /**
     * Downloads a jar and drops it in Bukkit's update folder rather than over the running file.
     * On Windows the loaded jar is locked and cannot be overwritten at all, and even on Linux
     * swapping it under a running JVM is a good way to get a half-read class. The update folder is
     * the supported swap point: Bukkit moves it into place during the next startup.
     */
    private StagedUpdate downloadAndStageJar(URI uri, File updateDir)
            throws IOException, InterruptedException {
        File dir = new File(getDataFolder(), "downloaded");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("could not create download folder");
        }
        File temp = Files.createTempFile(dir.toPath(), "function-plugin-update-", ".jar").toFile();

        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            // Redirects are followed by hand so every hop is re-checked against the allowlist; letting
            // the client follow them silently would let a permitted host bounce us anywhere.
            URI current = uri;
            HttpResponse<InputStream> response = null;
            for (int hop = 0; hop < 5; hop++) {
                HttpRequest request = HttpRequest.newBuilder(current)
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "FunctionPlugin")
                        .GET()
                        .build();
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int code = response.statusCode();
                if (code == 200) {
                    break;
                }
                if (code / 100 != 3) {
                    try (InputStream ignored = response.body()) { /* drain */ }
                    throw new IOException("server returned HTTP " + code);
                }
                String location = response.headers().firstValue("location").orElse(null);
                try (InputStream ignored = response.body()) { /* drain */ }
                if (location == null) {
                    throw new IOException("redirect with no location header");
                }
                current = current.resolve(location);
                if (!"https".equalsIgnoreCase(current.getScheme())
                        || !updateAllowedHosts.contains(current.getHost() == null
                                ? "" : current.getHost().toLowerCase(Locale.ROOT))) {
                    throw new IOException("redirected to a host that is not allowed: " + current.getHost());
                }
                response = null;
            }
            if (response == null || response.statusCode() != 200) {
                throw new IOException("too many redirects");
            }

            long total = 0;
            byte[] buf = new byte[8192];
            try (InputStream in = response.body(); OutputStream os = new FileOutputStream(temp)) {
                int read;
                while ((read = in.read(buf)) != -1) {
                    total += read;
                    if (total > updateMaxBytes) {
                        throw new IOException("file exceeds the " + (updateMaxBytes / 1024 / 1024) + "MB cap");
                    }
                    os.write(buf, 0, read);
                }
            }

            String version = verifyPluginJar(temp);

            String runningName = getFile().getName();
            File target = new File(updateDir, runningName);
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return new StagedUpdate(runningName, total, version, System.currentTimeMillis());
        } finally {
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException e) {
                getLogger().warning("Could not delete temporary update file " + temp.getName() + ": "
                        + e.getMessage());
            }
        }
    }

    /**
     * Reject a blank/disabled update-folder setting before it can resolve to the live plugins folder.
     * Replacing the running jar in place is unsafe and fails outright on platforms that lock loaded jars.
     */
    private File validatedUpdateFolder() throws IOException {
        File pluginsDir = getDataFolder().getParentFile().getCanonicalFile();
        File updateDir = getServer().getUpdateFolderFile().getCanonicalFile();
        if (updateDir.equals(pluginsDir)) {
            throw new IOException("Bukkit's update folder is disabled; set settings.update-folder to 'update' "
                    + "in bukkit.yml");
        }
        if (!updateDir.exists() && !updateDir.mkdirs()) {
            throw new IOException("could not create the update folder");
        }
        if (!updateDir.isDirectory()) {
            throw new IOException("the configured update folder is not a directory");
        }
        return updateDir;
    }

    /**
     * Refuses anything that is not this plugin. A jar that downloads cleanly but is some other
     * plugin would be moved into place under our filename and silently replace us at next boot,
     * so the name in its own plugin.yml has to match before it is allowed near the update folder.
     */
    private String verifyPluginJar(File jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry("plugin.yml");
            if (entry == null) {
                throw new IOException("that jar has no plugin.yml — it is not a plugin");
            }
            YamlConfiguration meta;
            try (InputStream in = zip.getInputStream(entry)) {
                meta = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in));
            }
            String name = meta.getString("name", "");
            if (!getName().equals(name)) {
                throw new IOException("that jar is '" + name + "', not " + getName());
            }
            return meta.getString("version", "?");
        } catch (IOException e) {
            jar.delete();
            throw e;
        }
    }

    // ---- /xpearl -------------------------------------------------------------

    /**
     * Bare toggles; with arguments it tunes. The values are written straight back to config.yml as
     * well as into the live fields, so a setting found by feel survives the next restart without a
     * trip to the file — the whole point of tuning it from in game.
     */
    private void handleXpearl(Player player, String[] args) {
        if (args.length == 0) {
            toggleXpearl(player);
            return;
        }
        String key = args[0].toLowerCase(Locale.ROOT);
        if (key.equals("show")) {
            showXpearl(player);
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /xpearl <speed|delay|aim|consumes> <value>, "
                    + "or /xpearl show", NamedTextColor.RED));
            return;
        }
        String value = args[1].toLowerCase(Locale.ROOT);

        switch (key) {
            case "speed" -> {
                Double parsed = parsePositive(player, value);
                if (parsed == null) {
                    return;
                }
                pearlChargeSpeed = parsed;
                getConfig().set("xpearl-charge-speed", pearlChargeSpeed);
            }
            case "delay" -> {
                int ticks;
                try {
                    ticks = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("'" + args[1] + "' is not a whole number.", NamedTextColor.RED));
                    return;
                }
                pearlChargeDelayTicks = Math.max(0, ticks);
                getConfig().set("xpearl-charge-delay-ticks", pearlChargeDelayTicks);
            }
            case "aim" -> {
                if (!value.equals("pearl") && !value.equals("look") && !value.equals("down")) {
                    player.sendMessage(Component.text("Aim must be pearl, look or down.", NamedTextColor.RED));
                    return;
                }
                pearlChargeAim = value;
                getConfig().set("xpearl-charge-aim", pearlChargeAim);
            }
            case "consumes" -> {
                pearlChargeConsumes = value.equals("on") || value.equals("true");
                getConfig().set("xpearl-charge-consumes", pearlChargeConsumes);
            }
            case "cooldown" -> {
                Double parsed = parsePositive(player, value);
                if (parsed == null) {
                    return;
                }
                pearlChargeCooldownMillis = (long) (parsed * 1000.0);
                getConfig().set("xpearl-cooldown-seconds", parsed);
            }
            default -> {
                player.sendMessage(Component.text("Unknown setting '" + key + "'.", NamedTextColor.RED));
                return;
            }
        }
        saveConfig();
        showXpearl(player);
    }

    private Double parsePositive(Player player, String raw) {
        try {
            double parsed = Double.parseDouble(raw);
            if (parsed < 0.0) {
                player.sendMessage(Component.text("That has to be zero or more.", NamedTextColor.RED));
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("'" + raw + "' is not a number.", NamedTextColor.RED));
            return null;
        }
    }

    private void showXpearl(Player player) {
        player.sendMessage(Component.text("xpearl  "
                + (pearlChain.contains(player.getUniqueId()) ? "ON" : "OFF")
                + "   speed " + String.format(Locale.ROOT, "%.2f", pearlChargeSpeed)
                + "   delay " + pearlChargeDelayTicks + "t"
                + "   aim " + pearlChargeAim
                + "   consumes " + (pearlChargeConsumes ? "on" : "off")
                + "   cooldown " + String.format(Locale.ROOT, "%.2f", pearlChargeCooldownMillis / 1000.0) + "s",
                NamedTextColor.AQUA));
    }

    private void toggleXpearl(Player player) {
        UUID id = player.getUniqueId();
        boolean on;
        if (pearlChain.contains(id)) {
            pearlChain.remove(id);
            lastPearlCharge.remove(id);
            on = false;
        } else {
            pearlChain.add(id);
            on = true;
        }
        player.sendActionBar(on
                ? Component.text("Pearl chain ON", NamedTextColor.GREEN)
                : Component.text("Pearl chain OFF", NamedTextColor.GRAY));
    }

    /**
     * Throwing a pearl fires a wind charge a few ticks behind it. The charge is launched through
     * launchProjectile so the player is its shooter — that is what makes the burst move them, and the
     * same reason the pearl itself has to be launched rather than spawned.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPearlThrown(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) {
            return;
        }
        if (!(pearl.getShooter() instanceof Player player) || !pearlChain.contains(player.getUniqueId())) {
            return;
        }

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastPearlCharge.get(id);
        if (last != null && now - last < pearlChargeCooldownMillis) {
            return;
        }
        if (pearlChargeConsumes && !player.getInventory().contains(Material.WIND_CHARGE)) {
            player.sendActionBar(Component.text("No wind charge", NamedTextColor.RED));
            return;
        }
        lastPearlCharge.put(id, now);

        // The throw direction is taken off the pearl itself, not off the player's look, so turning
        // your head during the delay does not send the charge somewhere the pearl never went.
        Vector thrown = pearl.getVelocity().lengthSquared() > 1.0e-6
                ? pearl.getVelocity().clone().normalize()
                : player.getLocation().getDirection().clone().normalize();

        getServer().getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (pearlChargeConsumes) {
                removeItems(player, Material.WIND_CHARGE, 1);
            }
            player.launchProjectile(WindCharge.class, chargeAim(player, pearl, thrown)
                    .multiply(pearlChargeSpeed));
        }, pearlChargeDelayTicks);
    }

    /**
     * Where the chased wind charge is thrown.
     * <p>
     * The default, "pearl", is an interception: it aims from the eye at where the pearl actually is
     * at fire time, not along the original throw line. That matters because the pearl is already
     * falling by then and a wind charge is not — aiming at the live pearl bends the charge down onto
     * the arc, where firing straight along the throw would sail over the top of it. Combined with a
     * charge speed above the pearl's own, that is what lets it catch up and burst on the pearl.
     */
    private Vector chargeAim(Player player, EnderPearl pearl, Vector thrown) {
        if (pearlChargeAim.equals("look")) {
            return player.getLocation().getDirection().clone().normalize();
        }
        if (pearlChargeAim.equals("down")) {
            return new Vector(0.0, -1.0, 0.0);
        }
        if (pearl.isValid()) {
            Vector toPearl = pearl.getLocation().toVector().subtract(player.getEyeLocation().toVector());
            if (toPearl.lengthSquared() > 1.0e-6) {
                return toPearl.normalize();
            }
        }
        return thrown.clone();
    }
}
