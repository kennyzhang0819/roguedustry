package mindustrysurvivors;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.defense.ForceProjector.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.production.Drill.*;
import mindustry.world.blocks.storage.CoreBlock.*;

import static mindustry.Vars.*;

public class BlockMods{
    static final float tile = 8f;
    static final int golemEvery = 20, fungalMax = 3;

    static final ObjectSet<Block> scrapWalls = new ObjectSet<>(), copperWalls = new ObjectSet<>(), titaniumWalls = new ObjectSet<>(),
        plastaniumWalls = new ObjectSet<>(), thoriumWalls = new ObjectSet<>(), phaseWalls = new ObjectSet<>(), surgeWalls = new ObjectSet<>(), menders = new ObjectSet<>();
    static final ObjectMap<Block, Block> fallbackTo = new ObjectMap<>();

    public static final IntFloatMap bonusItems = new IntFloatMap();
    static final IntFloatMap triageReady = new IntFloatMap(), repulsed = new IntFloatMap(), gridReady = new IntFloatMap(), forceLast = new IntFloatMap(), alchemy = new IntFloatMap();
    static final IntSet forceBroken = new IntSet(), refracted = new IntSet();
    static final IntMap<IntSeq> crawlers = new IntMap<>();
    static final Seq<Bullet> homing = new Seq<>();
    static final Seq<Building> oilWells = new Seq<>();
    static float t10, t20, t30, t60, t90, t120, t180, t600, t1800, cleanup;
    public static int scrapDeaths, mirrored;

    public static void load(){
        scrapWalls.addAll(Blocks.scrapWall, Blocks.scrapWallLarge, Blocks.scrapWallHuge, Blocks.scrapWallGigantic);
        copperWalls.addAll(Blocks.copperWall, Blocks.copperWallLarge);
        titaniumWalls.addAll(Blocks.titaniumWall, Blocks.titaniumWallLarge);
        plastaniumWalls.addAll(Blocks.plastaniumWall, Blocks.plastaniumWallLarge);
        thoriumWalls.addAll(Blocks.thoriumWall, Blocks.thoriumWallLarge);
        phaseWalls.addAll(Blocks.phaseWall, Blocks.phaseWallLarge);
        surgeWalls.addAll(Blocks.surgeWall, Blocks.surgeWallLarge);
        menders.addAll(Blocks.mender, Blocks.mendProjector);
        fallbackTo.put(Blocks.titaniumWall, Blocks.copperWall);
        fallbackTo.put(Blocks.titaniumWallLarge, Blocks.copperWallLarge);
    }

    static boolean has(Rewards.Reward reward){
        return RogueRun.active() && RogueRun.current.stacks(reward) > 0;
    }

    static Team team(){
        return state.rules.defaultTeam;
    }

    public static float speed(Building build){
        float mult = 1f;
        if(build.block == Blocks.pneumaticDrill && has(Rewards.airPressure) && build.optionalEfficiency < 0.5f) mult *= ((Drill)build.block).liquidBoostIntensity;
        if(build.block == Blocks.blastDrill && has(Rewards.coreTap)) mult *= 1f + 0.25f * (team().data().getBuildings(Blocks.blastDrill).size - 1);
        return mult;
    }

    static void update(){
        float d = Time.delta;
        boolean on10 = (t10 += d) >= 10f, on20 = (t20 += d) >= 20f, on30 = (t30 += d) >= 30f, on60 = (t60 += d) >= 60f, on90 = (t90 += d) >= 90f,
            on120 = (t120 += d) >= 120f, on180 = (t180 += d) >= 180f, on600 = (t600 += d) >= 600f, on1800 = (t1800 += d) >= 1800f;
        if(on10) t10 = 0f;
        if(on20) t20 = 0f;
        if(on30) t30 = 0f;
        if(on60) t60 = 0f;
        if(on90) t90 = 0f;
        if(on120) t120 = 0f;
        if(on180) t180 = 0f;
        if(on600) t600 = 0f;
        if(on1800) t1800 = 0f;
        if((cleanup += d) > 1200f){
            cleanup = 0f;
            refracted.clear();
            repulsed.clear();
        }

        Team team = team();
        updateBullets(team);

        boolean selfSealing = has(Rewards.selfSealing), tarPit = has(Rewards.tarPit), radiation = has(Rewards.radiation), repulsor = has(Rewards.repulsor),
            tesla = has(Rewards.teslaFence), pillbox = has(Rewards.pillbox), triage = has(Rewards.triage), fortress = has(Rewards.fortressShield), backlash = has(Rewards.backlash),
            wide = has(Rewards.wideBore), lucky = has(Rewards.luckyStrike), haul = has(Rewards.doubleHaul), vein = has(Rewards.deepVein), prospector = has(Rewards.prospector),
            alch = has(Rewards.alchemy), overpressure = has(Rewards.overpressure), sprinkler = has(Rewards.sprinkler), geyser = has(Rewards.geyser), spores = has(Rewards.sporeCloud),
            fungal = has(Rewards.fungalArmy), slick = has(Rewards.oilSlick), firewall = has(Rewards.firewall);
        CoreBuild core = team.core();
        Item rarest = lucky && on60 ? rarest() : null;
        if(on60) oilWells.clear();

        for(Building b : team.data().buildings){
            Block block = b.block;
            float reach = block.size * tile / 2f;

            if(block == Blocks.forceProjector && b instanceof ForceBuild f){
                updateForce(f, fortress, backlash);
                continue;
            }
            if(plastaniumWalls.contains(block)){
                if(selfSealing && on30 && b.damaged()) b.heal(b.maxHealth * 0.015f);
                if(tarPit && on20) Units.nearbyEnemies(team, b.x, b.y, reach + tile, u -> {
                    if(!u.isFlying()) u.apply(StatusEffects.unmoving, 120f);
                });
            }else if(thoriumWalls.contains(block)){
                if(radiation && on60) Units.nearbyEnemies(team, b.x, b.y, reach + 3f * tile, u -> u.damage(30f));
            }else if(phaseWalls.contains(block)){
                if(repulsor && on10) Units.nearbyEnemies(team, b.x, b.y, reach + tile, u -> repulse(u, core));
            }else if(surgeWalls.contains(block)){
                if(tesla && on60){
                    Unit target = Units.closestEnemy(team, b.x, b.y, reach + 5f * tile, u -> true);
                    if(target != null) Lightning.create(team, Pal.surge, 20f, b.x, b.y, b.angleTo(target), (int)(b.dst(target) / 8f) + 3);
                }
            }else if(copperWalls.contains(block)){
                if(pillbox && on60) shoot(b, ((ItemTurret)Blocks.duo).ammoTypes.get(Items.copper), 6f);
            }else if(menders.contains(block)){
                if(triage && on30 && triageReady.get(b.id, 0f) <= Time.time) triage(b);
            }else if(block == Blocks.mechanicalDrill && b instanceof DrillBuild drill){
                if(wide && on30) widen(drill, 2);
                if(rarest != null && core != null && b.efficiency > 0f && Mathf.chance(0.02)){
                    core.items.add(rarest, Math.min(25, core.getMaximumAccepted(rarest) - core.items.get(rarest)));
                    Fx.upgradeCore.at(b.x, b.y, block.size);
                }
            }else if(block == Blocks.pneumaticDrill && b instanceof DrillBuild drill){
                if(vein && on30 && drill.dominantItem != null && drill.dominantItem != Items.titanium) drill.dominantItem = Items.titanium;
                if(haul && on60 && drill.dominantItem != null) bonus(drill, drill.dominantItem, 1f);
            }else if(block == Blocks.laserDrill && b instanceof DrillBuild drill){
                if(prospector && on60) prospect(drill);
                if(alch && on60 && core != null){
                    float gained = alchemy.get(b.id, 0f) + drill.lastDrillSpeed * 60f * b.timeScale() / 10f;
                    int whole = (int)gained;
                    if(whole > 0) core.items.add(Items.thorium, Math.min(whole, core.getMaximumAccepted(Items.thorium) - core.items.get(Items.thorium)));
                    alchemy.put(b.id, gained - whole);
                }
            }else if(block == Blocks.blastDrill && b instanceof DrillBuild drill){
                if(overpressure && on1800 && core != null && drill.dominantItem != null){
                    int amount = (int)(drill.lastDrillSpeed * b.timeScale() * 60f * 60f);
                    core.items.add(drill.dominantItem, Math.max(0, Math.min(amount, core.getMaximumAccepted(drill.dominantItem) - core.items.get(drill.dominantItem))));
                    Fx.spawnShockwave.at(b.x, b.y, block.size * tile);
                }
            }else if(block == Blocks.waterExtractor){
                if(sprinkler && on60){
                    Units.nearbyEnemies(team, b.x, b.y, 6f * tile, u -> u.apply(StatusEffects.wet, 300f));
                    extinguish(b, 6);
                    Fx.bubble.at(b.x, b.y);
                }
                if(geyser && on600){
                    Fx.spawnShockwave.at(b.x, b.y, 8f * tile);
                    Units.nearbyEnemies(team, b.x, b.y, 8f * tile, u -> {
                        u.impulse(Tmp.v1.set(u).sub(b).setLength(18f * u.mass()));
                        u.apply(StatusEffects.wet, 300f);
                    });
                }
            }else if(block == Blocks.cultivator){
                if(spores && on60) Units.nearbyEnemies(team, b.x, b.y, 6f * tile, u -> {
                    u.apply(StatusEffects.sporeSlowed, 120f);
                    u.damage(10f);
                });
                if(fungal && on1800) growCrawler(b);
            }else if(block == Blocks.oilExtractor){
                if(on60) oilWells.add(b);
                if(slick && on60) Units.nearbyEnemies(team, b.x, b.y, 6f * tile, u -> u.apply(StatusEffects.tarred, 240f));
            }
        }
    }

    //drill speed follows how many ore tiles it counts, so counting the ore around it makes it mine more
    static void widen(DrillBuild drill, int extra){
        Item item = drill.dominantItem;
        if(item == null) return;
        int size = drill.block.size, off = -(size - 1) / 2 - extra, count = 0;
        for(int x = 0; x < size + extra * 2; x++){
            for(int y = 0; y < size + extra * 2; y++){
                Tile t = world.tile(drill.tileX() + off + x, drill.tileY() + off + y);
                if(t != null && t.drop() == item) count++;
            }
        }
        drill.dominantItems = Math.max(drill.dominantItems, count);
    }

    //adds the given share of what the drill mined this second as extra items, keeping leftover fractions for later
    static void bonus(DrillBuild drill, Item item, float share){
        int key = drill.id * 64 + item.id;
        float gained = bonusItems.get(key, 0f) + drill.lastDrillSpeed * drill.timeScale() * 60f * share;
        int whole = Math.min((int)gained, 60);
        for(int i = 0; i < whole; i++) drill.offload(item);
        bonusItems.put(key, gained - whole);
    }

    static void prospect(DrillBuild drill){
        if(drill.dominantItem == null || drill.dominantItems <= 0) return;
        Drill block = (Drill)drill.block;
        ObjectIntMap<Item> ores = new ObjectIntMap<>();
        int size = block.size, off = -(size - 1) / 2;
        for(int x = 0; x < size; x++){
            for(int y = 0; y < size; y++){
                Tile t = world.tile(drill.tileX() + off + x, drill.tileY() + off + y);
                Item drop = t == null ? null : t.drop();
                if(drop != null && drop != drill.dominantItem && drop.hardness <= block.tier) ores.increment(drop);
            }
        }
        for(var e : ores) bonus(drill, e.key, (float)e.value / drill.dominantItems);
    }

    static Item rarest(){
        Item best = null;
        for(Item item : content.items()){
            if(item.unlockedNow() && item.isOnPlanet(Planets.serpulo) && !item.isHidden() && (best == null || item.cost > best.cost)) best = item;
        }
        return best;
    }

    static void shoot(Building b, BulletType type, float tiles){
        Unit target = Units.closestEnemy(b.team, b.x, b.y, tiles * tile + b.block.size * tile / 2f, u -> true);
        if(target == null) return;
        type.create(b, b.team, b.x, b.y, b.angleTo(target));
    }

    static void extinguish(Building b, int tiles){
        int cx = b.tileX(), cy = b.tileY();
        for(int x = -tiles; x <= tiles; x++){
            for(int y = -tiles; y <= tiles; y++){
                if(x * x + y * y > tiles * tiles || !Fires.has(cx + x, cy + y)) continue;
                Tile t = world.tile(cx + x, cy + y);
                if(t != null) Fires.extinguish(t, 1000f);
            }
        }
    }

    //a ground unit standing inside a solid building is killed at once, so units are placed on clear ground beside it
    public static Unit spawnBeside(UnitType type, Team team, Building b){
        float dist = b.block.size * tile / 2f + type.hitSize / 2f + tile;
        for(int i = 0; i < 16; i++){
            Tmp.v1.trns(Mathf.random(360f), dist + Mathf.random(2f * tile)).add(b);
            Tile t = world.tileWorld(Tmp.v1.x, Tmp.v1.y);
            if(type.flying || (t != null && !t.solid() && !t.floor().isDeep())) return type.spawn(team, Tmp.v1.x, Tmp.v1.y);
        }
        return type.spawn(team, b.x, b.y + dist);
    }

    public static void growCrawler(Building b){
        IntSeq ids = crawlers.get(b.id);
        if(ids == null) crawlers.put(b.id, ids = new IntSeq());
        for(int i = ids.size - 1; i >= 0; i--){
            Unit u = Groups.unit.getByID(ids.get(i));
            if(u == null || u.dead) ids.removeIndex(i);
        }
        if(ids.size >= fungalMax) return;
        Unit u = spawnBeside(UnitTypes.crawler, b.team, b);
        ids.add(u.id);
        Fx.spawn.at(u);
    }

    static void repulse(Unit u, @Nullable Building core){
        if(core == null || repulsed.get(u.id, -999f) > Time.time) return;
        repulsed.put(u.id, Time.time + 120f);
        float fromX = u.x, fromY = u.y;
        for(float len = 10f * tile; len > 2f * tile; len -= 2f * tile){
            Tmp.v1.set(u).sub(core).setLength(len).add(u);
            Tile t = world.tileWorld(Tmp.v1.x, Tmp.v1.y);
            if(t == null || (!u.isFlying() && t.solid())) continue;
            u.set(Tmp.v1.x, Tmp.v1.y);
            u.snapInterpolation();
            Fx.teleportOut.at(fromX, fromY, u.hitSize / 2f);
            Fx.teleportOut.at(u.x, u.y, u.hitSize / 2f);
            return;
        }
    }

    static void triage(Building mender){
        float range = ((MendProjector)mender.block).range;
        boolean[] healed = {false};
        indexer.eachBlock(mender, range, other -> other.damaged() && other.health < other.maxHealth * 0.2f, other -> {
            other.heal();
            Fx.healBlockFull.at(other.x, other.y, other.block.size, Pal.heal, other.block);
            healed[0] = true;
        });
        if(healed[0]) triageReady.put(mender.id, Time.time + 600f);
    }

    static void updateForce(ForceBuild f, boolean fortress, boolean backlash){
        ForceProjector block = (ForceProjector)f.block;
        float last = forceLast.get(f.id, f.buildup);
        if(fortress){
            if(f.buildup > last) f.buildup = last + (f.buildup - last) / 3f;
            if(f.broken) f.buildup = Math.max(f.buildup - block.cooldownBrokenBase * Time.delta, 0f);
        }
        forceLast.put(f.id, f.buildup);
        boolean wasBroken = forceBroken.contains(f.id);
        if(f.broken && !wasBroken){
            forceBroken.add(f.id);
            if(backlash){
                float strength = block.shieldHealth * (fortress ? 3f : 1f), radius = block.radius;
                Fx.spawnShockwave.at(f.x, f.y, radius);
                Units.nearbyEnemies(f.team, f.x, f.y, radius, u -> u.damage(strength));
            }
        }else if(!f.broken && wasBroken){
            forceBroken.remove(f.id);
        }
    }

    //runs before the game moves anything, so a bullet is turned around before the force field would absorb it
    static void mirror(){
        if(!has(Rewards.mirror)) return;
        Team team = team();
        for(Building b : team.data().getBuildings(Blocks.forceProjector)){
            if(!(b instanceof ForceBuild f) || f.broken) continue;
            float r = f.realRadius();
            if(r <= 1f) continue;
            float reach = r + 40f;
            Groups.bullet.intersect(f.x - reach, f.y - reach, reach * 2f, reach * 2f, bullet -> {
                if(bullet.team == team || !bullet.type.absorbable || !bullet.within(f, r + bullet.vel.len() * 2f + 4f)) return;
                bullet.team = team;
                bullet.owner = f;
                bullet.vel.scl(-1f);
                Fx.absorb.at(bullet);
                mirrored++;
            });
        }
    }

    static void updateBullets(Team team){
        boolean refraction = has(Rewards.refraction);
        if(refraction){
            for(Bullet b : Groups.bullet){
                if(b.team != team || !(b.owner instanceof Building w) || !phaseWalls.contains(w.block) || !refracted.add(b.id)) continue;
                refract(b);
            }
        }
        for(int i = homing.size - 1; i >= 0; i--){
            Bullet b = homing.get(i);
            if(!b.isAdded() || b.team != team){
                homing.remove(i);
                continue;
            }
            Unit target = Units.closestEnemy(team, b.x, b.y, 20f * tile, u -> true);
            if(target != null) b.vel.setAngle(Angles.moveToward(b.vel.angle(), b.angleTo(target), 8f * Time.delta));
        }
    }

    static void refract(Bullet b){
        homing.add(b);
        for(int side = -1; side <= 1; side += 2){
            Bullet copy = b.type.create(b.owner, b.team, b.x, b.y, b.rotation() + side * 25f, b.damage, 1f, 1f, null);
            if(copy == null) continue;
            refracted.add(copy.id);
            homing.add(copy);
        }
    }

    static void reflect(Building wall, Bullet source, float damageScale){
        if(source.vel.len() <= 0.1f) return;
        Bullet copy = source.type.create(wall, wall.team, source.x, source.y, source.rotation() + 180f, source.damage * damageScale, 1f, 1f, null);
        if(copy != null && phaseWalls.contains(wall.block) && has(Rewards.refraction) && refracted.add(copy.id)) refract(copy);
    }

    static void hit(Building b, Bullet source){
        Block block = b.block;
        if(copperWalls.contains(block) && has(Rewards.thorns) && source.owner instanceof Healthc owner && source.owner instanceof Teamc t && t.team() != b.team){
            owner.damage(source.type.buildingDamage(source) * 0.5f);
        }
        if(plastaniumWalls.contains(block) && has(Rewards.elastic)) reflect(b, source, 2f);
        if(phaseWalls.contains(block) && has(Rewards.phaseLattice) && block instanceof Wall w && Mathf.chance(w.chanceDeflect / Math.max(source.damage(), 1f))) reflect(b, source, 1f);
        if(surgeWalls.contains(block) && block instanceof Wall w){
            if(has(Rewards.overcharge) && Mathf.chance(w.lightningChance * 2f)){
                Lightning.create(b.team, w.lightningColor, w.lightningDamage, b.x, b.y, source.rotation() + 180f, w.lightningLength);
            }
            if(has(Rewards.powerGrid)) powerGrid(b, w);
        }
    }

    static void powerGrid(Building start, Wall w){
        IntSet visited = new IntSet();
        Seq<Building> queue = Seq.with(start);
        visited.add(start.id);
        int count = 0;
        while(queue.any() && count < 30){
            Building b = queue.pop();
            count++;
            if(gridReady.get(b.id, 0f) <= Time.time){
                gridReady.put(b.id, Time.time + 30f);
                Lightning.create(b.team, w.lightningColor, w.lightningDamage, b.x, b.y, Mathf.random(360f), w.lightningLength);
            }
            for(Building near : b.proximity){
                if(surgeWalls.contains(near.block) && near.team == b.team && visited.add(near.id)) queue.add(near);
            }
        }
    }

    public static boolean resurrects(Building build){
        if(!has(Rewards.resurrection) || build.team != team() || build instanceof ConstructBuild || build instanceof CoreBuild) return false;
        for(Building b : team().data().buildings){
            if(menders.contains(b.block) && b != build && b.within(build, ((MendProjector)b.block).range)) return true;
        }
        return false;
    }

    static void destroyed(Building b){
        if(b.team != team() || b instanceof ConstructBuild) return;
        Block block = b.block;
        Tile spot = b.tile;
        float x = b.x, y = b.y;

        if(scrapWalls.contains(block)){
            scrapDeaths++;
            if(has(Rewards.junkGolem) && scrapDeaths % golemEvery == 0 && team().core() != null){
                Building core = team().core();
                Fx.spawn.at(spawnBeside(UnitTypes.fortress, b.team, core));
            }
            if(has(Rewards.selfAssembly)) TurretMods.scheduleRebuild(spot, block, b.team, b.rotation, 60f * 5f);
        }
        if(titaniumWalls.contains(block)){
            if(has(Rewards.shrapnel)){
                Damage.damage(b.team, x, y, 4f * tile + block.size * tile / 2f, b.maxHealth, true, true);
                Fx.flakExplosionBig.at(x, y);
            }
            if(has(Rewards.fallback)){
                Block next = fallbackTo.get(block);
                Core.app.post(() -> {
                    if(spot.build == null && next != null) spot.setNet(next, b.team, 0);
                });
            }
        }
        if(thoriumWalls.contains(block) && has(Rewards.criticalMass)){
            Damage.damage(b.team, x, y, 10f * tile, 2000f, true, true);
            Fx.reactorExplosion.at(x, y);
            Effect.shake(8f, 30f, x, y);
        }
        if(!TurretMods.rebuilding.containsKey(spot.pos()) && !(has(Rewards.fallback) && titaniumWalls.contains(block)) && resurrects(b)){
            TurretMods.scheduleRebuild(spot, block, b.team, b.rotation, 60f * 5f);
        }
    }

    static void enemyHit(Unit unit){
        if(!has(Rewards.firewall) || !unit.hasEffect(StatusEffects.tarred)) return;
        for(Building well : oilWells){
            if(well.isValid() && well.within(unit, 10f * tile)){
                unit.apply(StatusEffects.burning, 300f);
                Fx.fire.at(unit.x, unit.y);
                return;
            }
        }
    }

    public static void reset(){
        triageReady.clear();
        repulsed.clear();
        gridReady.clear();
        forceLast.clear();
        forceBroken.clear();
        refracted.clear();
        alchemy.clear();
        bonusItems.clear();
        crawlers.clear();
        homing.clear();
        oilWells.clear();
        scrapDeaths = 0;
    }

    static void registerEvents(){
        Events.run(Trigger.update, () -> {
            if(RogueRun.active() && !state.isPaused()) mirror();
        });

        Events.run(Trigger.afterGameUpdate, () -> {
            if(RogueRun.active() && !state.isPaused()) update();
        });

        Events.on(WorldLoadBeginEvent.class, e -> reset());

        Events.on(BuildDamageEvent.class, e -> {
            if(RogueRun.active() && e.build.team == team() && e.source != null && e.source.team != team()) hit(e.build, e.source);
        });

        Events.on(BlockDestroyEvent.class, e -> {
            if(RogueRun.active() && e.tile.build != null) destroyed(e.tile.build);
        });

        Events.on(UnitDamageEvent.class, e -> {
            if(RogueRun.active() && e.unit.team == state.rules.waveTeam) enemyHit(e.unit);
        });
    }
}
