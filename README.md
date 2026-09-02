# mc_plugins

Custom Paper/Spigot Minecraft plugins: a rarity-tiered weapons and boss plugin, plus a utility plugin with teleport, home, practice, and owner tooling.

## Requirements

- JDK 17+ (Gradle toolchain) and a JDK the server itself runs on (the bundled dev scripts default to JDK 25 — edit `scripts/mc.mjs` if yours differs)
- [pnpm](https://pnpm.io/) (or npm/yarn) for the dev-server scripts
- A [PaperMC](https://papermc.io/) server jar dropped in `server/` (not committed — see below)

## Project layout

```
plugin/           Gradle project — weapons and bosses (dev.rbm72.weaponsplugin)
functionplugin/   Gradle project — utility commands and owner tooling (dev.rbm72.functionplugin)
artifacts/        Versioned, ready-to-install plugin JARs
resourcepack/      Source assets for the optional client resource pack
weaponsplugin-resourcepack.zip   Packaged resource pack, ready to hand to players
server/           Local Paper dev server (gitignored — runtime/world data/logs)
scripts/mc.mjs    Dev-server control script (start/stop/build/deploy/console)
docs/             Design docs and specs
```

## Dev server commands

Run from the repo root (see `package.json`):

```bash
pnpm install        # once, to pull in script deps
pnpm run build       # gradlew build the plugin jar
pnpm run deploy      # copy the built jar into server/plugins/
pnpm run start        # start the local Paper server (detached)
pnpm run stop         # stop it gracefully
pnpm run restart      # stop + start
pnpm run dev          # build + deploy + restart, the usual iteration loop
pnpm run console -- <command>   # send a raw command to the running server console
```

`server/` isn't part of the repo — grab a Paper server jar matching the `api-version` in `plugin/src/main/resources/plugin.yml` and place it directly in `server/` before running `pnpm run start`.

### Building without the scripts

```bash
cd plugin
./gradlew build      # or gradlew.bat on Windows
```

The jar lands in `plugin/build/libs/`.

### FunctionPlugin

The ready-to-install build is `artifacts/function-plugin-1.5.2.jar`. It bundles WorldEdit 7.4.4 and
extracts that dependency on first enable when WorldEdit is not already present. If Paper cannot load
the extracted dependency live, one server restart completes the installation.

`FunctionDotExe` receives `worldedit.*` while online without being granted operator status. The player
name is configurable with `worldedit-owner` and defaults to the shared `owner` setting. Keep the server
in authenticated/online mode when using name-based owner access.

Build it independently with:

```bash
cd functionplugin
./gradlew clean build      # or gradlew.bat on Windows
```

## In-game commands

| Command | Description | Permission |
|---|---|---|
| `/giveweapon <id>` | Gives you a custom weapon | `weaponsplugin.give` |
| `/givearmor <id>` | Gives you a custom armor piece | `weaponsplugin.give` |
| `/giveshield <id>` | Gives you a custom shield | `weaponsplugin.give` |
| `/giveaccessory <id>` | Gives you a custom accessory | `weaponsplugin.give` |
| `/weapons` | Opens the weapon catalog GUI | — |
| `/hub` | Opens the hub menu (ender chest & accessories) | — |
| `/enderchest [player]` (aliases `/ec`, `/echest`) | Opens your ender chest, or another online player's | `weaponsplugin.enderchest.others` for others' |
| `/opcooldown` | Toggles ability-cooldown bypass for yourself | `weaponsplugin.opcooldown` |
| `/bossspawn [id]` | Opens the boss catalog GUI, or spawns a boss directly | `weaponsplugin.boss.spawn` |
| `/bossdespawn <id>` | Despawns a live boss | `weaponsplugin.boss.spawn` |
| `/bossinfo <id>` | Shows a boss's health, arena radius, phases, and loot odds | — |
| `/bosshardmode <id>` | Toggles hard mode for a boss (applies next spawn) | `weaponsplugin.boss.admin` |
| `/bossreload` | Reloads `config.yml` tuning without a full server `/reload` | `weaponsplugin.boss.admin` |

Per-weapon/boss/armor tuning (damage, cooldowns, health, arena size, etc.) lives in `plugin/src/main/resources/config.yml` and is generated with sane defaults on first run; override only the keys you want to change.

## Resource pack

`resourcepack/` is the source; `weaponsplugin-resourcepack.zip` is the packaged version to distribute to players who want the custom item textures/models. Re-zip after editing `resourcepack/`.

## Optional integrations

The plugin soft-depends on **WorldGuard** and **DecentHolograms** — both are guarded at runtime via `isPluginEnabled()`, so the server runs fine without either installed.
