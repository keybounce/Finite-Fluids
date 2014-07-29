/* EQUALIZATION SCHEDULING SYSTEM
 * 
 * TODO: Write better documentation
 * 
 */

package com.mcfht.realisticfluids.util;

import java.util.ArrayList;

import net.minecraft.world.World;

import com.mcfht.realisticfluids.fluids.BlockFiniteFluid;

/**
 * The thing that equalizes water. Calls the equalization method in the FFluid block (which exists
 * where it does to allow overrides in different fluids later).
 * @author FHT
 *
 */
public class Equalizer {

	public static final Thread WORKER = new Thread(new Worker());
	private static ArrayList<EqualizationTask> tasks = new ArrayList<EqualizationTask>();
	
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
		//Prevent leaking if we are too slow
		if (tasks.size() > 256)
		{
			System.out.println("The water equalizer is lagging behind!!!");
			return;
		}
		
		tasks.add(new EqualizationTask(w, x, y, z, f, distance));
	}
	
	/** Perform this equalization task. <b>THREAD SAFE</b>*/
	private static boolean equalize(int n)
	{
		if (n > tasks.size()) return false;
		EqualizationTask task = tasks.remove(n);
		if (task.world.getChunkFromChunkCoords(task.x >> 4, task.z >>4 ).isChunkLoaded)
		{
			task.f.equalize(task.world, task.x, task.y, task.z, task.distance);
			return true;
		}
		return false;
	}
	
	private static class Worker implements Runnable
	{
		@Override
		public void run() {
			for (int i = 0; i < Math.min(tasks.size(), 32); i++)
			{
				equalize(0);
			}
		}		
	}	
}
