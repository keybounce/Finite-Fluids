package com.mcfht.realisticfluids;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * Contains some fun physics-y kinds of things, like chain smashing more than
 * one glass block at once, things like that :)
 * 
 * @author FHT
 * 
 */
public class EventPerformer {

	public static void smashGlass(final World w, final int x0, final int y0,
			final int z0, final Block b0, final int force) {
		RealisticFluids.setBlock(w, x0, y0, z0, Blocks.air, 0, 3);

		w.playSoundEffect(x0 + 0.5D, y0 + 0.5D, z0 + 0.5D, "dig.glass", 1.F,
				.9F + w.rand.nextFloat() * 0.1F);

		Math.sqrt(Math.max(1, b0.getExplosionResistance(null)));

		for (int i = 0; i < 5; i++) {
			int xN = x0;
			int zN = z0;
			int yN = y0;

			for (int j = 0; j < 4; j++) {
				final int r = w.rand.nextInt(6);
				boolean flag = false;
				for (int k = 0; k < 6; k++) {
					final int dx = Util.intFaceX(k + r);
					final int dy = Util.intFaceY(k + r);
					final int dz = Util.intFaceZ(k + r);
					final int xM = xN + dx;
					final int yM = yN + dy;
					final int zM = zN + dz;

					final Block b = w.getBlock(xM, yM, zM);
					if (b == b0) {
						xN += dx;
						yN += dy;
						zN += dz;
						RealisticFluids.setBlock(w, xN, yN, zN, Blocks.air, 0,
								3);
						w.playSoundEffect(xN + 0.5D, yN + 0.5D, zN + 0.5D,
								"dig.glass", 1.F,
								.9F + w.rand.nextFloat() * 0.1F);
						flag = true;
						break;
					} else
						continue;
				}
				if (!flag)
					break;
			}
		}

	}
}
