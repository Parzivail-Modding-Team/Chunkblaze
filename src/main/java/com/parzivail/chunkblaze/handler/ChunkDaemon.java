package com.parzivail.chunkblaze.handler;

import com.google.common.collect.Maps;
import com.parzivail.chunkblaze.Chunkblaze;
import com.parzivail.chunkblaze.config.ChunkblazeConfig;
import com.parzivail.chunkblaze.io.IOUtils;
import com.parzivail.chunkblaze.util.CbUtil;
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
	private final Map<ChunkPos, NBTTagCompound> chunksToSave = Maps.newConcurrentMap();
	private final Set<ChunkPos> chunksBeingSaved = Collections.newSetFromMap(Maps.newConcurrentMap());
	private final IWorldEventListener listener = new ChunkModifiedListener(this);

	private WorldClient currentWorld;
	private AnvilSaveHandler saveHandler;
	private boolean flushing;

	@SubscribeEvent
	public void handleEvent(WorldEvent.Load args)
	{
		World world = args.getWorld();

		// Only set up the mirroring if we're on a server
		if (!(Chunkblaze.Session.canRun() && world instanceof WorldClient))
			return;

		// Set up the mirroring prereqs
		if (world != currentWorld || saveHandler == null)
		{
			// Write all pending chunks to disk if we're leaving a world, because
			// the dimension folder to which chunks are saved depends on the
			// provider of the `currentWorld`
			if (currentWorld != null)
				flush();

			currentWorld = (WorldClient)world;

			// Create save handler
			saveHandler = IOUtils.createSaveHandler(currentWorld);

			// Unregister the event handler if we've already visited this
			// world before we add it back so we don't get duplicate events
			world.removeEventListener(listener);
			world.addEventListener(listener);

			Chunkblaze.getLogger().info("Ready to save chunks in {}.", IOUtils.getServerName());

			// Reset the session state
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

		// Save the chunk the player just left
		if (CbUtil.changedChunks(player))
			saveChunk((int)player.prevPosX >> 4, (int)player.prevPosZ >> 4);
	}

	/**
	 * Queues the chunk at the specified location for writing to disk
	 * only if Chunkblaze is running
	 *
	 * @param chunkX The chunk's X position
	 * @param chunkZ The chunk's Z position
	 */
	void saveChunk(int chunkX, int chunkZ)
	{
		saveChunk(chunkX, chunkZ, false);
	}

	/**
	 * Queues the chunk at the specified location for writing to disk
	 *
	 * @param chunkX The chunk's X position
	 * @param chunkZ The chunk's Z position
	 * @param force  True to force the chunk to save regardless of whether or not Chunkblaze is running
	 */
	void saveChunk(int chunkX, int chunkZ, boolean force)
	{
		if (!(Chunkblaze.Session.isRunning() || force))
			return;

		Chunk chunk = currentWorld.getChunkFromChunkCoords(chunkX, chunkZ);

		try
		{
			// Make sure the chunk isn't empty before wasting time saving it
			int sections = countPopulatedSections(chunk);
			if (sections == 0)
			{
				if (ChunkblazeConfig.verbose)
					Chunkblaze.getLogger().info("Skipped empty chunk {}.", stringify(chunk));
				return;
			}

			// Queue the chunk to be written to disk
			enqueueChunkIO(chunk.getPos(), IOUtils.serializeChunk(chunk));

			Chunkblaze.Session.chunksMirrored++;

			if (ChunkblazeConfig.verbose)
				Chunkblaze.getLogger().info("Saved {} sections in chunk {}{}.", sections, stringify(chunk), force ? " (forced)" : "");
		}
		catch (Exception exception)
		{
			Chunkblaze.getLogger().error(String.format("Failed to save chunk %s.", stringify(chunk)), exception);
		}
	}

	/**
	 * Creates an informative string representation of the chunk's
	 * location in the format "(X,Z)/dimension"
	 *
	 * @param chunk The chunk to stringify
	 *
	 * @return The easily readable description of the chunk's location
	 */
	private String stringify(Chunk chunk)
	{
		return stringify(chunk.getPos());
	}

	/**
	 * Creates an informative string representation of the chunk's
	 * location in the format "(X,Z)/dimension"
	 *
	 * @param chunk The chunk position to stringify
	 *
	 * @return The easily readable description of the chunk's location
	 */
	private String stringify(ChunkPos chunk)
	{
		return String.format("(%s,%s)/%s", chunk.x, chunk.z, currentWorld.provider.getDimensionType().getName());
	}

	/**
	 * Finds the number of vertical chunks which contain blocks
	 *
	 * @param chunk The chunk to inspect
	 *
	 * @return The number of populated vertical chunks
	 */
	private int countPopulatedSections(Chunk chunk)
	{
		int sections = 0;

		for (ExtendedBlockStorage b : chunk.getBlockStorageArray())
			if (b != Chunk.NULL_BLOCK_STORAGE)
				sections++;

		return sections;
	}

	/**
	 * Adds the given chunk to the disk queue and notifies the file IO
	 * scheduler that we need to save something
	 *
	 * @param pos             The chunk position to save
	 * @param serializedChunk The serialized NBT representation of the chunk
	 */
	private void enqueueChunkIO(ChunkPos pos, NBTTagCompound serializedChunk)
	{
		if (!this.chunksBeingSaved.contains(pos))
			this.chunksToSave.put(pos, serializedChunk);

		ThreadedFileIOBase.getThreadedIOInstance().queueIO(this);
	}

	/**
	 * Gets the map of loaded chunks from the private field in {@link ChunkProviderClient}
	 *
	 * @return The map of loaded chunks
	 */
	private Long2ObjectMap<Chunk> getLoadedChunks()
	{
		ChunkProviderClient provider = currentWorld.getChunkProvider();
		return ReflectionHelper.getPrivateValue(ChunkProviderClient.class, provider, "chunkMapping", "field_73236_b", "c");
	}

	/**
	 * Gets a list of all of the chunk positions which are currently loaded
	 *
	 * @return A list of chunk positions
	 */
	private ArrayList<ChunkPos> getLoadedChunkPositions()
	{
		Long2ObjectMap<Chunk> chunks = getLoadedChunks();
		LongSet keys = chunks.keySet();

		ArrayList<ChunkPos> positions = new ArrayList<>();
		for (Long l : keys)
			positions.add(CbUtil.longToChunkPos(l));

		return positions;
	}

	/**
	 * Gets the number of currently loaded chunks
	 *
	 * @return The number of chunks loaded
	 */
	public int getNumLoadedChunks()
	{
		return getLoadedChunks().size();
	}

	/**
	 * Forcefully saves all loaded chunks to disk
	 */
	public void saveLoadedChunks()
	{
		Chunkblaze.getLogger().info("Saving all loaded chunks.");

		ArrayList<ChunkPos> chunks = getLoadedChunkPositions();
		for (ChunkPos pos : chunks)
			saveChunk(pos.x, pos.z, true);

		Chunkblaze.getLogger().info("Saved {} chunks.", chunks.size());
	}

	/**
	 * Forces all of the pending disk IO operations to complete
	 */
	private void flush()
	{
		if (chunksToSave.isEmpty())
			return;

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

	/**
	 * Pops one disk IO operation from the queue and performs it
	 *
	 * @return True if a pending IO operation was completed
	 */
	public boolean writeNextIO()
	{
		if (this.chunksToSave.isEmpty())
		{
			if (this.flushing && ChunkblazeConfig.verbose)
				Chunkblaze.getLogger().info("All pending chunks flushed to disk.");

			return false;
		}

		ChunkPos chunkpos = this.chunksToSave.keySet().iterator().next();
		NBTTagCompound serializedChunk = this.chunksToSave.remove(chunkpos);

		this.chunksBeingSaved.add(chunkpos);
		try
		{
			File chunkSaveLocation = IOUtils.createProviderFolder(saveHandler, currentWorld.provider);
			IOUtils.writeChunkData(chunkSaveLocation, chunkpos, serializedChunk);
		}
		catch (Exception exception)
		{
			Chunkblaze.getLogger().error(String.format("Failed to write chunk %s.", stringify(chunkpos)), exception);
		}
		this.chunksBeingSaved.remove(chunkpos);

		return true;
	}

	/**
	 * Gets the directory in which the currentl world will be saved
	 *
	 * @return The world's root directory
	 */
	public File getCurrentWorldDirectory()
	{
		return saveHandler.getWorldDirectory();
	}
}
