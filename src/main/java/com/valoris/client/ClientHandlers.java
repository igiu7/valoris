package com.valoris.client;

import com.valoris.net.ArmoryCatalogPayload;
import com.valoris.net.ArmoryOpenPayload;
import com.valoris.net.ConquestPayload;
import com.valoris.net.StatePayload;
import com.valoris.net.WeaponBalancePayload;
import com.valoris.tech.WeaponBalance;

import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientHandlers {
    private ClientHandlers() {}

    public static void onState(StatePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientState.receive(payload));
    }

    public static void onConquest(ConquestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientState.receiveConquest(payload));
    }

    public static void onWeaponBalance(WeaponBalancePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> WeaponBalance.set(payload.referenceBase(), payload.materials(), payload.types(), payload.itemMaterials()));
    }

    public static void onArmoryCatalog(ArmoryCatalogPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientArmory.setCatalog(payload.eraNames(), payload.entries());
            com.valoris.tech.ArmorBalance.set(payload.armorBalance(), payload.entries());
        });
    }

    public static void onAnimalInfo(com.valoris.net.AnimalInfoPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> AnimalHud.receive(payload));
    }

    public static void onRespawnOptions(com.valoris.net.RespawnOptionsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RespawnClient.receive(payload));
    }

    public static void onRespawnGo(com.valoris.net.RespawnGoPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(RespawnClient::go);
    }

    public static void onCityMap(com.valoris.net.CityMapPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof dev.ftb.mods.ftblibrary.ui.ScreenWrapper w
                    && w.getGui() instanceof com.valoris.client.gui.CityMapScreen map) {
                map.receive(payload);
            }
        });
    }

    public static void onInbox(com.valoris.net.InboxPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientInbox.receive(payload));
    }

    public static void onArmoryOpen(ArmoryOpenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientArmory.setKnown(payload.knownTechs(), payload.techNames());
            new com.valoris.client.gui.ArmoryScreen(payload.pos()).openGui();
        });
    }
}
