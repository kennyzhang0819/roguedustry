# Roguedustry

A roguelike tower-defense mod for Mindustry v8.

- You place turrets, walls, shields, menders and drills. No units or conveyors.
- Everything you place works on its own. Turrets need no ammo or power.
- Drills mine copper and send it straight to your core. Drills cost copper: Mechanical 20, Pneumatic 50, Laser 200, Blast 500. Removing one gives the copper back.
- Spend copper in the shop on more block packs. The shop gets new stock every wave, and you can pay to reroll it.
- Survive 15 hand-made waves. You lose if your core is destroyed.
- Kills give XP. Each level up pauses the game and lets you pick 1 of 3 block packs.
- Each pack gives a number of blocks you can place. The build menu only shows what you have left. Removing a block you placed gives it back.
- Stronger packs appear at levels 3, 6 and 9.

## Install

1. Run `./build.sh` (needs Java 17 or newer). It creates `build/Roguedustry.jar`.
2. Copy that file into your Mindustry mods folder:
   - Standalone: `%APPDATA%\Mindustry\mods\`
   - Steam: `<Steam>\steamapps\common\Mindustry\saves\mods\`
3. Restart the game and press **Roguedustry** in the main menu. Pick **New random map**, or one of the game's own maps.

Needs Mindustry v8 (build 154 or newer) on desktop.

## Playing

- Fly your drone to place blocks. It can't attack.
- The top-left panel shows the wave and enemies left. Press ▶ to send the next wave once the current one is cleared.
- Below that: your level, XP, the blocks you have left, and **Auto waves**, which sends each wave automatically.
- At the bottom: your copper and the **Shop** button.

## Random maps

Each random map has a land path and a river that both lead from the far side to your core, so ground, naval and air enemies can all reach it. The only ore is copper, with a few patches close to the core. On the game's own maps, other ores are removed, and naval enemies only show up if the map has a spawn point on water.

## Balance numbers

Run `./balance.sh` to see, for each wave, how strong it is and what level you'll likely be. It also shows rough damage per second for each pack, pack prices, and how fast each drill mines. The numbers are estimates. They don't account for which turrets can hit air or ground, and support turrets like Wave and Segment show about 0 damage.

## Screenshot check

Set `ROGUE_AUTOTEST=hud` and start the game. It plays one wave on a random map, saves a screenshot to `ROGUE_SHOTS` and quits. Point `APPDATA` at an empty folder so your own saves aren't touched.

## Known limits

- Loading a saved run starts it over.
- Single player only.
- Balance hasn't been tuned yet.
