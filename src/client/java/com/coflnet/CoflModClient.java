package com.coflnet;

import CoflCore.CoflCore;
import CoflCore.events.SocketOpen;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.session.Session;
import org.greenrobot.eventbus.Subscribe;
import org.joml.Matrix4f;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class CoflModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.

		HudRenderCallback.EVENT.register((drawContext, tickDeltaManager) -> {
			// Get the transformation matrix from the matrix stack, alongside the tessellator instance and a new buffer builder.
			Matrix4f transformationMatrix = drawContext.getMatrices().peek().getPositionMatrix();
			Tessellator tessellator = Tessellator.getInstance();

			// Begin a triangle strip buffer using the POSITION_COLOR vertex format.
			BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

			// Write our vertices, Z doesn't really matter since it's on the HUD.
			buffer.vertex(transformationMatrix, 60, 20, 5).color(0xFF414141);
			buffer.vertex(transformationMatrix, 50, 40, 5).color(0xFF000000);
			buffer.vertex(transformationMatrix, 355, 40, 5).color(0xFF000000);
			buffer.vertex(transformationMatrix, 205, 60, 5).color(0xFF414141);

			// We'll get to this bit in the next section.
			RenderSystem.setShader(GameRenderer::getPositionColorProgram);
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

			// Draw the buffer onto the screen.
			BufferRenderer.drawWithGlobalProgram(buffer.end());
		});

		CoflCore cofl = new CoflCore();
		try {
			Session s = MinecraftClient.getInstance().getSession();
			String username = s.getUsername();
			Path configDir = FabricLoader.getInstance().getConfigDir();;

			// Create folder "CoflSky" in the config directory if it doesnt exist yet
			Path p = configDir.resolve("CoflSky");
			if (!p.toFile().exists()) {
				p.toFile().mkdir();
			}

			File configFile = new File(p.toFile(), "config.json");

			cofl.setupSocket(username, configFile.toPath());
			cofl.registerEventFile(this);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Subscribe
	public void onMessage(SocketOpen event){
		System.out.println("Socket Opened");
	}
}