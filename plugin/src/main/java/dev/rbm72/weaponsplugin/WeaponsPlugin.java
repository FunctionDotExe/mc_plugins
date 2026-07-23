package dev.rbm72.weaponsplugin;

import dev.rbm72.weaponsplugin.ability.CooldownManager;
import dev.rbm72.weaponsplugin.ability.SummonRetargetTask;
import dev.rbm72.weaponsplugin.ability.WeaponSwitchLock;
import dev.rbm72.weaponsplugin.ability.WeaponTickTask;
import dev.rbm72.weaponsplugin.accessory.AccessoryManager;
import dev.rbm72.weaponsplugin.accessory.AccessoryRegistry;
import dev.rbm72.weaponsplugin.accessory.AccessoryTickTask;
import dev.rbm72.weaponsplugin.accessory.accessories.ArcaneGrimoire;
import dev.rbm72.weaponsplugin.accessory.accessories.BerserkerCharm;
import dev.rbm72.weaponsplugin.accessory.accessories.Chronostone;
import dev.rbm72.weaponsplugin.accessory.accessories.EchoingSigil;
import dev.rbm72.weaponsplugin.accessory.accessories.Emberheart;
import dev.rbm72.weaponsplugin.accessory.accessories.GaleStepBoots;
import dev.rbm72.weaponsplugin.accessory.accessories.GrappleHook;
import dev.rbm72.weaponsplugin.accessory.accessories.HollowMask;
import dev.rbm72.weaponsplugin.accessory.accessories.RingOfElements;
import dev.rbm72.weaponsplugin.accessory.accessories.RunicOverload;
import dev.rbm72.weaponsplugin.accessory.accessories.ShroudedCloak;
import dev.rbm72.weaponsplugin.accessory.accessories.SkyreaverTalons;
import dev.rbm72.weaponsplugin.accessory.accessories.SwiftcasterRing;
import dev.rbm72.weaponsplugin.accessory.accessories.TitansGirdle;
import dev.rbm72.weaponsplugin.accessory.accessories.VampiricLocket;
import dev.rbm72.weaponsplugin.accessory.accessories.WarriorsBand;
import dev.rbm72.weaponsplugin.accessory.accessories.WindwalkerBoots;
import dev.rbm72.weaponsplugin.armor.ArmorManager;
import dev.rbm72.weaponsplugin.armor.ArmorRegistry;
import dev.rbm72.weaponsplugin.armor.ArmorTickTask;
import dev.rbm72.weaponsplugin.armor.sets.CinderforgedPlate;
import dev.rbm72.weaponsplugin.armor.sets.GlacialWard;
import dev.rbm72.weaponsplugin.armor.sets.GrimshroudPlate;
import dev.rbm72.weaponsplugin.armor.sets.PenitentsVestments;
import dev.rbm72.weaponsplugin.armor.sets.ShroudveilMantle;
import dev.rbm72.weaponsplugin.armor.sets.StormplateAegis;
import dev.rbm72.weaponsplugin.armor.sets.ThornweaveVestments;
import dev.rbm72.weaponsplugin.armor.sets.WyrmwingPlate;
import dev.rbm72.weaponsplugin.boss.BossDamageListener;
import dev.rbm72.weaponsplugin.boss.BossManager;
import dev.rbm72.weaponsplugin.boss.bosses.AmalgamatedBulk;
import dev.rbm72.weaponsplugin.boss.bosses.DragonElder;
import dev.rbm72.weaponsplugin.boss.bosses.FallenKing;
import dev.rbm72.weaponsplugin.boss.bosses.FrostQueen;
import dev.rbm72.weaponsplugin.boss.bosses.GraftedHorror;
import dev.rbm72.weaponsplugin.boss.bosses.HollowChoir;
import dev.rbm72.weaponsplugin.boss.bosses.InfernoWarlord;
import dev.rbm72.weaponsplugin.boss.bosses.NecroOverlord;
import dev.rbm72.weaponsplugin.boss.bosses.PlagueWarden;
import dev.rbm72.weaponsplugin.boss.bosses.SolarColossus;
import dev.rbm72.weaponsplugin.boss.bosses.StormTyrant;
import dev.rbm72.weaponsplugin.boss.bosses.ThreefoldBane;
import dev.rbm72.weaponsplugin.boss.bosses.TideLeviathan;
import dev.rbm72.weaponsplugin.boss.bosses.Voidwyrm;
import dev.rbm72.weaponsplugin.boss.bosses.VoidSovereign;
import dev.rbm72.weaponsplugin.boss.bosses.WeepingColossus;
import dev.rbm72.weaponsplugin.boss.bosses.Worldender;
import dev.rbm72.weaponsplugin.boss.commands.BossDespawnCommand;
import dev.rbm72.weaponsplugin.boss.commands.BossHardModeCommand;
import dev.rbm72.weaponsplugin.boss.commands.BossInfoCommand;
import dev.rbm72.weaponsplugin.boss.commands.BossReloadCommand;
import dev.rbm72.weaponsplugin.boss.commands.BossSpawnCommand;
import dev.rbm72.weaponsplugin.boss.props.ArenaTotemDamageListener;
import dev.rbm72.weaponsplugin.commands.EnderChestCommand;
import dev.rbm72.weaponsplugin.commands.GiveAccessoryCommand;
import dev.rbm72.weaponsplugin.commands.GiveArmorCommand;
import dev.rbm72.weaponsplugin.commands.GiveShieldCommand;
import dev.rbm72.weaponsplugin.commands.GiveWeaponCommand;
import dev.rbm72.weaponsplugin.commands.HubCommand;
import dev.rbm72.weaponsplugin.commands.OpCooldownCommand;
import dev.rbm72.weaponsplugin.commands.WeaponMenuCommand;
import dev.rbm72.weaponsplugin.items.ShieldRegistry;
import dev.rbm72.weaponsplugin.items.WeaponRegistry;
import dev.rbm72.weaponsplugin.items.shields.BoneguardAegis;
import dev.rbm72.weaponsplugin.items.shields.DuelistsBuckler;
import dev.rbm72.weaponsplugin.items.shields.GlacialBastion;
import dev.rbm72.weaponsplugin.items.shields.GuardianBulwark;
import dev.rbm72.weaponsplugin.items.shields.ThornboundWard;
import dev.rbm72.weaponsplugin.items.shields.VoidguardAegis;
import dev.rbm72.weaponsplugin.items.shields.WardensAspis;
import dev.rbm72.weaponsplugin.items.weapons.AnglersHook;
import dev.rbm72.weaponsplugin.items.weapons.Apotheosis;
import dev.rbm72.weaponsplugin.items.weapons.ArcaneStaff;
import dev.rbm72.weaponsplugin.items.weapons.BloodReaper;
import dev.rbm72.weaponsplugin.items.weapons.CelestialBow;
import dev.rbm72.weaponsplugin.items.weapons.Chainwhip;
import dev.rbm72.weaponsplugin.items.weapons.ChronoBlade;
import dev.rbm72.weaponsplugin.items.weapons.CinderCleaver;
import dev.rbm72.weaponsplugin.items.weapons.Dawnbreaker;
import dev.rbm72.weaponsplugin.items.weapons.DragonFang;
import dev.rbm72.weaponsplugin.items.weapons.DuskfallMace;
import dev.rbm72.weaponsplugin.items.weapons.EarthbreakerAxe;
import dev.rbm72.weaponsplugin.items.weapons.ExcavatorsPick;
import dev.rbm72.weaponsplugin.items.weapons.FlameKatana;
import dev.rbm72.weaponsplugin.items.weapons.FrostScythe;
import dev.rbm72.weaponsplugin.items.weapons.GlacialScepter;
import dev.rbm72.weaponsplugin.items.weapons.HuntersCrossbow;
import dev.rbm72.weaponsplugin.items.weapons.IronclawKnuckles;
import dev.rbm72.weaponsplugin.items.weapons.KingsJudgment;
import dev.rbm72.weaponsplugin.items.weapons.LunarBlade;
import dev.rbm72.weaponsplugin.items.weapons.MaelstromTrident;
import dev.rbm72.weaponsplugin.items.weapons.Mournsong;
import dev.rbm72.weaponsplugin.items.weapons.NecromancerStaff;
import dev.rbm72.weaponsplugin.items.weapons.Nullblade;
import dev.rbm72.weaponsplugin.items.weapons.PlagueScythe;
import dev.rbm72.weaponsplugin.items.weapons.Rotscourge;
import dev.rbm72.weaponsplugin.items.weapons.SakuraBlade;
import dev.rbm72.weaponsplugin.items.weapons.ShadowDaggers;
import dev.rbm72.weaponsplugin.items.weapons.SolarGreatsword;
import dev.rbm72.weaponsplugin.items.weapons.Soulcrown;
import dev.rbm72.weaponsplugin.items.weapons.Soulharvester;
import dev.rbm72.weaponsplugin.items.weapons.Spinelash;
import dev.rbm72.weaponsplugin.items.weapons.Starbreaker;
import dev.rbm72.weaponsplugin.items.weapons.Starfang;
import dev.rbm72.weaponsplugin.items.weapons.Stormbreaker;
import dev.rbm72.weaponsplugin.items.weapons.StormChakrams;
import dev.rbm72.weaponsplugin.items.weapons.StormreachHalberd;
import dev.rbm72.weaponsplugin.items.weapons.Tearfall;
import dev.rbm72.weaponsplugin.items.weapons.TempestMaul;
import dev.rbm72.weaponsplugin.items.weapons.ThunderHammer;
import dev.rbm72.weaponsplugin.items.weapons.TidalTrident;
import dev.rbm72.weaponsplugin.items.weapons.VenomtipJavelin;
import dev.rbm72.weaponsplugin.items.weapons.Vitriol;
import dev.rbm72.weaponsplugin.items.weapons.VoidBlade;
import dev.rbm72.weaponsplugin.items.weapons.WindSpear;
import dev.rbm72.weaponsplugin.items.weapons.WyrmscaleBow;
import dev.rbm72.weaponsplugin.listeners.AccessoryDamageListener;
import dev.rbm72.weaponsplugin.listeners.AccessoryPersonalAbilityListener;
import dev.rbm72.weaponsplugin.listeners.AerialStrikeListener;
import dev.rbm72.weaponsplugin.listeners.BossMenuListener;
import dev.rbm72.weaponsplugin.listeners.CinderforgedListener;
import dev.rbm72.weaponsplugin.listeners.GlacialWardListener;
import dev.rbm72.weaponsplugin.listeners.GrimshroudListener;
import dev.rbm72.weaponsplugin.listeners.HubListener;
import dev.rbm72.weaponsplugin.listeners.MagicProjectileListener;
import dev.rbm72.weaponsplugin.listeners.MenuListener;
import dev.rbm72.weaponsplugin.listeners.PenanceListener;
import dev.rbm72.weaponsplugin.listeners.PlayerSummonTargetListener;
import dev.rbm72.weaponsplugin.listeners.ShieldBlockListener;
import dev.rbm72.weaponsplugin.listeners.ShroudveilListener;
import dev.rbm72.weaponsplugin.listeners.StormplateListener;
import dev.rbm72.weaponsplugin.listeners.ThornweaveListener;
import dev.rbm72.weaponsplugin.listeners.WeaponBlockBreakListener;
import dev.rbm72.weaponsplugin.listeners.WeaponDamageListener;
import dev.rbm72.weaponsplugin.listeners.WeaponInteractListener;
import dev.rbm72.weaponsplugin.listeners.WeaponMenuListener;
import dev.rbm72.weaponsplugin.listeners.WeaponSwitchListener;
import dev.rbm72.weaponsplugin.listeners.WingDashListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class WeaponsPlugin extends JavaPlugin {

    private WeaponRegistry weaponRegistry;
    private CooldownManager cooldownManager;
    private BossManager bossManager;
    private AccessoryRegistry accessoryRegistry;
    private AccessoryManager accessoryManager;
    private ArmorRegistry armorRegistry;
    private ArmorManager armorManager;
    private ShieldRegistry shieldRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        weaponRegistry = new WeaponRegistry();
        cooldownManager = new CooldownManager(this);
        bossManager = new BossManager(this);
        accessoryRegistry = new AccessoryRegistry();
        accessoryManager = new AccessoryManager(this, accessoryRegistry);
        armorRegistry = new ArmorRegistry();
        armorManager = new ArmorManager(armorRegistry);
        shieldRegistry = new ShieldRegistry();
        WeaponSwitchLock weaponSwitchLock = new WeaponSwitchLock();
        OpCooldownCommand opCooldownCommand = new OpCooldownCommand();

        accessoryRegistry.register(new WarriorsBand(this));
        accessoryRegistry.register(new BerserkerCharm(this));
        accessoryRegistry.register(new SwiftcasterRing(this));
        accessoryRegistry.register(new Chronostone(this));
        accessoryRegistry.register(new Emberheart(this));
        accessoryRegistry.register(new VampiricLocket(this));
        accessoryRegistry.register(new TitansGirdle(this));
        accessoryRegistry.register(new WindwalkerBoots(this));
        accessoryRegistry.register(new EchoingSigil(this));
        accessoryRegistry.register(new RunicOverload(this));
        accessoryRegistry.register(new HollowMask(this));
        accessoryRegistry.register(new GrappleHook(this));
        accessoryRegistry.register(new GaleStepBoots(this));
        accessoryRegistry.register(new ArcaneGrimoire(this));
        accessoryRegistry.register(new RingOfElements(this));
        accessoryRegistry.register(new ShroudedCloak(this));
        SkyreaverTalons skyreaverTalons = new SkyreaverTalons(this);
        accessoryRegistry.register(skyreaverTalons);

        shieldRegistry.register(new GuardianBulwark(this));
        shieldRegistry.register(new WardensAspis(this));
        shieldRegistry.register(new DuelistsBuckler(this));
        shieldRegistry.register(new GlacialBastion(this));
        shieldRegistry.register(new VoidguardAegis(this));
        shieldRegistry.register(new ThornboundWard(this));
        shieldRegistry.register(new BoneguardAegis(this));

        WyrmwingPlate wyrmwingPlate = new WyrmwingPlate(this);
        PenitentsVestments penitentsVestments = new PenitentsVestments(this);
        StormplateAegis stormplateAegis = new StormplateAegis(this);
        CinderforgedPlate cinderforgedPlate = new CinderforgedPlate(this);
        GlacialWard glacialWard = new GlacialWard(this);
        ShroudveilMantle shroudveilMantle = new ShroudveilMantle(this);
        ThornweaveVestments thornweaveVestments = new ThornweaveVestments(this);
        GrimshroudPlate grimshroudPlate = new GrimshroudPlate(this);
        armorRegistry.register(wyrmwingPlate);
        armorRegistry.register(penitentsVestments);
        armorRegistry.register(stormplateAegis);
        armorRegistry.register(cinderforgedPlate);
        armorRegistry.register(glacialWard);
        armorRegistry.register(shroudveilMantle);
        armorRegistry.register(thornweaveVestments);
        armorRegistry.register(grimshroudPlate);

        weaponRegistry.register(new Stormbreaker(this));
        weaponRegistry.register(new FlameKatana(this));
        weaponRegistry.register(new ThunderHammer(this));
        weaponRegistry.register(new FrostScythe(this));
        weaponRegistry.register(new ShadowDaggers(this));
        weaponRegistry.register(new ArcaneStaff(this));
        weaponRegistry.register(new WindSpear(this));
        weaponRegistry.register(new TidalTrident(this));
        weaponRegistry.register(new VoidBlade(this));
        weaponRegistry.register(new SolarGreatsword(this));
        weaponRegistry.register(new LunarBlade(this));
        weaponRegistry.register(new PlagueScythe(this));
        weaponRegistry.register(new DragonFang(this));
        weaponRegistry.register(new CelestialBow(this));
        weaponRegistry.register(new BloodReaper(this));
        weaponRegistry.register(new ChronoBlade(this));
        weaponRegistry.register(new StormChakrams(this));
        weaponRegistry.register(new EarthbreakerAxe(this));
        weaponRegistry.register(new NecromancerStaff(this));
        weaponRegistry.register(new SakuraBlade(this));
        weaponRegistry.register(new Starbreaker(this));
        weaponRegistry.register(new KingsJudgment(this));
        weaponRegistry.register(new GlacialScepter(this));
        weaponRegistry.register(new TempestMaul(this));
        weaponRegistry.register(new CinderCleaver(this));
        weaponRegistry.register(new Rotscourge(this));
        weaponRegistry.register(new Nullblade(this));
        weaponRegistry.register(new Dawnbreaker(this));
        weaponRegistry.register(new MaelstromTrident(this));
        weaponRegistry.register(new WyrmscaleBow(this));
        weaponRegistry.register(new Soulharvester(this));
        weaponRegistry.register(new Apotheosis(this));
        weaponRegistry.register(new DuskfallMace(this));
        weaponRegistry.register(new Chainwhip(this));
        weaponRegistry.register(new HuntersCrossbow(this));
        weaponRegistry.register(new IronclawKnuckles(this));
        weaponRegistry.register(new StormreachHalberd(this));
        weaponRegistry.register(new VenomtipJavelin(this));
        weaponRegistry.register(new AnglersHook(this));
        weaponRegistry.register(new ExcavatorsPick(this));
        weaponRegistry.register(new Spinelash(this));
        weaponRegistry.register(new Soulcrown(this));
        weaponRegistry.register(new Starfang(this));
        weaponRegistry.register(new Vitriol(this));
        weaponRegistry.register(new Mournsong(this));
        weaponRegistry.register(new Tearfall(this));

        bossManager.register(new FallenKing(this));
        bossManager.register(new FrostQueen(this));
        bossManager.register(new StormTyrant(this));
        bossManager.register(new InfernoWarlord(this));
        bossManager.register(new PlagueWarden(this));
        bossManager.register(new VoidSovereign(this));
        bossManager.register(new SolarColossus(this));
        bossManager.register(new TideLeviathan(this));
        bossManager.register(new DragonElder(this));
        bossManager.register(new NecroOverlord(this));
        bossManager.register(new GraftedHorror(this));
        bossManager.register(new ThreefoldBane(this));
        bossManager.register(new Voidwyrm(this));
        bossManager.register(new AmalgamatedBulk(this));
        bossManager.register(new HollowChoir(this));
        bossManager.register(new WeepingColossus(this));
        bossManager.register(new Worldender(this));

        getServer().getPluginManager().registerEvents(
                new WeaponInteractListener(this, weaponRegistry, cooldownManager, accessoryManager, opCooldownCommand, weaponSwitchLock), this);
        getServer().getPluginManager().registerEvents(
                new WeaponDamageListener(this, weaponRegistry, accessoryManager, cooldownManager, bossManager), this);
        getServer().getPluginManager().registerEvents(
                new AccessoryDamageListener(weaponRegistry, accessoryManager), this);
        getServer().getPluginManager().registerEvents(new WeaponSwitchListener(weaponRegistry, weaponSwitchLock), this);
        getServer().getPluginManager().registerEvents(new WeaponBlockBreakListener(weaponRegistry), this);
        getServer().getPluginManager().registerEvents(new ShieldBlockListener(shieldRegistry), this);
        getServer().getPluginManager().registerEvents(
                new MagicProjectileListener(this, weaponRegistry), this);
        getServer().getPluginManager().registerEvents(new WeaponMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new HubListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new BossMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new BossDamageListener(bossManager), this);
        PlayerSummonTargetListener summonTargetListener = new PlayerSummonTargetListener(this);
        getServer().getPluginManager().registerEvents(summonTargetListener, this);
        getServer().getPluginManager().registerEvents(new ArenaTotemDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new AccessoryPersonalAbilityListener(accessoryManager), this);
        getServer().getPluginManager().registerEvents(new AerialStrikeListener(accessoryManager, skyreaverTalons), this);
        getServer().getPluginManager().registerEvents(new WingDashListener(armorManager, wyrmwingPlate), this);
        getServer().getPluginManager().registerEvents(new PenanceListener(this, armorManager, penitentsVestments), this);
        getServer().getPluginManager().registerEvents(new StormplateListener(armorManager, stormplateAegis), this);
        getServer().getPluginManager().registerEvents(new CinderforgedListener(armorManager, cinderforgedPlate), this);
        getServer().getPluginManager().registerEvents(new GlacialWardListener(this, armorManager, glacialWard), this);
        getServer().getPluginManager().registerEvents(new ShroudveilListener(armorManager, shroudveilMantle), this);
        getServer().getPluginManager().registerEvents(new ThornweaveListener(armorManager, thornweaveVestments), this);
        getServer().getPluginManager().registerEvents(new GrimshroudListener(armorManager, grimshroudPlate), this);

        new WeaponTickTask(weaponRegistry).start(this);
        new SummonRetargetTask(this, summonTargetListener.key()).start();
        new AccessoryTickTask(accessoryManager).start(this);
        new ArmorTickTask(armorManager).start(this);

        // Hand the hub star to anyone already online (e.g. after a /reload).
        getServer().getOnlinePlayers().forEach(player ->
                dev.rbm72.weaponsplugin.gui.HubItem.ensure(this, player));

        GiveWeaponCommand giveWeaponCommand = new GiveWeaponCommand(weaponRegistry);
        getCommand("giveweapon").setExecutor(giveWeaponCommand);
        getCommand("giveweapon").setTabCompleter(giveWeaponCommand);
        getCommand("weapons").setExecutor(new WeaponMenuCommand(this));
        getCommand("opcooldown").setExecutor(opCooldownCommand);
        getCommand("bossspawn").setExecutor(new BossSpawnCommand(this));
        getCommand("bossdespawn").setExecutor(new BossDespawnCommand(bossManager));
        BossInfoCommand bossInfoCommand = new BossInfoCommand(bossManager);
        getCommand("bossinfo").setExecutor(bossInfoCommand);
        getCommand("bossinfo").setTabCompleter(bossInfoCommand);
        getCommand("bossreload").setExecutor(new BossReloadCommand(this));
        BossHardModeCommand bossHardModeCommand = new BossHardModeCommand(bossManager);
        getCommand("bosshardmode").setExecutor(bossHardModeCommand);
        getCommand("bosshardmode").setTabCompleter(bossHardModeCommand);
        getCommand("hub").setExecutor(new HubCommand(this));
        EnderChestCommand enderChestCommand = new EnderChestCommand();
        getCommand("enderchest").setExecutor(enderChestCommand);
        getCommand("enderchest").setTabCompleter(enderChestCommand);
        GiveAccessoryCommand giveAccessoryCommand = new GiveAccessoryCommand(accessoryRegistry);
        getCommand("giveaccessory").setExecutor(giveAccessoryCommand);
        getCommand("giveaccessory").setTabCompleter(giveAccessoryCommand);
        GiveArmorCommand giveArmorCommand = new GiveArmorCommand(armorRegistry);
        getCommand("givearmor").setExecutor(giveArmorCommand);
        getCommand("givearmor").setTabCompleter(giveArmorCommand);
        GiveShieldCommand giveShieldCommand = new GiveShieldCommand(shieldRegistry);
        getCommand("giveshield").setExecutor(giveShieldCommand);
        getCommand("giveshield").setTabCompleter(giveShieldCommand);

        getLogger().info("WeaponsPlugin enabled with " + weaponRegistry.all().size() + " weapon(s) and "
                + bossManager.all().size() + " boss(es)");
    }

    @Override
    public void onDisable() {
        if (bossManager != null) {
            bossManager.shutdownAll();
        }
        getLogger().info("WeaponsPlugin disabled");
    }

    public WeaponRegistry weaponRegistry() {
        return weaponRegistry;
    }

    public CooldownManager cooldownManager() {
        return cooldownManager;
    }

    public BossManager bossManager() {
        return bossManager;
    }

    public AccessoryRegistry accessoryRegistry() {
        return accessoryRegistry;
    }

    public AccessoryManager accessoryManager() {
        return accessoryManager;
    }

    public ArmorRegistry armorRegistry() {
        return armorRegistry;
    }

    public ArmorManager armorManager() {
        return armorManager;
    }

    public ShieldRegistry shieldRegistry() {
        return shieldRegistry;
    }
}
