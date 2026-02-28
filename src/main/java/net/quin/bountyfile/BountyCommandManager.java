package net.quin.bountyfile;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.Item;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.command.argument.EntityArgumentType;
import java.util.UUID;

import static net.quin.bountyfile.AutomatedBountyManager.anonymous;

public class BountyCommandManager {

    public static final Item CURRENCY_ITEM = Items.DIAMOND;


    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(CommandManager.literal("bounty")

                    // /bounty <targetName> <amount> [anonymous]
                    .then(CommandManager.argument("targetName", StringArgumentType.string())
                            .suggests((ctx, builder) -> {
                                OfflinePlayerCache.getOptedInNames().forEach(builder::suggest);
                                return builder.buildFuture();
                            })
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                    // Default: not anonymous
                                    .executes(ctx -> {
                                        String targetName = StringArgumentType.getString(ctx, "targetName");
                                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                        return placeBounty(ctx.getSource(), targetName, amount, false);
                                    })

                                    // Optional "anonymous" literal
                                    .then(CommandManager.literal("anonymous")
                                            .executes(ctx -> {
                                                String targetName = StringArgumentType.getString(ctx, "targetName");
                                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                return placeBounty(ctx.getSource(), targetName, amount, true);
                                            })
                                    )
                            )
                    )

                    // /bounty list
                    .then(CommandManager.literal("list")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(() ->
                                        Text.literal(BountyManager.getAllBountiesString()), false);
                                return 1;
                            })
                    )

                    .then(CommandManager.literal("top")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();

                                // Top 3 by most claimed
                                String mostClaimedTop = BountyManager.getMostClaimed().entrySet().stream()
                                        .sorted((a,b) -> b.getValue() - a.getValue())
                                        .limit(3)
                                        .map(e -> Text.literal(OfflinePlayerCache.getNameByUuid(e.getKey())).formatted(Formatting.YELLOW)
                                                .append(Text.literal(": " + e.getValue() + " bounties").formatted(Formatting.GOLD))
                                                .getString())
                                        .reduce((a,b) -> a + "\n" + b)
                                        .orElse("No data");

                                // Top 3 by highest claimed
                                String highestClaimedTop = BountyManager.getHighestClaimed().entrySet().stream()
                                        .sorted((a,b) -> b.getValue() - a.getValue())
                                        .limit(3)
                                        .map(e -> Text.literal(OfflinePlayerCache.getNameByUuid(e.getKey())).formatted(Formatting.YELLOW)
                                                .append(Text.literal(": " + e.getValue() + " diamonds").formatted(Formatting.GOLD))
                                                .getString())
                                        .reduce((a,b) -> a + "\n" + b)
                                        .orElse("No data");

                                // Top 3 by longest survived
                                String longestSurvivedTop = BountyManager.getLongestSurvived().entrySet().stream()
                                        .sorted((a,b) -> Long.compare(b.getValue(), a.getValue()))
                                        .limit(3)
                                        .map(e -> {
                                            long totalSeconds = e.getValue() / 1000; // assuming value is in ms
                                            long hours = totalSeconds / 3600;
                                            long minutes = (totalSeconds % 3600) / 60;
                                            long seconds = totalSeconds % 60;
                                            String timeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds);

                                            return Text.literal(OfflinePlayerCache.getNameByUuid(e.getKey())).formatted(Formatting.YELLOW)
                                                    .append(Text.literal(": " + timeFormatted + " survived").formatted(Formatting.GOLD))
                                                    .getString();
                                        })
                                        .reduce((a,b) -> a + "\n" + b)
                                        .orElse("No data");

                                player.sendMessage(Text.literal("--- Bounty Leaderboard ---").formatted(Formatting.GREEN, Formatting.BOLD), false);
                                player.sendMessage(Text.literal("Most Bounties Claimed:\n").formatted(Formatting.GREEN, Formatting.BOLD)
                                        .append(Text.literal(mostClaimedTop)), false);
                                player.sendMessage(Text.literal("Highest Diamonds Claimed:\n").formatted(Formatting.GREEN, Formatting.BOLD)
                                        .append(Text.literal(highestClaimedTop)), false);
                                player.sendMessage(Text.literal("Longest Survived Bounties:\n").formatted(Formatting.GREEN, Formatting.BOLD)
                                        .append(Text.literal(longestSurvivedTop)), false);

                                return 1;
                            })
                    )

                    // /bounty info <player>
                    .then(CommandManager.literal("info")
                            .then(CommandManager.argument("target", StringArgumentType.string())
                                    .executes(ctx -> {
                                        String targetName = StringArgumentType.getString(ctx, "target");
                                        UUID targetUUID = OfflinePlayerCache.getUuidByName(targetName);
                                        if (targetUUID == null || !BountyManager.hasBounty(targetUUID)) {
                                            ctx.getSource().sendFeedback(() ->
                                                    Text.literal(targetName + " has no active bounty."), false);
                                            return 0;
                                        }
                                        int amount = BountyManager.getBounty(targetUUID);
                                        ctx.getSource().sendFeedback(() ->
                                                Text.literal(targetName + " has a bounty of " + amount + " diamonds"), false);
                                        return 1;
                                    })
                            )
                    )

                    // /bounty remove <player> (OP only)
                    .then(CommandManager.literal("remove")
                            .requires(source -> isOp(source))
                            .then(CommandManager.argument("target", StringArgumentType.string())
                                    .executes(ctx -> {
                                        String targetName = StringArgumentType.getString(ctx, "target");
                                        UUID targetUUID = OfflinePlayerCache.getUuidByName(targetName);
                                        if (targetUUID == null || !BountyManager.hasBounty(targetUUID)) {
                                            ctx.getSource().sendError(Text.literal(targetName + " has no active bounty."));
                                            return 0;
                                        }

                                        UUID placerUUID = BountyManager.getPlacer(targetUUID);
                                        ServerPlayerEntity placer = ctx.getSource().getServer().getPlayerManager().getPlayer(placerUUID);
                                        if (placer != null) {
                                            int amount = BountyManager.getBounty(targetUUID);
                                            placer.getInventory().insertStack(new ItemStack(CURRENCY_ITEM, amount));
                                        }

                                        BountyManager.claimBounty(targetUUID);
                                        ctx.getSource().sendFeedback(() ->
                                                Text.literal("Removed bounty from " + targetName), true);
                                        return 1;
                                    })
                            )
                    )


                    // /bounty edit <player> <amount> (OP only)
                    .then(CommandManager.literal("edit")
                            .requires(source -> isOp(source)) // OP only
                            .then(CommandManager.argument("target", StringArgumentType.string())
                                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                            .executes(ctx -> {
                                                String targetName = StringArgumentType.getString(ctx, "target");
                                                int newAmount = IntegerArgumentType.getInteger(ctx, "amount");
                                                UUID targetUUID = OfflinePlayerCache.getUuidByName(targetName);

                                                if (targetUUID == null || !BountyManager.hasBounty(targetUUID)) {
                                                    ctx.getSource().sendError(Text.literal(targetName + " has no active bounty."));
                                                    return 0;
                                                }

                                                // SINGLE bounty per player
                                                BountyManager.BountyData bounty = BountyManager.getBountyData(targetUUID);
                                                UUID placerUUID = bounty.placer;

                                                ServerPlayerEntity placer = null;
                                                if (placerUUID != null) {
                                                    placer = ctx.getSource().getServer().getPlayerManager().getPlayer(placerUUID);
                                                }

                                                int oldAmount = bounty.amount;

                                                if (placer != null) {
                                                    if (newAmount > oldAmount) {
                                                        int extra = newAmount - oldAmount;

                                                        // Withdraw from mailbox first
                                                        int withdrawn = BountyMailboxManager.withdrawForBounty(placer, extra);
                                                        if (withdrawn < extra) {
                                                            int remaining = extra - withdrawn;

                                                            // Withdraw remaining from inventory
                                                            if (BountyCommandManager.countDiamonds(placer) < remaining) {
                                                                // Refund withdrawn to mailbox
                                                                if (withdrawn > 0) BountyMailboxManager.deposit(placer.getUuid(), withdrawn);
                                                                ctx.getSource().sendError(Text.literal(
                                                                        "Placer doesn't have enough diamonds in mailbox + inventory to increase bounty!"
                                                                ));
                                                                return 0;
                                                            }
                                                            BountyCommandManager.removeDiamonds(placer, remaining);
                                                        }
                                                    } else if (newAmount < oldAmount) {
                                                        int refund = oldAmount - newAmount;
                                                        // Refund goes to mailbox
                                                        BountyMailboxManager.deposit(placer.getUuid(), refund);
                                                    }
                                                }

                                                // Apply the new amount
                                                bounty.amount = newAmount;

                                                // Save changes
                                                BountyManager.save(); // ensure this method is public

                                                ctx.getSource().sendFeedback(() ->
                                                        Text.literal("Updated bounty for " + targetName + " to " + newAmount + " diamonds"), true);
                                                return 1;
                                            })
                                    )
                            )
                    )

                    // /bounty mailbox
                    .then(CommandManager.literal("mailbox")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                ctx.getSource().sendFeedback(() -> Text.literal(BountyMailboxManager.listMailbox(player.getUuid())), false);
                                return 1;
                            })

                            // /bounty mailbox claim
                            .then(CommandManager.literal("claim")
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        BountyMailboxManager.claimAll(player);
                                        return 1;
                                    })
                            )

                            // /bounty mailbox transfer <player> <amount>
                            .then(CommandManager.literal("transfer")
                                    .then(CommandManager.argument("target", net.minecraft.command.argument.EntityArgumentType.player())
                                            .suggests((ctx, builder) -> {
                                                // Suggest only opted-in online players
                                                ctx.getSource().getServer().getPlayerManager().getPlayerList().stream()
                                                        .filter(p -> BountyOptInManager.hasOptedIn(p.getUuid()))
                                                        .map(p -> p.getName().getString())
                                                        .forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                                    .executes(ctx -> {
                                                        ServerPlayerEntity sender = ctx.getSource().getPlayer();
                                                        ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "target");
                                                        int amount = IntegerArgumentType.getInteger(ctx, "amount");

                                                        if (!BountyMailboxManager.transfer(sender, target, amount)) {
                                                            ctx.getSource().sendError(Text.literal("Not enough diamonds in your mailbox!"));
                                                            return 0;
                                                        }

                                                        // Only send feedback to the sender
                                                        ctx.getSource().sendFeedback(() ->
                                                                Text.literal("Transferred " + amount + " diamonds to " + target.getName().getString()), false);

                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                    )
                    // /bounty mailbox deposit <amount>
                    .then(CommandManager.literal("deposit")
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                        int inventory = BountyCommandManager.countDiamonds(player);

                                        if (inventory < amount) {
                                            ctx.getSource().sendError(Text.literal("You don't have enough diamonds in your inventory!"));
                                            return 0;
                                        }

                                        BountyCommandManager.removeDiamonds(player, amount);
                                        BountyMailboxManager.deposit(player.getUuid(), amount);

                                        ctx.getSource().sendFeedback(() ->
                                                Text.literal("Deposited " + amount + " diamonds into your mailbox. New balance: " +
                                                        BountyMailboxManager.getBalance(player.getUuid())), true);
                                        return 1;
                                    })
                            )
                    )

                    // /bounty optin
                    .then(CommandManager.literal("optin")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                BountyOptInManager.optIn(player);
                                return 1;
                            })
                    )

                    // /bounty optout
                    .then(CommandManager.literal("optout")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                BountyOptInManager.optOut(player);
                                return 1;
                            })
                    )
                    // bounty status
                    .then(CommandManager.literal("status")
                            .executes(ctx -> {
                                var server = ctx.getSource().getServer();
                                ctx.getSource().sendFeedback(
                                        () -> BountyOptInManager.getFullStatusText(server),
                                        false
                                );
                                return 1;
                            })
                    )

                    //bounty cancel
                    .then(CommandManager.literal("cancel")
                            .then(CommandManager.argument("target", StringArgumentType.string())
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        String targetName = StringArgumentType.getString(ctx, "target");
                                        UUID targetUUID = OfflinePlayerCache.getUuidByName(targetName);

                                        if (targetUUID == null || !BountyManager.hasBounty(targetUUID)) {
                                            ctx.getSource().sendError(Text.literal("No active bounty on " + targetName));
                                            return 0;
                                        }

                                        UUID placerUUID = BountyManager.getPlacer(targetUUID);

                                        // Only the original placer can cancel
                                        if (!player.getUuid().equals(placerUUID)) {
                                            ctx.getSource().sendError(Text.literal("You did not place this bounty!"));
                                            return 0;
                                        }

                                        int amount = BountyManager.getBounty(targetUUID);

                                        // Refund to mailbox instead of inventory
                                        BountyMailboxManager.deposit(player.getUuid(), amount);

                                        // Remove bounty
                                        BountyManager.claimBounty(targetUUID);

                                        ctx.getSource().sendFeedback(() ->
                                                        Text.literal("Canceled bounty on " + targetName +
                                                                ". " + amount + " diamonds sent to your mailbox."),
                                                true);

                                        return 1;
                                    })
                            )
                    )
                    // /bounty auto interval <minutes>
                    .then(CommandManager.literal("auto")
                            .requires(source -> isOp(source))// OP only
                            .then(CommandManager.literal("interval")
                                    .then(CommandManager.argument("minutes", IntegerArgumentType.integer(1))
                                            .executes(ctx -> {
                                                int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                                                AutomatedBountyManager.setInterval(minutes);
                                                ctx.getSource().sendFeedback(() ->
                                                                Text.literal("Automated bounty interval set to " + minutes + " min."),
                                                        true);
                                                return 1;
                                            })
                                    )
                            )
                            // /bounty auto amount <min> <max>
                            .then(CommandManager.literal("amount")
                                    .then(CommandManager.argument("min", IntegerArgumentType.integer(1))
                                            .then(CommandManager.argument("max", IntegerArgumentType.integer(1))
                                                    .executes(ctx -> {
                                                        int min = IntegerArgumentType.getInteger(ctx, "min");
                                                        int max = IntegerArgumentType.getInteger(ctx, "max");
                                                        AutomatedBountyManager.setMinMax(min, max);
                                                        ctx.getSource().sendFeedback(() ->
                                                                        Text.literal("Automated bounty amount set: " + min + "–" + max + " diamonds."),
                                                                true);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                            // /bounty auto anonymous <true/false>
                            .then(CommandManager.literal("anonymous")
                                    .then(CommandManager.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                            .executes(ctx -> {
                                                boolean val = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "value");
                                                AutomatedBountyManager.setAnonymous(val);
                                                ctx.getSource().sendFeedback(() ->
                                                                Text.literal("Automated bounty anonymous set to: " + val),
                                                        true);
                                                return 1;
                                            })
                                    )
                            )
                    )



                    // /bounty help
                    .then(CommandManager.literal("help")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();

                                // Header
                                player.sendMessage(Text.literal("--- Bounty Commands ---").formatted(Formatting.YELLOW), false);

                                // Command + description messages
                                Text[] helpMessages = new Text[] {
                                        Text.literal("/bounty <player> <amount>").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — Place a bounty on a player (online or offline if opted in).").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty list").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — Show all active bounties.").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty top").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — Show top bounties.").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty info <player>").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — Show bounty info for a player.").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty remove <player>").formatted(Formatting.RED)
                                                .append(Text.literal(" — OP only: Remove a bounty.").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty edit <player> <amount>").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — OP only: Edit a bounty amount.").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty mailbox").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — View your bounty mailbox.").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty mailbox claim").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — Claim all bounties in mailbox.").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty mailbox transfer <player> <amount>").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — Transfer diamonds from your mailbox to another player.").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty deposit <amount>").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — Deposit diamonds into your bounty mailbox.").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty optin").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — Opt in to receive bounties.").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty optout").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — Opt out of receiving bounties.").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty cancel <player>").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — Cancel a bounty you placed and refund it to your mailbox.").formatted(Formatting.WHITE)),
                                        Text.literal("/bounty help").formatted(Formatting.GREEN)
                                                .append(Text.literal(" — Show this help message.").formatted(Formatting.WHITE))
                                };

                                // Send all help messages
                                for (Text msg : helpMessages) {
                                    player.sendMessage(msg, false);
                                }

                                return 1;
                            })
                    )
            );
        });
    }

    // -------------------------------
    // PRIVATE HELPERS
    // -------------------------------

    private static int placeBounty(ServerCommandSource source, String targetName, int amount, boolean anonymous) {
        try {
            MinecraftServer server = source.getServer();
            ServerPlayerEntity placer = source.getPlayer();
            UUID targetUUID = OfflinePlayerCache.getUuidByName(targetName);

            if (targetUUID == null) {
                source.sendError(Text.literal("Player not found or has never joined the server!"));
                return 0;
            }

            if (!BountyOptInManager.hasOptedIn(targetUUID)) {
                source.sendError(Text.literal(targetName + " has opted out of bounties."));
                return 0;
            }

            if (placer.getUuid().equals(targetUUID)) {
                source.sendError(Text.literal("You cannot place a bounty on yourself!"));
                return 0;
            }

            // Withdraw from mailbox first
            int withdrawn = BountyMailboxManager.withdrawForBounty(placer, amount);
            int remaining = amount - withdrawn;
            if (remaining > 0) {
                if (countDiamonds(placer) < remaining) {
                    source.sendError(Text.literal("Not enough diamonds in mailbox + inventory!"));
                    // Refund mailbox withdrawal
                    if (withdrawn > 0) BountyMailboxManager.deposit(placer.getUuid(), withdrawn);
                    return 0;
                }
                removeDiamonds(placer, remaining);
            }

            // Determine display name for chat/title
            String displayName = anonymous ? "Anonymous" : placer.getName().getString();

            // Store bounty
            BountyManager.setBounty(targetUUID, amount, targetName, anonymous ? null : placer.getUuid(), anonymous);

            // Broadcast to all players
            server.getPlayerManager().broadcast(
                    Text.literal(displayName + " placed a bounty of " + amount + " ♦ on " + targetName + "!")
                            .formatted(Formatting.GREEN),
                    false
            );

            // Send title to all players
            Text title = Text.literal("NEW BOUNTY: " + amount + " ♦ on " + targetName)
                    .formatted(Formatting.RED, Formatting.BOLD);
            Text subtitle = Text.literal("Placed by " + displayName)
                    .formatted(Formatting.GOLD);

            server.getPlayerManager().getPlayerList().forEach(player ->
                    player.networkHandler.sendPacket(new TitleS2CPacket(title))
            );

            // Title to placer for confirmation
            placer.networkHandler.sendPacket(new TitleS2CPacket(
                    Text.literal("BOUNTY PLACED: " + amount + " ♦").formatted(Formatting.GREEN, Formatting.BOLD)
            ));

            source.sendFeedback(() -> Text.literal("Bounty successfully placed on " + targetName), true);
            return 1;

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static boolean isOp(ServerCommandSource source) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            return source.getServer().getPlayerManager().isOperator(player.getPlayerConfigEntry());
        }
        return false;
    }

    private static int countDiamonds(ServerPlayerEntity player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).getItem() == Items.DIAMOND) total += player.getInventory().getStack(i).getCount();
        }
        return total;
    }

    private static void removeDiamonds(ServerPlayerEntity player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().size(); i++) {
            var stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.DIAMOND) {
                int remove = Math.min(stack.getCount(), remaining);
                stack.decrement(remove);
                remaining -= remove;
                if (remaining <= 0) break;
            }
        }
    }

    // -------------------------
// RANDOM AUTOMATED BOUNTY
// -------------------------
    public static void placeRandomBountyServer(MinecraftServer server, int minAmount, int maxAmount, boolean anonymous) {
        // Get UUIDs of all opted-in players
        var optedInUUIDs = OfflinePlayerCache.getOptedInNames().stream()
                .map(OfflinePlayerCache::getUuidByName)
                .filter(uuid -> uuid != null)
                .toList();

        if (optedInUUIDs.isEmpty()) return;

        // Pick a random target
        UUID targetUUID = optedInUUIDs.get(new java.util.Random().nextInt(optedInUUIDs.size()));
        String targetName = OfflinePlayerCache.getNameByUuid(targetUUID);

        // Choose a random bounty amount
        int amount = minAmount + new java.util.Random().nextInt(maxAmount - minAmount + 1);

        // Determine display name
        String displayName;
        if (anonymous) {
            displayName = "Anonymous";  // Player chose to be anonymous
        } else {
            displayName = "Server";     // Automated bounty
        }

// Store bounty
        BountyManager.setBounty(targetUUID, amount, displayName, null, anonymous);

        // Broadcast to players
        String placerName = anonymous ? "An anonymous player" : "Server";
        var broadcastText = net.minecraft.text.Text.literal(placerName).formatted(net.minecraft.util.Formatting.GREEN)
                .append(net.minecraft.text.Text.literal(" placed a bounty of "))
                .append(net.minecraft.text.Text.literal(amount + " ♦").formatted(net.minecraft.util.Formatting.GOLD))
                .append(net.minecraft.text.Text.literal(" on "))
                .append(net.minecraft.text.Text.literal(targetName).formatted(net.minecraft.util.Formatting.RED))
                .append(net.minecraft.text.Text.literal("!"));

        server.getPlayerManager().broadcast(broadcastText, false);

        // Title + sound
        server.getPlayerManager().getPlayerList().forEach(player -> {
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                    net.minecraft.text.Text.literal("NEW BOUNTY: " + amount + " ♦ on " + targetName)
                            .formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD)
            ));

            player.playSound(net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        });
    }

}
