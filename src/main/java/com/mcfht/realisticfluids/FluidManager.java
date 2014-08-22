package com.mcfht.realisticfluids;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import com.mcfht.realisticfluids.FluidData.ChunkCache;
import com.mcfht.realisticfluids.FluidData.ChunkData;
import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;
import com.mcfht.realisticfluids.fluids.BlockFiniteWater;

/**
 * Handles virtually all fluid calculations. Manages worker threads.
 *
 * @author FHT
 *
 */
public class FluidManager
{
	public static Delegator delegator = new Delegator();

	public static class WorkerThread {
		Thread thread = null;
		FluidWorker worker = null;

		public WorkerThread(final FluidWorker worker) {
			thread = new Thread(worker);
			this.worker = worker;
		}
	}

	/**
	 * Delegates tasks to different threads
	 *
	 * @author FHT
	 *
	 */
	public static class Delegator
	{
		public AtomicInteger sweepCost = new AtomicInteger(0);
		public int myStartTick;
		public World[] worlds;

		// Dun saturate
		public final int threads = Math.max(2,(RealisticFluids.CORES - 2) >> 1 << 1);

		public int sleepTime = Math.max(3, Math.min(15, ((50 * RealisticFluids.GLOBAL_RATE - 1) + 20)/(threads+3)));

		// Cycle through the available threads
		public int threadIndex = 0;

		public ArrayList<WorkerThread> threadPool = new ArrayList<WorkerThread>(threads);

		public void performTasks()
		{
			// Ensure we have adequate threads
			while (threadPool.size() < threads)
				threadPool.add(new WorkerThread(new FluidWorker()));

			// System.out.println("Operating with " + RealisticFluids.CORES +
			// " cores, " + this.threads + " threads.");
			// System.out.println("Interval: " + RealisticFluids.GLOBAL_RATE);

			for (final World world : worlds) {
				// There are no players, so there is no point
				if (world.playerEntities == null
						|| world.playerEntities.size() == 0)
					continue;

				// First, iterate over near chunks
				final ChunkCache chunks = FluidData.worldCache.get(world);

				if (chunks == null)
					continue;

				for (final Chunk c : chunks.priority) {
					final ChunkData data = chunks.chunks.get(c);
					if (data == null || !c.isChunkLoaded) {
						System.err.println("Attempting to do flow in inactive chunk! This should not happen!");
						continue;
					}


					while (threadPool.size() < threads)
						threadPool.add(new WorkerThread(new FluidWorker()));

					final WorkerThread wt = threadPool.get(threadIndex);
					wt.worker.tasks.add(new Task(data, true, myStartTick));
					wt.worker.index = threadIndex;
					// attempt to prevent task queue flooding and move new tasks
					// forward
					if (wt.worker.tasks.size() > 320)
						for (int i = 0; i < 8; i++)
							wt.worker.tasks.poll();
				}
				chunks.priority.clear();

				// Now do thingimy stuffs...
				while (chunks.distant.size() > 0) {
					final Chunk c = chunks.distant.poll();

					final ChunkData data = chunks.chunks.get(c);
					if (data == null || !c.isChunkLoaded)
						continue;

					final WorkerThread wt = threadPool.get(threadIndex + threads / 2);
					wt.worker.tasks.add(new Task(data, false, myStartTick));
				}

				threadIndex = (threadIndex + 1) % (threads / 2);
			}

			// Reset quota counter
			sweepCost.set(0);
			sleepTime = Math.max(3, Math.min(15, ((50 * RealisticFluids.GLOBAL_RATE - 1) + 20)/(threads+3)));
			for (final WorkerThread wt : threadPool)
				if (wt.worker.tasks.size() > 0 && !wt.worker.running)
					wt.thread.run();

		}
	}

	public static class Task {
		public boolean isHighPriority;
		public int myStartTick;
		public ChunkData data;

		public Task(final ChunkData data, final boolean highPriority,
				final int startTick) {
			this.data = data;
			isHighPriority = highPriority;
			myStartTick = startTick;
		}
	}

	public static class FluidWorker implements Runnable {
		public boolean running = false;
		public boolean forceQuit = false;
		public int cost;
		public int index = 0;
		public ConcurrentLinkedQueue<Task> tasks = new ConcurrentLinkedQueue<Task>();
		public int tasksThisInterval = 0;

		@Override
		public void run()
		{

			tasksThisInterval = tasks.size();

			try { Thread.sleep(index * delegator.sleepTime); }
			catch (final InterruptedException e) { run(); return; }

			running = true;
			int i = 0;
			while (tasks.size() > 0 && !forceQuit) {

				if (++i == 10)
				{
					//Give other threads on this core some time to work every now and then lol
					try { Thread.sleep(2); }
					catch (final InterruptedException e) { run(); return; }
					i = 0;
				}
				// System.out.println("Fluid Worker stuffing!");

				final Task task = tasks.poll();

				if (task == null)
					return;

				// System.out.println("Has task! pri: " + task.isHighPriority +
				// "(" + delegator.sweepCost.get() + ")");

				if (!task.isHighPriority && delegator.sweepCost.get() > RealisticFluids.FAR_UPDATES)
					return;

				// System.out.println("Doing task!");
				// this.cost = 32 + doTask(task.data, task.isHighPriority,
				// task.myStartTick);

				delegator.sweepCost.addAndGet(task.isHighPriority
								? doTask(task.data, task.isHighPriority, task.myStartTick) >> 2
								: doTask(task.data,	task.isHighPriority, task.myStartTick));
			}
			running = false;
		}
	}

	/**
	 * Performs updates within a chunk (or more precisely, a ChunkCache object
	 *
	 * @param w
	 * @param c
	 * @param data
	 * @param flag
	 *            Do heavy equalization?
	 * @return
	 */
	public static int doTask(final ChunkData data, final boolean isHighPriority, final int startTime) {
		final int interval = (startTime % RealisticFluids.GLOBAL_RATE);
		int cost = 0;
		//final int cyc = 3;
		final int x, y, z;

		// Iterate over each
		for (int i = 0; i < 16; i++) {

			// Don't bother with empty spaces
			if (data.c.getBlockStorageArray()[i] == null)
				continue;

			// First of all, let's perform our own random ticks (maor control)
			// do evaporation, seeping, refilling in rain, and so on.
			doRandomTicks(data, i, 3, isHighPriority);

			//No updates
			if (data.updates[i] == null || data.updates[i].updates.pos <= 0) continue;

			System.out.println("Ticking Chunk " + Util.intStr(data.c.xPosition, data.c.zPosition) + " segment " + i + ", theoretical updates: " + data.updates[i].updates.pos);

			data.updates[i].updates.resetClone();
			while (data.updates[i].updates.pos1 > 0)
			{
				final int index = data.updates[i].updates.poll();
				if (index < 0) continue;
				System.out.println("   -   Doing update for block index " + index + ", Flag: " + data.updates[i].updateFlags[index]);
				cost += tickCell(data, i, index, interval);
			}

			/*
			if (data.updates[i].currentData.pos > 0)
				System.arraycopy(data.updates[i].currentData.data, 0, data.updates[i].updates.data, 0, data.updates[i].currentData.pos);
			else
				data.updates[i] = null;
			 */


			/*
			// No updates, exit
			if (!data.updateCounter[i] || data.updateFlags[i] == null)
				continue;

			data.workingUpdate[i] = new boolean[4096];
			System.arraycopy(data.updateFlags[i], 0, data.workingUpdate[i], 0, 4096);
			data.updateFlags[i] = new boolean[4096];

			// cost += Math.max(16, t.updateCounter[i] >> 6); //Moved this to
			// the end

			// Reset the cube flag
			data.updateCounter[i] = false;

			// ///////////////////////////////////////////////////////////////////////////////////

			for (int j = 0; j < 4096; j++)
				if (data.workingUpdate[i][j]) {
					cost++;
					// Un-flag this block
					data.workingUpdate[i][j] = false;

					// Rebuild the coordinates from the array position
					x = (data.c.xPosition << 4) + (j & 0xF);
					y = (i << 4) + ((j >> 8) & 0xF);
					z = (data.c.zPosition << 4) + ((j >> 4) & 0xF);

					final Block b = data.c.getBlock(x & 0xF, y, z & 0xF);
					if (b instanceof BlockFiniteFluid)
						// Tick the water block
						((BlockFiniteFluid) b).doUpdate(data, x, y, z,
								data.w.rand, interval);

				}

				cyc = (cyc + 7) & 0x3;
				switch(cyc)
				{
					case  0: cost += iterateForwards(data, i, interval);
					case  1: cost += iterateBackwards(data, i, interval);
					case  2: cost += iterateLeft(data, i, interval);
					case  3: cost += iterateRight(data, i, interval);
					default: cost += iterateForwards(data, i, interval);
				}

*/
		}

		// TODO: Make distant chunks re-render
		return cost;
	}

	public static int iterateForwards(final ChunkData data, final int i, final int interval)
	{
		int cost = 0;
		for (int j = 0; j < 4096; j++)
			cost += tickCell(data, i, j , interval);
		return cost;
	}

	public static int iterateBackwards(final ChunkData data, final int i, final int interval)
	{
		int cost = 0;
		for (int k = 0; k < 16; k++)
			for (int j = 255; j >= 0; j--)
				cost += tickCell(data, i, j + (k << 4), interval);
		return cost;
	}

	public static int iterateLeft(final ChunkData data, final int i, final int interval)
	{
		int cost = 0;
		for (int j = 0; j < 16; j++)
			for (int k = 15; k >= 0; k--)
				for (int l = 0; l < 16; l++)
					cost += tickCell(data, i, (j << 4) + (l << 4) + k, interval);
		return cost;
	}
	public static int iterateRight(final ChunkData data, final int i, final int interval)
	{
		int cost = 0;
		for (int j = 0; j < 16; j++)
			for (int k = 15; k >= 0; k--)
				for (int l = 0; l < 16; l++)
					cost += tickCell(data, i, (j << 4) + (k << 4) + l, interval);
		return cost;
	}

	public static int tickCell(final ChunkData data, final int i, final int j, final int interval)
	{
		//if (data.workingUpdate[i][j]) {
		//	data.workingUpdate[i][j] = false;

		int x,y,z;

		// Un-flag this block
		// Rebuild the coordinates from the array position
		x = (data.c.xPosition << 4) + (j & 0xF);
		y = (i << 4) + ((j >> 8) & 0xF);
		z = (data.c.zPosition << 4) + ((j >> 4) & 0xF);

		final Block b = data.c.getBlock(x & 0xF, y, z & 0xF);
		if (b instanceof BlockFiniteFluid)
			// Tick the water block
			((BlockFiniteFluid) b).doUpdate(data, x, y, z, data.w.rand, interval);
		return 1;

	}


	/**
	 * Perform a specified number of random ticks in the 16x16x16 part of the
	 * world.
	 *
	 * @param w
	 * @param c
	 * @param data
	 * @param ebsY
	 * @param number
	 * @param isHighPriority
	 */
	public static void doRandomTicks(final ChunkData data, final int ebsY,
			final int number, final boolean isHighPriority) {

		int equalizationQuota = isHighPriority
				? RealisticFluids.EQUALIZE_NEAR
				: RealisticFluids.EQUALIZE_FAR;
		for (int i = 0; i < number; i++) {

			final int x = data.w.rand.nextInt(16);
			int y = data.w.rand.nextInt(16) + (ebsY << 4);
			final int z = data.w.rand.nextInt(16);

			final Block b = data.c.getBlock(x, y, z);

			if (b instanceof BlockFiniteWater)
				if (FluidEqualizer.tasks.size() < RealisticFluids.EQUALIZE_GLOBAL) {
					// Make sure we don't overstep the equalization quota,
					// Trivial
					// unless QUOTAS are set low
					if (equalizationQuota-- <= 0)
						continue;

					// Benefit large bodies of water by trying to find surface
					// blocks
					for (int j = 0; y < 255
							&& j < 8
							&& data.w.getBlock(x, y + 1, z) instanceof BlockFiniteFluid; j++)
						y++;

					if (data.w.getBlock(x, y + 1, z) != Blocks.air)
						continue;

					final int level = data.getLevel(x, y, z);
					// Prevent spamming on flat ocean areas
					if (level < RealisticFluids.MAX_FLUID
							- (RealisticFluids.MAX_FLUID / 16))
						if (data.w.rand.nextInt(5) == 0)
							// System.out.println("Smoothing...");
							FluidEqualizer.addSmoothTask(data.w,
									(data.c.xPosition << 4) + x, y,
									(data.c.zPosition << 4) + z,
									(BlockFiniteFluid) b,
									RealisticFluids.MAX_FLUID >> 1, 8);
						else
							FluidEqualizer
									.addLinearTask(
											data.w,
											(data.c.xPosition << 4) + x,
											y,
											(data.c.zPosition << 4) + z,
											(BlockFiniteFluid) b,
											isHighPriority
													? RealisticFluids.EQUALIZE_NEAR_R
													: RealisticFluids.EQUALIZE_FAR_R,
											3);
				}

			// w.markBlockRangeForRenderUpdate(p_147458_1_, p_147458_2_,
			// p_147458_3_, p_147458_4_, p_147458_5_, p_147458_6_);
			// Do rainfall and evaporation
			// First, try to move up a few blocks (aka to the top of stuff)
			/*
			 * if (c.heightMap != null && c.heightMap[x + (z << 4)] < y + 16 &&
			 * c.heightMap[x + (z << 4)] < 255) { Block b1 = c.getBlock(x,
			 * c.heightMap[x + (z << 4)] + 1, y); if (b ==
			 * RealisticFluids.finiteWater || b == Blocks.air) doWaterFun(w, c,
			 * x, c.heightMap[x + (z << 4)] + 1, z, b); }
			 */

			// doWaterFun(data, b, x, y, z);
			// Only bother doing the next part with fluids

		}
	}

	public static void doWaterFun(final ChunkData data, final Block b,
			final int x, final int y, final int z) {
		if (data.c.canBlockSeeTheSky(x, y, z)) {
			final int yRain = data.w.isRaining() ? 62 : data.w.isThundering()
					? 63
					: 0;
			if (yRain > 0 && data.c.getHeightValue(x, z) <= yRain) {
				final int wx = x + (data.c.xPosition << 4);
				final int wz = x + (data.c.zPosition << 4);
				final BiomeGenBase biome = data.c.getBiomeGenForWorldCoords(x,
						z, data.w.provider.worldChunkMgr);

				if (biome == BiomeGenBase.ocean
						|| biome == BiomeGenBase.deepOcean
						|| biome == BiomeGenBase.river)
					if (b == Blocks.air || b instanceof BlockFiniteWater) {
						System.out.println("Rain is falling!");
						FluidData
								.setLevel(
										data,
										(BlockFiniteFluid) Blocks.water,
										wx,
										y,
										wz,
										(RealisticFluids.MAX_FLUID >> 3)
												+ FluidData
														.getLevel(
																data,
																(BlockFiniteFluid) Blocks.water,
																x & 0xF, y + 1,
																z & 0xF), true);
					}
			}
		}
	}

	/**
	 * Handles all the fun things that can happen when playing with water, like
	 * evaporation and unicorns.
	 *
	 * @param w
	 * @param c
	 * @param x
	 * @param y
	 * @param z
	 * @param b
	 */
	/*
	 * public static void doWaterFun(World w, Chunk c, int x, int y, int z,
	 * Block b) { boolean isWater = b != Blocks.air; int xx = x +
	 * (c.xPosition<<4), zz = z + (c.zPosition<<4); BiomeGenBase biome =
	 * c.getBiomeGenForWorldCoords(x, z, w.getWorldChunkManager()); if (y <= 64
	 * && (w.isRaining() || w.isThundering()) && biome.rainfall > 0F &&
	 * w.canBlockSeeTheSky(x, y, z)) { System.out.println("Rain Increasing...");
	 * if (isWater) { BlockFiniteFluid f = ((BlockFiniteFluid)b); int l0 =
	 * f.getLevel(w, xx, y, zz); l0 += y < 64 ? RealisticFluids.MAX_FLUID/6 :
	 * RealisticFluids.MAX_FLUID; f.setLevel(w, xx, y, zz, l0, true); if (l0 >
	 * RealisticFluids.MAX_FLUID) f.setLevel(w, xx, y, zz, l0 -
	 * RealisticFluids.MAX_FLUID, true); } else { w.setBlock(xx, y, zz,
	 * Blocks.water); BlockFiniteFluid f = (BlockFiniteFluid) c.getBlock(x, y,
	 * z); f.setLevel(w, xx, y, zz, f.viscosity, true); }
	 *
	 * }
	 *
	 * //Make water evaporate in deserts? if (b.getMaterial() == Material.water
	 * && biome.temperature > 1F) { System.out.println("Evaporating..");
	 * BlockFiniteFluid f = (BlockFiniteFluid) b; int l0 = f.getLevel(w, xx, y,
	 * zz); f.setLevel(w, xx, y, z, l0 - RealisticFluids.MAX_FLUID/2, true);
	 * //ChunkDataMap.setWaterLevel(w, c, x, y, z, ChunkDataMap.getWaterLevel(w,
	 * c, x, y, z) - (FiniteWater.MAX_FLUID/3)); } }
	 */
}
