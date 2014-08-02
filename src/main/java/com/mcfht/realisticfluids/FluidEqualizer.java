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
			if (y0 < 1 || y0 > 255 || (distance > 0 && data.c.getBlock(x0 & 0xF, y0 + 1, z0 & 0xF) != Blocks.air))
				return 1;

			int l0 = FluidData.getLevel(data, f0, x0 & 0xF, y0, z0 & 0xF);
			int sum = 0;
			int counter = 0;
			int totalDist = 0;
			final int skew = data.w.rand.nextInt(8);

			// Make 100% sure the targets are valid
			if (data.fluidArray[y0 >> 4] == null)
				data.fluidArray[y0 >> 4] = new short[4096];

			if (data.fluidArray[(y0 - 1) >> 4] == null)
				data.fluidArray[(y0 - 1) >> 4] = new short[4096];
			else if (data.fluidArray[(y0 - 2) >> 4] == null)
				data.fluidArray[(y0 - 1) >> 4] = new short[4096];

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
				if (data.fluidArray[y0 >> 4] == null)
					data.fluidArray[y0 >> 4] = new short[4096];
				if (data.fluidArray[(y0 - 1) >> 4] == null)
					data.fluidArray[(y0 - 1) >> 4] = new short[4096];

				// Similar neighbor => probably large flat area, not for this
				// algorithm.
				if (Math.abs(FluidData.getLevel(data, f0, (x0 + dx) & 0xF, y0, (z0 + dz) & 0xF) - l0) < f0.viscosity >> 5)
					continue;

				for (dist = 1; dist < distance; dist++)
				{
					final int x1 = x0 + dist * dx;
					final int z1 = z0 + dist * dz;

					// Ensure we are in the right data object
					data = FluidData.testCurrentChunkData(data, x1, z1);
					if (data == null || !data.c.isChunkLoaded)
						break;

					// Get the block infront, and the block below
					final Block b1 = data.c.getBlock(x1 & 0xF, y0, z1 & 0xF);
					final Block b2 = data.c.getBlock(x1 & 0xF, y0 - 1, z1 & 0xF);
					// Only attempt to equalize if we are on water, and flowing
					// into water or air;
					if (Util.isSameFluid(f0, b2))
					{
						if (b1 == Blocks.air)
							continue;
						if (Util.isSameFluid(f0, b1))
						{
							sum += FluidData.getLevel(data, f0, x1 & 0xF, y0, z1 & 0xF);
							continue;
						}
						break;
					} else if (b2 == Blocks.air && b1 == Blocks.air)
						dist++; // step over the edge one block
					break;
				}
				// Prevent the algorithm from creating new blocks with
				// ~ridiculously~ little viscosity
				dist = (Math.min(dist, Math.max(1, sum >> 4)));
				// Reset our initial block and stuff
				l0 = sum / dist;
				data = FluidData.testCurrentChunkData(data, x0, z0);
				FluidData.setLevelWorld(data, f0, x0, y0, z0, l0, false);

				// Equalize all of the blocks in this direction
				for (int i = 1; i < dist; i++)
				{
					final int x1 = x0 + i * dx;
					final int z1 = z0 + i * dz;
					data = FluidData.testCurrentChunkData(data, x1, z1);
					if (data == null || !data.c.isChunkLoaded)
						break;

					// If we are flowing over the top of another fluid block
					if (Util.isSameFluid(f0, data.c.getBlock(x1 & 0xF, y0 - 1, z1 & 0xF)))
					{
						// TODO MAKE A METHOD CALLED "fillFromBottom"
						int lN = data.getLevel(x1 & 0xF, y0 - 1, z1 & 0xF);
						if (lN == 0) // No marked level, so read it from
						// metadata
						{
							lN = data.c.getBlockMetadata(x1 & 0xF, y0 - 1, z1 & 0xF);
							if (lN >= 7)
								lN = f0.viscosity;
							else
								lN = (8 - lN) * (RealisticFluids.MAX_FLUID >> 3);
						}
						FluidData.setLevelWorld(data, f0, x1, y0 - 1, z1, lN + sum / dist, true);
						FluidData.setLevelWorld(data, f0, x1, y0, z1, lN + (sum / dist) - RealisticFluids.MAX_FLUID, true);
					} else
						FluidData.setLevelWorld(data, f0, x1, y0, z1, sum / dist, true);
				}

				if (data.c.getBlock((x0 + dist * dx) & 0xF, y0 - 1, (z0 + dist * dz) & 0xF) == Blocks.air)
					FluidData.setLevelWorld(data, f0, (x0 + dist * dx), y0 - 1, (z0 + dist * dz), sum / dist, true);

				totalDist += dist;
				counter++; // No need to equalize too much!
			}
			return totalDist;
		}

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
			if (data.fluidArray[y0 >> 4] == null)
				data.fluidArray[y0 >> 4] = new short[4096];

			for (int i = y0 * 256; i < (y0 * 256) + 256; i++)
			{
				cx = i & 0xF;
				cz = (i & 255) >> 4;

				cur = data.fluidArray[y0 >> 4][i & 4095];
				if (cur < min)
					min = cur;
				else if (cur > max)
					max = cur;
				b1 = data.c.getBlock(cx, y0, cz);
				if (!Util.isSameFluid(f0, b1) || max - min > tolerance)
					return (i & 255) >> 5;
				sum += cur;
			}
			sum = sum / 256;

			// Make sure we are changing the level by a reasonable amount
			if (max - sum < threshold || sum - min < threshold)
				return 8;

			m1 = 8 - (sum / (RealisticFluids.MAX_FLUID >> 3));
			for (int i = y0 * 256; i < (y0 * 256) + 256; i++)
			{
				cx = i & 0xF;
				cz = (i & 255) >> 4;
				cur = data.fluidArray[y0 >> 4][i & 4095] = (short) sum;
				RealisticFluids.setBlock(data.w, cx, y0, cz, null, m1, -2);
				// Now mark all update flags at this layer?
				// data.updateFlags[y0 >> 4][i] = true;
			}
			return 64;
		}

		/**
		 * Equalizes the chunk containing the target block with a random
		 * adjacent chunk. <b>This method is very expensive</b>
		 * <p>
		 * Tolerance is an estimate of max surface difference (in blocks)
		 * <p>
		 * Magnitude is a measure of how much to balance the chunks
		 * 
		 * @param data0
		 * @param y0
		 * @param tolerance
		 * @param magnitude
		 */
		public static int chunkEqualize(final ChunkData data0, final BlockFiniteFluid f0, final int x0, final int y0, final int z0,
				final int tolerance, final int magnitude)
		{
			final int cx0 = x0 >> 4;
			final int cz0 = z0 >> 4;
			for (int i = 0; i < 4; i++)
			{
				// Test for adjacent chunks
				final int dx = Util.cardinalX(i);
				final int dz = Util.cardinalZ(i);
				final Chunk c = data0.w.getChunkFromChunkCoords(cx0 + dx, cz0 + dz);
				if (c.isChunkLoaded)
					continue;

				FluidData.getChunkData(c);

				// Now we are going to iterate over each surface of the chunks
				// until we reach
				// either the magnitude

				break;
			}

			int cur, min = 0, max = 0, sum = 0;
			Block b1;
			int m1;

			int cx, cz;

			// Make 100% sure the target fluid data is valid
			if (data0.fluidArray[y0 >> 4] == null)
				data0.fluidArray[y0 >> 4] = new short[4096];

			for (int i = y0 * 256; i < (y0 * 256) + 256; i++)
			{
				cx = i & 0xF;
				cz = (i & 255) >> 4;

				cur = data0.fluidArray[y0 >> 4][i & 4095];
				if (cur < min)
					min = cur;
				else if (cur > max)
					max = cur;
				b1 = data0.c.getBlock(cx, y0, cz);
				if (!Util.isSameFluid(f0, b1) || max - min > tolerance)
					return (i & 255) >> 5;
				sum += cur;
			}
			sum = sum / 256;

			// Make sure we are changing the level by a reasonable amount
			if (max - sum < magnitude || sum - min < magnitude)
				return 8;

			m1 = 8 - (sum / (RealisticFluids.MAX_FLUID >> 3));
			for (int i = y0 * 256; i < (y0 * 256) + 256; i++)
			{
				cx = i & 0xF;
				cz = (i & 255) >> 4;
				cur = data0.fluidArray[y0 >> 4][i & 4095] = (short) sum;
				RealisticFluids.setBlock(data0.w, cx, y0, cz, null, m1, -2);
				// Now mark all update flags at this layer?
				// data.updateFlags[y0 >> 4][i] = true;
			}
			return 64;
		}

		/*
		 * This algorithm uses the same principle as above, except that this
		 * implementation is able to handle 1-block "steps" in water level.
		 */

		/**
		 * Similar to LayerSmooth, except able to handle single layer changes in
		 * water level.
		 * <p>
		 * Does not accommodate thresholds or tolerances.
		 * 
		 * @param data
		 * @param y0
		 * @param tolerance
		 * @param threshold
		 */
		public static void layerFlatten(final ChunkData data, final BlockFiniteFluid f0, final int y0)
		{
			if (255 > y0 || y0 < 1)
				return;

			final int cwx = data.c.xPosition << 4;
			final int cwz = data.c.xPosition << 4;
			int cx, cz;

			// Make 100% sure the targets are valid
			if (data.fluidArray[y0 >> 4] == null)
				data.fluidArray[y0 >> 4] = new short[4096];
			if (data.fluidArray[(y0 - 1) >> 4] == null)
				data.fluidArray[(y0 - 1) >> 4] = new short[4096];

			int cur;
			int sum = 0;
			Block b1;
			int m1;
			for (int i = y0 * 256; i < (y0 * 256) + 256; i++)
			{
				cx = i & 0xF;
				cz = (i >> 4) & 0xF;
				b1 = data.c.getBlock(cx, y0, cz);
				// If it is air
				if (b1 == Blocks.air)
				{
					// If the block below is not good, exit
					if (data.c.getBlock(cx, y0 - 1, cz) == Blocks.air)
						return;
					continue; // The result will be zero, so skip the
					// calculations
				} else if (Util.isSameFluid(f0, b1))
				{
					// We are flowing into ourselves
					// Note that we ensure the sector is not null in the
					// previous step
					cur = data.fluidArray[y0 >> 4][i];
					if (cur == 0)
					{
						cur = data.c.getBlockMetadata(cx, y0, cz);
						if (cur >= 7)
							cur = f0.viscosity;
						else
							cur = (8 - cur) * (RealisticFluids.MAX_FLUID >> 3);
					}
					sum += cur;
					continue;
				}
				return;
			}
			sum = sum / 256;
			m1 = 8 - (sum / (RealisticFluids.MAX_FLUID >> 3));
			for (int i = y0 * 256; i < (y0 * 256) + 256; i++)
			{
				cx = i & 0xF;
				cz = (i >> 4) & 0xF;
				if (data.c.getBlock(cx, y0, cz) == Blocks.air)
					if (Util.isSameFluid(f0, data.c.getBlock(cx, y0 - 1, cz)))
					{
						// First, calculate the level differences etc

						// Retrieve the level
						int l0 = data.fluidArray[(y0 - 1) >> 4][i - 256];
						if (l0 == 0)
						{
							l0 = data.c.getBlockMetadata(cx, y0, cz);
							if (l0 >= 7)
								l0 = f0.viscosity;
							else
								l0 = (8 - l0) * (RealisticFluids.MAX_FLUID >> 3);
						}

						data.fluidArray[(y0 - 1) >> 4][i - 256] = (short) (Math.min(RealisticFluids.MAX_FLUID, l0 + sum));
						final int[] result =
						{l0 + sum, Math.max(0, l0 + sum - RealisticFluids.MAX_FLUID)};

						// Now move as much water as we can straight down into
						// the lower block
						int mN = Math.max(0, 8 - (result[0] / (RealisticFluids.MAX_FLUID >> 3)));
						data.w.setBlockMetadataWithNotify(cx, y0 - 1, cz, mN, 2);

						// If there was water left, put it above
						data.fluidArray[y0 >> 4][i] = (short) result[1];
						if (result[1] > 0)
						{
							mN = Math.max(0, 8 - (result[1] / (RealisticFluids.MAX_FLUID >> 3)));
							// Trigger an update! Note that this block is air
							RealisticFluids.setBlock(data.w, cwx + cx, y0, cwz + cz, f0, mN, 3);
						}
						continue;
					}
				// It wasn't air, hence it WAS a regular fluid block, so just
				// set the level and keep going
				data.fluidArray[y0 >> 4][cx + (cz << 4) + ((y0 & 0xF) << 8)] = (short) sum;
				data.w.setBlockMetadataWithNotify(cwx + cx, y0, cwz + cz, m1, 2);
			}

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
