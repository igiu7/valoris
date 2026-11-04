package com.valoris.client;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.NativeImage;
import com.valoris.net.TreatySavePayload;
import com.valoris.net.TreatySignPayload;
import com.valoris.registry.ModBlocks;
import com.valoris.treaty.TreatyData;
import com.valoris.valoris2;

import net.minecraft.util.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A treaty on old paper. Treaty and Quill: type the title and the text (click a field, Tab switches), Save makes it
 * a Treaty. Treaty: click an empty signature place and sign by hand with the mouse (Sign / Clear / Cancel); each party
 * signs once. Signed by both, the page is exported as a PNG to the screenshots folder or copied to the clipboard as
 * an image (TreatyExport).
 */
public class TreatyScreen extends Screen {
    private static final ResourceLocation PAPER = ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "textures/gui/treaty_paper.png");
    static final int PAGE_W = 232, PAGE_H = 216, BOX_W = 96, BOX_H = 40, MAX_LINES = 12;
    /** Lines a new text may have (one fewer than drawn, which leaves room for "Written by" above the signatures). */
    private static final int EDIT_LINES = 11;
    private static final int INK = 0xFF2B1D0E, FADED = 0xFF7A5C3A, SIGN_INK = 0xFF1B2A55;

    private final InteractionHand hand;
    private String title = "", content = "";
    private int focus = 1; // 0 title, 1 text
    private int drawingSlot = -1;
    private final List<int[]> points = new ArrayList<>(); // {x, y}, {-1, -1} ends a stroke
    private boolean penDown;
    private String capture; // "file" or "clipboard": the next frame is drawn bare and saved
    private int left, top;
    private Button save, sign, clear, cancel, export, copy;

    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new TreatyScreen(hand));
    }

    public TreatyScreen(InteractionHand hand) {
        super(Component.literal("Treaty"));
        this.hand = hand;
    }

    private ItemStack stack() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    private boolean draft() {
        return stack().is(ModBlocks.TREATY_AND_QUILL.get());
    }

    private TreatyData data() {
        return stack().get(TreatyData.COMPONENT.get());
    }

    @Override
    protected void init() {
        left = (width - PAGE_W) / 2;
        top = Math.max(2, (height - PAGE_H - 24) / 2);
        int y = top + PAGE_H + 4;
        int cx = width / 2;
        save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(cx - 50, y, 100, 20).build());
        sign = addRenderableWidget(Button.builder(Component.literal("Sign"), b -> commitSignature()).bounds(cx - 106, y, 70, 20).build());
        clear = addRenderableWidget(Button.builder(Component.literal("Clear"), b -> points.clear()).bounds(cx - 34, y, 68, 20).build());
        cancel = addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> stopDrawing()).bounds(cx + 36, y, 70, 20).build());
        export = addRenderableWidget(Button.builder(Component.literal("Export PNG"), b -> capture = "file").bounds(cx - 104, y, 100, 20).build());
        copy = addRenderableWidget(Button.builder(Component.literal("Copy image"), b -> capture = "clipboard").bounds(cx + 4, y, 100, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        boolean draft = draft();
        TreatyData data = data();
        save.visible = draft;
        save.active = !title.isBlank() && !content.isBlank();
        boolean drawing = drawingSlot >= 0;
        sign.visible = clear.visible = cancel.visible = !draft && drawing;
        sign.active = points.stream().anyMatch(p -> p[0] >= 0);
        boolean done = !draft && data != null && data.fullySigned();
        export.visible = copy.visible = done && !drawing;
    }

    @Override
    public void tick() {
        ItemStack stack = stack();
        if (!stack.is(ModBlocks.TREATY_AND_QUILL.get()) && !stack.is(ModBlocks.TREATY.get())) onClose(); // put away
        updateButtons();
    }

    // ------------------------------------------------------------------ layout

    private int boxX(int slot) {
        return left + (slot == 0 ? 16 : PAGE_W - 16 - BOX_W);
    }

    private int boxY() {
        return top + 156;
    }

    private boolean inBox(int slot, double mx, double my) {
        return mx >= boxX(slot) && mx < boxX(slot) + BOX_W && my >= boxY() && my < boxY() + BOX_H;
    }

    private boolean inTitle(double mx, double my) {
        return mx >= left + 10 && mx < left + PAGE_W - 10 && my >= top + 8 && my < top + 24;
    }

    private boolean inText(double mx, double my) {
        return mx >= left + 10 && mx < left + PAGE_W - 10 && my >= top + 30 && my < top + 30 + MAX_LINES * 9 + 4;
    }

    private List<FormattedCharSequence> lines(String text) {
        return font.split(FormattedText.of(text), PAGE_W - 28);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean bare = capture != null;
        if (!bare) super.render(g, mouseX, mouseY, partialTick); // background and buttons
        drawPage(g, bare);
        if (bare) {
            String mode = capture;
            capture = null;
            g.flush();
            NativeImage page = crop(Screenshot.takeScreenshot(minecraft.getMainRenderTarget()));
            TreatyExport.export(page, data() == null ? "treaty" : data().title(), mode.equals("clipboard"));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    private NativeImage crop(NativeImage full) {
        double scale = minecraft.getWindow().getGuiScale();
        int x = (int) Math.round(left * scale), y = (int) Math.round(top * scale);
        int w = Math.min((int) Math.round(PAGE_W * scale), full.getWidth() - x);
        int h = Math.min((int) Math.round(PAGE_H * scale), full.getHeight() - y);
        NativeImage out = new NativeImage(w, h, false);
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) out.setPixelRGBA(i, j, full.getPixelRGBA(x + i, y + j));
        full.close();
        return out;
    }

    private void drawPage(GuiGraphics g, boolean bare) {
        g.blit(PAPER, left, top, 0, 0, PAGE_W, PAGE_H, 256, 256);
        boolean draft = draft();
        TreatyData data = data();
        String t = draft ? title : data == null ? "" : data.title();
        String c = draft ? content : data == null ? "" : data.content();
        boolean caret = draft && !bare && (System.currentTimeMillis() / 500) % 2 == 0;

        // title
        Component titleText = Component.literal(t).withStyle(s -> s.withBold(true));
        if (t.isEmpty() && draft && !bare) {
            g.drawString(font, "Title", width / 2 - font.width("Title") / 2, top + 12, FADED, false);
        } else {
            g.drawString(font, titleText, width / 2 - font.width(titleText) / 2, top + 12, INK, false);
        }
        if (caret && focus == 0) g.fill(width / 2 + font.width(titleText) / 2 + 1, top + 11, width / 2 + font.width(titleText) / 2 + 2, top + 21, INK);
        g.fill(left + 30, top + 25, left + PAGE_W - 30, top + 26, 0x80553A1E);

        // text
        List<FormattedCharSequence> lines = lines(c);
        int y = top + 32;
        if (c.isEmpty() && draft && !bare) g.drawString(font, "The terms of the treaty...", left + 14, y, FADED, false);
        for (int i = 0; i < Math.min(lines.size(), MAX_LINES); i++) g.drawString(font, lines.get(i), left + 14, y + i * 9, INK, false);
        if (caret && focus == 1) {
            int last = Math.max(0, lines.size() - 1);
            int cx = left + 14 + (lines.isEmpty() || c.endsWith("\n") ? 0 : font.width(lines.get(last)));
            int cy = y + (c.endsWith("\n") ? lines.size() : last) * 9;
            g.fill(cx, cy - 1, cx + 1, cy + 9, INK);
        }
        if (!draft && data != null && !data.author().isEmpty()) {
            String by = "Written by " + data.author(); // right under the text, well clear of the signatures
            int byY = y + Math.min(lines.size(), MAX_LINES) * 9 + 2;
            g.drawString(font, by, left + PAGE_W - 14 - font.width(by), byY, FADED, false);
        }

        // signatures
        for (int slot = 0; slot < 2; slot++) {
            int bx = boxX(slot), by = boxY();
            String label = slot == 0 ? "First party" : "Second party";
            g.drawString(font, label, bx + BOX_W / 2 - font.width(label) / 2, by - 10, FADED, false);
            g.fill(bx + 4, by + BOX_H + 2, bx + BOX_W - 4, by + BOX_H + 3, 0xA0553A1E); // the line, under the signature with a margin
            var signature = data == null ? java.util.Optional.<TreatyData.Signature>empty() : data.signature(slot);
            if (signature.isPresent()) {
                drawStrokes(g, bx, by, decode(signature.get().strokes()));
                String name = signature.get().name();
                g.drawString(font, Component.literal(name).withStyle(s -> s.withItalic(true)),
                        bx + BOX_W / 2 - font.width(name) / 2, by + BOX_H + 5, FADED, false);
            } else if (drawingSlot == slot) {
                if (!bare) g.fill(bx, by, bx + BOX_W, by + BOX_H, 0x18000000);
                drawStrokes(g, bx, by, points);
            } else if (!bare) {
                String hint = draft ? "after saving" : "click to sign";
                g.drawString(font, hint, bx + BOX_W / 2 - font.width(hint) / 2, by + 14, 0xFFAA9070, false);
            }
        }
    }

    /** Strokes on a 2x finer canvas: half a GUI pixel per point. */
    private void drawStrokes(GuiGraphics g, int bx, int by, List<int[]> pts) {
        g.pose().pushPose();
        g.pose().translate(bx, by, 0);
        g.pose().scale(0.5F, 0.5F, 1.0F);
        int[] prev = null;
        for (int[] p : pts) {
            if (p[0] < 0) { prev = null; continue; }
            if (prev == null) {
                g.fill(p[0], p[1], p[0] + 2, p[1] + 2, SIGN_INK);
            } else {
                int steps = Math.max(Math.abs(p[0] - prev[0]), Math.abs(p[1] - prev[1]));
                for (int s = 0; s <= steps; s++) {
                    int x = prev[0] + (steps == 0 ? 0 : (p[0] - prev[0]) * s / steps);
                    int y = prev[1] + (steps == 0 ? 0 : (p[1] - prev[1]) * s / steps);
                    g.fill(x, y, x + 2, y + 2, SIGN_INK);
                }
            }
            prev = p;
        }
        g.pose().popPose();
    }

    static List<int[]> decode(String strokes) {
        List<int[]> out = new ArrayList<>();
        try {
            byte[] b = Base64.getDecoder().decode(strokes);
            for (int i = 0; i + 1 < b.length; i += 2) {
                int x = b[i] & 255, y = b[i + 1] & 255;
                out.add(x == 255 && y == 255 ? new int[]{-1, -1} : new int[]{x, y});
            }
        } catch (IllegalArgumentException ignored) {
        }
        return out;
    }

    private String encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int[] p : points) {
            out.write(p[0] < 0 ? 255 : p[0]);
            out.write(p[1] < 0 ? 255 : p[1]);
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (draft()) {
            if (inTitle(mx, my)) focus = 0;
            else if (inText(mx, my)) focus = 1;
            return true;
        }
        TreatyData data = data();
        if (data == null || button != 0) return false;
        for (int slot = 0; slot < 2; slot++) {
            if (!inBox(slot, mx, my) || data.signature(slot).isPresent()) continue;
            String me = minecraft.player.getGameProfile().getName();
            if (data.signature(1 - slot).map(s -> s.name().equals(me)).orElse(false)) return true; // signed the other side
            if (drawingSlot != slot) {
                drawingSlot = slot;
                points.clear();
            }
            penDown = true;
            addPoint(mx, my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (penDown && drawingSlot >= 0) {
            addPoint(mx, my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (penDown) {
            penDown = false;
            points.add(new int[]{-1, -1});
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void addPoint(double mx, double my) {
        if (points.size() >= TreatyData.POINTS_MAX) return;
        int x = (int) Math.floor((mx - boxX(drawingSlot)) * 2), y = (int) Math.floor((my - boxY()) * 2);
        x = Math.max(0, Math.min(TreatyData.CANVAS_W - 1, x));
        y = Math.max(0, Math.min(TreatyData.CANVAS_H - 1, y));
        if (!points.isEmpty()) {
            int[] last = points.get(points.size() - 1);
            if (last[0] == x && last[1] == y) return;
        }
        points.add(new int[]{x, y});
    }

    private void commitSignature() {
        if (drawingSlot < 0 || points.stream().noneMatch(p -> p[0] >= 0)) return;
        PacketDistributor.sendToServer(new TreatySignPayload(hand == InteractionHand.OFF_HAND, drawingSlot, encode()));
        stopDrawing();
    }

    private void stopDrawing() {
        drawingSlot = -1;
        points.clear();
        penDown = false;
    }

    private void save() {
        if (title.isBlank() || content.isBlank()) return;
        PacketDistributor.sendToServer(new TreatySavePayload(hand == InteractionHand.OFF_HAND, title, content));
        onClose();
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (!draft() || !StringUtil.isAllowedChatCharacter(c)) return super.charTyped(c, modifiers);
        insert(String.valueOf(c));
        return true;
    }

    /** Adds text at the end of the focused field, as much as fits on the page. */
    private void insert(String text) {
        for (char ch : text.toCharArray()) {
            if (ch == '\r') continue;
            if (focus == 0) {
                if (ch == '\n' || !StringUtil.isAllowedChatCharacter(ch)) continue;
                String next = title + ch;
                if (next.length() > TreatyData.TITLE_MAX || font.width(Component.literal(next).withStyle(s -> s.withBold(true))) > PAGE_W - 24) return;
                title = next;
            } else {
                if (ch != '\n' && !StringUtil.isAllowedChatCharacter(ch)) continue;
                String next = content + ch;
                int count = lines(next).size() + (next.endsWith("\n") ? 1 : 0);
                if (next.length() > TreatyData.CONTENT_MAX || count > EDIT_LINES) return;
                content = next;
            }
        }
        updateButtons();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (draft()) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (focus == 0 && !title.isEmpty()) title = title.substring(0, title.length() - 1);
                if (focus == 1 && !content.isEmpty()) content = content.substring(0, content.length() - 1);
                updateButtons();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (focus == 0) focus = 1;
                else insert("\n");
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB) {
                focus = 1 - focus;
                return true;
            }
            if (Screen.isPaste(key)) {
                insert(minecraft.keyboardHandler.getClipboard());
                return true;
            }
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                super.keyPressed(key, scan, modifiers);
                return true; // typing never triggers other key bindings
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
