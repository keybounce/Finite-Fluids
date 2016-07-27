package com.mcfht.realisticfluids;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.mcfht.realisticfluids.FluidData.ChunkCache;
import com.mcfht.realisticfluids.FluidData.ChunkData;
import com.mcfht.realisticfluids.RealisticFluids.RainType;
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
    public static Delegator			delegator	= new Delegator();

    public static WorkerPriority	PWorker		= new WorkerPriority();
    public static Thread			PRIORITY	= new Thread(PWorker);

    public static WorkerTrivial		TWorker		= new WorkerTrivial();
    public static Thread			TRIVIAL		= new Thread(TWorker);

    public static class WorkerThread
    {
        Thread		thread	= null;
        FluidWorker	worker	= null;

        public WorkerThread(final FluidWorker worker)
        {
            this.thread = new Thread(worker);
            this.worker = worker;
        }
    }

    public static Object peek(Collection<?> set)
    {
        Iterator<?> i = set.iterator();
        if (i.hasNext())
            return i.next();
        return null;
    }

    public static Object pop(Collection<?> set)
    {
        Object e;
        Iterator<?> i = set.iterator();
        e = i.next();   // ** Throws! if empty!
        set.remove(e);
        return e;
    }

    /**
     * Delegates tasks to different threads
     *
     * @author FHT
     *
     */
    public static class Delegator
    {
        public AtomicInteger			sweepCost	= new AtomicInteger(0);
        public int						myStartTick;
        public World[]					worlds;

        // Don't saturate
        // public final int				threads		= Math.max(2, (RealisticFluids.CORES - 2) >> 1 << 1);
        // Officially saying "don't pretend to be multitasking" anymore.
        public final int                threads     = 2;    // Code has separate NEAR and FAR queues.
        public final int                NEAR_THREAD = 0;
        public final int                FAR_THREAD  = 1;

        public LinkedHashSet<Chunk>            nearChunkSet = new LinkedHashSet<Chunk>();
        public LinkedHashSet<Chunk>            farChunkSet = new LinkedHashSet<Chunk>();

        // Cycle through the available threads
        public int						threadIndex	= 0;

        public ArrayList<WorkerThread>	threadPool	= new ArrayList<WorkerThread>(this.threads);

        private Boolean FirstRunFlag = true; /* Debug */

        public void performTasks()
        {
            // Ensure we have adequate threads
            int missing = this.threads - this.threadPool.size();
            for (int i = 0; i < missing; i++)
            {
                this.threadPool.add(new WorkerThread(new FluidWorker()));
            //    System.out.printf("Just added thread. i %d, pool size %d\n", i, this.threadPool.size());
            }

            // System.out.println("Operating with " + RealisticFluids.CORES +
            // " cores, " + this.threads + " threads.");
            // System.out.println("Interval: " + RealisticFluids.GLOBAL_RATE);

            if (FirstRunFlag)
            {
                System.out.printf("*CORES*, in use %d, %d\n", RealisticFluids.CORES, this.threads);
                FirstRunFlag = false;
            }

            for (final World world : this.worlds)
            {
//                // There are no players, so there is no point
                // Change! Will still flow water / clean things up in loaded chunks. I hope.
//                if (world.playerEntities == null || world.playerEntities.size() == 0)
//                    continue;

                // First, iterate over near chunks
                final ChunkCache chunks = FluidData.worldCache.get(world);

                if (chunks == null)
                    continue;

                for (final Chunk c : chunks.priority)
                {
                    final ChunkData data = chunks.chunks.get(c);
                    if (data == null || !c.isChunkLoaded)
                    {
                        System.err.println("Attempting to do flow in inactive chunk! This should not happen!");
                        continue;
                    }
                    final WorkerThread wt;

                    wt = this.threadPool.get(this.threadIndex);
                    wt.worker.tasks.add(new Task(data, true, this.myStartTick));
                    nearChunkSet.add(c);    // Always track this as a near chunk
                    farChunkSet.remove(c);  // Whether it was in far before or not, it's not now.

                    // FIXME("Need to remove that task from the far thread's task list");
                    
                    // attempt to prevent task queue flooding
                    // if (wt.worker.tasks.size() > 320)
                    // for (int i = 0; i < 8; i++)
                    // wt.worker.tasks.poll();
                }
                chunks.priority.clear();

                // Now do thingimy stuffs...
                while (chunks.distant.size() > 0)
                {
                    final Chunk c = (Chunk) pop(chunks.distant);

                    if (nearChunkSet.contains(c) || farChunkSet.contains(c))
                        continue;
                    
                    final ChunkData data = chunks.chunks.get(c);
                    if (data == null || !c.isChunkLoaded)
                        continue;

                    farChunkSet.add(c);
                    final WorkerThread wt = this.threadPool.get(this.threadIndex + this.threads / 2);
                    wt.worker.tasks.add(new Task(data, false, this.myStartTick));
                }

                // this.threadIndex = (this.threadIndex + 1) % (this.threads / 2);
                // Pretty sure that messes up non-overworld priority/distant queues.
            }

            this.sweepCost.set(0);
            // System.out.print("Worker task size count:");

            // This is NOT threading!
            // Java needs thread.start, not thread.run, to multi-task.
            // After that, the thread needs a signalling system to allow it to pause
            // when the queue is empty and restart when stuff is in the queue.
            // Finally, there needs to be a volatile variable for the workers to
            // write and the server to read, otherwise there is no proper transmission
            // of data to the server thread.
            //
            // Current status: The volatile stuff is there, but disabled. None of the
            // signalling/control is there. Ultimately, I'd want a single queue of work
            // that is read by all the threads.

            for (WorkerThread wt: this.threadPool)
            {
                System.out.printf("%d ", wt.worker.tasks.size());
                wt.thread.run();
            }
            System.out.printf("\n");
            
            for (final WorkerThread wt : this.threadPool)
            {
                // if (wt.worker.tasks.size() > 0 && !wt.worker.running)
                    // System.out.println("Restarting thread, " +
                    // wt.worker.tasks.size() + " tasks...");
                    // try {
                     //   wt.thread.run();
                    /*} catch (final Exception e)
                    {
                        System.out.println("Error restarting thread!");
                        // Do not permit the thread to be stuck in a failed state. If the thread
                        // has taken an exception, it may never get a chance to reset it's flags.
                        //
                        // Java won't restart it if it is already started. We just need to make sure
                        // that it must end in a state of "exited and ready to restart".
                        //
                        // Worst case: Clean exit with "forceQuit" set true by mistake. This will
                        // be corrected on the next server tick.
                        //
                        wt.worker.forceQuit = true; // Force it to exit
                        wt.worker.running = false; // Force a restart until it does
                    }*/


            }
        }
    }

    public static class Task
    {
        public boolean		isHighPriority;
        public int			myStartTick;
        public ChunkData	data;

        public Task(final ChunkData data, final boolean highPriority, final int startTick)
        {
            this.data = data;
            this.isHighPriority = highPriority;
            this.myStartTick = startTick;
        }
    }

    public static class FluidWorker implements Runnable
    {
        public boolean						running		= false;
        public boolean						forceQuit	= false;
        public int							cost;
        public ConcurrentLinkedQueue<Task>	tasks		= new ConcurrentLinkedQueue<Task>();

        @Override
        public void run()
        {
            int totalCost = 0;
            if (this.tasks.size() == 0)
                return;
            // System.out.println("Fluid Worker -> " + this.tasks.size() + ", " + this.forceQuit);

            while (this.tasks.size() > 0 && !this.forceQuit)
            {
                this.running = true;
                // System.out.println("Fluid Worker stuffing!");

                final Task task = this.tasks.peek();

                if (task == null)
                    continue;

                // System.out.println("Has task! pri: " + task.isHighPriority +
                // "(" + delegator.sweepCost.get() + ")");

                if (!task.isHighPriority && delegator.sweepCost.get() > RealisticFluids.FAR_UPDATES)
                {
                    System.out.println("*** Fluid Worker aborting low priority queue! Sweep cost "
                            + delegator.sweepCost.get()
                            + " Far Updates " + RealisticFluids.FAR_UPDATES);
                    break;
                }
                
                this.tasks.remove(task);

                // System.out.println("Doing task!");
                // this.cost = 32 + doTask(task.data, task.isHighPriority,
                // task.myStartTick);

                // remove this chunk from the tracking sets, so it can be done again in the future
                delegator.nearChunkSet.remove(task.data.c);
                delegator.farChunkSet.remove(task.data.c);
                
                int thisCost = doTask(task.data, task.isHighPriority, task.myStartTick);
                int adjCost = thisCost;

//                if (task.isHighPriority)
//                {
//                    adjCost = thisCost >> 2;
//                }
                totalCost = delegator.sweepCost.addAndGet(adjCost);
            }
      //      if (totalCost > 27000)
        //        System.out.println("Too many liquid blocks; total blocks " + totalCost);
            this.running = false;
            this.forceQuit = false;
            System.out.printf("%d updates ", totalCost );

        }
    }
    /**
     * Thread object to perform high priority updates
     *
     * @author 4HT
     *
     */
    public static class WorkerPriority implements Runnable
    {

        public int		myStartTime;
        public int		quota;
        public World[]	worlds;

        @Override
        public void run()
        {

            for (final World world : this.worlds)
            {
                // There are no players, so there is no point
                if (world.playerEntities == null || world.playerEntities.size() == 0)
                    continue;

                final ChunkCache map = FluidData.worldCache.get(world);
                if (map == null || map.priority.size() <= 0)
                    continue;

                for (final Chunk c : map.priority)
                {
                    final ChunkData data = map.chunks.get(c);
                    if (data == null || !c.isChunkLoaded)
                    {
                        System.out.println("Map was null");
                        continue;
                    }
                    // Delegate a thread task?
                    doTask(data, true, this.myStartTime);
                }
                map.priority.clear();

            }
        }
    }

    /**
     * Thread object to perform Trivial (aka distant) updates
     *
     * @author 4HT
     *
     */
    public static class WorkerTrivial implements Runnable
    {
        public int		myStartTime;
        public int		quota;
        public World[]	worlds;

        @Override
        public void run()
        {
            // System.err.println("Running trivial updater!");
            for (final World world : this.worlds)
            {
                // There are no players, so there is no point
                if (world.playerEntities == null || world.playerEntities.size() == 0)
                    continue;

                final ChunkCache map = FluidData.worldCache.get(world);

                if (map == null)
                    // System.err.println("Map is Null!");
                    continue;
                if (map.distant.size() <= 0)
                    // System.err.println("No distant updates!");
                    continue;

                int ticksLeft = RealisticFluids.FAR_UPDATES; // Give ourselves a
                                                                // tick quota
                // System.out.println("Ticks Left : " + ticksLeft);
                while (map.distant.size() > 0 && (ticksLeft > 0))
                {
                    // Select a random distant chunk
                    // int i = world.rand.nextInt(map.distant.size());
                    final Chunk c = (Chunk) pop(map.distant); // can we just do 0?
                    // map.distant.remove(c);

                    final ChunkData data = map.chunks.get(c);
                    if (data == null || !c.isChunkLoaded)
                        continue;
                    // System.out.println("Doing trivial stuff");
                    ticksLeft -= doTask(data, false, this.myStartTime);
                }
                if (ticksLeft < 1)
                {
                    System.out.printf("Worker Trivial aborted a distant queue! Remaining map size %d",
                            map.distant.size());
                }
            }
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
    public static int doTask(final ChunkData data, final boolean isHighPriority, final int startTime)
    {
        final int interval = (startTime % RealisticFluids.GLOBAL_RATE);
        int cost = 0;
        int x, y, z;

        // Iterate over each
        for (int i = 0; i < 16; i++)
        {

            // Don't bother with empty spaces
            if (data.c.getBlockStorageArray()[i] == null)
                continue;

            // First of all, let's perform our own random ticks (more control)
            // do evaporation, seeping, refilling in rain, and so on.
            if (RealisticFluids.FlowEnabled)
            {
                doRandomMinichunkTicks(data, i, 3, isHighPriority);
            }
            // No updates, exit
            if (!data.updateCounter[i] || data.updateFlags[i] == null)
                continue;

            // Reset the cube flag
            data.updateCounter[i] = false;

            if (RealisticFluids.FlowEnabled)
            {
                data.workingUpdate[i] = new boolean[4096];
                System.arraycopy(data.updateFlags[i], 0, data.workingUpdate[i], 0, 4096);
            }
            data.updateFlags[i] = new boolean[4096];	// Yes, this is GC churn. These will still get set, just ignored.

            // cost += Math.max(16, t.updateCounter[i] >> 6); //Moved this to
            // the end

            // ///////////////////////////////////////////////////////////////////////////////////
            if (RealisticFluids.FlowEnabled)
            {
                for (int j = 0; j < 4096; j++)
                    if (data.workingUpdate[i][j])
                    {
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
                            ((BlockFiniteFluid) b).doUpdate(data, x, y, z, data.w.rand, interval);

                    }
            }
        }
        // Finally, overall rainfall. This is per-chunk, not per-mini chunk, so it must be outside that loop
        if (RealisticFluids.FlowEnabled)
        {
            doChunkRainfall(data, 3, isHighPriority);
        }

        return cost;
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
    public static void doRandomMinichunkTicks(final ChunkData data, final int ebsY, final int number, final boolean isHighPriority)
    {
        if (!RealisticFluids.FlowEnabled)
            return;     // Nothing happens if fluid flow is off.
        int equalizationQuota = isHighPriority ? RealisticFluids.EQUALIZE_NEAR : RealisticFluids.EQUALIZE_FAR;
        for (int i = 0; i < number; i++)
        {

            final int cx = data.w.rand.nextInt(16);
            int wy = data.w.rand.nextInt(16) + (ebsY << 4);
            final int cz = data.w.rand.nextInt(16);

            final Block b = data.c.getBlock(cx, wy, cz);
            // w.markBlockRangeForRenderUpdate(p_147458_1_, p_147458_2_,
            // p_147458_3_, p_147458_4_, p_147458_5_, p_147458_6_);
            // Do rainfall and evaporation
            // First, try to move up a few blocks (aka to the top of stuff)
/*              // OLD CODE, disabled, reformatted.
            if (c.heightMap != null && c.heightMap[x + (z << 4)] < y + 16
                    && c.heightMap[x + (z << 4)] < 255)
            {
                Block b1 = c.getBlock(x, c.heightMap[x + (z << 4)] + 1, y);
                if (b == RealisticFluids.finiteWater || b == Blocks.air)
                    doWaterFun(w, c, x, c.heightMap[x + (z << 4)] + 1, z, b);
            }
*/
            // doWaterFun(data, b, x, y, z);
            // Only bother doing the next part with fluids
            if (b instanceof BlockFiniteFluid && FluidEqualizer.tasks.size() < RealisticFluids.EQUALIZE_GLOBAL)
            {
                // Make sure we don't overstep the equalization quota, Trivial
                // unless QUOTAS are set low
                if (equalizationQuota-- <= 0)
                {
                    // System.out.printf("BlockTicks hit equalization quota and aborted! ");
                    // System.out.printf("ChunkX %d, Chunk Z %d, ", data.c.xPosition, data.c.zPosition);
                    // System.out.printf((isHighPriority ? "*HIGH* priority\n" : "Low priority\n"));
                    continue;
                }
                // Benefit large bodies of water by trying to find surface
                // blocks
                for (int j = 0; wy < 255 && j < 8 && data.c.getBlock(cx, wy + 1, cz) instanceof BlockFiniteFluid; j++)
                    wy++;

                if (data.c.getBlock(cx, wy + 1, cz) != Blocks.air)
                    continue;

                final int level = data.getLevel(cx, wy, cz);
                // Prevent spamming on flat ocean areas
                if (level < RealisticFluids.MAX_FLUID - (RealisticFluids.MAX_FLUID / 16))
                    if (data.w.rand.nextInt(5) == 0)
                        // System.out.println("Smoothing...");
                        FluidEqualizer.addSmoothTask(data.w, (data.c.xPosition << 4) + cx, wy, (data.c.zPosition << 4) + cz,
                                (BlockFiniteFluid) b, RealisticFluids.MAX_FLUID >> 1, 8);
                    else
                        FluidEqualizer.addLinearTask(data.w, (data.c.xPosition << 4) + cx, wy, (data.c.zPosition << 4) + cz,
                                (BlockFiniteFluid) b, isHighPriority ? RealisticFluids.EQUALIZE_NEAR_R : RealisticFluids.EQUALIZE_FAR_R, 3);
            }
        }
    }

/*
 * Updated idea:
 * If config is simple,
 * And is raining (** How to test in a mod dimension??? **)
 * And the biome at X/Z has a base height of 0 or less
 * And the biome at X/Z permits rain
 * And a given X/Z (three checks per chunk)'s top height is lessthan or equal to sea level (** How to test sea level?? **)
 *
 * world.isRaining -- test for rain
 * ... something to determine the biome at x/z, check the root height of the biome for 0 or less
 * (Need to know how to find the biome at x/z, there's a method for the root height)
 * Walk Y from 255 down, find the first non-air block, see if that is below sea level
 * (Need to figure out how to determine sea level -- "average ground level" isn't, and it's also not sea level)
 * world.canLightningStrikeAt (x,y,z) -- test for sky exposure
 * And if so, plop some rainwater down.
 */
    private static void doChunkRainfall(ChunkData data, int count, boolean isHighPriority)
    {
        // Test for simple config
        if (RealisticFluids.RAINTYPE == RainType.NONE)
            return;
        // Test for raining
        if (! data.w.isRaining())
            return;
        // Loop count times
        for (int i=0; i<count; i++)
            doRainOnce(data, isHighPriority);
    }

    /*
     * Since I'm going to make massive loops on this, I'm commenting out the try block.
     * The time cost would be significant.
     *
     * GRR. WASTED. Turns out I need to loop over isAirBlock, and that requires going through
     * Block with world coordinates, and can be overridden. So I can't shortcut.
     */
    static Block fastGetBlockChunk(Chunk c, int cx, int cy, int cz)
    {
        Block block = Blocks.air;

        if (cy >> 4 < c.getBlockStorageArray().length)
        {
            ExtendedBlockStorage extendedblockstorage = c.getBlockStorageArray()[cy >> 4];

            if (extendedblockstorage != null)
            {
//                try
                {
                    block = extendedblockstorage.getBlockByExtId(cx, cy & 15, cz);
                }
//                catch (Throwable throwable)
//                {
//                    CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Getting block");
//                    CrashReportCategory crashreportcategory = crashreport.makeCategory("Block being got");
//                    crashreportcategory.addCrashSectionCallable("Location", new Callable()
//                    {
//                        private static final String __OBFID = "CL_00000374";
//                        public String call()
//                        {
//                            return CrashReportCategory.getLocationInfo(p_150810_1_, p_150810_2_, p_150810_3_);
//                        }
//                    });
//                    throw new ReportedException(crashreport);
//                }
            }
        }

        return block;
    }

    static int yOfTopNonAir (World w, int wx, int wz)
    {
        int y;
        @SuppressWarnings("unused")
        Block b;
        for (y=255; y > 0; y--)
        {
            b=w.getBlock(wx, y, wz);
            if (!w.isAirBlock(wx, y, wz))
                return y;
        }
        return 0;
    }

    private static void doRainOnce (ChunkData data, boolean isHighPriority)
    {
        // Get a position (x/z) in the chunk to test
        final int cx = data.w.rand.nextInt(16);
        final int cz = data.w.rand.nextInt(16);
        final int wx = cx + (data.c.xPosition << 4);
        final int wz = cz + (data.c.zPosition << 4);
        // World's current implementation of "getBiomeGenForCoords" goes though WorldProvider,
        // so this is valid for mystcraft/RfTools/etc that alter biome information.
        final BiomeGenBase biome = data.w.getBiomeGenForCoords(wx, wz);
        // Test for Base height below or equal 0
        if (biome.rootHeight > 0)
            return;
        // Test for top block less than sea level
        final int wy=yOfTopNonAir(data.w, wx, wz); // Where the top block is
        final int rainY = wy+1;                     // Where the rain would go
        //
        // Overworld: gAGL returns 64. Water is in block 62. So:
        //  IF wy == gAGL - 2, and is material water,
        //     OR, if wy < gAGL-2
        //  THEN rain in wy+1
        final int gAGL = data.w.provider.getAverageGroundLevel();
        if (gAGL <= rainY) // If the rain would be too high regardless
            return;
        // Complicated: The Y 63 block gets rain only if Y62 is water and not full.
        // So, water and not full negates to !water or full
        // Remember, we are writing negated tests because we are writing the abort/return cases
        if (gAGL-1 == rainY // The y=63 block gets rain only if
                && ( data.c.getBlock(cx, wy, cz).getMaterial() != Material.water  // Y=62 is water
                        || data.c.getBlockMetadata(cx, wy, cz) ==0                // and it is not full
                   )
            )
            return;
        // Test for biome permits rain
        if (0 >= biome.rainfall)
            return;
        if (data.w.canSnowAtBody(wz, wy, wz, false))
            return;     // No rain in the frozen snow area!
        // Action: Plop down water, amount based on biome humidity
        data.w.setBlock(wx, rainY, wz, Blocks.flowing_water); // This line may be unnecessary.
        FluidData.setLevel(data, Blocks.flowing_water, cx, cz, wx, rainY, wz,
                (int) (biome.rainfall*RealisticFluids.MAX_FLUID/RealisticFluids.RAINSPEED), true);
    }

    // This is unused old code.
    public static void doWaterFun(final ChunkData data, final Block b, final int x, final int y, final int z)
    {
        if (data.c.canBlockSeeTheSky(x, y, z))
        {
            final int yRain = data.w.isRaining() ? 62 : data.w.isThundering() ? 63 : 0;
            if (yRain > 0 && data.c.getHeightValue(x, z) <= yRain)
            {
                final int wx = x + (data.c.xPosition << 4);
                final int wz = z + (data.c.zPosition << 4);
                final BiomeGenBase biome = data.c.getBiomeGenForWorldCoords(x, z, data.w.provider.worldChunkMgr);

                if (biome == BiomeGenBase.ocean || biome == BiomeGenBase.deepOcean || biome == BiomeGenBase.river)
                    if (b == Blocks.air || b instanceof BlockFiniteWater)
                    {
                        System.out.println("Rain is falling!");
                        FluidData.setLevelWorld(data, (BlockFiniteFluid) Blocks.water, wx, y, wz, (RealisticFluids.MAX_FLUID >> 3)
                                + FluidData.getLevel(data, (BlockFiniteFluid) Blocks.water, x & 0xF, y + 1, z & 0xF), true);
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
//
//	public static void doWaterFunEvap(World w, Chunk c, int x, int y, int z, Block b)
//	{
//		boolean isWater = b != Blocks.air;
//		int xx = x + (c.xPosition<<4),
//				zz = z + (c.zPosition<<4);
//		BiomeGenBase biome = c.getBiomeGenForWorldCoords(x, z, w.getWorldChunkManager());
//		if (y <= 64	&& (w.isRaining() || w.isThundering())
//				&& biome.rainfall > 0F && w.canBlockSeeTheSky(x, y, z))
//		{
//			System.out.println("Rain Increasing...");
//			if (isWater)
//			{
//				BlockFiniteFluid f = ((BlockFiniteFluid)b);
//				int l0 = f.getLevel(w, xx, y, zz);
//				
//				l0 += (y < 64 ? RealisticFluids.MAX_FLUID/6 : RealisticFluids.MAX_FLUID);
//				f.setLevel(w, xx, y, zz, l0, true);
//				if (l0 > RealisticFluids.MAX_FLUID)
//					f.setLevel(w, xx, y, zz, l0 - RealisticFluids.MAX_FLUID, true);
//			} else {
//				w.setBlock(xx, y, zz, Blocks.water);
//				BlockFiniteFluid f = (BlockFiniteFluid) c.getBlock(x, y, z);
//				f.setLevel(w, xx, y, zz, f.viscosity, true);
//			}
//
//		}
//
//			//Make water evaporate in deserts?
//		if (b.getMaterial() == Material.water && biome.temperature > 1F)
//		{
//			System.out.println("Evaporating..");
//			BlockFiniteFluid f = (BlockFiniteFluid) b;
//			int l0 = f.getLevel(w, xx, y, zz);
//			
//			f.setLevel(w, xx, y, z, l0 - RealisticFluids.MAX_FLUID/2, true);
//			// ChunkDataMap.setWaterLevel(w, c, x, y, z, ChunkDataMap.getWaterLevel(w, c, x, y, z) - (FiniteWater.MAX_FLUID/3));
//		}
//	}

}
