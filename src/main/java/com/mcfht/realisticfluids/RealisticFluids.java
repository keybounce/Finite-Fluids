package com.mcfht.realisticfluids;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;
import com.mcfht.realisticfluids.fluids.BlockFiniteLava;
import com.mcfht.realisticfluids.fluids.BlockFiniteWater;
import com.mcfht.realisticfluids.fluids.BlockFluidSpawner;
import com.mcfht.realisticfluids.fluids.BlockGenWaterReplacer;
import com.mcfht.realisticfluids.util.Equalizer;
import com.mcfht.realisticfluids.util.FluidWorkers;
import com.mcfht.realisticfluids.util.UpdateHandler;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLConstructionEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;


/* ~~~~~~~~~~~~~~~~~~General Notes on Code~~~~~~~~~~~~~~~~~~~~
 * 
 * I have a very particular naming scheme when I code.
 * 
 * abc0 refers to the FIRST or ORIGIN or whatever
 * abc1 refers to the TARGET or NEXT
 * abcn refers to the Nth target
 * 
 * For example, if we are changing fluid level; l0 is the current level, 
 * l1 is the next level, b0 is the target block, etcetera
 * 
 * If you see "i" or "j" or "k", or a seriously shortened word (aka "dist" = "distance")
 * then it is a counter, or temp variable. The full word is regarded as a parameter or local
 * 
 * I also like to use specific letters for a range of specific things. For example,
 * world = w, Block = b, Meta = m, Random = r, Level = l, and so on.
 * 
 * Parameters which I deem to be "unclear" typically get a full name, since it
 * just helps, you know.
 * 
 * 
 * That's about all that needs to be said on this matter, so enjoy perusing.
 * 
 * - FHT
 * 
 */
public class RealisticFluids extends DummyModContainer 
{
	///////////////////////// GENERAL SETTINGS //////////////////////
	/** Max update quota per tick. TODO NOT MAX */	
	public static int MAX_UPDATES 		= 	1024;
	/** Force this much update quota TODO NOT MAX */
	public static int FAR_UPDATES		= 	48; 
	/** Number of ticks between update sweeps */
	public static int GLOBAL_RATE		= 	5;
	/** Max number of ticks between update sweeps */
	public static int GLOBAL_RATE_MAX	= 	10;
	public static int GLOBAL_RATE_AIM	= 	5;
	////////////////////DISTANCE BASED PRIORITIZATION ///////////////////////
	/** Priority distance*/
	public static int UPDATE_RANGE 		= 	4*4; //Note to reader: things like this get compiled away
	/** "Trivial" distance */
	public static int UPDATE_RANGE_FAR 	= 	12*12;
	
	///////////////////// EQUALIZATION SETTINGS //////////////////////
	/** Arbitrary limits on NEAR equalization */
	public static int EQUALIZE_NEAR 	=	 1;
	public static int EQUALIZE_NEAR_R	=	32;
	/** Aribtrary limits on DISTANT equalization */
	public static int EQUALIZE_FAR 		= 	16;
	public static int EQUALIZE_FAR_R	= 	32;
	
	public static int EQUALIZE_GLOBAL	= 	32;
	
	////////////////// FLUID SETTINGS //////////////////////
	/** The number of fluid levels for each cell */
	public final static short MAX_FLUID = 	16384; //Note to reader: Explicit final fields get compiled as constants
	
	//WATER
	/** Finite Water Blocks	*/
	//public static Block finiteWater;
	/** Relative update rate of water*/
	public static int WATER_UPDATE		= 	1;
	/** Runniness of water*/
	public static final int waterVisc 	= 	4;
	//LAVA
	/** Finite Lava blocks*/
	//public static Block finiteLava;
	/** update rate of lava in the overworld */ 	
	public static final int LAVA_UPDATE = 	5;
	/** update rate of lava in the nether) */		
	public static final int LAVA_NETHER = 	3;
	/** Runniness of lava*/							
	public static final int lavaVisc 	= 	3;
	
	//OTHER
	/** Infinite Source block (redstone trigger) */
	public static Block debugSource;
	/** Replaces water at gen-time?*/ 
	//public static Block replaceWater 	= 	new BlockGenWaterReplacer(Material.water);
	
	
	////////////////////////////ASM SETTINGS///////////////////////
	
	public static boolean ASM_WATER	 	= 	true;
	public static boolean ASM_DOOR 		= 	true;
	
	public RealisticFluids()
	{
		super(new ModMetadata());
		ModMetadata meta 		= getMetadata();
		meta.modId 				= ModLaunchWrapper.MODID;
		meta.name 				= ModLaunchWrapper.MODID;
		meta.version 			= ModLaunchWrapper.VERSION;
		meta.credits 			= "FHT";
		meta.authorList 		= Arrays.asList("FHT");
		meta.description 		= "";
		meta.url 				= "";
		meta.updateUrl			= "";
		meta.screenshots 		= new String[0];
		meta.logoFile 			= "";
	}
	
	@Override
	public boolean registerBus(EventBus bus, LoadController controller)
	{
		bus.register(this);
		return true;
	}

    
    @Subscribe
    public void preInit(FMLPreInitializationEvent event)
    {
    	ConfigHandler.handleConfigs(new Configuration(event.getSuggestedConfigurationFile()));
    }
    
    @Subscribe
    public void initEvent(FMLInitializationEvent event)
    {
    	
    	//finiteWater = 	new BlockFiniteWater(Material.water /*, waterVisc, WATER_UPDATE*/ ).setCreativeTab(CreativeTabs.tabMisc);
    	//finiteLava 	= 	new BlockFiniteLava(Material.lava /*, lavaVisc, LAVA_UPDATE*/ ).setCreativeTab(CreativeTabs.tabMisc);
    	//debugSource = 	new BlockFluidSpawner(Material.iron).setCreativeTab(CreativeTabs.tabMisc).setBlockTextureName("water_flowing");

    	//GameRegistry.registerBlock(finiteWater, 	"UninfiniteWater");
    	//GameRegistry.registerBlock(finiteLava, 		"UninfiniteLava");
    	//GameRegistry.registerBlock(debugSource, 	"debugSource");
    	//GameRegistry.registerBlock(replaceWater, 	"replaceWater");
    	
    	//Register event handlers
    	FMLCommonHandler.instance().bus().register(UpdateHandler.INSTANCE);
    	MinecraftForge.EVENT_BUS.register(UpdateHandler.INSTANCE);
    	MinecraftForge.TERRAIN_GEN_BUS.register(UpdateHandler.INSTANCE);
    }


	    
}
