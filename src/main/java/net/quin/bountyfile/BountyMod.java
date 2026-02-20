package net.quin.bountyfile;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.quin.bountyfile.gui.BountyScreenHandler;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.quin.bountyfile.gui.BountyScreen;

public class BountyMod implements ModInitializer {
	public static final String MOD_ID = "bounty-mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ScreenHandlerType<BountyScreenHandler> BOUNTY_SCREEN_HANDLER =
			new ExtendedScreenHandlerType<>(BountyScreenHandler::new);



	public static final Item CURRENCY_ITEM = Items.DIAMOND;

	@Override
	public void onInitialize() {



		// Initialize bounties
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			bountymanager.init(server.getRunDirectory().toFile());
		});

		// Register commands somewhere in server init
		// BountyCommand.register(dispatcher);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			bountymanager.init(server.getRunDirectory().toFile());
		});

		// Register /bounty commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			dispatcher.register(CommandManager.literal("bounty")
					// /bounty <player> <amount> → costs diamonds
					.then(CommandManager.argument("target", EntityArgumentType.player())
							.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
									.executes(context -> {
										ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
										int amount = IntegerArgumentType.getInteger(context, "amount");
										ServerPlayerEntity sender = context.getSource().getPlayer();

										// Count diamonds in sender's inventory
										int totalDiamonds = 0;
										for (int i = 0; i < sender.getInventory().size(); i++) {
											ItemStack stack = sender.getInventory().getStack(i);
											if (stack.getItem() == CURRENCY_ITEM) totalDiamonds += stack.getCount();
										}

										if (totalDiamonds < amount) {
											context.getSource().sendError(Text.literal("You don't have enough diamonds!"));
											return 0;
										}

										ServerPlayerEntity placer = context.getSource().getPlayer();

										if (placer.getUuid().equals(target.getUuid())) {
											placer.sendMessage(Text.literal("You cannot place a bounty on yourself!"), false);
											return 0;
										}


										// Remove diamonds from inventory
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

										// Set the bounty
										bountymanager.setBounty(target.getUuid(), amount, target.getName().getString());

										context.getSource().sendFeedback(
												() -> Text.literal("Bounty set on " + target.getName().getString() + " for " + amount + " diamonds!"),
												true
										);

										context.getSource().getServer().getPlayerManager().broadcast(
												Text.literal("A bounty of " + amount + " diamonds has been placed on " + target.getName().getString() + "!"),
												false
										);

										return 1;
									})
							)
					)

					// /bounty list
					.then(CommandManager.literal("list")
							.executes(context -> {
								String message = bountymanager.getAllBountiesString();
								context.getSource().sendFeedback(() -> Text.literal(message), false);
								return 1;
							})
					)

					// /bounty remove <player> (OP only)
					.then(CommandManager.literal("remove")
							.requires(source -> {
								if (source.getEntity() instanceof ServerPlayerEntity player) {
									return source.getServer().getPlayerManager().isOperator(player.getPlayerConfigEntry());
								}
								return false;
							})
							.then(CommandManager.argument("target", EntityArgumentType.player())
									.executes(context -> {
										ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");

										if (!bountymanager.hasBounty(target.getUuid())) {
											context.getSource().sendError(Text.literal(target.getName().getString() + " has no active bounty."));
											return 0;
										}

										bountymanager.removeBounty(target.getUuid());
										context.getSource().sendFeedback(
												() -> Text.literal("Removed bounty from " + target.getName().getString()),
												true
										);
										return 1;
									})
							)
					)

					// /bounty edit <player> <amount> (OP only)
					.then(CommandManager.literal("edit")
							.requires(source -> {
								if (source.getEntity() instanceof ServerPlayerEntity player) {
									return source.getServer().getPlayerManager().isOperator(player.getPlayerConfigEntry());
								}
								return false;
							})
							.then(CommandManager.argument("target", EntityArgumentType.player())
									.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
											.executes(context -> {
												ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
												int amount = IntegerArgumentType.getInteger(context, "amount");

												if (!bountymanager.hasBounty(target.getUuid())) {
													context.getSource().sendError(Text.literal(target.getName().getString() + " has no active bounty."));
													return 0;
												}

												bountymanager.editBounty(target.getUuid(), amount);

												context.getSource().sendFeedback(
														() -> Text.literal("Updated bounty for " + target.getName().getString() + " to " + amount + " diamonds"),
														true
												);
												return 1;
											})
									)
							)
					)

					// /bounty claim <player> (OP only manual claim)
					.then(CommandManager.literal("claim")
							.requires(source -> {
								if (source.getEntity() instanceof ServerPlayerEntity player) {
									return source.getServer().getPlayerManager().isOperator(player.getPlayerConfigEntry());
								}
								return false;
							})
							.then(CommandManager.argument("target", EntityArgumentType.player())
									.executes(context -> {
										ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");

										if (!bountymanager.hasBounty(target.getUuid())) {
											context.getSource().sendError(Text.literal(target.getName().getString() + " has no active bounty."));
											return 0;
										}

										int amount = bountymanager.getBounty(target.getUuid());
										ServerPlayerEntity sender = context.getSource().getPlayer();
										sender.getInventory().insertStack(new ItemStack(CURRENCY_ITEM, amount));

										bountymanager.removeBounty(target.getUuid());
										context.getSource().sendFeedback(
												() -> Text.literal("Manually claimed " + amount + " diamonds from bounty on " + target.getName().getString()),
												true
										);
										return 1;
									})
							)
					)

					// /bounty top
					.then(CommandManager.literal("top")
							.executes(context -> {
								String message = bountymanager.getTopBountiesString();
								context.getSource().sendFeedback(() -> Text.literal(message), false);
								return 1;
							})
					)

					// /bounty info <player>
					.then(CommandManager.literal("info")
							.then(CommandManager.argument("target", EntityArgumentType.player())
									.executes(context -> {
										ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");

										if (!bountymanager.hasBounty(target.getUuid())) {
											context.getSource().sendFeedback(
													() -> Text.literal(target.getName().getString() + " has no active bounty."),
													false
											);
											return 0;
										}

										int amount = bountymanager.getBounty(target.getUuid());
										context.getSource().sendFeedback(
												() -> Text.literal(target.getName().getString() + " has a bounty of " + amount + " diamonds"),
												false
										);
										return 1;
									})
							)
					)


			);
		});

		// Server tick: automatic bounty claim on player death
		ServerTickEvents.END_SERVER_TICK.register(this::checkBounties);
	}

	private void checkBounties(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!player.isDead()) continue;
			if (!bountymanager.hasBounty(player.getUuid())) continue;

			DamageSource lastSource = player.getDamageSources().playerAttack(player);
			if (lastSource == null || !(lastSource.getAttacker() instanceof ServerPlayerEntity killer)) continue;

			int amount = bountymanager.getBounty(player.getUuid());

			// Give diamonds as reward
			killer.getInventory().insertStack(new ItemStack(CURRENCY_ITEM, amount));

			// Broadcast bounty claim
			server.getPlayerManager().broadcast(
					Text.literal(killer.getName().getString() + " has claimed a bounty of " + amount + " diamonds from " + player.getName().getString() + "!"),
					false
			);

			// Remove bounty
			bountymanager.removeBounty(player.getUuid());
		}
	}
}