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
import org.jetbrains.annotations.NotNull;

/**
 * Abstract class that contains logic which is needed in implementations of BIN GUIs.
 * @see com.coflnet.gui.cofl.CoflBinGUI
 * @see com.coflnet.gui.tfm.TfmBinGUI
 */
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

    /**
     * Constructor for BIN-GUIs.
     * @param title title of the screen
     * @param gcs the BIN auction as an instance of {@link GenericContainerScreen}
     * @param p padding as an int
     * @param r radius for rounded corners
     */
    protected BinGUI(Text title, @NotNull GenericContainerScreen gcs, @NotNull int p, int r) {
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

    /**
     * Implementations of this method should include {@code this.clearChildren();}
     * and all initializations of your GUIs widgets (such as buttons).
     * @param screenWidth the current width of the screen as an int
     * @param screenHeight the current height of the screen as an int
     */
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

    /**
     * Handles clicking a {@link net.minecraft.screen.slot.Slot} for the player.
     * @param slotId the index of the slot that is to be clicked by the player.
     */
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

    /**
     * Determines the status of the auction based on the metadata of the "Confirm"-item in a BIN Auction
     * and furthermore updates the status in {@link #auctionStatus auctionStatus}.
     * @return the updated auctionStatus
     * @see AuctionStatus
     */
    protected AuctionStatus updateAuctionStatus(@NotNull ItemStack itemStack){
        Item item = itemStack.getItem();
        if (item == Items.BLACK_STAINED_GLASS_PANE) {
            auctionStatus = AuctionStatus.OWN_AUCTION_CANCELING;
            return auctionStatus;
        }

        if (item == Items.RED_BED) {
            auctionStatus = AuctionStatus.AUCTION_WAITING;
            return auctionStatus;
        }

        if(itemStack.getCustomName() == null)
            return auctionStatus;

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

    /**
     * Adds logic to update the size of the UI elements when the window size changes dynamically.
     * <br/>
     * <br/>
     * When overriding this method, make sure to include
     * {@code super.renderBackground(drawContext, mouseX, mouseY, delta);}
     * so your GUI changes size dynamically.
     */
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

    /**
     * Determines if the given screen is the confirmation part of a BIN auction.
     * @param gcs instance of {@link GenericContainerScreen}
     * @return {@code true} if the screen is the confirmation screen of a BIN auction, otherwise {@code false}
     */
    public static boolean isAuctionConfirming(@NotNull GenericContainerScreen gcs){
        return  gcs.getTitle().getString().contains("Confirm Purchase")
                && gcs.getScreenHandler().getInventory().size() == 9 * 3;
    }

    /**
     * Determines if the given screen is a BIN auction.
     * @param gcs instance of {@link GenericContainerScreen}
     * @return {@code true} if the screen is a BIN auction, otherwise {@code false}
     */
    public static boolean isAuctionInit(@NotNull GenericContainerScreen gcs) {
        return gcs.getTitle().getString().contains("BIN Auction View")
                && gcs.getScreenHandler().getInventory().size() == 9 * 6;
    }
}
