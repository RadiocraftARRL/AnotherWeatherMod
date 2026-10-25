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
	 * @param category  hurricane category or storm scale, 0 to 5, or -1
	 * @param tornadoX  where its tornado is (if {@code tornado} is above 0)
	 * @param tornado   its tornado's strength, 0 (none down) to 1
	 */
	public record Storm(long id, StormKind kind, double x, double z, float velocityX, float velocityZ, float radius,
			float intensity, int category, double tornadoX, double tornadoZ, float tornadoRadius, float tornado) {
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
			double tornado = cell.tornadoStrength(gameTime);
			storms.add(new Storm(cell.id(), cell.kind(), cell.centerX(gameTime), cell.centerZ(gameTime),
					(float)cell.velocityX(gameTime), (float)cell.velocityZ(gameTime), (float)radius, intensity,
					cell.category(), tornado > 0 ? cell.tornadoX(gameTime) : 0, tornado > 0 ? cell.tornadoZ(gameTime) : 0,
					(float)(cell.tornadoRadius() * (0.6 + 0.4 * tornado)), (float)tornado));
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
			buf.writeByte(s.category());
			buf.writeFloat(s.tornado());
			if(s.tornado() > 0) {
				buf.writeDouble(s.tornadoX());
				buf.writeDouble(s.tornadoZ());
				buf.writeFloat(s.tornadoRadius());
			}
		}
	}

	private static NearbyStormsPayload read(FriendlyByteBuf buf) {
		long gameTime = buf.readLong();
		int count = Math.min(buf.readVarInt(), 256);
		List<Storm> storms = new ArrayList<>(count);
		for(int i = 0; i < count; i++) {
			long id = buf.readVarLong();
			StormKind kind = buf.readEnum(StormKind.class);
			double x = buf.readDouble(), z = buf.readDouble();
			float vx = buf.readFloat(), vz = buf.readFloat(), radius = buf.readFloat(), intensity = buf.readFloat();
			int category = buf.readByte();
			float tornado = buf.readFloat();
			double tx = 0, tz = 0;
			float tr = 0;
			if(tornado > 0) {
				tx = buf.readDouble();
				tz = buf.readDouble();
				tr = buf.readFloat();
			}
			storms.add(new Storm(id, kind, x, z, vx, vz, radius, intensity, category, tx, tz, tr, tornado));
		}
		return new NearbyStormsPayload(gameTime, List.copyOf(storms));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
