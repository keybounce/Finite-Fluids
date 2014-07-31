package com.mcfht.realisticfluids.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;

import org.apache.commons.lang3.concurrent.ConcurrentUtils;

import scala.reflect.internal.util.Set;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Custom data structure object. Maps worlds to extended chunk data objects, ChunkCaches, which themselves
 * map chunks to their respective extended data (instantiated ChunkDataMaps).
 * @author FHT
 *
 */
public class ChunkDataMap {

	/** A map assigning Chunk Data to the corresponding World object */
	public static ConcurrentHashMap<World, ChunkCache> worldCache = new  ConcurrentHashMap<World, ChunkCache>(16);
	
	//INSTANTIATED
	/** Array of fluid levels */
	public short[] fluidArray = new short[65536];
	
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
	public ChunkDataMap(World w, Chunk c)
	{
		this.w = w;
		this.c = c;
		
		//Initialize
		for (int i = 0; i < 16; i++)
			updateCounter[i] = false;
		for (int i = 0; i < 65536; i++)
			fluidArray[i] = 0;
		
	}
	
	/**
	 * A cache which maps Chunk Data to each Chunk, and also contains 
	 * thread safe updating queues of near and distant chunks.
	 * @author FHT
	 *
	 */
	static class ChunkCache
	{
		/** A map linking chunks to their chunk data */
		public ConcurrentHashMap<Chunk, ChunkDataMap> chunks;
		
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
			this.chunks = new ConcurrentHashMap<Chunk, ChunkDataMap>(1024);
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
	 * Sets the water level in chunk coordinates. Thread Safe.
	 * 
	 * <p>FIXME Add variant that can be passed the data object directly (save getters & tests)
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
