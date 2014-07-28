package com.mcfht.finitewater.fluids;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import com.mcfht.finitewater.FiniteWater;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Currently unused. Will test various conditions and convert itself into the desired block, in order to simplify
 * the changes we need to make to the world gen.
 * @author FHT
 *
 */
public class BlockReplacer extends BlockFFluid
{
	public BlockReplacer(Material m) {
		super(m, 2, 2);
	}

	
	@Override
	public void updateTick(World world, int x, int y, int z, Random rand)
	{
		//Test directions, see if we can flow, then change to a different block
		//setLevel(world, x, y, z, FiniteWater.MAX_WATER, false);
		/*
		if (world.getBlock(x, y-1, z) == Blocks.air)
		{
			world.setBlock(x, y-1, z, Blocks.dirt);
		}
		
		if (world.getBlock(x, y-2, z) == Blocks.air)
		{
			world.setBlock(x, y-2, z, Blocks.stone);
		}
		
		int[][] directions = { {0,1}, {0,-1}, {1,0}, {-1,0}, {1,1} , {-1,-1}, {-1,1}, {1,-1} }; 
		for (int i = 0; i < 8; i++)
		{
			
			if (world.getBlock(x + directions[i][0], y, z + directions[i][2]) == Blocks.air)
			{
				world.setBlock(x + directions[i][0], y, z + directions[i][2], Blocks.dirt);
			}
		}
		 */

		if (world.getBlock(x, y-1, z) == Blocks.air)
		{
			world.setBlock(x, y, z, Blocks.dirt);
			return;
		}
		
		int[][] directions = { {0,1}, {0,-1}, {1,0}, {-1,0}, {1,1} , {-1,-1}, {-1,1}, {1,-1} }; 
		for (int i = 0; i < 8; i++)
		{
			
			if (world.getBlock(x + directions[i][0], y, z + directions[i][1]) == Blocks.air)
			{
				world.setBlock(x, y, z, Blocks.dirt);
				return;

			}
		}
		
		world.setBlock(x, y, z, FiniteWater.finiteWater);
		
	}

	
	@Override
	public void onBlockAdded(World world, int x, int y, int z)
	{
		
		BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
		
		if (y < 50)
		{
			world.setBlock(x,y,z,Blocks.water);
		}
		
		world.setBlock(x, y, z, FiniteWater.finiteWater);
	}
	
}