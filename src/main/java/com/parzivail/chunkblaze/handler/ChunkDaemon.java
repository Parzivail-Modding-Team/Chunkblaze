package com.parzivail.chunkblaze.handler;

import com.google.common.collect.Maps;
import com.parzivail.chunkblaze.Chunkblaze;
import com.parzivail.chunkblaze.ChunkblazeConfig;
import com.parzivail.chunkblaze.io.IOUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
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
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@SideOnly(Side.CLIENT)
public class ChunkDaemon implements IThreadedFileIO
{
	private WorldClient workingWorld;
	private AnvilSaveHandler saveHandler;
	private final Map<ChunkPos, NBTTagCompound> chunksToSave = Maps.newConcurrentMap();
	private final Set<ChunkPos> chunksBeingSaved = Collections.newSetFromMap(Maps.newConcurrentMap());
	private IWorldEventListener listener = new ChunkModifiedListener(this);
	private boolean flushing;

	@SubscribeEvent
	public void handleEvent(WorldEvent.Load args)
	{
		World world = args.getWorld();

		if (!Chunkblaze.Session.canRun() || !(world instanceof WorldClient))
			return;

		if (world != workingWorld || saveHandler == null)
		{
			if (workingWorld != null)
				flush();

			workingWorld = (WorldClient)world;
			saveHandler = IOUtils.createSaveHandler(workingWorld);

			world.removeEventListener(listener);
			world.addEventListener(listener);

			Chunkblaze.getLogger().info("Ready to save chunks in {}.", IOUtils.getWorldName());

			Chunkblaze.Session.setRunning(ChunkblazeConfig.launchRunning);
			Chunkblaze.Session.chunksMirrored = 0;
		}
	}

	@SubscribeEvent
	public void handleEvent(TickEvent.WorldTickEvent args)
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

		Chunk chunk = workingWorld.getChunkFromChunkCoords(chunkX, chunkZ);

		try
		{
			ExtendedBlockStorage[] blocks = chunk.getBlockStorageArray();
			int sections = countPopulatedSections(blocks);
			if (sections == 0)
			{
				if (ChunkblazeConfig.verbose)
					Chunkblaze.getLogger().info("Skipped empty chunk {}.", stringify(chunk));
				return;
			}

			NBTTagCompound nbtChunkContainer = IOUtils.serializeChunk(chunk);

			enqueueChunkIO(chunk.getPos(), nbtChunkContainer);

			Chunkblaze.Session.chunksMirrored++;

			if (ChunkblazeConfig.verbose)
				Chunkblaze.getLogger().info("Saved {} sections in chunk {}.", sections, stringify(chunk));
		}
		catch (Exception exception)
		{
			Chunkblaze.getLogger().error(String.format("Failed to save chunk %s.", stringify(chunk)), exception);
		}
	}

	private String stringify(Chunk chunk)
	{
		return stringify(chunk.getPos());
	}

	private String stringify(ChunkPos chunk)
	{
		return String.format("(%s,%s)/%s", chunk.x, chunk.z, workingWorld.provider.getDimensionType().getName());
	}

	private int countPopulatedSections(ExtendedBlockStorage[] blocks)
	{
		int sections = 0;

		for (ExtendedBlockStorage b : blocks)
			if (b != Chunk.NULL_BLOCK_STORAGE)
				sections++;

		return sections;
	}

	private void enqueueChunkIO(ChunkPos pos, NBTTagCompound compound)
	{
		if (!this.chunksBeingSaved.contains(pos))
			this.chunksToSave.put(pos, compound);

		ThreadedFileIOBase.getThreadedIOInstance().queueIO(this);
	}

	private Long2ObjectMap<Chunk> getLoadedChunks()
	{
		ChunkProviderClient provider = workingWorld.getChunkProvider();
		return ReflectionHelper.getPrivateValue(ChunkProviderClient.class, provider, "chunkMapping", "field_73236_b", "c");
	}

	private ArrayList<ChunkPos> getLoadedChunkPositions()
	{
		Long2ObjectMap<Chunk> chunks = getLoadedChunks();
		LongSet keys = chunks.keySet();

		ArrayList<ChunkPos> positions = new ArrayList<>();
		for (Long l : keys)
			positions.add(IOUtils.longToChunkPos(l));

		return positions;
	}

	public int getNumLoadedChunks()
	{
		return getLoadedChunks().size();
	}

	public void saveLoadedChunks()
	{
		Chunkblaze.getLogger().info("Saving all loaded chunks.");

		ArrayList<ChunkPos> chunks = getLoadedChunkPositions();
		for (ChunkPos pos : chunks)
			saveChunk(pos.x, pos.z);

		Chunkblaze.getLogger().info("Saved {} chunks.", chunks.size());
	}

	private void flush()
	{
		try
		{
			this.flushing = true;

			while (this.writeNextIO())
				;
		}
		finally
		{
			this.flushing = false;
		}
	}

	public boolean writeNextIO()
	{
		if (this.chunksToSave.isEmpty())
		{
			if (this.flushing && ChunkblazeConfig.verbose)
				Chunkblaze.getLogger().info("All pending chunks flushed.");

			return false;
		}

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
					Chunkblaze.getLogger().error(String.format("Failed to write chunk %s.", stringify(chunkpos)), exception);
				}
			}

			return true;
		}
		finally
		{
			this.chunksBeingSaved.remove(chunkpos);
		}
	}

	public File getCurrentWorldDirectory()
	{
		return saveHandler.getWorldDirectory();
	}
}
