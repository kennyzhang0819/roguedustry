# Roguedustry

A roguelike run through the Mindustry v8 campaign. The game itself is unchanged: you mine, build conveyors, factories, power and turrets as usual. The run adds the roguelike parts on top.

- Play the Serpulo campaign maps one after another, starting with a locked tech tree.
- Research with the items you collect. Maps open up the same way they do in the campaign: clear the map before it and research what it asks for.
- Each cleared map lets you pick 1 of 3 modifiers. Modifiers last for the whole run and some can be picked more than once. Red ones are risky: they help and hurt.
- Items left in your core when you clear a map are saved for research.
- Lose a map and the run is over. Your score is how many maps you cleared.
- After you beat Planetary Launch Terminal, random maps open up and the run goes on as long as you last.
- Your normal campaign progress is never touched.

## Install

1. Run `./build.sh` (needs Java 17 or newer). It creates `build/Roguedustry.jar`.
2. Copy that file into your Mindustry mods folder:
   - Standalone: `%APPDATA%\Mindustry\mods\`
   - Steam: `<Steam>\steamapps\common\Mindustry\saves\mods\`
3. Restart the game and press **Roguedustry** in the main menu.

Needs Mindustry v8 (build 154 or newer) on desktop.

## Playing

- The panel under the wave counter shows the map, its goal, and a **Research** button. Use this button, not the game's own tech tree.
- The run is saved between maps. If you quit in the middle of a map, you can replay it from the start, or load its save from Load Game to carry on.

## Run report

Run `./report.sh` to see every campaign map with what it needs to unlock, the total research cost, and the list of modifiers. It takes a few seconds and doesn't open the game.

## Screenshot check

Set `ROGUE_AUTOTEST=hud` and start the game. It starts a new run on Ground Zero, opens the research window, pretends the map was cleared, picks a modifier, saves a screenshot of each step to `ROGUE_SHOTS` and quits. Point the game's data folder at an empty folder so your own saves aren't touched (`APPDATA` on Windows, `XDG_DATA_HOME` on Linux). The test deletes any saved run.

## Known limits

- Single player only.
- Research costs are the campaign's own, which assume items from many maps over many hours. Balance hasn't been tuned yet.
- An Overdrive Projector's boost and a modifier's speed boost don't add up: the stronger one wins.
