/* EQUALIZATION SCHEDULING SYSTEM
 * 
 * TODO: Write better documentation
 * 
 */

package com.mcfht.realisticfluids.util;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.world.World;

import com.mcfht.realisticfluids.RealisticFluids;
import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;

/**
 * The thing that equalizes water. Calls the equalization method in the FFluid block (which exists
 * where it does to allow overrides in different fluids later).
 * @author FHT
 *
 */
public class Equalizer {

	public static final Worker EqualizeWorker = new Worker();
	public static final Thread WORKER = new Thread(EqualizeWorker);
	
	
	protected static ConcurrentLinkedQueue<EqualizationTask> tasks = new ConcurrentLinkedQueue<EqualizationTask>();
	//private static ArrayList<EqualizationTask> tasks = new ArrayList<EqualizationTask>();
	
	/**
	 * Equalization Task Object for multiple thread access stuffs
	 * @author FHT
	 *
	 */
	private static class EqualizationTask
	{
		World world;int x;int y;int z; BlockFiniteFluid f; int distance;
		
		public EqualizationTask(World w, int x, int y, int z, BlockFiniteFluid f, int distance)
		{
			this.world = w; this.x = x; this.y = y; this.z = z; this.f = f; this.distance = distance;
		}
	}
	
	public static void addTask(World w, int x, int y, int z, BlockFiniteFluid f, int distance)
	{
		//Prevent over-filling the queue
		if (tasks.size() > 4*RealisticFluids.EQUALIZE_GLOBAL)
		{
			//if (w.rand.nextInt(10) == 0) System.err.println("The water equalizer is running behind!");
			return;
		}
		
		tasks.add(new EqualizationTask(w, x, y, z, f, distance));
	}
	
	/** Perform this equalization task. <b>THREAD SAFE</b>*/
	private static boolean equalize(int n)
	{
		if (n > tasks.size()) return false;
		EqualizationTask task = tasks.poll(); if (task == null) return false;
		if (task.world.getChunkFromChunkCoords(task.x >> 4, task.z >>4 ).isChunkLoaded)
		{
			task.f.equalize(task.world, task.x, task.y, task.z, task.distance);
			return true;
		}
		return false;
	}
	
	public static class Worker implements Runnable
	{
		public int myStartTime;
		public boolean running = false;

		
		@Override
		public void run() 
		{
			long startTime = System.currentTimeMillis();
			int i = 0;
			while ((tasks.size() > 0 && System.currentTimeMillis() - startTime < 10 )|| (i++ < RealisticFluids.EQUALIZE_GLOBAL))
			{
				equalize(0);
			}
			tasks.clear();
		}
			
		
	}	
}
