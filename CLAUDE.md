# Mindustry Survivors

A Mindustry mod called **Mindustry Survivors** everywhere: the menu button, intro page and mod list, and also the mod id (`mindustry-survivors`), the jar, the package (`mindustrysurvivors`), save keys, content names and the data folder. It used to be called Roguedustry; saves and settings from before the rename are not carried over. The menu button is added the normal way, after the game's own buttons.

A roguelike mod for Mindustry v8 (build 160.7), modeled on the user's game animechs (`C:\Users\DELL\Desktop\animechs`, itself derived from Mindustry). A run is one randomly generated map (`MapGen.java`): every map gets either 1-3 big mountains or a wide open plain, a few small hills (rock clusters under 60 tiles are cleared so paths stay wide), and lakes, a coast or both with ragged banks and uneven beaches, all walkable; the player starts in a clearing near the middle and enemies spawn on any open tile near the edge. Like animechs, every map blends a second biome from the first one's kin over 20-42% of it, and one in three a third over 10-20%. Play it like Vampire Survivors: survive 50 waves from animechs' wave script (spawned at 20% of the written amounts), one every 18.5 seconds; each wave deals 3 of 6 unit families (Dagger, Nova, Crawler, Flare, Risso, Retusa) into its 3 slots. There is no core and nothing to build. The player walks as the Walker (a ground unit with Nova's look, `Chase.java`) and every enemy pathfinds to them; if they die the run is lost. The Walker keeps its own aimed gun and starts with no turrets; turrets come from level-ups. Weapons are turrets that orbit the player and fire where and when the player shoots: all 18 Serpulo turrets and all 10 Erekir ones (`Weapons.erekir`), and the Settings window picks both, Serpulo only or Erekir only for new runs (max 6, levels 1 to 10, planned per turret in `Weapons.java`: items are the turret's own ammo types, plus water and cryofluid cooling and bullet traits; turret power is two layers: global multipliers on the base game's numbers (`Weapons.globalDamage`, `globalFireRate`, `globalRange`), then a per-turret level 1 damage per second picked by niche in `Weapons.load`: short range hits hard, long range and splash softer; every level also adds +20% damage). Wave, Tsunami, Parallax and Segment are support turrets with low damage (Parallax and Segment run in `Survivor.supportUpdate`; Segment is the only turret that fires on its own). Scathe's missiles are units that fly to the player's aim; their damage comes from a status effect (`Survivor.missilePower`). The Settings window also holds the Admin tab. Passives are named after buildings (max 6, levels 1 to 5, `Survivor.java`) and support two playstyles: kiting at range (speed, range, dodge) and tanking up close (armor, a bullet-blocking shield, health, healing, life steal, a burn aura). After wave 50 is cleared the player picks End run or Endless (`Arena.endless`): endless repeats waves 41 to 50, and each cycle of ten adds 100% enemy health and damage. The first wave comes after 3 seconds, and enemy damage rises 4% per wave (`Arena.enemyDamagePerWave`). The build menu is hidden during a run. Kills drop XP gems that the player picks up; each level offers 3 choices (4 with luck), healing when everything is maxed. Kills have a 1.5% chance to drop a Thorium heal and a 0.1% chance to drop a magnet (`sprites/pickups/magnet.png`) that pulls every gem to the player, while fewer than 5 Thorium or 3 magnets lie on the map. Boats can cross land at half speed during a run. Five difficulties named like the game's own (Casual to Eradication) scale only the enemy count, from 25% to 100%; XP per kill scales the other way. Relics are off for now (`SurvivorsRun.relicsOn`). Curses come from four difficulties past Eradication (Eradication +1 to +4, `SurvivorsRun.heat`): each curse has a point cost (`Curses.java`), and a run rolls curses at its start within that level's budget (4, 6, 8, 11) and minimum count (2, 2, 3, 3), at most 4; they show under the level bar. The tech tree is fully unlocked. The old campaign modes (wireless, campaign profile) are dormant code.

Curse ideas come from the StarCraft II co-op list: https://starcraft2coop.com/resources/mutators. Curses should be very punishing. Call them curses everywhere, in the UI, the code and the docs. Give curses our own names, not the StarCraft II ones; names from the user's own game animechs (`C:\Users\DELL\Desktop\animechs\docs\mutators.md`) are fine. Keep every description to one short sentence.

Theme colors: curses purple, relics gold, modifiers blue. Modifier tiers are switched off for now: every modifier has the same chance.

Relics and modifiers:
- A relic is a big, one-of-a-kind effect that changes how you play (drills send ore straight to the core, all drills mine three times as fast). You can only have one of each.
- Modifiers belong to one turret, wall (all sizes of one wall count as one), building group (menders with repair points, the force projector) or production building, three each: a Common one that is a big stat boost in that turret's niche (several stats at once is fine, defense and repair count), and an Uncommon and a Rare one that are truly unique and make the player go "what the...". They are only offered once the turret is researched, and each can be taken once per sector. The icon is the turret's own icon. Turret effects live in `src/mindustrysurvivors/TurretMods.java`, the rest in `src/mindustrysurvivors/BlockMods.java`. A price cut is one of the Common ones for walls.

## Writing rules
- No code comments, except to warn about a game quirk that would cause a bug if someone missed it. Keep those to one line.
- Write docs only when needed. Keep them short and plain: every sentence must make sense to a person who doesn't know the code. No jargon.
- Never mention Claude or AI in commits, pull requests, comments or docs. No "Co-Authored-By" lines and no "Generated with" footers.

## Commands
- `./build.sh` builds `build/MindustrySurvivors.jar`. It downloads the game jar into `libs/` the first time.
- `./report.sh` prints the campaign sectors, disabled blocks, relics, modifiers and curses, without opening the game.
- `./play.sh` builds, installs the mod into `%APPDATA%\Mindustry\mods\` and starts the game in English with `SURVIVORS_DEV=1` (no weather, no planet haze, no day and night) `SURVIVORS_NOMUSIC=1` (no music) and `SURVIVORS_QUIET=1` (sound effects at 50%). It uses the user's real game data, so don't use it for tests. When launching the game yourself for the user to play, also set `SURVIVORS_DEV=1`, `SURVIVORS_NOMUSIC=1` and `-Duser.language=en -Duser.country=US`.

## Testing
- After a change: build, then run `./report.sh`.
- If the UI changed, run the short screenshot check `SURVIVORS_AUTOTEST=arena` (about 40 seconds; see README).
- After a turret balance change, `SURVIVORS_AUTOTEST=damage` logs every turret's damage per second at levels 1 and 10 (about 4 minutes).
- Don't run full automated playthroughs. The user playtests.
- When launching the game, point `APPDATA` (Windows) or `XDG_DATA_HOME` (Linux) at an empty folder so the user's saves aren't touched.

## Where things are
- `src/mindustrysurvivors/Arena.java`: the run: rules, start and continue, wave release from spawn tiles, families, copies and which blocks can be placed
- `src/mindustrysurvivors/Weapons.java`: the 10 levels of every orbiting turret and how they change its bullets
- `src/mindustrysurvivors/Survivor.java`: weapons, passives, XP gems, level-up choices, spawning the player and losing on death
- `src/mindustrysurvivors/Chase.java`: the Walker, enemies chasing the player, enemies hitting air and ground, the `chase` stress test
- `src/mindustrysurvivors/MapGen.java`: the random map; `tools/MapPreview.java` draws maps to PNGs
- `src/mindustrysurvivors/Wireless.java` (dormant): wireless mode: banned blocks, moving items and liquids, the liquid store, per-building picks, saving them
- `src/mindustrysurvivors/WirelessUI.java`: the tap pop-up (ammo, fuel, coolant, boost) and the liquid display
- `src/mindustrysurvivors/Profile.java`: swaps the player's campaign and the run's campaign (settings and sector saves) and reloads the game's campaign data
- `src/mindustrysurvivors/SurvivorsRun.java`: run state and saving it, free research (Roguelike Campaign only), sector lock, picks per sector, relic and modifier effects
- `src/mindustrysurvivors/Rewards.java`: relics and modifiers, their rarity, how many picks per sector
- `src/mindustrysurvivors/Curses.java`: curses and the curse offer
- `src/mindustrysurvivors/SurvivorsUI.java`: menu, curse, relic and modifier picks, Modifiers window and HUD button
- `src/mindustrysurvivors/AutoTest.java`: the screenshot check
- `sprites/curses/`, `sprites/relics/`: curse and relic icons, drawn by `tools/make-icons.py` (needs Python with Pillow and numpy)
- `sprites/ui/title.png` (the menu title), `sprites/ui/menu-bg.png` (the backdrop of the full-screen Survivors pages) and `icon.png` (the mod list thumbnail, in the style of the Vampire Survivors cover) are drawn by `tools/make-title.py`. It needs the game source from `tools/fetch-source.sh` and the free Cormorant font in `libs/fonts/`; `python tools/make-title.py duo` picks the unit or turret in the thumbnail.

## Icon style
Match the game's campaign map icons (`core/assets-raw/sprites/ui/sectors/` in the game source):
- 64x64, flat colors, no outlines, transparent background. Use the game's palette (listed at the top of the script).
- Every edge is straight or exactly 45°. The script stops with an error if a shape breaks this. Corners get only a tiny cut; the long 45° slopes do the rounding.
- Draw irregular shapes, not rounded boxes or even-width strokes: pieces that taper to a point, flat-topped peaks, V notches, chipped corners. Width changes within a shape (for example 16px at the base, 6px at the tip).
- Shapes overlap in layers, the back one darker. Keep icons off-center and lopsided; shapes may run off the frame edge.
- Fill over half the frame (the game's average is about 57%). Use 2 to 4 colors, roughly half for the main color, a third for the second, the rest for one small bright accent.
- Pair the theme color with a gray or slate of similar brightness, not a darker version of itself. Give a big shape a light side and a shadow side in two close tones (split it down the middle).
- Curses lead with purple (the script swaps the reds they are drawn with for purples), relics with purple too. Don't reuse one layout for every icon; the game's icons don't share one.
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
- The game kills a ground unit standing on a solid tile, so never teleport the player onto a wall.
- Weapons made after loading need their recoil time set by hand; the game only fills it in while loading unit types, and without it the recoil grows forever and pushes the sprite off screen.
- A unit marked as made by a core is deleted as soon as the player lets go of it. The Walker only reports that flag to the game's input code, to hide the "respawn" hint.
- A flow field that has no target when it is first made is never refreshed, so it must always get at least one target.
- Units have no size of their own. Avenger grows a unit by stretching the sprites it just drew around its center, using the draw buffer's internal fields. It only works while the game draws sorted, which it does for units.

## Several agents at once
- Give each agent its own branch or git worktree. Two agents must never edit the same files in one folder.
