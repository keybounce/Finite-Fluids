package com.mcfht.finitewater;

import java.util.Map;

import net.minecraft.init.Blocks;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion;

/**
 * The Launch Class for the mod. Required by the build in Forge ASM stuffs.
 * 
 * @author FHT
 *
 */
@MCVersion(value = "1.7.10")
public class FiniteWaterLauncher implements cpw.mods.fml.relauncher.IFMLLoadingPlugin
{
    public static final String MODID = "finitewater";
    public static final String VERSION = "0.2";
    
    @Override
	public String[] getASMTransformerClass() 
    {
		return new String[]{
				FHTClassTransformer.class.getName()
				};
	}

	@Override
	public String getModContainerClass() 
	{		
		return FiniteWater.class.getName();
	}

	@Override
	public String getSetupClass() 
	{
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {}

	@Override
	public String getAccessTransformerClass() 
	{
		return null;
	}
	
}



