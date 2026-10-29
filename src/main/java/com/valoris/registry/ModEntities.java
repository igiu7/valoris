package com.valoris.registry;

import com.valoris.mobs.Bear;
import com.valoris.valoris2;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own creatures. */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, valoris2.MODID);

    /** Big brown bear of tier 2 and 3 land (see mobs.Bear). */
    public static final DeferredHolder<EntityType<?>, EntityType<Bear>> BEAR = ENTITY_TYPES.register("bear",
            () -> EntityType.Builder.of(Bear::new, MobCategory.CREATURE)
                    .sized(1.5F, 1.9F)
                    .eyeHeight(1.3F)
                    .clientTrackingRange(10)
                    .build("bear"));

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(BEAR.get(), Bear.createAttributes().build()));
    }
}
