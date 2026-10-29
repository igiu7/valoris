package com.valoris.skill;

import java.util.function.Supplier;

import com.valoris.valoris2;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SkillAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, valoris2.MODID);

    public static final Supplier<AttachmentType<SkillData>> SKILLS = ATTACHMENTS.register("skills",
            () -> AttachmentType.builder(SkillData::new).serialize(SkillData.CODEC).copyOnDeath().build());

    private SkillAttachments() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
