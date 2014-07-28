package com.mcfht.finitewater.fluids;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.IPlantable;

import com.mcfht.finitewater.FiniteWater;
import com.mcfht.finitewater.util.ChunkCache;
import com.mcfht.finitewater.util.UpdateHandler;

/**
 * The parent class of all liquids.
 * TODO: Make sounds work
 * @author FHT
 *
 */
public class BlockFFluid extends BlockLiquid{

	
	public static final int maxWater = FiniteWater.MAX_WATER;
	
	/** Tendency of this liquid to flow */
	public final int viscosity;
	public int flowRate;
	
	/** Amount of fluid needed to break things*/
	public final int flowBreak = maxWater >> 2;
	public static final int[][] directions = { {0,1}, {1,0}, {0,-1}, {-1,0}, {-1,1}, {1,1}, {1,-1}, {-1,-1} };
	
	/**
	 * Initialize a new fluid.
	 * @param material => Water or lava (others will work, but interactions may be unreliable)
	 * @param runniness => Factor of flow. Water = 4, Lava = 3.
	 * @param flowRate => How often to update this block (every N sweeping updates = n*5 ticks)
	 */
	public BlockFFluid(Material material, int runniness, int flowRate) {
		super(material);
		this.viscosity = (maxWater >> runniness);
		this.setTickRandomly(true);
		this.flowRate = flowRate;
	}

	
	@Override
	public void onBlockAdded(World world, int x, int y, int z)
	{
		ChunkCache.markBlockForUpdate(world, x, y, z);
	}
	
	@Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float px, float py, float pz, int meta)
	{
		ChunkCache.markBlockForUpdate(world, x, y, z);
		world.setBlockMetadataWithNotify(x, y, z, 0, 3);
		setLevel(world, x, y, z, maxWater, true, this);
		return side;
	}
	
	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z, Block block)
	{
		ChunkCache.markBlockForUpdate(world, x, y, z);
	}
	
	/**
	 * Flags neighboring cells to be updated. ENSURES that they are fluid first!
	 * @param world
	 * @param x
	 * @param y
	 * @param z
	 */
	public static void scheduleNeighbors(World world, int x, int y, int z)
	{
		
		for (int i = 0; i < 4; i++)
		{
			//TEST is this necessary?
			//if (world.getBlock(x + directions[i][0], y, z + directions[i][1]) instanceof BlockFFluid)
				ChunkCache.markBlockForUpdate(world, (x + directions[i][0]), y, (z + directions[i][1]));
		}
		//TEST is this necessary?
		//if (world.getBlock(x, y + 1, z) instanceof BlockFFluid)
			ChunkCache.markBlockForUpdate(world, x, y + 1, z);
		//TEST is this necessary
		//if (world.getBlock(x, y - 1, z) instanceof BlockFFluid)
			ChunkCache.markBlockForUpdate(world, x, y - 1, z);
	}
	
	public void updateTick(World world, int x, int y, int z, Random rand)
	{
		//Factor in flow rate and viscosity;
		//We tick once every 5, but we only need to update once every n*5;
		testFlowRate:
		{
			if (flowRate != 1)
			{
				if ( world.provider.dimensionId == -1 && this.blockMaterial == Material.lava)
				{
					if (UpdateHandler.INSTANCE.tickCounter % (FiniteWater.GLOBAL_UPDATE_RATE * FiniteWater.LAVA_NETHER) != 0)
					{
						ChunkCache.markBlockForUpdate(world, x, y, z); //Mark ourselves to be updated next cycle
						return;
					}
					break testFlowRate;
				}
			
				if (UpdateHandler.INSTANCE.tickCounter % (FiniteWater.GLOBAL_UPDATE_RATE * flowRate) != 0)
				{
					ChunkCache.markBlockForUpdate(world, x, y, z); //Mark ourselves to be updated next cycle
					return;	
				}
			}
		}
	
		int l0 = getLevel(world,x,y,z);
		int _l0 = l0;
		try{
			
			//First, try to flow downwards
			int dy = -1;
			int y1 = y + dy;
			
			//Ensure we do not flow out of the world
			if (y1 < 0 || y1 > 255)
			{
				setLevel(world, x, y, z, 0, true);
				return;
			}
			
			//Now check if we can flow into the block below, etcetera
			Block b1 = world.getBlock(x, y1, z);
			int l1 = getLevel(world, x, y1, z);
			byte b = checkFlow(world, x, y, z, 0, -1, 0, b1, world.getBlockMetadata(x, y1, z), l0);
			if (b != 0)
			{
				y1 = y + b * dy;
				if (l1 < maxWater)
				{
					//Flow down
					l0 = Math.max(0, l0 + l1 - maxWater);
					l1 = Math.min(maxWater, _l0 + l1);
					
					setLevel(world, x, y1, z, l1, true);
				}
			}
			
			boolean flag = false;
			if (l0 < viscosity*2) //Since 2 blocks SHARE the content!!!
				flag = true;
				
			int dx,dz;
			int x1,z1;
			
			int offset = rand.nextInt(8);
			boolean diag = false;
			
			//Try to flow horizontally
			for (int i = 0; i < 8; i++)
			{
				dx = directions[(i + offset) & 0x7][0];
				dz = directions[(i + offset) & 0x7][1];
				diag = (dx != 0 && dz != 0);
				
				x1 = x + dx;
				z1 = z + dz;
				
				l1 = getLevel(world, x1, y, z1);
				b1 = world.getBlock(x1, y, z1);
				
				b = checkFlow(world, x, y, z, dx, 0, dz, b1, world.getBlockMetadata(x1, y, z1), l0);
				if (b != 0)
				{	
					if (!flag)
					{
						x1 = x + b * dx;
						z1 = z + b * dz;
						if (l0 > l1)
						{
							int flow = (l0 - l1)/2;
							if (diag) flow -= flow/3; //Credit to MBXR for this fancy trick
							
							if (l0 - flow >= viscosity && l1 + flow >= viscosity)
							{
								l0 -= flow;
								setLevel(world, x1, y, z1, l1 + flow, true);
							}
						}
					} else 
					{ 
						//Prevent water from getting stuck on ledges
						if (getLevel(world, x, y-1, z) == 0)
						{
							Block b2 = world.getBlock(x1, y-1, z1);
							if (b1 == Blocks.air)
							{
								if (b2 == Blocks.air || b2 == this)
								{
									setLevel(world, x1, y, z1, l0, true);
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
				setLevel(world, x, y, z, l0, Math.abs(l0 - _l0) > maxWater >> 10); //Only bother to update if a significant amount of water has passed
			}
		}
	}
	
	/**
	 * Returns 0 for cannot flow, 1 for can flow, 2 for cannot flow. Also handles
	 * vanilla fluid interactions.
	 * @param block
	 * @param Meta
	 * @return
	 */
	public byte checkFlow(World world, int x0, int y0, int z0, int dx, int dy, int dz, Block block, int meta, int level0)
	{
		if (block == Blocks.air || block == this){
			return 1;
		}
		
		int x1 = x0 + dx;
		int y1 = y0 + dy;
		int z1 = z0 + dz;

		//We can flow through fences
		if ((block == Blocks.fence || block == Blocks.nether_brick_fence || block == Blocks.iron_bars))
		{
			if (dx != 0 && dz != 0) return 0;
			
    		block = world.getBlock(x1 + dx, y1 + dy, z1 + dz);
    		if (y1 > -dy && block == Blocks.air || block == this)
    		{
    			ChunkCache.markBlockForUpdate(world, x0, y0, z0);
    			return 2;
    		}
			return 0;
		}
		
		if (block == Blocks.wooden_door || block == Blocks.iron_door) 
		{
			if ((dy != 0) || (dx != 0 && dz != 0)) return 0;
			if (meta == 8) //We need to go down and check the base of the door
			{
				meta = world.getBlockMetadata(x1, y1 - 1, z1);
			}
		
			//We are flowing along the X axis
			//FIXME this is buggy for some reason
			if (dx != 0)
			{
				if (meta == 0 || meta == 2 || meta == 5 || meta == 7) return 0;
				block = world.getBlock(x1 + dx, y1 + dy, z1 + dz);
				if (y1 > -dy && block == Blocks.air || block == this)
				{
					ChunkCache.markBlockForUpdate(world, x0, y0, z0);
					return 2;
				}
				return 0;
			}else //We are flowing along the Z axis
			//FIXME this is buggy
			{
				if (meta == 1 || meta == 3 || meta == 4 || meta == 6) return 0;
				
				block = world.getBlock(x1 + dx, y1 + dy, z1 + dz);
				if (y1 > -dy && block == Blocks.air || block == this)
				{
					ChunkCache.markBlockForUpdate(world, x0, y0, z0);
					return 2;
				}
				return 0;
			}
		}
        
        //If we are flowing into a trap door
        if (block == Blocks.trapdoor)
        {
        	//Only consider flowing if the trapdoor is open and we are trying to go down
        	//TODO allow treating open trapoor as air for horizontal flow
        	if (dy == 0) return 0;
        	
        	if ((meta >= 4 && meta <= 7) || meta >= 12) 
        	{
        		if (y1 < -dy )
        		{
        			setLevel(world, x0, y0, z0, 0, true);
        			return 0;
        		}
        		
	        	block = world.getBlock(x1 + dx, y1 + dy, z1 + dz);
	    		if (block == Blocks.air || block == this)
	    		{
	    			ChunkCache.markBlockForUpdate(world, x0, y0, z0);
	    			return 2;
	    		}
        	}
        	return 0;
        }
        
        //Check for torches, plants, etc. Behaves like vanilla water
		if (block instanceof IPlantable || block == Blocks.torch || block == Blocks.redstone_wire)
		{
			if (level0 >= flowBreak || dy != 0)
			{
				//if (block != Blocks.snow_layer)
				block.dropBlockAsItem(world, x0, y0, z0, meta, meta);
				world.setBlockToAir(x1, y1, z1);
				return 1;
			}
			return  0;
		}
		
        
        //The other block is a different fluid
		if (block instanceof BlockFFluid && block.getMaterial() != this.blockMaterial)
		{
			int level1 = getLevel(world, x1, y1, z1);
			if (this.blockMaterial == Material.water && block.getMaterial() == Material.lava)
			{
				lavaWaterInteraction(world, x0, y0, z0, level0, x1, y1, z1, level1);
				if (getLevel(world,x0,y0,z0) <= 0) return 0;
				return (byte) ((world.getBlock(x1, y1, z1) == Blocks.air || world.getBlock(x1, y1, z1) == this) ? 1 : 0);
			}
			
			if (this.blockMaterial == Material.lava && block.getMaterial() == Material.water)
			{
				lavaWaterInteraction(world, x0, y0, z0, level1, x1, y1, z1, level0);
				if (getLevel(world,x0,y0,z0) <= 0) return 0;
				return (byte) ((world.getBlock(x1, y1, z1) == Blocks.air || world.getBlock(x1, y1, z1) == this) ? 1 : 0);
			}
		}
		return 0;
	}
	
	public boolean canBreak(Block block)
	{
	    return block != Blocks.wooden_door && block != Blocks.iron_door && block != Blocks.standing_sign && block != Blocks.ladder && block != Blocks.reeds ? (block.getMaterial() == Material.portal ? true : block.getMaterial().blocksMovement()) : true;
	}
	
	/**
	 * Returns the fluid level of a cell at the given coordinates in the given world. Does not
	 * make any attempt to distinguish the type of fluid.
	 * @param world
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public int getLevel(World world,int x,int y,int z)
	{
		Block block0 = world.getBlock(x,y,z);
		if (block0 instanceof BlockFFluid)
		{
			int a = ChunkCache.getWaterLevel(world, x, y, z);
			if (a == 0) 
			{
				a = (8 - world.getBlockMetadata(x, y, z))  * (maxWater >> 3);
				if (a <=  (maxWater >> 3))
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
	 * @param world
	 * @param x
	 * @param y
	 * @param z
	 * @param level
	 * @param update
	 */
	public void setLevel(World world, int x, int y, int z, int level, boolean update)
	{
		setLevel(world, x, y, z, level, update, this);
	}
	/**
	 * Sets the level of a cell with the specified fluid (as instance of BlockFFluid)
	 * @param world
	 * @param x
	 * @param y
	 * @param z
	 * @param level
	 * @param update
	 * @param block
	 */
	public void setLevel(World world, int x, int y, int z, int level, boolean update, Block block)
	{		
		Block block1 = world.getBlock(x, y, z);
		int level0 = getLevel(world, x, y, z);
		level = level > maxWater ? maxWater : level;
		
		//Sledgehammer fix: prevent excessive equalization calculations
		if (update && Math.abs(level0 - level) <= 4) update = false;
		
		//Ensure that we not change the content of the wrong block by mistake
		if (block1 != Blocks.air && block1.getMaterial() != block.getMaterial()) return;
		
		//Empty empty blocks
		if (level <= 0)
		{	
			world.setBlockToAir(x, y, z);
			return;
		}
		
		//There is no need to update meta if the meta doesn't change
		int meta0 = world.getBlockMetadata(x, y, z);
		int meta1 = (maxWater - level) / (maxWater >> 3);
		ChunkCache.setWaterLevel(world, x, y, z, level);
		
		//We are modifying an existing fluid
		if (block1 instanceof BlockFFluid){
			if (meta0 != meta1)
			{
				//Don't throw update, it's pointless
				world.setBlockMetadataWithNotify(x, y, z, meta1, 2);
				//world.getChunkFromChunkCoords(x << 4,  z << 4).getBlockStorageArray()[y >> 4].setExtBlockMetadata(x  & 0xF, y  & 0xF, z  & 0xF, meta1);
			}
			
			if (!update) return;
			
			//Mark neighboring fluid blocks for update
			scheduleNeighbors(world, x, y, z);
			return;		
		}
		
		//It was empty, so set it to our block (the onAdded will throw an update)
		world.setBlock(x, y, z, block, meta1, 2);
	}
	
	/**
	 * Ensure that the content is reset when we get replaced
	 * <p>TODO test for gravel/sand falling and raise water level accordingly
	 */
	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta)
    {
		if (world.getBlock(x, y, z) != this)
		{
			ChunkCache.setWaterLevel(world, x, y, z, 0);
		}
    }

	
	/**
	 * Attempts to equalize levels between surface blocks in long horizontal lines
	 * 
	 * <p>Will be replaced with histograms at some point!
	 * @param world
	 * @param x
	 * @param y
	 * @param z
	 * @param offset
	 */
	public void equalize(World world, int x0, int y0, int z0, int distance)
	{
		//setLevel(world, x0, y0, z0, 0, true); //Debugs
		int level0;
		
		if (distance > 0 && world.getBlock(x0, y0 + 1,  z0) != Blocks.air)
			return;
		
		int sum;
		int r = world.rand.nextInt(8);

		//Start from a random direction and rotate around in 8 lines
		for (int dir = 0; dir < 8; dir++)
		{
			level0 = getLevel(world, x0, y0, z0);
			sum = level0;
			r = (r + 1) & 7;
			int dx = directions[r][0];
			int dz = directions[r][1];
			int dist = 1;
			
			//We are next to a roughly equal block, so skip the equalization
			if (Math.abs(getLevel(world, x0 + dx, y0, z0 + dz) - level0) < maxWater >> 6 ) continue;
		
			for (dist = 1; dist < 32; dist++)
			{

				int x1 = x0 + dist * dx;
				int z1 = z0 + dist * dz;
				
				Block b1 = world.getBlock(x1, y0, z1);
				Block b2 = world.getBlock(x1, y0 - 1, z1);

				//Make sure we don't flow illegally
				if ((b1 == this || b1 == Blocks.air ) && b2 == this )
				{
					sum += getLevel(world,x1,y0,z1);
				}
				else //We can't flow any further in this direction
				{
					//Carry some water over the edge
					if (b2 == Blocks.air)
					{
						dist += 1;
					}
					break;
				}
			//}
			}
			
			//Prevent the algorithm from creating new blocks with too little viscosity
			dist = (Math.min(dist, Math.max(1, (sum / viscosity))));
			
			//Don't bother equalizing in this direction if we cannot equalize over a reasonable distance
			if (dist < 8) continue;
			
			//Equalize all of the blocks in this direction
			for (int i = 0; i <= dist; i++)	
			{
				int x1 = x0 + i * dx;
				int z1 = z0 + i * dz;
				setLevel(world, x1, y0, z1, sum / dist, true);
			}
		}
	}
		
	/**
	 * Handles interaction of lava and water. 0 = water, 1 = lava
	 * @param world
	 * @param x0
	 * @param y0
	 * @param z0
	 * @param l0
	 * @param x1
	 * @param y1
	 * @param z1
	 * @param l1
	 */
	public void lavaWaterInteraction(World world, int x0, int y0, int z0, int l0, int x1, int y1, int z1, int l1)
	{
		//Basically, if there is enough water, solidify the lava block
		//Water is enough if and only if it is larger than half of the amount of lava
		//Or the amount of lava is smaller than 1/3 of a block
		
		if (l0 > l1/2 || l1 - (3*l0/2) < maxWater/4)
		{
			setLevel(world, x0, y0, z0, 0, false, FiniteWater.finiteWater);
			setLevel(world, x1, y1, z1, 0, false, FiniteWater.finiteLava);
			
			if (l1 > (3*maxWater)/4)
			{
				world.setBlock(x1, y1, z1, Blocks.obsidian);
				return;
			}
			world.setBlock(x1, y1, z1, world.rand.nextBoolean() ? Blocks.stone : Blocks.cobblestone);
			return;
		}
		
		setLevel(world, x0, y0, z0, Math.max(0, l0 - (2*l1)/3), false, FiniteWater.finiteWater);
		setLevel(world, x1, y1, z1, Math.max(0, l1 - (3*l0)/2), false, FiniteWater.finiteLava);
		
	}
		
}
