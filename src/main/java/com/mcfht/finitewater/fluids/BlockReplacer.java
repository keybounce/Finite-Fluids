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
	protected BlockReplacer() {
		super(Material.rock);
	}

	@Override
	public void onBlockAdded(World world, int x, int y, int z)
	{
		
		BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
		
		if (biome == biome.beach || biome == biome.coldBeach || biome == biome.deepOcean || biome == biome.frozenOcean ||
			biome == biome.river || biome == biome.ocean || biome == biome.frozenRiver)
			world.setBlock(x, y, z, Blocks.water);
		
		//TODO Set block to my finite water yardieHarHar
		
		world.setBlock(x, y, z, FiniteWater.finiteWater);
	}
}