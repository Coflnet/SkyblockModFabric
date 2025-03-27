package com.coflnet.gui.cofl;

import CoflCore.CoflCore;
import CoflCore.commands.models.FlipData;
import com.coflnet.CoflModClient;
import com.coflnet.gui.AuctionStatus;
import com.coflnet.gui.BinGUI;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.widget.ItemWidget;
import com.coflnet.gui.widget.ScrollableDynamicTextWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.*;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import oshi.util.tuples.Pair;

import java.util.List;

public class CoflBinGUI extends BinGUI {
    private TextWidget titleTextWidget;
    private ScrollableDynamicTextWidget loreScrollableTextWidget;
    private ClickableWidget rightClickableWidget;
    private ClickableWidget leftClickableWidget;

    public String title = "";
    public Text lore = Text.of("");
    public Pair<Integer, Integer> rightButtonCol = new Pair<>(CoflColConfig.BACKGROUND_SECONDARY, CoflColConfig.BACKGROUND_SECONDARY);

    public CoflBinGUI(GenericContainerScreen gcs){
        super(Text.literal("Cofl Bin Gui"), gcs, 5, 4);
        title = flipData == null ? "" : flipData.getMessageAsString();
    }

    @Override
    protected void clearAndInitWidgets(int screenWidth, int screenHeight) {
        this.clearChildren();
        itemWidget = new ItemWidget(
                screenWidth / 2 - width / 2 + p + 2,
                screenHeight / 2 - height / 2 + p + 12 + p + 2,
                Items.AIR.getDefaultStack()
        );

        leftClickableWidget = new ClickableWidget(
                screenWidth / 2 - width / 2 + p,
                screenHeight / 2 + height / 2 - p - (225 - 150 - 12 - p * 5) - screenHeight / 15,
                width / 5 * 2 - p,
                225 - 150 - 12 - p*5 + screenHeight / 15,
                Text.of("Cancel")
        ){
            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                RenderUtils.drawRoundedRect(context, getX(), getY(), getWidth(), getHeight(), r, this.isMouseOver(mouseX,mouseY) ? CoflColConfig.CANCEL_HOVER : CoflColConfig.CANCEL);
                RenderUtils.drawString(context, this.getMessage().getLiteralString(), getX() + 6, getY() + 4, CoflColConfig.TEXT_PRIMARY);
            }

            @Override
            protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

            @Override
            public void onClick(double mouseX, double mouseY) {
                if (auctionStatus != AuctionStatus.CONFIRMING) clickSlot(AUCTION_CANCEL_SLOT);
                else clickSlot(CONFIRMATION_CANCEL_SLOT);
            }
        };

        int tempWidth = width;
        int tempHeight = height;
        rightClickableWidget = new ClickableWidget(
                0, //screenWidth / 2 - width / 2, //screenWidth / 2 - width / 2 + p + width / 5 * 2,
                0, //screenHeight / 2 - height / 2, //screenHeight / 2 + height / 2 - p - (225 - 150 - 12 - p*5) - screenHeight / 15,
                screenWidth, //width, //width / 5 * 3 - p*2,
                screenHeight, //height, //225 - 150 - 12 - p*5 + screenHeight / 15,
                Text.of("")
        ){
            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                boolean mouseOver = mouseX >= (double)(screenWidth / 2 - tempWidth / 2 + p + tempWidth / 5 * 2)
                        && mouseY >= (double)(screenHeight / 2 + tempHeight / 2 - p - (225 - 150 - 12 - p*5) - screenHeight / 15)
                        && mouseX < (double)((screenWidth / 2 - tempWidth / 2 + p + tempWidth / 5 * 2) + (tempWidth / 5 * 3 - p*2))
                        && mouseY < (double)((screenHeight / 2 + tempHeight / 2 - p - (225 - 150 - 12 - p*5) - screenHeight / 15) + (225 - 150 - 12 - p*5 + screenHeight / 15));

                RenderUtils.drawRoundedRect(context,
                        screenWidth / 2 - tempWidth / 2 + p + tempWidth / 5 * 2,
                        screenHeight / 2 + tempHeight / 2 - p - (225 - 150 - 12 - p*5) - screenHeight / 15,
                        tempWidth / 5 * 3 - p*2,
                        225 - 150 - 12 - p*5 + screenHeight / 15,
                        r, mouseOver ? rightButtonCol.getB() : rightButtonCol.getA()
                );

                RenderUtils.drawString(context,
                        this.getMessage().getString(),
                        screenWidth / 2 - tempWidth / 2 + p + tempWidth / 5 * 2 + 6,
                        screenHeight / 2 + tempHeight / 2 - p - (225 - 150 - 12 - p*5) - screenHeight / 15 + 4,
                        CoflColConfig.TEXT_PRIMARY
                );
            }

            @Override
            protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

            @Override
            public void onClick(double mouseX, double mouseY) {
                if (leftClickableWidget.isMouseOver(mouseX, mouseY)) {
                    leftClickableWidget.onClick(mouseX, mouseY);
                } else {
                    if(auctionStatus != AuctionStatus.CONFIRMING) clickSlot(BUY_SLOT);
                    else if(auctionStatus == AuctionStatus.WAITING) MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal("[§1C§6oflnet§f]§7: waiting for auction grace period "));
                    else clickSlot(CONFIRM_SLOT);
                }
            }
        };

        titleTextWidget = new TextWidget(
                screenWidth / 2 - width / 2 + p + 3,
                screenHeight / 2 - height / 2 + p + 2,
                width - p*2 - 4, 10,
                Text.of(title),
                MinecraftClient.getInstance().textRenderer
        ).alignLeft();

        loreScrollableTextWidget = new ScrollableDynamicTextWidget(
                screenWidth / 2 - width / 2 + p + 20 + p + 4,
                screenHeight / 2 - height / 2 + p + 12 + p + 2,
                width - 20 - p*4 - 4, height - 75 - 2 - screenHeight / 15 - 2,
                lore, MinecraftClient.getInstance().textRenderer
        ){
            @Override
            protected void drawBox(DrawContext context) {}

            @Override
            public void onClick(double mouseX, double mouseY) {
                rightClickableWidget.onClick(mouseX,mouseY);
            }
        };

        if (auctionStatus == AuctionStatus.CONFIRMING) setRightButtonConfig(AuctionStatus.CONFIRMING);

        this.addDrawableChild(titleTextWidget);
        this.addDrawableChild(loreScrollableTextWidget);
        this.addDrawableChild(rightClickableWidget);
        this.addDrawableChild(leftClickableWidget);
        this.addDrawableChild(itemWidget);
    }

    private AuctionStatus setAuctionStatus(Item item){
        if (item == Items.GOLD_NUGGET) auctionStatus = AuctionStatus.BUYING;
        if (item == Items.RED_BED) auctionStatus = AuctionStatus.WAITING;
        if (item == Items.POTATO) auctionStatus = AuctionStatus.SOLD;
        return auctionStatus;
    }

    private void setRightButtonConfig(AuctionStatus auctionStatus){
        switch (auctionStatus){
            case INIT:
            case BUYING:
            case WAITING:
                rightButtonCol = new Pair<>(CoflColConfig.CONFIRM, CoflColConfig.CONFIRM_HOVER);
                rightClickableWidget.setMessage(Text.of("Buy (You can click anywhere)"));
                break;
            case SOLD:
                rightButtonCol = new Pair<>(CoflColConfig.UNAVAILABLE, CoflColConfig.UNAVAILABLE);
                rightClickableWidget.setMessage(Text.of("Bought by "));
                break;
            case CONFIRMING:
                rightButtonCol = new Pair<>(CoflColConfig.CONFIRM, CoflColConfig.CONFIRM_HOVER);
                rightClickableWidget.setMessage(Text.of("Confirm purchase"));
                break;
        }
    }

    public MutableText convertTextList(List<Text> collection){
        MutableText res = Text.empty();
        if (collection == null || collection.isEmpty()) return res;

        res.append(collection.getFirst());
        collection.removeFirst();

        for (Text text : collection) {
            res.append(Text.literal("\n"));
            res.append(text);
        }
        return res;
    }


    @Override
    public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        super.renderBackground(drawContext, mouseX, mouseY, delta);

        if(!gcsh.getInventory().isEmpty()){
            if (gcsh.getInventory().getStack(ITEM_SLOT).getItem() != Items.AIR) {
                setItem(gcsh.getInventory().getStack(ITEM_SLOT));
                lore = convertTextList(getTooltipFromItem(MinecraftClient.getInstance(), currentItem));
                loreScrollableTextWidget.updateText(lore);
            }
            if (gcsh.getInventory().getStack(BUY_SLOT).getItem() != Items.AIR) setRightButtonConfig(setAuctionStatus(gcsh.getInventory().getStack(ITEM_SLOT).getItem()));
        }

        // Background
        RenderUtils.drawRoundedRect(drawContext, screenWidth / 2 - width / 2,screenHeight / 2 - height / 2, width, height, r, CoflColConfig.BACKGROUND_PRIMARY);
        // Title Background
        RenderUtils.drawRoundedRect(drawContext, screenWidth / 2 - width / 2 + p,screenHeight / 2 - height / 2 + p, width - p*2, 12, r, CoflColConfig.BACKGROUND_SECONDARY);
        // Item Background
        RenderUtils.drawRoundedRect(drawContext, screenWidth / 2 - width / 2 + p,screenHeight / 2 - height / 2 + p + 12 + p, 20, 20, r, CoflColConfig.BACKGROUND_SECONDARY);
        // Description Background
        RenderUtils.drawRoundedRect(drawContext,screenWidth / 2 - width / 2 + p + 20 + p, screenHeight / 2 - height / 2 + p + 12+ p, width - 20 - p*3, height - 75 - screenHeight / 15, r, CoflColConfig.BACKGROUND_SECONDARY);
    }
}
