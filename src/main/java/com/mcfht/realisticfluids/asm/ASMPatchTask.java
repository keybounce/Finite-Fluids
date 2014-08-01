package com.mcfht.realisticfluids.asm;

import org.objectweb.asm.tree.ClassNode;

public interface ASMPatchTask {
	/** Perform patching task. Defers to the doPatch method. */
	public byte[] startPatch(String name, byte[] bytes, boolean obf);
	/** Performs class-specific patching. */
	public ClassNode doPatch(String name, byte[] bytes, boolean obf);
}
