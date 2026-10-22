# Roguedustry

A roguelike run through the Mindustry v8 campaign. The game itself is unchanged: you mine, build conveyors, factories, power and turrets as usual. The run adds the roguelike parts on top.

- Play the Serpulo campaign maps one after another, starting with a locked tech tree.
- Research is free. A tech tree node unlocks as soon as the campaign would allow it: when you enter or clear a map, when you first get an item, or when the nodes before it are unlocked. Maps open up the same way.
- After you beat Planetary Launch Terminal, random maps open up and the run goes on as long as you last.
- Processors and the other logic blocks are left out of the run.
- Your normal campaign progress is never touched.

### Losing

Lose a single map and the run is over: you start again from Ground Zero. Each map has one save of its own. If you quit, **Continue** picks up from that save, so quitting can't undo a bad situation. The save is deleted when the map ends. Other saves of the same map won't continue the run.

### Rewards

- **Relics** are very strong. You pick 1 of 3 at the start of each map.
- **Modifiers** are smaller changes, often to one turret or unit. They come in Common, Uncommon and Rare. You pick 1 of 3 at 25%, 50% and 75% of each map (waves survived, or enemy buildings destroyed on attack maps).
- Both last for the whole run. There are only 3 relics and 3 modifiers so far.

### Terror and mutators

Enemies get mutators, like in StarCraft II co-op. The terror level goes up by 1 for every map you clear. Each terror level adds 2 mutator points, and each mutator costs points, so later maps get more and nastier mutators. Each map shows its mutators before you pick it. There are 4 so far: Speed Freaks, Life Leech, Long Range and Just Die!.

## Install

1. Run `./build.sh` (needs Java 17 or newer). It creates `build/Roguedustry.jar`.
2. Copy that file into your Mindustry mods folder:
   - Standalone: `%APPDATA%\Mindustry\mods\`
   - Steam: `<Steam>\steamapps\common\Mindustry\saves\mods\`
3. Restart the game and press **Roguedustry** in the main menu.

Needs Mindustry v8 (build 154 or newer) on desktop.

## Playing

- The panel under the wave counter shows the map and its goal, the terror level and mutators, how far you are toward the next modifier, and a **Tech tree** button. It opens the campaign's tech tree without costs. Like in the campaign, locked nodes show a padlock and "???", and hovering a node shows what it still needs. Use this button, not the game's own tech tree.

## Run report

Run `./report.sh` to see every campaign map with what it needs to unlock, what is unlocked at the start, the relics and modifiers, and the mutators with their points. It takes a few seconds and doesn't open the game.

## Screenshot check

Set `ROGUE_AUTOTEST=hud` and start the game. It starts a new run on Ground Zero, picks a relic, opens the tech tree, picks a modifier, sends a wave with every mutator on, pretends the map was cleared, saves a screenshot of each step to `ROGUE_SHOTS` and quits. Point the game's data folder at an empty folder so your own saves aren't touched (`APPDATA` on Windows, `XDG_DATA_HOME` on Linux). The test deletes any saved run.

## Known limits

- Single player only.
- Balance hasn't been tuned yet.
- An Overdrive Projector's boost and a relic or modifier speed boost don't add up: the stronger one wins.
- Long Range doesn't yet affect enemy units that follow an enemy commander (used on some attack maps).
