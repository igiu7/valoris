package com.valoris.client;

import net.minecraft.client.Minecraft;

/** GUI buttons simply run the mod's own commands, so permissions and rules stay in one place (the server). */
public final class ClientActions {
    private ClientActions() {}

    public static void command(String command) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.connection.sendCommand(command);
        // answered from another screen: the request leaves the requests window too
        ClientInbox.entries().removeIf(e -> command.equals(e.acceptCommand()) || command.equals(e.declineCommand()));
        ClientState.scheduleRefresh();
    }

    public static String q(String name) {
        return "\"" + name + "\"";
    }
}
