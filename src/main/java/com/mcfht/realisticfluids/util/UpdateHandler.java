package com.mcfht.realisticfluids.util;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.terraingen.InitNoiseGensEvent;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.mcfht.realisticfluids.RealisticFluids;
import com.mcfht.realisticfluids.asm.PatchBlockRegistry;
import com.mcfht.realisticfluids.util.ChunkDataMap.ChunkCache;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
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
	

	protected long lastTime = 0L;
	
	/////////////////////////////////// BLOCK SETTING ///////////////////////////////////////////////////
	/* Vanilla world.setBlock calls are not necessarily thread reliable. This implementation allows us to schedule updates
	 * for the server thread, AND allows us to set blocks reliably in the EBS itself. Allows us to avoid locking.
	 * 
	 * ONLY FLAG IMMEDIACY FROM AN ENVIRONMENT WHERE WE ARE DEFINITELY THREAD SAFE!!!
	 */

	/**
	 * Marks block for update in world coordinates. Assumes block is fluid! Thread Safe.
	 * 
	 * FIXME: Add version which gets passed data structures innately
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 */
	public static void markBlockForUpdate(World w, int x, int y, int z)
	{
		//First ensure the target chunk is loaded and mapped
		//TODO Map chunks during load, pass parameter as entry
		Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
		
		if (!c.isChunkLoaded)
			c = w.getChunkProvider().provideChunk(x >> 4, z >> 4); //Ensure we can mark the updates
		
		if (ChunkDataMap.worldCache.get(w) == null)
			ChunkDataMap.worldCache.put(w, new ChunkCache());
		
		ChunkDataMap cc = ChunkDataMap.worldCache.get(w).chunks.get(c);
		if (cc == null)
		{
			cc = new ChunkDataMap(w,  c);
			ChunkDataMap.worldCache.get(w).chunks.put(c, cc);
		}
		//Register the update
		int xx = x & 0xF;
		int yy = y & 0xF;
		int zz = z & 0xF;
		int i = xx + (zz << 4) + (yy << 8);
		//System.out.println("Scheduled update for " + i + " - " + x + ", " + y + ", " + z + " => " + (y >> 4));
				
		cc.updateCounter[y >> 4] = true;
		cc.updateFlags[y >> 4][i] = true;
		//++cc.updateCounter[y >> 4];
	}
	
	public static void setBlock(World w, Chunk c, ExtendedBlockStorage ebs, int x, int y, int z, Block b, int m, int flag)
	{
		int _flag = flag < 0 ? -flag : flag;
		
		if (_flag >= 2)
			w.markBlockForUpdate(x, y, z);
		if (_flag >= 3)
			w.notifyBlockChange(x, y, z, b);
		
		x &= 0xF; 
		y &= 0xF; 
		z &= 0xF;
		
		//EXTREME HAX
		if (ebs == null)
		{
			if (b != null)
				w.setBlock(x, y, z, b, m, flag);
			else
			{	
				w.setBlockMetadataWithNotify(x, y, z, m, flag);
			}
			return;
		}
		
		ebs.setExtBlockMetadata(x, y, z, m);
		if (b != null) ebs.func_150818_a(x, y, z, b);
		
		//Allow skipping relights
		if (flag > 0)
			c.updateSkylightColumns[x + (z << 4)] = true;
	}
	
	/**
	 * Guaranteed thread safe block setting method, directly manipulates EBS, skips some redundant world.setBlock calls,
	 * allows skipping of light recalculations, and allows unimportant block updates to deferred to the server tick at a later time.
	 * 
	 * <p>Only supports flags 2 and 3, however negative versions will skip lighting recalculations.
	 * 
	 * <p>Flag immediacy for fluid updates. Updates with no immediate time requirement
	 * AND a need for guaranteed concurrency SHOULD be called as "not immediate" (where they will be set by the server
	 * at the end of the current tick).
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
			c = w.getChunkProvider().provideChunk(x >> 4, z >> 4);
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
		//Just to be safe
		PatchBlockRegistry.counter = 0;
		
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
		_tickCounter += 1;
		Equalizer.WORKER.run();
		
		if (event.phase == Phase.START)
		{
			long timeCost = System.currentTimeMillis() - lastTime;
			if (lastTime > 0)
				if (timeCost > 500)
				{
					++RealisticFluids.GLOBAL_RATE;
					RealisticFluids.GLOBAL_RATE = Math.min(RealisticFluids.GLOBAL_RATE,RealisticFluids.GLOBAL_RATE_MAX);
				}else
				if (timeCost < 40 && (_tickCounter % RealisticFluids.GLOBAL_RATE)  == 1)
				{
					--RealisticFluids.GLOBAL_RATE;
					RealisticFluids.GLOBAL_RATE = Math.max(RealisticFluids.GLOBAL_RATE,RealisticFluids.GLOBAL_RATE_AIM);
				}
			lastTime = System.currentTimeMillis();
		}
		
		//System.out.println("Doing tick");
		if (event.phase == Phase.END && (tickCounter() % RealisticFluids.GLOBAL_RATE)  == 0)
		{
			int tickQuota;
			//System.out.println("Doingerizing Update sweep!");
			
			//FIND CHUNKS
			for (World w : MinecraftServer.getServer().worldServers)
			{
				if (w.playerEntities == null || w.playerEntities.size() == 0) continue;
				for (Object p : w.playerEntities)
				{
					EntityPlayer player = (EntityPlayer) p;
					ChunkCache map 		= ChunkDataMap.worldCache.get(w);
					if (map == null) continue;
					//iterate over all flagged chunks
					for (Chunk c : map.chunks.keySet())
					{	
						if (!c.isChunkLoaded) continue;//Just to be safe;
						int x = c.xPosition - (((int)player.posX) >> 4); 
						int z = c.zPosition - (((int)player.posZ) >> 4); 
						int dist = x * x + z * z; //Distance for distance testing
						if (dist <= RealisticFluids.UPDATE_RANGE) map.priority.add(c);
						else if (dist <= RealisticFluids.UPDATE_RANGE_FAR) 
							if (map.distant.size() < 256) //prevent leaking
								map.distant.add(c);
					}
				}
			}
			
			//Leave a minimum number of ticks per world per player (should cover a couple of chunks)
			tickQuota = RealisticFluids.MAX_UPDATES/Math.max(1, MinecraftServer.getServer().getCurrentPlayerCount());
			
			FluidWorkers.PWorker.quota = tickQuota;
			FluidWorkers.PWorker.myStartTime = tickCounter(); //MAKE SURE WE REMEMBER THE TICK
			FluidWorkers.PWorker.worlds = MinecraftServer.getServer().worldServers.clone();
			FluidWorkers.PRIORITY.run();

			//FluidWorkers.TWorker.quota = tickQuota;
			FluidWorkers.TWorker.myStartTime = tickCounter(); //MAKE SURE WE REMEMBER THE TICK
			FluidWorkers.TWorker.worlds = MinecraftServer.getServer().worldServers.clone();
			FluidWorkers.TRIVIAL.run();
		}	
		
		//Set blocks for a little bit on the server thread
		//This is triggered from using the setBlock call WITHOUT Immediacy
		//NOTE: This is 100% utterly thread safe.
		int toPerform =  BlockTask.blockTasks.size()/16;
			toPerform = toPerform < 32 ? 32 : toPerform;

		//Prevent lagging the system by allocating a fixed amount of time
		while (System.currentTimeMillis() - lastTime < 10 && BlockTask.blockTasks.size() > 0)
		{
			for (int i = 0; i < Math.min(toPerform, BlockTask.blockTasks.size()); i++)
			{
				BlockTask.blockTasks.remove().set();
			}
		}
	}

	//@SubscribeEvent
	public void clientTick(ClientTickEvent event)
	{
		if (event.phase == Phase.START) return;
		Random r = new Random();
		//Every few seconds
		if (r.nextInt(40) == 0)
		{
			//Iterate over the chunks around each player!
			//FIND CHUNKS
			for (World w : MinecraftServer.getServer().worldServers)
			{
				if (w.playerEntities == null || w.playerEntities.size() == 0) continue;
				for (Object p : w.playerEntities)
				{
					EntityPlayer player = (EntityPlayer) p;
					///???
				}
			
			}
			
			
		}
	
	}
	
	
}
