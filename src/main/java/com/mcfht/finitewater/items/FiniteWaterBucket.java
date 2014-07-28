package com.mcfht.finitewater.items;

import com.mcfht.finitewater.FiniteWater;
import com.mcfht.finitewater.util.ChunkCache;

import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.FillBucketEvent;

public class FiniteWaterBucket extends Item {
	
	
	
	public int maxDamage = FiniteWater.MAX_WATER;
	
	
	public boolean isDamageable()
    {
        return true;
    }

	public ItemStack onItemRightClick(ItemStack bucket, World world, EntityPlayer player)
    {
        boolean flag = this.getDamage(bucket) == 0;
        
        MovingObjectPosition movingobjectposition = this.getMovingObjectPositionFromPlayer(world, player, flag);

        if (movingobjectposition == null)
        {
            return bucket;
        }
        else
        {
            if (movingobjectposition.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
            {
                int i = movingobjectposition.blockX;
                int j = movingobjectposition.blockY;
                int k = movingobjectposition.blockZ;

                if (!world.canMineBlock(player, i, j, k))
                {
                    return bucket;
                }

                if (flag)
                {
                    if (!player.canPlayerEdit(i, j, k, movingobjectposition.sideHit, bucket))
                    {
                        return bucket;
                    }
                    
                    Block b = world.getBlock(i, j, k);
                    Material material = world.getBlock(i, j, k).getMaterial();
                    int l = world.getBlockMetadata(i, j, k);

                    if (b == FiniteWater.finiteWater || (material == Material.water && l == 0))
                    {
                        
                        int level = ChunkCache.getWaterLevel(world, i, j, k);
                        
                        if (level == 0)
                        {
                        	int meta = world.getBlockMetadata(i, j, k);
                        	level = (8 - world.getBlockMetadata(i, j, k))  * (FiniteWater.MAX_WATER >> 3);
            				if (level <=  (FiniteWater.MAX_WATER >> 3))
            				{
            					level = FiniteWater.MAX_WATER >> 4;
            				}
            				
            				bucket.setItemDamage(level);
                        	return bucket;
                        	
                        }
                    	world.setBlockToAir(i, j, k);
                       
                        
                        //return this.func_150910_a(bucket, player, Items.water_bucket);
                    }

                    if (material == Material.lava && l == 0)
                    {
                        world.setBlockToAir(i, j, k);
                        //return this.func_150910_a(bucket, player, Items.lava_bucket);
                    }
                }
                else
                {
                    if (bucket.getItemDamage() == 0)
                    {
                        return new ItemStack(Items.bucket);
                    }

                    if (movingobjectposition.sideHit == 0)
                    {
                        --j;
                    }

                    if (movingobjectposition.sideHit == 1)
                    {
                        ++j;
                    }

                    if (movingobjectposition.sideHit == 2)
                    {
                        --k;
                    }

                    if (movingobjectposition.sideHit == 3)
                    {
                        ++k;
                    }

                    if (movingobjectposition.sideHit == 4)
                    {
                        --i;
                    }

                    if (movingobjectposition.sideHit == 5)
                    {
                        ++i;
                    }

                    if (!player.canPlayerEdit(i, j, k, movingobjectposition.sideHit, bucket))
                    {
                        return bucket;
                    }

                    if (this.tryPlaceContainedLiquid(world, i, j, k) && !player.capabilities.isCreativeMode)
                    {
                        return new ItemStack(Items.bucket);
                    }
                }
            }

            return bucket;
        }
    }

	private boolean tryPlaceContainedLiquid(World world, int x, int y,int z) {
		
		
		return false;
	}
	
	
	
}
