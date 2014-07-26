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
	public final int viscosity;
	public int flowRate;
	public final int flowBreak = maxWater >> 2;
	private static final int[][] directions = { {0,1}, {1,0}, {0,-1}, {-1,0}, {-1,1}, {1,1}, {1,-1}, {-1,-1} };
	
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
		world.setBlockMetadataWithNotify(x,y, z, 0, 2);
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
			if (world.getBlock(x + directions[i][0], y, z + directions[i][1]) instanceof BlockFFluid)
				ChunkCache.markBlockForUpdate(world, (x + directions[i][0]), y, (z + directions[i][1]));
		}
		//TEST is this necessary?
		if (world.getBlock(x, y + 1, z) instanceof BlockFFluid)
			ChunkCache.markBlockForUpdate(world, x, y + 1, z);
		//TEST is this necessary
		if (world.getBlock(x, y - 1, z) instanceof BlockFFluid)
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
					//System.out.println("LAVA IS IN THA NETHER!?  rate: " + FiniteWater.LAVA_NETHER );
					if (UpdateHandler.INSTANCE.tickCounter % (FiniteWater.LAVA_NETHER*FiniteWater.GLOBAL_UPDATE_RATE) != 0)
					{
						ChunkCache.markBlockForUpdate(world, x, y, z); //Mark ourselves to be updated next cycle
						return;
					}
					break testFlowRate;
				}
			
				if (UpdateHandler.INSTANCE.tickCounter % (FiniteWater.GLOBAL_UPDATE_RATE*flowRate) != 0)
				{
					ChunkCache.markBlockForUpdate(world, x, y, z); //Mark ourselves to be updated next cycle
					return;	
				}
			}
		}
	
		//System.out.println("Block is being tickered! " + x + ", " + y + ", " + z);
		int l0 = getLevel(world,x,y,z);
		int _l0 = l0;
		//System.out.println("Flowing! " + l0);
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
				b1 = world.getBlock(x1,y,z1);
				
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
					{ //Prevent water from getting stuck on ledges
						if (b1 == Blocks.air || b1 == this)
						{
							Block b2 = world.getBlock(x1,y-1,z1); 
							if (b2 == Blocks.air || b2 == this)
							{
								l1 = getLevel(world, x1, y, z1);
								int l2 = getLevel(world, x1, y - 1 , z1);
								
								if (l2 >= maxWater || l1 > l0)
									continue;
								
								setLevel(world, x1, y-1, z1, Math.min(maxWater, l0 + l1 + l2), true);
								setLevel(world, x1, y, z1, Math.max(0, l0 + l1 + l2 - maxWater), true);
								
								l0 = Math.max(0, l0 + l1 + l1 - (maxWater << 1));
								if (l0 == 0) return;
							}
						}
					}
				}
			}
		}finally
		{
			if (l0 != _l0)
			{
				setLevel(world, x, y, z, l0, Math.abs(l0 - _l0) > maxWater >> 7);
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
	public byte checkFlow(World world,  int x, int y, int z, int dx, int dy, int dz, Block block, int meta, int level0)
	{
		if (block == Blocks.air || block == this){
			return 1;
		}
		//TODO check torches, redstone dust, etcetera
		if (block instanceof IPlantable)
		{
			//If there is enough water ~or~ we flow down onto it
			if (level0 >= flowBreak || dy != 0)
			{
				if (block != Blocks.snow_layer) block.dropBlockAsItem(world, x, y, z, meta, meta);
				world.setBlockToAir(x + dx, y + dy, z + dz);
				return 1;
			}
			return  0;
		}
		//We can flow through fences
		if (block == Blocks.fence || block == Blocks.nether_brick_fence || block == Blocks.iron_bars)
		{
    		block = world.getBlock(x + 2*dx, y + 2*dy, z + 2*dz);
    		if (y + 2*dy > 0 && block == Blocks.air || block == this)
    			return 2;
			return 0;
		}
		
		if (block == Blocks.wooden_door || block == Blocks.iron_door) 
		{
			if ((dy != 0) || (dx != 0 && dz != 0)) return 0;
			
			if (meta == 8) //We need to go down and check the base of the door
			{
				meta = world.getBlockMetadata(x + dx, y + dy - 1, z + dz);
			}
		
			//We are flowing along the X axis
			//FIXME this is buggy for some reason
			if (dx != 0)
			{
				if (meta == 0 || meta == 2 || meta == 5 || meta == 7) return 0;
			
				block = world.getBlock(x + 2*dx, y + 2*dy, z + 2*dz);
				if (y + 2*dy > 0 && block == Blocks.air || block == this)
				{
					ChunkCache.markBlockForUpdate(world, x, y, z);
					return 2;
				}
				
			
				return 0;
			}else //We are flowing along the Z axis
			//FIXME this is buggy
			{
				if (meta == 1 || meta == 3 || meta == 4 || meta == 6) return 0;
				
				block = world.getBlock(x + 2*dx, y + 2*dy, z + 2*dz);
				if (y + 2*dy > 0 && block == Blocks.air || block == this)
				{
					ChunkCache.markBlockForUpdate(world, x, y, z);
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
        		if (y + 2*dy < 0 )
        		{
        			setLevel(world, x, y, z, 0, true);
        			return 0;
        		}
        		
	        	block = world.getBlock(x + 2*dx, y + 2*dy, z + 2*dz);
	    		if (block == Blocks.air || block == this)
	    		{
	    			ChunkCache.markBlockForUpdate(world, x, y, z);
	    			return 2;
	    		}
        	}
        	return 0;
        }
        
        //The other block is a different fluid
		if (block instanceof BlockFFluid && block.getMaterial() != this.blockMaterial)
		{
			//TODO write proper interaction handling methods
			int level1 = getLevel(world, x + dx, y + dy, z + dz);
			int mass;
			
			int a, b;
			if (this.blockMaterial == Material.lava && block.getMaterial() == Material.water) //we are lava flowing into water
			{
				//Evaporate all the water
				setLevel(world, x + dx, y + dy, z + dz, 0, true, block);
				world.setBlockToAir( x + dx, y + dy, z + dz);
				
				//If there was a significant amount of water
				if (level1 > maxWater/3)
				{
					if (level0 > 3*maxWater/2)
					{
						setLevel(world, x, y, z, Math.max(0, level0 - 5*maxWater/6), true);
						world.setBlock(x + dx, y + dy, z + dz, Blocks.obsidian);
						return 0;
					}
					
					if (world.rand.nextInt(maxWater) >= (3*level0/2) )
					{
						setLevel(world, x, y, z, 0, true);
						world.setBlock(x + dx, y + dy, z + dz, world.rand.nextBoolean() ? Blocks.stone : Blocks.cobblestone);
						return 0;
					}
				}
				return 1;
				
			}
			else if (this.blockMaterial == Material.water  && block.getMaterial() == Material.lava)
			{
				//evaporate all of the water
				setLevel(world, x, y, z, 0, true);
				//If there was enough water
				if (level0 > maxWater/3)
				{
					if (level1 > maxWater/2)
					{
						setLevel(world, x + dx, y + dy, z + dz, 0, true, block);
						world.setBlock(x + dx, y + dy, z + dz, Blocks.obsidian);
						return 0;
					}
					
					if (world.rand.nextInt(maxWater) >= (2 * (level1 + (level0/3))) )
					{
						setLevel(world, x + dx, y + dy, z + dz, 0, true, block);
						world.setBlock(x + dx, y + dy, z + dz, world.rand.nextBoolean() ? Blocks.stone : Blocks.cobblestone);
						return 0;
					}
				}
				setLevel(world, x + dx, y + dy, z + dz, Math.max(0, level1 - (level0 / 2)), true, block);
				return (byte) (world.getBlock(x + dx, y + dy, z + dz) == Blocks.air ? 1 : 0);
			}
			//return (byte) (world.getBlock(x1,y1,z1) == Blocks.air || world.getBlock(x1, y1, z1) == this ? 0 : 1);
		}
		return 0;
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
				if (a ==  (maxWater >> 3))
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

		//Sledgehammer fix: prevent excessive equalization calculations
		if (Math.abs(level0 - level) <= 4) update = false;
		//Ensure that we not change the content of the wrong block by mistake
		if (block1 != Blocks.air && block1.getMaterial() != block.getMaterial()) return;
		//Empty empty blocks
		if (level <= 0)
		{	
			world.setBlockToAir(x, y, z);
			return;
		}
		
		//The target cell is valid
		//There is no need to update meta if the meta doesn't change
		int meta0 = world.getBlockMetadata(x, y, z);
		int meta1 = (maxWater - level) / (maxWater >> 3);
		ChunkCache.setWaterLevel(world, x, y, z, level);
		
		//We are modifying an existing fluid
		if (block1 instanceof BlockFFluid){
			if (meta0 != meta1)
			{
				//TEST: Whether or not we can get away with no block update
				world.setBlockMetadataWithNotify(x, y, z, meta1, 2);
				//Already flagging update, so skip the flag here
				//world.getChunkFromChunkCoords(x << 4,  z << 4).getBlockStorageArray()[y >> 4].setExtBlockMetadata(x  & 0xF, y  & 0xF, z  & 0xF, meta1);
			}
			
			if (!update) return;
			//Mark neighboring fluid blocks for update
			scheduleNeighbors(world, x, y, z);
			return;		
		}
		
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
		
		if (world.getBlock(x0, y0 + 1,  z0) != Blocks.air)
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
			
			//We are next to an equal block, so there is no point testing in this direction?
			if (getLevel(world, x0 + dx, y0, z0 + dz) == level0 ) continue;
			
			for (dist = 1; dist < ((dx != 0 && dz != 0) ? 24 : 32); dist++)
			{
		        //if (level0 <= viscosity) return;
				int x1 = x0 + dist * dx;
				int z1 = z0 + dist * dz;
				//We can only flow into ourselves, or empty blocks which do not have air below them
				if ((world.getBlock(x1,y0,z1) == this || (world.getBlock(x1,y0,z1) == Blocks.air ) && world.getBlock(x1,y0-1,z1) == this) /* && world.getBlock(x1, y0-1, z1) == this*/)
				{
					sum += getLevel(world,x1,y0,z1);
				}
				else //We can't flow any further in this direction
				{
					break;
				}
			//}
			}
		
			//Prevent the algorithm from creating new blocks with too little viscosity
			dist = (Math.min(dist, Math.max(1, (2 * sum / viscosity)/3)));
			
			//Don't bother equalizing in this direction if we cannot equalize over a reasonable distance
			if (dist < 8) continue;
			if (dist > 16) System.out.println("Equalizating! (scope: " + dist + ")");
			
			//Equalize all of the blocks in this direction
			for (int i = 0; i <= dist; i++)	
			{
				int x1 = x0 + i * dx;
				int z1 = z0 + i * dz;
				setLevel(world, x1, y0, z1, sum / dist, true);
			}
				
		}
	}
		

		
}
