package com.valoris.util;

import java.util.regex.Pattern;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

public final class Names {
    private static final Pattern VALID = Pattern.compile("[\\p{L}\\p{N} '_-]{3,24}");

    private Names() {}

    /** Validates and trims a city/kingdom name. */
    public static String check(String raw) throws CommandSyntaxException {
        String name = raw.trim();
        if (!VALID.matcher(name).matches()) {
            throw Errors.of("Names must be 3-24 characters: letters, digits, spaces, ' _ -");
        }
        return name;
    }
}
