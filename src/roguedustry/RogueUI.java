package roguedustry;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.TechTree.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

public class RogueUI{
    public @Nullable BaseDialog menuDialog, missionDialog, rewardDialog, overDialog, infoDialog;
    public @Nullable TechTreeDialog researchDialog;
    public Seq<Runnable> offered = new Seq<>();

    public void init(){
        ui.menufrag.addButton("Roguedustry", Icon.play, this::showMenu);
        buildHud();

        //the game's own game-over window opens 3 seconds after the end; ours replaces it during a run
        ui.restart.shown(() -> Core.app.post(() -> {
            RogueRun run = RogueRun.current;
            if(run == null || !String.valueOf(run.id).equals(state.rules.tags.get(RogueRun.tag))) return;
            ui.restart.hide(null);
            if(run.over) showRunOver();
            else showMissions();
        }));

        Events.run(Trigger.update, this::update);
    }

    static RogueRun run(){
        return RogueRun.current;
    }

    void update(){
        if(!RogueRun.active() || state.gameOver || Core.scene.hasDialog()) return;
        if(run().pendingCurse) showCurse();
        else if(run().pendingRelic) showReward(true);
        else if(run().pendingModifiers > 0) showReward(false);
    }

    public void showMenu(){
        if(run() == null || run().over) RogueRun.current = RogueRun.load();

        menuDialog = new BaseDialog("Roguedustry");
        Table c = menuDialog.cont;
        c.defaults().pad(4f);

        if(run() == null){
            c.add("Play through the campaign maps in order, starting with a locked tech tree.").row();
            c.add("Pick a relic at the start of each map, and modifiers as the map goes on. They last the whole run.").row();
            c.add("At the start of each map you also pick a mutator for the enemies. They keep it for the whole run.").row();
            c.add("Lose a single map and the run is over: you start again from Ground Zero.").color(Pal.remove).row();
            c.add("Your normal campaign is not affected.").color(Color.lightGray).padBottom(10f).row();
            if(RogueRun.best() > 0) c.add("Best run: " + RogueRun.best() + " maps cleared").color(Pal.accent).row();
            c.button("Start a new run", Icon.play, () -> {
                RogueRun.current = RogueRun.create();
                menuDialog.hide();
                showMissions();
            }).size(320f, 64f);
        }else{
            c.add("Run in progress: " + run().missionsCleared + " maps cleared, " + run().mutators.size + " enemy mutators").color(Pal.accent).row();
            c.add(rewardSummary()).color(Color.lightGray).wrap().width(600f).padBottom(10f).row();
            c.button(run().mission != null ? "Continue " + Mission.of(run().mission).name() : "Continue", Icon.play, () -> {
                menuDialog.hide();
                if(run().mission != null) resume();
                else showMissions();
            }).size(360f, 64f).row();
            c.button("Abandon this run", Icon.trash, () -> ui.showConfirm("Abandon run", "This run will be lost. Are you sure?", () -> {
                run().lose();
                RogueRun.current = null;
                menuDialog.hide();
                showMenu();
            })).size(360f, 50f);
        }

        menuDialog.addCloseButton();
        menuDialog.show();
    }

    void resume(){
        Saves.SaveSlot slot = run().saveSlot();
        if(slot == null){
            String name = Mission.of(run().mission).name();
            run().lose();
            RogueRun.current = null;
            ui.showInfo("The save for " + name + " is gone, so the run is over.");
            return;
        }
        ui.loadAnd(() -> {
            try{
                net.reset();
                slot.load();
                state.rules.editor = false;
                state.rules.sector = null;
                state.set(State.playing);
            }catch(Throwable e){
                Log.err(e);
                logic.reset();
                ui.showException("Could not load the save for this map", e);
            }
        });
    }

    String rewardSummary(){
        if(run().rewards.isEmpty()) return "No relics or modifiers yet.";
        Seq<String> parts = new Seq<>();
        for(var e : run().rewards){
            parts.add(e.key.name + (e.value > 1 ? " x" + e.value : ""));
        }
        return parts.toString(", ");
    }

    static void mutatorIcons(Table table, Seq<Mutators.Mutator> list, float size, int perRow){
        table.clear();
        table.left();
        if(list.isEmpty()){
            table.add("[lightgray]none");
            return;
        }
        int i = 0;
        for(Mutators.Mutator m : list){
            table.image(m.icon()).size(size).pad(2f).tooltip(m.name + ": " + m.description);
            if(++i % perRow == 0) table.row();
        }
    }

    public void showMissions(){
        RogueRun run = run();
        boolean justCleared = state.isGame() && state.gameOver;
        missionDialog = new BaseDialog(justCleared ? "Map cleared!" : "Choose your next map");
        Table c = missionDialog.cont;

        c.add("Maps cleared: " + run.missionsCleared).color(Pal.accent).row();
        c.table(line -> {
            line.add("[scarlet]Enemy mutators: ");
            line.table(icons -> mutatorIcons(icons, run.mutators, 32f, 10));
        }).padBottom(8f).row();

        c.pane(p -> {
            p.defaults().pad(6f);
            int i = 0;
            for(Mission m : run.missionChoices()){
                p.table(Styles.black6, card -> {
                    card.margin(10f).left().defaults().left();
                    card.add(m.name()).color(Pal.accent).row();
                    card.add(m.goal()).row();
                    card.add("Threat " + Strings.autoFixed(m.threat(), 1)).color(Color.lightGray).row();
                    card.button("Play", Icon.play, () -> start(m)).size(180f, 50f).padTop(6f);
                }).width(300f).top();
                if(++i % 3 == 0) p.row();
            }
        }).maxHeight(380f).row();

        Seq<TechNode> locked = Tech.lockedSectors(run);
        if(locked.any()){
            c.add("Still locked").color(Color.lightGray).padTop(12f).row();
            c.pane(p -> {
                p.left().defaults().left().pad(2f);
                for(TechNode node : locked){
                    p.add("[lightgray]" + node.content.localizedName + ": []needs " + Tech.missingText(run, node)).wrap().width(760f).row();
                }
            }).maxHeight(180f).width(780f).row();
        }

        missionDialog.buttons.defaults().size(220f, 60f);
        missionDialog.buttons.button("Tech tree", Icon.tree, this::showResearch);
        missionDialog.buttons.button("Main menu", Icon.exit, () -> {
            missionDialog.hide();
            if(state.isGame()) logic.reset();
        });
        missionDialog.show();
    }

    void start(Mission m){
        if(missionDialog != null) missionDialog.hide();
        run().startMission(m);
    }

    public void showResearch(){
        //the tree layout keeps state in its nodes, so a fresh window is built each time
        researchDialog = new TechTreeDialog();
        researchDialog.show();
    }

    static void pauseWhileShown(BaseDialog dialog){
        boolean pause = state.isGame() && !state.gameOver;
        dialog.shown(() -> {
            if(pause) state.set(State.paused);
        });
        dialog.hidden(() -> {
            if(pause && state.isGame() && !state.gameOver) state.set(State.playing);
        });
    }

    public void showCurse(){
        RogueRun run = run();
        Seq<Mutators.Mutator> curses = run.curseOffer();
        if(curses.isEmpty()){
            run.curse(null);
            return;
        }
        offered = curses.map(m -> () -> run.curse(m));

        rewardDialog = new BaseDialog("Curse");
        Table c = rewardDialog.cont;
        c.add("Pick a curse. It stays with you for the rest of the run.").padBottom(8f).row();

        c.table(row -> {
            for(int i = 0; i < curses.size; i++){
                Mutators.Mutator mutator = curses.get(i);
                int index = i;
                row.button(card -> {
                    card.top().margin(12f);
                    card.image(mutator.icon()).size(64f).padBottom(6f).row();
                    card.add(mutator.name).color(Pal.remove).wrap().width(220f).padBottom(8f).get().setAlignment(Align.center);
                    card.row();
                    card.add(mutator.description).wrap().width(220f).row();
                }, Styles.defaultb, () -> choose(index)).size(260f, 260f).pad(8f);
            }
        });
        pauseWhileShown(rewardDialog);
        rewardDialog.show();
    }

    public void showReward(boolean relic){
        RogueRun run = run();
        Seq<Rewards.Reward> rewards = relic ? run.relicOffer() : run.modifierOffer();
        if(rewards.isEmpty()){
            run.skipPicks();
            return;
        }
        offered = rewards.map(r -> () -> run.take(r));

        rewardDialog = new BaseDialog(relic ? "Choose a relic" : "Choose a modifier");
        Table c = rewardDialog.cont;
        c.add(relic ? "Pick a relic. It stays with you for the rest of the run." : "Pick a modifier. It stays with you for the rest of the run.").padBottom(8f).row();

        c.table(row -> {
            for(int i = 0; i < rewards.size; i++){
                Rewards.Reward reward = rewards.get(i);
                int index = i;
                row.button(card -> {
                    card.top().margin(12f);
                    card.add(reward.rarity.label).color(reward.rarity.color).padBottom(4f).row();
                    card.add(reward.name).color(Pal.accent).padBottom(8f).row();
                    card.add(reward.description).wrap().width(220f).row();
                    if(run.stacks(reward) > 0) card.add("You have " + run.stacks(reward) + " of " + reward.max).color(Color.lightGray).padTop(8f).row();
                }, Styles.defaultb, () -> choose(index)).size(260f, 230f).pad(8f);
            }
        });
        pauseWhileShown(rewardDialog);
        rewardDialog.show();
    }

    public void choose(int index){
        if(rewardDialog == null || index >= offered.size) return;
        Runnable pick = offered.get(index);
        //a second click during the close animation must not pick again
        offered = new Seq<>();
        pick.run();
        rewardDialog.hide();
    }

    void showRunOver(){
        overDialog = new BaseDialog("Run over");
        overDialog.cont.add("Your core was destroyed. The run is over.").row();
        overDialog.cont.add("Maps cleared: " + run().missionsCleared).color(Pal.accent).row();
        overDialog.cont.add("Best run: " + RogueRun.best()).color(Color.lightGray).row();
        overDialog.buttons.button("Main menu", Icon.exit, () -> {
            overDialog.hide();
            RogueRun.current = null;
            logic.reset();
        }).size(220f, 60f);
        overDialog.show();
    }

    public void showRunInfo(){
        RogueRun run = run();
        if(!RogueRun.active() || run.mission == null) return;
        infoDialog = new BaseDialog("Run");
        Table c = infoDialog.cont;
        Mission m = Mission.of(run.mission);
        c.add(m.name() + ": " + m.goal()).color(Pal.accent).row();
        c.add("Maps cleared: " + run.missionsCleared + "   Next modifier after wave " + (run.mapPicks + 1) * Rewards.wavesPerModifier).color(Color.lightGray).padBottom(8f).row();

        c.pane(list -> {
            list.left().defaults().left().pad(2f);
            list.add("[scarlet]Enemy mutators").padTop(6f).row();
            if(run.mutators.isEmpty()) list.add("[lightgray]none").row();
            for(Mutators.Mutator mutator : run.mutators){
                list.table(line -> {
                    line.left();
                    line.image(mutator.icon()).size(40f).padRight(8f);
                    line.add("[scarlet]" + mutator.name + "[]: [lightgray]" + mutator.description).wrap().width(640f).left();
                }).row();
            }
            rewardList(list, run, "Relics", true);
            rewardList(list, run, "Modifiers", false);
        }).maxHeight(Core.graphics.getHeight() / Scl.scl() * 0.65f).width(720f);

        infoDialog.addCloseButton();
        pauseWhileShown(infoDialog);
        infoDialog.show();
    }

    static void rewardList(Table list, RogueRun run, String title, boolean relics){
        list.add("[accent]" + title).padTop(10f).row();
        boolean any = false;
        for(var e : run.rewards){
            if(e.key.isRelic() != relics) continue;
            any = true;
            list.add("[#" + e.key.rarity.color + "]" + e.key.name + (e.value > 1 ? " x" + e.value : "") + "[]: [lightgray]" + e.key.description).wrap().width(700f).row();
        }
        if(!any) list.add("[lightgray]none").row();
    }

    void buildHud(){
        Table panel = new Table();
        panel.name = "rogue-hud";
        panel.visible(() -> RogueRun.active() && ui.hudfrag.shown());
        panel.left();
        panel.defaults().height(40f).growX();
        panel.button("Run", Icon.book, Styles.cleart, this::showRunInfo);
        panel.button("Tech tree", Icon.tree, Styles.cleart, this::showResearch);

        Table waves = ui.hudGroup.find("waves");
        if(waves != null){
            waves.row();
            waves.add(panel).growX().left().padTop(4f);
        }else{
            ui.hudGroup.fill(t -> t.top().left().marginTop(130f).add(panel).width(330f));
        }
    }
}
