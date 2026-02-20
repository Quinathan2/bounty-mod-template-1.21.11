package net.quin.bountyfile.gui;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class BountyScreen extends HandledScreen<BountyScreenHandler> {

    private static final Identifier TEXTURE = Identifier.of("bountymod", "textures/gui/bounty.png");

    public BountyScreen(BountyScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(
                context.getMatrices(),  // pass the matrix stack
                TEXTURE,                // Identifier
                this.x, this.y,         // x, y
                0, 0,                   // u, v
                this.backgroundWidth, this.backgroundHeight,  // width, height
                256, 256                // full texture width/height
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
