package roguedustry;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.noise.*;
import mindustry.content.*;
import mindustry.world.*;

public class MapGen{
    public static final int size = 320;
    static final float k = size / 256f;
    static final int spawnClear = 4, spawnBand = 14, maxTries = 12;
    static final float spawnFromCore = 60f;

    public static class Result{
        public int seed, coreX, coreY, tries;
        public boolean sea;
        public String name;
        public Seq<String> biomes = new Seq<>();
        public byte[] region;
        public final IntSeq ground = new IntSeq(), water = new IntSeq();
        public Block[] floor, wall;
    }

    static class Biome{
        final String name;
        final Block[] floors, walls;
        final Block beach, shallow, deep;
        String[] kin = {};

        Biome(String name, Block beach, Block shallow, Block deep, Block... pairs){
            this.name = name;
            this.beach = beach;
            this.shallow = shallow;
            this.deep = deep;
            floors = new Block[pairs.length / 2];
            walls = new Block[pairs.length / 2];
            for(int i = 0; i < floors.length; i++){
                floors[i] = pairs[i * 2];
                walls[i] = pairs[i * 2 + 1];
            }
        }
    }

    static final String[][] kin = {
        {"Meadow", "Jungle", "Badlands"},
        {"Salt Pan", "Badlands"},
        {"Tundra", "Crystal Barrens"},
        {"Badlands", "Salt Pan", "Meadow", "Caldera"},
        {"Ashfall", "Caldera", "Spore Field"},
        {"Caldera", "Ashfall", "Badlands"},
        {"Jungle", "Meadow", "Spore Field"},
        {"Spore Field", "Jungle", "Ashfall", "Crystal Barrens"},
        {"Crystal Barrens", "Tundra", "Spore Field"}
    };

    static Seq<Biome> biomes(){
        Seq<Biome> all = Seq.with(
            new Biome("Meadow", Blocks.sand, Blocks.sandWater, Blocks.water, Blocks.grass, Blocks.pine, Blocks.stone, Blocks.stoneWall, Blocks.moss, Blocks.sporePine),
            new Biome("Salt Pan", Blocks.sand, Blocks.sandWater, Blocks.water, Blocks.salt, Blocks.saltWall, Blocks.sand, Blocks.sandWall, Blocks.dirt, Blocks.dirtWall),
            new Biome("Tundra", Blocks.snow, Blocks.water, Blocks.water, Blocks.snow, Blocks.snowWall, Blocks.iceSnow, Blocks.iceWall, Blocks.ice, Blocks.iceWall),
            new Biome("Badlands", Blocks.sand, Blocks.darksandWater, Blocks.water, Blocks.darksand, Blocks.duneWall, Blocks.sand, Blocks.sandWall, Blocks.dirt, Blocks.dirtWall),
            new Biome("Ashfall", Blocks.darksand, Blocks.darksandWater, Blocks.water, Blocks.basalt, Blocks.duneWall, Blocks.charr, Blocks.duneWall, Blocks.darksand, Blocks.duneWall),
            new Biome("Caldera", Blocks.darksand, Blocks.darksandWater, Blocks.water, Blocks.basalt, Blocks.duneWall, Blocks.hotrock, Blocks.duneWall, Blocks.magmarock, Blocks.duneWall),
            new Biome("Jungle", Blocks.sand, Blocks.sandWater, Blocks.water, Blocks.moss, Blocks.sporePine, Blocks.grass, Blocks.pine, Blocks.sporeMoss, Blocks.sporeWall),
            new Biome("Spore Field", Blocks.darksand, Blocks.darksandTaintedWater, Blocks.taintedWater, Blocks.sporeMoss, Blocks.sporeWall, Blocks.moss, Blocks.sporePine, Blocks.shale, Blocks.shaleWall),
            new Biome("Crystal Barrens", Blocks.sand, Blocks.sandWater, Blocks.water, Blocks.dacite, Blocks.daciteWall, Blocks.stone, Blocks.stoneWall, Blocks.snow, Blocks.snowWall)
        );
        for(String[] row : kin) all.find(b -> b.name.equals(row[0])).kin = java.util.Arrays.copyOfRange(row, 1, row.length);
        return all;
    }

    static Biome named(Seq<Biome> all, String name){
        return all.find(b -> b.name.equals(name));
    }

    public static Result generate(int seed){
        Throwable last = null;
        for(int t = 0; t < maxTries; t++){
            try{
                Result r = attempt(seed + t);
                if(r != null){
                    r.tries = t + 1;
                    return r;
                }
            }catch(Throwable e){
                last = e;
            }
        }
        throw new RuntimeException("no random map passed its checks", last);
    }

    static int idx(int x, int y){
        return y * size + x;
    }

    static boolean in(int x, int y){
        return x >= 0 && y >= 0 && x < size && y < size;
    }

    static final float[] waterWeights = {0.5f, 0.3f, 0.2f};
    static final int minRock = 60;

    //every map rolls its own landscape: big mountains or a wide open plain, small hills, and one kind of water
    static @Nullable Result attempt(int seed){
        Rand rnd = new Rand(seed);
        Seq<Biome> allBiomes = biomes();
        Biome biome = allBiomes.random(rnd);
        int n = size * size;

        float roll = rnd.random(1f), acc = waterWeights[0];
        int style = 0;
        while(style < waterWeights.length - 1 && roll > acc) acc += waterWeights[++style];
        boolean lakes = style == 0 || style == 2, coast = style == 1 || style == 2;

        int cx = size / 2 + (int)(rnd.range(40f) * k), cy = size / 2 + (int)(rnd.range(40f) * k);
        float[] water = new float[n], mount = new float[n];
        boolean[] wet = new boolean[n], rock = new boolean[n];

        if(lakes){
            float share = style == 2 ? rnd.random(0.04f, 0.1f) : rnd.random(0.06f, 0.18f);
            float small = rnd.random(6f, 12f) * k, big = rnd.random(14f, 32f) * k;
            float filled = 0f;
            for(int tries = 0; tries < 60 && filled < share * n; tries++){
                float x = rnd.random(size), y = rnd.random(size), radius = rnd.random(small, big);
                if(Mathf.dst(x, y, cx, cy) < radius * 1.5f + 12f * k) continue;
                blob(water, x, y, radius, seed + 200 + tries);
                filled += Mathf.PI * radius * radius;
            }
        }
        if(coast){
            int seas = rnd.chance(0.35) ? 2 : 1;
            for(int c = 0; c < seas; c++){
                float radius = rnd.random(70f, 150f) * k, out = radius * rnd.random(0.25f, 0.75f);
                Vec2 center;
                if(rnd.chance(0.3)){
                    float ang = 45f + 90f * rnd.random(3);
                    center = new Vec2(size / 2f, size / 2f).add(Tmp.v1.trns(ang, size * 0.7f + out * 0.5f));
                }else{
                    int side = rnd.random(3);
                    float t = rnd.random(size * 0.15f, size * 0.85f);
                    center = side == 0 ? new Vec2(-out, t) : side == 1 ? new Vec2(size + out, t) : side == 2 ? new Vec2(t, -out) : new Vec2(t, size + out);
                }
                if(center.dst(cx, cy) < radius + 30f * k) center.add(Tmp.v1.set(center).sub(cx, cy).setLength(radius + 30f * k - center.dst(cx, cy)));
                blob(water, center.x, center.y, radius, seed + 90 + c, 0.6f);
            }
        }
        for(int i = 0; i < n; i++){
            int x = i % size, y = i / size;
            //banks run in and out on fine noise, so no shore is a smooth line
            wet[i] = water[i] > 0.3f + (Simplex.noise2d(seed + 31, 2, 0.5, 1f / (12f * k), x, y) - 0.5f) * 0.4f;
        }

        int mountains = rnd.chance(0.75) ? rnd.random(1, 3) : 0;
        for(int m = 0, tries = 0; m < mountains && tries < 30; tries++){
            float x = rnd.random(30f, size - 30f), y = rnd.random(30f, size - 30f), radius = rnd.random(22f, 50f) * k;
            if(Mathf.dst(x, y, cx, cy) < radius + 18f * k) continue;
            blob(mount, x, y, radius, seed + 300 + m);
            m++;
        }
        float hills = mountains > 0 ? rnd.random(0.03f, 0.1f) : rnd.random(0.02f, 0.07f), hillScale = rnd.random(28f, 55f) * k, warp = rnd.random(0f, 20f) * k;
        float[] hv = new float[n];
        for(int y = 0; y < size; y++){
            for(int x = 0; x < size; x++){
                float wx = (Simplex.noise2d(seed + 11, 2, 0.5, 1f / (hillScale * 1.7f), x, y) - 0.5f) * warp * 2f;
                float wy = (Simplex.noise2d(seed + 12, 2, 0.5, 1f / (hillScale * 1.7f), x, y) - 0.5f) * warp * 2f;
                hv[idx(x, y)] = Simplex.noise2d(seed, 3, 0.5, 1f / hillScale, x + wx, y + wy);
            }
        }
        FloatSeq dry = new FloatSeq();
        for(int i = 0; i < n; i++) if(!wet[i]) dry.add(hv[i]);
        if(dry.size < n / 3) return null;
        float[] drySorted = dry.toArray();
        java.util.Arrays.sort(drySorted);
        float hillCut = drySorted[(int)((1f - hills) * (drySorted.length - 1))];
        for(int i = 0; i < n; i++){
            int x = i % size, y = i / size;
            boolean peak = mount[i] > 0.3f + (Simplex.noise2d(seed + 32, 2, 0.5, 1f / (12f * k), x, y) - 0.5f) * 0.45f;
            rock[i] = !wet[i] && (peak || hv[i] > hillCut);
        }
        clearSmall(rock, minRock);

        disc(rock, cx, cy, 9f * k, false);
        disc(wet, cx, cy, 7f * k, false);

        boolean[] reach = fill(cx, cy, i -> !rock[i]);
        int rocks = 0;
        for(int i = 0; i < n; i++){
            if(!reach[i]){
                rock[i] = true;
                wet[i] = false;
            }
            if(rock[i]) rocks++;
        }
        if(rocks > n * 0.45f) return null;

        byte[] depth = new byte[n];
        int[] fromShore = distance(wet);
        for(int i = 0; i < n; i++) if(wet[i]) depth[i] = (byte)(fromShore[i] >= 3 ? 2 : 1);

        Result r = new Result();
        r.seed = seed;
        r.coreX = cx;
        r.coreY = cy;
        r.sea = coast;
        r.name = biome.name + " " + landform(style, mountains);
        spawns(r.ground, i -> reach[i] && !rock[i] && !wet[i], cx, cy, spawnClear);
        spawns(r.water, i -> reach[i] && wet[i], cx, cy, 2);
        if(r.ground.size < 30) return null;

        paint(r, rock, depth, biome, allBiomes, seed, rnd);
        return r;
    }

    static String landform(int style, int mountains){
        if(style == 1) return mountains > 0 ? "Cliffs" : "Coast";
        if(style == 2) return "Shores";
        return mountains > 0 ? "Tarns" : "Lakes";
    }

    //a lake or mountain whose outline runs in and out around its middle
    static void blob(float[] field, float cx, float cy, float radius, int seed){
        blob(field, cx, cy, radius, seed, 0f);
    }

    //rough bends the outline on a slow noise over the ground too, so big shapes grow bays and headlands instead of staying round
    static void blob(float[] field, float cx, float cy, float radius, int seed, float rough){
        int ri = (int)Math.ceil(radius * 1.6f);
        for(int y = Math.max((int)cy - ri, 0); y <= Math.min(cy + ri, size - 1); y++){
            for(int x = Math.max((int)cx - ri, 0); x <= Math.min(cx + ri, size - 1); x++){
                float angle = Angles.angle(cx, cy, x, y), edge = radius / 0.7f * (0.65f + Simplex.noise2d(seed, 2, 0.5, 1f / 70f, angle, 0) * 0.7f);
                if(rough > 0f) edge *= 1f - rough / 2f + rough * Simplex.noise2d(seed + 7, 3, 0.5, 1f / (45f * k), x, y);
                int i = idx(x, y);
                field[i] = Math.max(field[i], Math.min(1f, 0.3f + (0.7f * edge - Mathf.dst(x, y, cx, cy)) / (8f * k)));
            }
        }
    }

    static void disc(boolean[] mask, float cx, float cy, float r, boolean value){
        int ri = (int)Math.ceil(r);
        for(int y = (int)cy - ri; y <= cy + ri; y++){
            for(int x = (int)cx - ri; x <= cx + ri; x++){
                if(in(x, y) && Mathf.dst(x, y, cx, cy) <= r) mask[idx(x, y)] = value;
            }
        }
    }

    //rock clusters too small to matter only get in the way, so they are cleared
    static void clearSmall(boolean[] rock, int min){
        boolean[] seen = new boolean[size * size];
        IntSeq group = new IntSeq();
        IntQueue queue = new IntQueue();
        for(int s = 0; s < rock.length; s++){
            if(!rock[s] || seen[s]) continue;
            group.clear();
            queue.addLast(s);
            seen[s] = true;
            while(!queue.isEmpty()){
                int i = queue.removeFirst(), x = i % size, y = i / size;
                group.add(i);
                for(Point2 d : Geometry.d4){
                    int nx = x + d.x, ny = y + d.y;
                    if(!in(nx, ny)) continue;
                    int j = idx(nx, ny);
                    if(rock[j] && !seen[j]){
                        seen[j] = true;
                        queue.addLast(j);
                    }
                }
            }
            if(group.size < min) for(int g = 0; g < group.size; g++) rock[group.get(g)] = false;
        }
    }

    //how many steps each wet tile is from the nearest dry one
    static int[] distance(boolean[] wet){
        int[] out = new int[size * size];
        IntQueue queue = new IntQueue();
        for(int i = 0; i < out.length; i++){
            if(wet[i]) out[i] = Integer.MAX_VALUE;
            else queue.addLast(i);
        }
        while(!queue.isEmpty()){
            int i = queue.removeFirst(), x = i % size, y = i / size;
            for(Point2 d : Geometry.d4){
                int nx = x + d.x, ny = y + d.y;
                if(!in(nx, ny)) continue;
                int j = idx(nx, ny);
                if(out[j] > out[i] + 1){
                    out[j] = out[i] + 1;
                    queue.addLast(j);
                }
            }
        }
        return out;
    }

    static float edgeIn(int side, float x, float y){
        return side == 0 ? x : side == 1 ? size - 1 - x : side == 2 ? y : size - 1 - y;
    }

    static boolean nearValue(byte[] mask, int x, int y, int r, byte value){
        for(int dy = -r; dy <= r; dy++) for(int dx = -r; dx <= r; dx++) if(in(x + dx, y + dy) && mask[idx(x + dx, y + dy)] == value) return true;
        return false;
    }

    static boolean[] fill(int sx, int sy, Intf pass){
        boolean[] seen = new boolean[size * size];
        if(!in(sx, sy) || !pass.get(idx(sx, sy))) return seen;
        IntQueue queue = new IntQueue();
        queue.addLast(idx(sx, sy));
        seen[idx(sx, sy)] = true;
        while(!queue.isEmpty()){
            int i = queue.removeFirst(), x = i % size, y = i / size;
            for(Point2 d : Geometry.d4){
                int nx = x + d.x, ny = y + d.y;
                if(!in(nx, ny)) continue;
                int j = idx(nx, ny);
                if(!seen[j] && pass.get(j)){
                    seen[j] = true;
                    queue.addLast(j);
                }
            }
        }
        return seen;
    }

    interface Intf{
        boolean get(int i);
    }

    public static boolean spawnBand(int x, int y, int width, int height){
        int edge = Math.min(Math.min(x, y), Math.min(width - 1 - x, height - 1 - y));
        return edge >= 2 && edge < spawnBand;
    }

    static void spawns(IntSeq out, Intf open, int cx, int cy, int clear){
        for(int i = 0; i < size * size; i++){
            int x = i % size, y = i / size;
            if(spawnBand(x, y, size, size) && open.get(i) && Mathf.dst(x, y, cx, cy) >= spawnFromCore * k) out.add(i);
        }
    }

    //each further biome takes its share where a slow noise of its own runs high; across a band round the cut the two interfinger on a fine noise
    static void paint(Result r, boolean[] rock, byte[] water, Biome biome, Seq<Biome> all, int seed, Rand rnd){
        int n = size * size;
        r.floor = new Block[n];
        r.wall = new Block[n];

        Biome second = named(all, biome.kin[rnd.random(biome.kin.length - 1)]);
        Seq<Biome> thirds = new Seq<>();
        for(String name : biome.kin) thirds.addUnique(named(all, name));
        for(String name : second.kin) thirds.addUnique(named(all, name));
        thirds.remove(biome);
        thirds.remove(second);
        Seq<Biome> blends = Seq.with(second);
        FloatSeq amounts = FloatSeq.with(rnd.random(0.2f, 0.42f));
        if(thirds.any() && rnd.chance(0.34)){
            blends.add(thirds.random(rnd));
            amounts.add(rnd.random(0.1f, 0.2f));
        }

        boolean[] dryMask = new boolean[n];
        for(int i = 0; i < n; i++) dryMask[i] = water[i] == 0;
        int[] shore = distance(dryMask);
        byte[] sec = new byte[n];
        float band = 0.03f, patchScale = rnd.random(20f, 60f) * k;
        for(int b = 0; b < blends.size; b++){
            int bs = seed + 70 + b * 10;
            float[] v = new float[n];
            for(int y = 0; y < size; y++){
                for(int x = 0; x < size; x++){
                    float wx = (Simplex.noise2d(bs + 1, 2, 0.5, 1f / (90f * k), x, y) - 0.5f) * 30f * k;
                    float wy = (Simplex.noise2d(bs + 2, 2, 0.5, 1f / (90f * k), x, y) - 0.5f) * 30f * k;
                    v[idx(x, y)] = Simplex.noise2d(bs, 2, 0.5, 1f / (100f * k), x + wx, y + wy);
                }
            }
            float[] sorted = v.clone();
            java.util.Arrays.sort(sorted);
            float cut = sorted[Math.min(n - 1, (int)((1f - amounts.get(b)) * n))];
            for(int y = 0; y < size; y++){
                for(int x = 0; x < size; x++){
                    int i = idx(x, y);
                    float u = Mathf.clamp((v[i] - (cut - band)) / (2f * band));
                    if(Simplex.noise2d(bs + 3, 2, 0.5, 1f / 5f, x, y) < u * u * (3f - 2f * u)) sec[i] = (byte)(b + 1);
                }
            }
        }

        for(int y = 0; y < size; y++){
            for(int x = 0; x < size; x++){
                int i = idx(x, y);
                Biome b = sec[i] == 0 ? biome : blends.get(sec[i] - 1);
                float patch = Simplex.noise2d(seed + 62, 3, 0.5, 1f / patchScale, x, y);
                int f = patch < 0.55f ? 0 : patch < 0.68f || b.floors.length < 3 ? 1 : 2;
                f = Math.min(f, b.floors.length - 1);
                Block floor = b.floors[f];
                if(water[i] == 2) floor = biome.deep;
                else if(water[i] == 1) floor = biome.shallow;
                else if(!rock[i] && shore[i] <= beachWidth(seed, x, y)) floor = b.beach;
                r.floor[i] = floor;
                r.wall[i] = rock[i] ? b.walls[f] : Blocks.air;
            }
        }
        r.region = sec;
        r.biomes = blends.map(bl -> bl.name);
        r.biomes.insert(0, biome.name);
    }

    //the sand along a shore is wide in some places and missing in others
    static int beachWidth(int seed, int x, int y){
        return (int)(Simplex.noise2d(seed + 41, 2, 0.5, 1f / (20f * k), x, y) * 5f) - 1;
    }

    public static void write(Tiles tiles, Result r){
        for(int y = 0; y < size; y++){
            for(int x = 0; x < size; x++){
                int i = idx(x, y);
                tiles.set(x, y, new Tile(x, y, r.floor[i], Blocks.air, r.wall[i]));
            }
        }
    }
}
