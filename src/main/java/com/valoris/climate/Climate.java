package com.valoris.climate;

import com.valoris.Config;
import com.valoris.net.ClimatePayload;
import com.valoris.registry.ModArmor;
import com.valoris.tech.ArmorBalance;
import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Cold and heat. In icy and snowy biomes a cold meter, in deserts and badlands a heat meter runs down (a full meter
 * lasts climate.minutes); when one is empty the player takes damage. Shown by client.ClimateHud where the air
 * bubbles are.
 * Cold: every piece of fur armor slows it down; a full set of fur armor (wolf and bear pieces mix) or a heat source
 * nearby (fire, campfire, lava, magma, a lit furnace... - not torches, lanterns or candles) warms the player up fast.
 * Heat: every piece of metal armor (not bronze) makes it run down faster, fur armor twice as fast; under a roof it
 * slowly refills whatever the armor, a bottle of water refills it at once.
 * Outside those biomes both refill. The meters are kept in the player's data as what is missing (0 = full) and
 * start full again after death.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class Climate {
    private Climate() {}

    public static final int NONE = 0, COLD = 1, HOT = 2;
    public static final int MAX = 1000;
    private static final String COLD_KEY = "valoris2_cold_loss";
    private static final String HEAT_KEY = "valoris2_heat_loss";

    private static final int PLAIN = 0, METAL = 1, FUR = 2;
    // armor pieces of these materials are not metal, whatever era they belong to
    private static final String[] NOT_METAL = {"gambeson", "linothorax", "leather", "fur", "cloth", "padded",
            "quilted", "robe"};

    public static int zone(Level level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return NONE;
        Holder<Biome> b = level.getBiome(pos);
        if (b.is(Tags.Biomes.IS_DESERT) || b.is(BiomeTags.IS_BADLANDS) || b.is(Tags.Biomes.IS_BADLANDS)) return HOT;
        if (b.is(Tags.Biomes.IS_ICY) || b.is(Tags.Biomes.IS_SNOWY) || b.value().getBaseTemperature() <= 0.0F) return COLD;
        return NONE;
    }

    private static boolean isFur(Item item) {
        return item == ModArmor.WOLF_FUR_HELMET.get() || item == ModArmor.WOLF_FUR_CHESTPLATE.get()
                || item == ModArmor.WOLF_FUR_LEGGINGS.get() || item == ModArmor.WOLF_FUR_BOOTS.get()
                || item == ModArmor.BEAR_FUR_HELMET.get() || item == ModArmor.BEAR_FUR_CHESTPLATE.get()
                || item == ModArmor.BEAR_FUR_LEGGINGS.get() || item == ModArmor.BEAR_FUR_BOOTS.get();
    }

    private static int kind(ItemStack stack) {
        if (stack.isEmpty()) return PLAIN;
        Item item = stack.getItem();
        if (isFur(item)) return FUR;
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
        for (String s : NOT_METAL) if (path.contains(s)) return PLAIN;
        String era = ArmorBalance.era(item);
        if (era != null) return era.equals("bronze") ? PLAIN : METAL;
        if (item instanceof ArmorItem armor) {
            Holder<ArmorMaterial> m = armor.getMaterial();
            if (m.is(ArmorMaterials.IRON.unwrapKey().orElseThrow()) || m.is(ArmorMaterials.CHAIN.unwrapKey().orElseThrow())
                    || m.is(ArmorMaterials.GOLD.unwrapKey().orElseThrow())
                    || m.is(ArmorMaterials.NETHERITE.unwrapKey().orElseThrow())) return METAL;
        }
        return PLAIN;
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** A heat source that warms a cold player: fire, campfires, lava, magma, anything lit except small lights. */
    private static boolean nearHeat(ServerLevel level, BlockPos center, int r) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 2, r))) {
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            Block b = s.getBlock();
            if (b instanceof TorchBlock || b instanceof LanternBlock || b instanceof CandleBlock
                    || b instanceof CandleCakeBlock || b instanceof RedstoneLampBlock || b instanceof RedStoneOreBlock) continue;
            if (s.is(BlockTags.FIRE) || s.is(Blocks.MAGMA_BLOCK) || s.getFluidState().is(FluidTags.LAVA)) return true;
            if (s.is(BlockTags.CAMPFIRES)) {
                if (CampfireBlock.isLitCampfire(s)) return true;
                continue;
            }
            if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT)) return true;
        }
        return false;
    }

    private static double perSecond(double secondsForFull) {
        return MAX / secondsForFull;
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        CompoundTag data = player.getPersistentData();
        double cold = data.getDouble(COLD_KEY), heat = data.getDouble(HEAT_KEY);
        int zone = NONE;
        if (Config.CLIMATE_ENABLED.get() && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
            zone = zone(player.level(), player.blockPosition());
            double base = perSecond(Config.CLIMATE_MINUTES.get() * 60.0);
            double outside = perSecond(Config.CLIMATE_OUTSIDE_REFILL_SECONDS.get());
            int fur = 0, metal = 0;
            for (EquipmentSlot slot : ARMOR) {
                int k = kind(player.getItemBySlot(slot));
                if (k == FUR) fur++;
                else if (k == METAL) metal++;
            }
            if (zone == COLD) {
                if (fur == 4 || nearHeat(player.serverLevel(), player.blockPosition(), Config.CLIMATE_HEAT_SOURCE_RADIUS.get())) {
                    cold -= perSecond(Config.CLIMATE_FAST_REFILL_SECONDS.get());
                } else {
                    cold += base * Math.max(0.0, 1.0 - fur * Config.CLIMATE_FUR_SLOWDOWN.get());
                }
                heat -= outside;
            } else if (zone == HOT) {
                if (!player.level().canSeeSky(BlockPos.containing(player.getEyePosition()))) {
                    heat -= perSecond(Config.CLIMATE_SHADE_REFILL_SECONDS.get());
                } else {
                    heat += base * (1.0 + metal * Config.CLIMATE_METAL_PIECE.get() + fur * Config.CLIMATE_FUR_PIECE.get());
                }
                cold -= outside;
            } else {
                cold -= outside;
                heat -= outside;
            }
            cold = Math.max(0, Math.min(MAX, cold));
            heat = Math.max(0, Math.min(MAX, heat));
            if ((cold >= MAX || heat >= MAX) && (player.tickCount / 20) % Config.CLIMATE_DAMAGE_SECONDS.get() == 0) {
                float dmg = Config.CLIMATE_DAMAGE.get().floatValue();
                if (dmg > 0) player.hurt(heat >= MAX ? player.damageSources().dryOut() : player.damageSources().freeze(), dmg);
            }
        } else if (!Config.CLIMATE_ENABLED.get()) {
            cold = 0;
            heat = 0;
        }
        store(player, cold, heat, zone);
    }

    private static void store(ServerPlayer player, double cold, double heat, int zone) {
        CompoundTag data = player.getPersistentData();
        if (cold <= 0) data.remove(COLD_KEY);
        else data.putDouble(COLD_KEY, cold);
        if (heat <= 0) data.remove(HEAT_KEY);
        else data.putDouble(HEAT_KEY, heat);
        PacketDistributor.sendToPlayer(player, new ClimatePayload(MAX - (int) Math.ceil(cold), MAX - (int) Math.ceil(heat), zone));
    }

    /** A bottle of water cools the player down at once. */
    @SubscribeEvent
    public static void onDrink(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItem();
        if (!stack.is(Items.POTION)) return;
        if (!stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) return;
        CompoundTag data = player.getPersistentData();
        store(player, data.getDouble(COLD_KEY), 0, zone(player.level(), player.blockPosition()));
    }

    /** The meters start full after death. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        event.getEntity().getPersistentData().remove(COLD_KEY);
        event.getEntity().getPersistentData().remove(HEAT_KEY);
    }
}
