# Roguedustry

A roguelike mod for Mindustry v8 (build 160.7). A run plays the Serpulo campaign maps in order with a locked tech tree. Research is free: each node unlocks as soon as its campaign requirements are met. Logic blocks (processors, memory, displays and so on) are left out of the run. The player picks a relic at the start of each map and modifiers during it; both last the whole run. Enemies get StarCraft II co-op style mutators, paid for with points from a terror level that rises every map. Losing one map ends the run for good. After Planetary Launch Terminal, random maps keep the run going. The base game stays unchanged; everything is built on top.

Mutator ideas come from the StarCraft II co-op list: https://starcraft2coop.com/resources/mutators. Mutators should be very punishing.

## Writing rules
- No code comments, except to warn about a game quirk that would cause a bug if someone missed it. Keep those to one line.
- Write docs only when needed. Keep them short and plain: every sentence must make sense to a person who doesn't know the code. No jargon.
- Never mention Claude or AI in commits, pull requests, comments or docs. No "Co-Authored-By" lines and no "Generated with" footers.

## Commands
- `./build.sh` builds `build/Roguedustry.jar`. It downloads the game jar into `libs/` the first time.
- `./report.sh` prints the campaign maps with what unlocks them, the starting tech, relics, modifiers and mutators, without opening the game.
- To install, copy `build/Roguedustry.jar` into `%APPDATA%\Mindustry\mods\`.

## Testing
- After a change: build, then run `./report.sh`.
- If the UI changed, run one short screenshot check (`ROGUE_AUTOTEST=hud`, about 15 seconds; see README).
- Don't run full automated playthroughs. The user playtests.
- When launching the game, point `APPDATA` (Windows) or `XDG_DATA_HOME` (Linux) at an empty folder so the user's saves aren't touched.

## Where things are
- `src/roguedustry/RogueRun.java`: run state, saving the run, the map's save slot, starting maps, wins and losses, relic and modifier effects
- `src/roguedustry/Tech.java`: which tech tree nodes and campaign maps are unlocked, read from the game's own tech tree
- `src/roguedustry/Mission.java`: one map of the run (a campaign map or a random one) and its rules
- `src/roguedustry/Rewards.java`: relics and modifiers, their rarity, how many picks per map
- `src/roguedustry/Mutators.java`: enemy mutators, terror level and points
- `src/roguedustry/MapGen.java`: random maps with a land path and a river to the core
- `src/roguedustry/RogueUI.java`: menu, map picker, relic and modifier picks, side panel
- `src/roguedustry/TechTreeDialog.java`: the tech tree window, a copy of the game's research window that reads the run's unlocks
- `tools/`: run report and source download script (not part of the mod)

## Game source
- Official repo: https://github.com/Anuken/Mindustry, tag **v160.7**, the same version `build.sh` uses.
- Check how the game really behaves in its source before relying on it. Don't guess from memory.
- Run `./tools/fetch-source.sh` to download it into `libs/mindustry-src` (a few seconds). Only do this when you need it. It's for reading only: never edit it.
- To change game version, update it in both `build.sh` and `tools/fetch-source.sh`, then delete `libs/`.

## Game quirks we hit
- A new map's rules aren't set yet when the "world loaded" event fires. Start runs on the "new game" event.
- The game pauses when its window loses focus.
- Outside the campaign the game treats everything as unlocked. The run locks blocks with the build menu whitelist. Never call `unlock()` on game content: it changes the player's real campaign.
- The unit ban list also stops enemy factories, so locked units are blocked by stalling the player's own factories instead.
- Many game texts show "???" for things locked in the player's real campaign. Write our own text for names and requirements.
- `playMap()` swaps in the wave list of the previous world. Generated maps set their waves again after loading.
- The game's game-over window opens 3 seconds after a map ends. During a run we close it and show our own.
- Mods marked `hidden` in `mod.hjson` can't add content, so the mod is not hidden (it adds a status effect).
- Unit, weapon and turret ranges are shared by both teams. Enemy-only range changes must be undone right after the enemy uses them.
- The game's "save on start" option makes a save of the map's start. During a run it is deleted, or a lost map could be replayed.
- When a save loads, the slot it came from is only known one frame after the "world loaded" event.
- The game's research window reads the real campaign's unlocks and can't be changed from a mod, so we draw our own.
- The tree layout keeps state in its nodes. Laying out the same nodes twice scatters them, so the tech tree window is rebuilt each time it opens.

## Several agents at once
- Give each agent its own branch or git worktree. Two agents must never edit the same files in one folder.
