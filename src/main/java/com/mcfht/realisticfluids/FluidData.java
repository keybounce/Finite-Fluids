package com.mcfht.realisticfluids;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;

import org.apache.commons.lang3.concurrent.ConcurrentUtils;

import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;






import scala.reflect.internal.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Custom data structure object. Maps worlds to extended chunk data objects, ChunkCaches, which themselves
 * map chunks to their respective extended data (instantiated ChunkDataMaps).
 * @author FHT
 *
 */
public class FluidData {

	/** A map assigning Chunk Data to the corresponding World object */
	public static ConcurrentHashMap<World, ChunkCache> worldCache = new  ConcurrentHashMap<World, ChunkCache>(16);

	/**
	 * A cache which maps Chunk Data to each Chunk, and also contains 
	 * thread safe updating queues of near and distant chunks.
	 * @author FHT
	 *
	 */
	static class ChunkCache
	{
		/** A map linking chunks to their chunk data */
		public ConcurrentHashMap<Chunk, ChunkData> chunks;
		/** Set of chunk updates to be performed with PRIORITY */
		public ConcurrentLinkedQueue<Chunk> priority = new ConcurrentLinkedQueue<Chunk>();
		/** Set of distant chunks to be updated if we have time */
		public ConcurrentLinkedQueue<Chunk> distant = new ConcurrentLinkedQueue<Chunk>();
		/**
		 * A cache which maps Chunk Data to each Chunk, and also contains 
		 * thread safe updating queues of near and distant chunks.
		 */
		public ChunkCache()
		{
			this.chunks = new ConcurrentHashMap<Chunk, ChunkData>(1024);
		}
	}

	public static class ChunkData
	{
		//INSTANTIATED
		/** Array of fluid levels */
		public short[][] fluidArray = new short[16][4096];
		
		/** A map of update flags, divided into EBS arrays */
		public boolean[][] updateFlags = new boolean[16][4096];
		
		public World w;
		public Chunk c;
		
		/** A simple counter telling us whether or not a given cube has updates */
		public boolean[] updateCounter = new boolean[16];
		
		
		/**
		 * Initialize a new Chunk Data object for the chunk in the given world
		 * @param w
		 * @param c
		 */
		public ChunkData(World w, Chunk c)
		{
			this.w = w;
			this.c = c;
			//Initialize
			for (int i = 0; i < 16; i++)
			{
				updateCounter[i] = false;
			    fluidArray[i] = null;
			    updateFlags[i] = null;
			}
		}
		
		/**
		 * Gets level in cx cy cz
		 * @param cx
		 * @param cy
		 * @param cz
		 * @return
		 */
		public int getLevel(int cx, int cy, int cz)
		{
			if (fluidArray[cy >> 4] == null)
				fluidArray[cy >> 4] = new short[4096];

			return fluidArray[cy>>4][cx + (cz << 4) + ((cy & 0xF) << 8)];
		}
		
		/**
		 * Gets level in cx cy cz
		 * @param cx
		 * @param cy
		 * @param cz
		 * @return
		 */
		public void setLevel(int cx, int cy, int cz, int l)
		{
			if (fluidArray[cy >> 4] == null)
				fluidArray[cy >> 4] = new short[4096];

			fluidArray[cy>>4][cx + (cz << 4) + ((cy & 0xF) << 8)] = (short) l;
		}
		
		/**
		 * Tries to put the specified amount of fluid into the cell,
		 * and returns the "overflow", along with the level of the now full block.
		 * @param cx
		 * @param cy
		 * @param cz
		 * @return
		 */
		public int[] addSetLevel(int cx, int cy, int cz, int l)
		{
			if (fluidArray[cy >> 4] == null)
				fluidArray[cy >> 4] = new short[4096];

			int i = fluidArray[cy>>4][cx + (cz << 4) + ((cy & 0xF) << 4)];
			
			fluidArray[cy>>4][cx + (cz << 4) + ((cy & 0xF) << 4)] = (short) (i + l > RealisticFluids.MAX_FLUID ? RealisticFluids.MAX_FLUID : i + l);
			return new int[]{i + l, Math.max(0, i + l - RealisticFluids.MAX_FLUID)};
		}
		
		/**
		 * Marks update in cx, cy, cz
		 * @param cx
		 * @param cy
		 * @param cz
		 */
		public void markUpdate(int cx, int cy, int cz)
		{
			if (updateFlags[cy >> 4] == null){
				updateFlags[cy >> 4] = new boolean[4096];
			}
			updateCounter[cy >> 4] = true;
			updateFlags[cy >> 4][cx + (cz << 4) + ((cy & 0xF) << 8)] = true;
			//System.out.println("***********DONE************");
		}
	}
	
	/**
	 * Flags neighboring cells to be updated. ENSURES that they are fluid first!
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 */
	public static void markNeighbors(ChunkData data, int x, int y, int z)
	{
		if (y < 255)
			data.markUpdate(x & 0xF, y+1, z & 0xF);
		if (y > 0)
			data.markUpdate(x & 0xF, y-1, z & 0xF);
		
		for (int i = 0; i < 4; i++)
		{
			int x1 = (x + Util.cardinalX(i)), z1 = (x + Util.cardinalZ(i));
			data = FluidData.forceCurrentChunkData(data, x, z);
			data.markUpdate(x1 & 0xF, y, z1 & 0xF);
		}

	}
	
	/**
	 * Flags neighboring cells to be updated. ENSURES that they are fluid first!
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 */
	public static void markNeighborsDiagonal(ChunkData data, int x, int y, int z)
	{
		if (y < 255)
			data.markUpdate(x & 0xF, y+1, z & 0xF);
		if (y > 0)
			data.markUpdate(x & 0xF, y-1, z & 0xF);
		
		for (int i = 0; i < 8; i++)
		{
			int x1 = (x + Util.intDirX(i)), z1 = (x + Util.intDirZ(i));
			data = FluidData.forceCurrentChunkData(data, x, z);
			data.markUpdate(x1 & 0xF, y, z1 & 0xF);
		}
	}
	
	/**
	 * Sets the water level in world coordinates. Thread Safe.
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 * @param level
	 */
	public static void setWaterLevel(World w, int x, int y, int z, int l)
	{
		setWaterLevel(w, w.getChunkFromBlockCoords(x,z), x & 0xF, y, z & 0xF, l);
	}
	
	/**
	 * Returns chunk data object. Assumes chunk is loaded!!!
	 * @param w
	 * @param c
	 * @return
	 */
	public static ChunkData getChunkData(Chunk c)
	{
		World w = c.worldObj;
		ChunkCache cache = worldCache.get(w);
		ChunkData data;
		if (cache == null)
		{
			System.err.println("There was no registered world cache! Initializing a new one...");
			cache = new ChunkCache();
			data = new ChunkData(w, c);
			cache.chunks.put(c, data);
			worldCache.put(w, new ChunkCache());
		}
		else
		{
			data = cache.chunks.get(c);
			if(data == null)
			{
				data = new ChunkData(w, c);
				cache.chunks.put(c, data);
			}
		}
		return data;
	}
	
	/**
	 * Ensures that the current data object is current, and returns the correct object if it is not.
	 * @param data0
	 * @param x1
	 * @param z1
	 * @return Null if unloaded chunk.
	 */
	public static ChunkData testCurrentChunkData(ChunkData data0, int x1, int z1)
	{
		Chunk cOut = data0.w.getChunkFromChunkCoords(x1 >> 4, z1 >> 4);
		if (!cOut.isChunkLoaded) return null;
		if (cOut.xPosition != data0.c.xPosition || cOut.zPosition != data0.c.zPosition)
		{
			return getChunkData(cOut);
		}
		return data0;
	}
	
	/**
	 * Ensures that the current data object is current, and returns the correct object if it is not.
	 * 
	 * <p><b>FORCES UNLOADED CHUNKS TO LOAD
	 * @param data0
	 * @param x1
	 * @param z1
	 * @return May return null in some situations!
	 */
	public static ChunkData forceCurrentChunkData(ChunkData data0, int x1, int z1)
	{
		Chunk cOut = data0.w.getChunkFromChunkCoords(x1 >> 4, z1 >> 4);
		if (!cOut.isChunkLoaded);
		{
			//Or should this be load chunk?
			cOut = data0.w.getChunkProvider().loadChunk(x1 >> 4, z1 >> 4);
		}
		
		if (cOut.xPosition != data0.c.xPosition || cOut.zPosition != data0.c.zPosition)
		{
			return getChunkData(cOut);
		}
		return data0;
	}
	
	/**
	 * Returns the fluid level of a cell at the given coordinates in the given data array.
	 * Targets specific fluid!!!
	 * @param w
	 * @param f0
	 * @param cx
	 * @param cy
	 * @param cz
	 * @return
	 */
	public static int getLevel(ChunkData data, BlockFiniteFluid f0, int cx, int cy, int cz)
	{
		Block b0 = data.c.getBlock(cx,cy,cz);
		int a = data.getLevel(cx,cy,cz);
		if (a == 0 && Util.isSameFluid(f0, b0))
		{
			a = data.c.getBlockMetadata(cx,cy,cz);
			if (a >= 7) return f0.viscosity;
			return (8 - a) * (RealisticFluids.MAX_FLUID >> 3);
		}
		return a;
	}
		
	public static Block convertFlowingStill(Block f0, int level)
	{
		if (f0.getMaterial() == Material.water)
		{
			if (level > (RealisticFluids.MAX_FLUID - (RealisticFluids.MAX_FLUID >> 3)))
			{
				return Blocks.water;
			}else return Blocks.flowing_water;
		}else
		{
			if (level > (RealisticFluids.MAX_FLUID - (RealisticFluids.MAX_FLUID >> 3)))
			{
				return Blocks.lava;
			}else return Blocks.flowing_lava;
		}
	}
	
	public static int setLevelWorld(ChunkData data, BlockFiniteFluid f0, int x, int y, int z, int l0, boolean updateNeighbors)
	{
		return setLevel(data, f0, x & 0xF, z & 0xF, x, y, z, l0, updateNeighbors);
	}

	public static int setLevel(ChunkData data, Block f1, int cx, int cz, int x, int y, int z, int l1, boolean updateNeighbors)
	{
		//Nte that the flow is decided, we do not care what the target is unless it is unmarked fluid
		
		//If level is less than 0, empty the block
		if (l1 <= 0) //We are emptying the block
		{
			//System.out.println("Set a block to air!");
			data.setLevel(cx, y, cz, 0);
			RealisticFluids.setBlock(data.w, x, y, z, Blocks.air, 0, 2);
			if (updateNeighbors) markNeighbors(data, x, y, z);
			return 0;
		}
		
		if (l1 > RealisticFluids.MAX_FLUID) l1 = RealisticFluids.MAX_FLUID;
		
		f1 = convertFlowingStill(f1, l1);
		Block b0 = data.c.getBlock(cx, y, cz);
		int l0 = data.getLevel(cx, y, cz);
		
		//SLEDGEHAMMER
		if (Math.abs(l0 - l1) <= 4)
		{
			//System.out.println("Spam blocks are a spamming...!");
			if (l0 <= 4 || l1 <= 4)
			{
				data.setLevel(cx, y, cz, 0);
				RealisticFluids.setBlock(data.w, x, y, z, Blocks.air, 0, updateNeighbors ? 3 : 2);
			}
			//updateNeighbors = false;
			return l1; //MAXXOR HAXXOR!
		}
		
		int m1 = Util.getMetaFromLevel(l1);
		data.markUpdate(cx, y, cz);
		if (updateNeighbors) markNeighbors(data, x, y, z);
		
		data.setLevel(cx, y, cz, l1);
		
		if (Util.isSameFluid(f1, b0))
		{
			int m0 = Util.getMetaFromLevel(l0);
			if (m0 != m1)
			{
				if (b0 != f1)
				{
					RealisticFluids.setBlock(data.w, x, y, z, f1, m1, 2, true);
					return l0;
				}
				RealisticFluids.setBlock(data.w, x, y, z, null, m1, -2, true);
				return l0;
			}
		}
		RealisticFluids.setBlock(data.w, x, y, z, f1, m1, 2);
		return l0;
	}
	
	/**
	 * Sets the water level in chunk coordinates. Thread Safe.
	 * 
	 * @param w
	 * @param c
	 * @param cx
	 * @param cy
	 * @param cz
	 * @param l
	 */
	public static void setWaterLevel(World w, Chunk c, int cx, int cy, int cz, int l)
	{
		getChunkData(c).setLevel(cx, cy, cz, l);
	}
	
	/**
	 * Gets the water level in world coordinates. Thread Safe. Returns 0 for unloaded chunk.
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public static int getWaterLevel(World w, int x, int y, int z)
	{
		return getWaterLevel(w, w.getChunkFromBlockCoords(x,z), x & 0xF, y, z & 0xF);
	}

	
	/**
	 * Gets the water level in chunk coordinates. Thread safe. Returns 0 for unloaded chunk.
	 * @param w
	 * @param c
	 * @param cx
	 * @param cy
	 * @param cz
	 * @return
	 */
	public static int getWaterLevel(World w, Chunk c, int cx, int cy, int cz)
	{
		ChunkCache cache = worldCache.get(w);
		if (cache!=null)
		{
			if (cache.chunks.get(c) != null){
				return cache.chunks.get(c).getLevel(cx, cy, cz);
			}
		}
		return 0;
	}


}
