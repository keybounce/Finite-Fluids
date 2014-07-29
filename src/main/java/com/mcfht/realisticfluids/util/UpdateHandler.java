package com.mcfht.realisticfluids.util;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.mcfht.realisticfluids.RealisticFluids;
import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;
import com.mcfht.realisticfluids.util.ChunkDataMap.ChunkCache;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;

/**
 *  How the scheduler works!
 *  <p>
 * 	<p><b>1. </b> Select all players, determine a global number of allowed updates to perform
 * 	depending on the number of players, etcetera.
 * 	<p><b>2. </b>Iterate over the chunks around the players, starting with the closer chunks and then trying
 * 	to perform a few updates in some random distant chunks.

 * <p>Within each chunk, we have a boolean map of flags, and a preset counter telling us roughly
 * how many updates we need to process in this chunk. From here, we just iterate over the boolean
 * map and update the blocks as we come to them, unflagging them as we go. Additionally, we spread
 * a few random ticks out to some blocks, to use for our own purposes.
 * 
 * <p> Note that the updater flags updates in 16x16x16 sections. This allows us to reduce total iterations
 * significantly in the majority of situations.
 * 
 * <p><b>ADVANTAGES:</b>
 * <p>By using a simple array to store flags, we can greatly increase the speed of flagging updates
 * (as opposed to maps). By performing all updates at once, we also ditch the overhead cost of
 * having updates "out of alignment" (aka, block A updates in tick 1, scheduling neighbor blocks,
 * which get scheduled again in the next tick by block B). By using thread safe implementations, 
 * we can very easily schedule and update chunks whenever we want, at any time, from any thread.
 * 
 * <p><b>HOW THE TASK SCHEDULING WORKS</b>
 * 
 * <p>World.setBlock is in practice, not thread safe, since it uses arrays which can be poor at notifying other
 * accessing threads of changes. For this reason, we use a simple queue to collate the block setting operations,
 * then perform them all at once from the updates from same place, meaning the data we use is the data in the world.
 * 
 * <p>Equalization uses a schedule so that we can set tasks to be performed at any time from multiple threads. May be 
 * a redundant implementation if I end up dedicating a thread to equalization, but w/e.
 */
public class UpdateHandler {
	
	public static final UpdateHandler INSTANCE = new  UpdateHandler();
	
	/** Hidden internal tick counter, prevents accidentally changing it during updating etc lol*/
	private static int _tickCounter = 0;
	
	/** Returns the current tick-time of this instance*/
	public static int tickCounter()		{ return _tickCounter;	}
	public static void incrTick()		{ _tickCounter += 1;	}
	

	
	
	/////////////////////////////////// BLOCK SETTING ///////////////////////////////////////////////////
	/* Vanilla world.setBlock calls are not necessarily thread reliable. This implementation allows us to schedule updates
	 * for the server thread, AND allows us to set blocks reliably in the EBS itself. Allows us to avoid locking.
	 * 
	 * ONLY FLAG IMMEDIACY FROM AN ENVIRONMENT WHERE WE ARE DEFINITELY THREAD SAFE!!!
	 */

	
	public static void setBlock(World w, Chunk c, ExtendedBlockStorage ebs, int x, int y, int z, Block b, int m, int flag)
	{
		int _flag = flag < 0 ? -flag : flag;
		if (_flag >= 2)
		{
			w.markBlockForUpdate(x, y, z);
		}
		if (_flag >= 3)
		{
			w.notifyBlockChange(x, y, z, b);
		}
		
		x &= 0xF; y &= 0xF; z &= 0xF;
		
		ebs.setExtBlockMetadata(x, y, z, m);
		if (b != null) ebs.func_150818_a(x, y, z, b);
		
		if (flag > 0)
		{
			c.updateSkylightColumns[x + (z << 4)] = true;
		}
	}
	
	/**
	 * Guaranteed thread safe block setting method, directly manipulates EBS, skips some redundant world.setBlock calls,
	 * allows skipping of light recalculations, and allows unimportant block updates to deferred to the server tick at a later time.
	 * 
	 * <p>Only supports flags 2 and 3, however negative versions will skip lighting recalculations.
	 * 
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 * @param f
	 * @param m
	 * @param f : 0 = no update or light, ±2 = render update, ±3 = block update, <= prevents light
	 * @param immediate : whether we should do this now, or defer it to the server tick event.
	 */
	public static void setBlock(World w, int x, int y, int z, Block b, int m, int f, boolean immediate)
	{
		
		Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
		if (c == null || !c.isChunkLoaded)
		{
			//c = world.getChunkProvider().provideChunk(x >> 4, z >> 4);
			c = w.getChunkProvider().loadChunk(x >> 4, z >> 4);
		}
		ExtendedBlockStorage ebs = c.getBlockStorageArray()[y >> 4];
		
		if (!immediate)
		{
			BlockTask.blockTasks.add(new BlockTask(w, c, ebs, x, y, z, b, m, f));
			return;
		}
		
		setBlock(w, c, ebs, x, y, z, b, m, f);
	}
	
	public static void setBlockMetadata(World world, int x, int y, int z, int meta, int flag)
	{
		setBlock(world, x, y, z, null, meta, flag, true);
	}
	
	public static void setBlock(World world, int x, int y, int z, Block block, int meta, int flag)
	{
		setBlock(world, x, y, z, block, meta, flag, true);
	}
	/** Simple queue implementation to prevent duplicate entries. TODO Benchmark
	 * @author FHT
	 * @param <E>
	 */
	static class QueueSet<E> extends ConcurrentLinkedQueue<E>
	{
		public boolean add(E e)
		{
			if (super.contains(e)) return false;
			return super.add(e);
		}
	}
	
	
	/**
	 * Block Task Object for multiple thread access stuffs
	 * @author FHT
	 *
	 */
	private static class BlockTask
	{
		public static QueueSet<BlockTask> blockTasks = new QueueSet<BlockTask>();
		
		World w; Chunk c; ExtendedBlockStorage ebs; int x; int y; int z; Block b; int m; int f;
		
		/** Block Task object to be constructed for thread safe block setting! */
		BlockTask(World world, Chunk c, ExtendedBlockStorage ebs, int x, int y, int z, Block b, int m,  int flag)
		{this.w = world; this.c = c; this.ebs = ebs; this.x = x; this.y = y; this.z = z; this.b = b; this.m = m; this.f = flag;}
		
		/** Perform this block task. Thread Safe.*/
		public boolean set()
		{
			setBlock(w, c, ebs, x, y, z, b, m, f);
			return true;
		}
		
		public boolean equals(BlockTask b)
		{
			if (b.x == x && b.y == y && b.z == z)
				return true;
			return false;
		}
	}

	
	/**
	 * Clean up after ourselves when a chunk is unloaded.
	 * @param event
	 */
	@SubscribeEvent
	public void chunkUnload(ChunkEvent.Unload event)
	{
		if (ChunkDataMap.worldCache.get(event.world) != null)
		{
			ChunkDataMap c = ChunkDataMap.worldCache.get(event.world).chunks.remove(event.getChunk());
			if (c != null)
			{
				//Save the GC some trouble
				c.fluidArray 	= null;
				c.updateFlags 	= null;
				c.updateCounter = null;
			}
		}
	}
	
	/**
	 * Clean up after ourselves when a world is unloaded
	 * @param event
	 */
	@SubscribeEvent
	public void worldUnload(WorldEvent.Unload event)
	{
		if (ChunkDataMap.worldCache.get(event.world) != null)
		{
			for (ChunkDataMap c : ChunkDataMap.worldCache.get(event.world).chunks.values())
			{
				c.fluidArray 	= null;
				c.updateFlags 	= null;
				c.updateCounter = null;
				ChunkDataMap.worldCache.get(event.world).chunks.values().remove(c);
			}
		}
	}
	
	@SubscribeEvent
	public void serverTick(ServerTickEvent event)
	{
		if (event.phase != Phase.END) return;
		
		
		long time = System.currentTimeMillis();
		//incrTick();
		_tickCounter += 1;
		System.out.println(tickCounter());
		//Schedule chunks, fires at n = 4
		if (tickCounter() % RealisticFluids.GLOBAL_RATE == (RealisticFluids.GLOBAL_RATE - 1))
		{
			System.out.println("Schedulingerizing");
			for (World world : MinecraftServer.getServer().worldServers)
			{
				if (world.playerEntities == null || world.playerEntities.size() == 0) continue;
				for (Object p : world.playerEntities)
				{
					EntityPlayer player = (EntityPlayer) p;
					ChunkCache map 		= ChunkDataMap.worldCache.get(world);
					if (map == null) continue;
					
					//iterate over all flagged chunks
					for (Chunk c : map.chunks.keySet())
					{	
						if (!c.isChunkLoaded) continue;//Just to be safe;
					
						int x = c.xPosition - (((int)player.posX) >> 4); 
						int z = c.zPosition - (((int)player.posZ) >> 4); 
						int y;
						
						int dist = x * x + z * z; //Distance for distance testing
						
						if (dist <= RealisticFluids.UPDATE_RANGE) map.priority.add(c);
						else if (dist <= RealisticFluids.UPDATE_RANGE_FAR) map.distant.add(c);
						
						
						}
					//Do some block setting

					}
				}	
			return;
		}
		
		else if ((tickCounter() % RealisticFluids.GLOBAL_RATE)  == 0)
		{
			int tickQuota;
			System.out.println("Doingerizing");
			//DO EQUALIZATION
			if (!Equalizer.WORKER.isAlive())
			{
				Equalizer.WORKER.run();
			}

			//Leave a minimum number of ticks per world per player (should cover a couple of chunks)
			tickQuota = RealisticFluids.MAX_UPDATES/Math.max(1, MinecraftServer.getServer().getCurrentPlayerCount());
			for (World world : MinecraftServer.getServer().worldServers)
			{
				//There are no players, so there is no point
				if (world.playerEntities == null || world.playerEntities.size() == 0) continue;

				ChunkCache map = ChunkDataMap.worldCache.get(world);
				if (map == null) continue;
				if (map.priority.size() <= 0) continue;
				
				int ticksLeft = tickQuota + RealisticFluids.FORCE_UPDATES; //Give ourselves a tick quota
				
				//Thread no. 1
				//Start with priority chunks!
				System.out.println("HP");
				for (Chunk c : map.priority)
				{
					ChunkDataMap t = map.chunks.get(c);
					if (t == null || !c.isChunkLoaded){
					System.out.println("Map was nulllllll"); continue;
					}
					ticksLeft -= doTask(world, c, t, true);
				}
				map.priority.clear();
				
				//Exit if we are under too much strain
				if (System.currentTimeMillis() - time > 40) return;
				
				//Thread no. 2
				
				//Now we are going to update some pseudo-random distant chunks
				//Give ourselves a certain number of random ticks
				ticksLeft =  RealisticFluids.FORCE_UPDATES + Math.max(0, ticksLeft);
				System.out.println("MP");
				while (map.distant.size() > 0 && (ticksLeft > 0 || System.currentTimeMillis() - time < 10))
				{
					//Select a random distant chunk
					int i = world.rand.nextInt(map.distant.size());
					Chunk c = (Chunk) map.distant.toArray()[i]; //can we just do 0?
					map.distant.remove(c);
					
					ChunkDataMap t = map.chunks.get(c);
					if (t == null || !c.isChunkLoaded) continue;
					
					ticksLeft -= doTask(world, c, t, false);
				}
			}
		}
		
		//Set blocks for a little bit if we aren't under load?
		int toPerform =  BlockTask.blockTasks.size()/16;
			toPerform = toPerform < 32 ? 32 : toPerform;
			
		boolean flagout = false;
		
		//Prevent lagging the system by allocating a fixed amount of time
		while (System.currentTimeMillis() - time < 10 && BlockTask.blockTasks.size() > 0)
		{
			for (int i = 0; i < Math.min(toPerform, BlockTask.blockTasks.size()); i++)
			{
				BlockTask.blockTasks.remove().set();
			}
		}
		
	}
	
	/**
	 * Performs updates within a chunk (or more precisely, a ChunkCache object
	 * @param w
	 * @param c
	 * @param d
	 * @param isHighPriority Do heavy equalization?
	 * @return
	 */
	public int doTask(World w, Chunk c, ChunkDataMap d, boolean isHighPriority)
	{
		int interval = (tickCounter() % RealisticFluids.GLOBAL_RATE);
		int cost = 0;
		int x,y,z;
		//Iterate over each 
		for (int i = 0; i < 16; i++)
		{
			
			//Perform our own update tick on some random blocks
			///////////////////////////////////////////////////////////////////////////////
			
			/* Do more equalization in distant chunks.
			 * This is for 2 reasons. First and foremost, distant chunks get updated less, so we
			 * need to dry and suck up as much water from them as we can.
			 * 
			 * Secondly, equalization can look a little bit fugly, so we should run it less on closer chunks.
			 * 
			 */
			int rem = isHighPriority ? 16 : 4; 
			for (int j = 0; j < 3; j++)
			{
				x = w.rand.nextInt(16);
				y = w.rand.nextInt(16) + (i << 4);
				z = w.rand.nextInt(16);
				
				Block b = c.getBlock(x, y, z);
				
				//Only bother updating it if it is a fluid
				if (b instanceof BlockFiniteFluid)
				{
					System.out.println("Random update!");
					//INSERT STUFF FOR EVAPORATION AND RAIN HERE?
					
					//Now equalize if we have quota
					if (rem-- <= 0) continue;
					
					//Attempt to reach a surface block (increases equalization attempts in larger bodies of water
					//Where many ticks would otherwise be lost to "deep water blocks"
					//Try to reach a top block
					for (int count = 0; w.getBlock(x, y+1, z) instanceof BlockFiniteFluid && count < 8; count++)
					{
						y += 1;
					}
					
					int level = d.getWaterLevel(w, c, x, y, z);
					
					if (level < BlockFiniteFluid.maxFluid - (BlockFiniteFluid.maxFluid >> 3)) 
					{
						Equalizer.addTask
						(w,	( c.xPosition << 4) + x, y,	( c.zPosition << 4) + z,
								(BlockFiniteFluid) b, 32 + (isHighPriority ? 32 : 0));
					}
				}
			
			}
			////////////////////////////////////////////////////////////////////////////////////

			//No updates, exit
			if (d.updateCounter[i] == 0)
				continue;

			//System.out.println(i + " =======> Performing : " + t.updateCounter[i] + " Block Updates");
			//Use some of our quota, but maintain some minimum number of global updates
			cost += Math.max(16, d.updateCounter[i] >> 6);
			//Reset the counter
			d.updateCounter[i] = 0;
			
			/////////////////////////////////////////////////////////////////////////////////////
			for (int j = 0; j < 4096; j++)
			{
				if (d.updateFlags[i][j])
				{
					//Un-flag this block
					d.updateFlags[i][j] = false;
			
					//Rebuild the coordinates from the array position
					x = c.xPosition << 4;
					z = c.zPosition << 4;
					y = i << 4;
														
					x +=  j 		& 0xF;
					z += (j >> 4)	& 0xF;
					y += (j >> 8)	& 0xF;

					//System.out.println("Have Update At: " + j + (i << 12) + " - " + t.updateFlags.getCluster((j + (i << 4)) >> 5));
					
					Block b = w.getBlock(x, y, z);
					if (b instanceof BlockFiniteFluid)
					{
						//System.out.println("Ticking...");
						//TODO thread pool?
						((BlockFiniteFluid) b).doUpdate(w, x, y, z, w.rand, interval);
					}
				}
			}
			////////////////////////////////////////////////////////////////////////////////////////////
		}
	
		//c.needsSaving(true);
		//world.markBlockForUpdate((c.xPosition <<  4) + 1, 1, (c.zPosition <<  4) + 1);
		if (isHighPriority && w.rand.nextBoolean()) c.sendUpdates = true;
		return cost;
	}
	
	
	
	
}
