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
    boolean continued;

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

        later(60f * 4f, () -> {
            Log.info("[Rogue-Test] RESULT timeout");
            Core.app.exit();
        });

        String mode = System.getenv("ROGUE_AUTOTEST");
        if("crash".equals(mode)){
            later(2f, () -> {
                Core.settings.put("serpulo-test-marker", "player");
                RogueRun.startNew();
                Log.info("[Rogue-Test] Quitting during a run: live=@", Profile.live());
                Core.app.exit();
            });
            return;
        }
        if("restore".equals(mode)){
            later(2f, () -> {
                Log.info("[Rogue-Test] RESULT after restart: live=@ playerMarker=@", Profile.live(), Core.settings.getString("serpulo-test-marker", "missing"));
                Core.app.exit();
            });
            return;
        }

        later(2f, () -> {
            Core.settings.put("serpulo-test-marker", "player");
            saveDirectory.child("sector-serpulo-999.msav").writeString("player save");
            RogueRun.startNew();
            Log.info("[Rogue-Test] In run: live=@ playerMarkerHidden=@ playerSaveHidden=@ copperUnlocked=@ duoCost=@ numberedLocked=@",
                Profile.live(), !Core.settings.has("serpulo-test-marker"), !saveDirectory.child("sector-serpulo-999.msav").exists(), Items.copper.unlocked(), Blocks.duo.techNode.requirements.length, !Planets.serpulo.allowLaunchToNumbered);
            control.playSector(SectorPresets.groundZero.sector);
        });

        Events.run(Trigger.newGame, () -> {
            if(!continued) later(12f, this::onSectorLoaded);
        });
    }

    void onSectorLoaded(){
        RogueRun run = RogueRun.current;
        RogueUI rui = RoguedustryMod.ui;
        Log.info("[Rogue-Test] Sector loaded. active=@ launches=@ relicPending=@ unitCap=@ processorBanned=@ duoUnlocked=@",
            RogueRun.active(), run.launches, run.pendingRelic, mindustry.entities.Units.getCap(state.rules.defaultTeam),
            state.rules.bannedBlocks.contains(Blocks.microProcessor), Blocks.duo.unlocked());
        shot("01-relic");

        later(0.7f, () -> rui.choose(0));
        later(0.9f, () -> run.pendingCurse = true);
        later(1.4f, () -> shot("02-curse"));
        later(1.8f, () -> rui.choose(0));
        later(2.2f, () -> {
            state.rules.winWave = 100;
            state.wave = 10;
        });
        later(2.8f, () -> shot("03-modifier"));
        later(3.4f, () -> rui.choose(0));
        later(3.8f, () -> {
            run.mutators.set(Mutators.all);
            logic.runWave();
        });
        later(5.5f, () -> {
            Seq<Unit> enemies = state.rules.waveTeam.data().units.copy();
            Log.info("[Rogue-Test] enemies=@ speed=@ longRange=@", enemies.size, enemies.count(u -> u.hasEffect(Mutators.speedEffect)), enemies.count(u -> u.controller() instanceof Mutators.LongRangeAI));
            shot("04-hud");
        });
        later(6f, rui::showRunInfo);
        later(6.5f, () -> shot("05-run"));
        later(7f, () -> {
            rui.infoDialog.hide();
            control.saves.getCurrent().save();
            logic.reset();
        });
        later(9f, () -> {
            Log.info("[Rogue-Test] Back in menu: live=@ playerMarker=@ runSectorKept=@",
                Profile.live(), Core.settings.getString("serpulo-test-marker", "missing"), Profile.sectorFiles(Profile.folder(Profile.run)).map(f -> f.name()));
            continued = true;
            RogueRun.enter();
            Log.info("[Rogue-Test] Continued: live=@ groundZeroSave=@ rewards=@", Profile.live(), SectorPresets.groundZero.sector.save != null, RogueRun.current.rewards.keys().toSeq().map(r -> r.name));
            RogueRun.leave();
            Log.info("[Rogue-Test] RESULT live=@ playerMarker=@ playerSave=@", Profile.live(), Core.settings.getString("serpulo-test-marker", "missing"), saveDirectory.child("sector-serpulo-999.msav").exists() ? saveDirectory.child("sector-serpulo-999.msav").readString() : "missing");
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
