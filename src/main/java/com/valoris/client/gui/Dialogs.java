package com.valoris.client.gui;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.ModalPanel;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Modal dialogs in the Valoris look: confirmation and name input. */
final class Dialogs {
    private static final int W = 244;
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} '_-]{3,24}");

    private Dialogs() {}

    /** RGB colour picker; onApply gets 0xRRGGBB. */
    static void color(BaseScreen gui, String title, int initial, Consumer<Integer> onApply) {
        ColorDialog dialog = new ColorDialog(gui, title, initial & 0xFFFFFF, onApply);
        dialog.setPos((gui.getWidth() - dialog.getWidth()) / 2, (gui.getHeight() - dialog.getHeight()) / 2);
        gui.pushModalPanel(dialog);
    }

    /**
     * A message with a list of checkboxes. notes: per option null (selectable) or why it cannot be picked.
     * onConfirm gets the picked options (possibly none).
     */
    static void checklist(BaseScreen gui, String title, String message, Color4I accent, List<String> options,
                          List<String> notes, String button, Consumer<List<String>> onConfirm) {
        ChecklistDialog dialog = new ChecklistDialog(gui, title, message, accent, options, notes, button, onConfirm);
        dialog.setPos((gui.getWidth() - dialog.getWidth()) / 2, (gui.getHeight() - dialog.getHeight()) / 2);
        gui.pushModalPanel(dialog);
    }

    static void confirm(BaseScreen gui, String title, String message, Color4I accent, Runnable onYes) {
        ConfirmDialog dialog = new ConfirmDialog(gui, title, message, accent, onYes);
        dialog.setPos((gui.getWidth() - dialog.getWidth()) / 2, (gui.getHeight() - dialog.getHeight()) / 2);
        gui.pushModalPanel(dialog);
    }

    static void prompt(BaseScreen gui, String title, String hint, Color4I accent, Consumer<String> onAccept) {
        prompt(gui, title, hint, accent, "Create", "", onAccept);
    }

    /** Text input with a custom button label and a starting text (e.g. the current name when renaming). */
    static void prompt(BaseScreen gui, String title, String hint, Color4I accent, String button, String initial,
                       Consumer<String> onAccept) {
        InputDialog dialog = new InputDialog(gui, title, hint, accent, button, initial, onAccept);
        dialog.setPos((gui.getWidth() - dialog.getWidth()) / 2, (gui.getHeight() - dialog.getHeight()) / 2);
        gui.pushModalPanel(dialog);
        dialog.focusInput();
    }

    /** Title and text of a notice; onPost gets (title, text). */
    static void notice(BaseScreen gui, String initialTitle, String initialBody,
                       java.util.function.BiConsumer<String, String> onPost) {
        NoticeDialog dialog = new NoticeDialog(gui, initialTitle, initialBody, onPost);
        dialog.setPos((gui.getWidth() - dialog.getWidth()) / 2, (gui.getHeight() - dialog.getHeight()) / 2);
        gui.pushModalPanel(dialog);
        dialog.focusInput();
    }

    /** The whole notice on a large parchment sheet. */
    static void noticeView(BaseScreen gui, com.valoris.net.NoticeRow row) {
        NoticeView dialog = new NoticeView(gui, row);
        dialog.setPos((gui.getWidth() - dialog.getWidth()) / 2, (gui.getHeight() - dialog.getHeight()) / 2);
        gui.pushModalPanel(dialog);
    }

    // ------------------------------------------------------------------ shared look

    private abstract static class BaseDialog extends ModalPanel {
        protected final String title;
        protected final Color4I accent;

        BaseDialog(BaseScreen gui, String title, Color4I accent) {
            super(gui);
            this.title = title;
            this.accent = accent;
        }

        protected void close() {
            getGui().closeModalPanel(this);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            g.pose().pushPose();
            g.pose().translate(0, 0, 900);
            // dim everything behind the dialog
            Color4I.BLACK.withAlpha(130).draw(g, 0, 0, getWindow().getGuiScaledWidth(), getWindow().getGuiScaledHeight());
            super.draw(g, theme, x, y, w, h);
            g.pose().popPose();
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            Color4I.BLACK.withAlpha(60).draw(g, x + 3, y + 3, w, h);
            Ui.box(g, x, y, w, h, Ui.HEADER, Ui.BORDER);
            accent.draw(g, x + 1, y + 1, w - 2, 2);
            Ui.text(g, theme, Component.literal(title).withStyle(ChatFormatting.BOLD), x + 12, y + 10, Ui.TEXT, true);
        }
    }

    // ------------------------------------------------------------------ confirm

    private static final class ConfirmDialog extends BaseDialog {
        private final List<FormattedText> lines;
        private final Runnable onYes;
        private Buttons.ChipButton yes;
        private Buttons.ChipButton no;

        ConfirmDialog(BaseScreen gui, String title, String message, Color4I accent, Runnable onYes) {
            super(gui, title, accent);
            this.onYes = onYes;
            this.lines = gui.getTheme().listFormattedStringToWidth(Component.literal(message), W - 24);
            setSize(W, 28 + lines.size() * 10 + 10 + 26);
        }

        @Override
        public void addWidgets() {
            yes = new Buttons.ChipButton(this, 78, "Confirm", accent, () -> {
                close();
                onYes.run();
            });
            no = new Buttons.ChipButton(this, 78, "Cancel", Ui.GRAY, this::close);
            add(yes);
            add(no);
        }

        @Override
        public void alignWidgets() {
            int y = height - 26;
            yes.setPos(width / 2 - 82, y);
            no.setPos(width / 2 + 4, y);
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            super.drawBackground(g, theme, x, y, w, h);
            int ty = y + 28;
            for (FormattedText line : lines) {
                Ui.text(g, theme, line, x + 12, ty, Ui.MUTED, false);
                ty += 10;
            }
        }
    }

    // ------------------------------------------------------------------ name input

    private static final class InputDialog extends BaseDialog {
        private final String hint;
        private final String button;
        private final String initial;
        private final Consumer<String> onAccept;
        private TextBox box;
        private Buttons.ChipButton ok;
        private Buttons.ChipButton cancel;

        InputDialog(BaseScreen gui, String title, String hint, Color4I accent, String button, String initial,
                    Consumer<String> onAccept) {
            super(gui, title, accent);
            this.hint = hint;
            this.button = button;
            this.initial = initial;
            this.onAccept = onAccept;
            setSize(W, 104);
        }

        @Override
        public void addWidgets() {
            box = new TextBox(this) {
                @Override
                public void onEnterPressed() {
                    submit();
                }
            };
            box.ghostText = hint;
            box.setMaxLength(24);
            box.setText(initial, false);
            add(box);
            ok = new Buttons.ChipButton(this, 78, button, accent, this::submit);
            add(ok);
            cancel = new Buttons.ChipButton(this, 78, "Cancel", Ui.GRAY, this::close);
            add(cancel);
        }

        @Override
        public void alignWidgets() {
            box.setPosAndSize(12, 30, width - 24, 16);
            ok.setPos(width / 2 - 82, height - 28);
            cancel.setPos(width / 2 + 4, height - 28);
        }

        private boolean valid() {
            return NAME.matcher(box.getText().trim()).matches();
        }

        private void submit() {
            if (!valid()) return;
            String value = box.getText().trim();
            close();
            onAccept.accept(value);
        }

        void focusInput() {
            box.setFocused(true);
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            super.drawBackground(g, theme, x, y, w, h);
            ok.setEnabled(valid());
            String text = box.getText().trim();
            boolean bad = !text.isEmpty() && !valid();
            Ui.text(g, theme, bad ? "3-24 characters: letters, digits, spaces, ' _ -" : "Press Enter to confirm",
                    x + 12, y + 52, bad ? Ui.RED : Ui.MUTED, false);
        }
    }

    // ------------------------------------------------------------------ notice

    private static final class NoticeDialog extends BaseDialog {
        private static final int NW = 300;
        private static final int TITLE_MAX = 40;
        private static final int BODY_MAX = 300;
        private final String initialTitle;
        private final String initialBody;
        private final java.util.function.BiConsumer<String, String> onPost;
        private TextBox titleBox;
        private TextBox bodyBox;
        private Buttons.ChipButton ok;
        private Buttons.ChipButton cancel;

        NoticeDialog(BaseScreen gui, String initialTitle, String initialBody,
                     java.util.function.BiConsumer<String, String> onPost) {
            super(gui, "Pin a notice", Color4I.rgb(0xd9b77a));
            this.initialTitle = initialTitle;
            this.initialBody = initialBody;
            this.onPost = onPost;
            setSize(NW, 206);
        }

        @Override
        public void addWidgets() {
            titleBox = new TextBox(this) {
                @Override
                public void onEnterPressed() {
                    bodyBox.setFocused(true);
                }
            };
            titleBox.ghostText = "Title";
            titleBox.setMaxLength(TITLE_MAX);
            titleBox.setText(initialTitle, false);
            add(titleBox);
            bodyBox = new TextBox(this) {
                @Override
                public void onEnterPressed() {
                    submit();
                }
            };
            bodyBox.ghostText = "What do you want to tell the world?";
            bodyBox.setMaxLength(BODY_MAX);
            bodyBox.setText(initialBody, false);
            add(bodyBox);
            ok = new Buttons.ChipButton(this, 78, "Pin it", accent, this::submit);
            add(ok);
            cancel = new Buttons.ChipButton(this, 78, "Cancel", Ui.GRAY, this::close);
            add(cancel);
        }

        @Override
        public void alignWidgets() {
            titleBox.setPosAndSize(12, 40, width - 24, 16);
            bodyBox.setPosAndSize(12, 74, width - 24, 16);
            ok.setPos(width / 2 - 82, height - 28);
            cancel.setPos(width / 2 + 4, height - 28);
        }

        private boolean valid() {
            return titleBox.getText().trim().length() >= 3 && !bodyBox.getText().trim().isEmpty();
        }

        private void submit() {
            if (!valid()) return;
            String t = titleBox.getText().trim();
            String b = bodyBox.getText().trim();
            close();
            onPost.accept(t, b);
        }

        void focusInput() {
            titleBox.setFocused(true);
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            super.drawBackground(g, theme, x, y, w, h);
            ok.setEnabled(valid());
            Ui.text(g, theme, "Title  (" + titleBox.getText().length() + "/" + TITLE_MAX + ")", x + 12, y + 29, Ui.MUTED, false);
            Ui.text(g, theme, "Text  (" + bodyBox.getText().length() + "/" + BODY_MAX + ")", x + 12, y + 63, Ui.MUTED, false);
            // the whole text, wrapped, as it will look on the board
            int py = y + 96;
            Ui.box(g, x + 12, py, w - 24, 72, Color4I.rgb(0xeadcb4), Color4I.rgb(0xc4ab78));
            List<FormattedText> lines = theme.listFormattedStringToWidth(Component.literal(bodyBox.getText()), w - 36);
            int ly = py + 5;
            for (int i = 0; i < Math.min(6, lines.size()); i++) {
                Ui.text(g, theme, lines.get(i), x + 18, ly, Color4I.rgb(0x2e1e10), false);
                ly += 10;
            }
            if (lines.size() > 6) Ui.text(g, theme, "...", x + 18, ly, Color4I.rgb(0x6b5032), false);
            Ui.text(g, theme, "Stays up 24 hours. A new notice replaces your old one.", x + 12, y + h - 40, Ui.MUTED, false);
        }
    }

    private static final class NoticeView extends BaseDialog {
        private static final int VW = 300;
        private static final Color4I INK = Color4I.rgb(0x2e1e10);
        private static final Color4I INK_SOFT = Color4I.rgb(0x6b5032);
        private final com.valoris.net.NoticeRow row;
        private final List<FormattedText> titleLines;
        private final List<FormattedText> bodyLines;
        private Buttons.ChipButton close;

        NoticeView(BaseScreen gui, com.valoris.net.NoticeRow row) {
            super(gui, "Notice from " + row.city(), Color4I.rgb(row.color() & 0xFFFFFF));
            this.row = row;
            Theme theme = gui.getTheme();
            this.titleLines = theme.listFormattedStringToWidth(
                    Component.literal(row.title()).withStyle(ChatFormatting.BOLD), VW - 52);
            this.bodyLines = theme.listFormattedStringToWidth(Component.literal(row.body()), VW - 52);
            setSize(VW, 30 + 14 + titleLines.size() * 10 + 22 + bodyLines.size() * 10 + 30 + 34);
        }

        @Override
        public void addWidgets() {
            close = new Buttons.ChipButton(this, 78, "Close", Ui.GRAY, this::close);
            add(close);
        }

        @Override
        public void alignWidgets() {
            close.setPos((width - 78) / 2, height - 28);
        }

        private static String left(int seconds) {
            if (seconds >= 3600) return "Taken down in " + (seconds / 3600) + "h " + (seconds % 3600) / 60 + "m";
            if (seconds >= 60) return "Taken down in " + (seconds / 60) + "m";
            return "Taken down in less than a minute";
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            super.drawBackground(g, theme, x, y, w, h);
            int px = x + 12, py = y + 28, pw = w - 24, ph = h - 28 - 36;
            Color4I.BLACK.withAlpha(80).draw(g, px + 2, py + 3, pw, ph);
            Color4I.rgb(0xc4ab78).draw(g, px - 1, py - 1, pw + 2, ph + 2);
            Color4I.rgb(0xeadcb4).draw(g, px, py, pw, ph);
            Color4I.rgb(0xd8c595).draw(g, px, py + ph - 3, pw, 3);
            Color4I.rgb(row.color() & 0xFFFFFF).draw(g, px, py, 3, ph);
            // pin
            int cx = px + pw / 2, cy = py + 5;
            Color4I.rgb(0xa8322a).draw(g, cx - 2, cy - 2, 5, 5);
            Color4I.rgb(0xe07a6e).draw(g, cx - 1, cy - 1, 2, 2);

            int ty = py + 14;
            for (FormattedText line : titleLines) {
                Ui.text(g, theme, line, px + 14, ty, INK, false);
                ty += 10;
            }
            Color4I.rgb(0xb59a66).draw(g, px + 14, ty + 1, pw - 28, 1);
            ty += 6;
            String from = row.city() + (row.kingdom().isEmpty() ? "" : ", " + row.kingdom());
            Ui.text(g, theme, Ui.fit(theme, from, pw - 28), px + 14, ty, Color4I.rgb(0x7a4b1e), false);
            ty += 16;
            for (FormattedText line : bodyLines) {
                Ui.text(g, theme, line, px + 14, ty, INK, false);
                ty += 10;
            }
            Ui.text(g, theme, Ui.fit(theme, "- " + row.ruler(), pw / 2), px + 14, py + ph - 16, INK_SOFT, false);
            String l = left(row.secondsLeft());
            theme.drawString(g, l, px + pw - 14 - theme.getStringWidth(l), py + ph - 16, INK_SOFT, 0);
        }
    }

    // ------------------------------------------------------------------ colour picker

    private static final int[] PRESETS = {
            0xB02E26, 0x7A1D1D, 0xF9801D, 0xF2C14E, 0xFED83D, 0x80C71F, 0x2E7D32, 0x169C9C, 0x3AB3DA,
            0x3C44AA, 0x1D2A5A, 0x8932B8, 0xC74EBD, 0xF38BAA, 0x835432, 0xF9FFFE, 0x9D9D97, 0x1D1D21};

    private static final class ColorDialog extends BaseDialog {
        private static final int SLIDER_W = 112;
        private final int initial;
        private final Consumer<Integer> onApply;
        private final int[] rgb = new int[3];
        private TextBox hex;
        private boolean updatingHex;
        private Buttons.ChipButton ok;
        private Buttons.ChipButton cancel;

        ColorDialog(BaseScreen gui, String title, int initial, Consumer<Integer> onApply) {
            super(gui, title, Color4I.rgb(initial));
            this.initial = initial;
            this.onApply = onApply;
            setColor(initial);
            setSize(W, 184);
        }

        int color() {
            return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
        }

        void setColor(int c) {
            rgb[0] = (c >> 16) & 255;
            rgb[1] = (c >> 8) & 255;
            rgb[2] = c & 255;
            syncHex();
        }

        void syncHex() {
            if (hex == null) return;
            updatingHex = true;
            hex.setText(String.format("%06X", color()), false);
            updatingHex = false;
        }

        @Override
        public void addWidgets() {
            for (int i = 0; i < 3; i++) add(new Slider(this, i));
            hex = new TextBox(this) {
                @Override
                public void onTextChanged() {
                    if (updatingHex) return;
                    String t = getText().trim().replace("#", "");
                    if (t.matches("[0-9a-fA-F]{6}")) {
                        int c = Integer.parseInt(t, 16);
                        rgb[0] = (c >> 16) & 255;
                        rgb[1] = (c >> 8) & 255;
                        rgb[2] = c & 255;
                    }
                }

                @Override
                public void onEnterPressed() {
                    apply();
                }
            };
            hex.setMaxLength(7);
            add(hex);
            syncHex();
            for (int i = 0; i < PRESETS.length; i++) add(new Swatch(this, PRESETS[i]));
            ok = new Buttons.ChipButton(this, 78, "Apply", Ui.GREEN, this::apply);
            cancel = new Buttons.ChipButton(this, 78, "Cancel", Ui.GRAY, this::close);
            add(ok);
            add(cancel);
        }

        @Override
        public void alignWidgets() {
            int sliderX = 84, y = 32, swatch = 0;
            for (var w : widgets) {
                if (w instanceof Slider sl) {
                    sl.setPosAndSize(sliderX + 10, y + sl.channel * 18, SLIDER_W, 12);
                } else if (w instanceof Swatch sw) {
                    int col = swatch % 9, row = swatch / 9;
                    sw.setPosAndSize(12 + col * 24, 112 + row * 18, 20, 14);
                    swatch++;
                }
            }
            hex.setPosAndSize(sliderX + 22, 88, 60, 14);
            ok.setPos(width / 2 - 82, height - 26);
            cancel.setPos(width / 2 + 4, height - 26);
        }

        private void apply() {
            int c = color();
            close();
            onApply.accept(c);
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            super.drawBackground(g, theme, x, y, w, h);
            Color4I.rgb(color()).draw(g, x + 1, y + 1, w - 2, 2); // the accent line follows the colour
            // preview: current colour above, new colour below
            int px = x + 12, py = y + 30;
            Ui.box(g, px - 1, py - 1, 64, 76, Color4I.BLACK, Ui.BORDER);
            Color4I.rgb(initial).draw(g, px, py, 62, 26);
            Color4I.rgb(color()).draw(g, px, py + 26, 62, 48);
            theme.drawString(g, "Now", px + 3, py + 3, readable(initial), Theme.SHADOW);
            theme.drawString(g, "New", px + 3, py + 29, readable(color()), Theme.SHADOW);
            String[] names = {"R", "G", "B"};
            for (int i = 0; i < 3; i++) {
                int ly = y + 34 + i * 18;
                Ui.text(g, theme, names[i], x + 84, ly, Ui.MUTED, false);
                Ui.textRight(g, theme, String.valueOf(rgb[i]), x + w - 10, ly, Ui.TEXT, true);
            }
            Ui.text(g, theme, "#", x + 96, y + 91, Ui.MUTED, false);
            Ui.text(g, theme, "PRESETS", x + 12, y + 101, Ui.MUTED, false);
        }

        private static Color4I readable(int c) {
            int lum = (((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114) / 1000;
            return lum > 150 ? Color4I.BLACK : Ui.TEXT;
        }

        /** Gradient track for one channel; click or drag to set it. */
        private final class Slider extends dev.ftb.mods.ftblibrary.ui.Widget {
            final int channel;
            private boolean dragging;

            Slider(dev.ftb.mods.ftblibrary.ui.Panel panel, int channel) {
                super(panel);
                this.channel = channel;
            }

            private void setFromMouse() {
                int v = Math.round((getMouseX() - getX()) * 255F / (width - 1));
                rgb[channel] = Math.max(0, Math.min(255, v));
                syncHex();
            }

            @Override
            public boolean mousePressed(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
                if (!isMouseOver()) return false;
                dragging = true;
                setFromMouse();
                return true;
            }

            @Override
            public void mouseReleased(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
                dragging = false;
            }

            @Override
            public boolean mouseScrolled(double scroll) {
                if (!isMouseOver()) return false;
                rgb[channel] = Math.max(0, Math.min(255, rgb[channel] + (scroll > 0 ? 1 : -1)));
                syncHex();
                return true;
            }

            @Override
            public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
                if (dragging) setFromMouse();
                GuiHelper.setupDrawing();
                Ui.box(g, x - 1, y - 1, w + 2, h + 2, Color4I.BLACK, Ui.BORDER);
                for (int i = 0; i < w; i++) {
                    int[] c = rgb.clone();
                    c[channel] = i * 255 / (w - 1);
                    Color4I.rgb((c[0] << 16) | (c[1] << 8) | c[2]).draw(g, x + i, y, 1, h);
                }
                int kx = x + rgb[channel] * (w - 1) / 255;
                Color4I.BLACK.draw(g, kx - 2, y - 2, 5, h + 4);
                Color4I.WHITE.draw(g, kx - 1, y - 1, 3, h + 2);
            }
        }

        /** One preset colour. */
        private final class Swatch extends dev.ftb.mods.ftblibrary.ui.Widget {
            private final int value;

            Swatch(dev.ftb.mods.ftblibrary.ui.Panel panel, int value) {
                super(panel);
                this.value = value;
            }

            @Override
            public boolean mousePressed(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
                if (!isMouseOver()) return false;
                setColor(value);
                return true;
            }

            @Override
            public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
                GuiHelper.setupDrawing();
                boolean sel = color() == value;
                Ui.box(g, x, y, w, h, Color4I.rgb(value), sel ? Ui.TEXT : isMouseOver() ? Ui.MUTED : Ui.BORDER);
                if (sel) GuiHelper.drawHollowRect(g, x + 1, y + 1, w - 2, h - 2, Color4I.BLACK, false);
            }
        }
    }

    // ------------------------------------------------------------------ checklist

    private static final class ChecklistDialog extends BaseDialog {
        private static final int ROW = 16;
        private final List<FormattedText> lines;
        private final List<String> options;
        private final List<String> notes;
        private final boolean[] picked;
        private final String button;
        private final Consumer<List<String>> onConfirm;
        private Buttons.ChipButton ok;
        private Buttons.ChipButton cancel;

        ChecklistDialog(BaseScreen gui, String title, String message, Color4I accent, List<String> options,
                        List<String> notes, String button, Consumer<List<String>> onConfirm) {
            super(gui, title, accent);
            this.lines = gui.getTheme().listFormattedStringToWidth(Component.literal(message), W + 36 - 24);
            this.options = options;
            this.notes = notes;
            this.picked = new boolean[options.size()];
            this.button = button;
            this.onConfirm = onConfirm;
            setSize(W + 36, 30 + lines.size() * 10 + 8 + Math.max(1, options.size()) * ROW + 36);
        }

        private int listY() {
            return 30 + lines.size() * 10 + 6;
        }

        @Override
        public void addWidgets() {
            for (int i = 0; i < options.size(); i++) add(new Check(this, i));
            ok = new Buttons.ChipButton(this, 96, button, accent, () -> {
                List<String> out = new java.util.ArrayList<>();
                for (int i = 0; i < options.size(); i++) if (picked[i]) out.add(options.get(i));
                close();
                onConfirm.accept(out);
            });
            cancel = new Buttons.ChipButton(this, 78, "Cancel", Ui.GRAY, this::close);
            add(ok);
            add(cancel);
        }

        @Override
        public void alignWidgets() {
            for (var w : widgets) {
                if (w instanceof Check c) c.setPosAndSize(12, listY() + c.index * ROW, width - 24, ROW - 2);
            }
            ok.setPos(width / 2 - 100, height - 26);
            cancel.setPos(width / 2 + 4, height - 26);
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            super.drawBackground(g, theme, x, y, w, h);
            int ty = y + 28;
            for (FormattedText line : lines) {
                Ui.text(g, theme, line, x + 12, ty, Ui.MUTED, false);
                ty += 10;
            }
            if (options.isEmpty()) Ui.text(g, theme, "(nothing to choose)", x + 14, y + listY() + 3, Ui.MUTED, false);
        }

        private final class Check extends dev.ftb.mods.ftblibrary.ui.Widget {
            final int index;

            Check(dev.ftb.mods.ftblibrary.ui.Panel panel, int index) {
                super(panel);
                this.index = index;
            }

            private boolean enabled() {
                return notes.get(index) == null;
            }

            @Override
            public boolean mousePressed(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
                if (!isMouseOver() || !enabled()) return false;
                picked[index] = !picked[index];
                return true;
            }

            @Override
            public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
                GuiHelper.setupDrawing();
                boolean on = picked[index];
                if (isMouseOver() && enabled()) Ui.TILE_HOVER.draw(g, x - 2, y - 1, w + 4, h + 2);
                Color4I box = enabled() ? (on ? accent : Ui.MUTED) : Ui.TILE_BORDER;
                Ui.box(g, x, y + 2, 10, 10, on ? accent.withAlpha(200) : Ui.TILE_DISABLED, box);
                if (on) theme.drawString(g, "\u2714", x + 5, y + 3, Ui.TEXT, Theme.CENTERED);
                Ui.text(g, theme, Ui.fit(theme, options.get(index), w - 20), x + 16, y + 3, enabled() ? Ui.TEXT : Ui.MUTED, false);
                String note = notes.get(index);
                if (note != null) {
                    int lw = theme.getStringWidth(options.get(index));
                    Ui.textRight(g, theme, Ui.fit(theme, note, w - lw - 30), x + w, y + 3, Ui.MUTED, false);
                }
            }
        }
    }
}
