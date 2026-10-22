package roguedustry;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
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
    public @Nullable BaseDialog menuDialog, rewardDialog, infoDialog;
    static final Color curseColor = Color.valueOf("f25555");
    public Seq<Runnable> offered = new Seq<>();
    int framesInMenu;

    public void init(){
        ui.menufrag.addButton("Roguedustry", Icon.play, this::showMenu);
        buildHud();
        Events.run(Trigger.update, this::update);
    }

    static RogueRun run(){
        return RogueRun.current;
    }

    void update(){
        //a sector launch passes through the menu for a couple of frames, so only a longer stay there leaves the run
        if(Profile.inRun() && state.isMenu() && !ui.planet.isShown()){
            if(++framesInMenu > 60){
                framesInMenu = 0;
                RogueRun.leave();
            }
        }else{
            framesInMenu = 0;
        }

        if(!RogueRun.active()) return;
        //the game shows the sector name for 5 seconds after the landing animation, over any dialog
        if(renderer.isCutscene()) run().loadedAt = Time.millis();
        if(state.gameOver || Core.scene.hasDialog() || Time.timeSinceMillis(run().loadedAt) < 5500) return;
        if(run().pendingRelic) showReward(true);
        else if(run().pendingCurse) showCurse();
        else if(run().pendingModifiers > 0) showReward(false);
    }

    public void showMenu(){
        menuDialog = new BaseDialog("Roguedustry");
        Table c = menuDialog.cont;
        c.defaults().pad(4f);

        c.add("The Serpulo campaign, with roguelike rewards and [#" + curseColor + "]curses[].").row();
        c.add("Research is free. Each new sector gives a [#" + Rewards.Rarity.relic.color + "]relic[], every second one a [#" + curseColor + "]curse[].").row();
        c.add("A modifier every 10 waves. All of them last the whole run.").row();
        c.add("This is a separate campaign. Your own campaign is not touched.").color(Color.lightGray).padBottom(10f).row();

        if(RogueRun.exists()){
            c.button("Continue run", Icon.play, () -> {
                menuDialog.hide();
                RogueRun.enter();
                ui.planet.show();
            }).size(360f, 64f).row();
        }
        c.button("Start a new run", Icon.add, () -> {
            Runnable start = () -> {
                menuDialog.hide();
                RogueRun.startNew();
                ui.planet.show();
            };
            if(RogueRun.exists()) ui.showConfirm("New run", "Your current run will be lost. Are you sure?", start);
            else start.run();
        }).size(360f, 64f).row();
        if(RogueRun.exists()){
            c.button("Abandon run", Icon.trash, () -> ui.showConfirm("Abandon run", "This run will be lost. Are you sure?", () -> {
                RogueRun.abandon();
                menuDialog.hide();
                showMenu();
            })).size(360f, 50f);
        }

        menuDialog.addCloseButton();
        menuDialog.show();
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

        rewardDialog = new BaseDialog("[#" + curseColor + "]Curse");
        Table c = rewardDialog.cont;
        c.add("Pick a [#" + curseColor + "]curse[].").padBottom(8f).row();

        c.table(row -> {
            for(int i = 0; i < curses.size; i++){
                Mutators.Mutator mutator = curses.get(i);
                int index = i;
                row.button(card -> {
                    card.top().margin(12f);
                    card.image(mutator.icon()).size(64f).padBottom(6f).row();
                    card.add(mutator.name).color(curseColor).wrap().width(220f).padBottom(8f).get().setAlignment(Align.center);
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

        rewardDialog = new BaseDialog(relic ? "[#" + Rewards.Rarity.relic.color + "]Relic" : "Modifier");
        Table c = rewardDialog.cont;
        String word = relic ? "[#" + Rewards.Rarity.relic.color + "]relic[]" : "modifier";
        c.add("Pick a " + word + ".").padBottom(8f).row();

        c.table(row -> {
            for(int i = 0; i < rewards.size; i++){
                Rewards.Reward reward = rewards.get(i);
                int index = i;
                row.button(card -> {
                    card.top().margin(12f);
                    card.add(reward.rarity.label).color(reward.rarity.color).padBottom(4f).row();
                    card.add(reward.name).color(reward.rarity.color).padBottom(8f).row();
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

    public void showRunInfo(){
        RogueRun run = run();
        if(!RogueRun.active()) return;
        infoDialog = new BaseDialog("Run Modifiers");
        Table c = infoDialog.cont;
        c.add("Next modifier after wave " + run.nextModifierWave()).color(Color.lightGray).padBottom(8f).row();

        c.pane(list -> {
            list.left().defaults().left().pad(2f);
            list.add("[#" + curseColor + "]Curses").padTop(6f).row();
            if(run.mutators.isEmpty()) list.add("[lightgray]none").row();
            for(Mutators.Mutator mutator : run.mutators){
                list.table(line -> {
                    line.left();
                    line.image(mutator.icon()).size(40f).padRight(8f);
                    line.add("[#" + curseColor + "]" + mutator.name + "[]: [lightgray]" + mutator.description).wrap().width(640f).left();
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
        Table panel = new Table(Tex.wavepane);
        panel.name = "rogue-hud";
        panel.visible(() -> RogueRun.active() && ui.hudfrag.shown());
        panel.left();
        panel.defaults().height(40f).growX();
        panel.button("Run Modifiers", Icon.book, Styles.cleart, this::showRunInfo);

        Table waves = ui.hudGroup.find("waves");
        if(waves != null){
            waves.row();
            waves.add(panel).growX().left().padTop(4f);
        }else{
            ui.hudGroup.fill(t -> t.top().left().marginTop(130f).add(panel).width(330f));
        }
    }
}
