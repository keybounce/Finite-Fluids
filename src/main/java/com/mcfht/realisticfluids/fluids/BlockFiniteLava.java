package com.mcfht.realisticfluids.fluids;

import java.util.Random;

import com.mcfht.realisticfluids.RealisticFluids;
import com.mcfht.realisticfluids.util.UpdateHandler;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;

public class BlockFiniteLava extends BlockFiniteFluid{

	public BlockFiniteLava(Material material) {
		super(material, RealisticFluids.lavaVisc, RealisticFluids.LAVA_UPDATE);
		this.setLightLevel(0.8F); //Not max brightness
		this.setLightOpacity(10);
		this.setResistance(7F);
	}
	
	//When we random tick, set things on fire
	@Override
	public void updateTick(World w, int x, int y, int z, Random r)
	{
		super.updateTick(w, x, y, z, r);

        int l = r.nextInt((int)(1.F + ((float) getLevel(w, x, y, z)/ (float) RealisticFluids.MAX_FLUID)*5F));
        int i1 = 0;
        
        //Because lava actually burns things, you know?
        for (int i = 0; i < 4; i++)
        {
        	int x1 = x + directions[i][0];
        	int z1 = z + directions[i][1];
        	if (this.isFlammable(w, x1, y, z1))
        	{
        		w.setBlock(x1, y, z1, Blocks.fire);
        		i1++;
        	}
        }
        int skew = r.nextInt(4);

        for (int i = 0; i < 4 && i1 > 0; i++)
        {
        	int x1 = x + directions[(i + skew) & 3][0];
        	int z1 = z + directions[(i + skew) & 3][1];
        	if (this.isSameFluid(this, w.getBlock(x1, y, z1)))
        	{
        		if (w.getBlock(x1, y+1, z1) == Blocks.air)
        			w.setBlock(x1, y, z1, Blocks.fire);
        		--i1;
        	}
        }
        
        for (i1 = 0; i1 < l; ++i1)
        {
            x += r.nextInt(3) - 1;
            ++y;
            z += r.nextInt(3) - 1;
            Block block = w.getBlock(x, y, z);

            if (block.getMaterial() == Material.air)
            {
                if (	this.isFlammable(w, x - 1, y, z)
                	||  this.isFlammable(w, x + 1, y, z)
                	||  this.isFlammable(w, x, y, z - 1) 
                	||  this.isFlammable(w, x, y, z + 1)
                	||  this.isFlammable(w, x, y - 1, z) 
                	||  this.isFlammable(w, x, y + 1, z))
                {
                    w.setBlock(x, y, z, Blocks.fire);
                    return;
                }
            }
            else if (block.getMaterial().blocksMovement())
            {
                return;
            }
        }

        if (l == 0)
        {
            i1 = x;
            int k1 = z;

            for (int j1 = 0; j1 < 3; ++j1)
            {
                x = i1 + r.nextInt(3) - 1;
                z = k1 + r.nextInt(3) - 1;

                if (w.isAirBlock(x, y + 1, z) && this.isFlammable(w, x, y, z, null))
                {
                    w.setBlock(x, y + 1, z, Blocks.fire);
                }
            }
        }
    }
    private boolean isFlammable(World w, int x, int y, int z)
    {
        return w.getBlock(x, y, z).getMaterial().getCanBurn();
    }
    
    /**
     * Makes lava flow a little bit more 
     */
	public int breakInteraction(World w, Block b1, int m1, int x0, int y0, int z0, int l0, int x1, int y1, int z1)
	{
		int c = canBurnAndBreak(b1);
		
		if (c < 0)
		{
			if (w.rand.nextInt(5) == 0) UpdateHandler.setBlock(w, x1, y1, z1, Blocks.fire, 0, -2, true);
			this.updateTick(w, x0, y0, z0, w.rand);
			return 1;
		}
		
		if (c > 0)
		{
			if (b1 instanceof IPlantable)
			{
				if (y0 - y1 >= 0)
					UpdateHandler.setBlock(w, x1, y1, z1, Blocks.fire, 0, -2, false);
				else
					UpdateHandler.setBlock(w, x1, y1, z1, Blocks.air, 0, -2, true);
				
				return 1;
			}
			if (b1 == Blocks.snow_layer)
			{
				UpdateHandler.setBlock(w, x1, y1, z1, Blocks.air, 0, -2, true);
				return 1;
			}
			if (y0 - y1 < 0 || l0 > flowBreak)
			{
				if (b1 == Blocks.fire)
				{
					if (w.getBlock(x1, y1+1, z1) == Blocks.air)
						UpdateHandler.setBlock(w, x0, y1+1, z0, Blocks.fire, 0, -2, true);
					else if (w.getBlock(x0, y1+1, z0) == Blocks.air)
						UpdateHandler.setBlock(w, x0, y1+1, z0, Blocks.fire, 0, -2, false);
					return 1;
				}
				
				if (b1 != Blocks.snow_layer)
					b1.dropBlockAsItem(w, x0, y0, z0, m1, m1); //if (block != Blocks.snow_layer)
				
				w.setBlockToAir(x1, y1, z1);
				return 1;
			}
			return  0;
		}
		return 0;
	}

	public int canBurnAndBreak(Block b)
	{
		if (b.getMaterial().getCanBurn())
		{
			if (new Random().nextInt(5) == 0)
			{
				return -1;
			}
		}
		boolean a = !(b != Blocks.wooden_door && b != Blocks.iron_door && b != Blocks.standing_sign && b != Blocks.ladder && b != Blocks.reeds ? (b.getMaterial() == Material.portal ? true : b.getMaterial().blocksMovement()) : true);
		if (a) return 1;
		return 0;
	}
}
