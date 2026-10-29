package com.valoris.mobs;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * - hostile mobs no longer spawn on their own (natural spawns, world generation, patrols, zombie reinforcements);
 *   spawners, trial spawners, structures, raids, spawn eggs and commands still work;
 * - wolves no longer spawn the vanilla way, they come in packs from WolfPacks;
 * - land animals (creature category) spawn only once, with the generated land, not again later (mobs.animalsRespawn).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class HostileSpawnRule {
    private HostileSpawnRule() {}

    private static boolean natural(MobSpawnType type) {
        return type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION
                || type == MobSpawnType.PATROL || type == MobSpawnType.REINFORCEMENT;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!natural(event.getSpawnType())) return;
        Mob mob = event.getEntity();
        boolean block;
        if (mob instanceof Wolf) {
            block = Config.WOLF_PACKS_ENABLED.get();
        } else if (mob.getType().getCategory() == MobCategory.CREATURE && !(mob instanceof Enemy)) {
            // land animals appear once, with the generated land; no new ones later (breeding still works)
            block = event.getSpawnType() == MobSpawnType.NATURAL && !Config.ANIMALS_RESPAWN.get();
        } else {
            block = Config.DISABLE_HOSTILE_SPAWNS.get()
                    && (mob instanceof Enemy || mob.getType().getCategory() == MobCategory.MONSTER)
                    && !Config.HOSTILE_SPAWN_ALLOW_LIST.get().contains(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        }
        if (block) {
            event.setSpawnCancelled(true);
            event.setCanceled(true);
        }
    }
}
