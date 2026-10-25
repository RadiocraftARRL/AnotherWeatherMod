package com.itcmd.anotherweathermod.command;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.api.StormArrival;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.WeatherSample;
import com.itcmd.anotherweathermod.api.event.StormEvent;
import com.itcmd.anotherweathermod.sim.StormCell;
import com.itcmd.anotherweathermod.sim.StormProfile;
import com.itcmd.anotherweathermod.world.LevelWeather;
import com.itcmd.anotherweathermod.world.WeatherManager;
import com.itcmd.anotherweathermod.api.AnotherWeatherApi;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /awm}: look at and steer the weather. {@code status}, {@code here}, {@code list} and {@code forecast} read;
 * {@code spawn}, {@code kill} and {@code clear} change it.
 */
@EventBusSubscriber(modid = AnotherWeatherMod.MOD_ID)
public final class AwmCommand {

	private static final SimpleCommandExceptionType NOT_MANAGED = new SimpleCommandExceptionType(
			Component.literal("This dimension has no localized weather (see the anotherweathermod server config)."));
	private static final SimpleCommandExceptionType NO_STORM = new SimpleCommandExceptionType(
			Component.literal("No storm with that id."));

	private AwmCommand() {
	}

	@SubscribeEvent
	public static void onRegister(RegisterCommandsEvent event) {
		register(event.getDispatcher());
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("awm")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("status").executes(AwmCommand::status))
				.then(Commands.literal("here").executes(AwmCommand::here))
				.then(Commands.literal("list").executes(AwmCommand::list))
				.then(Commands.literal("forecast").executes(AwmCommand::forecast))
				.then(Commands.literal("clear").executes(AwmCommand::clear))
				.then(Commands.literal("kill")
						.then(Commands.argument("id", LongArgumentType.longArg(1)).executes(AwmCommand::kill)))
				.then(Commands.literal("spawn")
						.then(Commands.argument("kind", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(StormKind.values())
										.map(k -> k.name().toLowerCase(Locale.ROOT)), b))
								.executes(c -> spawn(c, -1, -1, -1))
								.then(Commands.argument("intensity", FloatArgumentType.floatArg(0.05f, 1))
										.executes(c -> spawn(c, FloatArgumentType.getFloat(c, "intensity"), -1, -1))
										.then(Commands.argument("radius", DoubleArgumentType.doubleArg(16, 4000))
												.executes(c -> spawn(c, FloatArgumentType.getFloat(c, "intensity"),
														DoubleArgumentType.getDouble(c, "radius"), -1))
												.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 24 * 60))
														.executes(c -> spawn(c, FloatArgumentType.getFloat(c, "intensity"),
																DoubleArgumentType.getDouble(c, "radius"),
																IntegerArgumentType.getInteger(c, "minutes")))))))));
	}

	private static LevelWeather weather(CommandSourceStack source) throws CommandSyntaxException {
		LevelWeather weather = WeatherManager.get(source.getLevel());
		if(weather == null)
			throw NOT_MANAGED.create();
		return weather;
	}

	private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		LevelWeather weather = weather(c.getSource());
		long now = c.getSource().getLevel().getGameTime();
		var wind = weather.field().wind();
		double toward = Math.toDegrees(wind.direction(now));
		double from = ((toward + 180) % 360 + 360) % 360;
		send(c, String.format(Locale.ROOT, "%d storms, %d forecast. Steering wind from %.0f° at %.1f m/s.%s",
				weather.field().active().size(), weather.field().pending().size(), from, wind.speed(now),
				weather.overrideRain() ? " /weather override is on." : ""));
		return 1;
	}

	private static int here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		LevelWeather weather = weather(c.getSource());
		BlockPos pos = BlockPos.containing(c.getSource().getPosition());
		WeatherSample s = weather.sample(pos, c.getSource().getLevel().getGameTime());
		send(c, String.format(Locale.ROOT,
				"%s, rain %.2f, thunder %.2f, wind from %.0f° at %.1f m/s (Beaufort %d), %.1f °C, %.1f hPa, %.0f %%%s%s",
				s.precipitation().name().toLowerCase(Locale.ROOT), s.rainLevel(), s.thunderLevel(), s.windFromDegrees(),
				s.windSpeed(), s.beaufort(), s.temperature(), s.pressure(), s.humidity(),
				s.inStorm() ? ", storm #" + s.stormId() : "",
				s.hazards().isEmpty() ? "" : ", " + hazards(s)));
		return 1;
	}

	private static String hazards(WeatherSample s) {
		return String.join(", ", s.hazards().stream().map(h -> h.name().toLowerCase(Locale.ROOT)).sorted().toList());
	}

	private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		LevelWeather weather = weather(c.getSource());
		long now = c.getSource().getLevel().getGameTime();
		Vec3 here = c.getSource().getPosition();
		List<StormCell> cells = weather.field().all();
		if(cells.isEmpty()) {
			send(c, "No storms, none forecast.");
			return 0;
		}
		cells.stream().sorted(Comparator.comparingDouble(cell -> cell.centerAt(Math.max(now, cell.birthTick()))
				.distanceToSqr(here.x, 0, here.z))).forEach(cell -> {
			long t = Math.max(now, cell.birthTick());
			Vec3 center = cell.centerAt(t);
			Vec3 v = cell.velocityAt(t);
			String when = now < cell.birthTick() ? "forms in " + minutes(cell.birthTick() - now)
					: cell.stageAt(now).name().toLowerCase(Locale.ROOT) + ", ends in " + minutes(cell.deathTick() - now);
			send(c, String.format(Locale.ROOT, "#%d %s at %.0f %.0f (%.0f blocks away), r %.0f, intensity %.2f, moving %.1f blocks/s, %s",
					cell.id(), cell.kind().name().toLowerCase(Locale.ROOT), center.x, center.z,
					Math.sqrt(center.distanceToSqr(here.x, 0, here.z)), Math.max(cell.radiusAt(t), cell.peakRadius() * 0.4),
					cell.peakIntensity(), v.horizontalDistance() * 20, when));
		});
		return cells.size();
	}

	private static int forecast(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		weather(c.getSource());
		ServerLevel level = c.getSource().getLevel();
		BlockPos pos = BlockPos.containing(c.getSource().getPosition());
		AnotherWeatherApi api = AnotherWeatherApi.get();
		Optional<StormArrival> next = api.nextArrival(level, pos, api.forecastHorizonTicks(level));
		if(next.isEmpty()) {
			send(c, "No storm expected here in the next " + minutes(api.forecastHorizonTicks(level)) + ".");
			return 0;
		}
		StormArrival a = next.get();
		long now = level.getGameTime();
		WeatherSample peak = a.peak();
		String arrives = a.arrivalTick() <= now ? "here now" : "arrives in " + minutes(a.arrivalTick() - now);
		send(c, String.format(Locale.ROOT, "%s #%d %s, lasts %s; worst in %s: %s, wind %.0f m/s%s",
				a.storm().kind().name().toLowerCase(Locale.ROOT), a.storm().id(), arrives,
				minutes(a.departureTick() - Math.max(now, a.arrivalTick())), minutes(Math.max(0, a.peakTick() - now)),
				peak.precipitation().name().toLowerCase(Locale.ROOT), peak.windSpeed(),
				peak.hazards().isEmpty() ? "" : ", " + hazards(peak)));
		return 1;
	}

	private static int spawn(CommandContext<CommandSourceStack> c, float intensity, double radius, int minutes)
			throws CommandSyntaxException {
		LevelWeather weather = weather(c.getSource());
		StormKind kind;
		try {
			kind = StormKind.valueOf(StringArgumentType.getString(c, "kind").toUpperCase(Locale.ROOT));
		}
		catch(IllegalArgumentException e) {
			throw new SimpleCommandExceptionType(Component.literal("Unknown storm kind.")).create();
		}
		StormProfile p = StormProfile.of(kind);
		ServerLevel level = c.getSource().getLevel();
		long now = level.getGameTime();
		Vec3 pos = c.getSource().getPosition();
		int life = minutes > 0 ? minutes * 1200 : p.maxLifetime();
		StormCell cell = StormCell.matureAt(kind, pos.x, pos.z, now, life,
				radius > 0 ? radius : (p.minRadius() + p.maxRadius()) / 2.0,
				intensity > 0 ? intensity : p.maxIntensity(), level.random.nextLong(), weather.field().wind());
		StormCell placed = weather.field().add(cell, now);
		weather.markDirty();
		NeoForge.EVENT_BUS.post(new StormEvent.Formed(level, placed));
		send(c, "Spawned " + kind.name().toLowerCase(Locale.ROOT) + " #" + placed.id() + ".");
		return 1;
	}

	private static int kill(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		LevelWeather weather = weather(c.getSource());
		long id = LongArgumentType.getLong(c, "id");
		StormCell cell = weather.field().find(id);
		if(cell == null || !weather.field().remove(id))
			throw NO_STORM.create();
		weather.markDirty();
		NeoForge.EVENT_BUS.post(new StormEvent.Dissipated(c.getSource().getLevel(), cell));
		send(c, "Removed storm #" + id + ".");
		return 1;
	}

	private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		LevelWeather weather = weather(c.getSource());
		List<StormCell> removed = weather.field().clear();
		weather.markDirty();
		for(StormCell cell : removed)
			NeoForge.EVENT_BUS.post(new StormEvent.Dissipated(c.getSource().getLevel(), cell));
		send(c, "Removed " + removed.size() + " storms. The forecast will be decided again.");
		return removed.size();
	}

	private static String minutes(long ticks) {
		long seconds = ticks / 20;
		if(seconds < 90)
			return seconds + " s";
		return Math.round(seconds / 60.0) + " min";
	}

	private static void send(CommandContext<CommandSourceStack> c, String text) {
		c.getSource().sendSuccess(() -> Component.literal(text), false);
	}
}
