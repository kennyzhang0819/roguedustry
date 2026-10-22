package roguedustry;

import arc.*;
import arc.files.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.maps.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.defense.*;

import static mindustry.Vars.*;

public class AutoTest{
    final String mode = System.getenv("ROGUE_AUTOTEST");
    final String shotDir = OS.env("ROGUE_SHOTS") == null ? "rogue-shots" : OS.env("ROGUE_SHOTS");
    @Nullable String pendingShot;
    int levelShots, lastWaveShot;
    boolean choosing;

    public static boolean enabled(){
        return System.getenv("ROGUE_AUTOTEST") != null;
    }

    public void begin(){
        Log.info("[Rogue-Test] Autotest mode '@'", mode);
        Core.settings.put("backgroundpause", false);
        Time.setDeltaProvider(() -> Math.min(Core.graphics.getDeltaTime() * 60f, 3f) * 3f);

        Events.run(Trigger.uiDrawEnd, () -> {
            if(pendingShot != null){
                Fi file = Core.files.absolute(shotDir).child(pendingShot + ".png");
                ScreenUtils.saveScreenshot(file);
                Log.info("[Rogue-Test] Screenshot @", file.absolutePath());
                pendingShot = null;
            }
        });

        Events.run(Trigger.newGame, () -> later(1f, this::onRunLoaded));
        Events.run(Trigger.update, this::update);
        Events.on(GameOverEvent.class, e -> {
            RogueRun run = RogueRun.current;
            Log.info("[Rogue-Test] RESULT winner=@ won=@ wave=@ level=@", e.winner, run != null && run.won, state.wave, run == null ? -1 : run.level);
            later(4f, () -> shot("99-final"));
            later(7f, () -> Core.app.exit());
        });

        Timer.schedule(() -> Core.app.post(this::logStatus), 5f, 5f);
        later(60f * 15f, () -> {
            Log.info("[Rogue-Test] RESULT timeout");
            Core.app.exit();
        });

        later(2f, () -> {
            String name = OS.env("ROGUE_MAP");
            Seq<Map> list = RogueUI.playableMaps();
            Map map = name == null ? MapGen.create(1) : list.find(m -> m.name().equalsIgnoreCase(name));
            Log.info("[Rogue-Test] Playable maps: @", list.map(Map::name));
            Log.info("[Rogue-Test] Starting run on '@' (@ spawns)", map.name(), map.spawns);
            RogueRun.start(map);
        });
    }

    void onRunLoaded(){
        if(!RogueRun.active()) return;
        RogueRun run = RogueRun.current;

        if("win".equals(mode)){
            state.rules.teams.get(state.rules.waveTeam).unitHealthMultiplier = 0.02f;
        }

        Log.info("[Rogue-Test] Run loaded. Whitelist: @ | charges: @", state.rules.bannedBlocks.toSeq().map(b -> b.name), run.charges);
        placeAll();
        testRefund();
        shot("01-start");

        if("hud".equals(mode)){
            later(2f, run::startWave);
            later(9f, () -> shot("02-wave1"));
            later(11f, () -> {
                Log.info("[Rogue-Test] RESULT hud wave=@ enemies=@ queued=@", state.wave, state.enemies, run.spawning());
                Core.app.exit();
            });
            return;
        }
        later(2f, () -> run.autoStart = true);
    }

    void update(){
        if(!RogueRun.active() || state.gameOver) return;
        RogueRun run = RogueRun.current;
        RogueUI rui = RoguedustryMod.ui;

        if(!choosing && rui.levelDialog != null && rui.levelDialog.isShown()){
            choosing = true;
            if(levelShots++ < 2) shot("levelup-" + run.level);
            later(1f, () -> {
                rui.choose(0);
                placeAll();
                choosing = false;
            });
        }

        if(run.wave != lastWaveShot && (run.wave == 3 || run.wave == 8 || run.wave == 15)){
            lastWaveShot = run.wave;
            int w = run.wave;
            later(6f, () -> shot("wave-" + w));
        }
    }

    void placeAll(){
        RogueRun run = RogueRun.current;
        Building core = state.rules.defaultTeam.core();
        Tile spawn = spawner.getSpawns().isEmpty() ? null : spawner.getSpawns().first();
        if(run == null || core == null || spawn == null) return;

        float angle = Angles.angle(core.x, core.y, spawn.worldx(), spawn.worldy());
        int placed = 0;

        for(Block block : run.charges.keys().toSeq()){
            int start = block instanceof Wall ? 9 : block instanceof MendProjector || block instanceof ForceProjector ? 3 : 5;
            int count = run.charges.get(block, 0);
            for(int i = 0; i < count; i++){
                if(placeNear(block, core.tile, angle, start)) placed++;
            }
        }
        Log.info("[Rogue-Test] Placed @ blocks, charges left: @", placed, run.charges);
    }

    boolean placeNear(Block block, Tile center, float angle, int startRadius){
        Team team = state.rules.defaultTeam;
        for(int r = startRadius; r < startRadius + 25; r++){
            for(int step = 0; step <= 12; step++){
                float a = angle + (step % 2 == 0 ? 1 : -1) * (step + 1) / 2 * 10f;
                int x = center.x + Mathf.round(Angles.trnsx(a, r)), y = center.y + Mathf.round(Angles.trnsy(a, r));
                if(Build.validPlace(block, team, x, y, 0)){
                    Build.beginPlace(null, block, team, x, y, 0, null);
                    Tile tile = world.tile(x, y);
                    if(tile.build instanceof ConstructBuild){
                        ConstructBlock.constructFinish(tile, block, null, (byte)0, team, null);
                    }
                    return tile.block() == block;
                }
            }
        }
        return false;
    }

    void testRefund(){
        RogueRun run = RogueRun.current;
        int pos = -1;
        for(var en : run.paid){
            if(en.value instanceof Wall){
                pos = en.key;
                break;
            }
        }
        if(pos == -1){
            Log.info("[Rogue-Test] Refund test skipped: no wall placed");
            return;
        }
        Tile tile = world.tile(pos);
        Block block = run.paid.get(pos);
        int before = run.charges.get(block, 0);
        Build.beginBreak(null, state.rules.defaultTeam, tile.x, tile.y);
        ConstructBlock.deconstructFinish(tile, block, null);
        int after = run.charges.get(block, 0);
        Log.info("[Rogue-Test] Refund test: @ charges @ -> @ (@)", block.name, before, after, after == before + 1 ? "OK" : "FAIL");
    }

    void logStatus(){
        if(!RogueRun.active()) return;
        RogueRun run = RogueRun.current;
        Building core = state.rules.defaultTeam.core();
        Log.info("[Rogue-Test] wave=@/@ spawning=@ enemies=@ level=@ xp=@/@ pending=@ buildings=@ coreHp=@ fps=@",
            run.wave, run.totalWaves(), run.spawning(), state.enemies, run.level, run.xp, run.xpToNext(), run.pendingLevelUps,
            state.rules.defaultTeam.data().buildings.size, core == null ? 0 : (int)core.health, Core.graphics.getFramesPerSecond());
    }

    void shot(String name){
        pendingShot = name;
    }

    /** Uses real time, because the level-up dialog pauses game time. */
    static void later(float seconds, Runnable run){
        Timer.schedule(() -> Core.app.post(run), seconds);
    }
}
