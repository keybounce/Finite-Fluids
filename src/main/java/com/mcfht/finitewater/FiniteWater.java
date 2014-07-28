package com.mcfht.finitewater;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.mcfht.finitewater.fluids.BlockFFluid;
import com.mcfht.finitewater.fluids.BlockReplacer;
import com.mcfht.finitewater.fluids.BlockSourceD;
import com.mcfht.finitewater.util.UpdateHandler;

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

/**
 * The Main class for the mod. Contains all mod information, and calls
 * the Forge initialization events.
 * @author Ollie
 *
 */
public class FiniteWater extends DummyModContainer 
{
	/** Max update quota */
	public static int MAX_UPDATES = 300;
	/** Force this much update quota */
	public static int FORCE_UPDATES = 48;
	
	/** Number of ticks between update sweeps*/
	public static int GLOBAL_UPDATE_RATE = 5;
	
	/** The number of fluid levels for each cell*/
	public static short MAX_WATER = 16384;
	/** Relative update rate of water (n sweeps) */
	public static int WATER_UPDATE = 1;
	/** Relative update rate of lava in the overworld (n sweeps) */
	public static int LAVA_UPDATE = 5;
	/** Relative update rate of lava in the nether (n sweeps) */
	public static int LAVA_NETHER = 3;
	
	/** The number of chunks in which to prioritize updates*/
	public static int UPDATE_RANGE = 4*4;
	public static int UPDATE_RANGE_FAR = 12*12;
	
		public static boolean PATCH_WATER_DUPLICATION = true;
	public static boolean PATCH_DOOR_UPDATES = true;
	
	/** Runniness of water*/
	public static final int waterVisc = 4;
	/** Runniness of lava*/
	public static final int lavaVisc = 3;
	
	public FiniteWater() {
		super(new ModMetadata());
		ModMetadata meta = getMetadata();
		meta.modId = FiniteWaterLauncher.MODID;
		meta.name = FiniteWaterLauncher.MODID;
		meta.version = FiniteWaterLauncher.VERSION; //String.format("%d.%d.%d.%d", majorVersion, minorVersion, revisionVersion, buildVersion);
		meta.credits = "FHT";
		meta.authorList = Arrays.asList("FHT");
		meta.description = "";
		meta.url = "";
		meta.updateUrl = "";
		meta.screenshots = new String[0];
		meta.logoFile = "";
	}
	
	@Override
	public boolean registerBus(EventBus bus, LoadController controller)
	{
		bus.register(this);
		return true;
	}
	/**
	* Finite Water Blocks
	*/
	public static Block finiteWater;
	/**
	* Infinite Source block (redstone trigger)
	*/
    public static Block debugSource = new BlockSourceD(Material.iron).setCreativeTab(CreativeTabs.tabMisc).setBlockTextureName("water_flowing");
    /**
     * Finite Lava blocks
     */
    public static Block finiteLava;
    
    public static Block replaceWater = new BlockReplacer(Material.water);
    
    @Subscribe
    public void preInit(FMLPreInitializationEvent event)
    {
    	ConfigHandler.handleConfigs(new Configuration(event.getSuggestedConfigurationFile()));
    
    	
    	//Time to thingimy stuff and thingimy!
    	
    	
    	
    	
    }
    
    @Subscribe
    public void initEvent(FMLInitializationEvent event)
    {
    	
    	
    	finiteWater = new BlockFFluid(Material.water, waterVisc, WATER_UPDATE).setCreativeTab(CreativeTabs.tabMisc);
    	finiteLava = new BlockFFluid(Material.lava, lavaVisc, LAVA_UPDATE).setCreativeTab(CreativeTabs.tabMisc);
    	
    	GameRegistry.registerBlock(finiteWater, "UninfiniteWater");
    	GameRegistry.registerBlock(finiteLava, "UninfiniteLava");
    	GameRegistry.registerBlock(debugSource, "debugSource");
    	GameRegistry.registerBlock(replaceWater, "replaceWater");
    	
    	//Register event handlers
    	FMLCommonHandler.instance().bus().register(UpdateHandler.INSTANCE);
    	MinecraftForge.EVENT_BUS.register(UpdateHandler.INSTANCE);
    }


	    
}
