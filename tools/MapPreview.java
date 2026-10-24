package roguedustry;

import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.world.*;

import javax.imageio.*;
import java.awt.image.*;
import java.io.*;

public class MapPreview{

    //rough in-game colours, since the game's own map colours come from its textures and are not loaded here
    static final java.util.Map<String, Integer> colors = java.util.Map.ofEntries(
        java.util.Map.entry("grass", 0x5f9a4f), java.util.Map.entry("pine", 0x2f5a35), java.util.Map.entry("moss", 0x4f6e3f), java.util.Map.entry("spore-pine", 0x5a3f6a),
        java.util.Map.entry("spore-moss", 0x6e4f86), java.util.Map.entry("spore-wall", 0x6a4a80), java.util.Map.entry("stone", 0x7a7a80), java.util.Map.entry("stone-wall", 0x8a8a90),
        java.util.Map.entry("sand-floor", 0xd8c08a), java.util.Map.entry("sand-wall", 0xc8a870), java.util.Map.entry("darksand", 0x6a5a4a), java.util.Map.entry("dune-wall", 0x7a6a5a),
        java.util.Map.entry("salt", 0xe0d8d0), java.util.Map.entry("salt-wall", 0xd0c8c0), java.util.Map.entry("dirt", 0x7a5a3f), java.util.Map.entry("dirt-wall", 0x8a6a4f),
        java.util.Map.entry("snow", 0xeef2f8), java.util.Map.entry("snow-wall", 0xd8e0ea), java.util.Map.entry("ice-snow", 0xc8d8ea), java.util.Map.entry("ice", 0xa0c0e0), java.util.Map.entry("ice-wall", 0x90b0d8),
        java.util.Map.entry("basalt", 0x4a4a50), java.util.Map.entry("char", 0x3a3a3a), java.util.Map.entry("hotrock", 0x8a4a3a), java.util.Map.entry("magmarock", 0xa04a2a),
        java.util.Map.entry("dacite", 0x9a9a9a), java.util.Map.entry("dacite-wall", 0xaaaaaa), java.util.Map.entry("shale", 0x7a5a6a), java.util.Map.entry("shale-wall", 0x8a6a7a),
        java.util.Map.entry("deep-water", 0x2f4a90), java.util.Map.entry("shallow-water", 0x4a6ab0), java.util.Map.entry("sand-water", 0x7a9ac0), java.util.Map.entry("darksand-water", 0x5a6a90),
        java.util.Map.entry("tainted-water", 0x5a3f80), java.util.Map.entry("darksand-tainted-water", 0x6a5a8a)
    );

    static int look(Block block){
        return colors.getOrDefault(block.name, 0xff00ff);
    }

    public static void main(String[] args) throws Exception{
        Vars.headless = true;
        Vars.content = new ContentLoader();
        Vars.content.createBaseContent();

        File dir = new File(args.length > 0 ? args[0] : "build/maps");
        dir.mkdirs();
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 8, start = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        for(int s = start; s < start + count; s++){
            long time = System.nanoTime();
            MapGen.Result r = MapGen.generate(s * 7919);
            int n = MapGen.size;
            BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
            for(int y = 0; y < n; y++){
                for(int x = 0; x < n; x++){
                    int i = y * n + x;
                    Block f = r.floor[i];
                    int c = r.wall[i] != Blocks.air ? (look(r.wall[i]) >> 1) & 0x7f7f7f : look(f);
                    img.setRGB(x, n - 1 - y, c);
                }
            }
            for(int dy = -4; dy <= 4; dy++) for(int dx = -4; dx <= 4; dx++) img.setRGB(r.coreX + dx, n - 1 - (r.coreY + dy), 0xffd37f);
            File out = new File(dir, "map-" + s + ".png");
            ImageIO.write(img, "png", out);
            System.out.printf("%s  %-28s %-40s tries %d  ground spawns %d  water spawns %d  %d ms%n", out.getName(), r.name + (r.sea ? " (sea)" : ""), r.biomes, r.tries, r.ground.size, r.water.size, (System.nanoTime() - time) / 1000000);
        }
    }
}
