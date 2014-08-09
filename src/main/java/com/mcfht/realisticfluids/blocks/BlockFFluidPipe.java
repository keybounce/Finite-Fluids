package com.mcfht.realisticfluids.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.mcfht.realisticfluids.Util;

/**
 * Pipes don't do much by themselves :D TODO: Make water fall down pipes even
 * without pumps
 * 
 * @author FHT
 * 
 */
public class BlockFFluidPipe extends Block
{
	public static final int[][]	pipeDirections	=
												{
												{0, -1, 0},
												{0, 1, 0},
												{1, 0, 0},
												{0, 0, 1},
												{-1, 0, 0},
												{0, 0, -1}};

	protected BlockFFluidPipe(final Material material)
	{
		super(material);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void onBlockPlacedBy(final World w, final int x, final int y, final int z, final EntityLivingBase player, final ItemStack is)
	{
		// Get rotation meta
		final int l = Util.getRotationFromEntity(w, x, y, z, player);
		w.setBlockMetadataWithNotify(x, y, z, l, 2);
	}

}
