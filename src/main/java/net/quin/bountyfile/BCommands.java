package net.quin.bountyfile;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;

import static net.quin.bountyfile.BountyMod.CURRENCY_ITEM;

public class BCommands {

    // -------------------- Helper --------------------

    public static boolean isOp(ServerCommandSource source) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            return source.getServer().getPlayerManager().isOperator(player.getPlayerConfigEntry());
        }
        return false;
    }

    // -------------------- Commands --------------------

    // /bounty <player> <amount>
    public static int setBounty(CommandContext<ServerCommandSource> ctx) throws Exception {
        ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "target");
        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");
        ServerPlayerEntity sender = ctx.getSource().getPlayer();

        if (sender.getUuid().equals(target.getUuid())) {
            sender.sendMessage(Text.literal("You cannot place a bounty on yourself!"), false);
            return 0;
        }

        // Count diamonds in sender's inventory
        int totalDiamonds = 0;
        for (int i = 0; i < sender.getInventory().size(); i++) {
            ItemStack stack = sender.getInventory().getStack(i);
            if (stack.getItem() == CURRENCY_ITEM) totalDiamonds += stack.getCount();
        }

        if (totalDiamonds < amount) {
            ctx.getSource().sendError(Text.literal("You don't have enough diamonds!"));
            return 0;
        }

        // Remove diamonds
        int remaining = amount;
        for (int i = 0; i < sender.getInventory().size(); i++) {
            ItemStack stack = sender.getInventory().getStack(i);
            if (stack.getItem() == CURRENCY_ITEM) {
                int remove = Math.min(stack.getCount(), remaining);
                stack.decrement(remove);
                remaining -= remove;
                if (remaining <= 0) break;
            }
        }

        bountymanager.setBounty(target.getUuid(), amount, target.getName().getString());

        ctx.getSource().sendFeedback(() ->
                Text.literal("Bounty set on " + target.getName().getString() + " for " + amount + " diamonds!"), true);

        ctx.getSource().getServer().getPlayerManager().broadcast(
                Text.literal("A bounty of " + amount + " diamonds has been placed on " + target.getName().getString() + "!"),
                false
        );

        return 1;
    }

    // /bounty list
    public static int listBounties(CommandContext<ServerCommandSource> ctx) {
        String message = bountymanager.getAllBountiesString();
        ctx.getSource().sendFeedback(() -> Text.literal(message), false);
        return 1;
    }

    // /bounty remove <player> (OP only)
    public static int removeBounty(CommandContext<ServerCommandSource> ctx) throws Exception {
        ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "target");

        if (!bountymanager.hasBounty(target.getUuid())) {
            ctx.getSource().sendError(Text.literal(target.getName().getString() + " has no active bounty."));
            return 0;
        }

        bountymanager.removeBounty(target.getUuid());
        ctx.getSource().sendFeedback(() ->
                Text.literal("Removed bounty from " + target.getName().getString()), true);
        return 1;
    }

    // /bounty edit <player> <amount> (OP only)
    public static int editBounty(CommandContext<ServerCommandSource> ctx) throws Exception {
        ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "target");
        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");

        if (!bountymanager.hasBounty(target.getUuid())) {
            ctx.getSource().sendError(Text.literal(target.getName().getString() + " has no active bounty."));
            return 0;
        }

        bountymanager.editBounty(target.getUuid(), amount);
        ctx.getSource().sendFeedback(() ->
                Text.literal("Updated bounty for " + target.getName().getString() + " to " + amount + " diamonds"), true);
        return 1;
    }

    // /bounty claim <player> (OP only)
    public static int claimBounty(CommandContext<ServerCommandSource> ctx) throws Exception {
        ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "target");

        if (!bountymanager.hasBounty(target.getUuid())) {
            ctx.getSource().sendError(Text.literal(target.getName().getString() + " has no active bounty."));
            return 0;
        }

        int amount = bountymanager.getBounty(target.getUuid());
        ServerPlayerEntity sender = ctx.getSource().getPlayer();
        sender.getInventory().insertStack(new ItemStack(CURRENCY_ITEM, amount));

        bountymanager.removeBounty(target.getUuid());
        ctx.getSource().sendFeedback(() ->
                Text.literal("Manually claimed " + amount + " diamonds from bounty on " + target.getName().getString()), true);

        return 1;
    }

    // /bounty top
    public static int topBounties(CommandContext<ServerCommandSource> ctx) {
        String message = bountymanager.getTopBountiesString();
        ctx.getSource().sendFeedback(() -> Text.literal(message), false);
        return 1;
    }

    // /bounty info <player>
    public static int infoBounty(CommandContext<ServerCommandSource> ctx) throws Exception {
        ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "target");

        if (!bountymanager.hasBounty(target.getUuid())) {
            ctx.getSource().sendFeedback(() ->
                    Text.literal(target.getName().getString() + " has no active bounty."), false);
            return 0;
        }

        int amount = bountymanager.getBounty(target.getUuid());
        ctx.getSource().sendFeedback(() ->
                Text.literal(target.getName().getString() + " has a bounty of " + amount + " diamonds"), false);

        return 1;
    }

    // /bounty help
    public static int help(CommandContext<ServerCommandSource> ctx) {
        StringBuilder helpMessage = new StringBuilder("Bounty Commands:\n");
        helpMessage.append("/bounty <player> <amount> - Set a bounty (costs diamonds)\n");
        helpMessage.append("/bounty list - List all active bounties\n");
        helpMessage.append("/bounty info <player> - Show bounty on a specific player\n");
        helpMessage.append("/bounty top - Show top 10 bounties\n");
        helpMessage.append("/bounty remove <player> - Remove a bounty (OP only)\n");
        helpMessage.append("/bounty edit <player> <amount> - Edit bounty amount (OP only)\n");
        helpMessage.append("/bounty claim <player> - Claim bounty manually (OP only)\n");
        helpMessage.append("/bounty gui - Open the bounty GUI\n");
        helpMessage.append("/bounty help - Show this help message\n");

        ctx.getSource().sendFeedback(() -> Text.literal(helpMessage.toString()), false);
        return 1;
    }
}