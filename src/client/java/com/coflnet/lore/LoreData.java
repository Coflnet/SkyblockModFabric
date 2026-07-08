package com.coflnet.lore;

/**
 * parsed numeric fields extracted from a single items backend lore lines.
 *  
 * the coflnet backend appends pre formatted lore strings such as
 *  7lbin e690 000 or 7buy 6743.74k 7sell 6700k . the lore template
 * engine parses those strings back into these tokens so the user can re render
 * each line from a custom template like l clowest ebin r lbin .
 *  
 * every value is a double so fractional figures bazaar prices like 0.1 low
 * volumes like 0.7 survive. a null value means the field was not present for
 * that item which lets a template hide a module when there is no data.
 */
public class LoreData {
    public Double lbin;
    public Double lbinEach;
    public boolean lbinEstimate;

    public Double median;
    public Double medianEach;
    public boolean medianEstimate;

    public Double volume;        // auction volume single number 

    public Double craftCost;
    public Double cleanCraft;     // clean craft value on the craft line
    public Double modifierCost;   // modifier cost line
    public Double obtainCost;
    public boolean notCraftable;

    public Double buy;
    public Double buyEach;
    public Double sell;
    public Double sellEach;

    public Double buyVol;        // bazaar buy volume
    public Double sellVol;       // bazaar sell volume

    public Double purchasedFor;  // coins you paid for this item from ah history 

    /** true when at least one field was parsed the item has cofl price data . */
    public boolean hasAny() {
        return lbin != null || median != null || craftCost != null || obtainCost != null
                || buy != null || sell != null || volume != null || buyVol != null;
    }

    /** returns the numeric value for a token name or null if absent. */
    public Double get(String token) {
        return switch (token.toLowerCase(java.util.Locale.ROOT)) {
            case "lbin" -> lbin;
            case "lbin_each" -> lbinEach;
            case "median", "med" -> median;
            case "median_each", "med_each" -> medianEach;
            case "volume", "vol" -> volume;
            case "craftcost", "craft" -> craftCost;
            case "cleancraft", "clean" -> cleanCraft;
            case "modifiercost", "modcost", "modifier" -> modifierCost;
            case "obtaincost", "obtain" -> obtainCost;
            case "buy" -> buy;
            case "buy_each" -> buyEach;
            case "sell" -> sell;
            case "sell_each" -> sellEach;
            case "buyvol" -> buyVol;
            case "sellvol" -> sellVol;
            case "purchased", "purchasedfor", "paid" -> purchasedFor;
            default -> null;
        };
    }
}
