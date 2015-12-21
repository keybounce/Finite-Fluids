package com.mcfht.realisticfluids;

import com.mcfht.realisticfluids.RealisticFluids.RainType;

import net.minecraftforge.common.config.Configuration;

public class FluidConfig
{

	public static void handleConfigs(final Configuration config)
	{
		config.load();
        
        String GENERAL = "1 - General";
        String EQUALIZE = "2 - Equalization";
        String CORE = "3 - Core Mods";
        String RAINFALL =  "4 - Rainfall and Evaporation";
        
		// /////////////// GENERAL STUFZ ////////////////
		RealisticFluids.UPDATE_RANGE_FAR = config.getInt("UpdateRangeFar", "1 - General", 4, 1, 32,
		        "Distant update range (in chunks) *SAME AS NEAR*");
		RealisticFluids.UPDATE_RANGE = config.getInt("UpdateRangeNear", "1 - General", 2, 1, 32,
		        "High priority update range (in chunks)");
		// Convert to euc dist to save doing it later
        // Nope, we now do square/minecraft distance
		// RealisticFluids.UPDATE_RANGE *= RealisticFluids.UPDATE_RANGE;
		// RealisticFluids.UPDATE_RANGE_FAR *= RealisticFluids.UPDATE_RANGE_FAR;

		RealisticFluids.FAR_UPDATES = config.getInt("globalFarUpdates", "1 - General", 30000, 0, 10000000,
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

        // /////////////// Absorpotion / Evaporation / Rainfall //////
        RealisticFluids.ABSORB = config.getInt("AbsorptionThreshold", "1 - General",
                RealisticFluids.MAX_FLUID/12, 0, RealisticFluids.MAX_FLUID,
                    "Level at which flowing water will be absorbed by mod water.\n"
                    + "For Streams, 1/12*MAX will prevent almost all floods.\n"
                    + "Smaller values (try around 1/20th max) will permit extra watergen for steam engines/etc.\n"
                    + "1/4th max will effectively squash worldgen floods; smaller will put some worldgen excess into oceans\n"
                    + "Occasionally a stream will have an infinite gen spot; try 1/6th to 1/2 max to keep that under control.\n"
                    + "If rainfall is not NONE, this can be very large.\n"
                    + "Warning: set to " + RealisticFluids.MAX_FLUID + " at own risk\n"
                    );
        RealisticFluids.RAINTYPE = RainType.valueOf(
                config.getString("Raintype", RAINFALL, "SIMPLE",
                        "Rainfall type. NONE = no rain or evaporation.\n"
                        + "SIMPLE = simple rain in low biomes, no evaporation\n"
                        + "SIMPLE attempts to refill biomes that are submerged back to sea level; the test is approximate \n"
                        + "as Minecraft does not actually have a well-defined sea level concept. Overworld works, \n"
                        + "mod dimensions are _hopefully_ supported well enough not to break badly\n")
                        .toUpperCase());
        RealisticFluids.RAINSPEED = config.getInt("RainSpeed", RAINFALL, 20, 1, 1000,
                "Rainfall speed, lower is faster. 1 is very fast; sane may be 10-30.");
		config.save();
	}

}
