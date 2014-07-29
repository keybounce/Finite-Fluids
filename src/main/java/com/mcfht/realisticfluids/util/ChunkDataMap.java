package com.mcfht.realisticfluids.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;

import scala.reflect.internal.util.Set;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class ChunkDataMap {

	/** A map assigning Chunk Data to the corresponding World object  (<b>THREAD SAFE</b>)*/
	public static ConcurrentHashMap<World, ChunkCache> worldCache = new  ConcurrentHashMap<World, ChunkCache>(16);
	
	/** Thread-Safe array of fluid levels */
	public short[] fluidArray = new short[65536];
	
	/** A map of update flags, divided into EBS arrays */
	public boolean[][] updateFlags = new boolean[16][4096];
	
	//public boolean[][] updateFlags = new boolean[16][4096];
	
	/** A simple counter telling us roughly how many updates are registered for each EBS section.
	 *  This structure does not need rely on absolute precision, and hence asych and Concurrent data
	 *  loss is not really that bad.
	 * */
	public short[] updateCounter = new short[16];
	
	
	public World w;
	public Chunk c;
	
	/**
	 * Initialize a new Chunk Data object for the chunk in the given world
	 * @param w
	 * @param c
	 */
	public ChunkDataMap(World w, Chunk c)
	{
		this.w = w;
		this.c = c;
		
		//Initialize
		
		for (int i = 0; i < 16; i++){
			updateCounter[i] = 0;
		}
		
		for (int i = 0; i < 65536; i++)
		{
			fluidArray[i] = 0;
		}
	}
	
	/**
	 * A cache which maps Chunk Data to each Chunk, and also contains 
	 * thread safe updating queues of near and distant chunks.
	 * @author FHT
	 *
	 */
	static class ChunkCache
	{
		/** A map linking chunks to their chunk data (<b>THREAD SAFE</b>)*/
		public ConcurrentHashMap<Chunk, ChunkDataMap> chunks;
		
		/** Set of chunk updates to be performed with PRIORITY */
		public HashSet<Chunk> priority = new HashSet<Chunk>();
		
		/** Set of distant chunks to be updated if we have time */
		public HashSet<Chunk> distant = new HashSet<Chunk>();
		
		/**
		 * A cache which maps Chunk Data to each Chunk, and also contains 
		 * thread safe updating queues of near and distant chunks.
		 */
		public ChunkCache()
		{
			this.chunks = new ConcurrentHashMap<Chunk, ChunkDataMap>(1024);
		}
	}

	
	/**
	 * Marks block for update in world coordinates. Assumes block is fluid! Thread Safe.
	 * @param world
	 * @param x
	 * @param y
	 * @param z
	 */
	public static void markBlockForUpdate(World world, int x, int y, int z)
	{
		//First ensure the target chunk is loaded and mapped
		//TODO shift this to chunk loading?
		Chunk c = world.getChunkFromChunkCoords(x >> 4, z >> 4);
		if (!c.isChunkLoaded)
		{
			c = world.getChunkProvider().provideChunk(x >> 4, z >> 4); //Force chunk to load
		}
		
		if (worldCache.get(world) == null)
		{
			worldCache.put(world, new ChunkCache());
		}
		
		ChunkDataMap cc = worldCache.get(world).chunks.get(c);
		
		if (cc == null)
		{
			cc = new ChunkDataMap(world,  c);
			worldCache.get(world).chunks.put(c, cc);
		}
		
		//Register the update
		
		int xx = x & 0xF;
		int yy = y & 0xF;
		int zz = z & 0xF;
		int i = xx + (zz << 4) + (yy << 8);
		System.out.println("Scheduled update for " + i + " - " + x + ", " + y + ", " + z + " => " + (y >> 4));
				
		
		if (!cc.updateFlags[y >> 4][i])
			++cc.updateCounter[y >> 4];
		
		cc.updateFlags[y >> 4][i] = true;
		//++cc.updateCounter[y >> 4];
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
	 * Sets the water level in chunk coordinates. Thread Safe.
	 * @param w
	 * @param c
	 * @param cx
	 * @param cy
	 * @param cz
	 * @param l
	 */
	public static void setWaterLevel(World w, Chunk c, int cx, int cy, int cz, int l)
	{
		if (worldCache.get(w)==null)
		{
			worldCache.put(w, new ChunkCache());
		}
		
		if(worldCache.get(w).chunks.get(c) == null)
		{
			worldCache.get(w).chunks.put(c, new ChunkDataMap(w,c));
		}
		worldCache.get(w).chunks.get(c).fluidArray[cx + (cz << 4) + (cy << 8)] = (short) l;
		//worldCache.get(world).chunks.get(chunk).fluidArray[cx + (cz << 4) + (cy << 8)] = (short) level;
	}
	
	/**
	 * Gets the water level in world coordinates. Thread Safe.
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
	 * Gets the water level in chunk coordinates. Thread safe.
	 * @param w
	 * @param c
	 * @param cx
	 * @param cy
	 * @param cz
	 * @return
	 */
	public static int getWaterLevel(World w, Chunk c, int cx, int cy, int cz)
	{
		if (worldCache.get(w)!=null)
		{
			if(worldCache.get(w).chunks.get(c) != null){
				//System.out.println("GOT: " + worldCache.get(world).waterCache.get(chunk).waterArray[cx + (cz << 4) + (cy << 8)]);
				return worldCache.get(w).chunks.get(c).fluidArray[cx + (cz << 4) + (cy << 8)];
			}
		}
		return 0;
	}
	
	
	
}
