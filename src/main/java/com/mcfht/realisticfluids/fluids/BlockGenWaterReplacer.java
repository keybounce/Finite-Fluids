package com.mcfht.realisticfluids.fluids;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import com.mcfht.realisticfluids.RealisticFluids;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Currently unused. Will test various conditions and convert itself into the desired block, in order to simplify
 * the changes we need to make to the world gen.
 * @author FHT
 *
 */
public class BlockGenWaterReplacer extends BlockFiniteFluid
{
	public BlockGenWaterReplacer(Material m) {
		super(m, 1, 10);
	}

	@Override
	public void updateTick(World w, int x, int y, int z, Random r)
	{
		//Test directions, see if we can flow, then change to a different block
		//setLevel(world, x, y, z, FiniteWater.MAX_WATER, false);
		/*
		if (world.getBlock(x, y-1, z) == Blocks.air)
		{world.setBlock(x, y-1, z, Blocks.dirt);
		}if (world.getBlock(x, y-2, z) == Blocks.air)
		{world.setBlock(x, y-2, z, Blocks.stone);
		}int[][] directions = { {0,1}, {0,-1}, {1,0}, {-1,0}, {1,1} , {-1,-1}, {-1,1}, {1,-1} }; 
		for (int i = 0; i < 8; i++)
		{if (world.getBlock(x + directions[i][0], y, z + directions[i][2]) == Blocks.air)
		{world.setBlock(x + directions[i][0], y, z + directions[i][2], Blocks.dirt);}
		}
		 */
		//Use EBS Directly. Re-rendering is not a problem in any way, shape or form, since the blocks are identical
		//in all but function.
		
		ExtendedBlockStorage ebs0 = w.getChunkFromChunkCoords(x >> 4,  z >> 4).getBlockStorageArray()[y>>4];
		if (y <= 0) ebs0.func_150818_a(x & 0xF, y & 0xF, z & 0xF, Blocks.water);
		ExtendedBlockStorage ebs1 = ebs0;
		if ((y > 1) && (y-1) >> 4 != y >> 4){
			ebs1 = w.getChunkFromChunkCoords(x >> 4,  z >> 4).getBlockStorageArray()[(y-1)>>4];
		}
		if (ebs1.getBlockByExtId(x & 0xF, (y-1) & 0xF, z & 0xF).getMaterial() == Material.water)
		{
			ebs0.func_150818_a(x & 0xF, y & 0xF, z & 0xF, Blocks.water);
			//world.setBlock(x, y, z, FiniteWater.finiteWater);
		}else
		{
			ebs0.func_150818_a(x & 0xF, y & 0xF, z & 0xF, Blocks.water);
		}
		
		/*
		if (world.getBlock(x,y-1,z).getMaterial() == Material.water){
			world.setBlock(x, y, z, FiniteWater.finiteWater);
		}else{ world.setBlock(x, y, z, Blocks.water);}*/
	}


	public void doUpdate(World w, int x, int y, int z, Random r, int interval)
	{
		//Use EBS directly to make it faster. Re-rendering is not a problem in any way, shape or form, since the blocks are identical
		//in all but function.
		
		ExtendedBlockStorage ebs0 = w.getChunkFromChunkCoords(x >> 4,  z >> 4).getBlockStorageArray()[y>>4];
		if (y <= 0) ebs0.func_150818_a(x & 0xF, y & 0xF, z & 0xF, Blocks.water);
		ExtendedBlockStorage ebs1 = ebs0;
		if ((y > 1) && (y-1) >> 4 != y >> 4){
			ebs1 = w.getChunkFromChunkCoords(x >> 4,  z >> 4).getBlockStorageArray()[(y-1)>>4];
		}
		if (ebs1.getBlockByExtId(x & 0xF, (y-1) & 0xF, z & 0xF).getMaterial() == Material.water)
		{
			ebs0.func_150818_a(x & 0xF, y & 0xF, z & 0xF, Blocks.water);
			//world.setBlock(x, y, z, FiniteWater.finiteWater);
		}else
		{
			ebs0.func_150818_a(x & 0xF, y & 0xF, z & 0xF, Blocks.water);
		}
	}
	
	
	
	//@Override
	@Deprecated
	public void DEPRECATEDonBlockAdded(World w, int x, int y, int z)
	{
		BiomeGenBase biome = w.getBiomeGenForCoords(x, z);
		if (y < 50)
		{
			w.setBlock(x,y,z,Blocks.water);
		}
		w.setBlock(x, y, z, Blocks.water);
	}
	
}