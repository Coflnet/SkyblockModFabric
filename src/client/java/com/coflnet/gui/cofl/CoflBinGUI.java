package com.coflnet.gui.cofl;

import com.coflnet.gui.AuctionStatus;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.widget.ItemWidget;
import com.coflnet.gui.widget.ScrollableDynamicTextWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.MultilineText;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFWWindowRefreshCallback;
import org.lwjgl.glfw.GLFWWindowSizeCallback;
import oshi.util.tuples.Pair;

import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.List;

public class CoflBinGUI extends Screen {
    private TextWidget titleTextWidget;
    private ItemWidget itemWidget;
    private ScrollableDynamicTextWidget loreScrollableTextWidget;
    private ClickableWidget rightClickableWidget;
    private ClickableWidget leftClickableWidget;

    private int width;
    private int height;
    private int p;
    private int r;
    private final int ITEM_SLOT = 13;
    private final int BUY_SLOT = 31;
    private final int AUCTION_CANCEL_SLOT = 49;
    private final int CONFIRM_SLOT = 11;
    private final int CONFIRMATION_CANCEL_SLOT = 15;
    private AuctionStatus auctionStatus;
    public GenericContainerScreenHandler gcsh;
    public GenericContainerScreen gcs;
    public String title = "";
    public Text lore = Text.of(RenderUtils.lorem());
    public Pair<Integer, Integer> rightButtonCol = new Pair<>(CoflColConfig.BACKGROUND_SECONDARY, CoflColConfig.BACKGROUND_SECONDARY);

    public CoflBinGUI(Item item, GenericContainerScreen gcs){
        super(Text.literal("Cofl Bin Gui"));

        int screenWidth = MinecraftClient.getInstance().currentScreen.width;
        int screenHeight = MinecraftClient.getInstance().currentScreen.height;

        this.gcs = gcs;
        this.gcsh = gcs.getScreenHandler();

        if (gcsh.getType() == ScreenHandlerType.GENERIC_9X3){
            this.auctionStatus = AuctionStatus.CONFIRMING;
        } else this.auctionStatus = AuctionStatus.INIT;

        this.width = screenWidth / 2;
        if (width < 300) this.width = 300;

        this.height = screenHeight / 3 * 2;
        if (height < 225) this.height = 225;

        this.p = 5;
        this.r = 4;

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
                RenderUtils.drawString(context, this.getMessage().getLiteralString(), getX() + 6, getY() + 4, 0xFFEEEEEE);
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
                        0xFFEEEEEE
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
                width - 20 - p*4 - 4, height - 75 - 2 - screenHeight / 15,
                lore, MinecraftClient.getInstance().textRenderer
        ){
            @Override
            protected void drawBox(DrawContext context) {}

            @Override
            public boolean mouseReleased(double mouseX, double mouseY, int button) {
                if(super.mouseReleased(mouseX, mouseY, button) && isMouseOver(mouseX,mouseY) && button == 0){
                    rightClickableWidget.onClick(mouseX,mouseY);
                }
                return super.mouseReleased(mouseX, mouseY, button);
            }
        };
        //loreScrollableTextWidget.updateText(Text.of("AAAAAAAAAAAAAa"));


        itemWidget = new ItemWidget(
                screenWidth / 2 - width / 2 + p + 2,
                screenHeight / 2 - height / 2 + p + 12 + p + 2,
                Items.AIR.getDefaultStack()
        );

        this.addDrawableChild(titleTextWidget);
        this.addDrawableChild(loreScrollableTextWidget);
        this.addDrawableChild(itemWidget);
        this.addDrawableChild(rightClickableWidget);
        this.addDrawableChild(leftClickableWidget);

        gcsh.addListener(new ScreenHandlerListener() {
            @Override
            public void onSlotUpdate(ScreenHandler handler, int slotId, ItemStack stack) {
                //if (stack.getItem() != Items.AIR) System.out.println("slotid: "+slotId);
                if (slotId == ITEM_SLOT) setItem(stack);
                if (auctionStatus != AuctionStatus.CONFIRMING){
                    if(slotId == BUY_SLOT) setRightButtonConfig(setAuctionStatus(stack.getItem()));
                } else if (auctionStatus == AuctionStatus.CONFIRMING && slotId == CONFIRM_SLOT) setRightButtonConfig(AuctionStatus.CONFIRMING);
            }

            @Override
            public void onPropertyUpdate(ScreenHandler handler, int property, int value) {}
        });
    }

    private void clickSlot(int slotId) {
        PlayerEntity player = client.player;

        client.interactionManager.clickSlot(
                gcsh.syncId,
                slotId,
                0,
                SlotActionType.PICKUP,
                player
        );
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
                System.out.println("CASE ENTERED");
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

    public void setItem(ItemStack item) {
        lore = convertTextList(getTooltipFromItem(MinecraftClient.getInstance(), item));
        loreScrollableTextWidget.updateText(lore);
        this.itemWidget.item = item;
    }

    public MutableText convertTextList(List<Text> collection){
        MutableText res = Text.empty();
        if (collection == null || collection.isEmpty()) return res;

        res = Text.literal(collection.getFirst().getString()).setStyle(collection.getFirst().getStyle());
        collection.removeFirst();

        for (Text text : collection) {
            MutableText toAppend = Text.literal("\n"+text.getString()).setStyle(text.getStyle());
            res.append(toAppend);
        }
        return res;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        int screenWidth = MinecraftClient.getInstance().currentScreen.width;
        int screenHeight = MinecraftClient.getInstance().currentScreen.height;

        // Background
        RenderUtils.drawRoundedRect(drawContext, screenWidth / 2 - width / 2,screenHeight / 2 - height / 2, width, height, r, CoflColConfig.BACKGROUND_PRIMARY);
        // Title Background
        RenderUtils.drawRoundedRect(drawContext, screenWidth / 2 - width / 2 + p,screenHeight / 2 - height / 2 + p, width - p*2, 12, r, CoflColConfig.BACKGROUND_SECONDARY);
        // Item Background
        RenderUtils.drawRoundedRect(drawContext, screenWidth / 2 - width / 2 + p,screenHeight / 2 - height / 2 + p + 12 + p, 20, 20, r, CoflColConfig.BACKGROUND_SECONDARY);
        // Description Background
        RenderUtils.drawRoundedRect(drawContext,screenWidth / 2 - width / 2 + p + 20 + p, screenHeight / 2 - height / 2 + p + 12+ p, width - 20 - p*3, height - 75 - screenHeight / 15, r, CoflColConfig.BACKGROUND_SECONDARY);
    }

    @Override
    public void close() {
        gcs.close();
        super.close();
    }
}
