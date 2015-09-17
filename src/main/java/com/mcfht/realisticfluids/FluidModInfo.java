package com.mcfht.realisticfluids;

import cpw.mods.fml.common.ModMetadata;
public class FluidModInfo {

    public static final String MODID = "finitewater";
    public static final String VERSION = "0.3";
    public static final String AUTHOR = "FHT";
	
	public static void get(ModMetadata meta)
	{
		meta.modId = MODID;
		meta.name = MODID;
		meta.version = VERSION;
		meta.credits = AUTHOR;
		meta.authorList.add(AUTHOR);
		meta.description= "";
		meta.url = "";
		meta.updateUrl= "";
		meta.screenshots= new String[0];
		meta.logoFile = "";
	}
	
}
