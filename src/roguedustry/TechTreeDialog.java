package roguedustry;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.game.Objectives.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.ui.layout.*;
import mindustry.ui.layout.TreeLayout.*;

import java.util.*;

import static mindustry.Vars.*;

public class TechTreeDialog extends BaseDialog{
    static final String infoKey = "roguedustry-tech-info";

    final float nodeSize = Scl.scl(60f);
    final Seq<Node> nodes = new Seq<>();
    final Node root;
    final View view;
    Rect bounds = new Rect();

    public TechTreeDialog(){
        super("Tech tree");
        shouldPause = true;

        root = new Node(Planets.serpulo.techTree, null);
        margin(0f).marginBottom(8f);
        background(Styles.black9);
        cont.add(view = new View()).grow();
        addCloseButton();

        shown(() -> {
            layoutTree();
            view.hoverNode = null;
            view.infoTable.remove();
            if(!Core.settings.getBool(infoKey, false)){
                Core.settings.put(infoKey, true);
                ui.showInfo("In roguedustry, tech tree nodes are immediately unlocked upon entering a new level.");
            }
        });

        addListener(new InputListener(){
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
                view.setScale(Mathf.clamp(view.scaleX - amountY / 10f * view.scaleX, 0.25f, 1f));
                view.setOrigin(Align.center);
                view.setTransform(true);
                return true;
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y){
                view.requestScroll();
                return super.mouseMoved(event, x, y);
            }
        });

        touchable = Touchable.enabled;

        addCaptureListener(new ElementGestureListener(){
            @Override
            public void zoom(InputEvent event, float initialDistance, float distance){
                if(view.lastZoom < 0) view.lastZoom = view.scaleX;
                view.setScale(Mathf.clamp(distance / initialDistance * view.lastZoom, 0.25f, 1f));
                view.setOrigin(Align.center);
                view.setTransform(true);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                view.lastZoom = view.scaleX;
            }

            @Override
            public void pan(InputEvent event, float x, float y, float deltaX, float deltaY){
                view.panX += deltaX / view.scaleX;
                view.panY += deltaY / view.scaleY;
                view.moved = true;
            }
        });
    }

    static RogueRun run(){
        return RogueRun.current;
    }

    static boolean unlocked(TechNode node){
        return run() != null && run().unlocked.contains(node.content);
    }

    static boolean selectable(TechNode node){
        return unlocked(node) || run() != null && Tech.missing(run(), node).isEmpty();
    }

    boolean showNode(Node node){
        return node.parent == null || unlocked(node.parent.tech) && showNode(node.parent);
    }

    void layoutTree(){
        float spacing = 20f;
        Node[] all = root.children;
        Node[] left = Arrays.copyOfRange(all, 0, Mathf.ceil(all.length / 2f)), right = Arrays.copyOfRange(all, Mathf.ceil(all.length / 2f), all.length);

        root.children = left;
        new BranchTreeLayout(){{
            gapBetweenLevels = gapBetweenNodes = spacing;
            rootLocation = TreeLocation.top;
        }}.layout(root);
        float lastY = root.y;

        if(right.length > 0){
            root.children = right;
            new BranchTreeLayout(){{
                gapBetweenLevels = gapBetweenNodes = spacing;
                rootLocation = TreeLocation.bottom;
            }}.layout(root);
            shift(left, root.y - lastY);
        }
        root.children = all;

        float minx = 0f, miny = 0f, maxx = 0f, maxy = 0f;
        for(Node n : nodes){
            if(!showNode(n)) continue;
            minx = Math.min(n.x - n.width / 2f, minx);
            maxx = Math.max(n.x + n.width / 2f, maxx);
            miny = Math.min(n.y - n.height / 2f, miny);
            maxy = Math.max(n.y + n.height / 2f, maxy);
        }
        bounds = new Rect(minx, miny, maxx - minx, maxy - miny);
        bounds.y += nodeSize * 1.5f;
    }

    void shift(Node[] children, float amount){
        for(Node node : children){
            node.y += amount;
            if(node.children != null && node.children.length > 0) shift(node.children, amount);
        }
    }

    class Node extends TreeNode<Node>{
        final TechNode tech;

        Node(TechNode tech, Node parent){
            this.tech = tech;
            this.parent = parent;
            width = height = nodeSize;
            nodes.add(this);
            Seq<TechNode> kept = tech.children.select(c -> !Tech.removed(c));
            children = new Node[kept.size];
            for(int i = 0; i < children.length; i++){
                children[i] = new Node(kept.get(i), this);
            }
        }
    }

    class View extends Group{
        float panX = 0f, panY = -200f, lastZoom = -1f;
        boolean moved;
        @Nullable ImageButton hoverNode;
        final Table infoTable = new Table();

        View(){
            for(Node node : nodes){
                ImageButton button = new ImageButton(node.tech.content.uiIcon, Styles.nodei);
                button.resizeImage(32f);
                button.getImage().setScaling(Scaling.fit);
                button.visible(() -> showNode(node));
                button.touchable(() -> showNode(node) ? Touchable.enabled : Touchable.disabled);
                button.userObject = node.tech;
                button.setSize(nodeSize);

                button.clicked(() -> {
                    if(moved) return;
                    hoverNode = button;
                    rebuild();
                });
                button.hovered(() -> {
                    if(!mobile && hoverNode != button){
                        hoverNode = button;
                        rebuild();
                    }
                });
                button.exited(() -> {
                    if(!mobile && hoverNode == button && !infoTable.hasMouse() && !button.hasMouse()){
                        hoverNode = null;
                        rebuild();
                    }
                });
                button.update(() -> {
                    float offset = (Core.graphics.getHeight() % 2) / 2f;
                    button.setPosition(node.x + panX + width / 2f, node.y + panY + height / 2f + offset, Align.center);
                    boolean open = unlocked(node.tech), selectable = selectable(node.tech);
                    button.getStyle().up = open ? Tex.buttonOver : selectable ? Tex.button : Tex.buttonRed;
                    ((TextureRegionDrawable)button.getStyle().imageUp).setRegion(selectable ? node.tech.content.uiIcon : Icon.lock.getRegion());
                    button.getImage().setColor(open ? Color.white : selectable ? Color.gray : Pal.gray);
                    button.getImage().layout();
                });
                addChild(button);
            }

            setOrigin(Align.center);
            setTransform(true);
            released(() -> moved = false);
        }

        void clamp(){
            float pad = nodeSize, ox = width / 2f, oy = height / 2f;
            float rx = Mathf.clamp(bounds.x + panX + ox, -bounds.width + pad, Core.graphics.getWidth() - pad);
            float ry = Mathf.clamp(panY + oy + bounds.y, -bounds.height + pad, Core.graphics.getHeight() - pad);
            panX = rx - bounds.x - ox;
            panY = ry - bounds.y - oy;
        }

        void rebuild(){
            infoTable.remove();
            infoTable.clear();
            infoTable.update(null);
            if(hoverNode == null) return;

            ImageButton button = hoverNode;
            TechNode node = (TechNode)button.userObject;
            RogueRun run = run();

            infoTable.update(() -> infoTable.setPosition(button.x + button.getWidth(), button.y + button.getHeight(), Align.topLeft));
            infoTable.left().background(Tex.button).margin(8f);
            boolean selectable = selectable(node);
            infoTable.table(b -> {
                b.left().defaults().left();
                if(selectable) b.button(Icon.info, Styles.flati, () -> ui.content.show(node.content)).growY().width(50f);
                b.table(desc -> {
                    desc.left().defaults().left();
                    desc.add(selectable ? node.content.localizedName : "[accent]???").row();
                    if(node.content instanceof SectorPreset p && run != null && run.cleared.contains(p)){
                        desc.add("Cleared").color(Pal.accent);
                    }else if(unlocked(node)){
                        desc.add(node.content instanceof SectorPreset ? "Ready to play" : "Unlocked").color(Pal.accent);
                    }else if(run != null){
                        desc.add("Unlocks when you:").color(Color.lightGray).row();
                        for(Objective o : Tech.missing(run, node)){
                            desc.table(r -> {
                                r.left();
                                r.add("> " + Tech.describe(run, o)).color(Color.lightGray);
                                r.image(Icon.cancel, Color.scarlet).padLeft(3f);
                            }).row();
                        }
                    }
                }).pad(9f).left().growX();
            }).growX().left();

            infoTable.row();
            if(selectable && node.content.description != null && node.content.inlineDescription){
                infoTable.table(t -> t.margin(3f).left().labelWrap(node.content.displayDescription()).color(Color.lightGray)
                    .maxWidth(node.content.displayDescription().length() > 20 ? 270f : 0f).growX()).fillX();
            }

            addChild(infoTable);
            infoTable.pack();
        }

        @Override
        public void drawChildren(){
            clamp();
            float offsetX = panX + width / 2f, offsetY = panY + height / 2f;
            Draw.sort(true);

            for(Node node : nodes){
                if(!showNode(node)) continue;
                for(Node child : node.children){
                    if(!showNode(child)) continue;
                    boolean lock = !unlocked(node.tech) || !unlocked(child.tech);
                    Draw.z(lock ? 1f : 2f);
                    Lines.stroke(Scl.scl(4f), lock ? Pal.gray : Pal.accent);
                    Draw.alpha(parentAlpha);
                    if(Mathf.equal(Math.abs(node.y - child.y), Math.abs(node.x - child.x), 1f) && Mathf.dstm(node.x, node.y, child.x, child.y) <= node.width * 3){
                        Lines.line(node.x + offsetX, node.y + offsetY, child.x + offsetX, child.y + offsetY);
                    }else{
                        Lines.line(node.x + offsetX, node.y + offsetY, child.x + offsetX, node.y + offsetY);
                        Lines.line(child.x + offsetX, node.y + offsetY, child.x + offsetX, child.y + offsetY);
                    }
                }
            }

            Draw.sort(false);
            Draw.reset();
            super.drawChildren();
        }
    }
}
