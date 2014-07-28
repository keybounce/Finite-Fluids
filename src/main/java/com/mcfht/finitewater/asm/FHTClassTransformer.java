package com.mcfht.finitewater.asm;

import static org.objectweb.asm.Opcodes.FDIV;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.tree.AbstractInsnNode.METHOD_INSN;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

//import net.minecraft.block.Block;









import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.mcfht.finitewater.FiniteWater;


public class FHTClassTransformer implements net.minecraft.launchwrapper.IClassTransformer 
{
	public static final String waterReplacer = "finite";
	
	
	/*
	 net/minecraft/world/gen/ChunkProviderGenerate aqz
	 net/minecraft/world/gen/MapGenCaves aqw
	 net/minecraft/world/gen/ChunkProviderHell aqv
	 net/minecraft/world/gen/MapGenRavine aqs
	 net/minecraft/world/gen/feature/WorldGenLiquids asm
	 net/minecraft/world/gen/feature/WorldGenHellLava ars
	 net/minecraft/world/gen/MapGenCavesHell aqy
	 net/minecraft/world/gen/structure/StructureVillagePieces$Well awf
	 net/minecraft/world/gen/feature/WorldGenDesertWells arl

	 
	 */
	
	public static final String[] names = 
		{
		 "net.minecraft.world.gen.ChunkProviderGenerate aqz",
		 "net.minecraft.world.gen.MapGenCaves aqw",
		 "net.minecraft.world.gen.ChunkProviderHell aqv",
		 "net.minecraft.world.gen.MapGenRavine aqs",
		 "net.minecraft.world.gen.feature.WorldGenLiquids asm",
		 "net.minecraft.world.gen.feature.WorldGenHellLava ars",
		 "net.minecraft.world.gen.MapGenCavesHell aqy",
		 "net.minecraft.world.gen.structure.StructureVillagePieces$Well awf",
		 "net.minecraft.world.gen.feature.WorldGenDesertWells arl",
		};
	
	public static final List<StringComp> replaceCache = new ArrayList<StringComp>();

	
	public static class StringComp	{
		String a; String b;
		public StringComp(String a, String b){this.a = a; this.b = b;}}
	
	public static final PatchTask[] taskList = {
		
		//Water duplication "glitch"
		new PatchTask("net.minecraft.block.BlockDynamicLiquid", false, new PatchWaterDuplication()),
		new PatchTask("akr", true, new PatchWaterDuplication()),
		
		//Patch Doors not throwing block updates
		new PatchTask("net.minecraft.block.BlockDoor", false, new PatchDoorUpdates()),
		new PatchTask("net.minecraft.block.BlockTrapDoor", false, new PatchDoorUpdates()),
		new PatchTask("akn", true, new PatchDoorUpdates()),
		new PatchTask("aoe", true, new PatchDoorUpdates()),
		
		//Apply simple patch to ~reduce~ cave flooding
		//new PatchTask("net.minecraft.world.gen.MapGenCaves", false, new PatchCaveGen()),
		//new PatchTask("aqw", false, new PatchCaveGen())
		
		//Replace fluids
		/*
		new PatchTask("net.minecraft.world.gen.ChunkProviderHell", false, new ReplaceWorldFluids()),
		new PatchTask("net.minecraft.world.gen.ChunkProviderGenerate", false, new ReplaceWorldFluids()),
		new PatchTask("net.minecraft.world.gen.MapGenRavine", false, new ReplaceWorldFluids()),
		new PatchTask("net.minecraft.world.gen.MapGenCaves", false, new ReplaceWorldFluids()),
		*/
		
		/* className.equals("net.minecraft.world.gen.ChunkProviderHell")
		  ||className.equals("net.minecraft.world.gen.ChunkProviderGenerate")
		  ||className.equals("net.minecraft.world.gen.MapGenRavine")*/
		
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
	public byte[] transform(String className, String arg1, byte[] classBytes) {
		
		
		if (className.contains("mcfht")) return classBytes;
		//Patch initial tasks
		for (PatchTask t : taskList)
		{
			if (t.className.equals(className))
			{
				classBytes = ((FHTPatchTask)t.patcher).startPatch(className, classBytes, t.obfuscated);
			}
		}
	
		//Patch the heftier task list
		//ReplaceWorldFluids replacer = new ReplaceWorldFluids();
		//FIXME THIS METHOD IS FUCKING SHIT
		
		for (StringComp t : replaceCache)
		{
			if (t.a.equals(className))
			{
				return new ReplaceWorldFluids().startPatch(className, classBytes, true);
			}
			if (t.b.equals(className))
			{
				return new ReplaceWorldFluids().startPatch(className, classBytes, true);
			}
		}
		/*
		try
		{
			//System.out.println(className);
			return new ReplaceWorldFluids().startPatch(className, classBytes, true);
		}
		catch (Exception e)
		{
			return classBytes;
		}*/
		
		
		return classBytes;
		
		
	}
	
}
