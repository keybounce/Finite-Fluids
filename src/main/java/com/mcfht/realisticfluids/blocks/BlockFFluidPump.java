package com.mcfht.realisticfluids.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockFFluidPump extends Block
{

	protected BlockFFluidPump(final Material material)
	{
		super(material);
		// TODO Auto-generated constructor stub
	}

	/**
	 * When pump updates, it moves along up to X pipe blocks, then attempts to
	 * move fluid in the given direction.
	 * 
	 * Each pipe block can store 1/4 of a block at a time, and each pipe piece
	 * has 6 orientations.
	 * 
	 * First, pump makes a collection of nodes and their following node; List of
	 * arrays of node coordinates. If a node coord is full, it first looks for
	 * an air node it can flow to, and if no node exists, moves to a random full
	 * node.
	 * 
	 * Eventually we reach the end of the full nodes, and we can eventually make
	 * a flow with the "stored" water in the pump.
	 * 
	 * If going backwards, we check the 6 adjacent blocks and use the results to
	 * determine stuffs!
	 */

}
