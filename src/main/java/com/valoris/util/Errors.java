package com.valoris.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.network.chat.Component;

public final class Errors {
    private Errors() {}

    public static CommandSyntaxException of(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
}
