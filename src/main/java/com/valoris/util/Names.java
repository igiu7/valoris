package com.valoris.util;

import java.util.regex.Pattern;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

public final class Names {
    private static final Pattern VALID = Pattern.compile("[\\p{L}\\p{N} '_-]{3,24}");

    private static final Pattern TITLE = Pattern.compile("[\\p{L} '-]{2,20}");

    private Names() {}

    /** Validates a kingdom prefix ("Kingdom of") or ruler title ("King"): 2-20 letters, spaces, ' -; spaces collapsed. */
    public static String checkTitle(String raw) throws CommandSyntaxException {
        String title = raw.trim().replaceAll("\\s+", " ");
        if (!TITLE.matcher(title).matches()) {
            throw Errors.of("Titles must be 2-20 characters: letters, spaces, ' -");
        }
        return title;
    }

    /** Validates and trims a city/kingdom name. */
    public static String check(String raw) throws CommandSyntaxException {
        String name = raw.trim();
        if (!VALID.matcher(name).matches()) {
            throw Errors.of("Names must be 3-24 characters: letters, digits, spaces, ' _ -");
        }
        return name;
    }
}
