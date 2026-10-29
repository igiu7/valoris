package com.valoris.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.valoris.client.ClientActions;
import com.valoris.client.ClientState;
import com.valoris.net.GuiState;
import com.valoris.net.Row;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Panel;
import net.minecraft.network.chat.Component;

/** Diplomacy of the king's kingdom: incoming proposals and one button per other kingdom. */
public class DiplomacyScreen extends ValorisScreen {
    public DiplomacyScreen() {
        super(Component.literal("Diplomacy"));
        setHasSearchBox(false);
    }

    private static String relationLabel(int rel) {
        return switch (rel) {
            case 1 -> "Alliance";
            case 2 -> "Non-aggression pact";
            case 3 -> "WAR";
            default -> "Neutral";
        };
    }

    private static Icon relationIcon(int rel) {
        return switch (rel) {
            case 1 -> Icons.FRIENDS_GROUP;
            case 2 -> Icons.SHIELD;
            case 3 -> Icons.BARRIER;
            default -> Icons.GLOBE;
        };
    }

    private static String proposalLabel(String proposal) {
        return switch (proposal) {
            case "ALLIANCE" -> "an alliance";
            case "NON_AGGRESSION" -> "a non-aggression pact";
            default -> "peace";
        };
    }

    private static String time(int seconds) {
        return seconds >= 60 ? (seconds / 60) + " min" : seconds + " s";
    }

    private void openSiegeList(String kingdomName) {
        new PickScreen("Siege - cities of " + kingdomName, "That kingdom has no cities.", () -> {
            GuiState st = ClientState.get();
            List<PickScreen.Entry> list = new ArrayList<>();
            for (Row c : st.cities) {
                if (!c.c().equals(kingdomName)) continue;
                String label = c.a() + " (ruler " + c.b() + ")";
                int flags = c.m();
                if ((flags & 2) != 0) {
                    list.add(PickScreen.Entry.readOnly(label + " - already under siege", Icons.BARRIER));
                } else if ((flags & 4) != 0) {
                    list.add(PickScreen.Entry.readOnly(label + " - protected after a failed siege", Icons.LOCK));
                } else if ((flags & 1) == 0) {
                    list.add(PickScreen.Entry.readOnly(label + " - no standing city banner", Icons.BARRIER));
                } else {
                    list.add(PickScreen.Entry.confirmed(label, Icons.BARRIER,
                            "Besiege " + c.a() + "? It begins in " + st.siegePrepMinutes + " min and lasts "
                                    + st.siegeMinutes + " min. Destroy the city banner to conquer the city.",
                            "valoris war siege " + ClientActions.q(c.a())));
                }
            }
            return list;
        }).withSearch().openGui();
    }

    @Override
    public void addButtons(Panel panel) {
        GuiState s = ClientState.get();

        for (Row siege : s.sieges) {
            String when = siege.m() == 0 ? "begins in " : "ends in ";
            panel.add(infoRow(panel, "SIEGE: " + siege.a() + " vs " + siege.b() + " (" + siege.c() + ") - "
                    + when + time(siege.n())));
        }

        for (Row proposal : s.proposals) {
            String kingdom = proposal.a();
            List<ContextMenuItem> items = List.of(
                    PickScreen.menuItem("Accept", Icons.ACCEPT, "valoris diplomacy accept " + ClientActions.q(kingdom)),
                    PickScreen.menuItem("Decline", Icons.CANCEL, "valoris diplomacy decline " + ClientActions.q(kingdom)));
            panel.add(button(panel, Component.literal(kingdom + " proposes " + proposalLabel(proposal.b())), Icons.CHAT,
                    () -> openContextMenu(items)));
        }

        boolean any = false;
        for (Row k : s.kingdoms) {
            int rel = k.m();
            if (rel == -1) continue; // own kingdom
            any = true;
            String name = k.a();
            String q = ClientActions.q(name);
            List<ContextMenuItem> items = new ArrayList<>();
            switch (rel) {
                case 0 -> {
                    items.add(PickScreen.menuItem("Propose alliance", Icons.FRIENDS_GROUP, "valoris diplomacy propose alliance " + q));
                    items.add(PickScreen.menuItem("Propose non-aggression pact", Icons.SHIELD, "valoris diplomacy propose nonaggression " + q));
                    items.add(PickScreen.menuItem("Declare war", Icons.BARRIER, "valoris diplomacy war " + q));
                }
                case 1 -> items.add(PickScreen.menuItem("Break alliance", Icons.CANCEL, "valoris diplomacy break " + q));
                case 2 -> {
                    items.add(PickScreen.menuItem("Propose alliance", Icons.FRIENDS_GROUP, "valoris diplomacy propose alliance " + q));
                    items.add(PickScreen.menuItem("Break pact", Icons.CANCEL, "valoris diplomacy break " + q));
                }
                case 3 -> {
                    items.add(new ContextMenuItem(Component.literal("Siege a city..."), Icons.BARRIER, b -> openSiegeList(name)));
                    items.add(PickScreen.menuItem("Propose peace", Icons.ACCEPT, "valoris diplomacy propose peace " + q));
                }
                default -> {}
            }
            Component label = Component.literal(name + " (king " + k.b() + ") - " + relationLabel(rel));
            panel.add(button(panel, label, relationIcon(rel), () -> openContextMenu(items)));
        }
        if (!any) panel.add(infoRow(panel, "There are no other kingdoms yet."));
    }
}
