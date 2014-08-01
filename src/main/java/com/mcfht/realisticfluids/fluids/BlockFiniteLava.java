package com.mcfht.realisticfluids.fluids;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;


import com.mcfht.realisticfluids.RealisticFluids;
import com.mcfht.realisticfluids.Util;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
/**
 * WARNING: Lava will annihilate any flammable object it touches :D
 * @author FHT
 *
 */
public final class BlockFiniteLava extends BlockFiniteFluid{
	public BlockFiniteLava(Material material) {
		super(material, RealisticFluids.lavaVisc, RealisticFluids.LAVA_UPDATE);
		//this.setLightLevel(0.8F); //Not max brightness :D
		this.setLightOpacity(15);
		this.setResistance(7F);
	}
	
	//When we random tick, set things on fire (copy paste vanilla lol
	/** Pretty banal copy of the vanilla method, only exception is that more lava has more chances to start fires!
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 * @param r
	 */
	@Override
	public void updateTick(World w, int x, int y, int z, Random r)
	{
		super.updateTick(w, x, y, z, r);
        int l = r.nextInt((int)(1.F + ((float) w.getBlockMetadata(x, y, z)/1.666F))); //up to 2x as many chances for a full block
        int i1 = 0;
        for (int i = 0; i < 4; i++)
        {
        	int x1 = x + Util.cardinalX(i);
        	int z1 = z + Util.cardinalZ(i);
        	if (this.isFlammable(w, x1, y, z1))
        	{
        		w.setBlock(x1, y, z1, Blocks.fire);
        		i1++;
        	}
        }
        int skew = r.nextInt(4);
        for (int i = 0; i < 4 && i1 > 0; i++)
        {
        	int x1 = x + Util.cardinalX(i + skew);
        	int z1 = z + Util.cardinalZ(i + skew);
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
            	return;
            
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
     * Makes lava flow a little bit more dangerously
     */
	public int breakInteraction(World w, Block b1, int m1, int x0, int y0, int z0, int l0, int x1, int y1, int z1)
	{
		int c = canBurnAndBreak(b1);
		if (c < 0)
		{
			this.updateTick(w, x0, y0, z0, w.rand); //Increase chance to spread fire
			return 1;
		}
		if (c > 0)
		{
			//Chance to torch any adjacent small woodstuffs
			if (b1 instanceof IPlantable || b1.getMaterial() == Material.plants || b1.getMaterial() == Material.leaves || (b1.getMaterial() == Material.wood && (b1 instanceof BlockLadder || b1 instanceof BlockSign)))
			{
				if (y0 - y1 >= 0)
					RealisticFluids.setBlock(w, x1, y1, z1, Blocks.fire, 0, -2, false); //BUUUUURN EEEEEET
				if (w.rand.nextInt(45) == 0) this.updateTick(w,  x0, y0,  z0, w.rand); //Stacks with adjacent fire :D
				return 1;
			}
			if (b1 == Blocks.fire)
			{
				this.updateTick(w,  x0, y0,  z0, w.rand); //Stacks with adjacent fire :D
				if (y0 > y1 || w.rand.nextInt(30) == 0) w.setBlock(x1, y1, z1, Blocks.air);
				return 1;
			}
			if (b1 == Blocks.snow_layer)
			{
				RealisticFluids.setBlock(w, x1, y1, z1, Blocks.air, 0, -2, true);
				return 1;
			}
			if (y0 - y1 < 0 || l0 > flowBreak)
			{
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
			if (new Random().nextInt(50) == 0)
			{
				return -1;
			}
		}
		boolean a = !(b != Blocks.wooden_door && b != Blocks.iron_door && b != Blocks.standing_sign && b != Blocks.ladder && b != Blocks.reeds ? (b.getMaterial() == Material.portal ? true : b.getMaterial().blocksMovement()) : true);
		if (a) return 1;
		return 0;
	}

    @Override
    public Block setHardness(float f)
    {
    	return super.setHardness(f);
    }
    public Block c(float f)
    {
    	this.blockHardness = f;
    	return this;
    }
    @Override
    public Block setLightOpacity(int o)
    {
    	return super.setLightOpacity(o);
    }
    @Override
    public Block setBlockName(String name)
    {
    	return super.setBlockName(name);
    }
    @Override 
    public Block setLightLevel(float f)
    {
    	return super.setLightLevel(f);
    }
    @Override
    public Block setBlockTextureName(String tex)
    {
    	return super.setBlockTextureName(tex);
    }
    @Override
    public Block disableStats()
    {
    	return super.disableStats();
    }
}



