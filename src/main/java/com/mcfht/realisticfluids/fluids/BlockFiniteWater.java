package com.mcfht.realisticfluids.fluids;

import java.util.Random;

import com.mcfht.realisticfluids.RealisticFluids;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public final class BlockFiniteWater extends BlockFiniteFluid {

	public BlockFiniteWater(Material material) {
		super(material, RealisticFluids.waterVisc, RealisticFluids.WATER_UPDATE);
		//this.setLightOpacity(1);
		//this.setResistance(6F);
	}

	/* 
	//Getting some strange method not found errors...
	int field_149815_a;
    boolean[] field_149814_b = new boolean[4];
    int[] field_149816_M = new int[4];

    private void func_149811_n(World p_149811_1_, int p_149811_2_, int p_149811_3_, int p_149811_4_)
    {
    	return;
    }

    private void func_149813_h(World p_149813_1_, int p_149813_2_, int p_149813_3_, int p_149813_4_, int p_149813_5_)
    {
        return;
    }

    private int func_149812_c(World p_149812_1_, int p_149812_2_, int p_149812_3_, int p_149812_4_, int p_149812_5_, int p_149812_6_)
    {
       return 0;
    }

    private boolean[] func_149808_o(World p_149808_1_, int p_149808_2_, int p_149808_3_, int p_149808_4_)
    {
        return null;
    }

    private boolean func_149807_p(World p_149807_1_, int p_149807_2_, int p_149807_3_, int p_149807_4_)
    {
        return false;
    }

    protected int func_149810_a(World p_149810_1_, int p_149810_2_, int p_149810_3_, int p_149810_4_, int p_149810_5_)
    {
      return 0;
    }

    private boolean func_149809_q(World p_149809_1_, int p_149809_2_, int p_149809_3_, int p_149809_4_)
    {
    	return false;
    }
    public boolean func_149698_L()
    {
        return true;
    }


    @SideOnly(Side.CLIENT)
    public boolean getCanBlockGrass()
    {
        return this.canBlockGrass;
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
	*/
}
