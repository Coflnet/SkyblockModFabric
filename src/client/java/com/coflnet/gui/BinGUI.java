package com.coflnet.gui;

import CoflCore.commands.models.FlipData;
import com.coflnet.CoflModClient;
import com.coflnet.gui.widget.ItemWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public abstract class BinGUI extends Screen {
    protected int width;
    protected int height;
    protected int screenHeight;
    protected int screenWidth;
    protected int p;
    protected int r;
    protected final int AUCTION_ITEM_SLOT = 13;
    protected final int AUCTION_BUY_SLOT = 31;
    protected final int AUCTION_CANCEL_SLOT = 49;
    protected final int AUCTION_CONFIRM_SLOT = 11;
    protected final int AUCTION_CONFIRMATION_CANCEL_SLOT = 15;
    protected final int OWN_AUCTION_CLAIM_SLOT = 31;
    protected final int OWN_AUCTION_CANCEL_SLOT = 33;
    protected ItemWidget itemWidget;
    protected ItemStack currentItem;
    protected FlipData flipData;

    protected AuctionStatus auctionStatus;
    protected GenericContainerScreenHandler gcsh;
    protected GenericContainerScreen gcs;
    protected BinGUI(Text title, GenericContainerScreen gcs, int p, int r) {
        super(title);
        this.gcs = gcs;
        this.gcsh = gcs.getScreenHandler();
        this.p = p;
        this.r = r;

        if (isAuctionConfirming(gcs)) this.auctionStatus = AuctionStatus.AUCTION_CONFIRMING;
        else if (isAuctionInit(gcs)) this.auctionStatus = AuctionStatus.INIT;

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

    protected AuctionStatus updateAuctionStatus(ItemStack itemStack){
        Item item = itemStack.getItem();
        if (item == Items.BLACK_STAINED_GLASS_PANE) {
            auctionStatus = AuctionStatus.OWN_AUCTION_CANCELING;
            return auctionStatus;
        }

        if (item == Items.RED_BED) {
            auctionStatus = AuctionStatus.AUCTION_WAITING;
            return auctionStatus;
        }

        String customName = itemStack.getCustomName().getString();
        String loreString = itemStack.getComponents().get(DataComponentTypes.LORE).toString();

        switch (customName){
            case "Buy Item Right Now":
                if (loreString.contains("Cannot afford bid!")) auctionStatus = AuctionStatus.AUCTION_BUYING;
                else if (loreString.contains("Click to purchase!")) auctionStatus = AuctionStatus.AUCTION_BUYING;
                return auctionStatus;
            case "Collect Auction":
                if (loreString.contains("Click to collect coins!")) auctionStatus = AuctionStatus.OWN_AUCTION_CLAIMING;
                else if (loreString.contains("Click to pick up item!")) auctionStatus = AuctionStatus.OWN_AUCTION_CLAIMING;
                else if (loreString.contains("Someone else purchased the item")) auctionStatus = AuctionStatus.AUCTION_SOLD;
                return auctionStatus;
            case "Confirm":
                auctionStatus = AuctionStatus.AUCTION_CONFIRMING;
                return auctionStatus;
        }

        return auctionStatus;
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

    public static boolean isAuctionConfirming(GenericContainerScreen gcs){
        return  gcs.getTitle().getString().contains("Confirm Purchase")
                && gcs.getScreenHandler().getInventory().size() == 9 * 3;
    }


    public static boolean isAuctionInit(GenericContainerScreen gcs) {
        return gcs.getTitle().getString().contains("BIN Auction View")
                && gcs.getScreenHandler().getInventory().size() == 9 * 6;
    }
}
