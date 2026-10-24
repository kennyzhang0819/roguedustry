package roguedustry;

import arc.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;

public class RoguedustryMod extends Mod{
    public static final boolean roguelike = true, dev = System.getenv("ROGUE_DEV") != null && !System.getenv("ROGUE_DEV").isEmpty();

    public static RogueUI ui;
    public static WirelessUI wirelessUI;

    public RoguedustryMod(){
        RogueRun.registerEvents();
        Curses.registerEvents();
        Wireless.register();
        TurretMods.registerEvents();
        BlockMods.registerEvents();
        Arena.register();
        Chase.register();
        Survivor.register();

        //only local test launches set ROGUE_DEV; weather, the planet's haze and day and night are switched off there, never in the released mod
        Events.on(WorldLoadEvent.class, e -> {
            if(dev) Vars.state.rules.lighting = false;
        });

        Events.on(ClientLoadEvent.class, e -> {
            if(dev){
                Planets.serpulo.updateLighting = false;
                Planets.serpulo.hasAtmosphere = false;
                Vars.renderer.drawWeather = false;
            }
            if(System.getenv("ROGUE_NOMUSIC") != null) Core.audio.musicBus.setVolume(0f);
            if(System.getenv("ROGUE_QUIET") != null){
                Core.audio.soundBus.setVolume(0.5f);
                Vars.control.sound.uiBus.setVolume(0.5f);
            }

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
        Arena.load();
        Chase.load();
        Survivor.load();
        Curses.load();
        Rewards.load();
        TurretMods.load();
        BlockMods.load();
    }
}
