package roguedustry;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.EventType.*;

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

        Timer.schedule(() -> Core.app.post(this::logStatus), 5f, 5f);
        later(60f * 3f, () -> {
            Log.info("[Rogue-Test] RESULT timeout");
            Core.app.exit();
        });

        later(2f, () -> {
            Core.settings.remove(RogueRun.saveKey);
            RogueRun.current = RogueRun.create();
            Log.info("[Rogue-Test] New run. Unlocked: @", RogueRun.current.unlocked.toSeq().map(c -> c.name));
            Log.info("[Rogue-Test] Maps: @", RogueRun.current.missionChoices().map(Mission::name));
            RogueRun.current.startMission(new Mission(SectorPresets.groundZero));
        });

        Events.run(Trigger.newGame, () -> later(3f, this::onMissionLoaded));
    }

    void onMissionLoaded(){
        RogueRun run = RogueRun.current;
        Log.info("[Rogue-Test] Mission loaded. active=@ winWave=@ attack=@ whitelist=@", RogueRun.active(), state.rules.winWave, state.rules.attackMode, state.rules.bannedBlocks.toSeq().map(b -> b.name));
        Log.info("[Rogue-Test] Researchable: @", Tech.researchable(run).map(n -> n.content.name + Seq.with(Tech.cost(run, n))));
        shot("01-mission");

        RogueUI rui = RoguedustryMod.ui;
        later(1f, rui::showResearch);
        later(3f, () -> shot("02-research"));
        later(4f, () -> {
            rui.researchDialog.hide();
            state.rules.defaultTeam.core().items.add(Items.copper, 3000);
            Tech.researchable(run).each(n -> {
                if(Tech.canAfford(run, n)) run.research(n);
            });
            Log.info("[Rogue-Test] After research, whitelist=@", state.rules.bannedBlocks.toSeq().map(b -> b.name));
            state.gameOver = true;
            Events.fire(new GameOverEvent(state.rules.defaultTeam));
        });
        later(10f, () -> shot("03-pick"));
        later(11f, () -> rui.choose(0));
        later(13f, () -> shot("04-missions"));
        later(14f, () -> {
            Log.info("[Rogue-Test] RESULT cleared=@ modifiers=@ picks=@ next=@", run.missionsCleared, run.modifiers, run.pendingPicks, run.missionChoices().map(Mission::name));
            Core.app.exit();
        });
    }

    void logStatus(){
        if(!RogueRun.active()) return;
        Log.info("[Rogue-Test] wave=@ enemies=@ fps=@", state.wave, state.enemies, Core.graphics.getFramesPerSecond());
    }

    void shot(String name){
        pendingShot = name;
    }

    static void later(float seconds, Runnable run){
        Timer.schedule(() -> Core.app.post(run), seconds);
    }
}
