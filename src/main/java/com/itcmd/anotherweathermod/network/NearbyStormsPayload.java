package com.itcmd.anotherweathermod.network;

import com.itcmd.anotherweathermod.AnotherWeatherMod;
import com.itcmd.anotherweathermod.api.StormKind;
import com.itcmd.anotherweathermod.sim.StormCell;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The storms near a player, every two seconds, so the client can draw them from outside: rain walls at their edges and
 * dark cloud over them. Positions are for {@code gameTime}; the client moves them on by their velocity.
 */
public record NearbyStormsPayload(long gameTime, List<Storm> storms) implements CustomPacketPayload {

	public static final Type<NearbyStormsPayload> TYPE = new Type<>(AnotherWeatherMod.id("nearby_storms"));
	public static final StreamCodec<FriendlyByteBuf, NearbyStormsPayload> STREAM_CODEC = StreamCodec.of(
			(buf, p) -> p.write(buf), NearbyStormsPayload::read);

	/** Storms whose edge is within this many blocks of the player are sent. */
	public static final double RANGE = 1500;

	/**
	 * @param velocityX blocks per tick
	 * @param velocityZ blocks per tick
	 */
	public record Storm(long id, StormKind kind, double x, double z, float velocityX, float velocityZ, float radius,
			float intensity) {
	}

	public static NearbyStormsPayload of(List<StormCell> cells, double x, double z, long gameTime) {
		List<Storm> storms = new ArrayList<>();
		for(StormCell cell : cells) {
			float intensity = cell.intensityAt(gameTime);
			if(intensity <= 0)
				continue;
			double radius = cell.radiusAt(gameTime);
			double dx = cell.centerX(gameTime) - x;
			double dz = cell.centerZ(gameTime) - z;
			double reach = radius + RANGE;
			if(dx * dx + dz * dz > reach * reach)
				continue;
			storms.add(new Storm(cell.id(), cell.kind(), cell.centerX(gameTime), cell.centerZ(gameTime),
					(float)cell.velocityX(gameTime), (float)cell.velocityZ(gameTime), (float)radius, intensity));
		}
		return new NearbyStormsPayload(gameTime, List.copyOf(storms));
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeLong(gameTime);
		buf.writeVarInt(storms.size());
		for(Storm s : storms) {
			buf.writeVarLong(s.id());
			buf.writeEnum(s.kind());
			buf.writeDouble(s.x());
			buf.writeDouble(s.z());
			buf.writeFloat(s.velocityX());
			buf.writeFloat(s.velocityZ());
			buf.writeFloat(s.radius());
			buf.writeFloat(s.intensity());
		}
	}

	private static NearbyStormsPayload read(FriendlyByteBuf buf) {
		long gameTime = buf.readLong();
		int count = Math.min(buf.readVarInt(), 256);
		List<Storm> storms = new ArrayList<>(count);
		for(int i = 0; i < count; i++)
			storms.add(new Storm(buf.readVarLong(), buf.readEnum(StormKind.class), buf.readDouble(), buf.readDouble(),
					buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
		return new NearbyStormsPayload(gameTime, List.copyOf(storms));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
