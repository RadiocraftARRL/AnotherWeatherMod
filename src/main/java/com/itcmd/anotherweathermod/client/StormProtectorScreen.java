package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.network.ProtectorPayload;
import com.itcmd.anotherweathermod.world.StormProtection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/** A storm protector's settings: how far round it is kept safe. */
public final class StormProtectorScreen extends Screen {

	/** The radius moves in steps of this many blocks. */
	static final int STEP = 4;

	private final BlockPos pos;
	private int radius;

	private StormProtectorScreen(BlockPos pos, int radius) {
		super(Component.translatable("block.anotherweathermod.storm_protector"));
		this.pos = pos;
		this.radius = radius;
	}

	/** Opens the screen for the protector the server described. */
	public static void open(ProtectorPayload payload) {
		Minecraft.getInstance().setScreen(new StormProtectorScreen(payload.pos(), payload.radius()));
	}

	@Override
	protected void init() {
		int x = width / 2 - 100, y = height / 2 - 24;
		addRenderableWidget(new AbstractSliderButton(x, y, 200, 20, Component.empty(), toSlider(radius)) {
			{
				updateMessage();
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.translatable("gui.anotherweathermod.storm_protector.radius", radius));
			}

			@Override
			protected void applyValue() {
				radius = fromSlider(value);
			}
		});
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, y + 28, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 56, 0xFFFFFF);
		graphics.drawCenteredString(font, Component.translatable("gui.anotherweathermod.storm_protector.help"), width / 2,
				height / 2 - 42, 0xA0A0A0);
	}

	@Override
	public void onClose() {
		PacketDistributor.sendToServer(new ProtectorPayload(pos, radius));
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static double toSlider(int radius) {
		return (radius - StormProtection.MIN_RADIUS) / (double)(StormProtection.MAX_RADIUS - StormProtection.MIN_RADIUS);
	}

	private static int fromSlider(double value) {
		int r = (int)Math.round(Mth.lerp(value, StormProtection.MIN_RADIUS, StormProtection.MAX_RADIUS) / STEP) * STEP;
		return Mth.clamp(r, StormProtection.MIN_RADIUS, StormProtection.MAX_RADIUS);
	}
}
