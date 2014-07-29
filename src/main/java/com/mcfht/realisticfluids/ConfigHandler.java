package com.mcfht.realisticfluids;

import net.minecraftforge.common.config.Configuration;

public class ConfigHandler {

	public static void handleConfigs(Configuration config)
	{
		config.load();

		RealisticFluids.GLOBAL_RATE = config.getInt("globalUpdateRate", "1 - General", 5, 3, 64, "The number of ticks between each update sweep, !!!WIP: CHANGING COULD SERIOUSLY BREAK THINGS!!!");
		RealisticFluids.MAX_UPDATES = config.getInt("globalNearUpdates", "1 - General", 1024, 16, 100000, "Immediate update Factor. Currently has no effect on performance, but left over quota is given to distant chunks");
		RealisticFluids.FORCE_UPDATES = config.getInt("globalFarUpdates", "1 - General", 256, 16, 100000, "Distant update factor - Forces updates in distant chunks. Higher values give more updates to distant chunks, lower values benefit performance");
		
		RealisticFluids.UPDATE_RANGE =  config.getInt("globalUpdateRangeNear", "1 - General", 4, 1, 32, "Range of high priority updates");
		RealisticFluids.UPDATE_RANGE_FAR =  config.getInt("globalUpdateRangeFar", "1 - General", 6, 1, 32, "Range of low priority updates");

		RealisticFluids.EQUALIZE_NEAR =  config.getInt("globalEqualizeNear", "1 - General", 4, 1, 64, "Amount to equalize near chunks");
		RealisticFluids.EQUALIZE_FAR =  config.getInt("globalEqualizeFar", "1 - General", 16, 1, 64, "Amount to equalize far chunks");

		//Convert to guassian distance to save doing it later
		RealisticFluids.UPDATE_RANGE *= RealisticFluids.UPDATE_RANGE;
		RealisticFluids.UPDATE_RANGE_FAR *= RealisticFluids.UPDATE_RANGE_FAR;
		
		RealisticFluids.ASM_DOOR = config.getBoolean("patchVanillaDoors", "1 - General", true, "Force vanilla doors to throw block updates when opened (allowing water to flow through them)");
		RealisticFluids.ASM_WATER = config.getBoolean("patchWaterDuplication", "1 - General", true, "Prevents duplication of infinite water source blocks!");
		
		RealisticFluids.MAX_FLUID = (short) config.getInt("waterPrecision", "2 - Fluids", 16384, 256, 16384, "The number of water levels stored per block. Has no direct impact on performance, though different values may work better in some situations");
		
		RealisticFluids.WATER_UPDATE = config.getInt("waterUpdateSpeed", "2 - Fluids", 1, 1, 999, "The flow rate of water (relative to global update rate). Higher values result in slower flow but better performance");
		RealisticFluids.LAVA_UPDATE = config.getInt("lavaUpdateSpeed", "2 - Fluids", 5, 1, 999, "The relative update rate of Lava. Higher values = slower flow, but allow greater volumes");
		RealisticFluids.LAVA_NETHER = config.getInt("lavaUpdateSpeedNether", "2 - Fluids", 2, 1, 999, "The relative update rate of Lava in the NETHER. Higher values = slower flow, but allow greater volumes");
		
		
		
		//Simplify the flow rates. If the user say, doubles all of them, then we 
		//can tick them twice as much, half as often 
		//(for example, if the user sets the tick rate to 3 and then makes water tick every 2nd time,
		//We can piss on their efforts and simply tick once every 6 ticks because who cares.
		
		int min =  (Math.min(RealisticFluids.GLOBAL_RATE, Math.min(RealisticFluids.LAVA_UPDATE, Math.min(RealisticFluids.LAVA_NETHER, RealisticFluids.WATER_UPDATE))));
		RealisticFluids.GLOBAL_RATE *= min;
		
		RealisticFluids.WATER_UPDATE = Math.max(1, RealisticFluids.WATER_UPDATE / min);
		RealisticFluids.LAVA_UPDATE = Math.max(1, RealisticFluids.LAVA_UPDATE / min);
		RealisticFluids.LAVA_NETHER = Math.max(1, RealisticFluids.LAVA_NETHER / min);
		
		
		
		
		config.save();
	}
	
	
}
