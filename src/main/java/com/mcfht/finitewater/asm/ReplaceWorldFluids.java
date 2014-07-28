package com.mcfht.finitewater.asm;

import java.util.Iterator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class ReplaceWorldFluids implements FHTPatchTask{

	//public static final String waterReplacer = "finite";
	public static final String blockDesc = "Lnet/minecraft/block/Block;";
	public static final String blockOwner = "com/mcfht/finitewater/FiniteWater";
	
	public static final String blockWater = "finiteWater";
	public static final String blockLava = "finiteLava";
	
	
	public ClassNode doPatch(String name, byte[] bytes, boolean obfuscated) 
	{
		
		String nameWater = "water";
		String nameLava = "lava";
		
		ClassNode classNode = new ClassNode();
		ClassReader classReader = new ClassReader(bytes);
		classReader.accept(classNode, 0);
		
		@SuppressWarnings("unchecked")
		Iterator<MethodNode> methods = classNode.methods.iterator();
		
		while(methods.hasNext())
		{
			MethodNode m = methods.next();
			
			AbstractInsnNode node0 = null;
			@SuppressWarnings("unchecked")
			Iterator<AbstractInsnNode> iter = m.instructions.iterator();
			int index = -1;
			
			while (iter.hasNext())
			{
				index++;
				node0 = iter.next();
				
				//Replace all references to regular water and lava
				
				if (!(m.instructions.get(index) instanceof FieldInsnNode)) continue;
				
				if (	((FieldInsnNode)m.instructions.get(index)).name.equals(nameLava) 
					||	((FieldInsnNode)m.instructions.get(index)).name.equals("flowing_" + nameLava)
					
				){
					System.out.println("Patching a Lava block!");
					((FieldInsnNode)m.instructions.get(index)).name = blockLava;
					((FieldInsnNode)m.instructions.get(index)).owner = blockOwner;
					((FieldInsnNode)m.instructions.get(index)).desc = blockDesc;
				}
				
				if (	((FieldInsnNode)m.instructions.get(index)).name.equals(nameWater) 
					||	((FieldInsnNode)m.instructions.get(index)).name.equals("flowing_" + nameWater)
						
				){
					System.out.println("Patching a Water block!");
					((FieldInsnNode)m.instructions.get(index)).name = blockWater;
					((FieldInsnNode)m.instructions.get(index)).owner = blockOwner;
					((FieldInsnNode)m.instructions.get(index)).desc = blockDesc;
				}
				
				//Replace all references to flowing varieties
				/*
				if (((FieldInsnNode)m.instructions.get(index)).name.equals("flowing_water"))
				{
					((FieldInsnNode)m.instructions.get(index)).name = blockWater;
					((FieldInsnNode)m.instructions.get(index)).owner = blockOwner;
					((FieldInsnNode)m.instructions.get(index)).desc = blockDesc;
					
					System.out.println("Patched to : " + ((FieldInsnNode)m.instructions.get(index)).name);
				}
				
				if (((FieldInsnNode)m.instructions.get(index)).name.equals("flowing_lava"))
				{
					((FieldInsnNode)m.instructions.get(index)).name = blockLava;
					((FieldInsnNode)m.instructions.get(index)).owner = blockOwner;
					((FieldInsnNode)m.instructions.get(index)).desc = blockDesc;
				}*/
			}				
			
		}
		
		System.out.println("Replaced Fluids!");
		return classNode;
	}

	@Override
	public byte[] startPatch(String name, byte[] bytes, boolean obfuscated) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		ClassNode c = doPatch(name, bytes, obfuscated);
		
		if (c == null) return null;
		
		c.accept(writer);
		return writer.toByteArray();
	}
	
	
	
}
