package com.mcfht.realisticfluids;

import java.util.Map;

import com.mcfht.realisticfluids.asm.ASMTransformer;
import com.mcfht.realisticfluids.asm.ASMTransformer.StringComp;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion;

/**
 * The Launch Class for the mod. Required by the built in Forge ASM stuffs.
 * 
 * @author FHT
 *
 */
@MCVersion(value = "1.7.10")
public class ModLaunchWrapper implements cpw.mods.fml.relauncher.IFMLLoadingPlugin
{
    public static final String MODID = "finitewater";
    public static final String VERSION = "0.2";
    
    @Override
	public String[] getASMTransformerClass() 
    {
    	
    	for (String s : ASMTransformer.names)
    	{
    		System.out.println(s + " : -"+s.split(" ")[0] + "-");
    		//Construct the list of targets for replacing water in
    		ASMTransformer.replaceCache.add(new StringComp(
    				s.split(" ")[0],
    				s.split(" ")[1]));
    				
    	}
    	
		return new String[]{
				ASMTransformer.class.getName()
				};
	}

	@Override
	public String getModContainerClass() 
	{		
		return RealisticFluids.class.getName();
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



