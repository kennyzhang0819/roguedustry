package roguedustry;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.ui.*;
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
        c.add("Research is free. Each new sector gives a [#" + Rewards.Rarity.relic.color + "]relic[] and a [#" + curseColor + "]curse[].").row();
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
        if((relic ? run.relicOffer() : run.modifierOffer()).isEmpty()){
            run.skipPicks();
            return;
        }

        rewardDialog = new BaseDialog(relic ? "[#" + Rewards.Rarity.relic.color + "]Relic" : "Modifier");
        Table c = rewardDialog.cont;
        String word = relic ? "[#" + Rewards.Rarity.relic.color + "]relic[]" : "modifier";
        c.add("Pick a " + word + ".").padBottom(8f).row();

        Table row = c.table().get();
        Runnable[] fill = {null};
        fill[0] = () -> {
            row.clear();
            Seq<Rewards.Reward> rewards = relic ? run.relicOffer() : run.modifierOffer();
            offered = rewards.map(r -> () -> run.take(r));
            for(int i = 0; i < rewards.size; i++){
                Rewards.Reward reward = rewards.get(i);
                int index = i;
                row.table(col -> {
                    col.button(card -> {
                        card.top().margin(12f);
                        if(reward.hasIcon()) card.image(reward.icon()).size(64f).padBottom(6f).row();
                        card.add(reward.rarity.label).color(reward.rarity.color).padBottom(4f).row();
                        card.add(reward.name).color(reward.rarity.color).padBottom(8f).row();
                        card.add(reward.description).wrap().width(220f).row();
                    }, Styles.defaultb, () -> choose(index)).size(260f, 300f).row();
                    if(!relic){
                        col.button("Reroll", Icon.refresh, () -> {
                            run.reroll(index);
                            fill[0].run();
                        }).disabled(b -> !run.canReroll(index)).size(260f, 50f).padTop(6f);
                    }
                }).pad(8f).top();
            }
        };
        fill[0].run();
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
        infoDialog = new BaseDialog("Roguedustry Modifiers");
        Table c = infoDialog.cont;
        c.add("Next modifier after wave " + run.nextModifierWave()).color(Color.lightGray).padBottom(8f).row();

        c.pane(list -> {
            list.left().defaults().left().pad(2f);
            rewardIcons(list, run, "Relics", true);
            rewardIcons(list, run, "Modifiers", false);
            list.add("[#" + curseColor + "]Curses").padTop(10f).row();
            list.table(row -> {
                row.left();
                if(run.mutators.isEmpty()) row.add("[lightgray]none");
                int i = 0;
                for(Mutators.Mutator mutator : run.mutators){
                    iconCell(row, mutator.icon(), 0, "[#" + curseColor + "]" + mutator.name + "[]\n[lightgray]" + mutator.description);
                    if(++i % iconsPerRow == 0) row.row();
                }
            }).row();
        }).maxHeight(Core.graphics.getHeight() / Scl.scl() * 0.65f).width(720f);

        infoDialog.addCloseButton();
        pauseWhileShown(infoDialog);
        infoDialog.show();
    }

    static final int iconsPerRow = 10;

    static void rewardIcons(Table list, RogueRun run, String title, boolean relics){
        list.add((relics ? "[#" + Rewards.Rarity.relic.color + "]" : "[accent]") + title).padTop(relics ? 0f : 10f).row();
        list.table(row -> {
            row.left();
            int i = 0;
            for(Rewards.Reward reward : Rewards.all){
                int n = run.stacks(reward);
                if(n <= 0 || reward.isRelic() != relics) continue;
                String tip = "[#" + reward.rarity.color + "]" + reward.name + (n > 1 ? " x" + n : "") + "[]\n[lightgray]" + reward.description;
                iconCell(row, reward.hasIcon() ? reward.icon() : Icon.box.getRegion(), n, tip);
                if(++i % iconsPerRow == 0) row.row();
            }
            if(i == 0) row.add("[lightgray]none");
        }).row();
    }

    static void iconCell(Table row, TextureRegion icon, int count, String tip){
        Table badge = new Table();
        badge.bottom().right();
        if(count > 1) badge.add("x" + count).style(Styles.outlineLabel);
        row.stack(new Image(icon), badge).size(56f).pad(4f).tooltip(tip);
    }

    void buildHud(){
        Table panel = new Table(Tex.wavepane);
        panel.name = "rogue-hud";
        panel.visible(() -> RogueRun.active() && ui.hudfrag.shown());
        panel.left();
        panel.defaults().height(40f).growX();
        panel.button("Roguedustry Modifiers", Icon.book, Styles.cleart, this::showRunInfo);

        Table waves = ui.hudGroup.find("waves");
        if(waves != null){
            waves.row();
            waves.add(panel).growX().left().padTop(4f);
        }else{
            ui.hudGroup.fill(t -> t.top().left().marginTop(130f).add(panel).width(330f));
        }
    }
}
