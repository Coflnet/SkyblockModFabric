package com.coflnet.gui.tfm;

import CoflCore.commands.models.ChatMessageData;
import CoflCore.commands.models.FlipData;
import CoflCore.handlers.DescriptionHandler;
import com.coflnet.CoflModClient;
import com.coflnet.gui.AuctionStatus;
import com.coflnet.gui.BinGUI;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.widget.ItemWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TfmBinGUI extends BinGUI {
    public String lore = "";

    public TextWidget titleTextWidget;
    public MultilineTextWidget loreMultilineTextWidget;
    public ClickableWidget confirmClickableWidget;
    public ClickableWidget cancelClickableWidget;

    public TfmBinGUI(GenericContainerScreen gcs){
        super(Text.of("Tfm Bin Gui"), gcs, 1, 4);
    }

    @Override
    protected void clearAndInitWidgets(int screenWidth, int screenHeight) {
        clearChildren();
        itemWidget = new ItemWidget(
                screenWidth / 2 - 8,
                screenHeight / 2 - 8,
                Items.AIR.getDefaultStack()
        );

        titleTextWidget = new TextWidget(
                screenWidth / 2 - width / 2 + 12,
                screenHeight / 2 - height / 2 + 8,
                width - 12, 10,
                Text.of("Cofl - Auction View"),
                MinecraftClient.getInstance().textRenderer
        ).alignLeft();

        loreMultilineTextWidget = new MultilineTextWidget(
                screenWidth / 2 - width / 2 + 12,
                screenHeight / 2 - height / 2 + 8 + 8 + 6,
                Text.of(flipData == null ? "" : flipData.getMessageAsString().replace("sellers ah", "")),
                MinecraftClient.getInstance().textRenderer
        ){
            @Override
            protected boolean isValidClickButton(int button) {
                return button == 0 || button == 1;
            }
        }.setCentered(false);

        confirmClickableWidget = new ClickableWidget(
                0, 0, screenWidth, screenHeight, Text.empty()
        ) {
            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                RenderUtils.drawRectOutline(
                        context,
                        screenWidth / 2 - 80 / 2,
                        screenHeight / 2 - 40 / 2,
                        80, 40,
                        p, TfmColConfig.CONFIRM, TfmColConfig.BORDER
                );
            }

            @Override
            public void onClick(double mouseX, double mouseY) {
                if (cancelClickableWidget.isMouseOver(mouseX, mouseY)){
                    cancelClickableWidget.onClick(mouseX,mouseY);
                } else {
                    switch (auctionStatus){
                        case INIT:
                        case AUCTION_BUYING:
                        case AUCTION_WAITING:
                            clickSlot(AUCTION_BUY_SLOT);
                            break;
                        case AUCTION_CONFIRMING:
                            clickSlot(AUCTION_CONFIRM_SLOT);
                            break;
                        case OWN_AUCTION_CLAIMING:
                            clickSlot(OWN_AUCTION_CLAIM_SLOT);
                            break;
                        case OWN_AUCTION_CANCELING:
                            clickSlot(OWN_AUCTION_CANCEL_SLOT);
                            break;
                    }
                }
            }

            @Override
            protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
        };

        cancelClickableWidget = new ClickableWidget(
                screenWidth / 2 - 36 / 2,
                screenHeight / 2 + 40 + p,
                36, 30, Text.empty()
        ) {
            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                RenderUtils.drawRectOutline(
                        context, getX(), getY(),
                        getWidth(), getHeight(),
                        p, TfmColConfig.CANCEL, TfmColConfig.BORDER
                );
            }
            @Override
            protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

            @Override
            public void onClick(double mouseX, double mouseY) {
                if (auctionStatus != AuctionStatus.AUCTION_CONFIRMING) clickSlot(AUCTION_CANCEL_SLOT);
                else clickSlot(AUCTION_CONFIRMATION_CANCEL_SLOT);
            }
        };

        this.addDrawableChild(titleTextWidget);
        this.addDrawableChild(loreMultilineTextWidget);
        this.addDrawableChild(confirmClickableWidget);
        this.addDrawableChild(cancelClickableWidget);
        this.addDrawableChild(itemWidget);

        gcsh.getInventory();
    }

    @Override
    public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float delta){
        super.renderBackground(drawContext, mouseX, mouseY, delta);

        if(!gcsh.getInventory().isEmpty()){
            if (gcsh.getInventory().getStack(AUCTION_ITEM_SLOT).getItem() != Items.AIR){ 
                setItem(gcsh.getInventory().getStack(AUCTION_ITEM_SLOT));
                 updateAuctionStatus(
                        gcsh.getInventory()
                                .getStack(auctionStatus.compareTo(AuctionStatus.AUCTION_CONFIRMING) == 0 ? AUCTION_CONFIRM_SLOT : AUCTION_BUY_SLOT)
                );
            }
        }

        RenderUtils.drawRectOutline(
                drawContext,
                screenWidth / 2 - width / 2,
                screenHeight / 2 - height / 2,
                width, height,
                p, TfmColConfig.BACKGROUND_PRIMARY, TfmColConfig.BORDER
        );
    }
}
