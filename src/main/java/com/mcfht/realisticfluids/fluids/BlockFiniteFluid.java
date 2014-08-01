package com.mcfht.realisticfluids.fluids;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockPistonExtension;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.IPlantable;

import com.mcfht.realisticfluids.FluidData;

import com.mcfht.realisticfluids.RealisticFluids;
import com.mcfht.realisticfluids.Util;
import com.mcfht.realisticfluids.FluidData.ChunkData;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The parent class of all liquids.
 * 
 * 
 * 
 * TODO: Make sounds work
 * @author FHT
 *
 */
public class BlockFiniteFluid extends BlockLiquid{

	
	//public static final int RealisticFluids.MAX_FLUID = RealisticFluids.MAX_FLUID;
	
	/** Tendency of this liquid to flow */			public final int viscosity;
	/** Rate at which this liquid flows */			public int flowRate;
	/** Amount of fluid needed to break things*/ 	public final int flowBreak = RealisticFluids.MAX_FLUID >> 2;
	

	/**
	 * Initialize a new fluid.
	 * @param material => Water or lava (others will work, but interactions may be unreliable)
	 * @param runniness => Factor of flow. Water = 4, Lava = 3.
	 * @param flowRate => How often to update this block (every N sweeping updates = n*5 ticks)
	 */
	public BlockFiniteFluid(Material material, int runniness, int flowRate) {
		super(material);
		this.viscosity = (RealisticFluids.MAX_FLUID >> runniness);
		this.setTickRandomly(true); //Because who cares, you know?
		this.flowRate = flowRate;
		this.canBlockGrass = false;
	}

	@Override
	public void onBlockAdded(World w, int x, int y, int z)
	{
		RealisticFluids.markBlockForUpdate(w, x, y, z);
		FluidData.setLevelWorld(FluidData.getChunkData(w.getChunkFromChunkCoords(x>>4, z>>4)), this, x, y, z, RealisticFluids.MAX_FLUID, true);
	}
	
	/**
	 * Todo investigate way to nullify this method?
	 */
	@Override
	public void onNeighborBlockChange(World w, int x, int y, int z, Block b)
	{
		if (!isSameFluid(this, b))
			RealisticFluids.markBlockForUpdate(w, x, y, z);
	}

	/**
	 * Ensure that a block is marked as empty when replaced. Also allow displacement from falling blocks & pistons
	 */
	@Override
	public void breakBlock(World w, int x, int y, int z, Block b0, int m)
    {
		Block b1 = w.getBlock(x, y, z);
		ChunkData data = FluidData.getChunkData(w.getChunkFromChunkCoords(x >> 4, z >> 4));
		
		if (!isSameFluid(this, b1))
		{
			//Extreme hacks?
			StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			boolean flag1, flag2;
			flag1 = b1 instanceof BlockFalling && stack[4].getClassName().equals(EntityFallingBlock.class.getName());
			flag2 =    stack[5].getClassName().equals(BlockPistonBase.class.getName())
					|| stack[5].getClassName().equals(BlockPistonExtension.class.getName()
					);
			
			//System.out.println("Displace? " + flag1 + " - " + flag2 + " - " + (flag1 || flag2));
			if (flag1 || flag2)
				displace(data, x, y, z, m);

			//Make sure to empty the block out
			data.setLevel(x & 0xF, y, z & 0xF, 0);
			//.setWaterLevel(w, x, y, z, 0);
		}
    }
	
	/**
	 * Flags neighboring cells to be updated. ENSURES that they are fluid first!
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 */
	public static void scheduleNeighbors(World w, int x, int y, int z)
	{
		//UpdateHandler.markBlockForUpdate(w, x, y, z);
		for (int i = 0; i < 4; i++)
		{
			RealisticFluids.markBlockForUpdate(w, x + Util.cardinalX(i), y, z + Util.cardinalZ(i));
		}
		if (y < 255)
			RealisticFluids.markBlockForUpdate(w, x, y + 1, z);
		if (y > 0)
			RealisticFluids.markBlockForUpdate(w, x, y - 1, z);
	}
	
	public void updateTick(World w, int x, int y, int z, Random rand)
	{
		RealisticFluids.markBlockForUpdate(w, x, y, z);
	}
	
	public void doUpdate(ChunkData data, int x0, int y0, int z0, Random r, int interval)
	{

		testFlowRate:
		{
			if (flowRate != 1)
			{
				if ( data.w.provider.dimensionId == -1 && this.blockMaterial == Material.lava)
				{
					if (RealisticFluids.tickCounter() % (RealisticFluids.GLOBAL_RATE * RealisticFluids.LAVA_NETHER) != interval)
					{
						RealisticFluids.markBlockForUpdate(data.w, x0, y0, z0); //Mark ourselves to be updated next cycle
						return;
					}
					break testFlowRate;
				}
				if (RealisticFluids.tickCounter() % (RealisticFluids.GLOBAL_RATE * flowRate) != interval)
				{
					RealisticFluids.markBlockForUpdate(data.w, x0, y0, z0); //Mark ourselves to be updated next cycle
					return;	
				}
			}
		}
		data = FluidData.forceCurrentChunkData(data, x0, z0);
		int l0 = FluidData.getLevel(data, this, x0 & 0xF, y0, z0 & 0xF);
		int _l0 = l0;
		int efVisc = viscosity;
		try{
			
			//First, try to flow downwards
			int dy = -1;
			int y1 = y0 + dy;
			
			//Ensure we do not flow out of the w
			if (y1 < 0 || y1 > 255)
			{
				System.err.println("Fluid is flowing out of the world!");
				FluidData.setLevelWorld(data, this, x0, y0, z0, 0, true);
				return;
			}
			//Now check if we can flow into the block below, etcetera
			Block b1 = data.c.getBlock(x0 & 0xF, y1, z0 & 0xF);
			int l1 = FluidData.getLevel(data, this, x0 & 0xF, y1, z0 & 0xF);
			if (l1 > 0) efVisc = viscosity >> 2; //If there is already fluid below, we can flow a little better
			byte b = checkFlow(data, x0, y0, z0, 0, -1, 0, b1, data.c.getBlockMetadata(x0 & 0xF, y1, z0 & 0xF), l0);
			if (b != 0)
			{
				if (b > 1 ) 
				{
					y1 = y0 + b * dy;
					l1 = FluidData.getLevel(data, this, x0 & 0xF, y1, z0 & 0xF);
				}
				if (l1 < RealisticFluids.MAX_FLUID)
				{
					//Flow down
					l0 = l0 + l1 - RealisticFluids.MAX_FLUID; //No need to cap it (redundant)
					l1 = _l0 + l1; //We cap this to MAX_FLUID in the setter
					FluidData.setLevelWorld(data, this, x0, y1, z0, l1, true);
				}
			}
			
			if (l0 <= 0) return;
			
			boolean flag = false;
			if (l0 < efVisc << 1) //Since 2 blocks SHARE the content!!!
				flag = true;
				
			int dx,dz;
			int x1,z1;
			
			int skew = r.nextInt(8);
			boolean diag = false;
			
			//Try to flow horizontally
			for (int i = 0; i < 8; i++)
			{
				dx = Util.intDirX(i + skew);
				dz = Util.intDirZ(i + skew);
				diag = (dx != 0 && dz != 0);
				
				x1 = x0 + dx;
				z1 = z0 + dz;
				
				data = FluidData.forceCurrentChunkData(data, x1, z1);
				//if (data == null) continue;
				
				l1 = FluidData.getLevel(data, this, x1 & 0xF, y0, z1 & 0xF);
				b1 = data.w.getBlock(x1, y0, z1);
				
				b = checkFlow(data, x0, y0, z0, dx, 0, dz, b1, data.w.getBlockMetadata(x1, y0, z1), l0);
				if (b != 0)
				{	
					if (!flag)
					{
						if (b > 1)
						{
							x1 = x0 + b * dx;
							z1 = z0 + b * dz;
							data = FluidData.forceCurrentChunkData(data, x1, z1);
							l1 = FluidData.getLevel(data, this, x1 & 0xF, y0, z1 & 0xF);
						}
						
						if (l0 > l1)
						{
							int flow = (l0 - l1)/2;
							if (diag) flow -= flow/3; 
							if (flow > 0 && l0 - flow >= efVisc && l1 + flow >= efVisc)
							{
								//System.out.println(" ");
								//System.err.println("Flowing horizontally : " + l0 + ", " + l1 + " => " + flow);
								
								l0 -= flow;
								l1 += flow;
								
								FluidData.setLevel(data, this, x1 & 0xF, z1 & 0xF, x1, y0, z1, l1, true);
								//System.err.println("Flowed horizontally -> " + l0 + " and " + data.getLevel(x1 & 0xF, y0, z1 & 0xF) + ", should be: " + l1);
							}
						}
					} else 
					{ 
						//Prevent water from getting stuck on ledges
						if (getLevel(data.w, x0, y0-1, z0) == 0)
						{
							Block b2 = data.w.getBlock(x1, y0 - 1, z1);
							if (b1 == Blocks.air)
							{
								if (b2 == Blocks.air || isSameFluid(this, b2))
								{
									System.out.println("Pushing fluid over edge -> " + l0 + ", " + data.getLevel(x1 & 0xF, y0, z1 & 0xF));
									FluidData.setLevelWorld(data, this, x1, y0, z1, l0, true);
									l0 = 0;
									
									System.out.println("Pushed fluid over edge -> " + l0 + ", " + data.getLevel(x1 & 0xF, y0, z1 & 0xF));
									
									return;
								}
							}
						}
					}
				}
			}
		}finally
		{
			if (l0 != _l0)
			{
				FluidData.setLevelWorld(data, this, x0, y0, z0, l0, Math.abs(l0 - _l0) > RealisticFluids.MAX_FLUID >> 10); //Only bother to update if a significant amount of water has passed
			}
		}
	}
	
	/**
	 * Compares a fluid to a block returning true if it is the same fluid.
	 * @param f
	 * @param b
	 * @return
	 */
	public static boolean isSameFluid(BlockFiniteFluid f, Block b)
	{
		return Util.isSameFluid(f, b);
	}
	
	/**
	 * Returns the number of spaces that can be flowed. 0 = no flow, 1 = can flow into neighbor,
	 * 2 indicates that it can flow through the neighbor (fences and valves and stuff).
	 * @param b1
	 * @param Meta
	 * @return
	 */
	public byte checkFlow(ChunkData data, int x0, int y0, int z0, int dx, int dy, int dz, Block b1, int m, int l0)
	{
		if (b1 == Blocks.air || isSameFluid(this, b1)){
			return 1;
		}
		
		int x1 = x0 + dx;
		int y1 = y0 + dy;
		int z1 = z0 + dz;
		
		if (dx == 0 || dz == 0)
		{
			//We can flow through fences
			if ((b1 == Blocks.fence || b1 == Blocks.nether_brick_fence || b1 == Blocks.iron_bars))
			{
				Block bN = data.w.getBlock(x1+dx, y1+dy, z1+dz);
				if (bN == Blocks.air || isSameFluid(this, b1)) return 2;
				return 0;
			}
			
			if (b1 == Blocks.wooden_door || b1 == Blocks.iron_door) 
			{
				if (dy != 0) return 0;
				m = m == 8 ? data.w.getBlockMetadata(x1, y1 - 1, z1) : m;
				if (   	(dx == 0 && ((m & 1) == (m & 4)))
					|| 	(dz == 0 && ((m & 1) != (m & 4)))
				){
						b1 = data.w.getBlock(x1 + dx, y1 + dy, z1 + dz);
						if (y1 > -dy && b1 == Blocks.air || isSameFluid(this, b1)) return 2;
				}
				return 0;
			}
			if (dy != 0 && b1 == Blocks.trapdoor)//Only consider flowing if the trapdoor is open and we are trying to go down
			{
        	//TODO allow treating open trapoor as air for horizontal flow
	        	if ((m & 4) == 1) 
	        	{
	        		if (y1 + dy <= 0 )
	        			return (byte) FluidData.setLevelWorld(data, this, x0, y0, z0, 0, true);
	        		
		        	b1 = data.w.getBlock(x1 + dx, y1 + dy, z1 + dz);
		    		if (b1 == Blocks.air || isSameFluid(this, b1)) return 2;
	        	}
	        	return 0;
	        }
		}
        byte temp = (byte) breakInteraction(data.w, b1, m, x0, y0, z0, l0, x1, y1, z1);
		if (temp != 0) return temp;
		
        //The other block is a different fluid
		if (b1 instanceof BlockFiniteFluid && b1.getMaterial() != this.blockMaterial)
		{
			int level1 = getLevel(data.w, x1, y1, z1);
			if (this.blockMaterial == Material.water && b1.getMaterial() == Material.lava)
			{
				lavaWaterInteraction(data, x0, y0, z0, l0, x1, y1, z1, level1);
				if (getLevel(data.w,x0,y0,z0) <= 0) return 0;
				return (byte) ((data.w.getBlock(x1, y1, z1) == Blocks.air || isSameFluid(this, data.w.getBlock(x1, y1, z1))) ? 1 : 0);
			}
			
			if (this.blockMaterial == Material.lava && b1.getMaterial() == Material.water)
			{
				lavaWaterInteraction(data, x0, y0, z0, level1, x1, y1, z1, l0);
				if (getLevel(data.w,x0,y0,z0) <= 0) return 0;
				return (byte) ((data.w.getBlock(x1, y1, z1) == Blocks.air || isSameFluid(this, data.w.getBlock(x1, y1, z1))) ? 1 : 0);
			}
		}
				
		return 0;
	}
	
	public boolean canBreak(Block b)
	{
		return !(b != Blocks.wooden_door && b != Blocks.iron_door && b != Blocks.standing_sign && b != Blocks.ladder && b != Blocks.reeds ? (b.getMaterial() == Material.portal ? true : b.getMaterial().blocksMovement()) : true);
	}
	
	/**
	 * Returns the fluid level of a cell at the given coordinates in the given w. Does not
	 * make any attempt to distinguish the type of fluid.
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public int getLevel(World w,int x,int y,int z)
	{
		Block block0 = w.getBlock(x,y,z);
		if (block0 == Blocks.air) return 0;
		if (block0 instanceof BlockFiniteFluid)
		{
			int a = FluidData.getWaterLevel(w, x, y, z);
			if (a == 0) 
			{
				a = (8 - w.getBlockMetadata(x, y, z))  * (RealisticFluids.MAX_FLUID >> 3);
				if (a <=  (RealisticFluids.MAX_FLUID >> 3))
				{
					a = viscosity;
				}
			}
			return a;
		}
		return 0;
	}
	
	/**
	 * Returns the fluid level of a cell at the given coordinates in the given data array.
	 * A little more efficient as it is passed the data object directly.
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public int getLevel(ChunkData data, int x, int y, int z)
	{
		x &= 0xF; y &= 0xF; z &= 0xF;
		Block b0 = data.c.getBlock(x,y,z);
		int a = data.getLevel(x,y,z);
		if (a == 0 && b0 instanceof BlockFiniteFluid)
		{
			a = data.c.getBlockMetadata(x,y,z);
			if (a >= 7) return viscosity;
			return (8 - a) * (RealisticFluids.MAX_FLUID >> 3);
		}
		return a;
	}

	//TESTME
	public void velocityToAddToEntity(World w, int x, int y, int z, Entity e, Vec3 vec)
    {
		if (e instanceof EntityWaterMob) return;
		//Copy the flow of the above blocks
		Chunk c = w.getChunkFromChunkCoords(x >> 4,  z >> 4);
		int i;
		for (i = 0; i < 8 && isSameFluid(this, c.getBlock(x & 0xF, y+1, z & 0xF)); i++)
		{
			y++;
		}
		//Scale with depth somewhat
		double d = ((double) i / 3.D) + 0.7D;
		Vec3 vec1 = this.getFlowVector(w, x, y, z);
        vec.xCoord += vec1.xCoord * d;
        vec.yCoord += vec1.yCoord * d;
        vec.zCoord += vec1.zCoord * d;
    }
	
	
	private Vec3 getFlowVector(World w, int x, int y, int z)
    {
        Vec3 vec3 = Vec3.createVectorHelper(0.0D, 0.0D, 0.0D);
        int l = w.getBlockMetadata(x, y, z);

        for (int i = 0; i < 4; ++i)
        {
            int x1 = x;
            int z1 = z;

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
                        vec3 = vec3.addVector((double) (Util.intDirX(i) * i2), (double)((y - y) * i2), (double) (Util.intDirZ(i) * i2));
                    }
                }
            }
            else if (l1 >= 0)
            {
                i2 = l1 - l;
                vec3 = vec3.addVector((double)(Util.intDirX(i) * i2), (double)((y - y) * i2), (double)(Util.intDirZ(i) * i2));
            }
        }

        vec3 = vec3.normalize();
        return vec3;
    }

	
	/* Technical rundown. It's pretty simple.
	 * 
	 * 1a. Try to move fluid into adjacent cells.
	 *  - note that if the above block is liquid, the adjacent cells are 99.99% definitely full
	 * 1b. Attempt to move any remaining fluid straight up
	 * 
	 * 2a. Move up in a line from the block looking for air
	 * 2b. When we find the surface, move all of the water there.
	 * 
	 */
	/**
	 * Attempts to displace water by searching for a space above. The algorithm moves upwards trying to find a space;
	 * Within a max of about 60 blocks.
	 * @param data
	 * @param x
	 * @param y
	 * @param z
	 */
	public void displace(ChunkData data, int x, int y, int z, int m)
	{
		Block b1;
		int l0 = data.getLevel(x & 0xF, y, z & 0xF);
		if (l0 == 0) l0 = (8 - m) * (RealisticFluids.MAX_FLUID >> 3);
		
		//System.out.println("We are displacing water... " + l0);
		int dir = 0, moved = 0;
		//Try to set content of above and neighboring blocks
		int skew = data.w.rand.nextInt(4);
		
		b1 = data.c.getBlock(x & 0xF, y+1, z & 0xF); //Check the block above
		if (!isSameFluid(this, b1)) //It's not a similar fluid, so try to move the water to the sides
		{
			for (int j = 0; j < 4 && l0 > 0; j++)
			{
				int x1 = x + Util.cardinalX(j + skew);
				int z1 = z + Util.cardinalZ(j + skew);
				
				data = FluidData.forceCurrentChunkData(data, x1, z1);
				
				Block bN = data.c.getBlock(x1 & 0xF, y, z1 & 0xF);
				if (bN == Blocks.air)
				{
					FluidData.setLevelWorld(data, this, x1, y, z1, l0, true);
					l0 = 0;
					return;
				}
				else if (isSameFluid(this, bN))
				{
					int l1 = getLevel(data, x1 & 0xF, y, z1 & 0xF);
					int move = l0 >> 1;
					l0 += l1 + move - RealisticFluids.MAX_FLUID;
				}
			}
			//Try to push any remaining water straight up if there is space
			if (b1 == Blocks.air && l0 > 0)
			{
				//System.out.println("Pushed the last drop of water up!");
				FluidData.setLevelWorld(data, this, x, y+1, z, l0, true);
				l0 = 0;
			}
			return; //We can't go up or across any further, so exit
		}
		//There is fluid above, so just move to the top and put it there
		for (int i = 1; l0 > 0 && i < 64 && y + i < 255; i++)
		{
			b1 = data.w.getBlock(x, y+i, z);
			//There is fluid above, so move as much content as we can
			if ( b1 == Blocks.air || isSameFluid(this, b1))
			{
				int l1 = getLevel(data, x & 0xF, y+i, z & 0xF);
				if (l1 < RealisticFluids.MAX_FLUID){
					//System.out.println("Displaced some water vertically! " + y + ": " + l0 + ", " + l1 + " => " + Math.max(0, l1 + l0 - RealisticFluids.MAX_FLUID));
					FluidData.setLevelWorld(data, this, x, y+i, z, l1 + l0, true);
					l0 = l1 + l0 - RealisticFluids.MAX_FLUID;}
				continue;
			}
		}
	}
	
	//////////////////////////////////INTERACTIONS
	
	public int breakInteraction(World w, Block b1, int m1, int x0, int y0, int z0, int l0, int x1, int y1, int z1)
	{
        //Check for torches, plants, etc. similar to vanilla water
		if (canBreak(b1))
		{
			if (y0 - y1 < 0 || l0 > flowBreak)
			{
				b1.dropBlockAsItem(w, x0, y0, z0, m1, m1); //if (block != Blocks.snow_layer)
				w.setBlockToAir(x1, y1, z1);
				return 1;
			}
			return  0;
		}
		return 0;

	}
		
	/**
	 * Handles interaction of lava and water. 0 = water, 1 = lava
	 * @param w
	 * @param x0
	 * @param y0
	 * @param z0
	 * @param l0
	 * @param x1
	 * @param y1
	 * @param z1
	 * @param l1
	 */
	public void lavaWaterInteraction(ChunkData data, int x0, int y0, int z0, int l0, int x1, int y1, int z1, int l1)
	{
		//Basically, solidify the lava if we have enough water to do so
		//To ensure that this eventually occurs, decrease the volume of the lava
		//Obsidian can only be created by tipping a bucket of lava straight into water
		if (l0 > l1/2 || l1 - (3*l0/2) < RealisticFluids.MAX_FLUID/4)
		{
			FluidData.setLevelWorld(data, (BlockFiniteFluid) Blocks.flowing_water, x0, y0, z0, 0, true);
			FluidData.setLevelWorld(data, (BlockFiniteFluid) Blocks.flowing_lava, x1, y1, z1, 0, true);
			
			if (l1 > RealisticFluids.MAX_FLUID/2)
			{
				RealisticFluids.setBlock(data.w, x1, y1, z1, Blocks.obsidian, 0, 3, true);

				//w.setBlock(x1, y1, z1, Blocks.obsidian);
				return;
			}
			RealisticFluids.setBlock(data.w, x1, y1, z1, data.w.rand.nextBoolean() ? Blocks.stone : Blocks.cobblestone, 0, 3, true);
			//w.setBlock(x1, y1, z1, w.rand.nextBoolean() ? Blocks.stone : Blocks.cobblestone);
			return;
		}
		
		FluidData.setLevelWorld(data, (BlockFiniteFluid) Blocks.flowing_water, x0, y0, z0, Math.max(0, l0 - (2*l1)/3), false);
		FluidData.setLevelWorld(data, (BlockFiniteFluid) Blocks.flowing_lava, x1, y1, z1, Math.max(0, l1 - (3*l0)/2), false);
	}
	
	//Because bad stuff seems to be happening when these methods are not present
	//They should be inherited, but apparently not D:
	
    @SideOnly(Side.CLIENT)
    public boolean getCanBlockGrass()
    {
        return false;
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
    public Block setTickRandomly(boolean ticks)
    {
    	return super.setTickRandomly(ticks);
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
