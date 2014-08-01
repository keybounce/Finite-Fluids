/* EQUALIZATION SCHEDULING SYSTEM
 * 
 * TODO: Write better documentation
 * 
 */

package com.mcfht.realisticfluids;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.mcfht.realisticfluids.FluidData.ChunkData;
import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;

/**
 * The thing that equalizes water. Calls the equalization method in the FFluid block (which exists
 * where it does to allow overrides in different fluids later).
 * @author FHT
 *
 */
public class FluidEqualizer {

	public static final Worker EqualizeWorker = new Worker();
	public static final Thread WORKER = new Thread(EqualizeWorker);
	
	
	protected static ConcurrentLinkedQueue<EqualizationTask> tasks = new ConcurrentLinkedQueue<EqualizationTask>();
	//private static ArrayList<EqualizationTask> tasks = new ArrayList<EqualizationTask>();
	
	/**
	 * Equalization Task Object for multiple thread access stuffs
	 * @author FHT
	 */
	private static class EqualizationTask
	{
		World world;int x;int y;int z; BlockFiniteFluid f; int distance;
		public EqualizationTask(World w, int x, int y, int z, BlockFiniteFluid f, int distance)
		{
			this.world = w; this.x = x; this.y = y; this.z = z; this.f = f; this.distance = distance;
		}
	}
	
	public static void addTask(World w, int x, int y, int z, BlockFiniteFluid f, int distance)
	{
		//Prevent over-filling the queue
		if (tasks.size() > 4*RealisticFluids.EQUALIZE_GLOBAL)
		{
			return;
		}
		
		tasks.add(new EqualizationTask(w, x, y, z, f, distance));
	}
	
	/** Perform this equalization task. <b>THREAD SAFE</b>*/
	private static boolean equalize(int n)
	{
		if (n > tasks.size()) return false;
		EqualizationTask task = tasks.poll(); if (task == null) return false;
		if (task.world.getChunkFromChunkCoords(task.x >> 4, task.z >>4 ).isChunkLoaded)
		{
			EqualizeAlgorithms.directionalAverage(task.world, task.f, task.x, task.y, task.z, task.distance);
			//System.out.println("Equalizing - " + task.x + ", " + task.y + ", " + task.z);
			return true;
		}
		return false;
	}
	
	public static class Worker implements Runnable
	{
		public int myStartTime;
		public boolean running = false;

		
		@Override
		public void run() 
		{
			long startTime = System.currentTimeMillis();
			int i = 0;
			while ((tasks.size() > 0 && System.currentTimeMillis() - startTime < 10 )|| (i++ < RealisticFluids.EQUALIZE_GLOBAL))
			{
				equalize(0);
			}
			tasks.clear();
		}
			
	}	
	
	/**
	 * Contains a range of equalization algorithms
	 * @author FHT
	 *
	 */
	public static class EqualizeAlgorithms
	{

		/* This equalization algorithm is perhaps ~the fastest large area "smoothing" implementation~.
		 * 
		 * Quite simply, we iterate over the surface of the water, determine a max and min value as we go, and then
		 * assuming the entire layer was made up of water blocks; average the water over the surface of the chunk.
		 * 
		 */
		/**
		 * Equalizes all water blocks at a layer in a chunk. REQUIRES ALL BLOCKS TO BE FLUID!
		 * 
		 * <p>Tolerance is the max level difference that can be equalized.
		 * 
		 * <p>Threshold is similar, but is the required CHANGE before equalization can occur
		 * @param data
		 * @param y0
		 * @param tolerance
		 * @param threshold
		 */
		public static void layerSmooth(ChunkData data, BlockFiniteFluid f0, int y0, int tolerance, int threshold)
		{
			int cur, min = 0, max = 0, sum = 0;
			Block b1; int m1;

			int cwx = data.c.xPosition << 4;
			int cwz = data.c.xPosition << 4;
			int cx, cz;
			
			//Make 100% sure the target fluid data is valid
			if (data.fluidArray[y0 >> 4] == null) data.fluidArray[y0 >> 4] = new short[4096];
			
			for (int i = y0 *256 ; i < (y0 * 256) + 256; i++)
			{
				cx = i & 0xF; cz = (i >> 4) & 0xF;

				cur = data.fluidArray[y0 >> 4][i];
				if (cur < min) min = cur;
				else if (cur > max) max = cur;
				b1 = data.c.getBlock(cx, y0, cz);
				if (!Util.isSameFluid(f0, b1) || max - min > tolerance) return;
				sum += cur;
			}
			sum = sum/256;
			
			//Make sure we are changing the level by a reasonable amount
			if (max - sum < threshold || sum - min < threshold ) return;
			
			m1 = 8 - (sum / (RealisticFluids.MAX_FLUID >> 3));
			for (int i = y0 *256 ; i < (y0 * 256) + 256; i++)
			{
				cx = i & 0xF; cz = (i >> 4) & 0xF;
				cur = data.fluidArray[y0 >> 4][i] = (short) sum;
				RealisticFluids.setBlock(data.w, cx, y0, cz, f0, m1, -2);
				//Now mark all update flags at this layer?
				//data.updateFlags[y0 >> 4][i] = true;
			}
			
		}
		
		/* This algorithm uses the same principle as above, except that
		 * this implementation is able to handle 1-block "steps" in water level.
		 * 
		 * Basically, if we equalize over an edge AND there is a non-air block beneath us, we
		 * can move water into the space above. If the lower block is water, we move all of the fluid
		 * that we can down into it, since this looks much prettier and is cheaper here than in the
		 * fluid block class (as we can operate under some safe assumptions that cannot be made outside of this method).
		 * 
		 * Implemented Assumptions;
		 * 1. In second iteration, all blocks on our layer are air or the same fluid
		 * 2. No fluid blocks will be "emptied"
		 * 3. When moving down, we only need consider whether fluid or air
		*/
		 
		/**
		 * Similar to LayerSmooth, except able to handle single layer changes in water level.
		 * <p> Does not accommodate thresholds or tolerances.
		 * 
		 * @param data
		 * @param y0
		 * @param tolerance
		 * @param threshold
		 */
		public static void layerFlatten(ChunkData data, BlockFiniteFluid f0, int y0)
		{
			if (255 > y0 || y0 < 1) return;
			
			final int cwx = data.c.xPosition << 4;
			final int cwz = data.c.xPosition << 4;
			int cx, cz;
			
			//Make 100% sure the targets are valid
			if (data.fluidArray[y0 >> 4] == null) data.fluidArray[y0 >> 4] = new short[4096];
			if (data.fluidArray[(y0-1) >> 4] == null) data.fluidArray[(y0-1) >> 4] = new short[4096];
			
			int cur, min = 0, max = 0, sum = 0;
			Block b1; int m1;
			for (int i = y0 *256 ; i < (y0 * 256) + 256; i++)
			{
				cx = i & 0xF; cz = (i >> 4) & 0xF;
				b1 = data.c.getBlock(cx, y0, cz);
				//If it is air
				if (b1 == Blocks.air)
				{
					//If the block below is not good, exit
					if (data.c.getBlock(cx, y0-1, cz) == Blocks.air) 
						return;
					continue; //The result will be zero, so skip the calculations
				}
				else
				if (Util.isSameFluid(f0, b1))
				{
					//We are flowing into ourselves
					//Note that we ensure the sector is not null in the previous step
					cur = data.fluidArray[y0 >> 4][i];
					if (cur == 0)
					{
						cur = data.c.getBlockMetadata(cx, y0, cz);
						if (cur >= 7) cur = f0.viscosity;
						else cur = (8 - cur) * (RealisticFluids.MAX_FLUID >> 3);
					}
					sum += cur;
					continue;
				}
				return;
			}
			sum = sum/256;
			m1 = 8 - (sum / (RealisticFluids.MAX_FLUID >> 3));
			for (int i = y0 *256 ; i < (y0 * 256) + 256; i++)
			{
				cx = i & 0xF; cz = (i >> 4) & 0xF;
				if (data.c.getBlock(cx, y0, cz) == Blocks.air)
				{
					if (Util.isSameFluid(f0, data.c.getBlock(cx, y0 - 1, cz)))
					{
						//First, calculate the level differences etc
						
						//Retrieve the level
						int l0 = data.fluidArray[(y0 - 1) >> 4][i - 256];
						if (l0 == 0)
						{
							l0 = data.c.getBlockMetadata(cx, y0, cz);
							if (l0 >= 7) l0 = f0.viscosity;
							else l0 = (8 - l0) * (RealisticFluids.MAX_FLUID >> 3);
						}
						
						data.fluidArray[(y0 - 1) >> 4][i - 256] = (short) (Math.min(RealisticFluids.MAX_FLUID, l0 + sum));
						int[] result = {l0 + sum, Math.max(0, l0 + sum - RealisticFluids.MAX_FLUID)};
						
						//Now move as much water as we can straight down into the lower block
						int mN = Math.max(0, 8 - (result[0] / (RealisticFluids.MAX_FLUID >> 3)));
						data.w.setBlockMetadataWithNotify(cx, y0 - 1, cz, mN, 2);
						
						//If there was water left, put it above
						data.fluidArray[y0 >> 4][i] = (short) result[1];
						if (result[1] > 0) 
						{
							mN = Math.max(0, 8 - (result[1] / (RealisticFluids.MAX_FLUID >> 3)));
							//Trigger an update! Note that this block is air
							RealisticFluids.setBlock(data.w, cwx + cx, y0, cwz + cz, f0, mN, 3);
						}
						continue;
					}
				}
				//It wasn't air, hence it WAS a regular fluid block, so just set the level and keep going
				data.fluidArray[y0 >> 4][cx + (cz << 4) + ((y0 & 0xF) << 8)] = (short) sum;
				data.w.setBlockMetadataWithNotify(cwx + cx, y0, cwz + cz, m1, 2);
			}
			
		}
		
		/* Technically speaking, this algorithm is very simple.
		 * 
		 * First, we receive the coordinate of a block.
		 * Second, we move out in long lines, collating all of the water
		 * Finally, we average all of the water blocks in that line.
		 * 
	 	 * By moving the water around in this way, we can help average surfaces out moving large amounts of fluid
		 * closer to where that fluid will eventually end up.
		 * 
		 * Merging the fluid down when averaging is free, since it will only happen later in the updateTask method.
		 * We also try to move a little bit of water over edges to try and accelerate equalization attempts.
		 * I will look into making another algorithm to simulate water pressure effects for such situations later.
		 */
		/**	Provides a handy "interface" for the directional average algorithm. See the task method for more info.
		 */
		public static void directionalAverage(World w, BlockFiniteFluid f0, int x0, int y0, int z0, int distance)
		{
			Chunk c = w.getChunkFromChunkCoords(x0 >> 4, z0 >> 4);
			if (!c.isChunkLoaded) return;
			ChunkData data = FluidData.getChunkData(c);
			if (data != null)
			{
				//System.out.println(" - Starting linear equalizer");
				directionalAverageDo(data, f0, x0, y0, z0, distance, 3);
			}
		}
		/**
		 * Equalizes water in long straight lines. Distance is the length of each line, branches is the max no. of lines.
		 * @param data
		 * @param f0
		 * @param x0
		 * @param y0
		 * @param z0
		 * @param distance
		 * @param branches
		 */
		public static void directionalAverageDo(ChunkData data, BlockFiniteFluid f0, int x0, int y0, int z0, int distance, int branches)
		{
			//Use negative distance to allow equalization below the surface
			if (y0 < 1 || y0 > 255 || (distance > 0 && data.c.getBlock(x0 & 0xF, y0 + 1,  z0 & 0xF) != Blocks.air)) return;

			final int l0 = FluidData.getLevel(data, f0, x0 & 0xF, y0, z0 & 0xF);
			int sum = 0; int counter = 0;
			final int skew = data.w.rand.nextInt(8);
			
			//Make 100% sure the targets are valid
			if (data.fluidArray[y0 >> 4] == null) data.fluidArray[y0 >> 4] = new short[4096];
			if (data.fluidArray[(y0-1) >> 4] == null) data.fluidArray[(y0-1) >> 4] = new short[4096];
			else if (data.fluidArray[(y0-2) >> 4] == null) data.fluidArray[(y0-1) >> 4] = new short[4096];
			
			//boolean undermine = false;
			//Start from a random direction and rotate around in 3 semi-random directions
			for (int dir = 0; dir < 8 && counter < branches; dir++)
			{
				//System.out.println(" - Testing direction " + ((dir + skew) & 0x7));
				//data = FluidData.testCurrentChunkData(data, x0, z0);
				//if (data == null || !data.c.isChunkLoaded) break;
				
				sum += l0;
				int dx = Util.intDirX(dir + skew);
				int dz = Util.intDirZ(dir + skew);
				int dist = 0;
								
				data = FluidData.testCurrentChunkData(data, x0 + dx, z0 + dx);
				if (data == null || !data.c.isChunkLoaded) break;
				
				if (data.fluidArray[y0 >> 4] == null) data.fluidArray[y0 >> 4] = new short[4096];
				if (data.fluidArray[(y0-1) >> 4] == null) data.fluidArray[(y0-1) >> 4] = new short[4096];
				else if (data.fluidArray[(y0-2) >> 4] == null) data.fluidArray[(y0-1) >> 4] = new short[4096];
				
				
				//Similar neighbor => probably large flat area, not for this algorithm.
				if (Math.abs(FluidData.getLevel(data, f0, (x0 + dx) & 0xF, y0, (z0 + dz) & 0xF) - l0) < f0.viscosity >> 5) continue;
			
				for (dist = 1; dist < distance; dist++)
				{
					int x1 = x0 + dist * dx;
					int z1 = z0 + dist * dz;
					
					//Ensure we are in the right data object
					data = FluidData.testCurrentChunkData(data, x1, z1);
					if (data == null || !data.c.isChunkLoaded) break;
					
					Block b1 = data.c.getBlock(x1 & 0xF, y0, z1 & 0xF);
					Block b2 = data.c.getBlock(x1 & 0xF, y0 - 1, z1 & 0xF);
					//Only attempt to equalize if we are on water, and flowing into water or air;
					if (Util.isSameFluid(f0, b2))
					{
						if (b1 == Blocks.air || Util.isSameFluid(f0, b1) )
							sum += FluidData.getLevel(data, f0, x1 & 0xF, y0, z1 & 0xF);
						else
							break;
					}
					else
					{
						//We went a reasonable distance and flowed over an edge
						if (dist > 3 && b2 == Blocks.air)
						{	
							dist++; //Step over the edge one block
							//TODO: Make it move even more water again?
							//getBlock(x, y-2, z) isSameFluid
							// - move sum/dist down into it?
						}
						break;
					}
				}
				//Prevent the algorithm from creating new blocks with ~ridiculously~ little viscosity
				dist = (Math.min(dist, Math.max(1, sum >> 3)));
				//Equalize all of the blocks in this direction
				for (int i = 0; i <= dist; i++)	
				{
					int x1 = x0 + i * dx;
					int z1 = z0 + i * dz;
					
					data = FluidData.testCurrentChunkData(data, x1, z1);
					if (data == null || !data.c.isChunkLoaded) break;
					
					int index = (x1 & 0xF) + ((z1 & 0xF) << 4) + ((y0 & 0xF) << 8); //index of data arrays

					//If we are flowing over the top of another fluid block
					if (Util.isSameFluid(f0, data.c.getBlock(x1 & 0xF, y0-1, z1 & 0xF)))
					{
						//TODO MAKE A METHOD CALLED "fillFromBottom"
						//Retrieve the level below
						
						//int lN = data.fluidArray[(y0 - 1) >> 4][index - 256];
						int lN = data.getLevel(x1 & 0xF, y0 - 1, z1 & 0xF);
						if (lN == 0) //No marked level, so read it from metadata
						{
							lN = data.c.getBlockMetadata(x1 & 0xF, y0 - 1, z1 & 0xF);
							if (lN >= 7) lN = f0.viscosity;
							else lN = (8 - lN) * (RealisticFluids.MAX_FLUID >> 3);
						}
						
						
						//data.fluidArray[y0 >> 4][index] = (short) (Math.min(RealisticFluids.MAX_FLUID, lN + sum));
						int[] result = {lN + sum, Math.max(0, lN + (sum/dist) - RealisticFluids.MAX_FLUID)};
						
						FluidData.setLevelWorld(data, f0, x1, y0 - 1, z1, result[0], true);
						FluidData.setLevelWorld(data, f0, x1, y0, z1, result[1], true);
						//Now update the blocks accordingly
						//int mN = Util.getMetaFromLevel(result[0]);
						//RealisticFluids.setBlock(data.w, x1, y0 - 1, z1, null, mN, 2);
						//data.c.setBlockMetadata(x1 & 0xF, y0 - 1, z1 & 0xF, mN, 2);
						
						//If there was water left, put it above
						//data.setLevel(x1 & 0xF, y0, z1 & 0xF, result[1]);
						//data.fluidArray[y0 >> 4][index] = (short) result[1];
						
						//if (result[1] > 0) 
						//	RealisticFluids.setBlock(data.w, x1, y0, z1, f0, Util.getMetaFromLevel(result[1]), 3); //Trigger an update! Note that this block was air
					}
					else //If we are trying to flow into an empty block (the first loop ensures that this flow is itself valid)
					if (data.c.getBlock(x1 & 0xF, y0, z1 & 0xF) == Blocks.air)
					{
						//data.fluidArray[y0 >> 4][index] = (short) (sum/dist);
						FluidData.setLevelWorld(data, f0, x1, y0, z1, sum/dist, true);
						//data.setLevel(x1 & 0xF, y0, z1 & 0xF, sum/dist);
						//RealisticFluids.setBlock(data.w, x1, y0, z1, f0, Util.getMetaFromLevel(sum/dist), 3);
						//setLevel(w, x1, y0-1, z1, sum / dist, true);
					}
				}
				counter++; //No need to equalize too much!
			}
		}
		
		/* TODO Linear equalizer for sub-surface situations, attempt to estimate some kind of pressure information.
		 * 
		 * Ideal implementation;
		 *  1. Finds blocks above
		 *  2. Removes some fluid from a block above
		 *  3. Moves the stolen fluid into a nearby block at its own level
		 *  
		 *  Anticipated scope: 4 blocks up, 9x9 centered area
		 *  
		 *  TODO: May it be cheaper to do this within the block algorithm?
		 */
		
	}
	
}
