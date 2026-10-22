package roguedustry;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;

import static mindustry.Vars.*;

public class AutoTest{
    final String shotDir = OS.env("ROGUE_SHOTS") == null ? "rogue-shots" : OS.env("ROGUE_SHOTS");
    @Nullable String pendingShot;

    public static boolean enabled(){
        return System.getenv("ROGUE_AUTOTEST") != null;
    }

    public void begin(){
        Log.info("[Rogue-Test] Autotest started");
        Core.settings.put("backgroundpause", false);

        Events.run(Trigger.uiDrawEnd, () -> {
            if(pendingShot != null){
                Fi file = Core.files.absolute(shotDir).child(pendingShot + ".png");
                ScreenUtils.saveScreenshot(file);
                Log.info("[Rogue-Test] Screenshot @", file.absolutePath());
                pendingShot = null;
            }
        });

        later(60f * 3f, () -> {
            Log.info("[Rogue-Test] RESULT timeout");
            Core.app.exit();
        });

        later(2f, () -> {
            Core.settings.remove(RogueRun.saveKey);
            RogueRun.current = RogueRun.create();
            RogueRun.current.startMission(new Mission(SectorPresets.groundZero));
        });

        Events.run(Trigger.newGame, () -> later(3f, this::onMissionLoaded));
    }

    void onMissionLoaded(){
        RogueRun run = RogueRun.current;
        RogueUI rui = RoguedustryMod.ui;
        Log.info("[Rogue-Test] Mission loaded. active=@ slot=@ relicPending=@", RogueRun.active(), run.slot, run.pendingRelic);
        shot("01-relic");

        later(1f, () -> rui.choose(0));
        later(2f, () -> state.wave = 3);
        later(4f, () -> shot("02-modifier"));
        later(5f, () -> rui.choose(0));
        later(6f, () -> {
            run.mutators.set(Mutators.all);
            logic.runWave();
        });
        later(8f, () -> {
            Seq<Unit> enemies = state.rules.waveTeam.data().units.copy();
            Log.info("[Rogue-Test] enemies=@ speed=@ longRange=@", enemies.size, enemies.count(u -> u.hasEffect(Mutators.speedEffect)), enemies.count(u -> u.controller() instanceof Mutators.LongRangeAI));
            shot("03-hud");
            enemies.each(Unit::kill);
        });
        later(11f, () -> Log.info("[Rogue-Test] after kill: enemies=@ (revived)", Groups.unit.count(u -> u.team == state.rules.waveTeam)));
        later(12f, () -> {
            state.gameOver = true;
            Events.fire(new GameOverEvent(state.rules.defaultTeam));
        });
        later(17f, () -> shot("04-missions"));
        later(18f, () -> {
            Log.info("[Rogue-Test] RESULT cleared=@ rewards=@ slotGone=@ next=@", run.missionsCleared, run.rewards.keys().toSeq().map(r -> r.name), run.saveSlot() == null, run.missionChoices().map(Mission::name));
            Core.app.exit();
        });
    }

    void shot(String name){
        pendingShot = name;
    }

    static void later(float seconds, Runnable run){
        Timer.schedule(() -> Core.app.post(run), seconds);
    }
}
