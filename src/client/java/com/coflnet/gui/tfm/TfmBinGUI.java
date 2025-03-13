package com.coflnet.gui.tfm;

import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.widget.ItemWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TfmBinGUI extends Screen {
    private static final Logger log = LoggerFactory.getLogger(TfmBinGUI.class);
    private int width;
    private int height;
    private int p;
    private int r;
    public Item item = Items.AIR;
    public String title = "";

    public TfmBinGUI(Item item){
        super(
                Text.of("Tfm Bin Gui")
        );

        this.item = item;

        this.width = MinecraftClient.getInstance().currentScreen.width / 2;
        if (width < 300) this.width = 300;

        this.height = MinecraftClient.getInstance().currentScreen.height / 3 * 2;
        if (height < 225) this.height = 225;

        this.p = 1;
        this.r = 4;

        int screenWidth = MinecraftClient.getInstance().currentScreen.width;
        int screenHeight = MinecraftClient.getInstance().currentScreen.height;

        this.addDrawableChild(new TextWidget(
                screenWidth / 2 - width / 2 + 12,
                screenHeight / 2 - height / 2 + 8,
                width - 12, 10,
                Text.of("§2C§rofl - Auction View"),
                MinecraftClient.getInstance().textRenderer
        ).alignLeft());

        this.addDrawableChild(new MultilineTextWidget(
                screenWidth / 2 - width / 2 + 12,
                screenHeight / 2 - height / 2 + 8 + 8 + 6,
                Text.of(title),
                MinecraftClient.getInstance().textRenderer
        ).setCentered(false));

        this.addDrawableChild(new ItemWidget(
                screenWidth / 2 - 8,
                screenHeight / 2 - 8,
                item.getDefaultStack()
        ));
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float delta){
        int screenWidth = MinecraftClient.getInstance().currentScreen.width;
        int screenHeight = MinecraftClient.getInstance().currentScreen.height;

        RenderUtils.drawRectOutline(
                drawContext,
                screenWidth / 2 - width / 2,
                screenHeight / 2 - height / 2,
                width, height,
                p, TfmColConfig.BACKGROUND_PRIMARY, TfmColConfig.BORDER
        );

        RenderUtils.drawRectOutline(
                drawContext,
                screenWidth / 2 - 80 / 2,
                screenHeight / 2 - 40 / 2,
                80, 40,
                p, TfmColConfig.CONFIRM, TfmColConfig.BORDER
        );

        RenderUtils.drawRectOutline(
                drawContext,
                screenWidth / 2 - 36 / 2,
                screenHeight / 2 + 40 + p,
                36, 30,
                p, TfmColConfig.CANCEL, TfmColConfig.BORDER
        );

    }
}
