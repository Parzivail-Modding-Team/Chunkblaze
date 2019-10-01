package com.parzivail.chunkblaze.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.ChunkPos;

public class CbUtil
{
	/**
	 * Undoes {@link ChunkPos#asLong(int, int)}
	 *
	 * @param l The packed long
	 *
	 * @return An unpacked ChunkPos
	 */
	public static ChunkPos longToChunkPos(long l)
	{
		int x = (int)(l & 4294967295L);
		int z = (int)((l >> 32) & 4294967295L);

		return new ChunkPos(x, z);
	}

	/**
	 * Returns true if the player has passed into another chunk this tick
	 *
	 * @param player The player to test
	 *
	 * @return True if the player has changed chunks
	 */
	public static boolean changedChunks(EntityPlayer player)
	{
		int chunkX = (int)player.posX >> 4;
		int chunkZ = (int)player.posZ >> 4;
		int prevChunkX = (int)player.prevPosX >> 4;
		int prevChunkZ = (int)player.prevPosZ >> 4;

		return chunkX != prevChunkX || chunkZ != prevChunkZ;
	}
}
