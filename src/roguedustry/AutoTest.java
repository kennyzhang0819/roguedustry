package roguedustry;

import arc.*;
import arc.files.*;
import arc.scene.ui.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.*;

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
        Log.info("[Rogue-Test] Mission loaded. active=@ slot=@ cursePending=@ relicPending=@ unitCap=@", RogueRun.active(), run.slot, run.pendingCurse, run.pendingRelic, mindustry.entities.Units.getCap(state.rules.defaultTeam));
        shot("01-relic");

        later(0.7f, () -> rui.choose(0));
        later(0.9f, () -> run.pendingCurse = true);
        later(1.4f, () -> shot("00-curse"));
        later(1.8f, () -> {
            rui.choose(0);
            Log.info("[Rogue-Test] curses=@ microProcessor unlocked=@ buildable=@", run.mutators.map(m -> m.name), run.unlocked.contains(mindustry.content.Blocks.microProcessor), state.rules.bannedBlocks.contains(mindustry.content.Blocks.microProcessor));
            run.mutators.set(Mutators.all);
            Core.settings.remove(TechTreeDialog.infoKey);
        });
        later(2.4f, rui::showResearch);
        later(4f, () -> shot("02-tech-info"));
        later(5f, () -> {
            for(var e : Core.scene.root.getChildren().copy()){
                if(e instanceof Dialog d) d.hide();
            }
        });
        later(6.5f, rui::showResearch);
        later(7.3f, () -> {
            var view = rui.researchDialog.view;
            for(var e : view.getChildren()){
                if(e instanceof ImageButton b && b.userObject == mindustry.content.Blocks.microProcessor.techNode){
                    view.hoverNode = b;
                    view.rebuild();
                }
            }
        });
        later(8f, () -> shot("03-tech"));
        later(8.5f, () -> rui.researchDialog.hide());
        later(9.5f, () -> {
            state.rules.winWave = 100;
            state.wave = 10;
        });
        later(10f, () -> shot("04-modifier"));
        later(11f, () -> rui.choose(0));
        later(12f, () -> logic.runWave());
        later(14f, () -> {
            Seq<Unit> enemies = state.rules.waveTeam.data().units.copy();
            Log.info("[Rogue-Test] enemies=@ speed=@ longRange=@", enemies.size, enemies.count(u -> u.hasEffect(Mutators.speedEffect)), enemies.count(u -> u.controller() instanceof Mutators.LongRangeAI));
            Unit first = enemies.first();
            float x = first.x;
            Mutators.onEnemyHit(first);
            Log.info("[Rogue-Test] barrier shield=@ teleported=@", (int)first.shield, first.x != x);
            shot("05-hud");
            Unit second = enemies.get(1);
            second.set(first.x + 8f, first.y);
        });
        later(14.3f, () -> state.rules.waveTeam.data().units.first().kill());
        later(14.8f, () -> {
            Unit grown = Groups.unit.find(u -> u.team == state.rules.waveTeam && u.hasEffect(Mutators.avengerEffects[0]));
            Log.info("[Rogue-Test] after one kill: avenger=@", grown != null);
            if(grown == null) return;
            grown.unapply(Mutators.avengerEffects[0]);
            grown.apply(Mutators.avengerEffects[Mutators.maxAvenger - 1]);
            Building core = state.rules.defaultTeam.core();
            grown.set(core.x - 24f, core.y - 44f);
            grown.rotation = 90f;
            Unit normal = mindustry.content.UnitTypes.dagger.spawn(state.rules.waveTeam, core.x + 24f, core.y - 44f);
            normal.rotation = 90f;
        });
        later(15.0f, () -> {
            Groups.unit.each(u -> u.team == state.rules.waveTeam && u.within(state.rules.defaultTeam.core(), 80f), u -> u.set(u.x, state.rules.defaultTeam.core().y - 44f));
            state.set(mindustry.core.GameState.State.paused);
        });
        later(15.8f, () -> {
            shot("05b-avenger");
            Log.info("[Rogue-Test] grown hitSize=@ normal hitSize=@", (int)Groups.unit.find(u -> u.hasEffect(Mutators.avengerEffects[Mutators.maxAvenger - 1])).hitSize, (int)mindustry.content.UnitTypes.dagger.hitSize);
        });
        later(16.5f, () -> {
            state.set(mindustry.core.GameState.State.playing);
            Groups.unit.each(u -> u.team == state.rules.waveTeam, Unit::kill);
        });
        later(18f, () -> {
            Log.info("[Rogue-Test] after 3s: enemies=@ (revived)", Groups.unit.count(u -> u.team == state.rules.waveTeam));
            Mutators.deployTimer = 1e9f;
        });
        later(19f, () -> Log.info("[Rogue-Test] after deployment: enemies=@", Groups.unit.count(u -> u.team == state.rules.waveTeam)));
        later(19.1f, () -> {
            int before = run.mutators.size;
            run.pendingRelic = true;
            run.take(Rewards.absolution);
            Log.info("[Rogue-Test] absolution: curses @ -> @", before, run.mutators.size);
        });
        later(19.2f, rui::showRunInfo);
        later(19.6f, () -> shot("05c-run"));
        later(19.9f, () -> rui.infoDialog.hide());
        later(20f, () -> {
            state.gameOver = true;
            Events.fire(new GameOverEvent(state.rules.defaultTeam));
        });
        later(25f, () -> shot("06-missions"));
        later(26f, () -> {
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
