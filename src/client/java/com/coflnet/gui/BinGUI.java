package com.coflnet.gui;

import CoflCore.commands.models.FlipData;
import com.coflnet.CoflModClient;
import com.coflnet.gui.widget.ItemWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public abstract class BinGUI extends Screen {
    protected int width;
    protected int height;
    protected int screenHeight;
    protected int screenWidth;
    protected int p;
    protected int r;
    protected final int ITEM_SLOT = 13;
    protected final int BUY_SLOT = 31;
    protected final int AUCTION_CANCEL_SLOT = 49;
    protected final int CONFIRM_SLOT = 11;
    protected final int CONFIRMATION_CANCEL_SLOT = 15;
    protected ItemWidget itemWidget;
    protected ItemStack currentItem;
    protected FlipData flipData = null;

    protected AuctionStatus auctionStatus;
    protected GenericContainerScreenHandler gcsh;
    protected GenericContainerScreen gcs;
    protected BinGUI(Text title, GenericContainerScreen gcs, int p, int r) {
        super(title);
        this.gcs = gcs;
        this.gcsh = gcs.getScreenHandler();
        this.p = p;
        this.r = r;

        if (gcsh.getType() == ScreenHandlerType.GENERIC_9X3){
            this.auctionStatus = AuctionStatus.CONFIRMING;
        } else this.auctionStatus = AuctionStatus.INIT;

        flipData = CoflModClient.popFlipData();

        screenWidth = MinecraftClient.getInstance().currentScreen.width;
        screenHeight = MinecraftClient.getInstance().currentScreen.height;
        initSize(screenWidth, screenHeight);
        clearAndInitWidgets(screenWidth, screenHeight);
    }

    private void initSize(int screenWidth, int screenHeight){
        this.width = MinecraftClient.getInstance().currentScreen.width / 2;
        if (width < 300) this.width = 300;

        this.height = MinecraftClient.getInstance().currentScreen.height / 3 * 2;
        if (height < 225) this.height = 225;
    }

    protected abstract void clearAndInitWidgets(int screenWidth, int screenHeight);

    public void setItem(ItemStack item) {
        this.currentItem = item;
        this.itemWidget.item = item;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        auctionStatus = AuctionStatus.INIT;
        gcs.close();
        super.close();
    }

    protected void clickSlot(int slotId) {
        PlayerEntity player = client.player;

        client.interactionManager.clickSlot(
                gcsh.syncId,
                slotId,
                0,
                SlotActionType.PICKUP,
                player
        );
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (screenWidth != MinecraftClient.getInstance().currentScreen.width
            || screenHeight != MinecraftClient.getInstance().currentScreen.height){
            screenWidth = MinecraftClient.getInstance().currentScreen.width;
            screenHeight = MinecraftClient.getInstance().currentScreen.height;
            initSize(screenWidth, screenHeight);
            clearAndInitWidgets(screenWidth, screenHeight);
        }
    }
}
