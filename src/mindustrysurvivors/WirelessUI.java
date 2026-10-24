package mindustrysurvivors;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.blocks.*;

import java.lang.reflect.*;

import static mindustry.Vars.*;

public class WirelessUI{
    public final Table popup = new Table(Tex.pane);
    public @Nullable Building shown;
    final Seq<Liquid> listed = new Seq<>(), wanted = new Seq<>();
    @Nullable Element marker;
    @Nullable Method rebuildItems;

    public void init(){
        popup.visible = false;
        ui.hudGroup.addChild(popup);
        popup.update(() -> {
            if(shown == null || !shown.isValid() || !SurvivorsRun.wirelessOn()){
                hide();
                return;
            }
            Vec2 pos = Core.input.mouseScreen(shown.x, shown.y - shown.block.size * tilesize / 2f - 1f);
            popup.pack();
            popup.setPosition(pos.x, pos.y, Align.top);
        });

        Events.on(TapEvent.class, e -> {
            if(e.player != player || !SurvivorsRun.wirelessOn() || control.input.block != null || control.input.isBreaking()) return;
            Building b = e.tile.build;
            if(b != null && b.team == player.team() && !b.block.configurable && hasChoices(b)){
                if(shown == b) hide();
                else show(b);
            }else{
                hide();
            }
        });
        Events.on(ResetEvent.class, e -> hide());

        try{
            rebuildItems = CoreItemsDisplay.class.getDeclaredMethod("rebuild");
            rebuildItems.setAccessible(true);
        }catch(Throwable e){
            Log.err("[Survivors] Could not add liquids to the core items list", e);
        }
        Events.run(Trigger.update, this::syncLiquids);
    }

    public static boolean hasChoices(Building b){
        return Wireless.options(b, Wireless.ammo).any() || Wireless.options(b, Wireless.coolant).any()
            || Wireless.options(b, Wireless.fuel).any() || Wireless.hasBoost(b);
    }

    //the game's core items list clears itself whenever a new item shows up, so liquids are added back after each rebuild
    void syncLiquids(){
        if(rebuildItems == null) return;
        CoreItemsDisplay display = ui.hudfrag.coreItems;
        wanted.clear();
        if(SurvivorsRun.wirelessOn()){
            for(Liquid l : content.liquids()) if(Wireless.pool.get(l, 0f) >= 1f) wanted.add(l);
        }
        boolean present = marker != null && marker.parent == display;
        if(wanted.equals(listed) && (present || wanted.isEmpty())) return;

        try{
            rebuildItems.invoke(display);
        }catch(Throwable e){
            Log.err(e);
            rebuildItems = null;
            return;
        }
        listed.set(wanted);
        marker = null;
        if(listed.isEmpty()) return;

        display.background(Styles.black6);
        display.margin(4f);
        display.row();
        int i = 0;
        for(Liquid l : listed){
            Element icon = display.image(l.uiIcon).size(iconSmall).padRight(3f)
                .tooltip(t -> t.background(Styles.black6).margin(4f).add(l.localizedName).style(Styles.outlineLabel)).get();
            if(marker == null) marker = icon;
            display.label(() -> UI.formatAmount((long)Wireless.pool.get(l, 0f))).padRight(3f).minWidth(52f).left()
                .tooltip(t -> t.background(Styles.black6).margin(4f).label(() -> (int)Wireless.pool.get(l, 0f) + " / " + (int)Wireless.capacity()).style(Styles.outlineLabel));
            if(++i % 4 == 0) display.row();
        }
    }

    public void show(Building b){
        shown = b;
        popup.clear();
        popup.margin(8f);
        popup.defaults().left();
        popup.add(b.block.localizedName).color(Pal.accent).padBottom(4f).row();

        picker(b, Wireless.ammo, "Ammo", false);
        picker(b, Wireless.fuel, "Fuel", false);
        picker(b, Wireless.coolant, "Coolant", true);

        if(Wireless.hasBoost(b)){
            popup.check("Use boost", Wireless.boosting(b), v -> Wireless.set(b, Wireless.boost, v ? Wireless.on : Wireless.off)).padTop(4f).row();
        }

        popup.visible = true;
        popup.pack();
    }

    void picker(Building b, String key, String title, boolean canTurnOff){
        Seq<UnlockableContent> opts = Wireless.options(b, key);
        if(opts.isEmpty()) return;
        popup.table(head -> {
            head.left();
            head.add(title).padRight(8f);
            head.label(() -> Wireless.none.equals(Wireless.get(b, key)) ? "[lightgray]off" : Wireless.picked(b, key) == null ? "[lightgray]auto" : "");
        }).padTop(4f).row();
        popup.table(t -> ItemSelection.buildTable(null, t, opts, () -> Wireless.picked(b, key),
            v -> Wireless.set(b, key, v == null ? null : v.name), false, 2, 6)).row();
        if(canTurnOff){
            popup.button("Off", Styles.flatTogglet, () -> Wireless.set(b, key, Wireless.none.equals(Wireless.get(b, key)) ? null : Wireless.none))
                .checked(x -> Wireless.none.equals(Wireless.get(b, key))).size(80f, 32f).row();
        }
    }

    public void hide(){
        shown = null;
        popup.visible = false;
    }
}
