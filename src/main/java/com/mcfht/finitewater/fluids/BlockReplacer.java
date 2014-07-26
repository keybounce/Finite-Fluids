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
public class BlockReplacer extends Block
{
	public BlockReplacer(Material m) {
		super(m);
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