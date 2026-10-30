package com.valoris.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;

/** Snapshot of everything the GUI needs to know about the viewing player. Built on the server. */
public class GuiState {
    // role: NONE | RESIDENT | CITY_RULER | KING
    public String role = "NONE";
    public String roleTitle = "Wanderer";

    public String cityName = "";
    public String cityRuler = "";
    public boolean founded;
    public boolean capital;
    public int residents;
    public int claimed;
    public int claimLimit;

    public String kingdomName = "";

    // my realm: my kingdom, or my city when it belongs to no kingdom ("" = none). isLeader: I make its diplomacy
    public String realmName = "";
    public boolean realmIsKingdom;
    public boolean isLeader;
    public int realmColor = 0xB02E26; // RGB of my realm on the map
    // Row(name, leader, "KINGDOM" | "CITY", cityCount, relation)  relation: -1 own, 0 neutral, 1 alliance, 3 war
    public List<Row> realms = new ArrayList<>();
    // my wars: Row(warId, ourLeader, enemyLeader, flags, 0)  flags: 1 = we attacked (offensive), 2 = my realm leads our side
    public List<Row> wars = new ArrayList<>();
    // Row(warId, realmName, "OURS" | "THEIRS", isLeader, 0)
    public List<Row> warMembers = new ArrayList<>();
    // calls to arms for my realm: Row(callerName, enemyLeaderName, warId, 0, 0); sent: Row(allyName, enemyLeaderName, warId, 0, 0)
    public List<Row> warCalls = new ArrayList<>();
    public List<Row> sentCalls = new ArrayList<>();
    // statistics of my wars, and of each realm in them: Row(warId, realmName, howItJoined, kills, deaths)
    public List<WarInfo> warInfos = new ArrayList<>();
    public List<Row> warStats = new ArrayList<>();
    public String king = "";

    public int siegePrepMinutes;
    public int siegeMinutes;
    public String conquestCity = "";

    // skills, in Skill order: level, XP inside the current level, XP needed for the next level (0 at max)
    public int[] skillLevel = new int[0];
    public int[] skillXp = new int[0];
    public int[] skillNeed = new int[0];

    // technology of my city (empty when not in a founded city)
    public int[] citySkills = new int[0];
    public List<TechRow> techs = new ArrayList<>();
    public boolean canResearch;        // the viewer is the ruler of a founded city
    public String researching = "";    // id of the technology being researched   // non-empty: the king has a conquered city waiting for a decision

    public List<String> onlineResidents = new ArrayList<>();      // other online members of my city
    public List<String> candidates = new ArrayList<>();           // online players without a city (city invites)
    public List<String> cityInvites = new ArrayList<>();          // cities that invited me
    public List<String> cityJoinRequests = new ArrayList<>();     // players asking to join my city
    public List<String> kingdomInvites = new ArrayList<>();       // kingdoms that invited my city
    public List<String> kingdomJoinRequests = new ArrayList<>();  // cities asking to join my kingdom

    // Row(name, ruler, kingdom, residents, flags)  flags: 1 = can be besieged, 2 = under siege, 4 = protected after a siege,
    //   flags >> 8 = claimed chunks
    public List<Row> cities = new ArrayList<>();
    // Row(name, king, "", cityCount, relation)  relation: -1 own, 0 neutral, 1 alliance, 3 war
    public List<Row> kingdoms = new ArrayList<>();
    // Row(cityName, ruler, "", isCapital, rulerOnline)
    public List<Row> myCities = new ArrayList<>();
    // Row(playerName, cityName, "", 0, 0): online rulers of founded cities without a kingdom
    public List<Row> kingdomCandidates = new ArrayList<>();
    // Row(fromRealm, proposalName, "", 0, 0)
    public List<Row> proposals = new ArrayList<>();
    // Row(toKingdom, proposalName, "", 0, 0): proposals my king sent that are still waiting
    public List<Row> sentProposals = new ArrayList<>();
    // Row(attackerKingdom, city, defenderKingdom, remainingSeconds, phase)  phase: 0 preparation, 1 active
    public List<Row> sieges = new ArrayList<>();

    // notice board, newest first
    public List<NoticeRow> notices = new ArrayList<>();
    // Row(realmName, "", "", secondsLeft, 0): truces of my realm (no war with them until the time is up)
    public List<Row> truces = new ArrayList<>();
    // every kingdom and independent city with its statistics (the client sorts them)
    public List<LeaderRow> leaderboard = new ArrayList<>();

    private static void writeStrings(FriendlyByteBuf buf, List<String> list) {
        buf.writeCollection(list, (b, s) -> b.writeUtf(s));
    }

    private static List<String> readStrings(FriendlyByteBuf buf) {
        return buf.readList(b -> b.readUtf());
    }

    private static void writeRows(FriendlyByteBuf buf, List<Row> list) {
        buf.writeCollection(list, (b, r) -> r.write(b));
    }

    private static List<Row> readRows(FriendlyByteBuf buf) {
        return buf.readList(Row::read);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(role);
        buf.writeUtf(roleTitle);
        buf.writeUtf(cityName);
        buf.writeUtf(cityRuler);
        buf.writeBoolean(founded);
        buf.writeBoolean(capital);
        buf.writeVarInt(residents);
        buf.writeVarInt(claimed);
        buf.writeVarInt(claimLimit);
        buf.writeUtf(kingdomName);
        buf.writeUtf(king);
        buf.writeVarInt(siegePrepMinutes);
        buf.writeVarInt(siegeMinutes);
        buf.writeUtf(conquestCity);
        writeStrings(buf, onlineResidents);
        writeStrings(buf, candidates);
        writeStrings(buf, cityInvites);
        writeStrings(buf, cityJoinRequests);
        writeStrings(buf, kingdomInvites);
        writeStrings(buf, kingdomJoinRequests);
        writeRows(buf, cities);
        writeRows(buf, kingdoms);
        writeRows(buf, myCities);
        writeRows(buf, kingdomCandidates);
        writeRows(buf, proposals);
        writeRows(buf, sieges);
        writeRows(buf, sentProposals);
        buf.writeUtf(realmName);
        buf.writeBoolean(realmIsKingdom);
        buf.writeBoolean(isLeader);
        buf.writeInt(realmColor);
        writeRows(buf, realms);
        writeRows(buf, wars);
        writeRows(buf, warMembers);
        writeRows(buf, warCalls);
        writeRows(buf, sentCalls);
        buf.writeCollection(warInfos, (b, w) -> w.write(b));
        writeRows(buf, warStats);
        buf.writeVarIntArray(skillLevel);
        buf.writeVarIntArray(skillXp);
        buf.writeVarIntArray(skillNeed);
        buf.writeVarIntArray(citySkills);
        buf.writeCollection(techs, (b, t) -> t.write(b));
        buf.writeBoolean(canResearch);
        buf.writeUtf(researching);
        buf.writeCollection(notices, (b, n) -> n.write(b));
        writeRows(buf, truces);
        buf.writeCollection(leaderboard, (b, r) -> r.write(b));
    }

    public static GuiState read(FriendlyByteBuf buf) {
        GuiState s = new GuiState();
        s.role = buf.readUtf();
        s.roleTitle = buf.readUtf();
        s.cityName = buf.readUtf();
        s.cityRuler = buf.readUtf();
        s.founded = buf.readBoolean();
        s.capital = buf.readBoolean();
        s.residents = buf.readVarInt();
        s.claimed = buf.readVarInt();
        s.claimLimit = buf.readVarInt();
        s.kingdomName = buf.readUtf();
        s.king = buf.readUtf();
        s.siegePrepMinutes = buf.readVarInt();
        s.siegeMinutes = buf.readVarInt();
        s.conquestCity = buf.readUtf();
        s.onlineResidents = readStrings(buf);
        s.candidates = readStrings(buf);
        s.cityInvites = readStrings(buf);
        s.cityJoinRequests = readStrings(buf);
        s.kingdomInvites = readStrings(buf);
        s.kingdomJoinRequests = readStrings(buf);
        s.cities = readRows(buf);
        s.kingdoms = readRows(buf);
        s.myCities = readRows(buf);
        s.kingdomCandidates = readRows(buf);
        s.proposals = readRows(buf);
        s.sieges = readRows(buf);
        s.sentProposals = readRows(buf);
        s.realmName = buf.readUtf();
        s.realmIsKingdom = buf.readBoolean();
        s.isLeader = buf.readBoolean();
        s.realmColor = buf.readInt();
        s.realms = readRows(buf);
        s.wars = readRows(buf);
        s.warMembers = readRows(buf);
        s.warCalls = readRows(buf);
        s.sentCalls = readRows(buf);
        s.warInfos = buf.readList(WarInfo::read);
        s.warStats = readRows(buf);
        s.skillLevel = buf.readVarIntArray();
        s.skillXp = buf.readVarIntArray();
        s.skillNeed = buf.readVarIntArray();
        s.citySkills = buf.readVarIntArray();
        s.techs = buf.readList(TechRow::read);
        s.canResearch = buf.readBoolean();
        s.researching = buf.readUtf();
        s.notices = buf.readList(NoticeRow::read);
        s.truces = readRows(buf);
        s.leaderboard = buf.readList(LeaderRow::read);
        return s;
    }
}
