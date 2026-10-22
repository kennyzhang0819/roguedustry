# Roguedustry

A roguelike layer on top of the Mindustry v8 Serpulo campaign. The campaign plays exactly as usual: you launch between sectors, build up hubs, use launch pads, and a lost sector works like in the normal game. The run adds rewards for you and curses for the enemies.

- Pressing **Roguedustry** swaps in a fresh campaign of its own. Your own campaign is put aside and comes back when you return to the main menu. A copy of it is also kept once in `Mindustry/roguedustry/campaign-backup`.
- Research is free. A tech tree node unlocks as soon as the campaign would allow it.
- Sectors other than the named campaign maps stay locked until you capture Planetary Launch Terminal.
- Processors and the other logic blocks can't be built.
- There is no limit on how many units you can have.

### Rewards

- **Relics** are very strong. You pick 1 of 3 each time you land on a new sector.
- **Modifiers** are smaller changes, often to one turret or unit. They come in Common, Uncommon and Rare. You pick 1 of 3 for every 10 waves you clear on a sector.
- Both last for the whole run. There are only 4 relics and 3 modifiers so far. The Absolution relic removes 2 random curses.

### Mutators

Enemies get mutators, like in StarCraft II co-op. On every second new sector, right after your relic, you curse the enemies: pick 1 of 3 curses they don't have yet. They keep it for the whole run. There are 14 so far:

- Speed Freaks, Life Leech, Long Range, Just Die!
- On death: Self Destruction, Mutually Assured Destruction (bosses), Walking Infested (ground units release the first unit of their line), Alien Incubation (the same for air units)
- Enemy buffs: Barrier (a personal shield on the first hit), Avenger (grows bigger and stronger when allies die), Inspiration (bosses boost nearby enemies), Evasive Maneuvers (teleport forward when hit), Transmutation (rarely turns into the next unit of its line), Aggressive Deployment (an extra group between waves)

The icons are drawn by `tools/make-icons.py` (needs Python with Pillow and numpy).

## Install

1. Run `./build.sh` (needs Java 17 or newer). It creates `build/Roguedustry.jar`.
2. Copy that file into your Mindustry mods folder:
   - Standalone: `%APPDATA%\Mindustry\mods\`
   - Steam: `<Steam>\steamapps\common\Mindustry\saves\mods\`
3. Restart the game and press **Roguedustry** in the main menu.

Needs Mindustry v8 (build 154 or newer) on desktop.

## Playing

- Press **Roguedustry** in the main menu to start or continue a run, then pick a sector on the planet map as usual.
- The **Run Modifiers** button under the wave counter pauses the game and shows the curses, your relics and modifiers, and the wave of the next modifier pick.
- Going back to the main menu leaves the run and brings back your own campaign.

## Run report

Run `./report.sh` to see the campaign sectors, the disabled blocks, the relics and modifiers, and the curses. It takes a few seconds and doesn't open the game.

## Screenshot check

Set `ROGUE_AUTOTEST=hud` and start the game. It starts a new run on Ground Zero, picks a relic, a curse and a modifier, sends a wave with every curse on, opens the Run Modifiers window, goes back to the menu, checks that your own campaign came back, saves a screenshot of each step to `ROGUE_SHOTS` and quits. Point the game's data folder at an empty folder so your own saves aren't touched (`APPDATA` on Windows, `XDG_DATA_HOME` on Linux). The test deletes any saved run.

## Known limits

- Single player only.
- Balance hasn't been tuned yet.
- An Overdrive Projector's boost and a relic or modifier speed boost don't add up: the stronger one wins.
- The game's research window can't mark logic blocks as disabled. They show as normal there, but can't be built.
- Long Range doesn't yet affect enemy units that follow an enemy commander (used on some attack maps).
