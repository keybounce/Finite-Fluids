package com.mcfht.realisticfluids.fluids;

import com.mcfht.realisticfluids.RealisticFluids;

import net.minecraft.block.material.Material;

public class BlockFiniteWater extends BlockFiniteFluid {

	public BlockFiniteWater(Material material) {
		super(material, RealisticFluids.waterVisc, RealisticFluids.WATER_UPDATE);
		//this.setLightOpacity(1);
		//this.setResistance(6F);
	}

	
	
	
}
