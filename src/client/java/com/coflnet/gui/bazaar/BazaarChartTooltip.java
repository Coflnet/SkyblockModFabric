package com.coflnet.gui.bazaar;

import com.coflnet.core.BazaarChart;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record BazaarChartTooltip(BazaarChart chart, boolean buy, boolean volume) implements TooltipComponent { }
