package com.valoris.furnace;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The bronze furnace: copper (slot 0) and tin (slot 1) melt together into bronze (slot 3) while fuel (slot 2) burns,
 * like a furnace with two inputs. Hoppers: inputs from the top, fuel from the sides, bronze out of the bottom.
 */
public class BronzeFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int FIRST = 0, SECOND = 1, FUEL = 2, RESULT = 3;
    // as a vanilla furnace: inputs from the top, fuel from the sides, result (and empty buckets) out of the bottom
    private static final int[] TOP = {FIRST, SECOND}, SIDES = {FUEL}, BOTTOM = {RESULT, FUEL};

    private NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    private int litTime, litDuration, cookingProgress, cookingTotal;
    private float storedXp;

    /** 0 lit time, 1 lit duration, 2 cooking progress, 3 cooking total - for the menu. */
    final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> litTime;
                case 1 -> litDuration;
                case 2 -> cookingProgress;
                default -> cookingTotal;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> litTime = value;
                case 1 -> litDuration = value;
                case 2 -> cookingProgress = value;
                default -> cookingTotal = value;
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public BronzeFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(FurnaceRegistry.BRONZE_FURNACE_BE.get(), pos, state);
    }

    private boolean isLit() {
        return litTime > 0;
    }

    // ------------------------------------------------------------------ recipes

    private Optional<RecipeHolder<AlloyRecipe>> recipe(Level level) {
        if (items.get(FIRST).isEmpty() || items.get(SECOND).isEmpty()) return Optional.empty();
        return level.getRecipeManager().getRecipeFor(FurnaceRegistry.ALLOYING.get(),
                new AlloyRecipe.Input(items.get(FIRST), items.get(SECOND)), level);
    }

    /** True if some bronze furnace recipe takes this item in the given input slot. */
    public static boolean accepts(Level level, int slot, ItemStack stack) {
        if (level == null || stack.isEmpty()) return false;
        for (RecipeHolder<AlloyRecipe> h : level.getRecipeManager().getAllRecipesFor(FurnaceRegistry.ALLOYING.get())) {
            if ((slot == FIRST ? h.value().first() : h.value().second()).test(stack)) return true;
        }
        return false;
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    private boolean canTakeResult(AlloyRecipe r, Level level) {
        ItemStack out = items.get(RESULT);
        ItemStack result = r.getResultItem(level.registryAccess());
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, BronzeFurnaceBlockEntity be) {
        boolean wasLit = be.isLit();
        boolean changed = false;
        if (be.isLit()) be.litTime--;

        Optional<RecipeHolder<AlloyRecipe>> holder = be.recipe(level);
        AlloyRecipe recipe = holder.map(RecipeHolder::value).filter(r -> be.canTakeResult(r, level)).orElse(null);

        ItemStack fuel = be.items.get(FUEL);
        if (!be.isLit() && recipe != null && !fuel.isEmpty()) {
            int burn = fuel.getBurnTime(RecipeType.SMELTING);
            if (burn > 0) {
                be.litTime = be.litDuration = burn;
                ItemStack remainder = fuel.getCraftingRemainingItem();
                fuel.shrink(1);
                if (fuel.isEmpty() && !remainder.isEmpty()) be.items.set(FUEL, remainder);
                changed = true;
            }
        }

        if (be.isLit() && recipe != null) {
            be.cookingTotal = recipe.cookingTime();
            if (++be.cookingProgress >= be.cookingTotal) {
                be.cookingProgress = 0;
                be.items.get(FIRST).shrink(recipe.firstCount());
                be.items.get(SECOND).shrink(recipe.secondCount());
                ItemStack result = recipe.assemble(null, level.registryAccess());
                if (be.items.get(RESULT).isEmpty()) be.items.set(RESULT, result);
                else be.items.get(RESULT).grow(result.getCount());
                be.storedXp += recipe.experience();
            }
            changed = true;
        } else if (recipe == null) {
            if (be.cookingProgress != 0) changed = true;
            be.cookingProgress = 0;
        } else if (be.cookingProgress > 0) {
            be.cookingProgress = Math.max(0, be.cookingProgress - 2); // the melt cools down without fire
            changed = true;
        }

        if (wasLit != be.isLit()) {
            state = state.setValue(AbstractFurnaceBlock.LIT, be.isLit());
            level.setBlock(pos, state, 3);
            changed = true;
        }
        if (changed) setChanged(level, pos, state);
    }

    /** The vanilla experience of everything melted since it was last taken out. */
    void awardExperience(ServerPlayer player) {
        int whole = (int) storedXp;
        float rest = storedXp - whole;
        if (rest > 0 && player.getRandom().nextFloat() < rest) whole++;
        storedXp = 0;
        if (whole > 0 && player.level() instanceof ServerLevel level) {
            ExperienceOrb.award(level, player.position(), whole);
        }
        setChanged();
    }

    // ------------------------------------------------------------------ container

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.valoris2.bronze_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new BronzeFurnaceMenu(id, inventory, this, data);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return 4;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case FIRST, SECOND -> accepts(level, slot, stack);
            case FUEL -> isFuel(stack);
            default -> false;
        };
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? TOP : side == Direction.DOWN ? BOTTOM : SIDES;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (side != Direction.DOWN) return false;
        if (slot == RESULT) return true;
        // only what is left of a burnt fuel (an empty bucket) comes out of the fuel slot
        return slot == FUEL && (stack.is(net.minecraft.world.item.Items.BUCKET) || stack.is(net.minecraft.world.item.Items.WATER_BUCKET));
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot == FUEL && side == Direction.DOWN) return false;
        return canPlaceItem(slot, stack);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("LitTime", litTime);
        tag.putInt("LitDuration", litDuration);
        tag.putInt("CookTime", cookingProgress);
        tag.putInt("CookTimeTotal", cookingTotal);
        tag.putFloat("StoredXp", storedXp);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        litTime = tag.getInt("LitTime");
        litDuration = tag.getInt("LitDuration");
        cookingProgress = tag.getInt("CookTime");
        cookingTotal = tag.getInt("CookTimeTotal");
        storedXp = tag.getFloat("StoredXp");
    }
}
