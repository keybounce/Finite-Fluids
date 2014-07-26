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

	public static int MAX_UPDATES;
	public static int FORCE_UPDATES;
	public static int GLOBAL_UPDATE_RATE;
	
	public static int MAX_WATER;
	public static int WATER_UPDATE;
	public static int LAVA_UPDATE;
	public static int LAVA_NETHER;
	
	
	
	public static boolean PATCH_WATER_DUPLICATION = true;
	public static boolean PATCH_DOOR_UPDATES = true;
	
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
    
    
    
    @Subscribe
    public void preInit(FMLPreInitializationEvent event)
    {
    	ConfigHandler.handleConfigs(new Configuration(event.getSuggestedConfigurationFile()));
    }
    
    @Subscribe
    public void initEvent(FMLInitializationEvent event)
    {
    	
    	finiteWater = new BlockFFluid(Material.water, 4, WATER_UPDATE).setCreativeTab(CreativeTabs.tabMisc);
    	finiteLava = new BlockFFluid(Material.lava, 3, LAVA_UPDATE).setCreativeTab(CreativeTabs.tabMisc);
    	
    	GameRegistry.registerBlock(finiteWater, "UninfiniteWater");
    	GameRegistry.registerBlock(finiteLava, "UninfiniteLava");
    	GameRegistry.registerBlock(debugSource, "debugSource");
    	
    	//Register event handlers
    	FMLCommonHandler.instance().bus().register(UpdateHandler.INSTANCE);
    	MinecraftForge.EVENT_BUS.register(UpdateHandler.INSTANCE);
    }


	    
}
