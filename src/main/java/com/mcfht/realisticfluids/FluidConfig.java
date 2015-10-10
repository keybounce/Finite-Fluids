package com.mcfht.realisticfluids;

import net.minecraftforge.common.config.Configuration;

public class FluidConfig
{

	public static void handleConfigs(final Configuration config)
	{
		config.load();

		// /////////////// GENERAL STUFZ ////////////////
		RealisticFluids.UPDATE_RANGE_FAR = config.getInt("UpdateRangeFar", "1 - General", 8, 1, 32, "Distant update range (in chunks)");
		RealisticFluids.UPDATE_RANGE = config.getInt("UpdateRangeNear", "1 - General", 4, 1, 32, "High priority update range (in chunks)");
		// Convert to euc dist to save doing it later
        // Nope, we now do square/minecraft distance
		// RealisticFluids.UPDATE_RANGE *= RealisticFluids.UPDATE_RANGE;
		// RealisticFluids.UPDATE_RANGE_FAR *= RealisticFluids.UPDATE_RANGE_FAR;

		RealisticFluids.FAR_UPDATES = config.getInt("globalFarUpdates", "1 - General", 2048, 0, 10000000,
				"Estimate of max number of distant block updates");
		RealisticFluids.MAX_UPDATES = config.getInt("globalNearUpdates", "1 - General", 1024, 0, 10000000,
				"Immediate update Factor. WIP! Currently not implemented at all");

		RealisticFluids.GLOBAL_RATE_MAX = config.getInt("globalMaxUpdateInterval", "1 - General", 10, 3, 64,
				"The largest allowed number ticks between each update sweep");
		RealisticFluids.GLOBAL_RATE_AIM = config.getInt("globalIdealUpdateInterval", "1 - General", 5, 1, 64,
				"The ideal number of ticks between each update sweep");

		RealisticFluids.GLOBAL_RATE_MAX = Math.max(RealisticFluids.GLOBAL_RATE_AIM, RealisticFluids.GLOBAL_RATE_MAX);
		RealisticFluids.GLOBAL_RATE = RealisticFluids.GLOBAL_RATE_AIM;

		// /////////////// EQUALIZATION STUFZ /////////////
		RealisticFluids.EQUALIZE_FAR = config.getInt("EqualizeLinearFar", "2 - Equalization", 16, 1, 64,
				"Distant chunk equalization limit [0 to disable]");
		RealisticFluids.EQUALIZE_NEAR = config.getInt("EqualizeLinearNear", "2 - Equalization", 1, 1, 64,
				"Near chunk equalization limit, sane values: 1 - 4 [0 to disable]");

		RealisticFluids.EQUALIZE_GLOBAL = 5 * config.getInt("globalEqualizeCap", "2 - Equalization", 8, 1, 1024,
				"Max Equalizations per tick. More causes faster equalization. Sane values: 4 ~ 32");

		// /////////////// COREMOD STUFZ //////////////////
		RealisticFluids.ASM_DOOR = config.getBoolean("patchVanillaDoors", "3 - Core Mods", true,
				"Force vanilla doors to throw block updates when opened (allowing water to flow through them)");

		config.save();
	}

}
