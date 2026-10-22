# Roguedustry

A roguelike run through the Mindustry v8 campaign. The game itself is unchanged: you mine, build conveyors, factories, power and turrets as usual. The run adds the roguelike parts on top.

- Play the Serpulo campaign maps one after another, starting with a locked tech tree.
- Research is free. A tech tree node unlocks as soon as the campaign would allow it: when you enter or clear a map, when you first get an item, or when the nodes before it are unlocked. Maps open up the same way.
- After you beat Planetary Launch Terminal, random maps open up and the run goes on as long as you last.
- Processors and the other logic blocks, and the launch pads and other blocks that move things between maps, are disabled. They still show in the tech tree, greyed out.
- There is no limit on how many units you can have.
- Your normal campaign progress is never touched.

### Losing

Lose a single map and the run is over: you start again from Ground Zero. Each map has one save of its own. If you quit, **Continue** picks up from that save, so quitting can't undo a bad situation. The save is deleted when the map ends. Other saves of the same map won't continue the run.

### Rewards

- **Relics** are very strong. You pick 1 of 3 at the start of each map.
- **Modifiers** are smaller changes, often to one turret or unit. They come in Common, Uncommon and Rare. You pick 1 of 3 for every 10 waves you clear on a map, so a 45-wave map gives 4 picks. A pick you earn on the last wave carries over to the next map.
- Both last for the whole run. There are only 4 relics and 3 modifiers so far. The Absolution relic removes 2 random curses.

### Mutators

Enemies get mutators, like in StarCraft II co-op. Every second map, right after your relic, you curse the enemies: pick 1 of 3 curses they don't have yet. They keep it for the whole run. There are 14 so far:

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

- Under the wave counter there are two buttons. **Run** pauses the game and shows the map's goal, the curses, your relics and modifiers, and the wave of the next modifier pick.
- **Tech tree** opens the campaign's tech tree without costs. Like in the campaign, locked nodes show a padlock and "???", and hovering a node shows what it still needs. Use this button, not the game's own tech tree.

## Run report

Run `./report.sh` to see every campaign map with what it needs to unlock, what is unlocked at the start, the relics and modifiers, and the mutators. It takes a few seconds and doesn't open the game.

## Screenshot check

Set `ROGUE_AUTOTEST=hud` and start the game. It starts a new run on Ground Zero, picks a relic and a curse, opens the tech tree, picks a modifier, sends a wave with every mutator on, opens the Run window, pretends the map was cleared, saves a screenshot of each step to `ROGUE_SHOTS` and quits. Point the game's data folder at an empty folder so your own saves aren't touched (`APPDATA` on Windows, `XDG_DATA_HOME` on Linux). The test deletes any saved run.

## Known limits

- Single player only.
- Balance hasn't been tuned yet.
- An Overdrive Projector's boost and a relic or modifier speed boost don't add up: the stronger one wins.
- Long Range doesn't yet affect enemy units that follow an enemy commander (used on some attack maps).
