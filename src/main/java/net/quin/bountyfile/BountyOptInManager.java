package net.quin.bountyfile;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BountyOptInManager {

    private static final Set<UUID> optedInPlayers = new HashSet<>();
    private static final Set<UUID> joinedPlayers = new HashSet<>();

    /** Register join events for caching */
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();

            // Track that the player has joined
            if (!hasJoinedBefore(player.getUuid())) {
                addJoined(player.getUuid());
            }

            // If the player is opted in, ensure they are in OfflinePlayerCache for tab completion
            if (hasOptedIn(player.getUuid())) {
                OfflinePlayerCache.addPlayer(player.getUuid(), player.getName().getString());
            }
        });
    }

    /** Player opts in */
    public static void optIn(ServerPlayerEntity player) {
        optedInPlayers.add(player.getUuid());
        joinedPlayers.add(player.getUuid());

        // Add to OfflinePlayerCache for tab completion
        OfflinePlayerCache.addPlayer(player.getUuid(), player.getName().getString());

        player.sendMessage(Text.literal("You have opted in to receive bounties!")
                .formatted(Formatting.GREEN), false);
    }

    /** Player opts out */
    public static void optOut(ServerPlayerEntity player) {
        optedInPlayers.remove(player.getUuid());
        player.sendMessage(Text.literal("You have opted out of receiving bounties.")
                .formatted(Formatting.RED), false);
    }

    /** Get full status text for /bounty status */
    public static Text getFullStatusText(MinecraftServer server) {
        Text result = Text.literal("=== Bounty Opt Status (All Players) ===\n")
                .formatted(Formatting.GOLD);

        // Online players first
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Text name = Text.literal(player.getName().getString()).formatted(Formatting.YELLOW);
            Text status = hasOptedIn(player.getUuid())
                    ? Text.literal(" OPTED IN\n").formatted(Formatting.GREEN)
                    : Text.literal(" OPTED OUT\n").formatted(Formatting.RED);

            result = result.copy().append(name).append(status);
        }

        // Offline players who joined before
        for (UUID uuid : joinedPlayers) {
            boolean isOnline = server.getPlayerManager().getPlayer(uuid) != null;
            if (isOnline) continue; // already listed

            Text name = Text.literal(uuid.toString()).formatted(Formatting.GRAY);
            Text status = hasOptedIn(uuid)
                    ? Text.literal(" OPTED IN\n").formatted(Formatting.GREEN)
                    : Text.literal(" OPTED OUT\n").formatted(Formatting.RED);

            result = result.copy().append(name).append(status);
        }

        return result;
    }

    /** Check if a player has opted in */
    public static boolean hasOptedIn(UUID uuid) {
        return optedInPlayers.contains(uuid);
    }

    /** Check if a player has joined before */
    public static boolean hasJoinedBefore(UUID uuid) {
        return joinedPlayers.contains(uuid);
    }

    /** Add a player to the joined set manually (used for offline bounties) */
    public static void addJoined(UUID uuid) {
        joinedPlayers.add(uuid);
    }
}