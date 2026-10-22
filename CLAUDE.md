# Roguedustry

A roguelike mod for Mindustry v8 (build 160.7). A run plays the Serpulo campaign maps in order with a locked tech tree that opens up the way the campaign does. Each cleared map gives a pick of 1 of 3 permanent modifiers. Losing a map ends the run. After Planetary Launch Terminal, random maps keep the run going. The base game stays unchanged; everything is built on top.

## Writing rules
- No code comments, except to warn about a game quirk that would cause a bug if someone missed it. Keep those to one line.
- Write docs only when needed. Keep them short and plain: every sentence must make sense to a person who doesn't know the code. No jargon.
- Never mention Claude or AI in commits, pull requests, comments or docs. No "Co-Authored-By" lines and no "Generated with" footers.

## Commands
- `./build.sh` builds `build/Roguedustry.jar`. It downloads the game jar into `libs/` the first time.
- `./report.sh` prints the campaign maps with what unlocks them, total research cost and the modifier list, without opening the game.
- To install, copy `build/Roguedustry.jar` into `%APPDATA%\Mindustry\mods\`.

## Testing
- After a change: build, then run `./report.sh`.
- If the UI changed, run one short screenshot check (`ROGUE_AUTOTEST=hud`, about 15 seconds; see README).
- Don't run full automated playthroughs. The user playtests.
- When launching the game, point `APPDATA` (Windows) or `XDG_DATA_HOME` (Linux) at an empty folder so the user's saves aren't touched.

## Where things are
- `src/roguedustry/RogueRun.java`: run state, saving the run, starting maps, wins and losses, modifier effects during a map
- `src/roguedustry/Tech.java`: research and which campaign maps are open, read from the game's own tech tree
- `src/roguedustry/Mission.java`: one map of the run (a campaign map or a random one) and its rules
- `src/roguedustry/Modifiers.java`: the modifier list
- `src/roguedustry/MapGen.java`: random maps with a land path and a river to the core
- `src/roguedustry/RogueUI.java`: menu, map picker, research window, modifier pick, side panel
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

## Several agents at once
- Give each agent its own branch or git worktree. Two agents must never edit the same files in one folder.
