package com.coflnet.skyblock.testserver;

import com.coflnet.core.TradeValuation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Trade quote parsing regressions reachable through the two-argument API that predates the quote ranking.
 * Lives in the scenario-server test set because it compiles against the production client output alone,
 * without the client's own test sources, so the assertions run on a build that predates the newer API.
 */
class TradeValuationRegressionTest {
    @Test void valuesUpgradedRagnarokUsingItemEstimateInsteadOfUnmatchedBaseLbin() {
        String[] screenshot = {
                "§7lbin: §e~1,200,000 §8(estimate, no match found)",
                "§7Med: §b~245,750,502 §7Vol: §e0.4",
                "§7Full Craft Cost: §e347.68M",
                "§7AI Estimate: §e293,633,280",
                "§7Paid: §e290,000,000 §88d ago"
        };
        for (var basis : TradeValuation.WorthBasis.values()) {
            assertEquals(293_633_280L, TradeValuation.parseWorthFromTips(screenshot, basis));
            assertEquals(293_633_280L, TradeValuation.parseWorthFromTips(
                    new String[]{String.join(" ", screenshot)}, basis));
            var reversed = java.util.Arrays.asList(screenshot.clone());
            java.util.Collections.reverse(reversed);
            assertEquals(293_633_280L, TradeValuation.parseWorthFromTips(reversed.toArray(String[]::new), basis));
        }
    }

    @Test void supportsSingleBazaarItemsAndMultiplePricesOnOneLine() {
        String[] bazaar = {"§7Buy: §e585.8 §7Sell: §e521.6 §7Vol: 14/12"};
        assertEquals(585L, TradeValuation.parseWorthFromTips(bazaar, TradeValuation.WorthBasis.LBIN));
        assertEquals(521L, TradeValuation.parseWorthFromTips(bazaar, TradeValuation.WorthBasis.MEDIAN));
        assertEquals(123_000L, TradeValuation.parseWorthFromTips(
                new String[]{"Med: 123k Vol: 0.4 lbin: ~5k (higher value found)"}, TradeValuation.WorthBasis.LBIN));
    }
}
