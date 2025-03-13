package com.coflnet.gui.cofl;

import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.widget.ItemWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.InventoryChangedListener;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.text.Text;

public class CoflBinGUI extends Screen implements InventoryChangedListener {
    private int width;
    private int height;
    private int p;
    private int r;
    public GenericContainerScreenHandler gcsh;
    public Item item = Items.AIR;
    public String title = "";
    public String lore = RenderUtils.lorem();
    public String buttonRText = "";

    private TextWidget titleTextWidget;
    private ItemWidget itemWidget;
    private ScrollableTextWidget loreScrollableTextWidget;
    private ClickableWidget rightClickableWidget;
    private ClickableWidget leftClickableWidget;

    public CoflBinGUI(Item item, GenericContainerScreenHandler gcsh){
        super(Text.literal("Cofl Bin Gui"));

        int screenWidth = MinecraftClient.getInstance().currentScreen.width;
        int screenHeight = MinecraftClient.getInstance().currentScreen.height;

        this.item = item;
        this.gcsh = gcsh;

        this.width = screenWidth / 2;
        if (width < 300) this.width = 300;

        this.height = screenHeight / 3 * 2;
        if (height < 225) this.height = 225;

        this.p = 5;
        this.r = 4;

        gcsh.addListener(new ScreenHandlerListener() {
            @Override
            public void onSlotUpdate(ScreenHandler handler, int slotId, ItemStack stack) {
                if (stack.getItem() != Items.AIR) System.out.println("slotid: "+slotId);
                switch (slotId){
                    case 13:
                        setItem(stack.getItem());
                        break;
                    case 31:
                        break;
                    case 41:
                        break;
                }
            }

            @Override
            public void onPropertyUpdate(ScreenHandler handler, int property, int value) {}
        });

        leftClickableWidget = new ClickableWidget(
                screenWidth / 2 - width / 2 + p,
                screenHeight / 2 + height / 2 - p - (225 - 150 - 12 - p * 5) - screenHeight / 15,
                width / 5 * 2 - p,
                225 - 150 - 12 - p*5 + screenHeight / 15,
                Text.of("Cancel")
        ){
            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                RenderUtils.drawRoundedRect(context, getX(), getY(), getWidth(), getHeight(), r, CoflColConfig.CANCEL);
                RenderUtils.drawString(context, this.getMessage().getLiteralString(), getX() + 6, getY() + 4, 0xFFEEEEEE);
            }

            @Override
            protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

            @Override
            public void onClick(double mouseX, double mouseY) {close();}
        };

        rightClickableWidget = new ClickableWidget(
                screenWidth / 2 - width / 2, //screenWidth / 2 - width / 2 + p + width / 5 * 2,
                screenHeight / 2 - height / 2, //screenHeight / 2 + height / 2 - p - (225 - 150 - 12 - p*5) - screenHeight / 15,
                width, //width / 5 * 3 - p*2,
                height, //225 - 150 - 12 - p*5 + screenHeight / 15,
                Text.of(buttonRText)
        ){
            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                RenderUtils.drawRoundedRect(context,
                        screenWidth / 2 - width / 2 + p + width / 5 * 2,
                        screenHeight / 2 + height / 2 - p - (225 - 150 - 12 - p*5) - screenHeight / 15,
                        width / 5 * 3 - p*2,
                        225 - 150 - 12 - p*5 + screenHeight / 15,
                        r, CoflColConfig.CONFIRM
                );
                RenderUtils.drawString(context,
                        this.getMessage().getString(),
                        screenWidth / 2 - width / 2 + p + width / 5 * 2 + 6,
                        screenHeight / 2 + height / 2 - p - (225 - 150 - 12 - p*5) - screenHeight / 15 + 4,
                        0xFFEEEEEE
                );
            }
            @Override
            protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

            @Override
            public void onClick(double mouseX, double mouseY) {
                if (leftClickableWidget.isMouseOver(mouseX, mouseY)) {
                    leftClickableWidget.onClick(mouseX, mouseY);
                } else MinecraftClient.getInstance().player.sendMessage(Text.of("Confim clicked"));
            }
        };

        titleTextWidget = new TextWidget(
                screenWidth / 2 - width / 2 + p + 3,
                screenHeight / 2 - height / 2 + p + 2,
                width - p*2 - 4, 10,
                Text.of(title),
                MinecraftClient.getInstance().textRenderer
        ).alignLeft();

        loreScrollableTextWidget = new ScrollableTextWidget(
                screenWidth / 2 - width / 2 + p + 20 + p + 4,
                screenHeight / 2 - height / 2 + p + 12 + p + 2,
                width - 20 - p*4 - 4, height - 75 - 2 - screenHeight / 15,
                Text.of(lore), MinecraftClient.getInstance().textRenderer
        ){
            @Override
            protected void drawBox(DrawContext context) {}

            @Override
            public void onClick(double mouseX, double mouseY) {
                super.onClick(mouseX, mouseY);
            }
        };

        itemWidget = new ItemWidget(
                screenWidth / 2 - width / 2 + p + 2,
                screenHeight / 2 - height / 2 + p + 12 + p + 2,
                this.item.getDefaultStack()
        );

        this.addDrawableChild(titleTextWidget);
        this.addDrawableChild(loreScrollableTextWidget);
        this.addDrawableChild(itemWidget);
        this.addDrawableChild(rightClickableWidget);
        this.addDrawableChild(leftClickableWidget);
    }

    public void setItem(Item item) {
        this.item = item;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
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
    public void onInventoryChanged(Inventory sender) {
        System.out.println("Inv changed");
    }
}
