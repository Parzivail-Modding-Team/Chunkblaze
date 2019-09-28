package com.parzivail.chunkblaze.handler;

import com.google.common.collect.Maps;
import com.parzivail.chunkblaze.Chunkblaze;
import com.parzivail.chunkblaze.ChunkblazeConfig;
import com.parzivail.chunkblaze.io.IOUtils;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilSaveHandler;
import net.minecraft.world.storage.IThreadedFileIO;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ChunkDaemon implements IThreadedFileIO
{
	private World workingWorld;
	private AnvilSaveHandler saveHandler;
	private final Map<ChunkPos, NBTTagCompound> chunksToSave = Maps.newConcurrentMap();
	private final Set<ChunkPos> chunksBeingSaved = Collections.newSetFromMap(Maps.newConcurrentMap());

	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void on(WorldEvent.Load args)
	{
		World world = args.getWorld();

		if (!Chunkblaze.Session.canRun() || !(world instanceof WorldClient))
			return;

		if (world != workingWorld || saveHandler == null)
		{
			workingWorld = world;
			saveHandler = IOUtils.createSaveHandler(workingWorld);
			Chunkblaze.getLogger().info("Ready to save chunks in {}.", IOUtils.getWorldName());

			Chunkblaze.Session.setRunning(ChunkblazeConfig.launchRunning);
			Chunkblaze.Session.chunksMirrored = 0;
		}

		world.addEventListener(new ChunkModifiedListener(this));
	}

	void saveChunk(int chunkX, int chunkZ)
	{
		try
		{
			Chunk chunk = workingWorld.getChunkFromChunkCoords(chunkX, chunkZ);

			NBTTagCompound nbttagcompound = new NBTTagCompound();
			NBTTagCompound nbttagcompound1 = new NBTTagCompound();
			nbttagcompound.setTag("Level", nbttagcompound1);
			nbttagcompound.setInteger("DataVersion", 1343);
			net.minecraftforge.fml.common.FMLCommonHandler.instance().getDataFixer().writeVersionData(nbttagcompound);
			IOUtils.writeChunkToNBT(chunk, workingWorld, nbttagcompound1);
			net.minecraftforge.common.ForgeChunkManager.storeChunkNBT(chunk, nbttagcompound1);
			net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.ChunkDataEvent.Save(chunk, nbttagcompound));
			enqueueChunkIO(chunk.getPos(), nbttagcompound);

			if (ChunkblazeConfig.verbose)
				Chunkblaze.getLogger().info("Saved chunk ({},{})/{}.", chunkX, chunkZ, workingWorld.provider.getDimensionType().getName());

			Chunkblaze.Session.chunksMirrored++;
		}
		catch (Exception exception)
		{
			Chunkblaze.getLogger().error(String.format("Failed to save chunk (%s,%s)/%s.", chunkX, chunkZ, workingWorld.provider.getDimensionType().getName()), exception);
		}
	}

	protected void enqueueChunkIO(ChunkPos pos, NBTTagCompound compound)
	{
		if (!this.chunksBeingSaved.contains(pos))
		{
			this.chunksToSave.put(pos, compound);
		}

		ThreadedFileIOBase.getThreadedIOInstance().queueIO(this);
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
						File chunkSaveLocation = IOUtils.createProviderFolder(saveHandler, workingWorld.provider);
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

	public File getCurrentWorldDirectory()
	{
		return saveHandler.getWorldDirectory();
	}
}
