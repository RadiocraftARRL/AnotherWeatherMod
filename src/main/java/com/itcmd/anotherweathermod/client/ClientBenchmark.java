package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.AwmClientConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /awmclient benchmark} (2026-11-18): what each of the mod's effects costs in frame rate where the player
 * stands. With vsync and the frame cap lifted, it measures a few seconds with everything on, then with each effect off
 * in turn, then with all of them off, then everything on again (to see if the weather changed meanwhile), and prints
 * the frame time each one adds. When the game is held back by the graphics card, frame time is what the card spends,
 * so this finds what to make cheaper. Nothing is saved: the settings are put back as they were. Stand still and keep
 * the view the same while it runs.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID, value = Dist.CLIENT)
public final class ClientBenchmark {

	/** Ticks to settle after a change (tiles rebuild, fades), then ticks measured. */
	static final int SETTLE = 30, MEASURE = 60;

	private record Phase(String name, List<ModConfigSpec.BooleanValue> off, boolean vanillaCloudsOff) {}

	private record Result(String name, double ms) {}

	private static List<Phase> phases;
	private static int index = -1, tick;
	private static long frames, startNanos;
	private static final List<Result> results = new ArrayList<>();
	private static boolean vsync;
	private static int frameLimit;
	private static CloudStatus clouds;

	private ClientBenchmark() {
	}

	/** Starts it; false if one is running. */
	static boolean start() {
		if(index >= 0)
			return false;
		List<ModConfigSpec.BooleanValue> all = effects();
		defaults.clear();
		for(ModConfigSpec.BooleanValue value : all)
			if(value.get())
				defaults.add(value);
		phases = new ArrayList<>();
		phases.add(new Phase("everything on", List.of(), false));
		for(ModConfigSpec.BooleanValue value : all)
			if(value.get())
				phases.add(new Phase(name(value) + " off", List.of(value), false));
		phases.add(new Phase("all of the mod's effects off", all, false));
		phases.add(new Phase("all off, and no clouds at all", all, true));
		phases.add(new Phase("everything on again", List.of(), false));
		Minecraft mc = Minecraft.getInstance();
		vsync = mc.options.enableVsync().get();
		frameLimit = mc.options.framerateLimit().get();
		clouds = mc.options.cloudStatus().get();
		mc.options.enableVsync().set(false);
		mc.getWindow().updateVsync(false);
		mc.options.framerateLimit().set(260); // Unlimited.
		results.clear();
		index = 0;
		tick = 0;
		apply(phases.get(0));
		say("Benchmark: " + phases.size() + " phases, about " + phases.size() * (SETTLE + MEASURE) / 20 + " seconds. Keep still.");
		return true;
	}

	private static String name(ModConfigSpec.BooleanValue value) {
		List<String> path = value.getPath();
		return path.get(path.size() - 1);
	}

	private static void apply(Phase phase) {
		for(ModConfigSpec.BooleanValue value : effects())
			value.set(defaults.contains(value));
		for(ModConfigSpec.BooleanValue value : phase.off())
			value.set(false);
		Minecraft.getInstance().options.cloudStatus().set(phase.vanillaCloudsOff() ? CloudStatus.OFF : clouds);
	}

	/** The values that were on when it started, to put back. */
	private static final List<ModConfigSpec.BooleanValue> defaults = new ArrayList<>();

	/** The effects it measures, each a setting that turns one off. */
	private static List<ModConfigSpec.BooleanValue> effects() {
		return List.of(AwmClientConfig.CUSTOM_CLOUDS, AwmClientConfig.STORM_RAIN, AwmClientConfig.RAIN_WALLS,
				AwmClientConfig.STORM_CLOUDS, AwmClientConfig.STORM_FOG, AwmClientConfig.STORM_HORIZON,
				AwmClientConfig.LIGHTNING_IN_CLOUDS, AwmClientConfig.STRATUS, AwmClientConfig.RAIN_ONSET, AwmClientConfig.PUDDLES,
				AwmClientConfig.MIST, AwmClientConfig.WHIRLS, AwmClientConfig.TORNADO_FUNNELS, AwmClientConfig.WIND_STREAKS);
	}

	@SubscribeEvent
	public static void onFrame(RenderFrameEvent.Post event) {
		if(index >= 0 && tick >= SETTLE)
			frames++;
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		if(index < 0)
			return;
		if(Minecraft.getInstance().level == null) {
			finish(false);
			return;
		}
		tick++;
		if(tick == SETTLE) {
			frames = 0;
			startNanos = System.nanoTime();
		}
		if(tick < SETTLE + MEASURE)
			return;
		double seconds = (System.nanoTime() - startNanos) / 1e9;
		double fps = frames / Math.max(1e-3, seconds);
		results.add(new Result(phases.get(index).name(), 1000 / Math.max(1e-3, fps)));
		index++;
		tick = 0;
		if(index >= phases.size())
			finish(true);
		else
			apply(phases.get(index));
	}

	private static void finish(boolean report) {
		Minecraft mc = Minecraft.getInstance();
		if(phases != null)
			for(ModConfigSpec.BooleanValue value : effects())
				value.set(defaults.contains(value));
		mc.options.enableVsync().set(vsync);
		mc.getWindow().updateVsync(vsync);
		mc.options.framerateLimit().set(frameLimit);
		mc.options.cloudStatus().set(clouds);
		index = -1;
		if(!report || results.isEmpty())
			return;
		double base = results.get(0).ms();
		say(String.format(Locale.ROOT, "Benchmark: %.2f ms a frame (%.0f fps) with everything on.", base, 1000 / base));
		for(int i = 1; i < results.size(); i++) {
			Result r = results.get(i);
			say(String.format(Locale.ROOT, "  %-34s %6.2f ms (%4.0f fps)  %+.2f ms", r.name(), r.ms(), 1000 / r.ms(), base - r.ms()));
		}
		say("The last column is what each saves when off. If 'everything on again' differs a lot from the first, the weather changed meanwhile.");
	}

	private static void say(String text) {
		Minecraft mc = Minecraft.getInstance();
		if(mc.player != null)
			mc.player.displayClientMessage(Component.literal(text), false);
		AnotherWeatherMod.LOGGER.info(text);
	}

}
