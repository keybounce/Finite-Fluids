package com.mcfht.realisticfluids;

import java.util.Map;

import com.mcfht.realisticfluids.asm.ASMTransformer;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion;

/** Nothing special, needed by Forge ASM stuffs.
 * @author FHT
 */
@MCVersion(value = "1.7.10")
@IFMLLoadingPlugin.SortingIndex(1012) // Must be after 1001, after CofhCore. Room for someone else as well.

public class ModLaunchWrapper implements cpw.mods.fml.relauncher.IFMLLoadingPlugin{
    public static final String MODID = FluidModInfo.MODID;
    public static final String VERSION = FluidModInfo.VERSION;
    
    @Override
	public String[] getASMTransformerClass() { return new String[]{ ASMTransformer.class.getName()}; }
	@Override
	public String getModContainerClass() { return RealisticFluids.class.getName(); }
	@Override
	public String getSetupClass() {	return null; }
	@Override
	public void injectData(Map<String, Object> data) {}
	@Override
	public String getAccessTransformerClass() {	return null; }
}



