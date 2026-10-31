package com.valoris.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.valoris.client.ClientActions;
import com.valoris.client.ClientState;
import com.valoris.net.GuiState;
import com.valoris.net.Row;

import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Panel;
import net.minecraft.network.chat.Component;

/** Main menu. What is shown depends on the player's role: wanderer, resident, city ruler or king. */
public class MainScreen extends ValorisScreen {
    public MainScreen() {
        super(titleFor(ClientState.get()));
    }

    private static Component titleFor(GuiState s) {
        return Component.literal("Valoris - " + s.roleTitle);
    }

    @Override
    public void onStateChanged() {
        setTitle(titleFor(ClientState.get()));
        super.onStateChanged();
    }

    @Override
    public void addButtons(Panel panel) {
        GuiState s = ClientState.get();
        boolean hasCity = !s.cityName.isEmpty();

        // ---- summary ----
        if (hasCity) {
            panel.add(infoRow(panel, "City: " + s.cityName + " - ruler " + s.cityRuler + " (" + s.residents + " residents)"));
            panel.add(infoRow(panel, s.founded
                    ? "Claimed chunks: " + s.claimed + "/" + s.claimLimit
                    : "Not founded yet - place your City Banner"));
        } else {
            panel.add(infoRow(panel, "You do not belong to any city."));
        }
        if (!s.kingdomName.isEmpty()) {
            panel.add(infoRow(panel, "Kingdom: " + s.myKingdomTitle() + " - " + s.rulerTitle + " " + s.king));
        }

        // ---- actions by role ----
        switch (s.role) {
            case "KING" -> {
                addCityRulerActions(panel, s);
                addKingActions(panel, s);
            }
            case "CITY_RULER" -> {
                addCityRulerActions(panel, s);
                addKingdomActionsForRuler(panel, s);
            }
            case "RESIDENT" -> addResidentActions(panel, s);
            default -> addWandererActions(panel, s);
        }
        addBrowseActions(panel);
    }

    // ------------------------------------------------------------------ wanderer

    private void addWandererActions(Panel panel, GuiState s) {
        panel.add(button(panel, "Create a city", Icons.ADD,
                () -> promptName(Component.literal("City name"), name -> ClientActions.command("valoris city create " + ClientActions.q(name)))));

        panel.add(button(panel, "Ask to join a city...", Icons.PLAYER, () -> new PickScreen("Join a city", "No cities exist yet.", () -> {
            List<PickScreen.Entry> list = new ArrayList<>();
            for (Row c : ClientState.get().cities) {
                list.add(PickScreen.Entry.command(c.a() + " (ruler " + c.b() + ", " + c.n() + " residents)", Icons.PLAYER,
                        "valoris resident request " + ClientActions.q(c.a()), true));
            }
            return list;
        }).withSearch().openGui()));

        if (!s.cityInvites.isEmpty()) {
            panel.add(button(panel, "Invitations (" + s.cityInvites.size() + ")...", Icons.CHAT,
                    () -> new PickScreen("City invitations", "No invitations.", () -> {
                        List<PickScreen.Entry> list = new ArrayList<>();
                        for (String city : ClientState.get().cityInvites) {
                            list.add(PickScreen.Entry.command("Accept: " + city, Icons.ACCEPT,
                                    "valoris resident accept " + ClientActions.q(city), false));
                        }
                        return list;
                    }).openGui()));
        }
    }

    // ------------------------------------------------------------------ resident

    private void addResidentActions(Panel panel, GuiState s) {
        panel.add(button(panel, "Leave city", Icons.REMOVE, () -> confirm(
                Component.literal("Leave the city of " + s.cityName + "?"), Component.empty(),
                () -> ClientActions.command("valoris resident leave"))));
    }

    // ------------------------------------------------------------------ city ruler (and king)

    private void addCityRulerActions(Panel panel, GuiState s) {
        if (s.founded) {
            panel.add(button(panel, "Claim this chunk", Icons.ADD, () -> ClientActions.command("valoris city claim")));
            panel.add(button(panel, "Unclaim this chunk", Icons.REMOVE, () -> ClientActions.command("valoris city unclaim")));
            panel.add(button(panel, "Change city banner location", Icons.MAP, () -> confirm(
                    Component.literal("Move the city banner to where you are standing?"),
                    Component.literal("You must stand inside your city's land, on a free spot with a solid block below."),
                    () -> ClientActions.command("valoris city banner move"))));

            panel.add(button(panel, "Invite a player to the city...", Icons.PLAYER,
                    () -> new PickScreen("Invite to " + s.cityName, "No online players without a city.", () -> {
                        List<PickScreen.Entry> list = new ArrayList<>();
                        for (String name : ClientState.get().candidates) {
                            list.add(PickScreen.Entry.command(name, Icons.PLAYER, "valoris city invite " + name, true));
                        }
                        return list;
                    }).withSearch().openGui()));

            if (!s.cityJoinRequests.isEmpty()) {
                panel.add(button(panel, "Join requests (" + s.cityJoinRequests.size() + ")...", Icons.CHAT,
                        () -> new PickScreen("City join requests", "No requests.", () -> {
                            List<PickScreen.Entry> list = new ArrayList<>();
                            for (String name : ClientState.get().cityJoinRequests) {
                                list.add(PickScreen.Entry.menu(name + " wants to join", Icons.PLAYER, List.of(
                                        PickScreen.menuItem("Accept", Icons.ACCEPT, "valoris city accept " + name),
                                        PickScreen.menuItem("Decline", Icons.CANCEL, "valoris city decline " + name))));
                            }
                            return list;
                        }).openGui()));
            }

            panel.add(button(panel, "Remove a resident...", Icons.REMOVE,
                    () -> new PickScreen("Remove a resident", "No other residents online.", () -> {
                        List<PickScreen.Entry> list = new ArrayList<>();
                        for (String name : ClientState.get().onlineResidents) {
                            list.add(PickScreen.Entry.confirmed(name, Icons.REMOVE, "Remove " + name + " from the city?",
                                    "valoris city kick " + name));
                        }
                        return list;
                    }).openGui()));

            if (!s.capital) {
                panel.add(button(panel, "Hand the city to a resident...", Icons.STAR,
                        () -> new PickScreen("Transfer the city", "No other residents online.", () -> {
                            List<PickScreen.Entry> list = new ArrayList<>();
                            for (String name : ClientState.get().onlineResidents) {
                                list.add(PickScreen.Entry.confirmed(name, Icons.STAR,
                                        "Give the city to " + name + "? You will become a resident.",
                                        "valoris city transfer " + name));
                            }
                            return list;
                        }).openGui()));
            }
        }
    }

    private void addKingdomActionsForRuler(Panel panel, GuiState s) {
        if (!s.founded) {
            panel.add(button(panel, "Disband city", Icons.BIN, () -> confirm(
                    Component.literal("Disband the city of " + s.cityName + "?"), Component.literal("This cannot be undone."),
                    () -> ClientActions.command("valoris city disband"))));
            return;
        }

        if (s.kingdomName.isEmpty()) {
            panel.add(button(panel, "Proclaim a kingdom", Icons.STAR,
                    () -> promptName(Component.literal("Kingdom name"), name -> ClientActions.command("valoris kingdom create " + ClientActions.q(name)))));

            panel.add(button(panel, "Ask to join a kingdom...", Icons.GLOBE,
                    () -> new PickScreen("Join a kingdom", "No kingdoms exist yet.", () -> {
                        List<PickScreen.Entry> list = new ArrayList<>();
                        for (Row k : ClientState.get().kingdoms) {
                            list.add(PickScreen.Entry.command(ClientState.get().kingdomTitle(k.a()) + " (" + ClientState.get().rulerTitle(k.a()) + " " + k.b() + ", " + k.n() + " cities)", Icons.GLOBE,
                                    "valoris city kingdom join " + ClientActions.q(k.a()), true));
                        }
                        return list;
                    }).withSearch().openGui()));

            if (!s.kingdomInvites.isEmpty()) {
                panel.add(button(panel, "Kingdom invitations (" + s.kingdomInvites.size() + ")...", Icons.CHAT,
                        () -> new PickScreen("Kingdom invitations", "No invitations.", () -> {
                            List<PickScreen.Entry> list = new ArrayList<>();
                            for (String kingdom : ClientState.get().kingdomInvites) {
                                list.add(PickScreen.Entry.menu("Invitation from " + kingdom, Icons.CHAT, List.of(
                                        PickScreen.menuItem("Accept", Icons.ACCEPT, "valoris city kingdom accept " + ClientActions.q(kingdom)),
                                        PickScreen.menuItem("Decline", Icons.CANCEL, "valoris city kingdom decline " + ClientActions.q(kingdom)))));
                            }
                            return list;
                        }).openGui()));
            }
        } else {
            panel.add(button(panel, "Kingdom cities", Icons.INFO, () -> openKingdomCities()));
            if (!s.capital) {
                panel.add(button(panel, "Rebel against the kingdom...", Icons.BARRIER, () -> confirm(
                        Component.literal("Rebel against the " + s.myKingdomTitle() + "?"),
                        Component.literal("Your city leaves the kingdom and becomes an independent city. A rebellion against "
                                + "the " + s.myKingdomTitle() + " begins at once. If the " + s.rulerTitle + " puts it down, you cannot rebel again for a while."),
                        () -> ClientActions.command("valoris city kingdom rebel"))));
            }
        }

        if (s.kingdomName.isEmpty()) {
            panel.add(button(panel, "Disband city", Icons.BIN, () -> confirm(
                    Component.literal("Disband the city of " + s.cityName + "?"), Component.literal("This cannot be undone."),
                    () -> ClientActions.command("valoris city disband"))));
        }
    }

    // ------------------------------------------------------------------ king

    private void addKingActions(Panel panel, GuiState s) {
        if (!s.conquestCity.isEmpty()) {
            panel.add(button(panel, "Decide the fate of " + s.conquestCity + "...", Icons.CHAT,
                    () -> ClientActions.command("valoris war conquest")));
        }
        panel.add(button(panel, "Kingdom cities", Icons.INFO, () -> openKingdomCities()));

        panel.add(button(panel, "Invite a city to the kingdom...", Icons.ADD,
                () -> new PickScreen("Invite a city", "No online city rulers without a kingdom.", () -> {
                    List<PickScreen.Entry> list = new ArrayList<>();
                    for (Row r : ClientState.get().kingdomCandidates) {
                        list.add(PickScreen.Entry.command(r.b() + " (ruler " + r.a() + ")", Icons.ADD,
                                "valoris kingdom invite " + r.a(), true));
                    }
                    return list;
                }).withSearch().openGui()));

        if (!s.kingdomJoinRequests.isEmpty()) {
            panel.add(button(panel, "City requests (" + s.kingdomJoinRequests.size() + ")...", Icons.CHAT,
                    () -> new PickScreen("Cities asking to join", "No requests.", () -> {
                        List<PickScreen.Entry> list = new ArrayList<>();
                        for (String city : ClientState.get().kingdomJoinRequests) {
                            list.add(PickScreen.Entry.menu(city + " asks to join", Icons.CHAT, List.of(
                                    PickScreen.menuItem("Accept", Icons.ACCEPT, "valoris kingdom accept " + ClientActions.q(city)),
                                    PickScreen.menuItem("Decline", Icons.CANCEL, "valoris kingdom decline " + ClientActions.q(city)))));
                        }
                        return list;
                    }).openGui()));
        }

        panel.add(button(panel, "Expel a city...", Icons.REMOVE,
                () -> new PickScreen("Expel a city", "No other cities in the kingdom.", () -> {
                    List<PickScreen.Entry> list = new ArrayList<>();
                    for (Row c : ClientState.get().myCities) {
                        if (c.n() == 1) continue; // capital
                        list.add(PickScreen.Entry.confirmed(c.a(), Icons.REMOVE, "Expel " + c.a() + " from the kingdom?",
                                "valoris kingdom expel " + ClientActions.q(c.a())));
                    }
                    return list;
                }).openGui()));

        panel.add(button(panel, "Pass the crown...", Icons.STAR,
                () -> new PickScreen("Pass the crown", "No other city rulers are online.", () -> {
                    List<PickScreen.Entry> list = new ArrayList<>();
                    for (Row c : ClientState.get().myCities) {
                        if (c.n() == 1 || c.m() == 0) continue; // not the capital itself; ruler must be online
                        list.add(PickScreen.Entry.confirmed(c.b() + " of " + c.a(), Icons.STAR,
                                "Make " + c.b() + " the " + ClientState.get().rulerTitle + "? Your city stops being the capital.",
                                "valoris kingdom crown " + c.b()));
                    }
                    return list;
                }).openGui()));

        String proposals = s.proposals.isEmpty() ? "Diplomacy..." : "Diplomacy (" + s.proposals.size() + " proposals)...";
        panel.add(button(panel, proposals, Icons.GLOBE, () -> new DiplomacyScreen().openGui()));

        panel.add(button(panel, "Disband the kingdom", Icons.BIN, () -> confirm(
                Component.literal("Disband the " + s.myKingdomTitle() + "?"), Component.literal("This cannot be undone."),
                () -> ClientActions.command("valoris kingdom disband"))));
    }

    // ------------------------------------------------------------------ shared

    private void openKingdomCities() {
        new PickScreen("Cities of the kingdom", "No cities.", () -> {
            List<PickScreen.Entry> list = new ArrayList<>();
            for (Row c : ClientState.get().myCities) {
                list.add(PickScreen.Entry.readOnly(c.a() + " - ruler " + c.b() + (c.n() == 1 ? " (capital)" : ""),
                        c.n() == 1 ? Icons.STAR : Icons.PLAYER));
            }
            return list;
        }).openGui();
    }

    private void addBrowseActions(Panel panel) {
        panel.add(button(panel, "Browse cities", Icons.MAP, () -> new PickScreen("Cities", "No cities yet.", () -> {
            List<PickScreen.Entry> list = new ArrayList<>();
            for (Row c : ClientState.get().cities) {
                String kingdom = c.c().isEmpty() ? "" : " - " + c.c();
                list.add(PickScreen.Entry.readOnly(c.a() + " (ruler " + c.b() + ", " + c.n() + " residents)" + kingdom, Icons.PLAYER));
            }
            return list;
        }).withSearch().openGui()));

        panel.add(button(panel, "Browse kingdoms", Icons.GLOBE, () -> new PickScreen("Kingdoms", "No kingdoms yet.", () -> {
            List<PickScreen.Entry> list = new ArrayList<>();
            for (Row k : ClientState.get().kingdoms) {
                list.add(PickScreen.Entry.readOnly(ClientState.get().kingdomTitle(k.a()) + " (" + ClientState.get().rulerTitle(k.a()) + " " + k.b() + ", " + k.n() + " cities)", Icons.GLOBE));
            }
            return list;
        }).withSearch().openGui()));
    }
}
