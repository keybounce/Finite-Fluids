package com.mcfht.realisticfluids.asm;

import java.util.Iterator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

public class PatchDoorUpdates implements ASMPatchTask{

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
				if (node0.getOpcode() == org.objectweb.asm.Opcodes.ICONST_2 && iter.hasNext())
				{
					index++;
					if (iter.next().getOpcode()  == org.objectweb.asm.Opcodes.INVOKEVIRTUAL)
					{
						m.instructions.set(node0, new InsnNode(org.objectweb.asm.Opcodes.ICONST_3));
						continue;
					}
				}
			}
			
		}
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
