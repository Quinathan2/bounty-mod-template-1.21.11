package net.quin.bountyfile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.*;

public class bountymanager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File SAVE_FILE;

    // Stores UUID -> bounty amount
    private static final Map<UUID, Integer> BOUNTIES = new HashMap<>();

    // Stores UUID -> last known player name
    private static final Map<UUID, String> PLAYER_NAMES = new HashMap<>();

    // =========================
    // SAVE DATA WRAPPER
    // =========================
    private static class BountyData {
        Map<String, Integer> bounties = new HashMap<>();
        Map<String, String> playerNames = new HashMap<>();
    }

    // =========================
    // INIT
    // =========================
    public static void init(File worldFolder) {
        SAVE_FILE = new File(worldFolder, "data/bounties.json");
        if (!SAVE_FILE.getParentFile().exists()) SAVE_FILE.getParentFile().mkdirs();
        load();
    }

    // =========================
    // BASIC BOUNTY METHODS
    // =========================
    public static void setBounty(UUID uuid, int amount, String playerName) {
        BOUNTIES.put(uuid, amount);
        PLAYER_NAMES.put(uuid, playerName);
        save();
    }

    public static void removeBounty(UUID uuid) {
        BOUNTIES.remove(uuid);
        PLAYER_NAMES.remove(uuid);
        save();
    }

    public static void editBounty(UUID uuid, int newAmount) {
        if (BOUNTIES.containsKey(uuid)) {
            BOUNTIES.put(uuid, newAmount);
            save();
        }
    }

    public static boolean hasBounty(UUID uuid) {
        return BOUNTIES.containsKey(uuid);
    }

    public static int getBounty(UUID uuid) {
        return BOUNTIES.getOrDefault(uuid, 0);
    }

    public static String getPlayerName(UUID uuid) {
        return PLAYER_NAMES.getOrDefault(uuid, "Unknown");
    }

    // =========================
    // GETTERS FOR GUI
    // =========================
    public static Map<UUID, Integer> getBounties() {
        return Collections.unmodifiableMap(BOUNTIES);
    }

    public static Map<UUID, String> getPlayerNames() {
        return Collections.unmodifiableMap(PLAYER_NAMES);
    }

    // =========================
    // DISPLAY METHODS
    // =========================
    public static String getAllBountiesString() {
        if (BOUNTIES.isEmpty()) return "There are currently no active bounties.";

        StringBuilder builder = new StringBuilder("Active Bounties:\n");
        for (UUID uuid : BOUNTIES.keySet()) {
            builder.append("- ")
                    .append(getPlayerName(uuid))
                    .append(": ")
                    .append(BOUNTIES.get(uuid))
                    .append(" diamonds\n");
        }
        return builder.toString();
    }

    public static String getTopBountiesString() {
        if (BOUNTIES.isEmpty()) return "There are currently no active bounties.";

        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(BOUNTIES.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue())); // descending

        StringBuilder builder = new StringBuilder("Top Bounties:\n");
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            if (rank > 10) break;
            UUID uuid = entry.getKey();
            builder.append(rank)
                    .append(". ")
                    .append(getPlayerName(uuid))
                    .append(" - ")
                    .append(entry.getValue())
                    .append(" diamonds\n");
            rank++;
        }
        return builder.toString();
    }

    // =========================
    // SAVE / LOAD
    // =========================
    private static void save() {
        if (SAVE_FILE == null) return;

        try (Writer writer = new FileWriter(SAVE_FILE)) {
            BountyData data = new BountyData();
            BOUNTIES.forEach((uuid, amt) -> data.bounties.put(uuid.toString(), amt));
            PLAYER_NAMES.forEach((uuid, name) -> data.playerNames.put(uuid.toString(), name));
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void load() {
        if (SAVE_FILE == null || !SAVE_FILE.exists()) return;

        try (Reader reader = new FileReader(SAVE_FILE)) {
            BountyData data = GSON.fromJson(reader, BountyData.class);
            if (data != null) {
                BOUNTIES.clear();
                PLAYER_NAMES.clear();
                data.bounties.forEach((k, v) -> BOUNTIES.put(UUID.fromString(k), v));
                data.playerNames.forEach((k, v) -> PLAYER_NAMES.put(UUID.fromString(k), v));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}