package com.mcfht.realisticfluids;

import com.mcfht.realisticfluids.RealisticFluids.RainType;

import net.minecraftforge.common.config.Configuration;

public class FluidConfig
{

    private static final String GENERAL = "1 - General";
    private static final String EQUALIZATION = "2 - Equalization";
    private static final String CORE = "3 - Core Mods";
	private static final String RAINFALL = "4 - Rainfall and Evaporation";

    public static void handleConfigs(final Configuration config)
	{
		config.load();
                
        // /////// Notice! We now use "0" in the config to use the code defaults!
        
		// /////////////// GENERAL STUFZ ////////////////
		RealisticFluids.UPDATE_RANGE_FAR = FluidConfig.getInt(config, "UpdateRangeFar", GENERAL, 8, 1, 32, "Distant update range (in chunks)");
		RealisticFluids.UPDATE_RANGE = FluidConfig.getInt(config, "UpdateRangeNear", GENERAL, 4, 1, 32, "High priority update range (in chunks)");
		// Convert to euc dist to save doing it later
        // Nope, we now do square/minecraft distance
		// RealisticFluids.UPDATE_RANGE *= RealisticFluids.UPDATE_RANGE;
		// RealisticFluids.UPDATE_RANGE_FAR *= RealisticFluids.UPDATE_RANGE_FAR;

		RealisticFluids.FAR_UPDATES = FluidConfig.getInt(config, "globalFarUpdates", GENERAL, 2048, 0, 10000000, "Estimate of max number of distant block updates");
		RealisticFluids.MAX_UPDATES = FluidConfig.getInt(config, "globalNearUpdates", GENERAL, 1024, 0, 10000000, "Immediate update Factor. WIP! Currently not implemented at all");

		RealisticFluids.GLOBAL_RATE_MAX = FluidConfig.getInt(config, "globalMaxUpdateInterval", GENERAL, 10, 3, 64, "The largest allowed number of ticks between each update sweep");
		RealisticFluids.GLOBAL_RATE_AIM = FluidConfig.getInt(config, "globalIdealUpdateInterval", GENERAL, 5, 1, 64, "The ideal number of ticks between each update sweep");

		RealisticFluids.GLOBAL_RATE_MAX = Math.max(RealisticFluids.GLOBAL_RATE_AIM, RealisticFluids.GLOBAL_RATE_MAX);
		RealisticFluids.GLOBAL_RATE = RealisticFluids.GLOBAL_RATE_AIM;

		// /////////////// EQUALIZATION STUFZ /////////////
		RealisticFluids.EQUALIZE_FAR = FluidConfig.getInt(config, "EqualizeLinearFar", EQUALIZATION, 16, 1, 64, "Distant chunk equalization limit [0 to disable]");
		RealisticFluids.EQUALIZE_NEAR = FluidConfig.getInt(config, "EqualizeLinearNear", EQUALIZATION, 1, 1, 64, "Near chunk equalization limit, sane values: 1 - 4 [0 to disable]");

		RealisticFluids.EQUALIZE_GLOBAL = 5 * FluidConfig.getInt(config, "globalEqualizeCap", EQUALIZATION, 8, 1, 1024, "Max Equalizations per tick. More causes faster equalization. Sane values: 4 ~ 32");

		// /////////////// COREMOD STUFZ //////////////////
		RealisticFluids.ASM_DOOR = config.getBoolean("patchVanillaDoors", CORE, true,
				"Force vanilla doors to throw block updates when opened (allowing water to flow through them)");

        // /////////////// Absorpotion / Evaporation / Rainfall //////
        RealisticFluids.ABSORB = FluidConfig.getInt(config, "AbsorptionThreshold", GENERAL,
                RealisticFluids.MAX_FLUID/12, 0, RealisticFluids.MAX_FLUID,
                "Level at which flowing water will be absorbed by mod water.\n"
                        + "For Streams, 1/14*MAX will prevent almost all floods.\n"
                        + "Smaller values (try around 1/20th max) will permit extra watergen for steam engines/etc.\n"
                        + "1/4th max will effectively squash worldgen floods; smaller will put some worldgen excess into oceans\n"
                        + "Occasionally a stream will have an infinite gen spot; try 1/6th to 1/2 max to keep that under control.\n"
                        + "If rainfall is not NONE, this can be very large.\n"
                        + "Warning: set to " + RealisticFluids.MAX_FLUID + " at own risk\n");
        RealisticFluids.RAINTYPE = RainType.valueOf(
                FluidConfig.getString(config, "Raintype", RAINFALL, "SIMPLE", 
                        "Rainfall type. NONE = no rain or evaporation.\n"
                                + "SIMPLE = simple rain in low biomes, no evaporation\n"
                                + "SIMPLE attempts to refill biomes that are submerged back to sea level; the test is approximate \n"
                                + "as Minecraft does not actually have a well-defined sea level concept. Overworld works, \n"
                                + "mod dimensions are _hopefully_ supported well enough not to break badly\n")
                        .toUpperCase());
        RealisticFluids.RAINSPEED = FluidConfig.getInt(config, "RainSpeed", RAINFALL, 20, 1, 1000,
                "Rainfall speed, lower is faster. 1 is very fast; sane may be 10-30.");
		config.save();
	}

	/**
	 * A value of "0" in the config file means "Use the default".
	 */
    public static int getInt(Configuration configuration, String name,
            String category, int defaultValue, int minValue, int maxValue,
            String comment)
    {
        int temp;
        String newComment = comment + " [Actual default, if 0 is specified: " + defaultValue + "]";
        temp = configuration.getInt(name, category, 0, minValue, maxValue, newComment);
        if (0 == temp)
            return defaultValue;
        else return temp;
    }
    
    /**
     * A value of "" (null) in the config file means "Use the default".
     */
    public static String getString(Configuration configuration, String name,
            String category, String defaultValue, String comment)
    {
        String temp;
        String newComment = comment + " [Actual default, if blank: " + defaultValue + "]";
        temp = configuration.getString(name, category, "", newComment);
        if ("" == temp)
            return defaultValue;
        return temp;
    }
}
