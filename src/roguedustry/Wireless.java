package roguedustry;

import arc.*;
import arc.struct.*;
import arc.util.*;
import java.io.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.campaign.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.defense.turrets.ItemTurret.*;
import mindustry.world.blocks.defense.turrets.LiquidTurret.*;
import mindustry.world.blocks.defense.turrets.Turret.*;
import mindustry.world.blocks.liquid.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.power.NuclearReactor.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.blocks.storage.StorageBlock.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class Wireless{
    public static final String ammo = "ammo", coolant = "coolant", fuel = "fuel", boost = "boost", none = "none", on = "on", off = "off";
    public static final float liquidPerCore = 1000f, interval = 5f;

    public static final ObjectFloatMap<Liquid> pool = new ObjectFloatMap<>();
    public static final IntMap<StringMap> choices = new IntMap<>();
    static final IntMap<Block> choiceBlocks = new IntMap<>();
    static float timer, capacity;

    public static boolean banned(Block block){
        //terrain, ores and hidden blocks also count as "distribution" in the game, so only buildable ones are checked
        if(block.buildVisibility == BuildVisibility.hidden || block.buildVisibility == BuildVisibility.sandboxOnly) return false;
        return block.category == Category.distribution
            || block instanceof Conduit || block instanceof LiquidJunction || block instanceof LiquidBridge || block == Blocks.liquidRouter
            || block instanceof PayloadConveyor;
    }

    public static void register(){
        SaveVersion.addCustomChunk("roguedustry-wireless", new SaveFileReader.CustomChunk(){
            @Override
            public boolean shouldWrite(){
                return RogueRun.wirelessRun();
            }

            @Override
            public void write(DataOutput out) throws IOException{
                out.writeInt(1);
                out.writeInt(pool.size);
                for(var e : pool){
                    out.writeUTF(e.key.name);
                    out.writeFloat(e.value);
                }
                out.writeInt(choices.size);
                for(var e : choices){
                    Block block = choiceBlocks.get(e.key);
                    out.writeInt(e.key);
                    out.writeUTF(block == null ? "" : block.name);
                    out.writeInt(e.value.size);
                    for(var v : e.value){
                        out.writeUTF(v.key);
                        out.writeUTF(v.value);
                    }
                }
            }

            @Override
            public void read(DataInput in) throws IOException{
                in.readInt();
                int liquids = in.readInt();
                for(int i = 0; i < liquids; i++){
                    Liquid liquid = content.liquid(in.readUTF());
                    float amount = in.readFloat();
                    if(liquid != null) pool.put(liquid, amount);
                }
                int count = in.readInt();
                for(int i = 0; i < count; i++){
                    int pos = in.readInt();
                    Block block = content.block(in.readUTF());
                    int values = in.readInt();
                    StringMap map = new StringMap();
                    for(int j = 0; j < values; j++) map.put(in.readUTF(), in.readUTF());
                    if(block != null){
                        choices.put(pos, map);
                        choiceBlocks.put(pos, block);
                    }
                }
            }
        });

        //custom save chunks are read after this event, so clearing here never wipes loaded data
        Events.on(WorldLoadBeginEvent.class, e -> {
            pool.clear();
            choices.clear();
            choiceBlocks.clear();
        });

        Events.on(WorldLoadEvent.class, e -> Core.app.post(() -> {
            if(!RogueRun.wirelessOn()) return;
            for(Block block : content.blocks()){
                if(banned(block)) state.rules.bannedBlocks.add(block);
            }
            if(!headless) ui.hudfrag.blockfrag.rebuild();
        }));

        Events.run(Trigger.afterGameUpdate, () -> {
            if(!RogueRun.wirelessOn() || state.isPaused() || net.client()) return;
            if((timer += Time.delta) < interval) return;
            timer = 0f;
            update();
        });
    }

    public static float capacity(){
        return capacity;
    }

    static void update(){
        Team team = state.rules.defaultTeam;
        CoreBuild core = team.core();
        if(core == null) return;

        float cap = team.cores().size * liquidPerCore;
        for(Building b : team.data().buildings){
            if(b.block == Blocks.liquidTank || b.block == Blocks.liquidContainer) cap += b.block.liquidCapacity;
        }
        capacity = cap;

        for(Building b : team.data().buildings){
            if(skip(b)) continue;
            collect(b, core);
        }
        for(Building b : team.data().buildings){
            if(b instanceof NuclearReactorBuild) feed(b, core);
        }
        for(Building b : team.data().buildings){
            if(b instanceof TurretBuild) feed(b, core);
        }
        for(Building b : team.data().buildings){
            if(!(b instanceof NuclearReactorBuild) && !(b instanceof TurretBuild) && !skip(b)) feed(b, core);
        }
    }

    static boolean skip(Building b){
        return b instanceof CoreBuild || b instanceof StorageBuild || b instanceof ConstructBuild || b.block instanceof LaunchPad
            || b.block instanceof LiquidRouter || banned(b.block);
    }

    static void collect(Building b, CoreBuild core){
        if(b instanceof TurretBuild) return;
        //unit factories and reconstructors don't mark their inputs, so their items would look like output
        boolean dynamic = false;
        for(Consume c : b.block.consumers) if(c instanceof ConsumeItemDynamic) dynamic = true;
        if(b.items != null && b.block.hasItems && !dynamic){
            b.items.each((item, amount) -> {
                if(amount <= 0 || b.block.consumesItem(item)) return;
                core.handleStack(item, amount, b);
                b.items.remove(item, amount);
            });
        }
        if(b.liquids != null && b.block.hasLiquids){
            b.liquids.each((liquid, amount) -> {
                if(amount <= 0.001f || b.block.consumesLiquid(liquid)) return;
                pool.put(liquid, Math.min(pool.get(liquid, 0f) + amount, capacity));
                b.liquids.remove(liquid, amount);
            });
        }
    }

    static void feed(Building b, CoreBuild core){
        if(b instanceof ItemTurretBuild){
            Item item = (Item)pick(b, ammo);
            if(item != null) giveItem(b, item, core);
        }else if(b instanceof LiquidTurretBuild){
            Liquid liquid = (Liquid)pick(b, ammo);
            if(liquid != null) giveLiquid(b, liquid);
        }

        boolean boosting = boosting(b);
        for(Consume c : b.block.consumers){
            if(c instanceof ConsumeCoolant){
                Liquid liquid = (Liquid)pick(b, coolant);
                if(liquid != null) giveLiquid(b, liquid);
            }else if(c.optional || c.booster){
                if(!boosting) continue;
                if(c instanceof ConsumeItems ci) for(ItemStack s : ci.items) giveItem(b, s.item, core);
                else if(c instanceof ConsumeLiquid cl) giveLiquid(b, cl.liquid);
                else if(c instanceof ConsumeLiquids cls) for(LiquidStack s : cls.liquids) giveLiquid(b, s.liquid);
            }else if(b instanceof TurretBuild){
                continue;
            }else if(c instanceof ConsumeItems ci){
                for(ItemStack s : ci.items) giveItem(b, s.item, core);
            }else if(c instanceof ConsumeItemDynamic cd){
                for(ItemStack s : cd.items.get(b)) giveItem(b, s.item, core);
            }else if(c instanceof ConsumeItemFilter){
                Item item = (Item)pick(b, fuel);
                if(item != null) giveItem(b, item, core);
            }else if(c instanceof ConsumeLiquid cl){
                giveLiquid(b, cl.liquid);
            }else if(c instanceof ConsumeLiquids cls){
                for(LiquidStack s : cls.liquids) giveLiquid(b, s.liquid);
            }else if(c instanceof ConsumeLiquidFilter cf){
                Liquid best = null;
                for(Liquid l : content.liquids()) if(cf.filter.get(l) && pool.get(l, 0f) > 0.01f){ best = l; break; }
                if(best != null) giveLiquid(b, best);
            }
        }
    }

    static void giveItem(Building b, Item item, CoreBuild core){
        int have = core.items.get(item);
        if(have <= 0 || !b.acceptItem(core, item)) return;
        int amount = Math.min(have, b.acceptStack(item, have, null));
        if(amount <= 0) return;
        b.handleStack(item, amount, null);
        core.items.remove(item, amount);
    }

    static void giveLiquid(Building b, Liquid liquid){
        float have = pool.get(liquid, 0f);
        if(have <= 0.001f || !b.acceptLiquid(b, liquid)) return;
        float amount = Math.min(have, b.block.liquidCapacity - b.liquids.get(liquid));
        if(amount <= 0.001f) return;
        b.handleLiquid(b, liquid, amount);
        pool.put(liquid, have - amount);
    }

    public static Seq<UnlockableContent> options(Building b, String key){
        Seq<UnlockableContent> out = new Seq<>();
        if(key.equals(ammo)){
            if(b.block instanceof ItemTurret t) out.addAll(t.ammoTypes.keys().toSeq());
            if(b.block instanceof LiquidTurret t) out.addAll(t.ammoTypes.keys().toSeq());
        }else if(key.equals(coolant)){
            for(Consume c : b.block.consumers){
                if(c instanceof ConsumeCoolant cc) out.addAll(content.liquids().select(cc.filter::get));
            }
        }else if(key.equals(fuel) && !(b instanceof TurretBuild)){
            for(Consume c : b.block.consumers){
                if(c instanceof ConsumeItemFilter f && !c.optional) out.addAll(content.items().select(f.filter::get));
            }
        }
        return out;
    }

    public static boolean hasBoost(Building b){
        for(Consume c : b.block.consumers){
            if((c.optional || c.booster) && !(c instanceof ConsumeCoolant) && (c instanceof ConsumeItems || c instanceof ConsumeLiquid || c instanceof ConsumeLiquids)) return true;
        }
        return false;
    }

    static boolean boostsWithItems(Building b){
        for(Consume c : b.block.consumers){
            if((c.optional || c.booster) && c instanceof ConsumeItems) return true;
        }
        return false;
    }

    public static boolean boosting(Building b){
        String v = get(b, boost);
        return v == null ? !boostsWithItems(b) : v.equals(on);
    }

    public static @Nullable UnlockableContent picked(Building b, String key){
        String v = get(b, key);
        if(v == null || v.equals(none)) return null;
        for(UnlockableContent c : options(b, key)) if(c.name.equals(v)) return c;
        return null;
    }

    static @Nullable UnlockableContent pick(Building b, String key){
        String v = get(b, key);
        if(none.equals(v)) return null;
        UnlockableContent chosen = picked(b, key);
        if(chosen != null) return chosen;
        Seq<UnlockableContent> opts = options(b, key);
        if(key.equals(coolant)){
            Liquid best = null;
            for(UnlockableContent c : opts){
                Liquid l = (Liquid)c;
                if(pool.get(l, 0f) > 0.01f && (best == null || l.heatCapacity > best.heatCapacity)) best = l;
            }
            return best;
        }
        if(key.equals(ammo)){
            for(int i = opts.size - 1; i >= 0; i--) if(stock(opts.get(i))) return opts.get(i);
            return null;
        }
        for(UnlockableContent c : opts) if(stock(c)) return c;
        return null;
    }

    static boolean stock(UnlockableContent c){
        CoreBuild core = state.rules.defaultTeam.core();
        if(c instanceof Item item) return core != null && core.items.get(item) > 0;
        return c instanceof Liquid l && pool.get(l, 0f) > 0.01f;
    }

    public static @Nullable String get(Building b, String key){
        StringMap map = choices.get(b.pos());
        return map == null || choiceBlocks.get(b.pos()) != b.block ? null : map.get(key);
    }

    public static void set(Building b, String key, @Nullable String value){
        StringMap map = choices.get(b.pos());
        if(map == null || choiceBlocks.get(b.pos()) != b.block){
            map = new StringMap();
            choices.put(b.pos(), map);
            choiceBlocks.put(b.pos(), b.block);
        }
        if(value == null) map.remove(key);
        else map.put(key, value);
    }
}
