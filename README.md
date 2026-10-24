# Mindustry Survivors

Press **Mindustry Survivors** in the main menu and start a new run. A run is one random map: survive 50 waves, the way Vampire Survivors plays.

## The run

- Every run makes a new random map, and each one rolls its own landscape: big mountains or a wide open plain, and lakes, a coast or both. All water can be waded through. Each map mixes two or three biomes that blend into each other. Your own saves and campaign are never touched.
- Enemies come in from every side: they appear on any open ground or deep water near the map edge. Boats can also come onto land, at half speed.
- The first wave comes after 3 seconds, then one every 18.5 seconds. Enemies hit 4% harder with every wave. Each wave uses 3 of 6 unit families, chosen fresh for every wave: Dagger, Nova, Crawler, Flare, Risso and Retusa. The first two waves only walk.
- There is no core and nothing to build. You walk around as a ground unit, and every enemy comes after you. If you die, the run is over.
- Your own unit has a gun you aim and fire yourself. On top of that, turrets circle around you and fire with you: where you aim, only while you shoot. You start with only your own gun; turrets come from level-up picks. You can hold 6 turrets and 6 passives.
- Each turret has 10 levels. Levels switch it to the game's own better ammo (Duo: copper, then graphite, then homing silicon), add water and then cryofluid cooling, and add shots, damage, range or special traits like lightning, fragments, fire and piercing. `./report.sh` lists every level.
- Four support turrets do little damage but help the others: Wave sprays oil that slows enemies and makes fire burn them harder, Tsunami freezes enemies and shoves them back, Parallax grabs the enemies near your aim and pushes them away, and Segment shoots down enemy bullets on its own. At level 1 every turret does about the same damage per second to one target; they differ by range, splash, piercing and traits.
- Killing an enemy drops an experience gem. Walk near it to pick it up. Each level gives a pick of 3 (sometimes 4): a new weapon, a new passive, or the next level of one you have. Turrets go up to level 10, passives to level 5. When everything is maxed, a pick heals you.
- Experience drops as Titanium, with Plastanium and Surge Alloy as the bigger, more valuable versions.
- Each kill has a 1.5% chance to drop a glowing piece of Thorium (at most 3 lie around at once). Picking it up heals 50% of your health. Very rarely (0.1% per kill, at most 3 at once) an enemy drops a glowing magnet that pulls every gem on the map to you. The Repair Point passive also heals you over time.
- Passives are named after buildings. For kiting at range: Titanium Conveyor (move speed), Mass Driver (range and bullet speed), Phase Weaver (dodge chance), Unloader (pickup range). For tanking up close: Force Projector (armor), Thorium Wall (max health), Repair Point (healing), Mend Projector (life steal), Thorium Reactor (burns enemies next to you). For any build: Battery (damage), Overdrive Projector (reload), Impact Reactor (blast radius), Multiplicative Reconstructor (extra shots, then faster reload, piercing and damage), Cultivator (experience), Separator (a fourth choice, then more drops, healing on level-up and damage that keeps growing once your build is maxed).
- Pick a difficulty on the Mindustry Survivors page before starting: Casual, Easy, Normal, Hard or Eradication. They send 25%, 38%, 50%, 75% or 100% of the enemies. Gems are worth more on lower difficulties, so you level up just as often.
- Win when all 50 waves are dead. Quitting saves the run; press **Continue run** to go on.

### Relics and curses

Relics and curses are switched off for now. Curses will come back as random rules rolled for each run.

The icons are drawn by `tools/make-icons.py` (needs Python with Pillow and numpy).

## Install

The quickest way: run `./play.sh`. It builds the mod, puts it in the game's mods folder and starts the game. The first time, it also downloads the game (needs Java 17 or newer).

To do it by hand:

1. Run `./build.sh` (needs Java 17 or newer). It creates `build/Roguedustry.jar`.
2. Copy that file into your Mindustry mods folder:
   - Standalone: `%APPDATA%\Mindustry\mods\`
   - Steam: `<Steam>\steamapps\common\Mindustry\saves\mods\`
3. Restart the game and press **Mindustry Survivors** in the main menu.

Needs Mindustry v8 (build 154 or newer) on desktop.

## Playing

- Tick **Admin tools** in the Mindustry Survivors menu to get an **Admin** button under the wave counter: give yourself picks or a level, skip to any wave (the enemies on the map are removed and that wave is sent now), or tap any modifier to add or remove it.

## Run report

Run `./report.sh` to see the relics, modifiers, curses and wave totals, with the level you would reach by each wave. It takes a few seconds and doesn't open the game.

## Screenshot check

Set `ROGUE_AUTOTEST=arena` and start the game. It opens the menu, starts a run, picks a level-up choice, adds a few weapons, sends two waves, saves, continues the run, saves screenshots to `ROGUE_SHOTS` and quits. Point the game's data folder at an empty folder so your own saves aren't touched (`APPDATA` on Windows, `XDG_DATA_HOME` on Linux).

Set `ROGUE_AUTOTEST=chase` for a stress test: it starts a run, moves your unit in a circle and adds enemies in steps up to 5,000, logging frame times and how many enemies are closing in on you.

To look at random maps without the game, run `tools/MapPreview.java` (see `report.sh` for how to compile it): it writes pictures of maps to a folder.

## Known limits

- Single player only.
- Balance hasn't been tuned yet.
- An Overdrive Projector's boost and a relic or modifier speed boost don't add up: the stronger one wins.
- The game's research window can't mark logic blocks as disabled. They show as normal there, but can't be built.
- Long Range doesn't yet affect enemy units that follow an enemy commander (used on some attack maps).
