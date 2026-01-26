package com.coflnet.mixin;

import com.coflnet.CoflModClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to detect Hypixel scoreboard updates via network packets.
 * Hypixel uses team prefixes/suffixes for the actual displayed text,
 * and scores only for ordering. This approach is more accurate than
 * mixing into Scoreboard class methods.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ScoreboardMixin {
    
    /**
     * Detects when team data changes (prefix/suffix text updates).
     * Hypixel uses teams like "team_0", "team_1", etc. for sidebar lines.
     */
    @Inject(method = "onTeam", at = @At("TAIL"))
    private void onTeamPacket(TeamS2CPacket packet, CallbackInfo ci) {
        // Hypixel uses team names starting with "team_" for scoreboard lines
        String teamName = packet.getTeamName();
        if (teamName != null && teamName.startsWith("team_")) {
            CoflModClient.markScoreboardDirty();
        }
    }
    
    /**
     * Detects when score values change (used for ordering).
     * The "update" objective is commonly used by Hypixel.
     */
    @Inject(method = "onScoreboardScoreUpdate", at = @At("TAIL"))
    private void onScorePacket(ScoreboardScoreUpdateS2CPacket packet, CallbackInfo ci) {
        String objectiveName = packet.objectiveName();
        if (objectiveName != null && objectiveName.equals("update")) {
            CoflModClient.markScoreboardDirty();
        }
    }
    
    /**
     * Detects when the scoreboard objective itself changes (title updates).
     */
    @Inject(method = "onScoreboardObjectiveUpdate", at = @At("TAIL"))
    private void onObjectivePacket(ScoreboardObjectiveUpdateS2CPacket packet, CallbackInfo ci) {
        // Objective updates can indicate scoreboard title changes
        CoflModClient.markScoreboardDirty();
    }
}
