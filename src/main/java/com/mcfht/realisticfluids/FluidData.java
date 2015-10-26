package com.mcfht.realisticfluids;
/* for the editor
:set ai et ts=4 sw=4
*/

// Conversion to Fluid Sanity / getLevel vs getFluid in progress.
// Known issues/ TODO:
// 1. getLevel, computation, setLevel can cross chunk boundaries! This means that
//      thread/chunk-level synchronized locks are needed! Otherwise, water will
//      behave strangely or get lost at chunk boundaries and corners.
// 2. setBlock() calls need to be broken into separate set block ID and set meta.
//      Some of this is already done in RealisticFluids.setBlock(); it needs to be
//      better.
//
//      In particular, Minecraft.setBlock() will call FiniteFluids.onBlockPlaced()!
//      Warning for infinite loops!
//
// 3. (Mod fluids are now protected from destruction).
//
// 4. Volatiles for data synchronization are broken! It's allocating a single volatile
//      array that contains normal boolean flags. Fixing ... may be simple?
//      -- Baah, first attempt failed.
//      -- Should be fixed now, an array of helpers that are volatile.
//
//  Other issues that I can think of that I did not properly document as I went along:
//
//  1. ValidateModWater() needs to be inserted for testing before we just clobber blocks.
//      Current plan: Treat same material mod liquids as "infinite sinks" -- flowing
//      fuilds will just disappear into them and go out of the world.
//      ** CHANGE: Now we only absorb when it's a small amount, and on top.
//  2. When mod waters generate runoff water -- such as digging out from a Stream block --
//      then there will just be infinite generation. Deal with it. This can be considered
//      a bonus. If the water just goes into a pool, no problem. There's no pressure, so
//      it won't flood upwards.
//      ** UPDATE: The flow only happens at the rate of block ticks, and is small enough to not be an issue.
//  3. Eventually there will have to be evaporation coded.
//  4. Ideally, there should be some sort of "infinite source" block, so that singleton
//      blocks on the sides of cliff-faces can have something like normal flow behavior.
//
//      But that would require evaporation to be scheduled/dependable, not random block ticks.


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;

/**
 * Custom data structure object. Maps worlds to extended chunk data objects,
 * ChunkCaches, which themselves map chunks to their respective extended data
 * (instantiated ChunkDataMaps).
 * 
 * @author FHT
 * 
 */
public class FluidData
{

    /** A map assigning Chunk Data to the corresponding World object */
    public static ConcurrentHashMap<World, ChunkCache>	worldCache	= new ConcurrentHashMap<World, ChunkCache>(16);

    /* Volatile variable just for forcing threaded memory flushing. */
    public static volatile int sanityFlush=0;

    /* Just to make sure that threads have a chance to flush memory. */
    public static void sanitySyncFlush()
    {
                        // Technically, this could just be a write, no read. 
        sanityFlush++;  // It is not necessary that this have any value. 
    }

    /* Refresh read caches; do not need to flush write caches */
    public static int sanitySyncRead()
    {
        int x=sanityFlush;
        return x;
    }

    /* Helper class. */

    public static class VolatileBool{
        public volatile boolean value;

        public static VolatileBool[] create(int size)
        {
            VolatileBool[] array = new VolatileBool[size];
            for(int k=0;k<size;k++)
                array[k]=new VolatileBool();
            return array;
        }
    }

    /**
     * A cache which maps Chunk Data to each Chunk, and also contains thread
     * safe updating queues of near and distant chunks.
     * 
     * @author FHT
     * 
     */
    static class ChunkCache
    {
        /** A map linking chunks to their chunk data */
        public ConcurrentHashMap<Chunk, ChunkData>	chunks;
        /** Set of chunk updates to be performed with PRIORITY */
        public ConcurrentLinkedQueue<Chunk>			priority	= new ConcurrentLinkedQueue<Chunk>();
        /** Set of distant chunks to be updated if we have time */
        public ConcurrentLinkedQueue<Chunk>			distant		= new ConcurrentLinkedQueue<Chunk>();
        /**
         * A cache which maps Chunk Data to each Chunk, and also contains thread
         * safe updating queues of near and distant chunks.
         */
        public ChunkCache()
        {
            this.chunks = new ConcurrentHashMap<Chunk, ChunkData>(1024);
        }
    }

    public static class ChunkData
    {
        // INSTANTIATED
        /** Array of fluid levels */
        public int[][]		fluidArray		= new int[16][4096];
        VolatileBool [] fluidGuard          = VolatileBool.create(16);

        /** A map of update flags, divided into EBS arrays */
        public boolean[][]	updateFlags		= new boolean[16][4096];
        VolatileBool [] updateGuard         = VolatileBool.create(16);
        /** Array of flags to be parsed during THIS sweep */
        public boolean[][]	workingUpdate	= new boolean[16][4096];
        VolatileBool [] workingGuard        = VolatileBool.create(16);

        public World		w;
        public Chunk		c;

        /** A simple counter telling us whether or not a given cube has updates */
        public boolean[]	updateCounter	= new boolean[16];

        /**
         * Initialize a new Chunk Data object for the chunk in the given world
         * 
         * @param w
         * @param c
         */
        public ChunkData(final World w, final Chunk c)
        {
            this.w = w;
            this.c = c;
            // Initialize
            for (int i = 0; i < 16; i++)
            {
                this.updateCounter[i] = false;
                this.fluidArray[i] = null; // Save memory
                this.updateFlags[i] = null; // Save memory
                this.workingUpdate[i] = null; // Save memory
            }
        }

        /**
         * Gets level in cx cy cz
         * 
         * @param cx
         * @param cy
         * @param cz
         * @return
         */
        /*
         * Warning: Call sanity first!
         * Returns actual fluid level
         * Known issue: test is redundant after sanity (this is for diff-making-sense)
         */
        public int getFluid(final int cx, final int cy, final int cz)
        {
//            Block b0=c.getBlock(cx, cy, cz);
//            if (b0 instanceof BlockFiniteFluid)
                return this.fluidArray[cy >> 4][cx + (cz << 4) + ((cy & 0xF) << 8)];
//            throw new RuntimeException("Sanity failure! getFluid on non-fluid block");
// Silly me. Sanity actually calls this, and depends on getting the raw value.
        }

        /*
         * Warning: Call sanity first!
         * Sets actual fluid level
         * Caller must clear block if set to zero
         */
        /**
         * Gets level in cx cy cz
         * 
         * @param cx
         * @param cy
         * @param cz
         * @return
         */
        public void setFluid(final int cx, final int cy, final int cz,  int l)
        {
            if (l < 0)  // FIXME Breakpoint
            {
                l=l;
            }
            this.fluidArray[cy >> 4][cx + (cz << 4) + ((cy & 0xF) << 8)] = l;
        }

// diff-mark fluid-level

        /**
         * Gets post-sanity level in cx cy cz
         * 
         * @param cx
         * @param cy
         * @param cz
         * @return fluid level
         */
        public int getLevel(final int cx, final int cy, final int cz)
        {
            sanityLevelBlock(cx, cy, cz);
            return getFluid(cx, cy, cz);
        }

        /**
         * Set level in cx cy cz
         * 
         * @param cx
         * @param cy
         * @param cz
         * @param l
         * @return
         */
        public void setLevel(final int cx, final int cy, final int cz, final int l)
        {
            // This does not need a synchronized because we don't *READ* and modify.
            // We just set a new value. Well, that's not entirely true.
            // Feel free to point out any problems that might happen from a read much earlier,
            // and only being modified now. In response, I'll say that the alterations are per-chunk,
            // and each chunk is local to a given thread/CPU. We still need to flush our data out
            // for the next loop.
            //
            // Sadface. That isn't accurate. We can flow out across chunk lines. Sadface.
            //
            sanityLevelBlock(cx, cy, cz);
            setFluid(cx, cy, cz, l);
            // if (0 == l)
                // c.func_150807_a /* setBlockIDWithMetadata */(cx, cy, cz, Blocks.air, 0);
                // System.out.printf("SetLevel to zero, should be matched with setblock for air");
            sanitySyncFlush();
        }

        /**
         * Tries to put the specified amount of fluid into the cell, and returns
         * the "overflow", along with the level of the now full block.
         * 
         * @param cx
         * @param cy
         * @param cz
         * @return
         */
        public int[] addSetLevel(final int cx, final int cy, final int cz, final int l)
        {
            // ** This routine is not actually used **
            // Synchronization of this routine is questionable at best.
            // Again, it works if everything is chunk-local implying single thread.
            if (l < 0)
                throw new RuntimeException ("Attempted to flow negative fluid into a block");
            int oldLevel = getLevel(cx, cy, cz);    // Does a read sync
            int newLevel = oldLevel + l;
            int remainder = newLevel - RealisticFluids.MAX_FLUID;
            
            if (remainder < 0)
                remainder = 0;
            if (newLevel > RealisticFluids.MAX_FLUID)
                newLevel = RealisticFluids.MAX_FLUID;
            
            setLevel(cx, cy, cz, newLevel);         // Does a write flush sync
            
            return new int[] {newLevel, remainder};
        /*
            if (this.fluidArray[cy >> 4] == null)
                this.fluidArray[cy >> 4] = new int[4096];

            final int i = this.fluidArray[cy >> 4][cx + (cz << 4) + ((cy & 0xF) << 4)];

            this.fluidArray[cy >> 4][cx + (cz << 4) + ((cy & 0xF) << 4)] = (i + l > RealisticFluids.MAX_FLUID
                    ? RealisticFluids.MAX_FLUID
                    : i + l);
            return new int[]
            {i + l, Math.max(0, i + l - RealisticFluids.MAX_FLUID)};
        */
        }

        /*
         * Warning: Call sanity first!
         * Returns a value from 0 to 8; 0 implies non-fluid (not necessarily air)
         * 1 = almost empty to 1/8th; 8 = more than 7/8th up to MAX.
         */
        public int getFluid8th(final int cx, final int cy, final int cz)
        {
            int fluid = getFluid(cx, cy, cz);
            if (0 == fluid)
                return 0;   // Technically, this isn't needed :-).
            int wholePart = (fluid-1) * 8 / RealisticFluids.MAX_FLUID; // -1 gets rounding correct; 
                                                       // consider fluid of exactly 1/8th.
            return 1+wholePart;
        }

        /*
         * Warning: Call sanity first!
         * Takes a level from 0 to 8; caller must change block if set to 0
         */
        public void setFluid8th (final int cx, final int cy, final int cz, final int level8)
        {
            setFluid (cx, cy, cz, level8 * (RealisticFluids.MAX_FLUID >> 3));
        }

/*
 * New rules for access!
 *
 * The getFluid / setFluid routines are now the low-level access to the fluid array.
 * Callers must call sanityLevelBlock before accessing them.
 * All of the getLevel / setLevel calls are responsible for calling them; callers of those
 * are responsible for air block conversion on zero.
 *
 * In other words: the *Fluid routines are the low-level, raw routines, like C _ calls.
 * The *Level routines do all housekeeping, and are the high-level accessors.
 *
 * Ordering invariant: Fluid array must either be accurate, or 0.
 * When changing a fluid, change the fluid data first, and block second.
 * If checking the array and block, if fluid level is 0, assume block is accurate.
 * If fluid is non-zero, and block is NOT FLUID, assume block is valid and fluid is leftover.
 * (may have been changed by some other block / action elsewhere)
 */

        /*
         * sanityLevelBlock does all checking for valid access.
         * Call this before any fluid level access
         */
        public void sanityLevelBlock(final int cx, final int cy, final int cz)
        {
            // This can read from adjacent chunks, so it can cross chunks and cross threads.
            // Therefore, full synchronization (double-checked locking) is needed.
            //
            @SuppressWarnings("unused")
            boolean junk = fluidGuard[cy >> 4].value; // Read from a volatile
            if (this.fluidArray[cy >> 4] == null)
            {
                synchronized(this)
                {
                    // Synchronized locks code paths;
                    // Read test of flag forces updates of all data;
                    // "&&" forces testing AFTER fluidGuard is tested.
                    if (false == fluidGuard[cy >> 4].value && this.fluidArray[cy >> 4] == null)
                    {
                        this.fluidArray[cy >> 4] = new int[4096];
                        fluidGuard[cy >> 4].value = true;
                    }
                }
            }

            // Guaranteed: fluidArray[] has valid data at this point,
            // without itself needing to be volatile.

            int level = getFluid (cx, cy, cz);
            int meta=c.getBlockMetadata(cx, cy, cz);
            Block b0=c.getBlock(cx, cy, cz);
            if (b0 instanceof BlockFiniteFluid)
            {
                // Case 1: Test for fluid block, and 0 level.
                // Set level based on block.
                if (0 == level)
                {
                    int eights=8 - meta; // Normal 0=full, and 7=tiny
                    if (meta > 7)   // Exception is falling liquid
                        eights=8;    // they are treated as full
                    setFluid8th(cx, cy, cz, eights);
                    sanitySyncFlush();
                }
            } else {    // Case 2: Not a BlockFiniteFluid; force level to be zero
                if (0 != level)
                {
                    setFluid(cx, cy, cz, 0);
                    sanitySyncFlush();
                }
            }
        }


        /**
         * Marks update in cx, cy, cz
         * 
         * @param cx
         * @param cy
         * @param cz
         */
        public void markUpdate(final int cx, final int cy, final int cz)
        {
            @SuppressWarnings("unused")
            boolean junk = updateGuard[cy >> 4].value; // Read from a volatile
            if (this.updateFlags[cy >> 4] == null)
            {
                synchronized(this)
                {
                    // Synchronized locks code paths;
                    // Read test of flag forces updates of all data;
                    // "&&" forces testing AFTER fluidGuard is tested.
                    if (false == updateGuard[cy >> 4].value && this.updateFlags[cy >> 4] == null)
                    {
                        this.updateFlags[cy >> 4] = new boolean[4096];
                        updateGuard[cy >> 4].value = true;
                    }
                }
            }
            this.updateCounter[cy >> 4] = true;
            this.updateFlags[cy >> 4][cx + (cz << 4) + ((cy & 0xF) << 8)] = true;
            // System.out.println("***********DONE************");
        }

        /**
         * Marks update in cx, cy, cz, use to mark block above for fast falling
         * fluids
         * 
         * @param cx
         * @param cy
         * @param cz
         */
        public void markUpdateImmediate(final int cx, final int cy, final int cz)
        {
            this.markUpdate(cx, cy, cz);
            // And again, set a shared singleton array element
            @SuppressWarnings("unused")
            boolean junk = workingGuard[cy >> 4].value; // Read from a volatile
            if (this.workingUpdate[cy >> 4] == null)
            {
                synchronized(this)
                {
                    // Synchronized locks code paths;
                    // Read test of flag forces updates of all data;
                    // "&&" forces testing AFTER fluidGuard is tested.
                    if (false == workingGuard[cy >> 4].value && this.workingUpdate[cy >> 4] == null)
                    {
                        this.workingUpdate[cy >> 4] = new boolean[4096];
                        workingGuard[cy >> 4].value = true;
                    }
                }
            }
            this.workingUpdate[cy >> 4][cx + (cz << 4) + ((cy & 0xF) << 8)] = true;
        }
    }

    /**
     * Flags neighboring cells to be updated. ENSURES that they are fluid first!
     * 
     * @param w
     * @param x
     * @param y
     * @param z
     */
    public static void markNeighbors(ChunkData data, final int x, final int y, final int z)
    {
        if (y < 255)
            data.markUpdateImmediate(x & 0xF, y + 1, z & 0xF);
        if (y > 0)
            data.markUpdate(x & 0xF, y - 1, z & 0xF);

        for (int i = 0; i < 4; i++)
        {
            final int x1 = (x + Util.cardinalX(i)), z1 = (z + Util.cardinalZ(i));
            data = FluidData.forceCurrentChunkData(data, x1, z1);
            data.markUpdate(x1 & 0xF, y, z1 & 0xF);
        }

    }

    /**
     * Flags neighboring cells to be updated. ENSURES that they are fluid first!
     * 
     * @param w
     * @param x
     * @param y
     * @param z
     */
    public static void markNeighborsDiagonal(ChunkData data, final int x, final int y, final int z)
    {
        if (y < 255)
            data.markUpdate(x & 0xF, y + 1, z & 0xF);
        if (y > 0)
            data.markUpdate(x & 0xF, y - 1, z & 0xF);

        for (int i = 0; i < 8; i++)
        {
            final int x1 = (x + Util.intDirX(i)), z1 = (x + Util.intDirZ(i));
            data = FluidData.forceCurrentChunkData(data, x1, z1);
            data.markUpdate(x1 & 0xF, y, z1 & 0xF);
        }
    }

    /**
     * Returns chunk data object. Assumes chunk is loaded!!!
     * 
     * @param w
     * @param c
     * @return
     */
    public static ChunkData getChunkData(final Chunk c)
    {
        final World w = c.worldObj;
        ChunkCache cache = worldCache.get(w);
        ChunkData data;
        if (cache == null)
        {
            System.err.println("There was no registered world cache! Initializing a new one...");
            cache = new ChunkCache();
            data = new ChunkData(w, c);
            cache.chunks.put(c, data);
            worldCache.put(w, new ChunkCache());
        } else
        {
            data = cache.chunks.get(c);
            if (data == null)
            {
                data = new ChunkData(w, c);
                cache.chunks.put(c, data);
            }
        }
        return data;
    }

    /**
     * Ensures that the current data object is current, and returns the correct
     * object if it is not.
     * 
     * @param data0
     * @param x1
     * @param z1
     * @return Null if unloaded chunk.
     */
    public static ChunkData testCurrentChunkData(final ChunkData data0, final int x1, final int z1)
    {
        final Chunk cOut = data0.w.getChunkFromChunkCoords(x1 >> 4, z1 >> 4);
        if (!cOut.isChunkLoaded)
            return null;
        if (cOut.xPosition != data0.c.xPosition || cOut.zPosition != data0.c.zPosition)
            return getChunkData(cOut);
        return data0;
    }

    /**
     * Ensures that the current data object is current, and returns the correct
     * object if it is not.
     * 
     * <p>
     * <b>FORCES UNLOADED CHUNKS TO LOAD
     * 
     * @param data0
     * @param x1
     * @param z1
     * @return May return null in some situations!
     */
    public static ChunkData forceCurrentChunkData(final ChunkData data0, final int x1, final int z1)
    {
        try
        {
            Chunk cOut = data0.w.getChunkFromChunkCoords(x1 >> 4, z1 >> 4);
            if (!cOut.isChunkLoaded)
                ;
            {
                // Or should this be load chunk?
                cOut = data0.w.getChunkProvider().provideChunk(x1 >> 4, z1 >> 4);
            }
            if (cOut.xPosition != data0.c.xPosition || cOut.zPosition != data0.c.zPosition)
                return getChunkData(cOut);
            return data0;
        } catch (final Exception e)
        {
            System.err.println(e.getMessage());
            return data0;
        }

    }

    /**
     * Returns the fluid level of a cell at the given coordinates in the given
     * data array. Targets specific fluid!!!
     * 
     * @param w
     * @param f0
     * @param cx
     * @param cy
     * @param cz
     * @return
     */
    public static int getLevel(final ChunkData data, final BlockFiniteFluid f0, final int cx, final int cy, final int cz)
    {
        final Block b0 = data.c.getBlock(cx, cy, cz);
        int a = data.getLevel(cx, cy, cz);
        if (a == 0 && Util.isSameFluid(f0, b0))
        {
            a = data.c.getBlockMetadata(cx, cy, cz);
            if (a >= 7)
                return f0.viscosity;
            // Give existing water bodies some capacity to absorb fluid?
            // if (a == 0) return RealisticFluids.MAX_FLUID -
            // (RealisticFluids.MAX_FLUID >> 4);
            return (8 - a) * (RealisticFluids.MAX_FLUID >> 3);
        }
        return a;
    }

    public static int getLevelWorld(final ChunkData data, final BlockFiniteFluid f0, final int x, final int y, final int z)
    {
        return getLevel(data, f0, x&0xf, y, z&0xf);
    }
    
    public static Block convertFlowingStill(final Block f0, final int level)
    {
        if (! (f0 instanceof BlockFiniteFluid))
        {
            throw new RuntimeException("Converting a mod liquid!");
        }
        if (f0.getMaterial() == Material.water)
        {
            if (level > (RealisticFluids.MAX_FLUID - (RealisticFluids.MAX_FLUID >> 3)))
                return Blocks.water;
            else
                return Blocks.flowing_water;
        } else if (level > (RealisticFluids.MAX_FLUID - (RealisticFluids.MAX_FLUID >> 3)))
            return Blocks.lava;
        else
            return Blocks.flowing_lava;
    }

    public static int setLevelWorld(final ChunkData data, final BlockFiniteFluid f0, final int x, final int y, final int z, final int l0,
            final boolean updateNeighbors)
    {
        if (0 == l0)
             return setLevel(data, Blocks.air, x & 0xF, z & 0xF, x, y, z, l0, updateNeighbors);
        else return setLevel(data, f0, x & 0xF, z & 0xF, x, y, z, l0, updateNeighbors);
    }

    public static int setLevel(final ChunkData data, Block f1, final int cx, final int cz, final int x, final int y, final int z, int l1,
            final boolean updateNeighbors)
    {
        // Note that the flow is decided, we do not care what the target is
        // unless it is unmarked fluid

        // If level is less than 0, empty the block
        if (l1 <= 0) // We are emptying the block
        {
            // System.out.println("Set a block to air!");
            Block old= data.w.getBlock(x, y, z);
            data.setLevel(cx, y, cz, 0);
            if (old instanceof BlockFiniteFluid || Blocks.air == f1)
                RealisticFluids.setBlock(data.w, x, y, z, Blocks.air, 0, 2);
            else if (old instanceof BlockAir)
            	return 0;
            if (updateNeighbors)
                markNeighbors(data, x, y, z);
            return 0;
        }

        if (l1 > RealisticFluids.MAX_FLUID)
            l1 = RealisticFluids.MAX_FLUID;

        f1 = convertFlowingStill(f1, l1);
        final Block b0 = data.c.getBlock(cx, y, cz);
        final int l0 = data.getLevel(cx, y, cz);

        // Destination air, or finite fluid, is good. Otherwise, complain.
        if (! (b0.isAir(data.w, x, y, z) || b0 instanceof BlockFiniteFluid))
        {
            // System.out.println("Protecting non-finite block " + b0 + " at " + x + ", " + y + ", " + z);
            return l1;  // pretend that we flowed as much as we were trying to ...
        }

        // SLEDGEHAMMER
        if (Math.abs(l0 - l1) <= 4)
        {
            // System.out.println("Spam blocks are a spamming...!");
            if (l0 <= 4 || l1 <= 4)
            {
                data.setLevel(cx, y, cz, 0);
                RealisticFluids.setBlock(data.w, x, y, z, Blocks.air, 0, updateNeighbors ? 3 : 2);
            }
            // updateNeighbors = false;
            return l1; // MAXXOR HAXXOR!
        }

        final int m1 = Util.getMetaFromLevel(l1);
        data.markUpdate(cx, y, cz);
        if (updateNeighbors)
            markNeighbors(data, x, y, z);

        data.setLevel(cx, y, cz, l1);

        if (Util.isSameFluid(f1, b0))
        {
            // Both blocks are realistic, and same materials
            final int m0 = Util.getMetaFromLevel(l0);
            if (m0 != m1)
            {
                if (b0 != f1)
                {   // This should no longer happen.
                    // Implies both are realistic, and different.
                    // Possible cases: Block 8 and block 9 waters.
                    // System.out.println("Impossible case 1 seen: b0, f1, xyz: " + b0 + ", " + f1
                       //                 + ", " + x + ", " + y + ", " + z);
                    // Yep, block 8 can show up in the middle of a tall lump of water that "should"
                    //  be solid 9's. I don't know why.
                    //if (b0.isAir(data.w, x, y, z) || b0 instanceof BlockFiniteFluid) // Second place
                    {
                        RealisticFluids.setBlock(data.w, x, y, z, f1, m1, 2, true); // that clobbers mod stuff
                    }
                    return l0;
                } else {
                    // Both realistic, same fluid, different meta, same block.
                    RealisticFluids.setBlock(data.w, x, y, z, null, m1, -2, true);
                    return l0;
                }
            } else {
            // Both realistic, same material, same meta.
            // Do nothing
                 return l0;
            }
        }
        // If we are here, then we are putting finite fluid into a block that does not have the same
        // liquid. This could be air, or the other type (water overriding lava, etc), or it could be
        // trying to place into a mod liquid (Streams, super-hot Lava, etc).
        // Require that the destination can only be air or "normal" liquids.
        // if (b0.isAir(data.w, x, y, z) || b0 instanceof BlockFiniteFluid)
        //
        // New: By now, we should not have a non-finite fluid. Still can be air.
        {
            RealisticFluids.setBlock(data.w, x, y, z, f1, m1, 2); //!! This is where mod liquids are wrecked!
        }
        return l0;
    }

    /**
     * Merges top and bottom fluid blocks
     * 
     * @param data
     * @param x0
     * @param y0
     * @param z0
     * @param y1
     * @param toAdd
     */
    public static void mergeTopBottomFluid(final ChunkData data, final BlockFiniteFluid f0, final int x0, final int y0, final int z0,
            final int l0, final int y1, final int l1)
    {
        final int lT = l0 + l1;
        FluidData.setLevel(data, f0, x0 & 0xF, z0 & 0xF, x0, y0, z0, lT, true);
        FluidData.setLevel(data, f0, x0 & 0xF, z0 & 0xF, x0, y1, z0, lT - RealisticFluids.MAX_FLUID, true);

    }
}
