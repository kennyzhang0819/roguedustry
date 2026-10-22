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
            RogueRun.current.mutators.set(Mutators.all);
        });

        Events.run(Trigger.newGame, () -> later(3f, this::onMissionLoaded));
    }

    void onMissionLoaded(){
        RogueRun run = RogueRun.current;
        RogueUI rui = RoguedustryMod.ui;
        Log.info("[Rogue-Test] Mission loaded. active=@ slot=@ relicPending=@", RogueRun.active(), run.slot, run.pendingRelic);
        shot("01-relic");

        later(1f, () -> {
            rui.choose(0);
            Core.settings.remove(TechTreeDialog.infoKey);
        });
        later(2f, rui::showResearch);
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
                if(view.hoverNode == null && e instanceof ImageButton b && b.visible && b.x > 100f && b.y > 100f && b.x < Core.graphics.getWidth() - 300f && !RogueRun.current.unlocked.contains(((mindustry.content.TechTree.TechNode)b.userObject).content)){
                    view.hoverNode = b;
                    view.rebuild();
                }
            }
        });
        later(8f, () -> shot("03-tech"));
        later(8.5f, () -> rui.researchDialog.hide());
        later(9.5f, () -> state.wave = 3);
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
            Building core = state.rules.defaultTeam.core();
            Tile spot = world.tile(core.tile.x + 6, core.tile.y);
            spot.setNet(mindustry.content.Blocks.copperWall, state.rules.defaultTeam, 0);
            spot.build.kill();
            Unit second = enemies.get(1);
            second.set(first.x + 8f, first.y);
        });
        later(14.3f, () -> state.rules.waveTeam.data().units.first().kill());
        later(14.8f, () -> {
            Log.info("[Rogue-Test] after one kill: enemies=@ avenger=@", Groups.unit.count(u -> u.team == state.rules.waveTeam), Groups.unit.count(u -> u.team == state.rules.waveTeam && u.hasEffect(Mutators.avengerEffects[0])));
            shot("05b-avenger");
            Groups.unit.each(u -> u.team == state.rules.waveTeam, Unit::kill);
        });
        later(18f, () -> {
            Building core = state.rules.defaultTeam.core();
            Tile spot = world.tile(core.tile.x + 6, core.tile.y);
            Log.info("[Rogue-Test] after 3s: enemies=@ (revived) captured wall team=@", Groups.unit.count(u -> u.team == state.rules.waveTeam), spot.team());
            Mutators.deployTimer = 1e9f;
        });
        later(19f, () -> Log.info("[Rogue-Test] after deployment: enemies=@", Groups.unit.count(u -> u.team == state.rules.waveTeam)));
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
