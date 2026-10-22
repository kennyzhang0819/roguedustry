package roguedustry;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.noise.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.maps.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class MapGen{
    public static final int width = 170, height = 120;
    static final int border = 3;

    final Rand rand;
    final int seed;
    final Block[] floors = new Block[width * height], walls = new Block[width * height], ores = new Block[width * height];

    public int coreX, coreY, groundX, groundY, navalX, navalY;

    MapGen(int seed){
        this.seed = seed;
        rand = new Rand(seed);
    }

    public static Map create(){
        return create((int)(Math.random() * Integer.MAX_VALUE));
    }

    public static Map create(int seed){
        StringMap tags = StringMap.of("name", "Random map " + seed, "author", "Roguedustry");
        Map map = new Map(tags);
        world.addMapLoader(map, () -> world.loadGenerator(width, height, tiles -> new MapGen(seed).generate(tiles)));
        return map;
    }

    void generate(Tiles tiles){
        terrain();
        layout();
        carveRiver();
        carveLand();
        clearCore();
        placeOres();

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                int i = x + y * width;
                tiles.set(x, y, new Tile(x, y, floors[i], ores[i] == null ? Blocks.air : ores[i], walls[i] == null ? Blocks.air : walls[i]));
            }
        }

        tiles.getn(groundX, groundY).setOverlay(Blocks.spawn);
        tiles.getn(navalX, navalY).setOverlay(Blocks.spawn);
        tiles.getn(coreX, coreY).setBlock(Blocks.coreShard, Team.sharded, 0);
    }

    void terrain(){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                int i = x + y * width;
                float n = Simplex.noise2d(seed, 3, 0.5, 1f / 40f, x, y);
                floors[i] = n < 0.4f ? Blocks.stone : n < 0.5f ? Blocks.dirt : Blocks.grass;

                float rock = Simplex.noise2d(seed + 1, 4, 0.55, 1f / 18f, x, y);
                boolean edge = x < border || y < border || x >= width - border || y >= height - border;
                if(edge || rock > 0.6f){
                    walls[i] = floors[i] == Blocks.stone || floors[i] == Blocks.dirt ? Blocks.stoneWall : Blocks.pine;
                }
            }
        }
    }

    void layout(){
        coreX = 16;
        coreY = height / 2 + rand.range(height / 6);
        boolean groundTop = rand.chance(0.5);
        groundX = navalX = width - 12;
        groundY = groundTop ? rand.random(height * 2 / 3, height - 16) : rand.random(16, height / 3);
        navalY = groundTop ? rand.random(16, height / 3) : rand.random(height * 2 / 3, height - 16);
    }

    Seq<Vec2> route(int x1, int y1, int x2, int y2, int bends){
        Seq<Vec2> points = new Seq<>();
        points.add(new Vec2(x1, y1));
        for(int i = 1; i <= bends; i++){
            float t = i / (bends + 1f);
            float x = Mathf.lerp(x1, x2, t) + rand.range(8f);
            float y = Mathf.clamp(Mathf.lerp(y1, y2, t) + rand.range(height / 4f), 12f, height - 12f);
            points.add(new Vec2(x, y));
        }
        points.add(new Vec2(x2, y2));
        return points;
    }

    void walk(Seq<Vec2> points, Cons2i brush){
        for(int p = 0; p < points.size - 1; p++){
            Vec2 a = points.get(p), b = points.get(p + 1);
            int steps = (int)(a.dst(b) * 2) + 1;
            for(int s = 0; s <= steps; s++){
                float t = s / (float)steps;
                brush.get(Math.round(Mathf.lerp(a.x, b.x, t)), Math.round(Mathf.lerp(a.y, b.y, t)));
            }
        }
    }

    interface Cons2i{
        void get(int x, int y);
    }

    void carveRiver(){
        int endY = navalY > coreY ? coreY + 5 : coreY - 5;
        Seq<Vec2> points = route(navalX, navalY, coreX + 2, endY, 3);
        walk(points, (cx, cy) -> {
            int r = 5;
            for(int x = cx - r; x <= cx + r; x++){
                for(int y = cy - r; y <= cy + r; y++){
                    if(!inner(x, y)) continue;
                    float d = Mathf.dst(x, y, cx, cy);
                    int i = x + y * width;
                    if(d <= 2.5f){
                        floors[i] = Blocks.deepwater;
                        walls[i] = null;
                    }else if(d <= 4.5f && floors[i] != Blocks.deepwater){
                        floors[i] = Blocks.water;
                        walls[i] = null;
                    }
                }
            }
        });
    }

    void carveLand(){
        Seq<Vec2> points = route(groundX, groundY, coreX + 4, coreY, 3);
        walk(points, (cx, cy) -> {
            int r = 3;
            for(int x = cx - r; x <= cx + r; x++){
                for(int y = cy - r; y <= cy + r; y++){
                    if(!inner(x, y) || Mathf.dst(x, y, cx, cy) > r + 0.5f) continue;
                    int i = x + y * width;
                    walls[i] = null;
                    //deep water drowns ground units, so the path fords the river through shallow water
                    if(floors[i] == Blocks.deepwater) floors[i] = Blocks.water;
                }
            }
        });
    }

    void clearCore(){
        int r = 7;
        for(int x = coreX - r; x <= coreX + r; x++){
            for(int y = coreY - r; y <= coreY + r; y++){
                if(!inner(x, y)) continue;
                int i = x + y * width;
                walls[i] = null;
                if(Math.abs(x - coreX) <= 2 && Math.abs(y - coreY) <= 2) floors[i] = Blocks.stone;
            }
        }
    }

    void placeOres(){
        for(int n = 0; n < 3; n++){
            float angle = rand.random(360f), dist = rand.random(7f, 14f);
            orePatch(coreX + (int)Angles.trnsx(angle, dist), coreY + (int)Angles.trnsy(angle, dist), rand.random(2.5f, 3.5f));
        }
        for(int n = 0; n < 22; n++){
            orePatch(rand.random(border, width - border), rand.random(border, height - border), rand.random(2f, 4.5f));
        }
    }

    void orePatch(int cx, int cy, float radius){
        int r = (int)radius + 1;
        for(int x = cx - r; x <= cx + r; x++){
            for(int y = cy - r; y <= cy + r; y++){
                if(!inner(x, y)) continue;
                int i = x + y * width;
                if(walls[i] != null || floors[i] == Blocks.water || floors[i] == Blocks.deepwater) continue;
                if(Math.abs(x - coreX) <= 1 && Math.abs(y - coreY) <= 1) continue;
                float wobble = Simplex.noise2d(seed + 2, 2, 0.5, 1f / 3f, x, y) * 1.5f;
                if(Mathf.dst(x, y, cx, cy) + wobble <= radius + 0.75f) ores[i] = Blocks.oreCopper;
            }
        }
    }

    boolean inner(int x, int y){
        return x >= border && y >= border && x < width - border && y < height - border;
    }
}
