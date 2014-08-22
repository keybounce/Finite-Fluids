package com.mcfht.realisticfluids.fluids;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.init.Blocks;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.mcfht.realisticfluids.FluidData;
import com.mcfht.realisticfluids.FluidData.ChunkData;
import com.mcfht.realisticfluids.RealisticFluids;
import com.mcfht.realisticfluids.Util;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The parent class of all liquids. TODO: Make sounds work
 *
 * @author FHT
 */
public class BlockFiniteFluid extends BlockLiquid {
    /** Tendency of this liquid to flow */
    public final int viscosity;

    /** Internal flow capacity of liquid: how well it can equalize between neighbors */
    public final int internalFlowThreshold;

    /** Rate at which this liquid flows */
    public int flowRate;
    /** Amount of fluid needed to break things */
    public final int flowBreak = RealisticFluids.MAX_FLUID >> 3;

    public final double density;


    /**
     * Enables us to follow pressure propagation back to higher pressure areas.
     */
    public static final int pressureStep = 1;


    /**
     * Additional pressure from height; consider;
     *
     * p = depth * density * gravity
     *
     * Assume density is 1 because it is either constant or can be scaled
     * retrospectively between fluids, while g remains constant in all
     * situations, hence; p is a measure of depth and nothing more.
     *
     * Ideally using the max fluid value should be good enough, but whatever...
     */
    public static final int fullBlockPressureFactor = RealisticFluids.fluidFactor;
    public static final int pressureGain = 0x1 << fullBlockPressureFactor; //RealisticFluids.MAX_FLUID;

    /**
     * Initialize a new fluid.
     *
     * @param material
     *            	: 	Water or lava (others will work, but interactions may be
     *            		unreliable)
     * @param runniness
     *            	: 	Factor of flow. Water = 4, Lava = 3.
     * @param flowRate
     *            	: 	How often to update this block (every N sweeping updates =
     *           		n*5 ticks)
     * @param internalFlowThreshold
     *				: 	How hard it is for this fluid to equalize between neighboring blocks.
     * 					Suggested value of 0 means "maximum equalization", to override behavior
     * 					for a specific block, override the {@link getInternalFlow()} method
     */
    public BlockFiniteFluid(final Material material, final int runniness,final int flowRate, final int internalFlowThreshold, final float density) {
    	super(material);
		viscosity = (RealisticFluids.MAX_FLUID >> runniness);
		setTickRandomly(true); // Because who cares, you know?
		this.flowRate = flowRate;
		canBlockGrass = true;
		this.internalFlowThreshold = internalFlowThreshold;
		this.density = density;
    }

    //////////////////////////////////// STATIC METHOD CALLS //////////////////////////////

    public static int getBlockPressure(final World w, int l)
    {
    	if (l < RealisticFluids.MAX_FLUID) {
			return 1;
		}
    	l -= RealisticFluids.MAX_FLUID;
    	return (int) Math.ceil(((float)l/(float)pressureGain));
    }
    public static int getBlockEffectivePressure(final World w, final BlockFiniteFluid f, final int l)
    {
    	return (int) (f.density * getBlockPressure(w, l));
    }
    public static float getBlockPressureKpa(final World w, final BlockFiniteFluid f, final int l)
    {
    	return  (float) (f.density * 9.80665F * ((float)(l >> 6 << 6)/(float)pressureGain));
    }

    ///////////////////////////////// PROPERTY GETTERS ////////////////////////////////

	/**
	 * Gets the effective viscosity of a fluid block, with additional block and pressure parameters
	 * @param w
	 * @param l0
	 * @param b1
	 * @param l1
	 * @return
	 */
    public int getEffectiveViscosity(final World w, final int l0, final Block b1, final int l1) {
	return (l1 > 0 && blockMaterial == b1.getMaterial()) ? viscosity >> 2 : viscosity;
    }

    /**
     * Gets the minimum amount of fluid that can flow between blocks
     * @param w
     * @param l0
     * @return
     */
    public int getInternalFlow(final World w, final int l0)
    {
    	return internalFlowThreshold;
    }

    public int getFlowRate(final World w) {
    	return flowRate;
    }

    /////////////////////////////// STANDARD VANILLA BLOCK METHODS /////////////////////////////

    @Override
    public void onBlockAdded(final World w, final int x, final int y, final int z) {
    	RealisticFluids.markBlockForUpdate(w, x, y, z);
    	FluidData.setLevel(FluidData.getChunkData(w.getChunkFromChunkCoords(x >> 4, z >> 4)), this, x, y, z, RealisticFluids.MAX_FLUID, true);
    }

    @Override
    public void onNeighborBlockChange(final World w, final int x, final int y, final int z, final Block b) {
    	 if (b.getMaterial() != blockMaterial) {
			RealisticFluids.markBlockForUpdate(w, x, y, z);
		}
    }

    /**
     * Ensure that a block is marked as empty when replaced. Also allow
     * displacement from falling blocks & pistons
     */
    @Override
    public void breakBlock(final World w, final int x, final int y, final int z, final Block b0, final int m) {
		final Block b1 = w.getBlock(x, y, z);

		final ChunkData data = FluidData.getChunkData(w.getChunkFromChunkCoords(x >> 4, z >> 4));

		//FIXME DEGUB FEATURE
		if (b1 == Blocks.redstone_torch)
		{
			final int l = data.getLevel(x & 0xF, y, z & 0xF);
		    System.err.println("Level of block: " + l + ", Meters: " + getBlockPressure(w, l) + ", kPa: " + getBlockPressureKpa(w, this, l));
			w.setBlock(x, y, z, this, Math.max(0, 7 - (l / (RealisticFluids.MAX_FLUID >> 3))), 2);
			data.setLevel(x & 0xF, y, z & 0xF, l);
			//data.unmarkUpdate(x & 0xF, y, z & 0xF);
			return;
		}
		try
		{
		    if (!(b1 instanceof BlockFiniteFluid))
		    {
			// Extreme hacks?
			final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			if (b1 == Blocks.piston_extension
				|| (b1 instanceof BlockFalling
					&& stack[4].getClassName().equals(EntityFallingBlock.class.getName()))
					|| (stack[5].getClassName().equals(BlockPistonBase.class.getName()))) {
				displace(data, this, x, y, z, m, 32);
			}
		    }
		}
		finally
		{
		    data.setLevel(x & 0xF, y, z & 0xF, 0);
		}

    }

    @Override
    public void updateTick(final World w, final int x, final int y, final int z, final Random rand) {
    	RealisticFluids.markBlockForUpdate(w, x, y, z);
    }

    /////////////////////////////////////// THE REAL FUN STUFF ////////////////////////////////////



    public void doUpdate(ChunkData data0, final int x0, final int y0, final int z0, final Random r, final int interval)
    {

		if (flowRate != 1	&& RealisticFluids.tickCounter() % (RealisticFluids.GLOBAL_RATE * getFlowRate(data0.w)) != interval) {
		    data0.markUpdateDelayed(x0 & 0xF, y0, z0 & 0xF);
		    return;
		}

		final int cx0 = x0 & 0xF, cz0 = z0 & 0xF;

		// Our block content
		int l0;
		// ChunkData _data = data = FluidData.forceData(data, x0, z0);
		final int _l0 = l0 = FluidData.getLevel(data0, this, cx0, y0, cz0);

		//If we start out pressurized
		final boolean pressurized = _l0 > RealisticFluids.MAX_FLUID;
		boolean underPressure = false;
		final boolean sameFluid = false;

		try
		{
			int l1, lB, p1, maxNeighbor = RealisticFluids.MAX_FLUID;
		    Block b1, bB;
		    int dx, dy, dz;
			int x1, z1, y1;
			ChunkData data1 = data0;

			 //////////////////////////////// FLOWING DOWN ////////////////////////////////
		    y1 = y0 + (dy = -1);
		    if (y1 < 0 && (l0 = 0) == 0) {
				return;
			}
		    bB = data0.c.getBlock(cx0, y1, cz0);
		    lB = FluidData.getLevel(data0, this, bB, cx0, y1, cz0);

		    if (Util.isSameFluid(this, bB) || bB == Blocks.air)
		    {
				if (lB < RealisticFluids.MAX_FLUID)
		    	{
		    		if (canFlowInto(data0, l0, x0, y1, z0, dy, bB))
		    		{
				    	l0 = Math.min(l0, RealisticFluids.MAX_FLUID);
				    	l1 = lB + l0;
				    	l0 = l1 - RealisticFluids.MAX_FLUID;
				    	FluidData.setLevel(data0, this, x0, y1, z0, l1, true);
				    	if (l0 <= 0) {
							return;
						}
		    		}
		    	}
		    	else if (l0 >= RealisticFluids.MAX_FLUID)
		    	{
		    		p1 = lB - pressureGain - pressureStep;
		    		if (p1 > maxNeighbor)
		    		{
		 		    	maxNeighbor = p1;
						data0.markUpdateImmediate(cx0, y0+1, cz0);
						underPressure = true;
		    		}
		    	}
		    }



		    final int efVisc = getEffectiveViscosity(data0.w, l0, bB, lB);
	    	final int prevLevel = l0;
    	    ////////////////////////////// FLOWING ACROSS ////////////////////////////////
		    final int skew = data0.w.rand.nextInt(4);
	    	for (int i = 0; i < 4; i++)
			{
			    x1 = x0 + (dx = Util.cardinalX(i + skew));
			    z1 = z0 + (dz = Util.cardinalZ(i + skew));
			    data1 = FluidData.forceData(data1, x1, z1);
			    b1 = data1.c.getBlock(x1 & 0xF, y0, z1 & 0xF);

		    	l1 = FluidData.getLevel(data1, this, b1, x1 & 0xF, y0, z1 & 0xF);

		    	//If it is a full block, compare pressures
		    	if (l1 >= RealisticFluids.MAX_FLUID)
		    	{
		    		if (l0 >= RealisticFluids.MAX_FLUID - 1)
		    		{
			    		underPressure = true;
			    		l0 = Math.max(RealisticFluids.MAX_FLUID, l0);
			    		if ((p1 = l1 - pressureStep) > maxNeighbor)
			    		{
			    			l0 = p1;
							maxNeighbor = p1;
							data1.markUpdateImmediate(x1 & 0xF, y0, z1 & 0xF);
						}
			    		else if ((p1 = l0 - pressureStep) > l1)
			    		{
			    			l1 = p1;
			    			FluidData.setLevel(data1, this, x1, y0, z1, p1, false);
			    			data1.markUpdateImmediate(x1 & 0xF, y0, z1 & 0xF);
			    		}
		    		}
		    		else
		    		{
		    			final int[] from = moveToHighPressure(data0, this, x0, y0, z0, l0, 32);
		    			final int[] target = moveToLowFluid(data0, this, x0, y0, z0, l0, 5, true);

		    			if (from[0] != x0 || from[1] != y0 || from[2] != z0)
		    			{
		    				final int canPull = RealisticFluids.MAX_FLUID - l0;
		    				final ChunkData dataN = FluidData.forceData(data0, from[0], from[2]);
		    				int lN = dataN.getLevel(from[0] & 0xF, from[1], from[2] & 0xF);
		    				Block bN = null;
							final Block bM;
		    				bN = dataN.c.getBlock(from[0] & 0xF, from[1] + 1, from[2] & 0xF);
		    				if (!Util.isSameFluid(bN, this)) {
								return;
							}
		    				lN = Math.min(lN, RealisticFluids.MAX_FLUID) - canPull;
		    				FluidData.setLevel(dataN, this, from[0], from[1], from[2], lN, true);
		    				//Don't mark immediate updates for b1
		    				FluidData.setLevel(data0, this, x0, y0, z0, RealisticFluids.MAX_FLUID, false);
		    			}
		    		}
		    	}

		    	else if (isValidFlowTarget(data1, l0, x1, y0, z1, 0, b1) )
		    	{
	    			if (l0 > l1)
		    		{
						final int flow = (Math.min(l0, RealisticFluids.MAX_FLUID) - l1)/2;
						if (l1 + flow >= efVisc && l0 - flow >= efVisc)
						{
							l0 = Math.min(l0, RealisticFluids.MAX_FLUID) - flow;
							FluidData.setLevel(data1, this, x1, y0, z1, l1 + flow, true);
						}
		    		}
				}
			}
			//////////////////////////////////////////////////////////////////////////////


	    	//////////////////////////FLOWING UP - PRESSURE///////////////////////////////

	    	if (l0 >= RealisticFluids.MAX_FLUID)
	    	{
	    		//l0 = maxNeighbor >= prevLevel ? maxNeighbor : RealisticFluids.MAX_FLUID;

	    		//Check above//
		    	y1 = y0 + (dy = 1);
			    Block bA = null;
			    bA = data0.c.getBlock(cx0, y1, cz0);
			    final int lA = FluidData.getLevel(data0, this, bA, cx0, y1, cz0);

		    	if (l0 >= RealisticFluids.MAX_FLUID && y1 <= 255)
		    	{
		    		if (Util.isSameFluid(this, bA))
		    		{
			 		    p1 = lA + pressureGain - pressureStep;
			 		    if (p1 > maxNeighbor)
			 		    {
			 		    	maxNeighbor = p1;
							data0.markUpdateImmediate(cx0, y0-1, cz0);
							underPressure = true;
			 		    }
		    		}
		    		else
		    			if (bA != Blocks.air) bA = null;
		    	}

				//if (maxNeighbor < prevLevel) { l0 = RealisticFluids.MAX_FLUID; return; }
				l0 = maxNeighbor;

				/*
				//Now attempt to propagate pressure change down a couple of blocks?
				int yN = y0, lM; l1 = l0;
				if (l0 != _l0 && Util.isSameFluid(bB, this))
				{
					for (int i = 0; i < 8; i++)
					{
						--yN;
						b1 = data0.c.getBlock(cx0, yN, cz0);
						if (Util.isSameFluid(this, b1) && (lM = FluidData.getLevel(data0, this, b1, cx0, yN, cz0)) > RealisticFluids.MAX_FLUID && l1 + pressureGain - pressureStep > lM)
						{
							data0.setLevel(cx0, yN, cz0, l1 + pressureGain - pressureStep);
							l1 = lM;
							data0.markUpdate(cx0, yN, cz0);
						}
						else break;
					}
				}

				 */
	    		//y1 = y0 + (dy = 1);
		    	if (y1 <= 255 && bA != null && lA < RealisticFluids.MAX_FLUID)
		    	{
		    		int canPush = 0;
		    		if (l0 > RealisticFluids.MAX_FLUID + pressureGain - pressureStep) {
						canPush = RealisticFluids.MAX_FLUID - lA;
					}
		    		/*else if (l0 > RealisticFluids.MAX_FLUID + (pressureGain/2) - pressureStep) {
						canPush = RealisticFluids.MAX_FLUID/2 - lA;
					}*/
		    		if (canPush > 0 && isValidFlowTarget(data0, l0, x0, y1, z0, 1, bA))
		    		{
		    			//Now attempt to find a high pressure x,y,z
		    			final int[] dest = moveToHighPressure(data0, this, x0, y0, z0, l0, 32);
		    			if (dest[0] != x0 || dest[1] != y0 || dest[2] != z0)
		    			{
		    				final ChunkData dataN = FluidData.forceData(data0, dest[0], dest[2]);
		    				int lN = dataN.getLevel(dest[0] & 0xF, dest[1], dest[2] & 0xF);
		    				Block bN = null;
							final Block bM;
		    				bN = dataN.c.getBlock(dest[0] & 0xF, dest[1] + 1, dest[2] & 0xF);
		    				if (!Util.isSameFluid(bN, this)) {
								return;
							}
		    				lN = Math.min(lN, RealisticFluids.MAX_FLUID) - canPush;
		    				FluidData.setLevel(dataN, this, dest[0], dest[1], dest[2], lN, true);
		    				//Don't mark immediate updates for b1
		    				FluidData.setLevel(data0, this, x0, y1, z0, lA + canPush, false);
		    			}
		    			return;
		    		}
		    	}
	    	}
	    	/*
	    	else if (l0 < RealisticFluids.MAX_FLUID)
	    	{
	    		System.out.println("Pulling... " + Util.intStr(x0,y0,z0,l0));
	    		//Now attempt to find a high pressure x,y,z
    			final int[] dest = moveToHighPressure(data0, this, x0, y0, z0, l0, 8, true);

    			if (dest[0] != x0 || dest[1] != y0 || dest[2] != z0)
    			{
    				final int canPull = RealisticFluids.MAX_FLUID - l0;
    				final ChunkData dataN = FluidData.forceData(data0, dest[0], dest[2]);
    				int lN = dataN.getLevel(dest[0] & 0xF, dest[1], dest[2] & 0xF);
    				Block bN = null;
					final Block bM;
    				bN = dataN.c.getBlock(dest[0] & 0xF, dest[1] + 1, dest[2] & 0xF);
    				if (!Util.isSameFluid(bN, this)) {
						return;
					}
    				lN = Math.min(lN, RealisticFluids.MAX_FLUID) - canPull;
    				FluidData.setLevel(dataN, this, dest[0], dest[1], dest[2], lN, true);
    				//Don't mark immediate updates for b1
    				FluidData.setLevel(data0, this, x0, y0, z0, l0 + canPull, false);
    			}


	    	}*/






		}
		finally
		{
		    if (l0 != _l0)
		    {
				data0 = FluidData.forceData(data0, x0, z0);
				//data.markUpdate(cx0, y0, cz0);

				if (l0 >= RealisticFluids.MAX_FLUID	&& _l0 >= RealisticFluids.MAX_FLUID)
					/*
					if (y0 < 255)
						data.markUpdate(cx0, y0 + 1, cz0);
					if (y0 > 0)
						data.markUpdate(cx0, y0 - 1, cz0);
					for (int i = 0; i < 4; i++)
					{
						final int x1 = (x0 + Util.cardinalX(i)), z1 = (z0 + Util.cardinalZ(i));
						data = FluidData.forceData(data, x1, z1);
						data.markUpdate(x1 & 0xF, y0, z1 & 0xF);
					}*/
				{
					data0.setLevel(cx0, y0, cz0, l0);
					data0.markUpdateDelayed(cx0, y0, cz0);

				} else {
					FluidData.setLevelChunk(data0, this, cx0, cz0, x0, y0, z0, l0, true);
				}
		    }
		}

    }

    public static int[] moveToHighPressure(final ChunkData data0, final BlockFiniteFluid f0, final int x0, final int y0, final int z0, final int l0, final int maxSteps)
    {
    	return moveToHighPressure(data0, f0, x0, y0, z0, l0, maxSteps, false);
    }
    /**
     * Moves from a fluid cell to a "pressure origin" within the same fluid (aka, a place with higher water blocks)
     * @param data0
     * @param f0
     * @param x0
     * @param y0
     * @param z0
     * @param l0
     * @param maxSteps
     * @return
     */
    public static int[] moveToHighPressure(final ChunkData data0, final BlockFiniteFluid f0, final int x0, final int y0, final int z0, final int l0, final int maxSteps, final boolean horizontalOnly)
    {
    	int lN = Math.max(RealisticFluids.MAX_FLUID, l0);
		int dy, xN, yN, zN, xT = xN = x0, yT = yN = y0, zT = zN = z0;
		ChunkData dataN, dataT = dataN = data0;
		Block b1;
		for (int steps = 0; steps < maxSteps; steps++)
		{
			int lT = lN, highDir = -1;
			for (int dir = 0; dir < 6; dir++)
			{
				if (horizontalOnly && (dir == 0 || dir == 5)) {
					continue;
				}
				xT = xN + Util.intFaceX(dir);
				zT = zN + Util.intFaceZ(dir);
				yT = yN + (dy = Util.intFaceY(dir));
				if ((dataT = FluidData.testData(dataT, xT, zT))== null) {
					break;
				}
				b1 = dataT.c.getBlock(xT & 0xF, yT, zT & 0xF);
				if (!Util.isSameFluid(f0, b1)) {
					continue;
				}
				lT = FluidData.getLevel(dataT, f0, b1, xT & 0xF, yT, zT & 0xF);
				if (lT > lN)
				{
					highDir = dir;
					//dataN = dataT;
					lN = lT;
				}
			}
			if (highDir == -1) {
				break;
			}
			xN = xN + Util.intFaceX(highDir);
			yN = yN + Util.intFaceY(highDir);
			zN = zN + Util.intFaceZ(highDir);
		}
		//We've moved, pull some water from the block we found and put it above
    	return new int[]{xN, yN, zN};
    }


    /**
     * Moves from a fluid cell to a "pressure origin" within the same fluid (aka, a place with higher water blocks)
     * @param data0
     * @param f0
     * @param x0
     * @param y0
     * @param z0
     * @param l0
     * @param maxSteps
     * @return
     */
    public static int[] moveToLowFluid(final ChunkData data0, final BlockFiniteFluid f0, final int x0, final int y0, final int z0, final int l0, final int maxSteps, final boolean horizontalOnly)
    {
    	int lN = l0;
		int dy, xN, yN, zN, xT = xN = x0, yT = yN = y0, zT = zN = z0;
		ChunkData dataN, dataT = dataN = data0;
		Block b1;
		for (int steps = 0; steps < maxSteps; steps++)
		{
			int lT = lN, highDir = -1;
			for (int dir = 0; dir < 6; dir++)
			{
				if (horizontalOnly && (dir == 0 || dir == 5)) {
					continue;
				}
				xT = xN + Util.intFaceX(dir);
				zT = zN + Util.intFaceZ(dir);
				yT = yN + (dy = Util.intFaceY(dir));
				if ((dataT = FluidData.testData(dataT, xT, zT)) == null) {
					break;
				}
				b1 = dataT.c.getBlock(xT & 0xF, yT, zT & 0xF);
				if (b1 != Blocks.air && !Util.isSameFluid(f0, b1)) {
					continue;
				}
				lT = FluidData.getLevel(dataT, f0, b1, xT & 0xF, yT, zT & 0xF);
				if (lT < lN)
				{
					highDir = dir;
					lN = lT;
				}
			}
			if (highDir == -1) {
				break;
			}
			xN = xN + Util.intFaceX(highDir);
			yN = yN + Util.intFaceY(highDir);
			zN = zN + Util.intFaceZ(highDir);
		}
		//We've moved, pull some water from the block we found and put it above
    	return new int[]{xN, yN, zN};
    }


    /**
     * Attempts to displace water by searching for a space above. The algorithm
     * moves upwards trying to find a space.
     *
     * @param data
     * @param x
     * @param y
     * @param z
     */
    public static void displace(final ChunkData data, final BlockFiniteFluid f, final int x, final int y, final int z, final int m, final int maxOutHeight)
    {
    	//FIXME rewrite displacement it was shit
    }

    ////////////////////////////////// FLOW TESTS /////////////////////////////////////

    public boolean isValidFlowTarget(final ChunkData data0, final int l0, final int x0, final int y0, final int z0, final int dy, final Block b1)
    {
    	if (Util.isSameFluid(this, b1) || b1 == Blocks.air) {
			return true;
		}
    	if (canBreak(b1))
    	{
		    if (dy < 0 || l0 > flowBreak)
		    {
		    	int m;
		    	b1.dropBlockAsItem(data0.w, x0, y0, z0, m = data0.c.getBlockMetadata(x0 & 0xF, y0, z0 & 0xF), m);
			   	RealisticFluids.setBlock(data0.w, x0, y0, z0, Blocks.air, 0, 0);
			   	return true;
		    }
		    return false;
		}
    	return false;
    }

    public boolean canFlowInto(final ChunkData data0, final int l0, final int x0, final int y0, final int z0, final int dy, final Block b1)
    {
    	if (canBreak(b1))
    	{
		    if (dy < 0 || l0 > flowBreak)
		    {
		    	int m;
		    	b1.dropBlockAsItem(data0.w, x0, y0, z0, m = data0.c.getBlockMetadata(x0 & 0xF, y0, z0 & 0xF), m);
			   	RealisticFluids.setBlock(data0.w, x0, y0, z0, Blocks.air, 0, 0);
			   	return true;
		    }
		    return false;
		}
    	return false;
    }

    public static boolean canBreak(final Block b)
    {
    	return (b == Blocks.wooden_door || b == Blocks.iron_door || b == Blocks.standing_sign || b == Blocks.ladder || b == Blocks.reeds)
    			? false
    			: (!b.getMaterial().blocksMovement() && b.getMaterial() != Material.portal);
    }

    @Override
    public void velocityToAddToEntity(final World w, final int x, int y, final int z, final Entity e, final Vec3 vec)
    {
		if (e instanceof EntityWaterMob) {
			return;
		}

		// Copy the flow of the above blocks
		final Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
		int i; for (i = 0; i < 8 && Util.isSameFluid(this, c.getBlock(x & 0xF, y + 1, z & 0xF)); i++) {
			y++;
		}

		// Scale with depth (lots of water washes you away lol)
		final double d = (i / 2.D) + 0.8D;
		final Vec3 vec1 = getFlowVector(w, this, x, y, z);

		vec.xCoord += vec1.xCoord * d;
		vec.yCoord += vec1.yCoord * d;
		vec.zCoord += vec1.zCoord * d;
    }

    private static Vec3 getFlowVector(final World w, final BlockFiniteFluid f, final int x, final int y, final int z)
    {
		Vec3 vec3 = Vec3.createVectorHelper(0.0D, 0.0D, 0.0D);
		final int l = w.getBlockMetadata(x, y, z);
		int x1, z1;
		for (int i = 0; i < 4; ++i)
		{
		    x1 = x + Util.intDirX(i);
		    z1 = z + Util.intDirZ(i);
		    int l1 = getFlowDecay(w, f, x1, y, z1);
		    int i2;
		    if (l1 < 0)
		    {
				if (!w.getBlock(x1, y, z1).getMaterial().blocksMovement())
				{
				    l1 = getFlowDecay(w, f, x1, y - 1, z1);
				    if (l1 >= 0)
				    {
					i2 = l1 - (l - 8);
					vec3 = vec3.addVector(Util.intDirX(i) * i2, (y - y) * i2, Util.intDirZ(i) * i2);
				    }
				}
		    } else if (l1 >= 0)
		    {
		    	i2 = l1 - l;
		    	vec3 = vec3.addVector(Util.intDirX(i) * i2, (y - y) * i2,Util.intDirZ(i) * i2);
		    }
		}
		return vec3.normalize();
    }

    public static int getFlowDecay(final World w, final BlockFiniteFluid f, final int x0, final int y0, final int z0)
    {
    	 if (w.getBlock(x0, y0, z0).getMaterial() != f.blockMaterial) {
			return -1;
		} else {
			return w.getBlockMetadata(x0, y0, z0);
		}
    }



    // Because bad stuff seems to be happening when these methods are not
    // present... they should be inherited, but apparently not D:
    @Override
    @SideOnly(Side.CLIENT)
    public boolean getCanBlockGrass() {
	return false;
    }

    @Override
    public Block setHardness(final float f) {
	return super.setHardness(f);
    }

    public Block c(final float f) {
	blockHardness = f;
	return this;
    }

    @Override
    public Block setTickRandomly(final boolean ticks) {
	return super.setTickRandomly(ticks);
    }

    @Override
    public Block setLightOpacity(final int o) {
	return super.setLightOpacity(o);
    }

    @Override
    public Block setBlockName(final String name) {
	return super.setBlockName(name);
    }

    @Override
    public Block setLightLevel(final float f) {
	return super.setLightLevel(f);
    }

    @Override
    public Block setBlockTextureName(final String tex) {
	return super.setBlockTextureName(tex);
    }

    @Override
    public Block disableStats() {
	return super.disableStats();
    }

}
