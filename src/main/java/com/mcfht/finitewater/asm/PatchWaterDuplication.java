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

public class PatchWaterDuplication implements FHTPatchTask{

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
			if (m.name.equals(obfuscated ? "a" : "updateTick"))
			{
				AbstractInsnNode node0 = null;
				@SuppressWarnings("unchecked")
				Iterator<AbstractInsnNode> iter = m.instructions.iterator();
				int index = -1;
				while (iter.hasNext())
				{
					index++;
					node0 = iter.next();
					if (node0.getOpcode() == org.objectweb.asm.Opcodes.GETFIELD && iter.hasNext())
					{
						index++;
						node0 = iter.next();
						if (node0.getOpcode() == org.objectweb.asm.Opcodes.ICONST_2)
						{
							m.instructions.set(node0, new InsnNode(org.objectweb.asm.Opcodes.ICONST_5));
							continue;
						}
					}
				}
			}
		}
		
		System.out.println("Patched water!");
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
