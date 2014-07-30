package com.mcfht.realisticfluids.util;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import com.mcfht.realisticfluids.RealisticFluids;
import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;
import com.mcfht.realisticfluids.util.ChunkDataMap.ChunkCache;

/**
 * Handles virtually all fluid calculations. Manages worker threads.
 * @author FHT
 *
 */
public class FluidWorkers {

	public static WorkerPriority PWorker = new WorkerPriority();
	public static Thread PRIORITY = new Thread(PWorker);
	
	public static WorkerPriority TWorker = new WorkerPriority();
	public static Thread TRIVIAL = new Thread(TWorker);
	
	/**
	 * Thread object to perform high priority updates
	 * @author 4HT
	 *
	 */
	public static class WorkerPriority implements Runnable
	{

		public int myStartTime;
		public int quota;
		public World[] worlds;
		
		
		@Override
		public void run() {
			
			for (World world : worlds)
			{
				//There are no players, so there is no point
				if (world.playerEntities == null || world.playerEntities.size() == 0) continue;

				ChunkCache map = ChunkDataMap.worldCache.get(world);
				if (map == null) continue;
				if (map.priority.size() <= 0) continue;
				
				int ticksLeft = quota + RealisticFluids.FORCE_UPDATES; //Give ourselves a tick quota
				
				//Thread no. 1
				//Start with priority chunks!
				for (Chunk c : map.priority)
				{
					ChunkDataMap t = map.chunks.get(c);
					if (t == null || !c.isChunkLoaded){
						System.out.println("Map was null"); continue;
					}
					ticksLeft -= doTask(world, c, t, true, myStartTime);
				}
				map.priority.clear();
			}
		}
	}
	
	/**
	 * Thread object to perform Trivial (aka distant) updates
	 * @author 4HT
	 *
	 */
	public static class WorkerTrivial implements Runnable
	{
		public int myStartTime;
		public int quota;
		public World[] worlds;
		
		@Override
		public void run() 
		{
			for (World world : worlds)
			{
				//There are no players, so there is no point
				if (world.playerEntities == null || world.playerEntities.size() == 0) continue;

				ChunkCache map = ChunkDataMap.worldCache.get(world);
				if (map == null) continue;
				if (map.priority.size() <= 0) continue;
				
				int ticksLeft = RealisticFluids.FORCE_UPDATES; //Give ourselves a tick quota
					
				while (map.distant.size() > 0 && (ticksLeft > 0))
				{
					//Select a random distant chunk
					//int i = world.rand.nextInt(map.distant.size());
					Chunk c = (Chunk) map.distant.poll(); //can we just do 0?
					map.distant.remove(c);
					
					ChunkDataMap t = map.chunks.get(c);
					if (t == null || !c.isChunkLoaded) continue;
					
					ticksLeft -= doTask(world, c, t, false, myStartTime);
				}
			}
		}
	}

	/**
	 * Performs updates within a chunk (or more precisely, a ChunkCache object
	 * @param w
	 * @param c
	 * @param d
	 * @param flag Do heavy equalization?
	 * @return
	 */
	public static int doTask(World w, Chunk c, ChunkDataMap d, boolean isHighPriority, int startTime)
	{
		int interval = (startTime % RealisticFluids.GLOBAL_RATE);
		int cost = 0;
		int x,y,z;
		//Iterate over each 
		for (int i = 0; i < 16; i++)
		{
			//Don't bother with empty spaces
			if (c.getBlockStorageArray()[i] == null) continue;
			
			//First of all, let's perform our own random ticks (maor control)
			//do evaporation, seeping, refilling in rain, and so on.
			doRandomTicks(w, c, d, i, 3, isHighPriority);
			
			//No updates, exit
			if (d.updateCounter[i] == 0)
				continue;

			//cost += Math.max(16, t.updateCounter[i] >> 6); //Moved this to the end
			
			//Reset the counter
			d.updateCounter[i] = 0;
			//Count the updates which occur
			int counter = 0;
			/////////////////////////////////////////////////////////////////////////////////////
			for (int j = 0; j < 4096; j++)
			{
				if (d.updateFlags[i][j])
				{
					//Un-flag this block
					d.updateFlags[i][j] = false;
			
					//Rebuild the coordinates from the array position
					x = (c.xPosition << 4) + (j & 0xF);
					y = (i << 4) + ((j >> 8) & 0xF);
					z = (c.zPosition << 4) + ((j >> 4) & 0xF);

					Block b = w.getBlock(x, y, z);
					if (b instanceof BlockFiniteFluid)
					{
						//Tick the water block
						((BlockFiniteFluid) b).doUpdate(w, x, y, z, w.rand, interval);
					}
					counter++;
				}
			}
			cost += counter;
		}
		//c.needsSaving(true);
		//world.markBlockForUpdate((c.xPosition <<  4) + 1, 1, (c.zPosition <<  4) + 1);
		//if (!isHighPriority && w.rand.nextBoolean()) c.sendUpdates = true;
		//TODO: Make distant chunks re-render
		
		return Math.max(16, 1 + (cost >> 6));
	}

	/**
	 * Perform a specified number of random ticks in the 16x16x16 part of the world.
	 * @param w
	 * @param c
	 * @param d
	 * @param ebsY
	 * @param number
	 * @param isHighPriority
	 */
	public static void doRandomTicks(World w, Chunk c, ChunkDataMap d, int ebsY, int number, boolean isHighPriority)
	{

		int equalizationQuota = isHighPriority ? RealisticFluids.EQUALIZE_NEAR : RealisticFluids.EQUALIZE_FAR; 
		for (int i = 0; i < number; i++){
			
			int x = w.rand.nextInt(16);
			int y = w.rand.nextInt(16) + (ebsY << 4);
			int z = w.rand.nextInt(16);
			
			Block b = c.getBlock(x, y, z);
			
			//Do rainfall and evaporation
			//First, try to move up a few blocks (aka to the top of stuff)
			/*
			if (c.heightMap != null && c.heightMap[x + (z << 4)] < y + 16 && c.heightMap[x + (z << 4)] < 255)
			{
				Block b1 = c.getBlock(x, c.heightMap[x + (z << 4)] + 1, y);
				if (b == RealisticFluids.finiteWater || b == Blocks.air)
					doWaterFun(w, c, x, c.heightMap[x + (z << 4)] + 1, z, b);
			}*/
			
			//Only bother doing the next part with fluids
			if (b instanceof BlockFiniteFluid && Equalizer.tasks.size() < RealisticFluids.EQUALIZE_GLOBAL)
			{
				//Make sure we don't overstep the equalization quota, Trivial unless QUOTAS are set low
				if (equalizationQuota-- <= 0) continue;
				
				//Benefit large bodies of water by trying to find surface blocks
				for (int j = 0; y < 255 && j < 8 && w.getBlock(x, y+1, z) instanceof BlockFiniteFluid; j++) y++;
				
				if (w.getBlock(x, y+1, z) != Blocks.air) continue;
				
				int level = d.getWaterLevel(w, c, x, y, z);
				if (level < RealisticFluids.MAX_FLUID -  (RealisticFluids.MAX_FLUID >> 4))
				{
					Equalizer.addTask
					(w,	( c.xPosition << 4) + x, y,	( c.zPosition << 4) + z,
							(BlockFiniteFluid) b, isHighPriority ? RealisticFluids.EQUALIZE_NEAR_R : RealisticFluids.EQUALIZE_FAR_R);
				}
			}
		
		}
	}
	
	
	/**
	 * Handles all the fun things that can happen when playing with water, like evaporation and unicorns.
	 * @param w
	 * @param c
	 * @param x
	 * @param y
	 * @param z
	 * @param b
	 */
	public static void doWaterFun(World w, Chunk c, int x, int y, int z, Block b)
	{
		boolean isWater = b != Blocks.air;
		int xx = x + (c.xPosition<<4), zz = z + (c.zPosition<<4);
		BiomeGenBase biome = c.getBiomeGenForWorldCoords(x, z, w.getWorldChunkManager());
		if (y <= 64 && (w.isRaining() || w.isThundering())  && biome.rainfall > 0F && w.canBlockSeeTheSky(x, y, z))
		{
				System.out.println("Rain Increasing...");
				if (isWater)
				{
					BlockFiniteFluid f = ((BlockFiniteFluid)b);
					int l0 = f.getLevel(w, xx, y, zz);
					l0 += y < 64 ? RealisticFluids.MAX_FLUID/6 : RealisticFluids.MAX_FLUID;
					f.setLevel(w, xx, y, zz, l0, true);
					if (l0 > RealisticFluids.MAX_FLUID) f.setLevel(w, xx, y, zz, l0 - RealisticFluids.MAX_FLUID, true);
				}
				else
				{
					w.setBlock(xx, y, zz, RealisticFluids.finiteWater);
					BlockFiniteFluid f = (BlockFiniteFluid) c.getBlock(x, y, z);
					f.setLevel(w, xx, y, zz, f.viscosity, true);
				}
			
		}

		//Make water evaporate in deserts?
		if (b == RealisticFluids.finiteWater &&  biome.temperature > 1F)
		{
			System.out.println("Evaporating..");
			BlockFiniteFluid f = (BlockFiniteFluid) b;
			int l0 = f.getLevel(w, xx, y, zz);
			f.setLevel(w, xx, y, z, l0 - RealisticFluids.MAX_FLUID/2, true);
			//ChunkDataMap.setWaterLevel(w, c, x, y, z, ChunkDataMap.getWaterLevel(w, c, x, y, z) - (FiniteWater.MAX_FLUID/3));
		}
	}
	
}
