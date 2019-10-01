package com.parzivail.chunkblaze.handler;

import com.google.common.collect.Maps;
import com.parzivail.chunkblaze.Chunkblaze;
import com.parzivail.chunkblaze.ChunkblazeConfig;
import com.parzivail.chunkblaze.io.IOUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilSaveHandler;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.storage.IThreadedFileIO;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
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
	private IWorldEventListener listener = new ChunkModifiedListener(this);

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

			world.removeEventListener(listener);
			world.addEventListener(listener);

			Chunkblaze.getLogger().info("Ready to save chunks in {}.", IOUtils.getWorldName());

			Chunkblaze.Session.setRunning(ChunkblazeConfig.launchRunning);
			Chunkblaze.Session.chunksMirrored = 0;
		}
	}

	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void on(TickEvent.WorldTickEvent args)
	{
		if (args.phase != TickEvent.Phase.END)
			return;

		Minecraft mc = Minecraft.getMinecraft();

		EntityPlayer player = mc.player;

		if (player == null)
			return;

		if (changedChunks(player))
			saveChunk((int)player.posX >> 4, (int)player.posZ >> 4);
	}

	private boolean changedChunks(EntityPlayer player)
	{
		int chunkX = (int)player.posX >> 4;
		int chunkZ = (int)player.posZ >> 4;
		int prevChunkX = (int)player.prevPosX >> 4;
		int prevChunkZ = (int)player.prevPosZ >> 4;

		return chunkX != prevChunkX || chunkZ != prevChunkZ;
	}

	void saveChunk(int chunkX, int chunkZ)
	{
		if (!Chunkblaze.Session.isRunning())
			return;

		try
		{
			Chunk chunk = workingWorld.getChunkFromChunkCoords(chunkX, chunkZ);

			ExtendedBlockStorage[] blocks = chunk.getBlockStorageArray();
			int sections = countPopulatedSections(blocks);
			if (sections == 0)
			{
				if (ChunkblazeConfig.verbose)
					Chunkblaze.getLogger().info("Skipped empty chunk ({},{})/{}.", chunkX, chunkZ, workingWorld.provider.getDimensionType().getName());
				return;
			}

			NBTTagCompound nbtChunkContainer = new NBTTagCompound();
			NBTTagCompound nbtChunkData = new NBTTagCompound();
			nbtChunkContainer.setTag("Level", nbtChunkData);
			nbtChunkContainer.setInteger("DataVersion", 1343);
			net.minecraftforge.fml.common.FMLCommonHandler.instance().getDataFixer().writeVersionData(nbtChunkContainer);
			IOUtils.writeChunkToNBT(chunk, workingWorld, nbtChunkData);
			net.minecraftforge.common.ForgeChunkManager.storeChunkNBT(chunk, nbtChunkData);
			net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.ChunkDataEvent.Save(chunk, nbtChunkContainer));
			enqueueChunkIO(chunk.getPos(), nbtChunkContainer);

			Chunkblaze.Session.chunksMirrored++;

			if (ChunkblazeConfig.verbose)
				Chunkblaze.getLogger().info("Saved chunk ({},{})/{} ({} vertical chunks).", chunkX, chunkZ, workingWorld.provider.getDimensionType().getName(), sections);
		}
		catch (Exception exception)
		{
			Chunkblaze.getLogger().error(String.format("Failed to save chunk (%s,%s)/%s.", chunkX, chunkZ, workingWorld.provider.getDimensionType().getName()), exception);
		}
	}

	private int countPopulatedSections(ExtendedBlockStorage[] blocks)
	{
		int sections = 0;

		for (ExtendedBlockStorage b : blocks)
			if (b != Chunk.NULL_BLOCK_STORAGE)
				sections++;

		return sections;
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
