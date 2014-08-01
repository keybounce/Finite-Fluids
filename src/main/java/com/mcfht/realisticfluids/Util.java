package com.mcfht.realisticfluids;

import java.util.Random;

import net.minecraft.block.Block;

import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;

/**
 * Contains some handy dandy thingies to make my life easier.
 * 
 * <p>Also provides an interface which which to more easily access various methods that are scattered
 * around some of the horribly messy parts of my code.
 * @author FHT
 *
 */
public class Util {

	/** Array of relative x,y,z directions*/
	public static final int[][] directions = { {0,1}, {1,0}, {0,-1}, {-1,0}, {-1,1}, {1,1}, {1,-1}, {-1,-1} };
	public static final Random r = new Random();
	/** used to retrieve and shuffle random directions */
	public static int offset = r.nextInt(8);
	
	/**
	 * Gets an 8-directional dx from an integer
	 * @param dir
	 * @return
	 */
	public static int intDirX(int dir)
	{
		return directions[dir & 0x7][0]; 
	}
	/**
	 * Gets an 8-directional dz from an integer
	 * @param dir
	 * @return
	 */
	public static int intDirZ(int dir)
	{
		return directions[dir & 0x7][1]; 
	}
	
	/**
	 * Gets a cardinal (N-S) dx from an integer
	 * @param dir
	 * @return
	 */
	public static int cardinalX(int dir)
	{
		return directions[dir & 0x3][0]; 
	}
	/**
	 * Gets an cardinal (E-W) dz from an integer
	 * @param dir
	 * @return
	 */
	public static int cardinalZ(int dir)
	{
		return directions[dir & 0x3][1]; 
	}
	
	/**
	 * Calculates a pseudo random X/Z vector, consecutive calls will cycleall 8 directions.
	 * @return
	 */
	public static int[] nextXZ()
	{
		offset = (++offset & 0x7);
		return new int[]{directions[offset][0], directions[offset][1]};
	}
	
	
	
	/**
	 * Compares fluids. Assumes that b0 <b>is</b> a fluid
	 * @param b0
	 * @param b1
	 * @return
	 */
	public static boolean isSameFluid(Block b0, Block b1)
	{
		return (b1 instanceof BlockFiniteFluid && b0.getMaterial() == b1.getMaterial());
	}
	
	public static String intStr(int... i)
	{
		String out = " [";
		for (int val : i)
		{
			out += val + ", ";
		}
		out.substring(0, out.length()-2);
		return out + "]";
	}
	
	public static int getMetaFromLevel(int l)
	{
		return Math.max(0, 7 - (l / (RealisticFluids.MAX_FLUID >> 3)));
	}

}
