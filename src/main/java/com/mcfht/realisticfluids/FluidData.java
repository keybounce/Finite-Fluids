package com.mcfht.realisticfluids;

// This is the old fluid system. Fluid access has no sanity.

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
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

        /** A map of update flags, divided into EBS arrays */
        public boolean[][]	updateFlags		= new boolean[16][4096];
        /** Array of flags to be parsed during THIS sweep */
        public boolean[][]	workingUpdate	= new boolean[16][4096];

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
        public int getLevel(final int cx, final int cy, final int cz)
        {
            if (this.fluidArray[cy >> 4] == null)
                this.fluidArray[cy >> 4] = new int[4096];
            if ((c.getBlock(cx, cy, cz) instanceof BlockFiniteFluid))
                return this.fluidArray[cy >> 4][cx + (cz << 4) + ((cy & 0xF) << 8)];
            return 0;
        }

        /**
         * Gets level in cx cy cz
         * 
         * @param cx
         * @param cy
         * @param cz
         * @return
         */
        public void setLevel(final int cx, final int cy, final int cz, final int l)
        {
            if (this.fluidArray[cy >> 4] == null)
                this.fluidArray[cy >> 4] = new int[4096];

            this.fluidArray[cy >> 4][cx + (cz << 4) + ((cy & 0xF) << 8)] = l;
        }

// diff-mark fluid-level

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
            if (this.fluidArray[cy >> 4] == null)
                this.fluidArray[cy >> 4] = new int[4096];

            final int i = this.fluidArray[cy >> 4][cx + (cz << 4) + ((cy & 0xF) << 4)];

            this.fluidArray[cy >> 4][cx + (cz << 4) + ((cy & 0xF) << 4)] = (i + l > RealisticFluids.MAX_FLUID
                    ? RealisticFluids.MAX_FLUID
                    : i + l);
            return new int[]
            {i + l, Math.max(0, i + l - RealisticFluids.MAX_FLUID)};
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
            if (this.updateFlags[cy >> 4] == null)
                this.updateFlags[cy >> 4] = new boolean[4096];
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

            if (this.workingUpdate[cy >> 4] == null)
                this.workingUpdate[cy >> 4] = new boolean[4096];

            this.workingUpdate[cy >> 4][cx + (cz << 4) + ((cy & 0xF) << 8)] = true;
            // System.out.println("***********DONE************");
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
            final int x1 = (x + Util.cardinalX(i)), z1 = (x + Util.cardinalZ(i));
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
        return setLevel(data, f0, x & 0xF, z & 0xF, x, y, z, l0, updateNeighbors);
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
            data.setLevel(cx, y, cz, 0);
            RealisticFluids.setBlock(data.w, x, y, z, Blocks.air, 0, 2);
            if (updateNeighbors)
                markNeighbors(data, x, y, z);
            return 0;
        }

        if (l1 > RealisticFluids.MAX_FLUID)
            l1 = RealisticFluids.MAX_FLUID;

        f1 = convertFlowingStill(f1, l1);
        final Block b0 = data.c.getBlock(cx, y, cz);
        final int l0 = data.getLevel(cx, y, cz);

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
            final int m0 = Util.getMetaFromLevel(l0);
            if (m0 != m1)
            {
                if (b0 != f1)
                {
                    //if (b0.isAir(data.w, x, y, z) || b0 instanceof BlockFiniteFluid) // Second place
                    {
                        RealisticFluids.validateModWater(data.w, x, y, z, f1);
                        RealisticFluids.setBlock(data.w, x, y, z, f1, m1, 2, true); // that clobbers mod stuff
                    }
                    return l0;
                }
                RealisticFluids.validateModWater(data.w, x, y, z, f1);
                RealisticFluids.setBlock(data.w, x, y, z, null, m1, -2, true);
                return l0;
            }
        }
        // If we are here, then we are putting finite fluid into a block that does not have the same
        // liquid. This could be air, or the other type (water overriding lava, etc), or it could be
        // trying to place into a mod liquid (Streams, super-hot Lava, etc).
        // Require that the destination can only be air or "normal" liquids.
        // if (b0.isAir(data.w, x, y, z) || b0 instanceof BlockFiniteFluid)
        {
            RealisticFluids.validateModWater(data.w, x, y, z, f1);
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
