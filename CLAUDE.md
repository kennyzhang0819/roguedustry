# Roguedustry

A roguelike tower-defense mod for Mindustry v8 (build 160.7). The player places turrets, walls, shields, menders and drills. Waves are written by hand. Kills give XP, and each level up offers a choice of 3 block packs. Drills mine copper into the core, and copper buys packs in the shop.

## Writing rules
- No code comments, except to warn about a game quirk that would cause a bug if someone missed it. Keep those to one line.
- Write docs only when needed. Keep them short and plain: every sentence must make sense to a person who doesn't know the code. No jargon.
- Never mention Claude or AI in commits, pull requests, comments or docs. No "Co-Authored-By" lines and no "Generated with" footers.

## Commands
- `./build.sh` builds `build/Roguedustry.jar`. It downloads the game jar into `libs/` the first time.
- `./balance.sh` prints wave strength, XP per level and turret damage in about 2 seconds, without opening the game.
- To install, copy `build/Roguedustry.jar` into `%APPDATA%\Mindustry\mods\`.

## Testing
- After a change: build, then run `./balance.sh`.
- If the UI changed, run one short screenshot check (`ROGUE_AUTOTEST=hud`, about 15 seconds; see README).
- Don't run full automated playthroughs. The user playtests.
- When launching the game, point `APPDATA` at an empty folder so the user's saves aren't touched.

## Where things are
- `src/roguedustry/Waves.java`: the wave list
- `src/roguedustry/Packs.java`: starting blocks, level-up packs, tiers, shop and drill prices
- `src/roguedustry/MapGen.java`: random map with a land path and a river to the core
- `src/roguedustry/RogueRun.java`: run setup, spawning, XP, build charges
- `src/roguedustry/RogueUI.java`: menu button, map picker, side panel, level-up dialog, shop
- `tools/`: balance report and source download script (not part of the mod)

## Game source
- Official repo: https://github.com/Anuken/Mindustry, tag **v160.7**, the same version `build.sh` uses.
- Check how the game really behaves in its source before relying on it. Don't guess from memory.
- Run `./tools/fetch-source.sh` to download it into `libs/mindustry-src` (a few seconds). Only do this when you need it. It's for reading only: never edit it.
- To change game version, update it in both `build.sh` and `tools/fetch-source.sh`, then delete `libs/`.

## Game quirks we hit
- Vanilla waves must stay switched on, or enemies don't target the core.
- A new map's rules aren't set yet when the "world loaded" event fires. Start runs on the "new game" event.
- The game pauses when its window loses focus.
- The game stops building any block that isn't whitelisted, even one already started. A block keeps its whitelist spot until its last paid copy is finished.
- Removing a block puts its build cost into the core unless the deconstruct refund is set to 0.
- Maps loaded from a file bring the game's default waves; clear them or they spawn on top of ours.

## Several agents at once
- Give each agent its own branch or git worktree. Two agents must never edit the same files in one folder.
