package com.mcfht.finitewater.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.mcfht.finitewater.FiniteWater;
import com.mcfht.finitewater.fluids.BlockFFluid;
import com.mcfht.finitewater.util.ChunkCache.ChunkMap;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;

/**
 *  How it works!
 *  <p>
 * 	<p><b>1. </b> Select all players, determine a global number of allowed updates to perform
 * 	depending on the number of players, etcetera.
 * 	<p><b>2. </b>Iterate over the chunks around the players, starting with the closer chunks and then trying
 * 	to perform a few updates in some random distant chunks.

 * 
 * <p>Within each chunk, we have a boolean map of flags, and a preset counter telling us roughly
 * how many updates we need to process in this chunk. From here, we just iterate over the boolean
 * map and update the blocks as we come to them, unflagging them as we go. Additionally, we spread
 * a few random ticks out to some blocks, to use for our own purposes.
 * 
 * <p>Note that the boolean map uses a 16x16x16 grouping. This allows us to simplify calculations,
 * since who is going to 1. Fill a chunk from top to bottom with water, and 2. Want their code
 * performing 65536 iteration loops for less than 4000 water blocks.
 * 
 * <p><b>ADVANTAGES:</b>
 * <p>By using a simple array to store flags, we can greatly increase the speed of flagging updates
 * (as opposed to maps). By performing all updates at once, we also ditch the overhead cost of
 * having updates "out of alignment" (aka, block A updates in tick 1, scheduling neighbor blocks,
 * which get scheduled again in the next tick by block B).
 * 
 * <p><b>DISADVANTAGES:</b>
 * <p>Potential for jitter... Might investigate threading 
 * (kudos to mbxr and his "Fysiks is Fun" mod for the threading idea)
 */
public class UpdateHandler {
	public int tickCounter = 0;
	
	public static final UpdateHandler INSTANCE = new UpdateHandler();
		
	static class equalizationTask
	{
		public static ArrayList<equalizationTask> tasks = new ArrayList<equalizationTask>();
		
		World world;
		int x;
		int y;
		int z;
		BlockFFluid block;
		public equalizationTask(World world, int x, int y, int z, BlockFFluid block)
		{
			this.world = world; this.x = x; this.y = y; this.z = z; this.block = block;
		}
		
		public static boolean doTask(int n)
		{
			if (n > tasks.size()) return false;
			equalizationTask t = tasks.remove(n);
			if (t.world.getChunkFromChunkCoords(t.x >>4, t.z>>4).isChunkLoaded)
			{
				t.block.equalize(t.world, t.x, t.y, t.z, 32);
				return true;
			}
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
		if (ChunkCache.worldCache.get(event.world) != null)
		{
			ChunkCache c = ChunkCache.worldCache.get(event.world).waterCache.remove(event.getChunk());
			if (c != null)
			{
				//Save the GC some trouble
				c.waterArray = null;
				c.updateFlags = null;
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
		if (ChunkCache.worldCache.get(event.world) != null)
		{
			for (ChunkCache c : ChunkCache.worldCache.get(event.world).waterCache.values())
			{
				c.waterArray = null;
				c.updateFlags = null;
				c.updateCounter = null;
				ChunkCache.worldCache.get(event.world).waterCache.values().remove(c);
			}
		}
	}
	
	@SubscribeEvent
	public void serverTick(ServerTickEvent event)
	{
		if (event.phase != Phase.END) return;
		
		
		long time = System.currentTimeMillis();
		tickCounter++;
		
		//Perform equalization tasks for a certain amount of time per tick, fires at n = 2
		if (tickCounter % FiniteWater.GLOBAL_UPDATE_RATE == (FiniteWater.GLOBAL_UPDATE_RATE/2) - 1)
		{
			while (equalizationTask.tasks.size() > 0 && System.currentTimeMillis() - time < 20)
			{
				equalizationTask.doTask(0);
			}
		}
		
		//Schedule chunks, fires at n = 4
		if (tickCounter % FiniteWater.GLOBAL_UPDATE_RATE == (FiniteWater.GLOBAL_UPDATE_RATE - 1))
		{
			
			//int tickQuota = Math.max(48, 300/Math.max(1, MinecraftServer.getServer().getCurrentPlayerCount()));
			
			for (World world : MinecraftServer.getServer().worldServers)
			{
				if (world.playerEntities == null || world.playerEntities.size() == 0) continue;
				
				for (Object p : world.playerEntities)
				{
					EntityPlayer player = (EntityPlayer) p;
					ChunkMap map = ChunkCache.worldCache.get(world);
					//System.out.println("We are in " + world.provider.dimensionId + ", is registered? " + map != null);
					if (map == null) continue;
					
					//iterate over all flagged chunks
					for (Chunk c : map.waterCache.keySet())
					{
						//Just to be safe;
						if (!c.isChunkLoaded) continue;
						
						//Get all the relative coordinates of each chunk for distance testing
						int x = c.xPosition - (((int)player.posX) >> 4); 
						int z = c.zPosition - (((int)player.posZ) >> 4); 
						int y;
						
						int dist = x * x + z * z;
						
						if (dist <= FiniteWater.UPDATE_RANGE)
							map.priority.add(c);
						
						else if (dist <= 2 * FiniteWater.UPDATE_RANGE)
							map.random.add(c);
						
						}
					}
				}	
			return;
		}
		
		//Fires at n = 5
		if (tickCounter % FiniteWater.GLOBAL_UPDATE_RATE == 0)
		{
			//System.out.println("*********************************UPDATE TICKING!");
			
			
			int tickQuota;
			//Leave a minimum number of ticks per world per player (should cover a couple of chunks)
			//Not sure how well this works with hundreds of players lol
			tickQuota = 300/Math.max(1, MinecraftServer.getServer().getCurrentPlayerCount());
			for (World world : MinecraftServer.getServer().worldServers)
			{
				
				//There are no players, so there is no point
				if (world.playerEntities == null || world.playerEntities.size() == 0) continue;

				ChunkMap map = ChunkCache.worldCache.get(world);
				if (map == null) continue;
				
				if (map.priority.size() <= 0) continue;
				
				//System.out.println("World: " + world.provider.getDimensionName() + ", " + map.priority.size());
				
				int ticksLeft = tickQuota + FiniteWater.FORCE_UPDATES; //Give ourselves a tick quota
				
				//Start with priority chunks!
				for (Chunk c : map.priority)
				{
					ChunkCache t = map.waterCache.get(c);
					if (t == null || !c.isChunkLoaded) continue;
					ticksLeft -= doTask(world, c, t, false);
				}
				map.priority.clear();
				
				//Exit if we are under load
				if (System.currentTimeMillis() - time > 40) return;
				if (ticksLeft <= 0 && FiniteWater.FORCE_UPDATES <= 0) break; //screw this world lol

				//Now we are going to update some pseudo-random distant chunks
				//Give ourselves a certain number of random ticks
				ticksLeft =  FiniteWater.FORCE_UPDATES > ticksLeft ? FiniteWater.FORCE_UPDATES : ticksLeft;
			
				while (map.random.size() > 0 && (ticksLeft > 0 || System.currentTimeMillis() - time < 10))
				{
					//Select a random distant chunk
					int i = world.rand.nextInt(map.random.size());
					Chunk c = (Chunk) map.random.toArray()[i]; //can we just do 0?
					map.random.remove(c);
					
					ChunkCache t = map.waterCache.get(c);
					if (t == null || !c.isChunkLoaded) continue;
					
					ticksLeft -= doTask(world, c, t, true);
				}
			}
		}
		
	}
	
	/**
	 * Performs updates within a chunk (or more precisely, a ChunkCache object
	 * @param world
	 * @param c
	 * @param t
	 * @param flag Do heavy equalization?
	 * @return
	 */
	public int doTask(World world, Chunk c, ChunkCache t, boolean flag)
	{
		int cost = 0;
		int x,y,z;
		//Iterate over each 
		for (int i = 0; i < 16; i++)
		{
			//Perform our own update tick on some random blocks
			///////////////////////////////////////////////////////////////////////////////
			int rem = flag ? 4 : 1; //equalize more in distant chunks since it is ugly
			for (int j = 0; j < 3; j++)
			{
				x = world.rand.nextInt(16);
				y = world.rand.nextInt(16) + (i << 4);
				z = world.rand.nextInt(16);
				
				Block b = c.getBlock(x, y, z);
				
				//Only bother updating it if it is a fluid
				if (b instanceof BlockFFluid)
				{
					//DO SOME OTHHER STUFF?
					
					//Now equalize
					if (rem-- <= 0) continue;
					
					//Now try to move towards surface a little bit
					//Try to reach a top block
					for (int count = 0; world.getBlock(x, y+1, z) instanceof BlockFFluid && count < 8; count++)
					{
						y += 1;
					}
					
					int level = t.getWaterLevel(world, c, x, y, z);
					if (level < BlockFFluid.maxWater - (BlockFFluid.maxWater >> 3))
					{
						//Queue this block to be equalized (spread some load to other ticks)
						equalizationTask.tasks.add(new equalizationTask(world,(c.xPosition << 4) + x, y,
								(c.zPosition << 4) + z, (BlockFFluid) b));
					}
				}
			
			}
			////////////////////////////////////////////////////////////////////////////////////
			
			//No updates, exit
			if (t.updateCounter[i] == 0)
				continue;

			//System.out.println(i + " =======> Performing : " + c.getValue().updateCounter[i] + " Block Updates");
			//Use some tick quota, but maintain some minimum number of global updates
			cost += Math.max(4, t.updateCounter[i] >> 8);
			//Reset the counter
			t.updateCounter[i] = 0;;
			
			/////////////////////////////////////////////////////////////////////////////////////
			for (int j = 0; j < 4096; j++)
			{
				if (t.updateFlags[i][j])
				{
					//Un-flag this block
					t.updateFlags[i][j] = false;
			
					x = c.xPosition << 4;
					z = c.zPosition << 4;
					
					y = i << 4;
														
					x +=  j 		& 0xF;
					y += (j >> 4) 	& 0xF;
					z += (j >> 8) 	& 0xF;
					
					Block b = world.getBlock(x, y, z);
					if (b instanceof BlockFFluid)
					{
						//System.out.println("Ticking...");
						//TODO thread pool?
						b.updateTick(world, x, y, z, world.rand);
					}
				}
			}
			////////////////////////////////////////////////////////////////////////////////////////////
		}
	
		
		return 0;
	}
	
}
