package com.coflnet.gui.cofl;

import com.coflnet.gui.AuctionStatus;
import com.coflnet.gui.BinGUI;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.widget.ItemWidget;
//import com.coflnet.gui.widget.ScrollableDynamicTextWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import oshi.util.tuples.Pair;

import java.util.List;

public class CoflBinGUI extends BinGUI {
    private TextWidget titleTextWidget;
    private ScrollableTextWidget loreScrollableTextWidget;
    private ClickableWidget rightClickableWidget;
    private ClickableWidget leftClickableWidget;

    public String title = "";
    public Text lore = Text.of("");
    public Pair<Integer, Integer> rightButtonCol = new Pair<>(CoflColConfig.BACKGROUND_SECONDARY, CoflColConfig.BACKGROUND_SECONDARY);

    public CoflBinGUI(GenericContainerScreen gcs){
        super(Text.literal("Cofl Bin Gui"), gcs, 5, 4);
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
                if (auctionStatus != AuctionStatus.AUCTION_CONFIRMING) clickSlot(AUCTION_CANCEL_SLOT);
                else clickSlot(AUCTION_CONFIRMATION_CANCEL_SLOT);
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
                    switch (auctionStatus){
                        case INIT:
                        case AUCTION_BUYING:
                            clickSlot(AUCTION_BUY_SLOT);
                            break;
                        case AUCTION_WAITING:
                            MinecraftClient.getInstance().inGameHud.getChatHud()
                                    .addMessage(Text.literal("[§1C§6oflnet§f]§7: waiting for auction grace period "));
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
        };

        titleTextWidget = new TextWidget(
                screenWidth / 2 - width / 2 + p + 3,
                screenHeight / 2 - height / 2 + p + 2,
                width - p*2 - 4, 10,
                Text.of(flipData == null ? "" : flipData.getMessageAsString().replace("sellers ah", "")),
                MinecraftClient.getInstance().textRenderer
        ).alignLeft();

        loreScrollableTextWidget = new ScrollableTextWidget(
                screenWidth / 2 - width / 2 + p + 20 + p + 4,
                screenHeight / 2 - height / 2 + p + 12 + p + 2,
                width - 20 - p*4 - 4,  height - 75 - 2 - screenHeight / 15 - 2,
                lore == null ? Text.of("") : lore, MinecraftClient.getInstance().textRenderer
        ){
            @Override
            protected void drawBox(DrawContext context) {}

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                boolean clicked = super.mouseClicked(mouseX, mouseY, button);
                if (clicked) rightClickableWidget.onClick(mouseX, mouseY);
                return clicked;
            }

            @Override
            protected boolean isValidClickButton(int button) {
                return button == 0 || button == 1;
            }
        };

        if(auctionStatus.compareTo(AuctionStatus.AUCTION_CONFIRMING) == 0) setRightButtonConfig(auctionStatus);

        this.addDrawableChild(titleTextWidget);
        this.addDrawableChild(loreScrollableTextWidget);
        this.addDrawableChild(rightClickableWidget);
        this.addDrawableChild(leftClickableWidget);
        this.addDrawableChild(itemWidget);
    }

    private void setRightButtonConfig(AuctionStatus auctionStatus){
        switch (auctionStatus){
            case INIT:
            case AUCTION_BUYING:
            case AUCTION_WAITING:
                rightButtonCol = new Pair<>(CoflColConfig.CONFIRM, CoflColConfig.CONFIRM_HOVER);
                rightClickableWidget.setMessage(Text.of("Buy (You can click anywhere)"));
                break;
            case AUCTION_SOLD:
                rightButtonCol = new Pair<>(CoflColConfig.UNAVAILABLE, CoflColConfig.UNAVAILABLE);
                for (Text line : currentItem.getComponents().get(DataComponentTypes.LORE).lines()) {
                    if (line.getString().startsWith("Buyer: ")) rightClickableWidget.setMessage(Text.literal("Bought by "+line.getString().substring("Buyer: ".length())));
                }
                break;
            case AUCTION_CONFIRMING:
                rightButtonCol = new Pair<>(CoflColConfig.CONFIRM, CoflColConfig.CONFIRM_HOVER);
                rightClickableWidget.setMessage(Text.of("Confirm purchase"));
                break;
            case OWN_AUCTION_CLAIMING:
                rightButtonCol = new Pair<>(CoflColConfig.CONFIRM, CoflColConfig.CONFIRM_HOVER);
                rightClickableWidget.setMessage(Text.of("Claim Auction"));
                break;
            case OWN_AUCTION_CANCELING:
                rightButtonCol = new Pair<>(CoflColConfig.UNAVAILABLE, CoflColConfig.UNAVAILABLE);
                rightClickableWidget.setMessage(Text.of("Cancel Auction"));
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
            if (gcsh.getInventory().getStack(AUCTION_ITEM_SLOT).getItem() != Items.AIR) {
                setItem(gcsh.getInventory().getStack(AUCTION_ITEM_SLOT));
                lore = convertTextList(getTooltipFromItem(MinecraftClient.getInstance(), currentItem));
                loreScrollableTextWidget.setMessage(lore == null ? Text.empty() : lore);
            }

            if (gcsh.getInventory()
                    .getStack(auctionStatus.compareTo(AuctionStatus.AUCTION_CONFIRMING) == 0 ? AUCTION_CONFIRM_SLOT : AUCTION_BUY_SLOT)
                    .getItem() != Items.AIR) {
                AuctionStatus as = updateAuctionStatus(
                        gcsh.getInventory()
                                .getStack(auctionStatus.compareTo(AuctionStatus.AUCTION_CONFIRMING) == 0 ? AUCTION_CONFIRM_SLOT : AUCTION_BUY_SLOT)
                );
                //System.out.println(as);
                setRightButtonConfig(as);
            }
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
