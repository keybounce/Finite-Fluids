package com.mcfht.finitewater;

import net.minecraftforge.common.config.Configuration;

public class ConfigHandler {

	public static void handleConfigs(Configuration config)
	{
		config.load();

		FiniteWater.GLOBAL_UPDATE_RATE = config.getInt("globalUpdateRate", "1 - General", 5, 1, 64, "The global update rate of fluids.");
		FiniteWater.MAX_UPDATES = config.getInt("globalMaxUpdates", "1 - General", 300, 16, 2048, "A factor determining the max number of updates.");
		FiniteWater.FORCE_UPDATES = config.getInt("globalMinUpdates", "1 - General", 48, 16, 2048, "Forced update factor. Lower values benefit large numbers of players.");
		
		FiniteWater.UPDATE_RANGE =  config.getInt("globalUpdateRange", "1 - General", 4, 1, 16, "Distance in which to prioritize updates.");
		FiniteWater.UPDATE_RANGE *= FiniteWater.UPDATE_RANGE;
		
		FiniteWater.PATCH_DOOR_UPDATES = config.getBoolean("patchVanillaDoors", "1 - General", true, "Force vanilla doors to throw block updates when opened (allowing water to flow through them)");
		FiniteWater.PATCH_WATER_DUPLICATION = config.getBoolean("patchWaterDuplication", "1 - General", true, "Prevents duplication of infinite water source blocks!");
		
		FiniteWater.MAX_WATER = config.getInt("waterPrecision", "2 - Fluids", 65536, 256, 65536, "The number of water levels stored per block. Has no direct impact on performance, though different values may work better in some situations");
		
		FiniteWater.WATER_UPDATE = config.getInt("waterUpdateSpeed", "2 - Fluids", 1, 1, 999, "The flow rate of water (relative to global update rate). Higher values will allow greater volumes of water, at the cost of the water flowing slowly");
		FiniteWater.LAVA_UPDATE = config.getInt("lavaUpdateSpeed", "2 - Fluids", 5, 1, 999, "The relative update rate of Lava. Higher values = slower flow, but allow greater volumes");
		FiniteWater.LAVA_NETHER = config.getInt("lavaUpdateSpeedNether", "2 - Fluids", 3, 1, 999, "The relative update rate of Lava. Higher values = slower flow, but allow greater volumes");
		
		int min =  (Math.min(FiniteWater.GLOBAL_UPDATE_RATE, Math.min(FiniteWater.LAVA_UPDATE, Math.min(FiniteWater.LAVA_NETHER, FiniteWater.WATER_UPDATE))));
		
		FiniteWater.GLOBAL_UPDATE_RATE *= min;
		FiniteWater.WATER_UPDATE = Math.max(1, FiniteWater.WATER_UPDATE / min);
		FiniteWater.LAVA_UPDATE = Math.max(1, FiniteWater.LAVA_UPDATE / min);
		FiniteWater.LAVA_NETHER = Math.max(1, FiniteWater.LAVA_NETHER / min);
		
		
		
		
		config.save();
	}
	
	
}
