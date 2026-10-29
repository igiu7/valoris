package com.valoris.client.gui;

import dev.ftb.mods.ftblibrary.icon.Color4I;

/** A small action button drawn inside a row (Accept, Decline, Invite, ...). */
record Chip(String label, Color4I color, Runnable action, boolean enabled) {
    static Chip of(String label, Color4I color, Runnable action) {
        return new Chip(label, color, action, true);
    }
}
