package com.parzivail.chunkblaze.handler;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class ChunkModifiedListener implements IWorldEventListener
{
	private final ChunkDaemon interceptor;

	public ChunkModifiedListener(ChunkDaemon interceptor)
	{
		this.interceptor = interceptor;
	}

	/**
	 * Listens for ranges of blocks to change in a chunk. The reason it works to hook into
	 * this render event handler is that it's called under every condition immediately after
	 * the network receives and unpacks a chunk data packet. It's a bit of a hack but it works
	 * without having to make an ASM patch.
	 */
	@Override
	public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		interceptor.saveChunk(x1 >> 4, z1 >> 4);
	}

	@Override
	public void notifyBlockUpdate(World worldIn, BlockPos pos, IBlockState oldState, IBlockState newState, int flags)
	{
		interceptor.saveChunk(pos.getX() >> 4, pos.getZ() >> 4);
	}

	@Override
	public void notifyLightSet(BlockPos pos)
	{
	}

	@Override
	public void playSoundToAllNearExcept(@Nullable EntityPlayer player, SoundEvent soundIn, SoundCategory category, double x, double y, double z, float volume, float pitch)
	{
	}

	@Override
	public void playRecord(SoundEvent soundIn, BlockPos pos)
	{
	}

	@Override
	public void spawnParticle(int particleID, boolean ignoreRange, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters)
	{
	}

	@Override
	public void spawnParticle(int id, boolean ignoreRange, boolean p_190570_3_, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int... parameters)
	{
	}

	@Override
	public void onEntityAdded(Entity entityIn)
	{
		interceptor.saveChunk((int)entityIn.posX >> 4, (int)entityIn.posZ >> 4);
	}

	@Override
	public void onEntityRemoved(Entity entityIn)
	{
		interceptor.saveChunk((int)entityIn.posX >> 4, (int)entityIn.posZ >> 4);
	}

	@Override
	public void broadcastSound(int soundID, BlockPos pos, int data)
	{
	}

	@Override
	public void playEvent(EntityPlayer player, int type, BlockPos blockPosIn, int data)
	{
	}

	@Override
	public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress)
	{
	}
}
