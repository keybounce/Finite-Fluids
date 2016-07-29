package com.mcfht.realisticfluids.asm;

import java.util.ArrayList;
import java.util.List;

//import net.minecraft.block.Block;



public class ASMTransformer implements net.minecraft.launchwrapper.IClassTransformer 
{
	/**Toggle "finite" for regular finite blocks, "replace" for the conditional block.
	 * 
	 * <p> Unfortunately the conditional block is not very fast, will investigate skipping light calcs
	 * and not sending block updates for that.
	 */
	public static final String waterReplacer = "finite";
	

	
	/*
	 "net.minecraft.world.gen.ChunkProviderGenerate aqz",
	 "net.minecraft.world.gen.MapGenCaves aqw",
	 "net.minecraft.world.gen.ChunkProviderHell aqv",
	 "net.minecraft.world.gen.MapGenRavine aqs",
	 "net.minecraft.world.gen.feature.WorldGenLiquids asm",
	 "net.minecraft.world.gen.feature.WorldGenHellLava ars",
	 "net.minecraft.world.gen.MapGenCavesHell aqy",
	 "net.minecraft.world.gen.structure.StructureVillagePieces$Well awf",
	 "net.minecraft.world.gen.feature.WorldGenDesertWells arl",
	 */
	
	/** Cache of target class names to replace (with obf and not obf mappings as above)*/
	public static final List<StringComp> replaceCache = new ArrayList<StringComp>();
	
	public static class StringComp	{
		String a; String b;
		public StringComp(String a, String b){this.a = a; this.b = b;}}
	
	public static final PatchTask[] taskList = 
	{
		
		//REPLACE FLUIDS
		new PatchTask("net.minecraft.block.Block", false, new PatchBlockRegistry()),
		new PatchTask("aji", true, new PatchBlockRegistry()),
		
		//Patch the set block flags in doors and trapdoors (force throw block update)
		new PatchTask("net.minecraft.block.BlockDoor", false, new PatchDoorUpdates()),
		new PatchTask("net.minecraft.block.BlockTrapDoor", false, new PatchDoorUpdates()),
		new PatchTask("akn", true, new PatchDoorUpdates()),
		new PatchTask("aoe", true, new PatchDoorUpdates()),
		
		
	};

	static class PatchTask
	{
		public String className;
		public boolean obfuscated;
		public Object patcher;
		public PatchTask(String className, boolean obfuscated, Object patcher)
		{
			this.className = className;
			this.obfuscated = obfuscated;
			this.patcher = patcher;
		}
	}

	@Override
	public byte[] transform(String className, String arg1, byte[] classBytes) 
	{
		//Patch initial tasks
		for (PatchTask t : taskList)
		{
			if (t.className.equals(className))
			{
				classBytes = ((ASMPatchTask)t.patcher).startPatch(className, classBytes, t.obfuscated);
			}
		}
		return classBytes;
	}
}
