package com.mcfht.realisticfluids.asm;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;


public interface ASMPatchTask {
	
	/**
	 * Perform patching task. Defers to the doPatch method.
	 * @param name
	 * @param bytes
	 * @param obfuscated
	 * @return
	 */
	public byte[] startPatch(String name, byte[] bytes, boolean obfuscated);
	
	/**
	 * Performs class-specific patching
	 * @param name
	 * @param bytes
	 * @param obfuscated
	 * @return
	 */
	public ClassNode doPatch(String name, byte[] bytes, boolean obfuscated);
	
	
	

	
	
}
