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
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.IPlantable;

import com.mcfht.realisticfluids.RealisticFluids;
import com.mcfht.realisticfluids.util.ChunkDataMap;
import com.mcfht.realisticfluids.util.FastMath;
import com.mcfht.realisticfluids.util.UpdateHandler;

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

	
	public static final int maxFluid = RealisticFluids.MAX_FLUID;
	
	/** Tendency of this liquid to flow */			public final int viscosity;
	/** Rate at which this liquid flows */			public int flowRate;
	/** Amount of fluid needed to break things*/ 	public final int flowBreak = maxFluid >> 2;
	
	
	/** CONSTANT array of x,z directions*/
	public static final int[][] directions = { {0,1}, {1,0}, {0,-1}, {-1,0}, {-1,1}, {1,1}, {1,-1}, {-1,-1} };
	
	/**
	 * Initialize a new fluid.
	 * @param material => Water or lava (others will work, but interactions may be unreliable)
	 * @param runniness => Factor of flow. Water = 4, Lava = 3.
	 * @param flowRate => How often to update this block (every N sweeping updates = n*5 ticks)
	 */
	public BlockFiniteFluid(Material material, int runniness, int flowRate) {
		super(material);
		this.viscosity = (maxFluid >> runniness);
		this.setTickRandomly(true); //Because who cares, you know?
		this.flowRate = flowRate;
	}

	@Override
	public void onBlockAdded(World w, int x, int y, int z)
	{
		ChunkDataMap.markBlockForUpdate(w, x, y, z);
	}
	
	@Override
    public int onBlockPlaced(World w, int x, int y, int z, int side, float px, float py, float pz, int m)
	{
		ChunkDataMap.markBlockForUpdate(w, x, y, z);
		w.setBlockMetadataWithNotify(x, y, z, 0, 3);
		setLevel(w, x, y, z, maxFluid, true, this);
		return side;
	}
	
	/**
	 * Todo investigate way to nullify this method?
	 */
	@Override
	public void onNeighborBlockChange(World w, int x, int y, int z, Block b)
	{
		ChunkDataMap.markBlockForUpdate(w, x, y, z);
	}
	
	/**
	 * Ensure that a block is marked as empty when replaced
	 * <p>TODO Investigate way to recognize falling sand/gravel and have them displace water? [stacktrace?]
	 */
	@Override
	public void breakBlock(World w, int x, int y, int z, Block b0, int m)
    {
		Block b1 = w.getBlock(x, y, z);
		if (b1 != this)
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
			{
				
				displace(w, x, y, z);
			}
			
			//Make sure to empty the block out
			ChunkDataMap.setWaterLevel(w, x, y, z, 0);
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
		//ChunkDataMap.markBlockForUpdate(w, x, y, z);
		for (int i = 0; i < 4; i++)
		{
			ChunkDataMap.markBlockForUpdate(w, (x + directions[i][0]), y, (z + directions[i][1]));
		}
		ChunkDataMap.markBlockForUpdate(w, x, y + 1, z);
		ChunkDataMap.markBlockForUpdate(w, x, y - 1, z);
	}
	
	public void updateTick(World w, int x, int y, int z, Random rand)
	{
	
	}
	
	public void doUpdate(World w, int x, int y, int z, Random r, int interval)
	{
		//Factor in flow rate and viscosity;
		//We tick once every 5, but we only need to update once every n*5;
		
		//System.out.println(UpdateHandler.tickCounter());
		testFlowRate:
		{
			if (flowRate != 1)
			{
				if ( w.provider.dimensionId == -1 && this.blockMaterial == Material.lava)
				{
					if (UpdateHandler.tickCounter() % (RealisticFluids.GLOBAL_RATE * RealisticFluids.LAVA_NETHER) != interval)
					{
						ChunkDataMap.markBlockForUpdate(w, x, y, z); //Mark ourselves to be updated next cycle
						return;
					}
					break testFlowRate;
				}
			
				if (UpdateHandler.tickCounter() % (RealisticFluids.GLOBAL_RATE * flowRate) != interval)
				{
					ChunkDataMap.markBlockForUpdate(w, x, y, z); //Mark ourselves to be updated next cycle
					return;	
				}
			}
		}
	
		int l0 = getLevel(w,x,y,z);
		int _l0 = l0;
		try{
			
			//First, try to flow downwards
			int dy = -1;
			int y1 = y + dy;
			
			//Ensure we do not flow out of the w
			if (y1 < 0 || y1 > 255)
			{
				setLevel(w, x, y, z, 0, true);
				return;
			}
			
			//Now check if we can flow into the block below, etcetera
			Block b1 = w.getBlock(x, y1, z);
			int l1 = getLevel(w, x, y1, z);
			byte b = checkFlow(w, x, y, z, 0, -1, 0, b1, w.getBlockMetadata(x, y1, z), l0);
			if (b != 0)
			{
				if (b > 1 ) 
				{
					y1 = y + b * dy;
					l1 = getLevel(w, x, y1, z);
				}
				if (l1 < maxFluid)
				{
					//Flow down
					l0 = Math.max(0, l0 + l1 - maxFluid);
					l1 = Math.min(maxFluid, _l0 + l1);
					
					setLevel(w, x, y1, z, l1, true);
				}
			}
			
			boolean flag = false;
			if (l0 < viscosity*2) //Since 2 blocks SHARE the content!!!
				flag = true;
				
			int dx,dz;
			int x1,z1;
			
			int offset = r.nextInt(8);
			boolean diag = false;
			
			//Try to flow horizontally
			for (int i = 0; i < 8; i++)
			{
				dx = directions[(i + offset) & 0x7][0];
				dz = directions[(i + offset) & 0x7][1];
				diag = (dx != 0 && dz != 0);
				
				x1 = x + dx;
				z1 = z + dz;
				
				l1 = getLevel(w, x1, y, z1);
				b1 = w.getBlock(x1, y, z1);
				
				b = checkFlow(w, x, y, z, dx, 0, dz, b1, w.getBlockMetadata(x1, y, z1), l0);
				if (b != 0)
				{	
					if (!flag)
					{
						if (b > 1)
						{
							x1 = x + b * dx;
							z1 = z + b * dz;
							l1 = getLevel(w, x1, y, z1);
						}
						if (l0 > l1)
						{
							int flow = (l0 - l1)/2;
							if (diag) flow -= flow/3; //Credit to MBXR for this fancy trick
							
							if (l0 - flow >= viscosity && l1 + flow >= viscosity)
							{
								l0 -= flow;
								setLevel(w, x1, y, z1, l1 + flow, true);
							}
						}
					} else 
					{ 
						//Prevent water from getting stuck on ledges
						if (getLevel(w, x, y-1, z) == 0)
						{
							Block b2 = w.getBlock(x1, y-1, z1);
							if (b1 == Blocks.air)
							{
								if (b2 == Blocks.air || b2 == this)
								{
									setLevel(w, x1, y, z1, l0, true);
									l0 = 0;
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
				setLevel(w, x, y, z, l0, Math.abs(l0 - _l0) > maxFluid >> 10); //Only bother to update if a significant amount of water has passed
			}
		}
	}
	
	/**
	 * Returns the number of spaces that can be flowed. 0 = no flow, 1 = can flow into neighbor,
	 * 2 indicates that it can flow through the neighbor (fences and valves and stuff).
	 * @param b
	 * @param Meta
	 * @return
	 */
	public byte checkFlow(World w, int x0, int y0, int z0, int dx, int dy, int dz, Block b, int m, int l0)
	{
		if (b == Blocks.air || b == this){
			return 1;
		}
		
		int x1 = x0 + dx;
		int y1 = y0 + dy;
		int z1 = z0 + dz;

		//We can flow through fences
		if ((b == Blocks.fence || b == Blocks.nether_brick_fence || b == Blocks.iron_bars))
		{
			if (dx != 0 && dz != 0) return 0;
			
    		b = w.getBlock(x1 + dx, y1 + dy, z1 + dz);
    		if (y1 > -dy && b == Blocks.air || b == this)
    		{
    			ChunkDataMap.markBlockForUpdate(w, x0, y0, z0);
    			ChunkDataMap.markBlockForUpdate(w, x1 + dx, y1 + dy, z1 + dz);
    			return 2;
    		}
			return 0;
		}
		
		if (b == Blocks.wooden_door || b == Blocks.iron_door) 
		{
			if ((dy != 0) || (dx != 0 && dz != 0)) return 0;
			if (m == 8) //We need to go down and check the base of the door
			{
				m = w.getBlockMetadata(x1, y1 - 1, z1);
			}
		
			//We are flowing along the X axis
			//FIXME this is buggy for some reason
			if (dx != 0)
			{
				if (m == 0 || m == 2 || m == 5 || m == 7) return 0;
				b = w.getBlock(x1 + dx, y1 + dy, z1 + dz);
				if (y1 > -dy && b == Blocks.air || b == this)
				{
	    			ChunkDataMap.markBlockForUpdate(w, x0, y0, z0);
	    			ChunkDataMap.markBlockForUpdate(w, x1 + dx, y1 + dy, z1 + dz);
					return 2;
				}
				return 0;
			}else //We are flowing along the Z axis
			//FIXME this is buggy
			{
				if (m == 1 || m == 3 || m == 4 || m == 6) return 0;
				
				b = w.getBlock(x1 + dx, y1 + dy, z1 + dz);
				if (y1 > -dy && b == Blocks.air || b == this)
				{
	    			ChunkDataMap.markBlockForUpdate(w, x0, y0, z0);
	    			ChunkDataMap.markBlockForUpdate(w, x1 + dx, y1 + dy, z1 + dz);
					return 2;
				}
				return 0;
			}
		}
        
        //If we are flowing into a trap door
        if (b == Blocks.trapdoor)
        {
        	//Only consider flowing if the trapdoor is open and we are trying to go down
        	//TODO allow treating open trapoor as air for horizontal flow
        	if (dy == 0) return 0;
        	
        	if ((m >= 4 && m <= 7) || m >= 12) 
        	{
        		if (y1 < -dy )
        		{
        			setLevel(w, x0, y0, z0, 0, true);
        			return 0;
        		}
        		
	        	b = w.getBlock(x1 + dx, y1 + dy, z1 + dz);
	    		if (b == Blocks.air || b == this)
	    		{
	    			ChunkDataMap.markBlockForUpdate(w, x0, y0, z0);
	    			ChunkDataMap.markBlockForUpdate(w, x1 + dx, y1 + dy, z1 + dz);
	    			return 2;
	    		}
        	}
        	return 0;
        }
        
        //Check for torches, plants, etc. Behaves like vanilla water
		if (b instanceof IPlantable || b == Blocks.torch || b == Blocks.redstone_wire)
		{
			if (l0 >= flowBreak || dy != 0)
			{
				//if (block != Blocks.snow_layer)
				b.dropBlockAsItem(w, x0, y0, z0, m, m);
				w.setBlockToAir(x1, y1, z1);
				return 1;
			}
			return  0;
		}

        //The other block is a different fluid
		if (b instanceof BlockFiniteFluid && b.getMaterial() != this.blockMaterial)
		{
			int level1 = getLevel(w, x1, y1, z1);
			if (this.blockMaterial == Material.water && b.getMaterial() == Material.lava)
			{
				lavaWaterInteraction(w, x0, y0, z0, l0, x1, y1, z1, level1);
				if (getLevel(w,x0,y0,z0) <= 0) return 0;
				return (byte) ((w.getBlock(x1, y1, z1) == Blocks.air || w.getBlock(x1, y1, z1) == this) ? 1 : 0);
			}
			
			if (this.blockMaterial == Material.lava && b.getMaterial() == Material.water)
			{
				lavaWaterInteraction(w, x0, y0, z0, level1, x1, y1, z1, l0);
				if (getLevel(w,x0,y0,z0) <= 0) return 0;
				return (byte) ((w.getBlock(x1, y1, z1) == Blocks.air || w.getBlock(x1, y1, z1) == this) ? 1 : 0);
			}
		}
		return 0;
	}
	
	public boolean canBreak(Block b)
	{
	    return b != Blocks.wooden_door && b != Blocks.iron_door && b != Blocks.standing_sign && b != Blocks.ladder && b != Blocks.reeds ? (b.getMaterial() == Material.portal ? true : b.getMaterial().blocksMovement()) : true;
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
		if (block0 instanceof BlockFiniteFluid)
		{
			int a = ChunkDataMap.getWaterLevel(w, x, y, z);
			if (a == 0) 
			{
				a = (8 - w.getBlockMetadata(x, y, z))  * (maxFluid >> 3);
				if (a <=  (maxFluid >> 3))
				{
					a = viscosity;
				}
			}
			return a;
		}
		return 0;
	}
	
	/**
	 * Sets the level of a cell. Assumes that we are using the same type of fluid.
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 * @param l
	 * @param causeUpdates
	 */
	public void setLevel(World w, int x, int y, int z, int l, boolean causeUpdates)
	{
		setLevel(w, x, y, z, l, causeUpdates, this);
	}
	/**
	 * Sets the level of a cell with the specified fluid (as instance of BlockFFluid)
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 * @param l1
	 * @param causeUpdates
	 * @param b1
	 */
	public void setLevel(World w, int x, int y, int z, int l1, boolean causeUpdates, Block b1)
	{		
		Block b0 = w.getBlock(x, y, z);
		int l0 = getLevel(w, x, y, z);
		
		l1 = l1 > maxFluid ? maxFluid : l1;
		
		//Sledgehammer fix: prevent excessive equalization calculations
		if (Math.abs(l0 - l1) <= 4) return;
		
		//Ensure that we do not change the content of the wrong block by mistake
		if (b0 != Blocks.air && b0 != this) return;
		
		//Empty empty blocks
		if (l1 <= 0)
		{	
			if (b0 != Blocks.air)
				UpdateHandler.setBlock(w, x, y, z, Blocks.air, 0, causeUpdates ? 2 : 3, true);
				//w.setBlockToAir(x, y, z);
			if (l0 != 0)
				ChunkDataMap.setWaterLevel(w, x, y, z, 0);
			
			//ChunkDataMap.markBlockForUpdate(w, x, y, z);
			
			if (causeUpdates) scheduleNeighbors(w, x, y, z);
			return;
		}
		
		//There is no need to update meta if the meta doesn't change
		int m0 = w.getBlockMetadata(x, y, z);
		int m1 = (maxFluid - l1) / (maxFluid >> 3);
		ChunkDataMap.setWaterLevel(w, x, y, z, l1);
		//We are modifying an existing fluid
		if (b0 instanceof BlockFiniteFluid){
			if (m0 != m1)
			{
				//Do not update light (neg. flag), since fluid level is not relevant to light
				UpdateHandler.setBlock(w, x, y, z, null, m1, -2, true);
			}
			
			//Mark necessary updates
			ChunkDataMap.markBlockForUpdate(w, x, y, z);
			if (causeUpdates) scheduleNeighbors(w, x, y, z);

			
			
			return;		
		}
		
		//It was empty, so set it to our block (the onAdded will throw an update)?
		//w.setBlock(x, y, z, block, meta1, 2);
		ChunkDataMap.markBlockForUpdate(w, x, y, z);
		UpdateHandler.setBlock(w, x, y, z, b1, m1, 2, true);
	}

	//TESTME
	public void velocityToAddToEntity(World w, int x, int y, int z, Entity e, Vec3 vec)
    {
		if (e instanceof EntityWaterMob) return;
		
		//Copy the flow of the above blocks
		Chunk c = w.getChunkFromChunkCoords(x >> 4,  z >> 4);
		
		int i;
		for (i = 0; i < 8 && c.getBlock(x & 0xF, y+1, z & 0xF) == this; i++)
		{
			y++;
		}

		//Scale with depth somewhat
		double d = ((double) i / 6.D) + 0.8D;
		
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

            x1 = x + directions[i][0];
            z1 = z + directions[i][1];
            
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
                        vec3 = vec3.addVector((double)((directions[i][0]) * i2), (double)((y - y) * i2), (double)((directions[i][1]) * i2));
                    }
                }
            }
            else if (l1 >= 0)
            {
                i2 = l1 - l;
                vec3 = vec3.addVector((double)((directions[i][0]) * i2), (double)((y - y) * i2), (double)((directions[i][0]) * i2));
            }
        }

        vec3 = vec3.normalize();
        return vec3;
    }

	
	
	/**
	 * Attempts to equalize levels between surface blocks in long horizontal lines.
	 * 
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 * @param offset
	 */
	public void equalize(World w, int x0, int y0, int z0, int distance)
	{
		//System.out.println("Equalizing Water...");
		
		//setLevel(w, x0, y0, z0, 0, true); //Debugs
		int level0;
		
		if (distance > 0 && w.getBlock(x0, y0 + 1,  z0) != Blocks.air)
			return;
		
		int sum;
		int r = w.rand.nextInt(8);

		//Start from a random direction and rotate around in the 8 main directions
		for (int dir = 0; dir < 8; dir++)
		{
			level0 = getLevel(w, x0, y0, z0);
			sum = level0;
			int dx = directions[(dir + r) & 7][0];
			int dz = directions[(dir + r) & 7][1];
			int dist = 1;
			
			//We are next to a roughly equal block, so skip the equalization
			if (Math.abs(getLevel(w, x0 + dx, y0, z0 + dz) - level0) < maxFluid >> 6 ) continue;
		
			for (dist = 1; dist < distance; dist++)
			{
				int x1 = x0 + dist * dx;
				int z1 = z0 + dist * dz;
				Block b1 = w.getBlock(x1, y0, z1);
				Block b2 = w.getBlock(x1, y0 - 1, z1);

				//Only attempt to equalize if we are on water, and flowing into water or air;
				if (b2 == this)
				{
					if (b1 == this || b1 == Blocks.air )
						sum += getLevel(w,x1,y0,z1);
					else
						break;
				}
				else
				{
					//We flowed over an edge
					if (dist > 3 && b2 == Blocks.air)
					{	
						dist++; //Step over the edge one block
						//Now try to move even more water if we can
						x1 += dx;
						z1 += dz;
						b1 = w.getBlock(x1, y0, z1);
						//Make sure we are flowing into water or an empty space
						if (b1 != this && b1 != Blocks.air) break;
						b2 = w.getBlock(x1, y0-1, z1);
						
						//Now make sure that there is water close below us
						if (b2 == Blocks.air)
						{
							b2 = w.getBlock(x1, y0-2, z1);
							if (b2 != this) break;
						}
						
						//Now move a chunk of water over the edge
						dist++;
						/*
						int flow = sum/dist, l1 = getLevel(w, x1, y0-1, z1);
						setLevel(w, x1, y0-1, z1, Math.min(maxWater, l1 + flow), true);
						setLevel(w, x1, y0, z1, Math.max(0, l1 + flow - maxWater), true);
						sum -= (sum/dist);
						dist--;*/
					}
					break;
				}
			}
			//WE PROBABLY DON'T NEED THIS ANYMORE THANKS TO VALIDITY CHECKING ABOVE
			//Prevent the algorithm from creating new blocks with  ridiculously little viscosity
			//dist = (Math.min(dist, Math.max(1, 4*(sum / (viscosity)))));
			
			//Don't bother equalizing in this direction if we cannot equalize over a reasonable distance
			if (dist < 4) continue;
			
			//Equalize all of the blocks in this direction
			for (int i = 0; i <= dist; i++)	
			{
				int x1 = x0 + i * dx;
				int z1 = z0 + i * dz;
				//If we are flowing onto ourselves
				if (w.getBlock(x1,y0-1,z1) == this)
				{
					int l1 = getLevel(w, x1, y0-1, z1) + sum/dist;
					//Now shift as much water as we can straight down into the block below us (looks a little nicer)
					setLevel(w, x1, y0 - 1, z1, Math.min(maxFluid, l1), true);
					setLevel(w, x1, y0, z1, Math.max(0, l1 - maxFluid), true);
				}
				else //If we are trying to flow into an empty block (the first loop ensures that this flow is itself valid)
					if (w.getBlock(x1, y0, z1) == Blocks.air)
					{
						setLevel(w, x1, y0-1, z1, sum / dist, true);
					}
			}
		}
	}
	
	/**
	 * Attempts to displace water by searching for a space above. The algorithm moves upwards trying to find a space;
	 * With a max of about 60 blocks.
	 * @param w
	 * @param x
	 * @param y
	 * @param z
	 */
	public void displace(World w, int x, int y, int z)
	{
		Block b;
		int l0 = ChunkDataMap.getWaterLevel(w, x, y, z);
		//System.out.println("We are displacing water... " + l0);
		
		//Try to set content of above and neighboring blocks
		int skew = w.rand.nextInt(3);
		for (int i = 1; i < 64  && y + i < 255 && l0 > 0; i++)
		{
			b = w.getBlock(x, y+i, z);
			//There is water above, so try and set it to ~us~
			if (b == this)
			{
				int l1 = getLevel(w, x, y+i, z);
				setLevel(w, x, y+i, z, Math.min(maxFluid, l1 + l0), true);
				l0 = Math.max(0, l1 + l0 - maxFluid);
				continue;
			}
			else
			//There is air above, so first try to move into a neighbor (prettier)
			if (b == Blocks.air)
			{
				//Check the 4 main sides
				for (int j = 0; j < 4 && l0 > 0; j++)
				{
					int x1 = x + directions[(j + skew)&3][0];
					int z1 = z + directions[(j + skew)&3][1];
					b = w.getBlock(x1, y+i-1, z1);
					int l1 =  getLevel(w, x1, y+i-1, z1);
					if (b == Blocks.air || b == this)
					{
						//Don't move the entire block in one direction
						int move = l0/3;
						setLevel(w, x1, y+i-1, z1, Math.min(maxFluid, l1 + move), true);
						l0 = Math.max(0, l1 + move - maxFluid);
					}
				}
				//We still have water, so put it here
				if (l0 > 0)
				{
					int l1 = getLevel(w, x, y+i, z);
					setLevel(w, x, y+i, z, Math.min(maxFluid, l1 + l0), true);
					l0 = Math.max(0, l1 + l0 - maxFluid);
				}
			}
		}
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
	public void lavaWaterInteraction(World w, int x0, int y0, int z0, int l0, int x1, int y1, int z1, int l1)
	{
		//Basically, solidify the lava if we have enough water to do so
		//To ensure that this eventually occurs, decrease the volume of the lava
		//Obsidian can only be created by tipping a bucket of lava straight into water
		if (l0 > l1/2 || l1 - (3*l0/2) < maxFluid/4)
		{
			setLevel(w, x0, y0, z0, 0, false, RealisticFluids.finiteWater);
			setLevel(w, x1, y1, z1, 0, false, RealisticFluids.finiteLava);
			
			if (l1 > maxFluid/2)
			{
				UpdateHandler.setBlock(w, x1, y1, z1, Blocks.obsidian, 0, 3, true);

				//w.setBlock(x1, y1, z1, Blocks.obsidian);
				return;
			}
			UpdateHandler.setBlock(w, x1, y1, z1, w.rand.nextBoolean() ? Blocks.stone : Blocks.cobblestone, 0, 3, true);
			//w.setBlock(x1, y1, z1, w.rand.nextBoolean() ? Blocks.stone : Blocks.cobblestone);
			return;
		}
		
		setLevel(w, x0, y0, z0, Math.max(0, l0 - (2*l1)/3), false, RealisticFluids.finiteWater);
		setLevel(w, x1, y1, z1, Math.max(0, l1 - (3*l0)/2), false, RealisticFluids.finiteLava);
		
	}
		
}
