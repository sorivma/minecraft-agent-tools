package dev.minecraftagenttools.harness.schematica;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.nio.IntBuffer;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Native hover support owned by the optional integration, without extending the Harness API. */
public final class MaterialTooltip extends GuiScreen {
    private static Map<String, Object> hovered;
    private static final IntBuffer SCISSOR = BufferUtils.createIntBuffer(4);

    @SubscribeEvent
    public void begin(GuiScreenEvent.DrawScreenEvent.Pre event) {
        hovered = null;
    }

    static void offer(Map<String, Object> material, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        if (screen == null) return;
        int mouseX = Mouse.getX(), mouseY = Mouse.getY();
        int logicalX = mouseX * screen.width / mc.displayWidth;
        int logicalY = screen.height - mouseY * screen.height / mc.displayHeight - 1;
        if (logicalX < x || logicalX >= x + width || logicalY < y || logicalY >= y + height) return;
        // The custom drawing callback runs inside the host's effective chat/card scissor.
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            SCISSOR.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR);
            if (mouseX < SCISSOR.get(0)
                    || mouseX >= SCISSOR.get(0) + SCISSOR.get(2)
                    || mouseY < SCISSOR.get(1)
                    || mouseY >= SCISSOR.get(1) + SCISSOR.get(3)) return;
        }
        hovered = material;
    }

    @SubscribeEvent
    public void end(GuiScreenEvent.DrawScreenEvent.Post event) {
        Map<String, Object> material = hovered;
        hovered = null;
        if (material == null) return;
        mc = Minecraft.getMinecraft();
        width = event.gui.width;
        height = event.gui.height;
        String block = (String) material.get("block");
        int meta =
                ((Number) material.get(material.containsKey("machineId") ? "machineId" : "meta"))
                        .intValue();
        Object item = Item.itemRegistry.getObject(block);
        String name;
        try {
            if (item instanceof Item) name = new ItemStack((Item) item, 1, meta).getDisplayName();
            else {
                Object registered = Block.blockRegistry.getObject(block);
                name = registered instanceof Block ? ((Block) registered).getLocalizedName() : "?";
            }
        } catch (RuntimeException e) {
            name = "?";
        }
        // Post runs after the host has restored the chat clip, so the tooltip can extend past a
        // card.
        drawHoveringText(
                Collections.singletonList(name), event.mouseX, event.mouseY, mc.fontRenderer);
    }
}
