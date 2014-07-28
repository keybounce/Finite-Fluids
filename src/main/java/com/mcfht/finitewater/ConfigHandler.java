package com.mcfht.finitewater;

import net.minecraftforge.common.config.Configuration;

public class ConfigHandler {

	public static void handleConfigs(Configuration config)
	{
		config.load();

		FiniteWater.GLOBAL_UPDATE_RATE = config.getInt("globalUpdateRate", "1 - General", 5, 3, 64, "The number of ticks between each update sweep, min of 3");
		FiniteWater.MAX_UPDATES = config.getInt("globalMaxUpdates", "1 - General", 300, 16, 2048, "A factor determining the max number of updates.");
		FiniteWater.FORCE_UPDATES = config.getInt("globalMinUpdates", "1 - General", 48, 16, 2048, "Forced update factor. Lower values benefit large numbers of players.");
		
		FiniteWater.UPDATE_RANGE =  config.getInt("globalUpdateRange", "1 - General", 4, 1, 16, "Distance in which to prioritize updates.");
		FiniteWater.UPDATE_RANGE *= FiniteWater.UPDATE_RANGE;
		
		FiniteWater.PATCH_DOOR_UPDATES = config.getBoolean("patchVanillaDoors", "1 - General", true, "Force vanilla doors to throw block updates when opened (allowing water to flow through them)");
		FiniteWater.PATCH_WATER_DUPLICATION = config.getBoolean("patchWaterDuplication", "1 - General", true, "Prevents duplication of infinite water source blocks!");
		
		FiniteWater.MAX_WATER = (short) config.getInt("waterPrecision", "2 - Fluids", 16384, 256, 16384, "The number of water levels stored per block. Has no direct impact on performance, though different values may work better in some situations");
		
		FiniteWater.WATER_UPDATE = config.getInt("waterUpdateSpeed", "2 - Fluids", 1, 1, 999, "The flow rate of water (relative to global update rate). Higher values result in slower flow but better performance");
		FiniteWater.LAVA_UPDATE = config.getInt("lavaUpdateSpeed", "2 - Fluids", 5, 1, 999, "The relative update rate of Lava. Higher values = slower flow, but allow greater volumes");
		FiniteWater.LAVA_NETHER = config.getInt("lavaUpdateSpeedNether", "2 - Fluids", 2, 1, 999, "The relative update rate of Lava in the NETHER. Higher values = slower flow, but allow greater volumes");
		
		
		
		//Simplify the flow rates. If the user say, doubles all of them, then we 
		//can tick them twice as much, half as often 
		//(for example, if the user sets the tick rate to 3 and then makes water tick every 2nd time,
		//We can piss on their efforts and simply tick once every 6 ticks because who cares.
		
		int min =  (Math.min(FiniteWater.GLOBAL_UPDATE_RATE, Math.min(FiniteWater.LAVA_UPDATE, Math.min(FiniteWater.LAVA_NETHER, FiniteWater.WATER_UPDATE))));
		FiniteWater.GLOBAL_UPDATE_RATE *= min;
		
		FiniteWater.WATER_UPDATE = Math.max(1, FiniteWater.WATER_UPDATE / min);
		FiniteWater.LAVA_UPDATE = Math.max(1, FiniteWater.LAVA_UPDATE / min);
		FiniteWater.LAVA_NETHER = Math.max(1, FiniteWater.LAVA_NETHER / min);
		
		
		
		
		config.save();
	}
	
	
}
