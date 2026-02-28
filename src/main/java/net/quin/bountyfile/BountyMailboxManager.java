package net.quin.bountyfile;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import java.util.*;

public class BountyMailboxManager {

    private static final Map<UUID, Integer> mailbox = new HashMap<>();

    /** Add diamonds to a player's mailbox */
    public static void deposit(UUID playerUUID, int amount) {
        mailbox.put(playerUUID, mailbox.getOrDefault(playerUUID, 0) + amount);
    }

    /** Add diamonds from a bounty (like a "mailbox deposit") */
    public static void addToMailbox(UUID playerUUID, int amount) {
        deposit(playerUUID, amount);
    }

    /** Withdraw diamonds from mailbox into inventory */
    public static boolean withdraw(ServerPlayerEntity player, int amount) {
        int balance = mailbox.getOrDefault(player.getUuid(), 0);
        if (balance < amount) return false;

        mailbox.put(player.getUuid(), balance - amount);
        player.getInventory().insertStack(new ItemStack(BountyMod.CURRENCY_ITEM, amount));
        return true;
    }

    /** Withdraw as much as possible up to amount for bounty placement */
    public static int withdrawForBounty(ServerPlayerEntity player, int amount) {
        UUID uuid = player.getUuid();
        int withdrawn = 0;

        // Take from mailbox first
        int mailboxBalance = mailbox.getOrDefault(uuid, 0);
        if (mailboxBalance > 0) {
            int fromMailbox = Math.min(mailboxBalance, amount);
            mailbox.put(uuid, mailboxBalance - fromMailbox);
            withdrawn += fromMailbox;
            amount -= fromMailbox;
        }

        // Take remaining from inventory
        if (amount > 0) {
            int remaining = amount;
            for (int i = 0; i < player.getInventory().size(); i++) {
                var stack = player.getInventory().getStack(i);
                if (stack.getItem() == BountyMod.CURRENCY_ITEM) {
                    int take = Math.min(stack.getCount(), remaining);
                    stack.decrement(take);
                    withdrawn += take;
                    remaining -= take;
                    if (remaining <= 0) break;
                }
            }
        }

        return withdrawn;
    }

    /** Get balance */
    public static int getBalance(UUID playerUUID) {
        return mailbox.getOrDefault(playerUUID, 0);
    }

    /** List mailbox contents (balance) */
    public static String listMailbox(UUID playerUUID) {
        int balance = getBalance(playerUUID);
        return "Mailbox Balance: " + balance + " diamonds";
    }

    /** Claim all diamonds from mailbox */
    public static void claimAll(ServerPlayerEntity player) {
        int balance = mailbox.getOrDefault(player.getUuid(), 0);
        if (balance <= 0) {
            player.sendMessage(Text.literal("Your mailbox is empty!"), false);
            return;
        }
        mailbox.remove(player.getUuid());
        player.getInventory().insertStack(new ItemStack(BountyMod.CURRENCY_ITEM, balance));
        player.sendMessage(Text.literal("You claimed " + balance + " diamonds from your mailbox!"), false);
    }

    /** Transfer diamonds from your mailbox to another player's mailbox */
    public static boolean transfer(ServerPlayerEntity sender, ServerPlayerEntity target, int amount) {
        int senderBalance = mailbox.getOrDefault(sender.getUuid(), 0);
        if (senderBalance < amount) return false;

        // Deduct from sender
        mailbox.put(sender.getUuid(), senderBalance - amount);

        // Add to target
        deposit(target.getUuid(), amount);

        // Send messages
        sender.sendMessage(Text.literal("Transferred " + amount + " diamonds to " + target.getName().getString()), false);
        target.sendMessage(Text.literal("You received " + amount + " diamonds from " + sender.getName().getString()), false);

        return true;
    }
}