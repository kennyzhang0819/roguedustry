# Roguedustry

Press **Roguedustry** in the main menu and start a new run. You pick one of two modes:

- **Roguelike Campaign**: everything is wireless (below). After the first wave, the wait between waves is 90% shorter, and 2.5 times as many enemies spawn. Your core unit builds twice as fast.
- **Vanilla Campaign**: the normal campaign with belts and pipes, plus the roguelike rewards and curses.

Both play in a separate campaign, so your own campaign and saves are never touched. Both are Serpulo only: Erekir can't be visited during a run.

## Wireless (Roguelike Campaign)

No belts and no pipes. Everything goes through your core.

- Drills, factories and generators put what they make straight into the core, and take what they need straight from it.
- Liquids go into a shared liquid store. It holds 1,000 of each liquid per core, plus the size of every Liquid Tank and Liquid Container you build. It shows under the top bar.
- Turrets take their ammo and coolant from the core and the liquid store. Tap a turret to pick its ammo and coolant. If you pick nothing, it uses the best ammo you have and the best coolant.
- Tap a generator to pick its fuel. Tap a mender, drill or projector to turn its boost on or off (item boosts start off, water boosts start on).
- Thorium reactors get their cryofluid first, then turrets, then everything else.
- Conveyors, sorters, routers, bridges, mass drivers, unloaders, conduits, liquid routers and payload conveyors can't be built.
- Power still uses power nodes.

## Roguelike (both modes)

A roguelike layer on top of the Mindustry v8 Serpulo campaign. The campaign plays exactly as usual: you launch between sectors, build up hubs, use launch pads, and a lost sector works like in the normal game. The run adds rewards for you and curses for the enemies.

- Pressing **Roguedustry** swaps in a fresh campaign of its own. Your own campaign is put aside and comes back when you return to the main menu. A copy of it is also kept once in `Mindustry/roguedustry/campaign-backup`.
- In the Roguelike Campaign, research is free: a tech tree node unlocks as soon as the campaign would allow it. The Vanilla Campaign uses normal research.
- Sectors other than the named campaign maps stay locked until you capture Planetary Launch Terminal.
- Processors and the other logic blocks can't be built.
- There is no limit on how many units you can have.

### Rewards

Everything below belongs to one sector. It starts fresh on each new sector and is gone once you capture it. A captured sector has no more waves and is never attacked.

- **Relics** are big, rare effects. Each one comes with a random curse for the enemies. You pick 1 of 3 relic and curse pairs when you land, and again at levels 10, 20 and 30, so up to 4 per sector.
- **XP and levels**: every enemy you kill gives XP equal to its health. Each level gives a modifier pick. The level bar is under the wave counter.
- **Modifiers** belong to one turret, wall, drill or other building. Each has three: a Common one that makes it much better at what it already does, and an Uncommon and a Rare one that change how it works. They come up 67% Common, 28% Uncommon and 5% Rare. A modifier only shows up once you have researched what it is for. Each one can be taken once per sector; one you skip can come up again. Each of the 3 cards has a Reroll button you can use once, which swaps it for another modifier of the same rarity. So far: Duo, Scatter, Scorch and Hail; every wall (scrap, copper, titanium, plastanium, thorium, phase, surge); menders and repair points; the force projector; and every drill, the water and oil extractors and the cultivator.
- There are 10 relics and 60 modifiers so far. The Absolution relic removes 2 random curses.

### Curses

Curses make the enemies stronger, in the style of StarCraft II co-op. Each relic you pick brings one. They last until the sector is captured. There are 20 so far:

- Speedy, Bloodthirst, Far Sight, Reconstruction
- On death: Volatile, Dead Man's Switch (Tier 4 and 5), Mitosis (spawns Tier 1 units), Mech Virus (infects your nearest turret)
- Enemy buffs: Reactive Plating, Grudge, Warlord, Blink, Evolution, Reinforcements, Hungry Mechs, Overshields, Armored Swarms, Leadership
- Against your base: Shield Towers (enemy force projectors appear along the enemy's path), Conquest (destroyed turrets switch sides)

The icons are drawn by `tools/make-icons.py` (needs Python with Pillow and numpy).

## Install

The quickest way: run `./play.sh`. It builds the mod, puts it in the game's mods folder and starts the game. The first time, it also downloads the game (needs Java 17 or newer).

To do it by hand:

1. Run `./build.sh` (needs Java 17 or newer). It creates `build/Roguedustry.jar`.
2. Copy that file into your Mindustry mods folder:
   - Standalone: `%APPDATA%\Mindustry\mods\`
   - Steam: `<Steam>\steamapps\common\Mindustry\saves\mods\`
3. Restart the game and press **Roguedustry** in the main menu.

Needs Mindustry v8 (build 154 or newer) on desktop.

## Playing

- Press **Roguedustry** in the main menu to start or continue a run, then pick a sector on the planet map as usual.
- The **Roguedustry Modifiers** button under the wave counter pauses the game and shows the curses, your relics and modifiers, and your level and XP.
- Tick **Admin tools** in the Roguedustry menu to get an **Admin** button in the Roguedustry Modifiers window. From there you can capture the current sector, jump to any campaign map (listed in campaign order), unlock all research, give yourself picks or a level, and tap any relic, modifier or curse to add or remove it.
- Going back to the main menu leaves the run and brings back your own campaign.

## Run report

Run `./report.sh` to see the campaign sectors, the disabled blocks, the relics and modifiers, and the curses. It takes a few seconds and doesn't open the game.

## Screenshot check

Set `ROGUE_AUTOTEST=wireless` to check the Roguelike Campaign: it shows the mode picker, starts a wireless run on Ground Zero, checks the wave timing, places a drill and a Duo, checks that copper and water reach the Duo, opens the Duo's picker, adds and removes a relic and a curse in the Admin window, opens the sector list, saves screenshots to `ROGUE_SHOTS` and quits. `ROGUE_AUTOTEST=hud` checks a Vanilla Campaign run (below). `ROGUE_AUTOTEST=turrets` gives every turret modifier on Ground Zero and checks each one; `ROGUE_AUTOTEST=blocks` does the same for walls, menders, the force projector and production buildings. `ROGUE_AUTOTEST=curses` turns on the newer curses on Ground Zero, sets up a case for each and logs whether it worked.

Set `ROGUE_AUTOTEST=hud` and start the game. It starts a new run on Ground Zero, picks a relic, a curse and a modifier, sends a wave with every curse on, opens the Roguedustry Modifiers window, goes back to the menu, checks that your own campaign came back, saves a screenshot of each step to `ROGUE_SHOTS` and quits. Point the game's data folder at an empty folder so your own saves aren't touched (`APPDATA` on Windows, `XDG_DATA_HOME` on Linux). The test deletes any saved run.

## Known limits

- Single player only.
- Balance hasn't been tuned yet.
- An Overdrive Projector's boost and a relic or modifier speed boost don't add up: the stronger one wins.
- The game's research window can't mark logic blocks as disabled. They show as normal there, but can't be built.
- Long Range doesn't yet affect enemy units that follow an enemy commander (used on some attack maps).
