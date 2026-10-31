package com.valoris.net;

import com.valoris.client.ClientHandlers;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ValorisNet {
    private ValorisNet() {}

    /** Registered on the mod event bus. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(OpenRequestPayload.TYPE, OpenRequestPayload.CODEC, ValorisNet::onOpenRequest);
        registrar.playToServer(ArmoryCraftPayload.TYPE, ArmoryCraftPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                com.valoris.armory.ArmoryService.craft(player, payload.pos(), payload.entryId());
            }
        }));
        registrar.playToServer(CityMapRequestPayload.TYPE, CityMapRequestPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) com.valoris.war.CityMapService.request(player, payload.city());
        }));
        registrar.playToServer(RespawnChoicePayload.TYPE, RespawnChoicePayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                if (payload.city().equals("?")) com.valoris.respawn.RespawnService.sendOptions(player);
                else com.valoris.respawn.RespawnService.choose(player, payload.city());
            }
        }));
        registrar.playToServer(NoticePostPayload.TYPE, NoticePostPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                com.valoris.city.NoticeBoard.post(player, payload.title(), payload.body());
                sendState(player, false);
            }
        }));
        registrar.playToServer(AnimalInfoRequestPayload.TYPE, AnimalInfoRequestPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) com.valoris.mobs.Breeding.sendInfo(player, payload.entityId());
        }));
        registrar.playToServer(WagonInputPayload.TYPE, WagonInputPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player.level().getEntity(payload.vehicle()) instanceof com.valoris.compat.WagonDrive wagon
                    && ((net.minecraft.world.entity.Entity) wagon).getControllingPassenger() == player) {
                // only -1, 0 or 1 (a modified client could send NaN or huge values)
                wagon.valoris$setInput(sign(payload.forward()), sign(payload.strafe()));
            }
        }));
        if (FMLEnvironment.dist.isClient()) {
            registrar.playToClient(AnimalInfoPayload.TYPE, AnimalInfoPayload.CODEC, ClientHandlers::onAnimalInfo);
            registrar.playToClient(RespawnOptionsPayload.TYPE, RespawnOptionsPayload.CODEC, ClientHandlers::onRespawnOptions);
            registrar.playToClient(RespawnGoPayload.TYPE, RespawnGoPayload.CODEC, ClientHandlers::onRespawnGo);
            registrar.playToClient(InboxPayload.TYPE, InboxPayload.CODEC, ClientHandlers::onInbox);
            registrar.playToClient(CityMapPayload.TYPE, CityMapPayload.CODEC, ClientHandlers::onCityMap);
            registrar.playToClient(StatePayload.TYPE, StatePayload.CODEC, ClientHandlers::onState);
            registrar.playToClient(ConquestPayload.TYPE, ConquestPayload.CODEC, ClientHandlers::onConquest);
            registrar.playToClient(WeaponBalancePayload.TYPE, WeaponBalancePayload.CODEC, ClientHandlers::onWeaponBalance);
            registrar.playToClient(ArmoryCatalogPayload.TYPE, ArmoryCatalogPayload.CODEC, ClientHandlers::onArmoryCatalog);
            registrar.playToClient(ArmoryOpenPayload.TYPE, ArmoryOpenPayload.CODEC, ClientHandlers::onArmoryOpen);
            registrar.playToClient(CombatTagPayload.TYPE, CombatTagPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.valoris.client.CombatClient.receive(payload.remainingMillis())));
            registrar.playToClient(ClimatePayload.TYPE, ClimatePayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> {
                        com.valoris.client.ClimateHud.receive(payload);
                        com.valoris.client.TierBar.receive(payload.tier());
                    }));
            registrar.playToClient(ReloadInterruptPayload.TYPE, ReloadInterruptPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(com.valoris.client.GunReloadClient::interrupted));
            registrar.playToClient(NameColorsPayload.TYPE, NameColorsPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.valoris.client.NameColorsClient.receive(payload.colors())));
        } else {
            registrar.playToClient(ClimatePayload.TYPE, ClimatePayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(ReloadInterruptPayload.TYPE, ReloadInterruptPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(NameColorsPayload.TYPE, NameColorsPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(AnimalInfoPayload.TYPE, AnimalInfoPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(RespawnOptionsPayload.TYPE, RespawnOptionsPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(RespawnGoPayload.TYPE, RespawnGoPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(InboxPayload.TYPE, InboxPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(CityMapPayload.TYPE, CityMapPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(StatePayload.TYPE, StatePayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(ConquestPayload.TYPE, ConquestPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(WeaponBalancePayload.TYPE, WeaponBalancePayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(ArmoryCatalogPayload.TYPE, ArmoryCatalogPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(ArmoryOpenPayload.TYPE, ArmoryOpenPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(CombatTagPayload.TYPE, CombatTagPayload.CODEC, (payload, ctx) -> {});
        }
    }

    private static float sign(float v) {
        return v > 0 ? 1 : v < 0 ? -1 : 0;
    }

    private static void onOpenRequest(OpenRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) sendState(player, payload.open());
        });
    }

    public static void sendArmoryCatalog(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ArmoryCatalogPayload(
                com.valoris.armory.ArmoryCatalog.eraNames(), com.valoris.armory.ArmoryCatalog.entries(),
                com.valoris.armory.ArmoryCatalog.armorBalance()));
    }

    public static void sendWeaponBalance(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new WeaponBalancePayload(
                com.valoris.tech.WeaponBalance.referenceBase(),
                com.valoris.tech.WeaponBalance.materials(),
                com.valoris.tech.WeaponBalance.types(),
                com.valoris.tech.WeaponBalance.itemMaterials()));
    }

    public static void sendConquest(ServerPlayer player, ConquestPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendState(ServerPlayer player, boolean open) {
        PacketDistributor.sendToPlayer(player, new StatePayload(open, StateBuilder.build(player)));
    }
}
