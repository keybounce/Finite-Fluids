package com.mcfht.realisticfluids;

import java.util.Map;

import com.mcfht.realisticfluids.asm.ASMTransformer;
import com.mcfht.realisticfluids.asm.ASMTransformer.StringComp;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion;

/** Nothing special, needed by Forge ASM stuffs.
 * @author FHT
 */
@MCVersion(value = "1.7.10")
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


