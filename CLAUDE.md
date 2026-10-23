# Roguedustry

A run has one of two modes, picked when it starts. Roguelike Campaign is wireless (no belts or pipes, every building trades items with the core and liquids with a shared liquid store, tap turrets to pick ammo and coolant), after the first wave it shortens the wait between waves by 90% and spawns 2.5 times the enemies, and the core unit builds twice as fast. Vanilla Campaign keeps belts and pipes. Both get the relics, curses and modifiers, and both are Serpulo only. Nothing changes outside a run.

A roguelike mod for Mindustry v8 (build 160.7). It is a layer of modifiers on the real Serpulo campaign, played in a separate campaign profile so the player's own campaign is never changed. Launching, hubs, launch pads and losing sectors work exactly like the base game. In the Roguelike Campaign research is free: each node unlocks as soon as its campaign requirements are met. The Vanilla Campaign uses normal research. Logic blocks can't be built. Sectors other than the named campaign maps stay locked until Planetary Launch Terminal is captured. Relics, curses (in the style of StarCraft II co-op) and modifiers belong to one sector: they are saved in the sector's own save file, start fresh on each new sector and are wiped when it is captured. A relic pick (each relic paired with a random curse) comes on landing and at levels 10, 20 and 30. Kills give XP, and each level gives a modifier pick. Sector invasions are off.

Curse ideas come from the StarCraft II co-op list: https://starcraft2coop.com/resources/mutators. Curses should be very punishing. Call them curses everywhere, in the UI, the code and the docs. Give curses our own names, not the StarCraft II ones; names from the user's own game animechs (`C:\Users\DELL\Desktop\animechs\docs\mutators.md`) are fine. Keep every description to one short sentence.

Theme colors: curses red, relics purple, modifiers by rarity (common green, uncommon blue, rare gold).

Relics and modifiers:
- A relic is a big, one-of-a-kind effect that changes how you play (drills send ore straight to the core, all drills mine three times as fast). You can only have one of each.
- Modifiers belong to one turret, wall (all sizes of one wall count as one), building group (menders with repair points, the force projector) or production building, three each: a Common one that is a big stat boost in that turret's niche (several stats at once is fine, defense and repair count), and an Uncommon and a Rare one that are truly unique and make the player go "what the...". They are only offered once the turret is researched, and each can be taken once per sector. The icon is the turret's own icon. Turret effects live in `src/roguedustry/TurretMods.java`, the rest in `src/roguedustry/BlockMods.java`. A price cut is one of the Common ones for walls.

## Writing rules
- No code comments, except to warn about a game quirk that would cause a bug if someone missed it. Keep those to one line.
- Write docs only when needed. Keep them short and plain: every sentence must make sense to a person who doesn't know the code. No jargon.
- Never mention Claude or AI in commits, pull requests, comments or docs. No "Co-Authored-By" lines and no "Generated with" footers.

## Commands
- `./build.sh` builds `build/Roguedustry.jar`. It downloads the game jar into `libs/` the first time.
- `./report.sh` prints the campaign sectors, disabled blocks, relics, modifiers and curses, without opening the game.
- `./play.sh` builds, installs the mod into `%APPDATA%\Mindustry\mods\` and starts the game. It uses the user's real game data, so don't use it for tests.

## Testing
- After a change: build, then run `./report.sh`.
- If the UI changed, run one short screenshot check (`ROGUE_AUTOTEST=wireless` for the Roguelike Campaign or `hud` for the Vanilla Campaign, about 30 seconds each; see README).
- Don't run full automated playthroughs. The user playtests.
- When launching the game, point `APPDATA` (Windows) or `XDG_DATA_HOME` (Linux) at an empty folder so the user's saves aren't touched.

## Where things are
- `src/roguedustry/Wireless.java`: wireless mode: banned blocks, moving items and liquids, the liquid store, per-building picks, saving them
- `src/roguedustry/WirelessUI.java`: the tap pop-up (ammo, fuel, coolant, boost) and the liquid display
- `src/roguedustry/Profile.java`: swaps the player's campaign and the run's campaign (settings and sector saves) and reloads the game's campaign data
- `src/roguedustry/RogueRun.java`: run state and saving it, free research (Roguelike Campaign only), sector lock, picks per sector, relic and modifier effects
- `src/roguedustry/Rewards.java`: relics and modifiers, their rarity, how many picks per sector
- `src/roguedustry/Curses.java`: curses and the curse offer
- `src/roguedustry/RogueUI.java`: menu, curse, relic and modifier picks, Roguedustry Modifiers window and HUD button
- `src/roguedustry/AutoTest.java`: the screenshot check
- `sprites/curses/`, `sprites/relics/`: curse and relic icons, drawn by `tools/make-icons.py` (needs Python with Pillow and numpy)

## Icon style
Match the game's campaign map icons (`core/assets-raw/sprites/ui/sectors/` in the game source):
- 64x64, flat colors, no outlines, transparent background. Use the game's palette (listed at the top of the script).
- Every edge is straight or exactly 45°. The script stops with an error if a shape breaks this. Corners get only a tiny cut; the long 45° slopes do the rounding.
- Draw irregular shapes, not rounded boxes or even-width strokes: pieces that taper to a point, flat-topped peaks, V notches, chipped corners. Width changes within a shape (for example 16px at the base, 6px at the tip).
- Shapes overlap in layers, the back one darker. Keep icons off-center and lopsided; shapes may run off the frame edge.
- Fill over half the frame (the game's average is about 57%). Use 2 to 4 colors, roughly half for the main color, a third for the second, the rest for one small bright accent.
- Pair the theme color with a gray or slate of similar brightness, not a darker version of itself. Give a big shape a light side and a shadow side in two close tones (split it down the middle).
- Curses lead with red, relics with purple. Don't reuse one layout for every icon; the game's icons don't share one.
- Every new curse or relic gets its icon in the same change: add a function to `tools/make-icons.py`, run it, and commit the PNG.
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
