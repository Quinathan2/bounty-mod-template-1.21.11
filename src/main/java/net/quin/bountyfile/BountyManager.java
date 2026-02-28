package net.quin.bountyfile;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BountyManager {

    public static class BountyData {
        public int amount;
        public String name;
        public UUID placer;      // null if automated
        public boolean anonymous;
        public long placedTime;

        public BountyData(int amount, String name, UUID placer, boolean anonymous) {
            this.amount = amount;
            this.name = name;
            this.placer = placer;
            this.anonymous = anonymous;
            this.placedTime = System.currentTimeMillis();
        }
    }

    private static final Map<UUID, BountyData> bounties = new HashMap<>();
    private static File dataFile;
    private static final Gson gson = new Gson();
    private static final Map<UUID, Integer> mostClaimed = new HashMap<>();      // number of bounties claimed
    private static final Map<UUID, Integer> highestClaimed = new HashMap<>();   // total diamonds earned
    private static final Map<UUID, Long> longestSurvived = new HashMap<>();     // time a bounty lasted
    // ----------------------------
    // Initialization / Saving
    // ----------------------------

    public static void init(File runDir) {
        dataFile = new File(runDir, "bounties.json");
        if (dataFile.exists()) {
            try (FileReader reader = new FileReader(dataFile)) {
                Map<String, BountyData> loaded = gson.fromJson(reader,
                        new TypeToken<Map<String, BountyData>>() {}.getType());
                loaded.forEach((k, v) -> bounties.put(UUID.fromString(k), v));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void save() {
        if (dataFile == null) return;
        Map<String, BountyData> toSave = new HashMap<>();
        for (Map.Entry<UUID, BountyData> entry : bounties.entrySet()) {
            toSave.put(entry.getKey().toString(), entry.getValue());
        }
        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(toSave, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ----------------------------
    // CRUD METHODS
    // ----------------------------
    public static BountyData getBountyData(UUID player) {
        return bounties.get(player); // returns null if no bounty exists
    }
    /** Set or replace a bounty for a player */
    public static void setBounty(UUID target, int amount, String name, UUID placer, boolean anonymous) {
        bounties.put(target, new BountyData(amount, name, placer, anonymous));
        save();
    }

    /** Edit existing bounty amount */
    public static void editBounty(UUID target, int newAmount) {
        BountyData data = bounties.get(target);
        if (data != null) {
            data.amount = newAmount;
            save();
        }
    }

    /** Get bounty amount for a player */
    public static int getBounty(UUID player) {
        BountyData data = bounties.get(player);
        return data != null ? data.amount : 0;
    }

    /** Check if a player has a bounty */
    public static boolean hasBounty(UUID target) {
        return bounties.containsKey(target);
    }

    /** Remove bounty (claim) */
    // When claiming a bounty, update stats
    public static BountyData claimBounty(UUID target) {
        BountyData data = bounties.remove(target);
        if (data != null) {
            // Update mostClaimed & highestClaimed for the killer (placer)
            if (data.placer != null) {
                mostClaimed.put(data.placer, mostClaimed.getOrDefault(data.placer, 0) + 1);
                highestClaimed.put(data.placer, highestClaimed.getOrDefault(data.placer, 0) + data.amount);
            }

            // Update longest survived
            long survivedTime = System.currentTimeMillis() - data.placedTime;
            longestSurvived.put(target, Math.max(longestSurvived.getOrDefault(target, 0L), survivedTime));
        }
        save();
        return data;
    }

    /** Get placer UUID */
    public static UUID getPlacer(UUID player) {
        BountyData data = bounties.get(player);
        return data != null ? data.placer : null;
    }

    /** Get bounty name */
    public static String getName(UUID player) {
        BountyData data = bounties.get(player);
        return data != null ? data.name : null;
    }
    /** Get all bounties as a string for display */
    public static String getAllBountiesString() {
        if (bounties.isEmpty()) return "No active bounties.";
        StringBuilder sb = new StringBuilder();
        bounties.forEach((uuid, data) -> {
            sb.append(data.name) // will now correctly show "Server" for automated, "Anonymous" for anonymous player
                    .append(": ")
                    .append(data.amount)
                    .append(" diamonds\n");
        });
        return sb.toString();
    }

    /** Get top bounty string (optional) */
    public static String getTopBountiesString() {
        if (bounties.isEmpty()) return "No active bounties.";
        return bounties.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().amount, a.getValue().amount))
                .map(e -> e.getValue().name + ": " + e.getValue().amount + " diamonds")
                .reduce((a, b) -> a + "\n" + b)
                .orElse("No active bounties.");
    }
    public static Map<UUID, Integer> getMostClaimed() { return mostClaimed; }
    public static Map<UUID, Integer> getHighestClaimed() { return highestClaimed; }
    public static Map<UUID, Long> getLongestSurvived() { return longestSurvived; }
}