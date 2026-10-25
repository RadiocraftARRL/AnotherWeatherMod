package com.itcmd.anotherweathermod.network;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.api.Hazard;
import com.itcmd.anotherweathermod.api.Precipitation;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.api.WeatherSample;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * The weather at a player, for the mod's client side (hail, fog). Optional: clients without the mod never get it.
 *
 * @param precipitation what falls there
 * @param rain          0..1
 * @param windX         metres per second
 * @param windZ         metres per second
 * @param hazards       dangerous weather there
 * @param stormKind     the strongest storm's kind, or null
 * @param category      that storm's category or scale, 0 to 5, or -1
 * @param fog           0..1, how thick a fog bank is there
 * @param tornado       0..1, how close a tornado is (1 in its core)
 */
public record LocalWeatherPayload(Precipitation precipitation, float rain, float windX, float windZ, Set<Hazard> hazards,
		@Nullable StormKind stormKind, int category, float fog, float tornado) implements CustomPacketPayload {

	public static final Type<LocalWeatherPayload> TYPE = new Type<>(AnotherWeatherMod.id("local_weather"));
	public static final StreamCodec<FriendlyByteBuf, LocalWeatherPayload> STREAM_CODEC = StreamCodec.of(
			(buf, p) -> p.write(buf), LocalWeatherPayload::read);

	public static final LocalWeatherPayload CLEAR = new LocalWeatherPayload(Precipitation.NONE, 0, 0, 0, Set.of(), null, -1,
			0, 0);

	public static LocalWeatherPayload of(WeatherSample sample, @Nullable StormKind kind, int category, double fog,
			double tornado) {
		return new LocalWeatherPayload(sample.precipitation(), sample.rainLevel(), (float)sample.wind().x,
				(float)sample.wind().z, sample.hazards(), kind, category, (float)fog, (float)tornado);
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeEnum(precipitation);
		buf.writeFloat(rain);
		buf.writeFloat(windX);
		buf.writeFloat(windZ);
		int mask = 0;
		for(Hazard hazard : hazards)
			mask |= 1 << hazard.ordinal();
		buf.writeVarInt(mask);
		buf.writeByte(stormKind == null ? -1 : stormKind.ordinal());
		buf.writeByte(category);
		buf.writeFloat(fog);
		buf.writeFloat(tornado);
	}

	private static LocalWeatherPayload read(FriendlyByteBuf buf) {
		Precipitation precipitation = buf.readEnum(Precipitation.class);
		float rain = buf.readFloat();
		float windX = buf.readFloat();
		float windZ = buf.readFloat();
		int mask = buf.readVarInt();
		Set<Hazard> hazards = EnumSet.noneOf(Hazard.class);
		for(Hazard hazard : Hazard.values())
			if((mask & 1 << hazard.ordinal()) != 0)
				hazards.add(hazard);
		int kind = buf.readByte();
		StormKind stormKind = kind >= 0 && kind < StormKind.values().length ? StormKind.values()[kind] : null;
		int category = buf.readByte();
		float fog = buf.readFloat();
		float tornado = buf.readFloat();
		return new LocalWeatherPayload(precipitation, rain, windX, windZ, Set.copyOf(hazards), stormKind, category, fog,
				tornado);
	}

	public boolean has(Hazard hazard) {
		return hazards.contains(hazard);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
