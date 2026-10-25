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
import com.itcmd.anotherweathermod.world.WindDamage;
import com.itcmd.anotherweathermod.api.Hazard;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.concurrent.CompletableFuture;
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
 * {@code spawn}, {@code approach}, {@code hazard}, {@code calm}, {@code kill} and {@code clear} change it; {@code windburst} and
 * {@code regrow} test wind damage and regrowth.
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
				.then(Commands.literal("calm").executes(AwmCommand::calm))
				.then(Commands.literal("regrow").executes(AwmCommand::regrow))
				.then(Commands.literal("approach")
						.then(Commands.argument("kind", StringArgumentType.word()).suggests(AwmCommand::suggestKinds)
								.executes(c -> approach(c, 2))
								.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 120))
										.executes(c -> approach(c, IntegerArgumentType.getInteger(c, "minutes"))))))
				.then(Commands.literal("hazard")
						.then(Commands.argument("hazard", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(HAZARDS, b))
								.executes(AwmCommand::hazard)))
				.then(Commands.literal("windburst")
						.then(Commands.argument("speed", DoubleArgumentType.doubleArg(0, 120))
								.executes(c -> windburst(c, 16))
								.then(Commands.argument("radius", IntegerArgumentType.integer(1, 48))
										.executes(c -> windburst(c, IntegerArgumentType.getInteger(c, "radius"))))))
				.then(Commands.literal("kill")
						.then(Commands.argument("id", LongArgumentType.longArg(1)).executes(AwmCommand::kill)))
				.then(Commands.literal("spawn")
						.then(Commands.argument("kind", StringArgumentType.word()).suggests(AwmCommand::suggestKinds)
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

	private static final List<String> HAZARDS = List.of("end", "severe", "hail", "blizzard", "sandstorm", "tornado",
			"tornado_0", "tornado_1", "tornado_2", "tornado_3", "tornado_4", "tornado_5",
			"hurricane", "hurricane_0", "hurricane_1", "hurricane_2", "hurricane_3", "hurricane_4", "hurricane_5", "eye",
			"fog", "heat", "cold");

	private static CompletableFuture<Suggestions> suggestKinds(CommandContext<CommandSourceStack> c,
			SuggestionsBuilder b) {
		return SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(Arrays.stream(StormKind.values())
				.map(k -> k.name().toLowerCase(Locale.ROOT)), java.util.stream.IntStream.rangeClosed(0, 5)
				.mapToObj(n -> "hurricane_" + n + " tornado_" + n).flatMap(s -> Arrays.stream(s.split(" ")))), b);
	}

	private static StormKind kind(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String name = StringArgumentType.getString(c, "kind").toUpperCase(Locale.ROOT);
		if(name.startsWith("HURRICANE_"))
			name = "HURRICANE";
		else if(name.startsWith("TORNADO_"))
			name = "TORNADO";
		try {
			return StormKind.valueOf(name);
		}
		catch(IllegalArgumentException e) {
			throw new SimpleCommandExceptionType(Component.literal("Unknown storm kind.")).create();
		}
	}

	/**
	 * The category in a {@code hurricane_N} kind or the rating in a {@code tornado_N}, or -1 to let the storm draw its
	 * own.
	 */
	private static int category(String token) {
		int at = token.lastIndexOf('_');
		String lower = token.toLowerCase(Locale.ROOT);
		if(!lower.startsWith("hurricane_") && !lower.startsWith("tornado_") || at < 0)
			return -1;
		try {
			return Math.max(0, Math.min(5, Integer.parseInt(token.substring(at + 1))));
		}
		catch(NumberFormatException e) {
			return -1;
		}
	}

	/** {@code cell} in the category the command asked for, if it asked. */
	private static StormCell withCategory(CommandContext<CommandSourceStack> c, StormCell cell) {
		int category = category(StringArgumentType.getString(c, "kind"));
		return category < 0 ? cell : cell.withCategory(category);
	}

	/** Adds a storm and tells listeners. */
	private static StormCell place(CommandContext<CommandSourceStack> c, LevelWeather weather, StormCell cell) {
		ServerLevel level = c.getSource().getLevel();
		StormCell placed = weather.field().add(cell, level.getGameTime());
		weather.markDirty();
		NeoForge.EVENT_BUS.post(level.getGameTime() >= placed.birthTick() ? new StormEvent.Formed(level, placed)
				: new StormEvent.Forecast(level, placed));
		return placed;
	}

	/** A storm that forms upwind now and reaches you in {@code minutes}: for testing forecasts and approaching storms. */
	private static int approach(CommandContext<CommandSourceStack> c, int minutes) throws CommandSyntaxException {
		LevelWeather weather = weather(c.getSource());
		StormKind kind = kind(c);
		StormProfile p = StormProfile.of(kind);
		ServerLevel level = c.getSource().getLevel();
		long now = level.getGameTime();
		Vec3 pos = c.getSource().getPosition();
		StormCell cell = withCategory(c, StormCell.arrivingAt(kind, pos.x, pos.z, now, now + minutes * 1200L,
				p.maxLifetime(), (p.minRadius() + p.maxRadius()) / 2.0, p.maxIntensity(), level.random.nextLong(),
				weather.field().wind()));
		StormCell placed = place(c, weather, cell);
		Vec3 center = placed.centerAt(now);
		send(c, String.format(Locale.ROOT, "%s #%d formed %.0f blocks away and reaches you in about %d min.",
				kind.name().toLowerCase(Locale.ROOT), placed.id(), Math.sqrt(center.distanceToSqr(pos.x, 0, pos.z)),
				minutes));
		return 1;
	}

	/** Puts you in a hazard at once. Blizzards and sandstorms need a snowy or a dry biome, and it says so if not. */
	private static int hazard(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		LevelWeather weather = weather(c.getSource());
		String hazard = StringArgumentType.getString(c, "hazard").toLowerCase(Locale.ROOT);
		if(hazard.equals("end"))
			return calm(c);
		ServerLevel level = c.getSource().getLevel();
		long now = level.getGameTime();
		Vec3 pos = c.getSource().getPosition();
		var wind = weather.field().wind();
		long seed = level.random.nextLong();
		int category = hazard.contains("_") ? category(hazard) : -1;
		if(hazard.startsWith("hurricane_"))
			hazard = "hurricane";
		else if(hazard.startsWith("tornado_"))
			hazard = "tornado";
		int hurricaneCategory = category < 0 ? 3 : category;
		StormCell cell = switch(hazard) {
			// Halfway out, where the gust front blows hardest.
			case "severe" -> StormCell.matureAt(StormKind.SEVERE_THUNDERSTORM, pos.x - 100, pos.z, now, 12000, 200, 1f,
					seed, wind);
			case "hail", "blizzard", "sandstorm" -> StormCell.matureAt(StormKind.SUPERCELL, pos.x, pos.z, now, 12000,
					160, 1f, seed, wind);
			case "eye" -> StormCell.matureAt(StormKind.HURRICANE, pos.x, pos.z, now, 48000, 800, 1f, seed, wind)
					.withCategory(hurricaneCategory);
			// Centred so you stand in the eyewall, where the wind is worst.
			case "hurricane" -> StormCell.matureAt(StormKind.HURRICANE, pos.x - 800 * 0.14, pos.z, now, 48000, 800, 1f,
					seed, wind).withCategory(hurricaneCategory);
			case "tornado" -> tornadoUpwind(pos, now, seed, wind, category);
			case "fog" -> StormCell.matureAt(StormKind.DENSE_FOG, pos.x, pos.z, now, 24000, 400, 1f, seed, wind);
			case "heat" -> StormCell.matureAt(StormKind.HEAT_WAVE, pos.x, pos.z, now, 24000, 1800, 1f, seed, wind);
			case "cold" -> StormCell.matureAt(StormKind.COLD_SNAP, pos.x, pos.z, now, 24000, 1800, 1f, seed, wind);
			default -> throw new SimpleCommandExceptionType(
					Component.literal("Hazards: " + String.join(", ", HAZARDS))).create();
		};
		StormCell placed = place(c, weather, cell);
		WeatherSample s = weather.sample(BlockPos.containing(pos), now);
		String note = hazard.equals("blizzard") && !s.has(Hazard.BLIZZARD) ? " (blizzards need a snowy biome)"
				: hazard.equals("sandstorm") && !s.has(Hazard.SANDSTORM) ? " (sandstorms need a dry biome)"
				: hazard.equals("heat") && !s.has(Hazard.HEAT_WAVE) ? " (heat waves need dry ground, by day)"
				: hazard.equals("cold") && !s.has(Hazard.COLD_SNAP) ? " (cold snaps need snowy ground, at night)"
				: hazard.equals("tornado") ? " (an EF" + placed.tornadoRating()
						+ " tornado is down 120 blocks upwind and coming your way)" : "";
		send(c, "Storm #" + placed.id() + ". Here now: " + s.precipitation().name().toLowerCase(Locale.ROOT)
				+ (s.hazards().isEmpty() ? ", no hazards" : ", " + hazards(s)) + note + ".");
		return 1;
	}

	/**
	 * A tornadic storm with its tornado down 120 blocks upwind of {@code pos}, on a track that crosses it in a few
	 * minutes.
	 */
	private static StormCell tornadoUpwind(Vec3 pos, long now, long seed,
			com.itcmd.anotherweathermod.sim.SteeringWind wind, int rating) {
		StormCell cell = StormCell.atLife(StormKind.TORNADO, pos.x, pos.z, now, 0.4, 36000, 240, 1f, seed, wind);
		if(rating >= 0)
			cell = cell.withCategory(rating);
		double vx = cell.velocityX(now), vz = cell.velocityZ(now);
		double v = Math.max(1e-6, Math.hypot(vx, vz));
		double aimX = pos.x - vx / v * 120, aimZ = pos.z - vz / v * 120;
		return cell.shifted(aimX - cell.tornadoX(now), aimZ - cell.tornadoZ(now));
	}

	/** The wind damages the surface around you now, storm or not: for testing damage and regrowth. */
	private static int windburst(CommandContext<CommandSourceStack> c, int radius) throws CommandSyntaxException {
		LevelWeather weather = weather(c.getSource());
		double speed = DoubleArgumentType.getDouble(c, "speed");
		ServerLevel level = c.getSource().getLevel();
		BlockPos center = BlockPos.containing(c.getSource().getPosition());
		long now = level.getGameTime();
		double windX = weather.field().wind().x(now);
		double windZ = weather.field().wind().z(now);
		int damaged = 0;
		for(int dx = -radius; dx <= radius; dx++)
			for(int dz = -radius; dz <= radius; dz++) {
				if(dx * dx + dz * dz > radius * radius)
					continue;
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, center.offset(dx, 0, dz));
				for(int down = 0; down < 6; down++) // Down through a tree's crown.
					if(WindDamage.apply(level, top.below(down), speed, windX, windZ))
						damaged++;
			}
		send(c, "Wind at " + speed + " m/s damaged " + damaged + " blocks; " + weather.regrowth().size()
				+ " are waiting to grow back.");
		return damaged;
	}

	private static int regrow(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		LevelWeather weather = weather(c.getSource());
		int count = weather.regrowth().size();
		weather.regrowth().hurry(c.getSource().getLevel().getGameTime());
		send(c, count + " damaged blocks grow back now (a few dozen a second, where no storm is overhead).");
		return count;
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
					cell.id(), cell.kind().name().toLowerCase(Locale.ROOT)
							+ (cell.kind() == StormKind.HURRICANE ? " category " + cell.category()
							: cell.kind() == StormKind.TORNADO ? " EF" + cell.tornadoRating() : ""), center.x, center.z,
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
		StormKind kind = kind(c);
		StormProfile p = StormProfile.of(kind);
		ServerLevel level = c.getSource().getLevel();
		long now = level.getGameTime();
		Vec3 pos = c.getSource().getPosition();
		int life = minutes > 0 ? minutes * 1200 : p.maxLifetime();
		StormCell cell = withCategory(c, StormCell.matureAt(kind, pos.x, pos.z, now, life,
				radius > 0 ? radius : (p.minRadius() + p.maxRadius()) / 2.0,
				intensity > 0 ? intensity : p.maxIntensity(), level.random.nextLong(), weather.field().wind()));
		StormCell placed = place(c, weather, cell);
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

	/**
	 * Ends the weather where you are: removes every storm whose rain or wind reaches you. Storms elsewhere and the
	 * forecast stay; {@code clear} removes those too.
	 */
	private static int calm(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		LevelWeather weather = weather(c.getSource());
		ServerLevel level = c.getSource().getLevel();
		long now = level.getGameTime();
		Vec3 pos = c.getSource().getPosition();
		int removed = 0;
		for(StormCell cell : weather.field().active())
			if(cell.reaches(pos.x, pos.z, now) && weather.field().remove(cell.id())) {
				NeoForge.EVENT_BUS.post(new StormEvent.Dissipated(level, cell));
				removed++;
			}
		weather.markDirty();
		send(c, removed == 0 ? "No storm reaches you here." : "Removed " + removed + (removed == 1 ? " storm" : " storms")
				+ " over you. The rain fades out over a few seconds.");
		return removed;
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
