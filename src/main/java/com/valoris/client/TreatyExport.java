package com.valoris.client;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.mojang.blaze3d.platform.NativeImage;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/**
 * A treaty page as an image (TreatyScreen): saved as a PNG in the screenshots folder, or put on the system clipboard.
 * Minecraft runs Java without a desktop toolkit, so the clipboard is filled by the system: PowerShell on Windows,
 * osascript on macOS, wl-copy or xclip on Linux.
 */
final class TreatyExport {
    private TreatyExport() {}

    static void export(NativeImage image, String title, boolean clipboard) {
        Minecraft mc = Minecraft.getInstance();
        String name = "treaty_" + title.replaceAll("[^A-Za-z0-9_-]+", "_") + "_"
                + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.ROOT).format(new Date()) + ".png";
        File file = clipboard ? new File(System.getProperty("java.io.tmpdir"), name)
                : new File(new File(mc.gameDirectory, "screenshots"), name);
        Util.ioPool().execute(() -> {
            String message;
            ChatFormatting color = ChatFormatting.GREEN;
            try (image) {
                file.getParentFile().mkdirs();
                image.writeToFile(file);
                if (clipboard) {
                    copy(file);
                    message = "The treaty was copied to the clipboard as an image.";
                } else {
                    message = null;
                }
            } catch (Exception e) {
                valoris2.LOGGER.warn("Treaty export failed", e);
                message = (clipboard ? "Could not copy the treaty: " : "Could not save the treaty: ") + e.getMessage();
                color = ChatFormatting.RED;
            }
            String text = message;
            ChatFormatting c = color;
            mc.execute(() -> {
                if (mc.player == null) return;
                if (text != null) {
                    mc.player.displayClientMessage(Component.literal(text).withStyle(c), false);
                } else {
                    mc.player.displayClientMessage(Component.literal("The treaty was saved as ")
                            .append(Component.literal(file.getName()).withStyle(s -> s.withUnderlined(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath()))))
                            .withStyle(ChatFormatting.GREEN), false);
                }
            });
        });
    }

    private static void copy(File file) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String path = file.getAbsolutePath();
        ProcessBuilder pb;
        if (os.contains("win")) {
            pb = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command",
                    "Add-Type -AssemblyName System.Windows.Forms; Add-Type -AssemblyName System.Drawing; "
                            + "$i = [System.Drawing.Image]::FromFile('" + path.replace("'", "''") + "'); "
                            + "[System.Windows.Forms.Clipboard]::SetImage($i); $i.Dispose()");
        } else if (os.contains("mac")) {
            pb = new ProcessBuilder("osascript", "-e",
                    "set the clipboard to (read (POSIX file \"" + path.replace("\"", "\\\"") + "\") as «class PNGf»)");
        } else if (System.getenv("WAYLAND_DISPLAY") != null) {
            pb = new ProcessBuilder("wl-copy", "--type", "image/png").redirectInput(file);
        } else {
            pb = new ProcessBuilder("xclip", "-selection", "clipboard", "-t", "image/png", "-i", path);
        }
        Process p = pb.redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        if (p.waitFor() != 0) throw new IllegalStateException("the system clipboard command failed");
    }
}
