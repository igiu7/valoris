package com.valoris.command;

import com.valoris.net.ValorisNet;
import com.valoris.valoris2;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /valoris gui           - opens the menu (default key: K)
 * /valoris resident ...   - everybody (residents / peasants)
 * /valoris city ...       - city rulers
 * /valoris kingdom ...    - kings (and info for everybody)
 * /valoris diplomacy ...  - kings
 */
@EventBusSubscriber(modid = valoris2.MODID)
public class ValorisCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("valoris")
                .then(net.minecraft.commands.Commands.literal("gui")
                        .executes(c -> CommandUtil.run(c, p -> ValorisNet.sendState(p, true))))
                .then(net.minecraft.commands.Commands.literal("requests").executes(c -> {
                    com.valoris.diplomacy.Inbox.send(c.getSource().getPlayerOrException(), false, true);
                    return 1;
                }))
                .then(ResidentCommands.build())
                .then(CityCommands.build())
                .then(KingdomCommands.build())
                .then(DiplomacyCommands.build())
                .then(WarCommands.build())
                .then(SkillCommands.build())
                .then(TechCommands.build()));
    }
}
