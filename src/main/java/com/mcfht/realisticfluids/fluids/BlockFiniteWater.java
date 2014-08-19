package com.mcfht.realisticfluids.fluids;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

import com.mcfht.realisticfluids.RealisticFluids;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class BlockFiniteWater extends BlockFiniteFluid {

	public BlockFiniteWater(final Material material) {
		super(material, RealisticFluids.waterVisc, RealisticFluids.WATER_UPDATE, 0);
		//this.setLightOpacity(1);
		//this.setResistance(6F);
	}

	//Getting some strange method not found errors...
	int field_149815_a;
    boolean[] field_149814_b = new boolean[4];
    int[] field_149816_M = new int[4];

    protected int func_149810_a(final World p_149810_1_, final int p_149810_2_, final int p_149810_3_, final int p_149810_4_, final int p_149810_5_)
    {
      return 0;
    }

    @Override
	public boolean func_149698_L()
    {
        return true;
    }


    @Override
	@SideOnly(Side.CLIENT)
    public boolean getCanBlockGrass()
    {
        return canBlockGrass;
    }
    @Override
    public Block setHardness(final float f)
    {
    	return super.setHardness(f);
    }
    @Override
	public Block c(final float f)
    {
    	blockHardness = f;
    	return this;
    }
    @Override
    public Block setLightOpacity(final int o)
    {
    	return super.setLightOpacity(o);
    }
    @Override
    public Block setBlockName(final String name)
    {
    	return super.setBlockName(name);
    }
    @Override
    public Block setLightLevel(final float f)
    {
    	return super.setLightLevel(f);
    }
    @Override
    public Block setBlockTextureName(final String tex)
    {
    	return super.setBlockTextureName(tex);
    }
    @Override
    public Block disableStats()
    {
    	return super.disableStats();
    }

}
