package com.mcfht.realisticfluids;

import net.minecraftforge.common.config.Configuration;

public class ConfigHandler {

	public static void handleConfigs(Configuration config)
	{
		config.load();

		///////////////// GENERAL STUFZ /////////////
		RealisticFluids.EQUALIZE_FAR =  config.getInt("globalEqualizeFar", "1 - General", 16, 1, 64, "Distant chunk equalization limit [0 to disable]");
		RealisticFluids.EQUALIZE_NEAR =  config.getInt("globalEqualizeNear", "1 - General", 1, 1, 64, "Near chunk equalization limit, sane values: 1 ~ 4 [0 to disable]");
		
		RealisticFluids.EQUALIZE_GLOBAL	= 5 * config.getInt("globalEqualizeCap", "1 - General", 8, 1, 256, "Max Equalizations per tick. More causes faster equalization. Sane values: 8 ~ 64");
		
		RealisticFluids.UPDATE_RANGE_FAR =  config.getInt("globalUpdateRangeFar", "1 - General", 8, 1, 32, "Distant update range (in chunks)");
		RealisticFluids.UPDATE_RANGE =  config.getInt("globalUpdateRangeNear", "1 - General", 4, 1, 32, "High priority update range (in chunks)");
		
		RealisticFluids.FAR_UPDATES = config.getInt("globalFarUodates", "1 - General", 512, 16, 100000, "Quota for trivial updates, From 16-256 quota used per chunk [avg ~24-32 in deep ocean]]");
		RealisticFluids.MAX_UPDATES = config.getInt("globalNearUpdates", "1 - General", 1024, 16, 100000, "Immediate update Factor. WIP! Currently not implemented well/at all");
		RealisticFluids.GLOBAL_RATE = config.getInt("globalUpdateRate", "1 - General", 5, 3, 32, "The number of ticks between each update sweep. (3 ~ 16)!");

		///////////////// FLUID STUFFZ ////////////////
		//RealisticFluids.LAVA_NETHER = config.getInt("lavaUpdateSpeedNether", "2 - Fluids", 2, 1, 999, "The relative update rate of Lava in the NETHER. Higher values = slower flow, but allow greater volumes");
		//RealisticFluids.LAVA_UPDATE = config.getInt("lavaUpdateSpeed", "2 - Fluids", 5, 1, 999, "The relative update rate of Lava. Higher values = slower flow, but allow greater volumes");
		//RealisticFluids.WATER_UPDATE = config.getInt("waterUpdateSpeed", "2 - Fluids", 1, 1, 999, "The flow rate of water (relative to global update rate). Higher values result in slower flow but better performance");
		
		//RealisticFluids.MAX_FLUID = (short) config.getInt("waterPrecision", "2 - Fluids", 16384, 256, 16384, "The number of water levels stored per block. Has no direct impact on performance, though different values may work better in some situations");
	
		//Convert to euc dist to save doing it later
		RealisticFluids.UPDATE_RANGE *= RealisticFluids.UPDATE_RANGE;
		RealisticFluids.UPDATE_RANGE_FAR *= RealisticFluids.UPDATE_RANGE_FAR;

		///////////////// COREMOD STUFZ ///////////////
		RealisticFluids.ASM_DOOR = config.getBoolean("patchVanillaDoors", "2 - Core Mods", true, "Force vanilla doors to throw block updates when opened (allowing water to flow through them)");
		RealisticFluids.ASM_WATER = config.getBoolean("patchWaterDuplication", "2 - Core Mods", true, "Prevents duplication of infinite water source blocks!");
		
		
		//Simplify the flow rates. If the user say, doubles all of them, then we 
		//can tick them twice as much, half as often 
		//(for example, if the user sets the tick rate to 3 and then makes water tick every 2nd time,
		//We can piss on their efforts and simply tick once every 6 ticks because who cares.
		//int min =  (Math.min(RealisticFluids.GLOBAL_RATE, Math.min(RealisticFluids.LAVA_UPDATE, Math.min(RealisticFluids.LAVA_NETHER, RealisticFluids.WATER_UPDATE))));
		//RealisticFluids.GLOBAL_RATE *= min;
		
		//RealisticFluids.WATER_UPDATE = Math.max(1, RealisticFluids.WATER_UPDATE / min);
		//RealisticFluids.LAVA_UPDATE = Math.max(1, RealisticFluids.LAVA_UPDATE / min);
		//RealisticFluids.LAVA_NETHER = Math.max(1, RealisticFluids.LAVA_NETHER / min);

		config.save();
	}
	
	
}
