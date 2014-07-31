package com.mcfht.realisticfluids.asm;

import java.util.Iterator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
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

public class PatchBlockRegistry implements ASMPatchTask{

	//public static final String blockDesc = "Lnet/minecraft/block/Block;";
	//public static final String blockObfDesc = "aji;";
	public static final String blockOwner = "com/mcfht/realisticfluids/RealisticFluids";
	
	public static int counter = 0;
	
	public static final String[] blocks = {"finiteWater", "finiteLava", "finiteWater", "finiteLava"};
	public static final String blockWater = "finiteWater";
	public static final String blockLava = "finiteLava";
	
	
	@Override
	public ClassNode doPatch(String name, byte[] bytes, boolean obfuscated) 
	{
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
				int startIndex = -1;
				while (counter < 4 && iter.hasNext())
				{
					index++;
					node0 = iter.next();
					if (node0.getOpcode() == org.objectweb.asm.Opcodes.LDC && iter.hasNext())
					{
						startIndex = index++;
						AbstractInsnNode node1 = iter.next();
						if (node1.getOpcode() != Opcodes.NEW) continue;
						LdcInsnNode target = (LdcInsnNode) node0;

						//String newDesc = obfuscated ? blockObfDesc : blockDesc;
						InsnList toRemove = new InsnList();
						toRemove.add(node1);
						//Store the starting node and starting index
						if (target.cst.equals("water") || target.cst.equals("flowing_water"))
						{
     						((TypeInsnNode)m.instructions.get(startIndex+1)).desc = "com/mcfht/realisticfluids/fluids/BlockFiniteWater";
							((MethodInsnNode)m.instructions.get(startIndex+4)).owner = "com/mcfht/realisticfluids/fluids/BlockFiniteWater";
							((MethodInsnNode)m.instructions.get(startIndex+6)).owner = "com/mcfht/realisticfluids/fluids/BlockFiniteWater";
							m.instructions.set(m.instructions.get(startIndex+7), new InsnNode(Opcodes.ICONST_1));
							++counter;
						}
						
						if (target.cst.equals("flowing_lava") || target.cst.equals("lava"))
						{
							((TypeInsnNode)m.instructions.get(startIndex+1)).desc = "com/mcfht/realisticfluids/fluids/BlockFiniteLava";
							((MethodInsnNode)m.instructions.get(startIndex+4)).owner = "com/mcfht/realisticfluids/fluids/BlockFiniteLava";
							((MethodInsnNode)m.instructions.get(startIndex+6)).owner = "com/mcfht/realisticfluids/fluids/BlockFiniteLava";
							//Patch light level because we can
							m.instructions.set(m.instructions.get(startIndex+7), new LdcInsnNode(new Float(0.8F)));
							++counter;
						}
						
						
					}
				}
			}
		
		
		return classNode;
	}

	public byte[] startPatch(String name, byte[] bytes, boolean obfuscated) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		ClassNode c = doPatch(name, bytes, obfuscated);
		
		if (c == null) return null;
		
		c.accept(writer);
		System.out.println("Patched Block Registry!");
		return writer.toByteArray();
	}


	
}
