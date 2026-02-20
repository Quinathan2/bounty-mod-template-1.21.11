package net.quin.bountyfile.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.quin.bountyfile.BountyMod;

import java.util.Map;
import java.util.UUID;

public class BountyScreenHandler extends ScreenHandler {

    private final Inventory inventory;

    public BountyScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(54)); // 6x9 chest
    }

    public BountyScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(BountyMod.BOUNTY_SCREEN_HANDLER, syncId);
        this.inventory = inventory;

        // Bounty slots (6x9)
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }

        // Player inventory slots (3x9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        // Hotbar slots (1x9)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true; // GUI-only
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        // Disable shift-clicking
        return ItemStack.EMPTY;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Prevent taking items from bounty slots
        if (slotIndex >= 0 && slotIndex < inventory.size()) {
            ItemStack stack = inventory.getStack(slotIndex);
            if (!stack.isEmpty()) {
                // Show bounty info in chat
                player.sendMessage(Text.literal("Bounty Info: " + stack.getName().getString()), false);
            }
            return; // do not move items
        }

        super.onSlotClick(slotIndex, button, actionType, player);
    }

    /**
     * Fill the GUI with player heads representing bounties.
     *
     * @param bounties Map of player UUID → bounty amount
     * @param names    Map of player UUID → player name
     */
    public void fillBounties(Map<UUID, Integer> bounties, Map<UUID, String> names) {
        int index = 0;

        for (Map.Entry<UUID, Integer> entry : bounties.entrySet()) {
            if (index >= inventory.size()) break;

            String playerName = names.getOrDefault(entry.getKey(), "Unknown");
            int bounty = entry.getValue();

            ItemStack stack = new ItemStack(Items.PLAYER_HEAD);

            // Attach skull owner via CUSTOM_DATA
            NbtCompound skullTag = new NbtCompound();
            skullTag.putString("SkullOwner", playerName);

            NbtCompound customData = new NbtCompound();
            customData.put("BlockEntityTag", skullTag);

            stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, customData);

            // Set display name using DataComponents
            stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(playerName + " $" + bounty));

            inventory.setStack(index, stack);
            index++;
        }
    }
}