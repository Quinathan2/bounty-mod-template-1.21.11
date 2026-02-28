package net.quin.bountyfile;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public class BountyDeathHandler {

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity killer)) return;

            UUID victimUUID = victim.getUuid();
            UUID killerUUID = killer.getUuid();

            if (victimUUID.equals(killerUUID)) return; // can't kill self
            if (!BountyOptInManager.hasOptedIn(victimUUID) || !BountyOptInManager.hasOptedIn(killerUUID)) return;
            if (!BountyManager.hasBounty(victimUUID)) return;

            // Claim all bounties
            BountyManager.BountyData claimed = BountyManager.claimBounty(victimUUID);
            if (claimed == null) return;
            int totalAmount = claimed.amount;


            if (totalAmount <= 0) return;

            // Add to mailbox
            BountyMailboxManager.addToMailbox(killerUUID, totalAmount);

            // Message to killer
            killer.sendMessage(Text.literal("You received " + totalAmount + " diamonds! Check your /bounty mailbox.").formatted(Formatting.GREEN), false);

            // Broadcast
            ServerWorld world = (ServerWorld) victim.getEntityWorld();
            world.getServer().getPlayerManager().broadcast(
                    Text.literal(killer.getName().getString()).formatted(Formatting.GREEN)
                            .append(Text.literal(" claimed a bounty of "))
                            .append(Text.literal(totalAmount + " diamonds").formatted(Formatting.GOLD))
                            .append(Text.literal(" for killing "))
                            .append(Text.literal(victim.getName().getString()).formatted(Formatting.RED)),
                    false
            );

            // Sound
            world.playSound(null, killer.getX(), killer.getY(), killer.getZ(),
                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1f);
        });
    }

}