package com.valoris.armory;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One item the Armory Table can make: what it costs and which technology it needs. */
public record ArmoryEntry(String id, String category, String era, String tech, ItemStack result, List<Cost> cost) {

    /** A material: an item tag ("c:ingots/bronze") or a single item, and how many. */
    public record Cost(String id, boolean tag, int count) {
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) return false;
            ResourceLocation rl = ResourceLocation.parse(id);
            return tag ? stack.is(TagKey.create(Registries.ITEM, rl)) : stack.is(BuiltInRegistries.ITEM.get(rl));
        }

        /** An item to show for this material (first item of the tag). */
        public ItemStack display() {
            ResourceLocation rl = ResourceLocation.parse(id);
            if (!tag) return new ItemStack(BuiltInRegistries.ITEM.get(rl));
            return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl))
                    .flatMap(set -> set.stream().findFirst())
                    .map(h -> new ItemStack((Item) h.value()))
                    .orElse(new ItemStack(Items.BARRIER));
        }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeBoolean(tag);
            buf.writeVarInt(count);
        }

        static Cost read(RegistryFriendlyByteBuf buf) {
            return new Cost(buf.readUtf(), buf.readBoolean(), buf.readVarInt());
        }
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(category);
        buf.writeUtf(era);
        buf.writeUtf(tech);
        ItemStack.STREAM_CODEC.encode(buf, result);
        buf.writeVarInt(cost.size());
        for (Cost c : cost) c.write(buf);
    }

    public static ArmoryEntry read(RegistryFriendlyByteBuf buf) {
        String id = buf.readUtf();
        String category = buf.readUtf();
        String era = buf.readUtf();
        String tech = buf.readUtf();
        ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
        int n = buf.readVarInt();
        List<Cost> cost = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) cost.add(Cost.read(buf));
        return new ArmoryEntry(id, category, era, tech, result, List.copyOf(cost));
    }
}
