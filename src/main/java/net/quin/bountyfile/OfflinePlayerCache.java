package net.quin.bountyfile;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OfflinePlayerCache {

    // Maps lowercase player names -> UUID
    private static final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();

    // Maps UUID -> exact player name
    private static final Map<UUID, String> uuidToName = new HashMap<>();

    /** Called when a player joins; caches their UUID and name */
    public static void onPlayerJoin(ServerPlayerEntity player) {
        String name = player.getName().getString();
        nameToUuid.put(name.toLowerCase(), player.getUuid());
        uuidToName.put(player.getUuid(), name);
    }

    /** Manually add a player (e.g., for opted-in offline players) */
    public static void addPlayer(UUID uuid, String name) {
        uuidToName.put(uuid, name);
        nameToUuid.put(name.toLowerCase(), uuid);
    }

    /** Get UUID by player name (case-insensitive) */
    public static UUID getUuidByName(String name) {
        return uuidToName.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(name))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /** Get cached name by UUID */
    public static String getNameByUuid(UUID uuid) {
        return uuidToName.get(uuid);
    }

    /** Get all opted-in player names (for tab completion) */
    public static List<String> getOptedInNames() {
        return uuidToName.entrySet().stream()
                .filter(e -> BountyOptInManager.hasOptedIn(e.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /** Register event listeners */
    public static void register() {
        // Player entity load (joins server)
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ServerPlayerEntity player) {
                onPlayerJoin(player);
            }
        });

        // Optional: Also hook into JOIN for immediate caching
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            onPlayerJoin(player);

            // Ensure opted-in players are cached for tab completion
            if (BountyOptInManager.hasOptedIn(player.getUuid())) {
                addPlayer(player.getUuid(), player.getName().getString());
            }
        });
    }
}