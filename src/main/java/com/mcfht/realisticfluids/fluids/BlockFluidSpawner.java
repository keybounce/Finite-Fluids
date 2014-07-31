package com.mcfht.realisticfluids.fluids;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.mcfht.realisticfluids.RealisticFluids;
import com.mcfht.realisticfluids.util.UpdateHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Produces liquid beneath itself at a high rate. Is triggered by
 * redstone, and changes mode when right clicked & sneaking. Pretty basic tbh.
 * @author FHT
 *
 */
public class BlockFluidSpawner extends Block{

	public BlockFluidSpawner(Material material) {
		super(material);
		this.setTickRandomly(true);
	}

	public IIcon lava;
	public IIcon water;
	public IIcon off;
	
	public int tickRate(World w)
	{
		return 5;
	}
	
	public void onBlockAdded(World w, int x, int y, int z)
	{
		w.scheduleBlockUpdate(x, y, z, this, this.tickRate(w));
		//world.setBlockMetadataWithNotify(x, y, z, 2, 2);
	}
	

	public void onNeighborBlockChange(World w, int x, int y, int z, Block b)
	{
		if (w.isBlockIndirectlyGettingPowered(x, y, z) || w.isBlockIndirectlyGettingPowered(x, y + 1, z))
		{
			w.scheduleBlockUpdate(x, y, z, this, 5 + (UpdateHandler.INSTANCE.tickCounter() % 5));			
		}
	}
	
	public void updateTick(World w, int x, int y, int z, Random r)
	{
		
		//System.out.println("Source Block Ticking..." + world.getBlockMetadata(x, y, z));
		int meta = w.getBlockMetadata(x, y, z);
		if (w.getBlockMetadata(x, y, z) == 0) return;
		
		if (w.isBlockIndirectlyGettingPowered(x, y, z) || w.isBlockIndirectlyGettingPowered(x, y + 1, z))
		{
			//System.out.println("Source Block is powered...");
			Block b = w.getBlock(x, y-1,z);
			if (b == Blocks.air)
			{
				w.setBlock(x,y-1,z, meta == 1 ? Blocks.water : Blocks.lava);
				return;
			}
			if (b instanceof BlockFiniteFluid)
			{
				
				if (meta == 1)
				{ //water
					if (b.getMaterial() == Material.water)
					{
						((BlockFiniteFluid)b).setLevel(w, x, y-1, z, RealisticFluids.MAX_FLUID, true);
						return;
					}
					w.setBlock(x, y-1, z, Blocks.lava, 0, 3);
					((BlockFiniteFluid)b).setLevel(w, x, y-1, z, RealisticFluids.MAX_FLUID, true, Blocks.water);
					
				}else
				{//Lava
					if (b.getMaterial() == Material.lava)
					{
						((BlockFiniteFluid)b).setLevel(w, x, y-1, z, RealisticFluids.MAX_FLUID, true);
						return;
					}
					w.setBlock(x, y-1, z, Blocks.lava, 0, 3);
					((BlockFiniteFluid)b).setLevel(w, x, y-1, z, RealisticFluids.MAX_FLUID, true, Blocks.lava);
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
    public IIcon getIcon(int side, int m)
    {
    	if (m == 2)
     		return lava;
    	if (m == 1)
    		return water;
        return off;
    }
    
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int uK, float px, float py, float pz)
    {
        if (!p.isSneaking())
        {
            return false;
        }
            int i1 = w.getBlockMetadata(x, y, z);
            w.setBlockMetadataWithNotify(x, y, z, (i1 + 1) % 3, 2);
            w.scheduleBlockUpdate(x, y, z, this, 5 + (UpdateHandler.INSTANCE.tickCounter() % 5));	
            return true;
        
    }

    

}
