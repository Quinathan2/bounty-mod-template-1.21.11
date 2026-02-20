package net.quin.bountyfile;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.quin.bountyfile.gui.BountyScreen;


public class BountyModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register the GUI screen for the bounty container
        HandledScreens.register(BountyMod.BOUNTY_SCREEN_HANDLER, BountyScreen::new);
    }
}