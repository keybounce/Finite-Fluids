package com.mcfht.realisticfluids;

import cpw.mods.fml.common.ModMetadata;
public class FluidModInfo {

    public static final String MODID = "{@modid:mod-name}";
    public static final String VERSION = "{@version:fluids-master}";
    public static final String AUTHOR = "FHT";
	
	public static void get(ModMetadata meta)
	{
		meta.modId = MODID;
		meta.name = MODID;
		meta.version = VERSION;
		meta.credits = AUTHOR + ", Keybounce";
		meta.authorList.add(AUTHOR);
		meta.description= "Finite, flowing water and lava, more realistic than forge fluids";
		meta.url = "http://www.minecraftforum.net/forums/mapping-and-modding/minecraft-mods/wip-mods/2158282-";
		meta.updateUrl= "";
		meta.screenshots= new String[0];
		meta.logoFile = "";
	}
	
}
