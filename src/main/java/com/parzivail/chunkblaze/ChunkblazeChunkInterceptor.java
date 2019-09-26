package com.parzivail.chunkblaze;

import com.google.common.collect.Maps;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilSaveHandler;
import net.minecraft.world.storage.IThreadedFileIO;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ChunkblazeChunkInterceptor implements IThreadedFileIO
{
	private World workingWorld;
	private AnvilSaveHandler saveHandler;
	private final Map<ChunkPos, NBTTagCompound> chunksToSave = Maps.newConcurrentMap();
	private final Set<ChunkPos> chunksBeingSaved = Collections.newSetFromMap(Maps.newConcurrentMap());
	private File chunkSaveLocation;

	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void on(WorldEvent.Load args)
	{
		World world = args.getWorld();

		if (!(world instanceof WorldClient))
			return;

		if (world != workingWorld || saveHandler == null)
		{
			workingWorld = world;
			saveHandler = IOUtils.createSaveHandler(workingWorld);
			Chunkblaze.getLogger().info("Ready to save chunks in {}.", IOUtils.getWorldName(workingWorld));
		}

		world.addEventListener(new ChunkSaveEventListener(this));
	}

	private void saveChunk(World world, Chunk chunk)
	{
		try
		{
			NBTTagCompound nbttagcompound = new NBTTagCompound();
			NBTTagCompound nbttagcompound1 = new NBTTagCompound();
			nbttagcompound.setTag("Level", nbttagcompound1);
			nbttagcompound.setInteger("DataVersion", 1343);
			net.minecraftforge.fml.common.FMLCommonHandler.instance().getDataFixer().writeVersionData(nbttagcompound);
			IOUtils.writeChunkToNBT(chunk, world, nbttagcompound1);
			net.minecraftforge.common.ForgeChunkManager.storeChunkNBT(chunk, nbttagcompound1);
			net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.ChunkDataEvent.Save(chunk, nbttagcompound));
			enqueueChunkIO(chunk.getPos(), nbttagcompound);

			if (Chunkblaze.getConfig().getVerbose())
				Chunkblaze.getLogger().info("Saved chunk ({},{})/{}.", chunk.x, chunk.z, workingWorld.provider.getDimensionType().getName());
		}
		catch (Exception exception)
		{
			Chunkblaze.getLogger().error(String.format("Failed to save chunk (%s,%s)/%s.", chunk.x, chunk.z, workingWorld.provider.getDimensionType().getName()), exception);
		}
	}

	protected void enqueueChunkIO(ChunkPos pos, NBTTagCompound compound)
	{
		if (!this.chunksBeingSaved.contains(pos))
		{
			this.chunksToSave.put(pos, compound);
		}

		ThreadedChunkIO.getThreadedIOInstance().queueIO(this);
	}

	public boolean writeNextIO()
	{
		if (!this.chunksToSave.isEmpty())
		{
			ChunkPos chunkpos = this.chunksToSave.keySet().iterator().next();

			try
			{
				this.chunksBeingSaved.add(chunkpos);
				NBTTagCompound nbttagcompound = this.chunksToSave.remove(chunkpos);

				if (nbttagcompound != null)
				{
					try
					{
						IOUtils.writeChunkData(chunkSaveLocation, chunkpos, nbttagcompound);
					}
					catch (Exception exception)
					{
						Chunkblaze.getLogger().error(String.format("Failed to write chunk (%s,%s)/%s.", chunkpos.x, chunkpos.z, workingWorld.provider.getDimensionType().getName()), exception);
					}
				}

				return true;
			}
			finally
			{
				this.chunksBeingSaved.remove(chunkpos);
			}
		}

		return false;
	}

	void saveModifiedChunks(int x1, int z1, int x2, int z2)
	{
		int minX = Math.min(x1 / 16, x2 / 16);
		int minZ = Math.min(z1 / 16, z2 / 16);

		int maxX = Math.max(x1 / 16, x2 / 16);
		int maxZ = Math.max(z1 / 16, z2 / 16);

		for (int cX = minX; cX <= maxX; cX++)
		{
			for (int cZ = minZ; cZ <= maxZ; cZ++)
			{
				Chunk chunk = workingWorld.getChunkFromChunkCoords(cX, cZ);

				chunkSaveLocation = IOUtils.createProviderFolder(saveHandler, workingWorld.provider);
				saveChunk(workingWorld, chunk);
			}
		}
	}
}
