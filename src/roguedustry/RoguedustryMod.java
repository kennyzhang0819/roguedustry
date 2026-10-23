package roguedustry;

import arc.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;

public class RoguedustryMod extends Mod{
    public static final boolean roguelike = true;

    public static RogueUI ui;
    public static WirelessUI wirelessUI;

    public RoguedustryMod(){
        RogueRun.registerEvents();
        Curses.registerEvents();
        Wireless.register();
        TurretMods.registerEvents();
        BlockMods.registerEvents();

        Events.on(ClientLoadEvent.class, e -> {
            wirelessUI = new WirelessUI();
            wirelessUI.init();

            if(roguelike){
                ui = new RogueUI();
                ui.init();
            }

            if(AutoTest.enabled()){
                new AutoTest().begin();
            }
        });
    }

    @Override
    public void loadContent(){
        Curses.load();
        Rewards.load();
        TurretMods.load();
        BlockMods.load();
    }
}
