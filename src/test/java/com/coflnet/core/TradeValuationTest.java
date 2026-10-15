package com.coflnet.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TradeValuationTest {
    @Test void readsAuctionAndBazaarPerItemValues() {
        assertEquals(12_500_000L, TradeValuation.parseWorthFromTips(
                new String[]{"§7Lowest BIN: §612.5m", "§7Med: §6611.8m"}, TradeValuation.WorthBasis.LBIN));
        String bazaar = "Buy: 37.49K (585.8 each)Sell: 33.38K (521.6 each)";
        assertEquals(585L, TradeValuation.parseWorthFromTips(new String[]{bazaar}, TradeValuation.WorthBasis.LBIN));
        assertEquals(521L, TradeValuation.parseWorthFromTips(new String[]{bazaar}, TradeValuation.WorthBasis.MEDIAN));
    }

    @Test void prefersMatchingMarketQuotesThenAiThenApproximateMedian() {
        String[] tips = {"lbin: 100m", "Med: 90m", "AI Estimate: 150m"};
        assertEquals(100_000_000L, TradeValuation.parseWorthFromTips(tips, TradeValuation.WorthBasis.LBIN));
        assertEquals(90_000_000L, TradeValuation.parseWorthFromTips(tips, TradeValuation.WorthBasis.MEDIAN));
        assertEquals(90_000_000L, TradeValuation.parseWorthFromTips(
                new String[]{"lbin: ~1m (estimate, no match found)", "Med: 90m", "AI Estimate: 150m"}, TradeValuation.WorthBasis.LBIN));
        assertEquals(90_000_000L, TradeValuation.parseWorthFromTips(
                new String[]{"lbin: ~1m (estimate, no match found)", "Med: ~90m", "AI Estimate: none"}, TradeValuation.WorthBasis.LBIN));
        assertNull(TradeValuation.parseWorthFromTips(
                new String[]{"lbin: ~1m (estimate, no match found)", "Full Craft Cost: 100m", "Paid: 80m"}, TradeValuation.WorthBasis.LBIN));
    }

    @Test void usesPerItemAuctionQuotesInsteadOfMultiplyingStackTotalsAgain() {
        String[] tips = {"lbin: 6,400,000 (100,000 each)", "Med: 5,120,000 (80,000 each)Vol: 12"};
        assertEquals(100_000L, TradeValuation.parseWorthFromTips(tips, TradeValuation.WorthBasis.LBIN, 64));
        assertEquals(80_000L, TradeValuation.parseWorthFromTips(tips, TradeValuation.WorthBasis.MEDIAN, 64));
        assertEquals(100_000L, TradeValuation.parseWorthFromTips(
                new String[]{"AI Estimate: 6,400,000"}, TradeValuation.WorthBasis.LBIN, 64));
        assertEquals(100_000L, TradeValuation.parseWorthFromTips(
                new String[]{"lbin: 6,400,000"}, TradeValuation.WorthBasis.LBIN, 64));
    }

    @Test void missingOrZeroQuotesRemainUnpriced() {
        assertNull(TradeValuation.parseWorthFromTips(null, TradeValuation.WorthBasis.LBIN));
        assertNull(TradeValuation.parseWorthFromTips(new String[]{null, "AI Estimate: none", "Med: 0", "Buy: 0"}, TradeValuation.WorthBasis.LBIN));
        assertNull(TradeValuation.parseWorthFromTips(new String[]{"Med: 2m"}, TradeValuation.WorthBasis.LBIN, 0));
    }

    @Test void aiBasisUsesOnlyAiQuotesAndNormalizesStacks() {
        String[] tips = {"lbin: 100m", "Med: 90m", "§7AI Estimate: §e150m"};
        assertEquals(150_000_000L, TradeValuation.parseWorthFromTips(tips, TradeValuation.WorthBasis.AI_ESTIMATE));
        assertEquals(2_343_750L, TradeValuation.parseWorthFromTips(tips, TradeValuation.WorthBasis.AI_ESTIMATE, 64));
        assertNull(TradeValuation.parseWorthFromTips(new String[]{"lbin: 100m", "Med: 90m", "AI Estimate: none"},
                TradeValuation.WorthBasis.AI_ESTIMATE));
        assertNull(TradeValuation.parseWorthFromTips(new String[]{"Buy: 10 Sell: 9", "AI Estimate: 0"},
                TradeValuation.WorthBasis.AI_ESTIMATE));
    }

    @Test void readsFormattedCoinOffer() {
        assertEquals(1_500_000L, TradeValuation.parseCoinOffer("§61.5m coins"));
        assertNull(TradeValuation.parseCoinOffer("§x1.5m coins"));
        assertNull(TradeValuation.parseCoinOffer("§6Golden Dragon"));
    }

    @Test void totalsPricedAndUnpricedTradeItems() {
        var side = TradeValuation.sum(List.of(1_500_000L, 611_800_000L));
        assertEquals(613_300_000L, side.total());
        assertEquals(0, side.unpriced());
        var withUnpriced = TradeValuation.sum(java.util.Arrays.asList(67L, null));
        assertEquals(67L, withUnpriced.total());
        assertEquals(1, withUnpriced.unpriced());
    }
}
