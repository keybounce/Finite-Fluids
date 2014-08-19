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
    public static final int fullBlockPressure = RealisticFluids.MAX_FLUID;

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
    public BlockFiniteFluid(final Material material, final int runniness,final int flowRate, final int internalFlowThreshold) {
    	super(material);
		viscosity = (RealisticFluids.MAX_FLUID >> runniness);
		setTickRandomly(true); // Because who cares, you know?
		this.flowRate = flowRate;
		canBlockGrass = true;
		this.internalFlowThreshold = internalFlowThreshold;
    }

    @Override
    public void onBlockAdded(final World w, final int x, final int y, final int z) {
    	RealisticFluids.markBlockForUpdate(w, x, y, z);
    	FluidData.setLevel(FluidData.getChunkData(w.getChunkFromChunkCoords(x >> 4, z >> 4)), this, x, y, z, RealisticFluids.MAX_FLUID, true);
    }

    @Override
    public void onNeighborBlockChange(final World w, final int x, final int y, final int z, final Block b) {
    	// if (!isSameFluid(this, b))
    	RealisticFluids.markBlockForUpdate(w, x, y, z);
    }

    /**
     * Ensure that a block is marked as empty when replaced. Also allow
     * displacement from falling blocks & pistons
     */
    @Override
    public void breakBlock(final World w, final int x, final int y, final int z, final Block b0, final int m) {
		final Block b1 = w.getBlock(x, y, z);

		final ChunkData data = FluidData.getChunkData(w.getChunkFromChunkCoords(x >> 4, z >> 4));
		if (b1 == Blocks.redstone_torch)
		{
			final int l = data.getLevel(x & 0xF, y, z & 0xF);
		    System.err.println("Level of block: " + l + ", Meters: " + Math.ceil(((float)l/(float)RealisticFluids.MAX_FLUID)) + ", kPa: " + (9.807F * ((float)l/(float)RealisticFluids.MAX_FLUID)));
			w.setBlock(x, y, z, this);
			data.setLevel(x & 0xF, y, z & 0xF, l);
			w.setBlockMetadataWithNotify(x, y, z, Math.max(0, 7 - (l / (RealisticFluids.MAX_FLUID >> 3))), 2);
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
					|| (stack[5].getClassName().equals(BlockPistonBase.class.getName())))
			    displace(data, x, y, z, m, 32);
		    }
		}
		finally
		{
		    data.setLevel(x & 0xF, y, z & 0xF, 0);
		}

    }

    @Override
    public void updateTick(final World w, final int x, final int y,
	    final int z, final Random rand) {
	RealisticFluids.markBlockForUpdate(w, x, y, z);
    }
	/**
	 * Gets the effective viscosity of a fluid block, with additional block and pressure parameters
	 * @param w
	 * @param l0
	 * @param b1
	 * @param l1
	 * @return
	 */
    public int getEffectiveViscosity(final World w, final int l0, final Block b1, final int l1) {
	return (l1 > 0 && blockMaterial == b1.getMaterial()) ? Math.max(1,
		viscosity >> 15) : viscosity;
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

    public void doUpdate(ChunkData data, final int x0, final int y0, final int z0, final Random r, final int interval)
    {

		if (flowRate != 1	&& RealisticFluids.tickCounter() % (RealisticFluids.GLOBAL_RATE * getFlowRate(data.w)) != interval) {
		    data.markUpdate(x0 & 0xF, y0, z0 & 0xF);
		    return;
		}

		int l0, p0;
		final int cx0 = x0 & 0xF, cz0 = z0 & 0xF;
		ChunkData _data = data = FluidData.forceData(data, x0, z0);
		final int _l0 = l0 = FluidData.getLevel(data, this, cx0, y0, cz0);

		try
		{
		    Block b1; int l1;
		    int dx, dy, dz;
			int x1, z1, y1;

			if (l0 >= RealisticFluids.MAX_FLUID - 1)
	    	{
	    		l0 = RealisticFluids.MAX_FLUID;
	    		for (int i = 0; i < 6; i++)
				{
				    x1 = x0 + Util.intFaceX(i);
				    z1 = z0 + Util.intFaceZ(i);
				    y1 = y0 + (dy = Util.intFaceY(i));
				    // Ensure we do not flow out of the world
				    if (y1 > 255) continue;
				    if (y1 < 0 && (l0 = 0) == 0) return;

				    _data = FluidData.forceData(_data, x1, z1);

				    b1 = _data.c.getBlock(x1 & 0xF, y1, z1 & 0xF);
				    if (!Util.isSameFluid(this, b1)) continue;

				    l1 = FluidData.getLevel(_data, this, b1, x1 & 0xF, y1, z1 & 0xF);
				    if ((p0 = l1 + dy * fullBlockPressure - pressureStep) > l0) l0 = p0;
				}
	    	}

		    y1 = y0 + (dy = -1);
		    if (y1 < 0 && (l0 = 0) == 0) return;

		    b1 = data.c.getBlock(cx0, y1, cz0);
		    l1 = FluidData.getLevel(data, this, b1, cx0, y1, cz0);

		    //////////////////////////////// FLOWING DOWN ////////////////////////////////

	    	if (l1 < RealisticFluids.MAX_FLUID && canFlow(data, l0, x0, y1, z0, dy, b1))
			{
		    	l0 = Math.min(l0, RealisticFluids.MAX_FLUID);
		    	l1 = l1 + l0;
		    	l0 = l1 - RealisticFluids.MAX_FLUID;
		    	FluidData.setLevel(data, this, x0, y1, z0, l1, true);
		    	if (l0 <= 0) return;
		    }
	    	//////////////////////////////////////////////////////////////////////////////

    	    final int efVisc = getEffectiveViscosity(data.w, l0, b1, l1);

    	    ////////////////////////////// FLOWING ACROSS ////////////////////////////////
		    final int skew = data.w.rand.nextInt(4);
	    	for (int i = 0; i < 4; i++)
			{
			    x1 = x0 + (dx = Util.cardinalX(i + skew));
			    z1 = z0 + (dz = Util.cardinalZ(i + skew));

			    _data = FluidData.forceData(_data, x1, z1);
			    b1 = _data.c.getBlock(x1 & 0xF, y0, z1 & 0xF);
			    l1 = FluidData.getLevel(_data, this, b1, x1 & 0xF, y0, z1 & 0xF);
			    if (l0 > l1 && l1 < RealisticFluids.MAX_FLUID)
					if (canFlow(_data, l0, x1, y0, z1, 0, b1) )
					{
						final int flow = (Math.min(l0, RealisticFluids.MAX_FLUID) - l1)/2;
					    if (flow > getInternalFlow(_data.w, _l0) && l1 + flow >= efVisc && l0 - flow >= efVisc)
					    {
					    	l0 = Math.min(l0, RealisticFluids.MAX_FLUID) - flow;
							FluidData.setLevel(_data, this, x1, y0, z1, l1 + flow, true);
					    }
					}
			}
			//////////////////////////////////////////////////////////////////////////////

	    	//////////////////////////FLOWING UP - PRESSURE///////////////////////////////
	    	y1 = y0 + (dy = 1);
	    	if ( y1 <= 255 && l0 > RealisticFluids.MAX_FLUID)
	    	{
	    		//Get the plevel from above
	    		b1 = data.c.getBlock(cx0, y1, cz0);
	    		l1 = FluidData.getLevel(data, this, b1, cx0, y1, cz0);

	    		if (l1 < RealisticFluids.MAX_FLUID && canFlow(data, l0, x0, y1, z0, dy, b1) )
	    		{
	    			FluidData.setLevelChunk(data, this, cx0, cz0, x0, y1, z0, Math.min(RealisticFluids.MAX_FLUID, l0), true);
	    			int lN = (l0 = l1);

	    			//////////////////////////////////////// PRESSURE PULL ///////////////////////////////////////////////
					int xN, yN, zN, xT = xN = x0, yT = yN = y0, zT = zN = y0;
	    			ChunkData dataN, dataT = dataN = data;
	    			for (int steps = 0; steps < 16; steps++)
	    			{
	    				int lT = lN, highDir = -1;
	    				for (int dir = 0; dir < 6; dir++)
	    				{
	    					xT = xN + Util.intFaceX(dir);
	    					zT = zN + Util.intFaceY(dir);
	    					yT = yN + (dy = Util.intFaceY(dir));
	    					dataT = FluidData.forceData(dataN, xT, zT);
	    					lT = dataT.getLevel(xT & 0xF, yT, zT & 0xF) + fullBlockPressure*dy - pressureStep;
	    					b1 = dataT.c.getBlock(xT & 0xF, yT, zT & 0xF);

	    					if (lT > lN && Util.isSameFluid(this, b1))
	    					{
	    						highDir = dir;
	    						dataN = dataT;
	    						lN = lT;
	    					}
	    				}
	    				if (highDir == -1) break;
	    				xN = xN + Util.intFaceX(highDir);
	    				yN = yN + Util.intFaceY(highDir);
						zN = zN + Util.intFaceY(highDir);
	    			}
	    			//We've moved, pull some water from the block we found and put it above
	    			if (yN != y0 || xN != x0 || zN != z0)
	    			{
	    				lN = Math.min(RealisticFluids.MAX_FLUID, lN);
	    				final int flow = Math.min(lN, RealisticFluids.MAX_FLUID - l0);
	    				FluidData.setLevel(dataN, this, xN, yN, zN, lN - flow, true);
	    				l0 += flow;
	    				if (l0 >= RealisticFluids.MAX_FLUID) l0 = _l0;
	    			}
	    			///////////////////////////////////////////////////////////////////////////////////////////////

	    		}
    		}
	    	//////////////////////////////////////////////////////////////////////////////
		}
		finally
		{
		    if (l0 != _l0)
		    {
				data = FluidData.forceData(data, x0, z0);
				data.markUpdate(x0 & 0xF, y0, z0 & 0xF);
				if (l0 >= RealisticFluids.MAX_FLUID	&& _l0 >= RealisticFluids.MAX_FLUID)
				{
					if (y0 < 255)
						data.markUpdateImmediate(cx0, y0 + 1, cz0);
					if (y0 > 0)
						data.markUpdate(cx0, y0 - 1, cz0);
					for (int i = 0; i < 4; i++)
					{
						final int x1 = (x0 + Util.cardinalX(i)), z1 = (z0 + Util.cardinalZ(i));
						data = FluidData.forceData(data, x1, z1);
						data.markUpdate(x1 & 0xF, y0, z1 & 0xF);
					}
					data.setLevel(cx0, y0, cz0, l0);
				}
				else
					FluidData.setLevelChunk(data, this, cx0, cz0, x0, y0, z0, l0, true);
		    }
		}

    }

    public boolean doDoubleFlow(final ChunkData data, final int x0, final int y0, final int z0, final int x1, final int y1, final int z1) {
		final Block b1 = data.w.getBlock(x1, y1, z1);
		if (b1 == Blocks.air || Util.isSameFluid(this, b1))
		{
		    data.markUpdate(x0 & 0xF, y0, z0 & 0xF);
		    return true;
		}
		return false;
    }

    public boolean canBreak(final Block b)
    {
    	return (b == Blocks.wooden_door || b == Blocks.iron_door || b == Blocks.standing_sign || b == Blocks.ladder || b == Blocks.reeds)
    			? false
    			: (!b.getMaterial().blocksMovement() && b.getMaterial() != Material.portal);
    }

    @Override
    public void velocityToAddToEntity(final World w, final int x, int y, final int z, final Entity e, final Vec3 vec)
    {
		if (e instanceof EntityWaterMob) return;

		// Copy the flow of the above blocks
		final Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
		int i; for (i = 0; i < 8 && Util.isSameFluid(this, c.getBlock(x & 0xF, y + 1, z & 0xF)); i++) y++;

		// Scale with depth (lots of water washes you away lol)
		final double d = (i / 2.D) + 0.8D;
		final Vec3 vec1 = getFlowVector(w, x, y, z);

		vec.xCoord += vec1.xCoord * d;
		vec.yCoord += vec1.yCoord * d;
		vec.zCoord += vec1.zCoord * d;
    }

    private Vec3 getFlowVector(final World w, final int x, final int y, final int z)
    {
		Vec3 vec3 = Vec3.createVectorHelper(0.0D, 0.0D, 0.0D);
		final int l = w.getBlockMetadata(x, y, z);
		int x1, z1;
		for (int i = 0; i < 4; ++i)
		{
		    x1 = x + Util.intDirX(i);
		    z1 = z + Util.intDirZ(i);
		    int l1 = getEffectiveFlowDecay(w, x1, y, z1);
		    int i2;
		    if (l1 < 0)
		    {
				if (!w.getBlock(x1, y, z1).getMaterial().blocksMovement())
				{
				    l1 = getEffectiveFlowDecay(w, x1, y - 1, z1);
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


    /**
     * Attempts to displace water by searching for a space above. The algorithm
     * moves upwards trying to find a space.
     *
     * @param data
     * @param x
     * @param y
     * @param z
     */
    public void displace(final ChunkData data, final int x, final int y, final int z, final int m, final int maxOutHeight)
    {
    	//FIXME rewrite displacement it was shit
    }

    // ////////////////////////////////INTERACTIONS ////////////////////////////

    public boolean canFlow(final ChunkData data0, final int l0, final int x0, final int y0, final int z0, final int dy, final Block b1)
    {
    	if (Util.isSameFluid(this, b1) || b1 == Blocks.air) return true;
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

    /**
     * Handles interaction of lava and water. 0 = water, 1 = lava
     *
     * @param w
     * @param xw
     * @param yw
     * @param zw
     * @param lw
     * @param xl
     * @param yl
     * @param zl
     * @param ll
     */
    public void lavaWaterInteraction(final ChunkData data, final int xw,
	    final int yw, final int zw, final int lw, final int xl,
	    final int yl, final int zl, final int ll) {
	final ChunkData data1 = FluidData.forceData(data, xl, zl);

	if (yl - yw > 0) // Lava flows down into water
	{
	    if (ll > (RealisticFluids.MAX_FLUID - (RealisticFluids.MAX_FLUID / 3))) {
		FluidData.setLevel(data,
			(BlockFiniteFluid) Blocks.flowing_water, xw, yw, zw, 0,
			true);
		FluidData.setLevel(data1,
			(BlockFiniteFluid) Blocks.flowing_lava, xl, yl, zl, 0,
			true);
		RealisticFluids.setBlock(data.w, xw, yw, zw, Blocks.obsidian,
			0, 3, true);
		return;
	    } else {
		FluidData.setLevel(data,
			(BlockFiniteFluid) Blocks.flowing_water, xw, yw, zw, 0,
			true);
		FluidData.setLevel(data1,
			(BlockFiniteFluid) Blocks.flowing_lava, xl, yl, zl, 0,
			true);
		RealisticFluids.setBlock(data.w, xw, yw, zw, Blocks.stone, 0,
			3, true);
		return;
	    }
	} else if (ll > (RealisticFluids.MAX_FLUID - (RealisticFluids.MAX_FLUID / 3))) {
	    FluidData.setLevel(data,
		    (BlockFiniteFluid) Blocks.flowing_water, xw, yw, zw, 0,
		    true);
	    FluidData
	    .setLevel(data1,
		    (BlockFiniteFluid) Blocks.flowing_lava, xl, yl, zl,
		    0, true);
	    RealisticFluids.setBlock(data.w, xl, yl, zl, Blocks.obsidian, 0, 3,
		    true);
	    return;
	} else {
	    FluidData.setLevel(data,
		    (BlockFiniteFluid) Blocks.flowing_water, xw, yw, zw, 0,
		    true);
	    FluidData
	    .setLevel(data1,
		    (BlockFiniteFluid) Blocks.flowing_lava, xl, yl, zl,
		    0, true);
	    RealisticFluids.setBlock(data.w, xl, yl, zl, Blocks.cobblestone, 0,
		    3, true);
	    return;
	}
	// Lower the levels of the fluids
	// FluidData.setLevelWorld(data, (BlockFiniteFluid)
	// Blocks.flowing_water, xw, yw, zw, lw - (2 * ll) / 3, false);
	// FluidData.setLevelWorld(data1, (BlockFiniteFluid)
	// Blocks.flowing_lava, xl, yl, zl, ll - (3 * lw) / 2, false);
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
