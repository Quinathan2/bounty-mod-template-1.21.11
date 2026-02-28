package net.quin.bountyfile;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.inventory.SimpleInventory;


public class BountyMod implements ModInitializer {
	public static final String MOD_ID = "bounty-mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Item CURRENCY_ITEM = Items.DIAMOND;


	@Override
	public void onInitialize() {
		LOGGER.info("Bounty Mod initialized!");



		// Initialize bounties
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			BountyManager.init(server.getRunDirectory().toFile());
			AutomatedBountyManager.start(server); // start automated bounties
		});

		BountyDeathHandler.register();
		OfflinePlayerCache.register();
		// Register commands
		BountyCommandManager.register();
		BountyOptInManager.register();
		// Track opted-in players on join
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			// Mark the player as having joined the world
			BountyOptInManager.addJoined(player.getUuid());
		});



	}


}