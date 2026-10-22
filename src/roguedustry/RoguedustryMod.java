package roguedustry;

import arc.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;

public class RoguedustryMod extends Mod{
    public static RogueUI ui;

    public RoguedustryMod(){
        RogueRun.registerEvents();
        Mutators.registerEvents();

        Events.on(ClientLoadEvent.class, e -> {
            ui = new RogueUI();
            ui.init();

            if(AutoTest.enabled()){
                new AutoTest().begin();
            }
        });
    }

    @Override
    public void loadContent(){
        Mutators.load();
        Rewards.load();
    }
}
