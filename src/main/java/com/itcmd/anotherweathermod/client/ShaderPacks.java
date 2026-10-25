package com.itcmd.anotherweathermod.client;

import com.itcmd.anotherweathermod.AnotherWeatherMod;

import java.lang.reflect.Method;

/**
 * Whether an Iris or Oculus shader pack is on, through Iris's public API by reflection (neither is a dependency). A
 * pack draws its own sky, clouds and often rain, and may clash with ours: see the shader compatibility settings.
 */
public final class ShaderPacks {

	private static boolean looked;
	private static Object api;
	private static Method inUse;

	private ShaderPacks() {
	}

	/** Whether a shader pack is in use now. */
	public static boolean inUse() {
		if(!looked) {
			looked = true;
			try {
				Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				api = type.getMethod("getInstance").invoke(null);
				inUse = type.getMethod("isShaderPackInUse");
			} catch(ClassNotFoundException e) {
				api = null; // Neither Iris nor Oculus.
			} catch(ReflectiveOperationException | LinkageError e) {
				AnotherWeatherMod.LOGGER.warn("Could not ask Iris whether a shader pack is on", e);
				api = null;
			}
		}
		if(api == null)
			return false;
		try {
			return (boolean)inUse.invoke(api);
		} catch(ReflectiveOperationException e) {
			api = null;
			return false;
		}
	}
}
