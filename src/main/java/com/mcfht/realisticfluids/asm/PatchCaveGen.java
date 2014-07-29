package com.mcfht.realisticfluids.asm;

import java.util.Iterator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class PatchCaveGen implements ASMPatchTask{

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
				
				while (iter.hasNext())
				{
					index++;
					node0 = iter.next();
					if (node0.getOpcode() == org.objectweb.asm.Opcodes.ICONST_1 && iter.hasNext())
					{
						index++;
						AbstractInsnNode node1 = iter.next();
						if (node1.getOpcode() == org.objectweb.asm.Opcodes.ISTORE)
						{
							//Make sure it is the correct node (FIXME THIS ~WILL~ BREAK WITH UPDATES
							if (((VarInsnNode)node1).var == 57)
							{
								//Inject a set block call to turn the lower block to stone
								//This will prevent lots of caves from being exposed
								
								InsnList toAdd = new InsnList();
								toAdd.add(new FieldInsnNode(org.objectweb.asm.Opcodes.GETSTATIC, "net/minecraft/init/Blocks", "stone", "Lnet/minecraft/block/Block;"));
								toAdd.add( new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "setBlock", "IIILnet/minecraft/block/Block;)Z"));
								
								
								m.instructions.insert(node1, toAdd);
								
								
							}
						}
					}
				}
			}
		
		System.out.println("Patched Caves!");
		return classNode;
	}

	public byte[] startPatch(String name, byte[] bytes, boolean obfuscated) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		ClassNode c = doPatch(name, bytes, obfuscated);
		
		if (c == null) return null;
		
		c.accept(writer);
		return writer.toByteArray();
	}


	
}
