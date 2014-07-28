package com.mcfht.finitewater;

import static org.objectweb.asm.Opcodes.FDIV;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.tree.AbstractInsnNode.METHOD_INSN;

import java.util.Iterator;
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


public class FHTClassTransformer implements net.minecraft.launchwrapper.IClassTransformer 
{

	static enum Target
	{
		DOOR,
		WATER,
		FLUIDS
	}
	@Override
	public byte[] transform(String className, String arg1, byte[] classBytes) {
		
		
	/*	if (arg0.equals("akr"))
		{
			System.out.println("*** Obfuscated MC environment detected! patching water!  (classname = " + arg0 + ")");
			return patchClassASM(arg0, arg2, true);
		}
		*/
		//net.minecraft.world.gen.ChunkProviderHell
		if (    className.equals("net.minecraft.world.gen.ChunkProviderHell")
			  ||className.equals("net.minecraft.world.gen.ChunkProviderGenerate")
			  ||className.equals("net.minecraft.world.gen.MapGenRavine")
		){
			System.out.println(className);
			return patchASM(className, classBytes, false, Target.FLUIDS);
		}
		
		
		
		if (className.equals("net.minecraft.block.BlockDynamicLiquid"))
		{
			return patchASM(className, classBytes, false, Target.WATER);
		}
		if (className.equals("akr"))
		{
			
			return patchASM(className, classBytes, true, Target.WATER);
		}
		
		if ((className.equals("net.minecraft.block.BlockDoor") || className.equals("net.minecraft.block.BlockTrapDoor")))
		{
			return patchASM(className, classBytes, false, Target.DOOR);
		}
		if ((className.equals("akn") || className.equals("aoe")))
		{
			return patchASM(className, classBytes, true, Target.DOOR);
		}
		//System.out.println("*** Obfuscated MC environment detected! patching class:  (classname = " + arg0 + ")");
		//return patchClassASM(arg0, arg2, true);
		
		return classBytes;
	}
	
	public byte[] patchASM(String name, byte[] bytes, boolean obfuscated, Target target) 
	{
		String targetMethodName = "";
		
		if (target == Target.WATER)
		{
			if(obfuscated == true)
				targetMethodName ="a";
			else
				targetMethodName ="updateTick";
		}

		//func_147419_a
		
		
		
		ClassNode classNode = new ClassNode();
		ClassReader classReader = new ClassReader(bytes);
		classReader.accept(classNode, 0);
		
		@SuppressWarnings("unchecked")
		Iterator<MethodNode> methods = classNode.methods.iterator();
		
		while(methods.hasNext())
		{
			MethodNode m = methods.next();
			int fdiv_index = -1;
			System.out.println(m.name + " : " + targetMethodName);
			
			if ((target == Target.DOOR || target == Target.FLUIDS) || m.name.equals(targetMethodName))
			{
			
				AbstractInsnNode node0 = null;
		
				@SuppressWarnings("unchecked")
				Iterator<AbstractInsnNode> iter = m.instructions.iterator();
				int index = -1;
				
				while (iter.hasNext())
				{
					index++;
					node0 = iter.next();
					
					
					switch(target)
					{
					case DOOR:
						if (node0.getOpcode() == org.objectweb.asm.Opcodes.ICONST_2 && iter.hasNext())
						{
							index++;
							if (iter.next().getOpcode()  == org.objectweb.asm.Opcodes.INVOKEVIRTUAL)
							{
								m.instructions.set(node0, new InsnNode(org.objectweb.asm.Opcodes.ICONST_3));
								System.out.println("Patched door!");
								continue;
							}
						}
						break;
						
					case WATER:
						if (node0.getOpcode() == org.objectweb.asm.Opcodes.GETFIELD && iter.hasNext())
						{
							index++;
							node0 = iter.next();
							if (node0.getOpcode() == org.objectweb.asm.Opcodes.ICONST_2)
							{
								m.instructions.set(node0, new InsnNode(org.objectweb.asm.Opcodes.ICONST_5));
								System.out.println("Patched water!");
								
								continue;
							}
						}
						break;
						
						
					case FLUIDS:
						
						if (node0.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC)
						{
							System.out.println("Found a thingy! " + ((FieldInsnNode)m.instructions.get(index)).name);
							
							if (((FieldInsnNode)m.instructions.get(index)).name.equals("lava"))
							{
								
								((FieldInsnNode)m.instructions.get(index)).name = "finiteLava";
								((FieldInsnNode)m.instructions.get(index)).owner = "com/mcfht/finitewater/FiniteWater";
								System.out.println("Patched to : " + ((FieldInsnNode)m.instructions.get(index)).name);
							}
							
							
							if (((FieldInsnNode)m.instructions.get(index)).name.equals("DEPflowing_lava"))
							{
								
								((FieldInsnNode)m.instructions.get(index)).name = "finiteLava";
								((FieldInsnNode)m.instructions.get(index)).owner = "com/mcfht/finitewater/FiniteWater";
								((FieldInsnNode)m.instructions.get(index)).desc = "Lnet/minecraft/block/Block";
								System.out.println("Patched to : " + ((FieldInsnNode)m.instructions.get(index)).name);
							}
							
							
							if (((FieldInsnNode)m.instructions.get(index)).name.equals("water"))
							{
								//((FieldInsnNode)m.instructions.get(index)).name = "finiteWater";
								((FieldInsnNode)m.instructions.get(index)).name = "finiteWater";
								((FieldInsnNode)m.instructions.get(index)).owner = "com/mcfht/finitewater/FiniteWater";
								//((FieldInsnNode)m.instructions.get(index)).desc = "Lnet/minecraft/block/Block";
								
								
								System.out.println("Patched to : " + ((FieldInsnNode)m.instructions.get(index)).name);
							}
							if (((FieldInsnNode)m.instructions.get(index)).name.equals("DEPflowing_water"))
							{
								((FieldInsnNode)m.instructions.get(index)).name = "finiteWater";
								((FieldInsnNode)m.instructions.get(index)).owner = "com/mcfht/finitewater/FiniteWater";
								System.out.println("Patched to : " + ((FieldInsnNode)m.instructions.get(index)).name);
							}
							
						}
						break;
							
					default:
						break;
					}
				}
			}
		}
		
	
		
		//ASM specific for cleaning up and returning the final bytes for JVM processing.
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
				//ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		classNode.accept(writer);
		System.out.println("*** DONE!");
		return writer.toByteArray();
	}
	

	
	
	
}
