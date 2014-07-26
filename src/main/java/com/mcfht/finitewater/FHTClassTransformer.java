package com.mcfht.finitewater;

import static org.objectweb.asm.Opcodes.FDIV;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.tree.AbstractInsnNode.METHOD_INSN;

import java.util.Iterator;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;


public class FHTClassTransformer implements net.minecraft.launchwrapper.IClassTransformer 
{

	static enum Target
	{
		Door,
		Water
	}
	@Override
	public byte[] transform(String className, String arg1, byte[] classBytes) {
		
		
	/*	if (arg0.equals("akr"))
		{
			System.out.println("*** Obfuscated MC environment detected! patching water!  (classname = " + arg0 + ")");
			return patchClassASM(arg0, arg2, true);
		}
		*/
		
		if (FiniteWater.PATCH_WATER_DUPLICATION && className.equals("net.minecraft.block.BlockDynamicLiquid"))
		{
			return patchASM(className, classBytes, false, Target.Water);
		}
		if (FiniteWater.PATCH_WATER_DUPLICATION && className.equals("akr"))
		{
			
			return patchASM(className, classBytes, true, Target.Water);
		}
		
		if (FiniteWater.PATCH_DOOR_UPDATES && (className.equals("net.minecraft.block.BlockDoor") || className.equals("net.minecraft.block.BlockTrapDoor")))
		{
			return patchASM(className, classBytes, false, Target.Door);
		}
		if (FiniteWater.PATCH_DOOR_UPDATES && (className.equals("akn") || className.equals("aoe")))
		{
			return patchASM(className, classBytes, true, Target.Door);
		}
		//System.out.println("*** Obfuscated MC environment detected! patching class:  (classname = " + arg0 + ")");
		//return patchClassASM(arg0, arg2, true);
		
		return classBytes;
	}
	
	public byte[] patchASM(String name, byte[] bytes, boolean obfuscated, Target target) 
	{
		String targetMethodName = "";
		
		if (target == Target.Water)
		{
			if(obfuscated == true)
				targetMethodName ="a";
			else
				targetMethodName ="updateTick";
		}
		
		ClassNode classNode = new ClassNode();
		ClassReader classReader = new ClassReader(bytes);
		classReader.accept(classNode, 0);
		
		@SuppressWarnings("unchecked")
		Iterator<MethodNode> methods = classNode.methods.iterator();
		while(methods.hasNext())
		{
			MethodNode m = methods.next();
			int fdiv_index = -1;
			
			if ((target == Target.Door) || m.name.equals(targetMethodName))
			{
			
				AbstractInsnNode node0 = null;
		
				@SuppressWarnings("unchecked")
				Iterator<AbstractInsnNode> iter = m.instructions.iterator();
				//int index = -1;
				
				while (iter.hasNext())
				{
					//index++;
					node0 = iter.next();
					
					switch(target)
					{
					case Door:
						if (node0.getOpcode() == org.objectweb.asm.Opcodes.ICONST_2 && iter.hasNext())
						{
							//index++;
							if (iter.next().getOpcode()  == org.objectweb.asm.Opcodes.INVOKEVIRTUAL)
							{
								m.instructions.set(node0, new InsnNode(org.objectweb.asm.Opcodes.ICONST_3));
								System.out.println("Patched door!");
								continue;
							}
						}
						break;
						
					case Water:
						if (node0.getOpcode() == org.objectweb.asm.Opcodes.GETFIELD && iter.hasNext())
						{
							node0 = iter.next();
							if (node0.getOpcode() == org.objectweb.asm.Opcodes.ICONST_2)
							{
								m.instructions.set(node0, new InsnNode(org.objectweb.asm.Opcodes.ICONST_5));
								System.out.println("Patched water!");
								//index++;
								continue;
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
