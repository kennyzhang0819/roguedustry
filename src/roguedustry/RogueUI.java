package roguedustry;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.func.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.ui.fragments.MenuFragment.*;

import static mindustry.Vars.*;

public class RogueUI{
    public @Nullable BaseDialog menuDialog, rewardDialog, infoDialog;
    static final Color curseColor = Color.valueOf("f25555");
    static final String adminKey = "roguedustry-admin";
    public Seq<Runnable> offered = new Seq<>();
    int framesInMenu;

    public void init(){
        MenuButton button = new MenuButton("Roguedustry", Icon.play, this::showMenu);
        if(!mobile && ui.menufrag.desktopButtons != null){
            ui.menufrag.desktopButtons.insert(1, button);
            //the main menu only rebuilds its buttons on a resize
            Events.fire(new ResizeEvent());
        }else{
            ui.menufrag.addButton(button);
        }
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
        if(run().pendingGift) showGift();
        else if(run().pendingRelic) showReward(true);
        else if(run().pendingCurse) showCurse();
        else if(run().pendingModifiers > 0) showReward(false);
    }

    public void showMenu(){
        menuDialog = new BaseDialog("Roguedustry");
        Table c = menuDialog.cont;
        c.defaults().pad(4f);

        c.add("The Serpulo campaign, with roguelike rewards and [#" + curseColor + "]curses[].").row();
        c.add("Kill enemies to obtain powerful modifiers and relics.").row();
        c.add("This is a separate campaign. Your own campaign is not touched.").color(Color.lightGray).padBottom(10f).row();

        if(RogueRun.exists()){
            RogueRun saved = RogueRun.load();
            if(saved != null) c.add("Current run: " + (saved.wireless ? "Roguelike Campaign" : "Vanilla Campaign")).color(Color.lightGray).row();
            c.button("Continue run", Icon.play, () -> {
                menuDialog.hide();
                RogueRun.enter();
                ui.planet.show();
            }).size(360f, 64f).row();
        }
        c.button("Start a new run", Icon.add, () -> {
            if(RogueRun.exists()) ui.showConfirm("New run", "Your current run will be lost. Are you sure?", this::showModes);
            else showModes();
        }).size(360f, 64f).row();
        if(RogueRun.exists()){
            c.button("Abandon run", Icon.trash, () -> ui.showConfirm("Abandon run", "This run will be lost. Are you sure?", () -> {
                RogueRun.abandon();
                menuDialog.hide();
                showMenu();
            })).size(360f, 50f);
        }

        c.check("Admin tools", Core.settings.getBool(adminKey, false), v -> Core.settings.put(adminKey, v)).padTop(10f).row();

        menuDialog.addCloseButton();
        menuDialog.show();
    }

    public void showModes(){
        BaseDialog dialog = new BaseDialog("New run");
        Table c = dialog.cont;
        c.defaults().pad(8f);
        modeCard(c, dialog, true, "Roguelike Campaign",
            "The intended roguelike experience.\n\n"
            + "- Everything is wireless. All buildings deliver to and take from the core on their own. Turrets shoot using ammo from the core. Conveyor belts and other logistics are banned.\n\n"
            + "- After the first wave, the wait between waves is 90% shorter, and many more enemies spawn.\n\n"
            + "- Research is free and unlocks on its own.\n\n"
            + "- Your core unit builds twice as fast.");
        modeCard(c, dialog, false, "Vanilla Campaign",
            "The original campaign with the new roguelike modifiers. A much slower pace than the Roguelike Campaign, but it keeps the original game's flavor.");
        dialog.addCloseButton();
        dialog.show();
    }

    void modeCard(Table c, BaseDialog dialog, boolean wireless, String title, String text){
        c.button(card -> {
            card.top().left().margin(16f);
            card.add(title).color(Pal.accent).left().padBottom(8f).row();
            card.add(text).wrap().width(380f).left();
        }, Styles.defaultb, () -> {
            dialog.hide();
            if(menuDialog != null) menuDialog.hide();
            RogueRun.startNew(wireless);
            ui.planet.show();
        }).size(420f, 380f).top();
    }

    static void curseIcons(Table table, Seq<Curses.Curse> list, float size, int perRow){
        table.clear();
        table.left();
        if(list.isEmpty()){
            table.add("[lightgray]none");
            return;
        }
        int i = 0;
        for(Curses.Curse m : list){
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
        Seq<Curses.Curse> curses = run.curseOffer();
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
                Curses.Curse curse = curses.get(i);
                int index = i;
                row.button(card -> {
                    card.top().margin(12f);
                    card.image(curse.icon()).size(64f).padBottom(6f).row();
                    card.add(curse.name).color(curseColor).wrap().width(220f).padBottom(8f).get().setAlignment(Align.center);
                    card.row();
                    card.add(curse.description).wrap().width(220f).row();
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
        String word = relic ? "[#" + Rewards.Rarity.relic.color + "]relic[]. Each one comes with a [#" + curseColor + "]curse[]" : "modifier";
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
                        Curses.Curse curse = relic ? run.curseFor(reward) : null;
                        if(curse != null){
                            card.image().color(curseColor).height(2f).growX().pad(10f, 0f, 10f, 0f).row();
                            card.table(head -> {
                                head.image(curse.icon()).size(32f).padRight(6f);
                                head.add(curse.name).color(curseColor);
                            }).padBottom(4f).row();
                            card.add(curse.description).wrap().width(220f).color(Color.lightGray).row();
                        }
                    }, Styles.defaultb, () -> choose(index)).size(260f, relic ? 420f : 300f).row();
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

    public void showGift(){
        RogueRun run = run();
        Seq<Item> choices = RogueRun.giftChoices();
        int needed = Math.min(Rewards.giftChoices, choices.size);
        Seq<Item> picked = new Seq<>();
        rewardDialog = new BaseDialog("[#" + Rewards.Rarity.relic.color + "]Grace Period");
        Table c = rewardDialog.cont;
        c.add("Pick " + needed + " resources. You get " + Rewards.giftAmount + " of each.").padBottom(8f).row();
        c.table(grid -> {
            int i = 0;
            for(Item item : choices){
                grid.button(b -> {
                    b.image(item.uiIcon).size(40f).row();
                    b.add(item.localizedName).padTop(4f);
                }, Styles.clearTogglei, () -> {
                    if(picked.contains(item)) picked.remove(item);
                    else if(picked.size < needed) picked.add(item);
                }).checked(b -> picked.contains(item)).size(120f, 90f).pad(4f);
                if(++i % 6 == 0) grid.row();
            }
        }).row();
        c.button("Take", Icon.ok, () -> {
            run.gift(picked);
            rewardDialog.hide();
        }).disabled(b -> picked.size < needed).size(240f, 60f).padTop(10f);
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
        c.add(RogueRun.captured() ? "Sector cleared: no relics, curses or modifiers here anymore." : "Level " + run.level + ", " + (int)run.xp + "/" + (int)RogueRun.xpFor(run.level) + " XP to the next modifier").color(Color.lightGray).padBottom(8f).row();

        c.pane(list -> {
            list.left().defaults().left().pad(2f);
            rewardIcons(list, run, "Relics", true);
            rewardIcons(list, run, "Modifiers", false);
            list.add("[#" + curseColor + "]Curses").padTop(10f).row();
            list.table(row -> {
                row.left();
                if(run.curses.isEmpty()) row.add("[lightgray]none");
                int i = 0;
                for(Curses.Curse curse : run.curses){
                    iconCell(row, curse.icon(), 0, "[#" + curseColor + "]" + curse.name + "[]\n[lightgray]" + curse.description);
                    if(++i % iconsPerRow == 0) row.row();
                }
            }).row();
        }).maxHeight(Core.graphics.getHeight() / Scl.scl() * 0.65f).width(720f);

        infoDialog.addCloseButton();
        if(Core.settings.getBool(adminKey, false)){
            infoDialog.buttons.button("Admin", Icon.wrench, () -> {
                infoDialog.hide();
                showAdmin();
            }).size(210f, 64f);
        }
        pauseWhileShown(infoDialog);
        infoDialog.show();
    }

    public void showAdmin(){
        RogueRun run = run();
        if(!RogueRun.active()) return;
        BaseDialog dialog = new BaseDialog("Admin");
        Table c = dialog.cont;

        c.table(row -> {
            row.defaults().size(220f, 54f).pad(4f);
            row.button("Capture this sector", Icon.ok, () -> {
                RogueRun.adminCapture();
                dialog.hide();
            }).disabled(b -> RogueRun.captured());
            row.button("Go to a sector", Icon.planet, () -> showSectors(dialog));
            row.button("Unlock all research", Icon.tree, RogueRun::adminUnlockResearch);
            row.row();
            row.button("+1 modifier pick", Icon.add, () -> {
                run.pendingModifiers++;
                dialog.hide();
            }).disabled(b -> RogueRun.captured());
            row.button("+1 relic pick", Icon.add, () -> {
                run.pendingRelic = true;
                dialog.hide();
            }).disabled(b -> RogueRun.captured() || run.pendingRelic);
            row.button("+1 level", Icon.up, () -> {
                run.level++;
                run.xp = 0f;
                run.pendingModifiers++;
                if(run.level % RogueRun.levelsPerRelic == 0 && run.relicsTaken < RogueRun.maxRelics) run.pendingRelic = true;
                dialog.hide();
            }).disabled(b -> RogueRun.captured());
        }).padBottom(8f).row();
        c.add("Tap to add or remove.").color(Color.lightGray).padBottom(4f).row();

        c.pane(list -> {
            list.left().defaults().left().pad(2f);
            adminRewards(list, run, "Relics", "[#" + Rewards.Rarity.relic.color + "]", true);
            adminRewards(list, run, "Modifiers", "[accent]", false);
            list.add("[#" + curseColor + "]Curses").padTop(10f).row();
            list.table(row -> {
                row.left();
                int i = 0;
                for(Curses.Curse curse : Curses.all){
                    adminCell(row, curse.icon(), curse.name, curseColor, () -> run.curses.contains(curse), () -> run.adminToggle(curse));
                    if(++i % adminPerRow == 0) row.row();
                }
            }).row();
        }).maxHeight(Core.graphics.getHeight() / Scl.scl() * 0.6f).width(1100f);

        dialog.addCloseButton();
        pauseWhileShown(dialog);
        dialog.show();
    }

    static final int adminPerRow = 7;

    static void adminRewards(Table list, RogueRun run, String title, String color, boolean relics){
        list.add(color + title).padTop(relics ? 0f : 10f).row();
        list.table(row -> {
            row.left();
            int i = 0;
            for(Rewards.Reward reward : Rewards.all){
                if(reward.isRelic() != relics) continue;
                adminCell(row, reward.hasIcon() ? reward.icon() : Icon.box.getRegion(), reward.name, reward.rarity.color, () -> run.stacks(reward) > 0, () -> run.adminToggle(reward));
                if(++i % adminPerRow == 0) row.row();
            }
        }).row();
    }

    static void adminCell(Table row, TextureRegion icon, String name, Color color, Boolp owned, Runnable toggle){
        row.button(b -> {
            b.image(icon).size(40f).row();
            b.add(name).color(color).wrap().width(136f).padTop(4f).get().setAlignment(Align.center);
        }, Styles.clearTogglei, toggle).checked(b -> owned.get()).size(150f, 100f).pad(2f);
    }

    void showSectors(BaseDialog admin){
        BaseDialog dialog = new BaseDialog("Go to a sector");
        dialog.cont.pane(list -> {
            list.defaults().size(360f, 50f).pad(3f);
            int i = 0;
            for(SectorPreset preset : RogueRun.campaignOrder()){
                Sector sector = preset.sector;
                list.button((sector.isCaptured() ? "[lightgray]" : "") + (++i) + ". " + preset.localizedName, sector.isCaptured() ? Icon.ok : Icon.play, Styles.cleart, () -> {
                    if(sector == state.rules.sector) return;
                    dialog.hide();
                    admin.hide();
                    RogueRun.adminLand(preset);
                }).disabled(b -> sector == state.rules.sector).row();
            }
        }).maxHeight(Core.graphics.getHeight() / Scl.scl() * 0.7f);
        dialog.addCloseButton();
        dialog.show();
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
        panel.add(new Bar(() -> run() == null ? "" : RogueRun.captured() ? "Sector cleared" : "Level " + run().level, () -> Pal.accent, () -> run() == null ? 0f : RogueRun.captured() ? 1f : run().xpProgress())).height(24f).pad(4f).row();
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
