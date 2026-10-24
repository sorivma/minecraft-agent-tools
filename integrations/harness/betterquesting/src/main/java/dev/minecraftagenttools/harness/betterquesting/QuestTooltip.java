package dev.minecraftagenttools.harness.betterquesting;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.nio.IntBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Native hover details, offered only from inside the card's actual clipped drawing region. */
public final class QuestTooltip extends GuiScreen {
    private static final IntBuffer SCISSOR = BufferUtils.createIntBuffer(4);
    private static String hovered;

    @SubscribeEvent
    public void begin(GuiScreenEvent.DrawScreenEvent.Pre event) {
        hovered = null;
    }

    static void offer(String text, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen == null) return;
        GuiScreen screen = mc.currentScreen;
        int mouseX = Mouse.getX(), mouseY = Mouse.getY();
        int logicalX = mouseX * screen.width / mc.displayWidth;
        int logicalY = screen.height - mouseY * screen.height / mc.displayHeight - 1;
        if (logicalX < x || logicalX >= x + width || logicalY < y || logicalY >= y + height) return;
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            SCISSOR.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR);
            if (mouseX < SCISSOR.get(0)
                    || mouseX >= SCISSOR.get(0) + SCISSOR.get(2)
                    || mouseY < SCISSOR.get(1)
                    || mouseY >= SCISSOR.get(1) + SCISSOR.get(3)) return;
        }
        hovered = text;
    }

    @SubscribeEvent
    public void end(GuiScreenEvent.DrawScreenEvent.Post event) {
        String text = hovered;
        hovered = null;
        if (text == null) return;
        mc = Minecraft.getMinecraft();
        width = event.gui.width;
        height = event.gui.height;
        List<String> lines = new ArrayList<String>();
        int maxWidth = Math.max(80, Math.min(360, width - 32));
        int maxLines = Math.max(2, Math.min(20, (height - 30) / (mc.fontRenderer.FONT_HEIGHT + 1)));
        for (String paragraph : text.split("\n"))
            lines.addAll(mc.fontRenderer.listFormattedStringToWidth(paragraph, maxWidth));
        if (lines.size() > maxLines) {
            lines = new ArrayList<String>(lines.subList(0, maxLines - 1));
            lines.add("... full details in quest book");
        }
        drawHoveringText(lines, event.mouseX, event.mouseY, mc.fontRenderer);
    }
}
