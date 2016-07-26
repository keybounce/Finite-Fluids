/*
 * EQUALIZATION SCHEDULING SYSTEM TODO: Write better documentation
 */

package com.mcfht.realisticfluids;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.mcfht.realisticfluids.FluidData.ChunkData;
import com.mcfht.realisticfluids.FluidEqualizer.EqualizeAlgorithms.EqualizeTask;
import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;

/**
 * The thing that equalizes water.
 * 
 * @author FHT
 */
public class FluidEqualizer
{

	public static final Worker								EqualizeWorker	= new Worker();
	public static final Thread								WORKER			= new Thread(EqualizeWorker);

	protected static ConcurrentLinkedQueue<EqualizeTask>	tasks			= new ConcurrentLinkedQueue<EqualizeTask>();

	// private static ArrayList<EqualizationTask> tasks = new
	// ArrayList<EqualizationTask>();
	public static void addLinearTask(final World w, final int x, final int y, final int z, final BlockFiniteFluid f, final int... pars)
	{
		// Prevent over-filling the queue
		if (tasks.size() > 4 * RealisticFluids.EQUALIZE_GLOBAL)
			return;
		tasks.add(new EqualizeAlgorithms.EqualizeDirectionalAverage(w, f, x, y, z, pars));
	}

	public static void addSmoothTask(final World w, final int x, final int y, final int z, final BlockFiniteFluid f, final int... pars)
	{
		// Prevent over-filling the queue
		if (tasks.size() > 4 * RealisticFluids.EQUALIZE_GLOBAL)
			return;
		tasks.add(new EqualizeAlgorithms.EqualizeLayerSmooth(w, f, x, y, z, pars));
	}

	/** Perform this equalization task. <b>THREAD SAFE</b> */
	private static int equalize()
	{
		if (tasks.size() == 0)
			return 1;
		final EqualizeTask task = tasks.poll();
		if (task == null)
			return 1;
		if (task.w.getChunkFromChunkCoords(task.x >> 4, task.z >> 4).isChunkLoaded)
			return task.perform();
		return 1;
	}

	public static class Worker implements Runnable
	{
		public int		myStartTime;
		public boolean	running	= false;

		@Override
		public void run()
		{
			final long startTime = System.currentTimeMillis();
			int i = 0;
			while ((tasks.size() > 0 && System.currentTimeMillis() - startTime < 10) || (i < RealisticFluids.EQUALIZE_GLOBAL))
				i += equalize();
			tasks.clear();
		}

	}

	/**
	 * Contains a range of equalization algorithms
	 * 
	 * @author FHT
	 */
	public static class EqualizeAlgorithms
	{
		public static class EqualizeTask
		{
			World				w;
			int					x;
			int					y;
			int					z;
			BlockFiniteFluid	f;
			int[]				pars;

			public EqualizeTask(final World w, final BlockFiniteFluid f, final int x, final int y, final int z, final int... pars)
			{
				this.w = w;
				this.x = x;
				this.y = y;
				this.z = z;
				this.f = f;
				this.pars = pars;
			}

			/** Performs equalizer task. multiple allowed integer parameters. */
			public int perform()
			{
				return 0;
			}
		}

		public static class EqualizeDirectionalAverage extends EqualizeTask
		{
			public EqualizeDirectionalAverage(final World w, final BlockFiniteFluid f, final int x, final int y, final int z,
					final int... pars)
			{
				super(w, f, x, y, z, pars);
			}

			@Override
			public int perform()
			{
				final Chunk c = this.w.getChunkFromChunkCoords(this.x >> 4, this.z >> 4);
				if (!c.isChunkLoaded)
					return 1;
				final ChunkData data = FluidData.getChunkData(c);
				if (data != null)
					return directionalAverage(data, this.f, this.x, this.y, this.z, this.pars[0], this.pars.length > 1 ? this.pars[1] : 3);
				return 1;
			}
		}

		public static class EqualizeLayerSmooth extends EqualizeTask
		{
			public EqualizeLayerSmooth(final World w, final BlockFiniteFluid f, final int x, final int y, final int z, final int... pars)
			{
				super(w, f, x, y, z, pars);
			}

			@Override
			public int perform()
			{
				final Chunk c = this.w.getChunkFromChunkCoords(this.x >> 4, this.z >> 4);
				if (!c.isChunkLoaded)
					return 1;
				final ChunkData data = FluidData.getChunkData(c);
				if (data != null)
					return layerSmooth(data, this.f, this.x, this.y, this.z, this.pars[0], this.pars[1]);
				return 1;
			}
		}

		/*
		 * Technically speaking, this algorithm is very simple. We basically
		 * just span out in lines, or branches, and as we go, average the fluid
		 * level of every block in that branch.
		 */
		/**
		 * Equalizes water in long straight lines. Distance is the length of
		 * each line, branches is the max no. of lines.
		 * 
		 * @param data
		 * @param f0
		 * @param x0
		 * @param y0
		 * @param z0
		 * @param distance
		 * @param branches
		 */
		public static int directionalAverage(ChunkData data, final BlockFiniteFluid f0, final int x0, final int y0, final int z0,
				final int distance, final int branches)
		{

			// Use negative distance to allow equalization below the surface?
			if (y0 < 1 || y0 > 255 || (distance > 0 && !data.w.isAirBlock(x0, y0, z0)))
				return 1;

			final int l0 = FluidData.getLevel(data, f0, x0 & 0xF, y0, z0 & 0xF);
			int sum = 0;
			int counter = 0;
			int totalDist = 0;
			final int skew = data.w.rand.nextInt(8);

			// boolean undermine = false;
			// Start from a random direction and rotate around in 3 semi-random
			// directions
			for (int dir = 0; dir < 8 && counter < branches; dir++)
			{
				sum = l0; // Reset the sum
				final int dx = Util.intDirX(dir + skew);
				final int dz = Util.intDirZ(dir + skew);
				int dist = 0;

				data = FluidData.testCurrentChunkData(data, x0 + dx, z0 + dx);
				if (data == null || !data.c.isChunkLoaded)
					break;

				// Similar neighbor => probably large flat area, not for this
				// algorithm. Will alsso prevent equalizing if not next to
				// another
				// fluid block
				// < f0.viscosity >> 8 as condition?
				if (Math.abs(FluidData.getLevel(data, f0, (x0 + dx) & 0xF, y0, (z0 + dz) & 0xF) - l0) > 0)
					continue;

				for (dist = 1; dist < distance; dist++)
				{
					final int x1 = x0 + dist * dx;
					final int z1 = z0 + dist * dz;
					// Ensure we are in the right data object
					data = FluidData.testCurrentChunkData(data, x1, z1);
					if (data == null || !data.c.isChunkLoaded)
					{
						--dist;
						break;
					}
					// Get the next block
					final Block b1 = data.c.getBlock(x1 & 0xF, y0, z1 & 0xF);
					// final Block b2 = data.c.getBlock(x1 & 0xF, y0 - 1, z1 &
					// 0xF);

					if (Util.isSameFluid(f0, b1))
						sum += FluidData.getLevel(data, f0, x1 & 0xF, y0, z1 & 0xF);
					// Now if we are going into air but there is water below
					else if (b1 == Blocks.air)
					{
						if (Util.isSameFluid(f0, data.c.getBlock(x1 & 0xF, y0 - 1, z1 & 0xF)))
							continue;
						break;
					} else
						break;
				}
				// Don't equalize if we didn't go very far
				if (dist <= 4)
					break;
				// Find the average for each fluid block
				final int avgL = sum / dist;
				data = FluidData.testCurrentChunkData(data, x0, z0);
				// Don't make water blocks with too little fluid in them
				if (avgL < f0.getEffectiveViscosity(data.w, data.c.getBlock(x0 & 0xF, y0 - 1, z0 & 0xF), RealisticFluids.MAX_FLUID))
					break;
				// Set the first block
				FluidData.setLevel(data, f0, x0 & 0xF, z0 & 0xF, x0, y0, z0, avgL, true);
				// Do the rest of the blocks
				for (int i = 1; i < dist; i++)
				{
					final int x1 = x0 + i * dx;
					final int z1 = z0 + i * dz;
					// Ensure we are in the right data object
					data = FluidData.forceCurrentChunkData(data, x1, z1);
					int l1 = FluidData.getLevel(data, f0, x1 & 0xF, y0, z1 & 0xF);
					if (l1 <= 0)
					{
						l1 = FluidData.getLevel(data, f0, x1 & 0xF, y0 - 1, z1 & 0xF);
						FluidData.setLevel(data, f0, x1 & 0xF, z1 & 0xF, x1, y0, z1, l1 + avgL, true);
						FluidData.setLevel(data, f0, x1 & 0xF, z1 & 0xF, x1, y0, z1, l1 + avgL - RealisticFluids.MAX_FLUID, true);

					} else
						FluidData.setLevel(data, f0, x1 & 0xF, z1 & 0xF, x1, y0, z1, avgL, true);
				}
				totalDist += dist;
				counter++;
			}

			return totalDist;
		}

		/*
		 * Technically speaking, this algorithm is very simple. We basically
		 * just span out in lines, or branches, and as we go, average the fluid
		 * level of every block in that branch.
		 */
		/**
		 * Equalizes water in long straight lines. Distance is the length of
		 * each line, branches is the max no. of lines.
		 * 
		 * @param data
		 * @param f0
		 * @param x0
		 * @param y0
		 * @param z0
		 * @param distance
		 * @param branches
		 */
		/*
		 * public static int directionalAverage(ChunkData data, final
		 * BlockFiniteFluid f0, final int x0, final int y0, final int z0, final
		 * int distance, final int branches) {
		 * 
		 * // Use negative distance to allow equalization below the surface? if
		 * (y0 < 1 || y0 > 255 || (distance > 0 && data.c.getBlock(x0 & 0xF, y0
		 * + 1, z0 & 0xF) != Blocks.air)) return 1;
		 * 
		 * final int l0 = FluidData.getLevel(data, f0, x0 & 0xF, y0, z0 & 0xF);
		 * int sum = 0; int counter = 0; int totalDist = 0; final int skew =
		 * data.w.rand.nextInt(8);
		 * 
		 * // boolean undermine = false; // Start from a random direction and
		 * rotate around in 3 semi-random // directions for (int dir = 0; dir <
		 * 8 && counter < branches; dir++) { sum = l0; // Reset the sum final
		 * int dx = Util.intDirX(dir + skew); final int dz = Util.intDirZ(dir +
		 * skew); int dist = 0;
		 * 
		 * data = FluidData.testCurrentChunkData(data, x0 + dx, z0 + dx); if
		 * (data == null || !data.c.isChunkLoaded) break;
		 * 
		 * // Similar neighbor => probably large flat area, not for this //
		 * algorithm. if (Math.abs(FluidData.getLevel(data, f0, (x0 + dx) & 0xF,
		 * y0, (z0 + dz) & 0xF) - l0) < f0.viscosity >> 5) continue;
		 * 
		 * for (dist = 1; dist < distance; dist++) { final int x1 = x0 + dist *
		 * dx; final int z1 = z0 + dist * dz; // Ensure we are in the right data
		 * object data = FluidData.testCurrentChunkData(data, x1, z1); if (data
		 * == null || !data.c.isChunkLoaded) { --dist; break; } // Get the next
		 * block, and the block below final Block b1 = data.c.getBlock(x1 & 0xF,
		 * y0, z1 & 0xF); final Block b2 = data.c.getBlock(x1 & 0xF, y0 - 1, z1
		 * & 0xF); if (Util.isSameFluid(f0, b2)) { if (b1 == Blocks.air)
		 * continue; else if (Util.isSameFluid(f0, b1)) { sum +=
		 * FluidData.getLevel(data, f0, x1 & 0xF, y0, z1 & 0xF); continue; }
		 * else { --dist; break; } } else if (b1 == Blocks.air) break; }
		 * 
		 * if (dist <= 3) break; if (sum / dist < f0.viscosity >> 2) break;
		 * 
		 * final int avgL = sum / dist;
		 * 
		 * data = FluidData.testCurrentChunkData(data, x0, z0);
		 * FluidData.setLevel(data, f0, x0 & 0xF, z0 & 0xF, x0, y0, z0, avgL,
		 * true);
		 * 
		 * for (int i = 1; i < dist; i++) { final int x1 = x0 + i * dx; final
		 * int z1 = z0 + i * dz; // Ensure we are in the right data object data
		 * = FluidData.forceCurrentChunkData(data, x1, z1); final Block b1 =
		 * data.c.getBlock(x1 & 0xF, y0, z1 & 0xF); if (b1 == Blocks.air ||
		 * Util.isSameFluid(f0, b1)) { final Block b2 = data.c.getBlock(x1 &
		 * 0xF, y0 - 1, z1 & 0xF); final int l1 = FluidData.getLevel(data, f0,
		 * x0 & 0xF, y0 - 1, z0 & 0xF);; if (b2 == Blocks.air ||
		 * Util.isSameFluid(f0, b2) && l1 < RealisticFluids.MAX_FLUID) { final
		 * int lT = avgL + l1; FluidData.setLevel(data, f0, x0 & 0xF, z0 & 0xF,
		 * x0, y0 - 1, z0, lT, true); FluidData.setLevel(data, f0, x0 & 0xF, z0
		 * & 0xF, x0, y0, z0, lT - RealisticFluids.MAX_FLUID, true); //
		 * FluidData.mergeTopBottomFluid(data, f0, x1, y0 - // 1, z1, y0, avgL);
		 * } else FluidData.setLevel(data, f0, x1 & 0xF, z1 & 0xF, x1, y0, z1,
		 * avgL, true); } else break; } totalDist += dist; counter++; }
		 * 
		 * return totalDist; }
		 */

		/*
		 * This equalization algorithm is perhaps ~the fastest large area
		 * "smoothing" implementation [though smaller areas than 16x16x16 will
		 * be faster ofc~. Quite simply, we iterate over the surface of the
		 * water, determine a max and min value as we go, and then assuming the
		 * entire layer was made up of water blocks; average the water over the
		 * surface of the chunk.
		 */
		/**
		 * Equalizes all water blocks at a layer in a chunk. REQUIRES ALL BLOCKS
		 * TO BE FLUID!
		 * <p>
		 * Tolerance is the max level difference that can be equalized.
		 * <p>
		 * Threshold is similar, but is the required CHANGE before equalization
		 * can occur
		 * 
		 * @param data
		 * @param y0
		 * @param tolerance
		 * @param threshold
		 */
		public static int layerSmooth(final ChunkData data, final BlockFiniteFluid f0, final int x0, final int y0, final int z0,
				final int tolerance, final int threshold)
		{
			int cur, min = 0, max = 0, sum = 0;
			Block b1;
			int m1;

			int cx, cz;

			// Make 100% sure the target fluid data is valid
			// if (data.fluidArray[y0 >> 4] == null)
			//    data.fluidArray[y0 >> 4] = new int[4096];

			for (int i = y0 * 256; i < (y0 * 256) + 256; i++)
			{
				cx = i & 0xF;
				cz = (i & 0xFF) >> 4;
				cur = FluidData.getLevel(data, f0, cx, y0, cz);
				if (cur < min)
					min = cur;
				else if (cur > max)
					max = cur;
				b1 = data.c.getBlock(cx, y0, cz);
				if (!Util.isSameFluid(f0, b1) || max - min > tolerance)
					return (i & 0xFF) >> 5;
				sum += cur;
			}
			sum = sum / 256;

			// Make sure we are changing the level by a reasonable amount
			if (max - sum < threshold || sum - min < threshold)
				return 8;

			m1 = Util.getMetaFromLevel(sum);
			for (int i = y0 * 256; i < (y0 * 256) + 256; i++)
			{
				cx = i & 0xF;
				cz = (i & 255) >> 4;
				// cur = data.fluidArray[y0 >> 4][i & 4095] = (short) sum;
			    data.setFluid(cx, y0, cz, sum);
                // RealisticFluids.setBlock(data.w, cx, y0, cx, null, m1, -2);
                RealisticFluids.setBlock(data.w, cx + data.c.xPosition*16,
                                            y0, cz + data.c.zPosition*16, null, m1, -2);
				// Now mark all update flags at this layer?
				// data.updateFlags[y0 >> 4][i] = true;
			}
			// System.out.println("Did layer smoothing!");
			return 64;
		}

		/*
		 * TODO Linear equalizer for sub-surface situations, attempt to estimate
		 * some kind of pressure information. Ideal implementation; 1. Finds
		 * blocks above 2. Removes some fluid from a block above 3. Moves the
		 * stolen fluid into a nearby block at its own level Anticipated scope:
		 * 4 blocks up, 9x9 centered area TODO: May it be cheaper to do this
		 * within the block algorithm?
		 */

	}

}
