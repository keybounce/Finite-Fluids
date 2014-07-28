package com.mcfht.finitewater;

import java.util.Map;

import com.mcfht.finitewater.asm.FHTClassTransformer;
import com.mcfht.finitewater.asm.FHTClassTransformer.StringComp;

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
    	
    	for (String s : FHTClassTransformer.names)
    	{
    		System.out.println(s + " : -"+s.split(" ")[0] + "-");
    		//Construct the list of targets for replacing water in
    		FHTClassTransformer.replaceCache.add(new StringComp(
    				s.split(" ")[0],
    				s.split(" ")[1]));
    				
    	}
    	
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



