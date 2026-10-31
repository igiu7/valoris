package com.valoris.compat;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.neoforged.fml.loading.LoadingModList;

/** Applies the compat mixins only when the mod they patch is installed. */
public final class CompatMixinPlugin implements IMixinConfigPlugin {
    private static boolean present(String modId) {
        LoadingModList list = LoadingModList.get();
        return list != null && list.getModFileById(modId) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (targetClassName.startsWith("net.favouriteless.trotting_wagons.")) return present("trotting_wagons");
        if (targetClassName.startsWith("ru.magistu.siegemachines.")) return present("siegemachines");
        if (targetClassName.startsWith("com.magistuarmory.")) return present("magistuarmory");
        if (targetClassName.startsWith("lekavar.lma.drinkbeer.")) return present("drinkbeer");
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
