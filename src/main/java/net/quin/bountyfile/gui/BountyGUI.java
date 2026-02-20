package net.quin.bountyfile.gui;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.quin.bountyfile.bountymanager;

public class BountyGUI {

    public static void open(ServerPlayerEntity player) {
        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {

            @Override
            public Text getDisplayName() {
                return Text.literal("Bounties");
            }

            @Override
            public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory inv, net.minecraft.entity.player.PlayerEntity playerEntity) {
                BountyScreenHandler handler = new BountyScreenHandler(syncId, inv);
                handler.fillBounties(bountymanager.getBounties(), bountymanager.getPlayerNames());
                return handler;
            }
        };

        player.openHandledScreen(factory); // now works!
    }
}