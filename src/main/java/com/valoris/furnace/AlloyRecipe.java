package com.valoris.furnace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A bronze furnace recipe ("valoris2:alloying"): first_count of "first" (the copper slot) and second_count of "second"
 * (the tin slot) melt together into "result" after cookingtime ticks.
 */
public record AlloyRecipe(Ingredient first, int firstCount, Ingredient second, int secondCount, ItemStack result,
                          float experience, int cookingTime) implements Recipe<AlloyRecipe.Input> {

    /** The two input slots of the furnace. */
    public record Input(ItemStack first, ItemStack second) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return index == 0 ? first : second;
        }

        @Override
        public int size() {
            return 2;
        }
    }

    @Override
    public boolean matches(Input input, Level level) {
        return first.test(input.first()) && input.first().getCount() >= firstCount
                && second.test(input.second()) && input.second().getCount() >= secondCount;
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(FurnaceRegistry.BRONZE_FURNACE_ITEM.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FurnaceRegistry.ALLOYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return FurnaceRegistry.ALLOYING.get();
    }

    public static final class Serializer implements RecipeSerializer<AlloyRecipe> {
        private static final MapCodec<AlloyRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("first").forGetter(AlloyRecipe::first),
                Codec.intRange(1, 64).optionalFieldOf("first_count", 1).forGetter(AlloyRecipe::firstCount),
                Ingredient.CODEC_NONEMPTY.fieldOf("second").forGetter(AlloyRecipe::second),
                Codec.intRange(1, 64).optionalFieldOf("second_count", 1).forGetter(AlloyRecipe::secondCount),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(AlloyRecipe::result),
                Codec.FLOAT.optionalFieldOf("experience", 0F).forGetter(AlloyRecipe::experience),
                Codec.intRange(1, 72000).optionalFieldOf("cookingtime", 300).forGetter(AlloyRecipe::cookingTime)
        ).apply(i, AlloyRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, AlloyRecipe> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.first());
                    buf.writeVarInt(r.firstCount());
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.second());
                    buf.writeVarInt(r.secondCount());
                    ItemStack.STREAM_CODEC.encode(buf, r.result());
                    buf.writeFloat(r.experience());
                    buf.writeVarInt(r.cookingTime());
                },
                buf -> new AlloyRecipe(Ingredient.CONTENTS_STREAM_CODEC.decode(buf), buf.readVarInt(),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buf), buf.readVarInt(),
                        ItemStack.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readVarInt()));

        @Override
        public MapCodec<AlloyRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, AlloyRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
