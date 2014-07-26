package com.mcfht.finitewater.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class ChunkCache {

	public static ConcurrentHashMap<World, ChunkMap> worldCache = new  ConcurrentHashMap<World, ChunkMap>(16);
	
	public int[] waterArray = new int[65536];
	
	//Should we do update tasks like this, or with a map?
	//Eff it, just use a boolean flag
	/** A map of update flags, divided into EBS arrays*/
	public boolean[][] updateFlags = new boolean[16][4096];
	/** A simple counter telling us roughly how many updates are registered for each EBS section*/
	public short[] updateCounter = new short[16];
	
	
	public World world;
	public Chunk chunk;
	
	/**
	 * Initialize a new Chunk Data object for the chunk in the given world
	 * @param world
	 * @param chunk
	 */
	public ChunkCache(World world, Chunk chunk)
	{
		this.world = world;
		this.chunk = chunk;
		for (int i = 0; i < 16; i++){
			updateCounter[i] = 0;
		}
		for (int i = 0; i < 65536; i++)
		{
			waterArray[i] = 0;
		}
	}
	
	/**
	 * Marks block for update in world coordinates. Assumes block is fluid!
	 * @param world
	 * @param x
	 * @param y
	 * @param z
	 */
	public static void markBlockForUpdate(World world, int x, int y, int z)
	{
		//First ensure the target chunk is loaded and mapped
		//TODO shift this to chunk loading?
		Chunk c = world.getChunkFromBlockCoords(x, z);
		if (!c.isChunkLoaded)
		{
			c = world.getChunkProvider().provideChunk(x >> 4, z >> 4); //Force chunk to load
		}
		
		if (worldCache.get(world) == null)
		{
			worldCache.put(world, new ChunkMap());
		}
		
		ChunkCache cc = worldCache.get(world).waterCache.get(c);
		
		if (cc == null)
		{
			cc = new ChunkCache(world,  c);
			worldCache.get(world).waterCache.put(c, cc);
		}
		
		//Register the update
		y = y < 0 ? 0 : y;
		boolean p =  cc.updateFlags[y >> 4][(x & 0xF) + ((y & 0xF) << 4) + ((z & 0xF) << 8)];
		
		if (p == false)
			++cc.updateCounter[y >> 4];
		
		cc.updateFlags[y >> 4][(x & 0xF) + ((y & 0xF) << 4) + ((z & 0xF) << 8)] = true;
		//++cc.updateCounter[y >> 4];
	}
	
	/**
	 * Marks block for update in EBS coordinates
	 * @param x
	 * @param y
	 * @param z
	 */
	public void markBlockForUpdateEBS(int x, int y, int z)
	{
		if (!updateFlags[y][(x) + (y << 4) + (z << 8)])
			++updateCounter[y];

		updateFlags[y][(x) + (y << 4) + (z << 8)] = true;
	}
	
	/**
	 * Sets the water level in world coordinates
	 * @param world
	 * @param x
	 * @param y
	 * @param z
	 * @param level
	 */
	public static void setWaterLevel(World world, int x, int y, int z, int level)
	{
		setWaterLevel(world, world.getChunkFromBlockCoords(x,z), x & 0xF, y, z & 0xF, level);
	}
	
	/**
	 * Sets the water level in chunk coordinates.
	 * @param world
	 * @param chunk
	 * @param cx
	 * @param cy
	 * @param cz
	 * @param level
	 */
	public static void setWaterLevel(World world, Chunk chunk, int cx, int cy, int cz, int level)
	{
		if (worldCache.get(world)==null)
		{
			worldCache.put(world, new ChunkMap());
		}
		
		if(worldCache.get(world).waterCache.get(chunk) == null)
		{
			worldCache.get(world).waterCache.put(chunk, new ChunkCache(world,chunk));
		}
			
		worldCache.get(world).waterCache.get(chunk).waterArray[cx + (cz << 4) + (cy << 8)] = level;

	}
	
	/**
	 * Gets the water level in world coordinates
	 * @param world
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public static int getWaterLevel(World world, int x, int y, int z)
	{
		return getWaterLevel(world, world.getChunkFromBlockCoords(x,z), x & 0xF, y, z & 0xF);
	}
	/**
	 * Gets the water level in chunk coordinates
	 * @param world
	 * @param chunk
	 * @param cx
	 * @param cy
	 * @param cz
	 * @return
	 */
	public static int getWaterLevel(World world, Chunk chunk, int cx, int cy, int cz)
	{
		if (worldCache.get(world)!=null)
		{
			if(worldCache.get(world).waterCache.get(chunk) != null){
				//System.out.println("GOT: " + worldCache.get(world).waterCache.get(chunk).waterArray[cx + (cz << 4) + (cy << 8)]);
				return worldCache.get(world).waterCache.get(chunk).waterArray[cx + (cz << 4) + (cy << 8)];
			}
		}
		return 0;
	}
	
	/**
	 * An object mapping used to map chunk data to each Chunk, and Chunks to each world.
	 * @author FHT
	 *
	 */
	static class ChunkMap
	{
		public ConcurrentHashMap<Chunk, ChunkCache> waterCache;
		
		public ChunkMap()
		{
			this.waterCache = new ConcurrentHashMap<Chunk, ChunkCache>(1024);
		}
	}

	
}
