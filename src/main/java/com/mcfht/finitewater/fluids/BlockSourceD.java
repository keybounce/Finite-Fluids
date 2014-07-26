package com.mcfht.finitewater.fluids;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.mcfht.finitewater.FiniteWater;
import com.mcfht.finitewater.util.UpdateHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Produces liquid beneath itself at a high rate. Is triggered by
 * redstone, and changes mode when right clicked & sneaking. Pretty basic tbh.
 * @author FHT
 *
 */
public class BlockSourceD extends Block{

	public BlockSourceD(Material material) {
		super(material);
		this.setTickRandomly(true);
	}

	public IIcon lava;
	public IIcon water;
	public IIcon off;
	
	public int tickRate(World world)
	{
		return 5;
	}
	
	public void onBlockAdded(World world, int x, int y, int z)
	{
		world.scheduleBlockUpdate(x, y, z, this, this.tickRate(world));
		//world.setBlockMetadataWithNotify(x, y, z, 2, 2);
	}
	

	public void onNeighborBlockChange(World world, int x, int y, int z, Block block)
	{
		if (world.isBlockIndirectlyGettingPowered(x, y, z) || world.isBlockIndirectlyGettingPowered(x, y + 1, z))
		{
			world.scheduleBlockUpdate(x, y, z, this, 5 + (UpdateHandler.INSTANCE.tickCounter % 5));			
		}
	}
	
	public void updateTick(World world, int x, int y, int z, Random r)
	{
		
		//System.out.println("Source Block Ticking..." + world.getBlockMetadata(x, y, z));
		int meta = world.getBlockMetadata(x, y, z);
		if (world.getBlockMetadata(x, y, z) == 0) return;
		
		if (world.isBlockIndirectlyGettingPowered(x, y, z) || world.isBlockIndirectlyGettingPowered(x, y + 1, z))
		{
			//System.out.println("Source Block is powered...");
			Block b = world.getBlock(x, y-1,z);
			if (b == Blocks.air)
			{
				world.setBlock(x,y-1,z, meta == 1 ? FiniteWater.finiteWater : FiniteWater.finiteLava);
				return;
			}
			if (b instanceof BlockFFluid)
			{
				
				if (meta == 1)
				{ //water
					if (b.getMaterial() == Material.water)
					{
						((BlockFFluid)b).setLevel(world, x, y-1, z, BlockFFluid.maxWater, true);
						return;
					}
					world.setBlock(x, y-1, z, FiniteWater.finiteLava, 0, 3);
					((BlockFFluid)b).setLevel(world, x, y-1, z, BlockFFluid.maxWater, true, FiniteWater.finiteWater);
					
				}else
				{//Lava
					if (b.getMaterial() == Material.lava)
					{
						((BlockFFluid)b).setLevel(world, x, y-1, z, BlockFFluid.maxWater, true);
						return;
					}
					world.setBlock(x, y-1, z, FiniteWater.finiteLava, 0, 3);
					((BlockFFluid)b).setLevel(world, x, y-1, z, BlockFFluid.maxWater, true, FiniteWater.finiteLava);
				}
			}
		}
	}
	
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg)
    {
        this.lava = reg.registerIcon("lava_flow");
        this.water = reg.registerIcon("water_flow");
        this.off = reg.registerIcon("bedrock");
    }
    
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta)
    {
    	if (meta == 2)
     		return lava;
    	if (meta == 1)
    		return water;
        return off;
    }
    
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer p, int px, float py, float pz, float pzz)
    {
        if (!p.isSneaking())
        {
            return false;
        }
            int i1 = world.getBlockMetadata(x, y, z);
            world.setBlockMetadataWithNotify(x, y, z, (i1 + 1) % 3, 2);
            world.scheduleBlockUpdate(x, y, z, this, 5 + (UpdateHandler.INSTANCE.tickCounter % 5));	
            return true;
        
    }

    

}
