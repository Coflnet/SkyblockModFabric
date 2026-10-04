package com.coflnet.core;

/** Pure visibility rule for the HUD info displays (and their hover tooltips); kept free of Minecraft types for testing. */
public final class InfoDisplayVisibility {
    private InfoDisplayVisibility() {
    }

    /**
     * @param inWorld         a level and player exist (false on the title screen / server list)
     * @param editScreen      the display editor is open (it draws its own previews)
     * @param noScreenOrChat  no screen is open, or the chat screen is
     * @param showInGuis      user setting to also show displays over other GUIs
     */
    public static boolean isVisible(boolean inWorld, boolean editScreen, boolean noScreenOrChat, boolean showInGuis) {
        return inWorld && !editScreen && (noScreenOrChat || showInGuis);
    }
}
