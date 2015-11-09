package com.mcfht.realisticfluids.fluids;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockFalling;
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
public class BlockFiniteFluid extends BlockDynamicLiquid
{
    /** Tendency of this liquid to flow */
    public final int	viscosity;
    /** Rate at which this liquid flows */
    public int			flowRate;
    /** Amount of fluid needed to break things */
    public final int	flowBreak	= RealisticFluids.MAX_FLUID >> 3;

    // Debug!
    static int minwater = RealisticFluids.MAX_FLUID;
    static int minlava = RealisticFluids.MAX_FLUID;
    
    static int minwaterledge = RealisticFluids.MAX_FLUID;
    static int minlavaledge = RealisticFluids.MAX_FLUID;

    /**
     * Initialize a new fluid.
     * 
     * @param material
     *            => Water or lava (others will work, but interactions may be
     *            unreliable)
     * @param runniness
     *            => Factor of flow. Water = 4, Lava = 3.
     * @param flowRate
     *            => How often to update this block (every N sweeping updates =
     *            n*5 ticks)
     */
    public BlockFiniteFluid(final Material material, final int runniness, final int flowRate)
    {
        super(material);
        this.viscosity = (RealisticFluids.MAX_FLUID >> runniness);
        this.setTickRandomly(true); // Because who cares, you know?
        this.flowRate = flowRate;
        this.canBlockGrass = false;
    }

    @Override
    public void onBlockAdded(final World w, final int x, final int y, final int z)
    {
        // Old code (broken):
        // RealisticFluids.markBlockForUpdate(w, x, y, z);
        // FluidData.setLevelWorld(FluidData.getChunkData(w.getChunkFromChunkCoords(x >> 4, z >> 4)), this, x, y, z,
        //        RealisticFluids.MAX_FLUID, true);
        //
        // Problem: we can be called after a water block (any mod water) is placed into the world.
        //
        // We have to first call the vanilla water onBlockAdded (it's super()), to let it do any
        // special interactions for water placement (lava? Nether prohibitation?).
        // Then, we have to see if the block that is finally there at the end is a realistic fluid block.
        // If so, then we can do our stuff.

        super.onBlockAdded(w, x, y, z);
        final Block b1 = w.getBlock(x, y, z);
        if (b1 == this)
        {
            RealisticFluids.markBlockForUpdate(w, x, y, z);
            FluidData.setLevelWorld(FluidData.getChunkData(w.getChunkFromChunkCoords(x >> 4, z >> 4)),
                                this, x, y, z, RealisticFluids.MAX_FLUID, true);
        }

    }

    @Override
    public void onNeighborBlockChange(final World w, final int x, final int y, final int z, final Block b)
    {
        // if (!isSameFluid(this, b))
        RealisticFluids.markBlockForUpdate(w, x, y, z);
    }

    /**
     * Ensure that a block is marked as empty when replaced. Also allow
     * displacement from falling blocks & pistons
     */
    @Override
    public void breakBlock(final World w, final int x, final int y, final int z, final Block b0, final int m)
    {
        final Block b1 = w.getBlock(x, y, z);
        final ChunkData data = FluidData.getChunkData(w.getChunkFromChunkCoords(x >> 4, z >> 4));
        try
        {
            if (!(b1 instanceof BlockFiniteFluid))
            {
                // Extreme hacks?
                final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                if (b1 == Blocks.piston_extension
                        || (b1 instanceof BlockFalling && stack[4].getClassName().equals(EntityFallingBlock.class.getName()))
                        || (stack[5].getClassName().equals(BlockPistonBase.class.getName())))
                    this.displace(data, x, y, z, m, 32);
            }
        } finally
        {
            data.setLevel(x & 0xF, y, z & 0xF, 0);
        }

    }

    @Override
    public void updateTick(final World w, final int x, final int y, final int z, final Random rand)
    {
        RealisticFluids.markBlockForUpdate(w, x, y, z);
    }

    public int getEffectiveViscosity(final World w, final Block b1, final int l1)
    {
        return (l1 > 0 && this.blockMaterial == b1.getMaterial()) ? Math.max(1, this.viscosity >> 15) : this.viscosity;
    }
    public int getFlowRate(final World w)
    {
        return this.flowRate;
    }
    public void doUpdate(ChunkData data, final int x0, final int y0, final int z0, final Random r, final int interval)
    {
        if (this.flowRate != 1 && RealisticFluids.tickCounter() % (RealisticFluids.GLOBAL_RATE * this.getFlowRate(data.w)) != interval)
        {
            // Mark ourselves for later
            RealisticFluids.markBlockForUpdate(data.w, x0, y0, z0);
            return;
        }

        data = FluidData.forceCurrentChunkData(data, x0, z0);
        int l0 = FluidData.getLevel(data, this, x0 & 0xF, y0, z0 & 0xF);
        int l1=l0;  // Normally reset below. But if y0=0, this is needed.
        final int _l0 = l0;
        try
        {
            // First, try to flow downwards
            int y1 = y0 - 1;
            // Ensure we do not flow out of the world
            if (y1 < 0)
                return; //  ** Todo: We need to flow sideways. But the code has 
                        //  horrible assumptions, and constantly refers to results from
                        // the block underneath us. So, for now, pretend it's "do nothing".
            if (y1 > 255)
            {       // This should only happen ... ?!??! Never???
                // FluidData.setLevelWorld(data, this, x0, y0, z0, 0, true);
                return;
            }

            // Now check if we can flow into the block below, etcetera
            Block b1 = data.c.getBlock(x0 & 0xF, y1, z0 & 0xF);
            l1 = FluidData.getLevel(data, this, x0 & 0xF, y1, z0 & 0xF);
            // Emulate surface tension for water and lava
            byte flowResult = this.checkFlow(data, x0, y0, z0, 0, -1, 0, b1, data.c.getBlockMetadata(x0 & 0xF, y1, z0 & 0xF), l0);

            // DEBUG: Track what the flow levels are / what absorption should be.
            if (0 == flowResult)
            {
                if (this.getMaterial() == b1.getMaterial())
                {
                    if (Material.lava == this.getMaterial())
                    {
                        minlava = adjustMinFluid(minlava, "lava", _l0);
                    } else {
                        minwater = Math.min(RealisticFluids.MAX_FLUID,
                                        adjustMinFluid(minwater, "water", _l0));
                    }
                } else { // TODO visible mark on the scrollbar
                    if (Material.lava == this.getMaterial())
                    {
                        minlavaledge = adjustMinFluid(minlavaledge, "lava ledge", _l0);
                    } else {
                        minwaterledge = Math.min(RealisticFluids.MAX_FLUID,
                                        adjustMinFluid(minwaterledge, "water ledge", _l0));
                    }
                    
                }
            }
            
            if (flowResult != 0 && y0 > 0) // do not flow downward at y=0
            {
                if (flowResult > 1)
                {
                    y1 = y0 - flowResult;
                    l1 = FluidData.getLevel(data, this, x0 & 0xF, y1, z0 & 0xF);
                }
                if (l1 < RealisticFluids.MAX_FLUID)
                {
                    // See if we can take water from higher up to save updates?
                    int k, lN = 0, _lN;
                    for (k = 1; k < 16; k++)
                    {
                        _lN = FluidData.getLevel(data, this, x0 & 0xF, y0 + k, z0 & 0xF);
                        if (_lN < RealisticFluids.MAX_FLUID)
                        {
                            k--;
                            break;
                        }
                        lN = _lN;
                        continue;
                    }

                    if (lN >= RealisticFluids.MAX_FLUID)
                    {
                        // Pull the water down
                        FluidData.setLevel(data, this, x0 & 0xF, z0 & 0xF, x0, y0 + k, z0, l1, true);
                        FluidData.setLevelWorld(data, this, x0, y1, z0, RealisticFluids.MAX_FLUID, true);
                    } else
                    {
                        // Flow down
                        l0 = l0 + l1 - RealisticFluids.MAX_FLUID;
                        FluidData.setLevelWorld(data, this, x0, y1, z0, _l0 + l1, true);
                    }

                }
            }

            if (l0 <= 0)
            {
                FluidData.markNeighborsDiagonal(data, x0, y0, z0);
                return;
            }

            final int efVisc = this.getEffectiveViscosity(data.w, b1, l1);

            boolean flag = false;
            // Block _b1 = b1;
            if (l0 < efVisc << 1) // Since 2 blocks SHARE the content!!!
                flag = true;

            int dx, dz;
            int x1, z1;
            final int skew = r.nextInt(8);
            boolean diag = false;

            // Try to flow horizontally
            for (int i = 0; i < 8; i++)
            {
                dx = Util.intDirX(i + skew);
                dz = Util.intDirZ(i + skew);
                diag = (dx != 0 && dz != 0);

                x1 = x0 + dx;
                z1 = z0 + dz;

                data = FluidData.forceCurrentChunkData(data, x1, z1);
                l1 = FluidData.getLevel(data, this, x1 & 0xF, y0, z1 & 0xF);
                b1 = data.c.getBlock(x1 & 0xF, y0, z1 & 0xF);
                flowResult = this.checkFlow(data, x0, y0, z0, dx, 0, dz, b1, data.c.getBlockMetadata(x1 & 0xF, y0, z1 & 0xF), l0);

                if (flowResult != 0)
                    if (!flag)
                    {
                        if (flowResult > 1)
                        {
                            x1 = x0 + flowResult * dx;
                            z1 = z0 + flowResult * dz;
                            data = FluidData.forceCurrentChunkData(data, x1, z1);
                            l1 = FluidData.getLevel(data, this, x1 & 0xF, y0, z1 & 0xF);
                        }
                        if (l0 > l1)
                        {
                            int flow = (l0 - l1) / 2;
                            if (diag)
                                flow -= flow / 3;
                            if (flow >= 4 && l0 - flow >= efVisc && l1 + flow >= efVisc)
                            {
                                l0 -= flow;
                                FluidData.setLevel(data, this, x1 & 0xF, z1 & 0xF, x1, y0, z1, l1 + flow, true);
                                if (l0 < (efVisc >> 2))
                                {
                                    data = FluidData.forceCurrentChunkData(data, x0, z0);
                                    data.markUpdate(x0 & 0xF, y0, z0 & 0xF);
                                    return; // Fix problems with edge stuffs
                                }
                            }
                        }
                    } else // Prevent water from getting stuck on ledges
                    if (FluidData.getLevel(data, this, x0 & 0xF, y0 - 1, z0 & 0xF) == 0)
                    {
                        final Block b2 = data.c.getBlock(x1 & 0xF, y0 - 1, z1 & 0xF);
                        if ((b1 == Blocks.air || (l1 > 0 && l1 + l0 < RealisticFluids.MAX_FLUID))
                                && (b2 == Blocks.air || b2.getMaterial() == this.blockMaterial))
                        {
                            FluidData.setLevelWorld(data, this, x1, y0, z1, l1 + l0, true);
                            FluidData.markNeighborsDiagonal(data, x1, y0, z1);
                            l0 = 0;
                            return;
                        }
                    }
            }
        } finally
        {
            if (l0 != _l0)
            {
                data = FluidData.forceCurrentChunkData(data, x0, z0);
                // Only flag updates if a significant fuid volume passes?
                // Math.abs(l0 - _l0) > RealisticFluids.MAX_FLUID >> 10
                FluidData.setLevelWorld(data, this, x0, y0, z0, l0, true);
                // Overkill?
                data.markUpdate(x0 & 0xF, y0, z0 & 0xF);
            }
        }
    }

    private int adjustMinFluid(int currentMin, String string, int test) // FIXME Breakpoint
    {
        if (test < 5)
            test = test;    // TODO mark eclipse for breakpoint
        if (test > currentMin)
            return currentMin + 1;
//        System.out.println("Min: " + string + " old " + currentMin + " new " + test +
//                " Divisor: " + String.valueOf((float)RealisticFluids.MAX_FLUID / test));
        return Math.min(currentMin + 50, (int)(test + 50));    // Does not give "min", but some fluctuation. Testing.
    }

    public boolean doDoubleFlow(final ChunkData data, final int x0, final int y0, final int z0, final int x1, final int y1, final int z1)
    {
        final Block b1 = data.w.getBlock(x1, y1, z1);
        if (b1 == Blocks.air || Util.isSameFluid(this, b1))
        {
            data.markUpdate(x0 & 0xF, y0, z0 & 0xF);
            return true;
        }
        return false;
    }
    /**
     * Returns the number of spaces that can be flowed. 0 = no flow, 1 = can
     * flow into neighbor, 2 indicates that it can flow through the neighbor
     * (fences and valves and stuff).
     * 
     * @param b1
     * @param Meta
     * @return
     */
    public byte checkFlow(ChunkData data, final int x0, final int y0, final int z0, final int dx, final int dy, final int dz,
            final Block b1, final int m, final int l0)
    {
        if (b1 == Blocks.air
            ||  Util.isSameFluid(this, b1)   // Old: Same finite fluid
            )
            return 1;
        if (   this.getMaterial() == b1.getMaterial()  // New: Same type of (possibly mod) fluid.
                    && (-1 == dy)                           // and we are going down
                    && (FluidData.getLevelWorld(data, this, x0, y0, z0) < RealisticFluids.ABSORB)
           )
        {
//            System.out.println("Absorbing fluid in mod liquid at " + x0 + ", " + y0 + ", " + z0);
            return 1;
        }
            // TODO: Need to test ledges. We have access to the dynamic adjustments, use them.
        final int x1 = x0 + dx;
        final int y1 = y0 + dy;
        final int z1 = z0 + dz;
        // final int xN = x1 + dx; final int yN = y1 + dy; final int zN = z1 +
        // dz;

        // First check for fences;
        if (dx == 0 || dz == 0)
            // We can flow through fences
            if ((b1 == Blocks.fence || b1 == Blocks.nether_brick_fence || b1 == Blocks.iron_bars))
                return (byte) (this.doDoubleFlow(data, x0, y0, z0, x1 + dx, y1 + dy, z1 + dz) ? 2 : 0);

        // BreakInteraction should now mimic vanilla perfectly.
        final byte temp = (byte) this.breakInteraction(data.w, b1, m, x0, y0, z0, l0, x1, y1, z1);
        if (temp != 0)
            return temp;

        // The other block is a different fluid
        if (b1 instanceof BlockFiniteFluid && b1.getMaterial() != this.blockMaterial)
        {
            data = FluidData.forceCurrentChunkData(data, x1, z1);
            final int level1 = FluidData.getLevel(data, this, x1 & 0xF, y1, z1 & 0xF);
            if (this.blockMaterial == Material.water && b1.getMaterial() == Material.lava)
            {
                this.lavaWaterInteraction(data, x0, y0, z0, l0, x1, y1, z1, level1);
                data = FluidData.forceCurrentChunkData(data, x1, z1);
                return (byte) ((data.w.getBlock(x1, y1, z1) == Blocks.air || Util.isSameFluid(this, data.w.getBlock(x1, y1, z1))) ? 1 : 0);
            }
            if (this.blockMaterial == Material.lava && b1.getMaterial() == Material.water)
            {
                this.lavaWaterInteraction(data, x1, y1, z1, level1, x0, y0, z0, l0);
                data = FluidData.forceCurrentChunkData(data, x1, z1);
                return (byte) ((data.w.getBlock(x1, y1, z1) == Blocks.air || Util.isSameFluid(this, data.w.getBlock(x1, y1, z1))) ? 1 : 0);
            }
        }
        return 0;
    }

    public boolean canBreak(final Block b)
    {
        throw new RuntimeException("Call must be converted!");
        /* 
        return !
          (b != Blocks.wooden_door && b != Blocks.iron_door && b != Blocks.standing_sign
                                          && b != Blocks.ladder && b != Blocks.reeds
            ?
                (b.getMaterial() == Material.portal 
                            ? true 
                            : b.getMaterial().blocksMovement())
            : true); */
    }

    public boolean canBreak(World w, int x, int y, int z)
    {
        return func_149809_q(w, x, y, z);
    }
    
    // Non-private version of Mojang's code.
    // Not marked as @override because original is private.
    boolean func_149809_q(World w, int x, int y, int z)
    {
        Material material = w.getBlock(x, y, z).getMaterial();
        return material == this.blockMaterial ? false 
                : (material == Material.lava ? false 
                        : !this.func_149807_p(w, x, y, z));
    }
    
    // Non-private version of Mojang's code; uses getMaterial() accessor.
    // Not marked as @override because original is private.
    boolean func_149807_p(World w, int x, int y, int z)
    {
        Block block = w.getBlock(x, y, z);
        return block != Blocks.wooden_door && block != Blocks.iron_door && block != Blocks.standing_sign
                        && block != Blocks.ladder && block != Blocks.reeds 
                ? (block.getMaterial() == Material.portal ? true 
                        : block.getMaterial().blocksMovement()) : true;

    }
    
    @Override
    public void velocityToAddToEntity(final World w, final int x, int y, final int z, final Entity e, final Vec3 vec)
    {
        if (e instanceof EntityWaterMob)
            return;
        // Copy the flow of the above blocks
        final Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
        int i;
        for (i = 0; i < 8 && Util.isSameFluid(this, c.getBlock(x & 0xF, y + 1, z & 0xF)); i++)
            y++;
        // Scale with depth (lots of water washes you away lol
        final double d = (i / 2.D) + 0.7D;
        final Vec3 vec1 = this.getFlowVector(w, x, y, z);
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
            int l1 = this.getEffectiveFlowDecay(w, x1, y, z1);
            int i2;
            if (l1 < 0)
            {
                if (!w.getBlock(x1, y, z1).getMaterial().blocksMovement())
                {
                    l1 = this.getEffectiveFlowDecay(w, x1, y - 1, z1);

                    if (l1 >= 0)
                    {
                        i2 = l1 - (l - 8);
                        vec3 = vec3.addVector(Util.intDirX(i) * i2, (y - y) * i2, Util.intDirZ(i) * i2);
                    }
                }
            } else if (l1 >= 0)
            {
                i2 = l1 - l;
                vec3 = vec3.addVector(Util.intDirX(i) * i2, (y - y) * i2, Util.intDirZ(i) * i2);
            }
        }
        return vec3.normalize();
    }

    /*
     * Technical rundown. It's pretty simple.
     * 
     * 1a. Try to move fluid into adjacent cells. - note that if the above block
     * is liquid, the adjacent cells are 99.99% definitely full 1b. Attempt to
     * move any remaining fluid straight up
     * 
     * 2a. Move up in a line from the block looking for air 2b. When we find the
     * surface, move all of the water there.
     */
    /**
     * Attempts to displace water by searching for a space above. The algorithm
     * moves upwards trying to find a space.
     * 
     * @param data
     * @param x
     * @param y
     * @param z
     */
    public void displace(ChunkData data, final int x, final int y, final int z, final int m, final int maxOutHeight)
    {
        Block b1;
        int l0 = data.getLevel(x & 0xF, y, z & 0xF);
        if (l0 == 0)
            l0 = (8 - m) * (RealisticFluids.MAX_FLUID >> 3);

        // Try to set content of above and neighboring blocks
        final int skew = data.w.rand.nextInt(4);

        b1 = data.c.getBlock(x & 0xF, y + 1, z & 0xF); // Check the block above
        // If it is not a liquid, try moving the blocks to the sides
        if (!Util.isSameFluid(this, b1))
        {
            for (int j = 0; j < 4 && l0 > 0; j++)
            {
                final int x1 = x + Util.cardinalX(j + skew);
                final int z1 = z + Util.cardinalZ(j + skew);
                data = FluidData.forceCurrentChunkData(data, x1, z1);
                final Block bN = data.c.getBlock(x1 & 0xF, y, z1 & 0xF);
                if (bN == Blocks.air)
                {
                    FluidData.setLevelWorld(data, this, x1, y, z1, l0, true);
                    l0 = 0;
                    return;
                } else if (Util.isSameFluid(this, bN))
                {
                    final int l1 = FluidData.getLevel(data, this, x1 & 0xF, y, z1 & 0xF);
                    final int move = l0 >> 1;
                    FluidData.setLevelWorld(data, this, x1, y, z1, l1 + move, true);
                    l0 += l1 + move - RealisticFluids.MAX_FLUID;
                }
            }
            data = FluidData.forceCurrentChunkData(data, x, z);
            if (l0 > 0 && b1 == Blocks.air)
            {
                FluidData.setLevelWorld(data, this, x, y + 1, z, l0, true);
                l0 = 0;
            }
            return; // We can't go up or across any further, so exit
        }
        // There is fluid above, so just move to the top and put it there
        for (int i = 1; l0 > 0 && i < maxOutHeight && y + i < 255; i++)
        {
            b1 = data.w.getBlock(x, y + i, z);
            // There is fluid above, so move as much content as we can
            if (b1 == Blocks.air)
            {
                FluidData.setLevelWorld(data, this, x, y + i, z, l0, true);
                l0 = 0;
            } else if (Util.isSameFluid(this, b1))
            {
                final int l1 = FluidData.getLevel(data, this, x & 0xF, y + i, z & 0xF);
                if (l1 < RealisticFluids.MAX_FLUID)
                {
                    FluidData.setLevelWorld(data, this, x, y + i, z, l1 + l0, true);
                    l0 += l1 - RealisticFluids.MAX_FLUID;
                }
                continue;
            } else
                break;
        }
    }

    // ////////////////////////////////INTERACTIONS ////////////////////////////

    public int breakInteraction(final World w, final Block b1, final int m1, final int x0, final int y0, final int z0, final int l0,
            final int x1, final int y1, final int z1)
    {
        // Check for torches, plants, etc. similar to vanilla water
        if (this.canBreak(w, x1, y1, z1))
        {
            if (y0 - y1 < 0 || l0 > this.flowBreak)
            {
                b1.dropBlockAsItem(w, x0, y0, z0, m1, m1);
                w.setBlockToAir(x1, y1, z1);
                return 1;
            }
            return 0;
        }
        return 0;
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
    public void lavaWaterInteraction(final ChunkData data, final int xw, final int yw, final int zw, final int lw, final int xl,
            final int yl, final int zl, final int ll)
    {
        final ChunkData data1 = FluidData.forceCurrentChunkData(data, xl, zl);

        if (yl - yw > 0) // Lava flows down into water
        {
            if (ll > (RealisticFluids.MAX_FLUID - (RealisticFluids.MAX_FLUID / 3)))
            {
                FluidData.setLevelWorld(data, (BlockFiniteFluid) Blocks.flowing_water, xw, yw, zw, 0, true);
                FluidData.setLevelWorld(data1, (BlockFiniteFluid) Blocks.flowing_lava, xl, yl, zl, 0, true);
                RealisticFluids.setBlock(data.w, xw, yw, zw, Blocks.obsidian, 0, 3, true);
                return;
            } else
            {
                FluidData.setLevelWorld(data, (BlockFiniteFluid) Blocks.flowing_water, xw, yw, zw, 0, true);
                FluidData.setLevelWorld(data1, (BlockFiniteFluid) Blocks.flowing_lava, xl, yl, zl, 0, true);
                RealisticFluids.setBlock(data.w, xw, yw, zw, Blocks.stone, 0, 3, true);
                return;
            }
        } else if (ll > (RealisticFluids.MAX_FLUID - (RealisticFluids.MAX_FLUID / 3)))
        {
            FluidData.setLevelWorld(data, (BlockFiniteFluid) Blocks.flowing_water, xw, yw, zw, 0, true);
            FluidData.setLevelWorld(data1, (BlockFiniteFluid) Blocks.flowing_lava, xl, yl, zl, 0, true);
            RealisticFluids.setBlock(data.w, xl, yl, zl, Blocks.obsidian, 0, 3, true);
            return;
        } else
        {
            FluidData.setLevelWorld(data, (BlockFiniteFluid) Blocks.flowing_water, xw, yw, zw, 0, true);
            FluidData.setLevelWorld(data1, (BlockFiniteFluid) Blocks.flowing_lava, xl, yl, zl, 0, true);
            RealisticFluids.setBlock(data.w, xl, yl, zl, Blocks.cobblestone, 0, 3, true);
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
    public boolean getCanBlockGrass()
    {
        return false;
    }
    @Override
    public Block setHardness(final float f)
    {
        return super.setHardness(f);
    }
    public Block c(final float f)
    {
        this.blockHardness = f;
        return this;
    }
    @Override
    public Block setTickRandomly(final boolean ticks)
    {
        return super.setTickRandomly(ticks);
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
