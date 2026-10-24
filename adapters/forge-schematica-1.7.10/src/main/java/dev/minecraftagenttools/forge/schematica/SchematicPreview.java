package dev.minecraftagenttools.forge.schematica;

import dev.minecraftagenttools.schematica.contract.Layout;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Textured, orthographic model of the detached schematic world, independent of ghost placement. */
final class SchematicPreview {
    private final World world;
    private final Layout layout;
    private int lists;
    private static Object resources;
    private static int resourceGeneration;
    private int compiledGeneration;
    private final java.util.List<TileEntity> tiles = new java.util.ArrayList<TileEntity>();

    SchematicPreview(World world, Layout layout) {
        this.world = world;
        this.layout = layout;
        for (int y = 0; y < layout.height; y++)
            for (int z = 0; z < layout.length; z++)
                for (int x = 0; x < layout.width; x++) {
                    TileEntity tile = world.getTileEntity(x, y, z);
                    if (tile != null) tiles.add(tile);
                }
    }

    void render(int left, int top, int width, int height) throws Exception {
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            Object currentResources = Minecraft.getMinecraft().getResourceManager();
            if (currentResources != resources
                    && currentResources instanceof IReloadableResourceManager) {
                resources = currentResources;
                ((IReloadableResourceManager) resources)
                        .registerReloadListener(manager -> resourceGeneration++);
            }
            if (lists != 0 && compiledGeneration != resourceGeneration) close();
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            // Fit all eight corners, with space around the model for texture/model overhangs.
            double projectedWidth = (layout.width + layout.length) / Math.sqrt(2);
            double projectedHeight =
                    (layout.width + layout.length) / Math.sqrt(6)
                            + layout.height * Math.sqrt(2.0 / 3);
            double scale = Math.min((width - 8) / projectedWidth, (height - 8) / projectedHeight);
            GL11.glTranslated(left + width / 2.0, top + height / 2.0, 100);
            GL11.glScaled(scale, -scale, scale);
            GL11.glRotated(35.26438968, 1, 0, 0);
            GL11.glRotated(-45, 0, 1, 0);
            GL11.glTranslated(-layout.width / 2.0, -layout.height / 2.0, -layout.length / 2.0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1, 1, 1, 1);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(TextureMap.locationBlocksTexture);
            if (lists == 0) compile();
            for (int pass = 0; pass < 2; pass++) {
                GL11.glDepthMask(pass == 0);
                if (pass == 1) {
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                } else GL11.glDisable(GL11.GL_BLEND);
                Minecraft.getMinecraft()
                        .getTextureManager()
                        .bindTexture(TextureMap.locationBlocksTexture);
                GL11.glCallList(lists + pass);
                int[] old = ChunkRenderPass.enter(ForgeHooksClient.class, pass);
                try {
                    for (TileEntity tile : tiles) {
                        if (tile.shouldRenderInPass(pass)) {
                            RenderHelper.enableStandardItemLighting();
                            TileEntityRendererDispatcher.instance.renderTileEntityAt(
                                    tile, tile.xCoord, tile.yCoord, tile.zCoord, 0);
                            RenderHelper.disableStandardItemLighting();
                        }
                    }
                } finally {
                    ChunkRenderPass.exit(ForgeHooksClient.class, old);
                }
            }
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            OpenGlHelper.setActiveTexture(activeTexture);
        }
    }

    private void compile() throws Exception {
        int next = GL11.glGenLists(2);
        if (next == 0) throw new IllegalStateException("Cannot allocate schematic preview");
        try {
            RenderBlocks renderer = new RenderBlocks(world);
            java.util.List<Integer> order = new java.util.ArrayList<Integer>();
            int[] cells = layout.cells();
            for (int i = 0; i < cells.length; i++) if (cells[i] >= 0) order.add(i);
            // The fixed isometric camera looks along (+x,+y,+z): alpha blocks draw far to near.
            order.sort(java.util.Comparator.comparingInt(this::depth));
            for (int pass = 0; pass < 2; pass++) {
                int[] old = ChunkRenderPass.enter(ForgeHooksClient.class, pass);
                GL11.glNewList(next + pass, GL11.GL_COMPILE);
                Tessellator tess = Tessellator.instance;
                tess.startDrawingQuads();
                try {
                    tess.setBrightness(15728880);
                    for (int cell : order) {
                        int x = cell % layout.width;
                        int y = cell / (layout.width * layout.length);
                        int z = (cell / layout.width) % layout.length;
                        Block block = world.getBlock(x, y, z);
                        if (!block.isAir(world, x, y, z) && block.canRenderInPass(pass))
                            renderer.renderBlockByRenderType(block, x, y, z);
                    }
                } finally {
                    try {
                        tess.draw();
                    } finally {
                        GL11.glEndList();
                        ChunkRenderPass.exit(ForgeHooksClient.class, old);
                    }
                }
            }
            lists = next;
            compiledGeneration = resourceGeneration;
        } finally {
            if (lists != next) GL11.glDeleteLists(next, 2);
        }
    }

    private int depth(int cell) {
        return cell % layout.width
                + cell / (layout.width * layout.length)
                + (cell / layout.width) % layout.length;
    }

    void close() {
        if (lists != 0) GL11.glDeleteLists(lists, 2);
        lists = 0;
    }
}
