# Roguedustry

A roguelike mod for Mindustry v8 (build 160.7). It is a layer of modifiers on the real Serpulo campaign, played in a separate campaign profile so the player's own campaign is never changed. Launching, hubs, launch pads and losing sectors work exactly like the base game. Research is free: each node unlocks as soon as its campaign requirements are met. Logic blocks can't be built. Sectors other than the named campaign maps stay locked until Planetary Launch Terminal is captured. Each new sector gives a relic pick, every second one also a curse for the enemies (a StarCraft II co-op style mutator), and every 10 waves cleared on a sector gives a modifier pick. All of them last the whole run.

Mutator ideas come from the StarCraft II co-op list: https://starcraft2coop.com/resources/mutators. Mutators should be very punishing. In the UI they are called curses.

Theme colors: curses red, relics purple, modifiers by rarity (common green, uncommon blue, rare gold).

## Writing rules
- No code comments, except to warn about a game quirk that would cause a bug if someone missed it. Keep those to one line.
- Write docs only when needed. Keep them short and plain: every sentence must make sense to a person who doesn't know the code. No jargon.
- Never mention Claude or AI in commits, pull requests, comments or docs. No "Co-Authored-By" lines and no "Generated with" footers.

## Commands
- `./build.sh` builds `build/Roguedustry.jar`. It downloads the game jar into `libs/` the first time.
- `./report.sh` prints the campaign sectors, disabled blocks, relics, modifiers and mutators, without opening the game.
- To install, copy `build/Roguedustry.jar` into `%APPDATA%\Mindustry\mods\`.

## Testing
- After a change: build, then run `./report.sh`.
- If the UI changed, run one short screenshot check (`ROGUE_AUTOTEST=hud`, about 30 seconds; see README).
- Don't run full automated playthroughs. The user playtests.
- When launching the game, point `APPDATA` (Windows) or `XDG_DATA_HOME` (Linux) at an empty folder so the user's saves aren't touched.

## Where things are
- `src/roguedustry/Profile.java`: swaps the player's campaign and the run's campaign (settings and sector saves) and reloads the game's campaign data
- `src/roguedustry/RogueRun.java`: run state and saving it, free research, sector lock, picks per sector, relic and modifier effects
- `src/roguedustry/Rewards.java`: relics and modifiers, their rarity, how many picks per sector
- `src/roguedustry/Mutators.java`: enemy mutators and the mutator offer
- `src/roguedustry/RogueUI.java`: menu, curse, relic and modifier picks, Run Modifiers window and HUD button
- `src/roguedustry/AutoTest.java`: the screenshot check
- `sprites/mutators/`: mutator icons, drawn by `tools/make-icons.py` (needs Python with Pillow and numpy)

## Icon style
Match the game's campaign map icons (`core/assets-raw/sprites/ui/sectors/` in the game source):
- 64x64, flat colors, no outlines, transparent background. Use the game's palette (listed at the top of the script).
- Every edge is straight or exactly 45°. The script stops with an error if a shape breaks this. Every corner, inside and out, has a small 45° cut, so shapes look slightly rounded.
- Shapes are thick bands (about 10px), overlapping and touching each other. Pieces never float apart.
- Fill most of the frame. Bold shapes, few tiny details. A main color plus a darker partner of the same hue for depth.
- Compare new icons side by side with the game's icons before committing.
- `tools/`: run report, icon script and source download script (not part of the mod)

## Game source
- Official repo: https://github.com/Anuken/Mindustry, tag **v160.7**, the same version `build.sh` uses.
- Check how the game really behaves in its source before relying on it. Don't guess from memory.
- Run `./tools/fetch-source.sh` to download it into `libs/mindustry-src` (a few seconds). Only do this when you need it. It's for reading only: never edit it.
- To change game version, update it in both `build.sh` and `tools/fetch-source.sh`, then delete `libs/`.

## Game quirks we hit
- The game reads unlocks, research and sector data once at startup. After swapping campaign profiles everything must be reloaded, some of it through reflection.
- If the game closes during a run, the run's campaign is still the live one at the next start. It is swapped back on startup.
- A sector launch passes through the menu for a couple of frames. Only a longer stay in the menu leaves the run.
- "Sector launch" fires for every new sector landing, including the first one.
- The game shows the sector name for 5 seconds after the landing animation, over any dialog. Picks wait for it.
- The game's research window can't mark blocks as disabled from a mod, so logic blocks are only banned.
- The game pauses when its window loses focus.
- Mods marked `hidden` in `mod.hjson` can't add content, so the mod is not hidden (it adds status effects).
- Unit, weapon and turret ranges are shared by both teams. Enemy-only range changes must be undone right after the enemy uses them.
- Units have no size of their own. Avenger grows a unit by stretching the sprites it just drew around its center, using the draw buffer's internal fields. It only works while the game draws sorted, which it does for units.

## Several agents at once
- Give each agent its own branch or git worktree. Two agents must never edit the same files in one folder.
