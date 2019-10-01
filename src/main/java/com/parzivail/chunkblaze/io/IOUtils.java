package com.parzivail.chunkblaze.io;

import com.parzivail.chunkblaze.Chunkblaze;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameType;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.AnvilSaveHandler;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class IOUtils
{
	/**
	 * Creates the folder that corresponds to the chunk provider in the world folder
	 *
	 * @param handler  The world's save handler
	 * @param provider The dimension's chunk provider
	 *
	 * @return A folder which will contain the region files
	 */
	public static File createProviderFolder(AnvilSaveHandler handler, WorldProvider provider)
	{
		File file1 = handler.getWorldDirectory();

		if (provider.getSaveFolder() != null)
		{
			File file3 = new File(file1, provider.getSaveFolder());
			file3.mkdirs();
			return file3;
		}
		else
		{
			return file1;
		}
	}

	/**
	 * Writes chunk data to a MCA region file
	 *
	 * @param providerFolder The provider folder for the chunk's dimension
	 * @param pos            The chunk's position
	 * @param compound       The serialized chunk data
	 *
	 * @throws IOException
	 */
	public static void writeChunkData(File providerFolder, ChunkPos pos, NBTTagCompound compound) throws IOException
	{
		DataOutputStream dataoutputstream = RegionFileCache.getChunkOutputStream(providerFolder, pos.x, pos.z);
		CompressedStreamTools.write(compound, dataoutputstream);
		dataoutputstream.close();
	}

	/**
	 * Serializes the given chunk's data to NBT
	 *
	 * @param chunk    The chunk to serialize
	 * @param compound The tag to serialize into
	 */
	public static void writeChunkData(Chunk chunk, NBTTagCompound compound)
	{
		World world = chunk.getWorld();

		compound.setInteger("xPos", chunk.x);
		compound.setInteger("zPos", chunk.z);
		compound.setLong("LastUpdate", world.getTotalWorldTime());
		compound.setIntArray("HeightMap", chunk.getHeightMap());
		compound.setBoolean("TerrainPopulated", chunk.isTerrainPopulated());
		compound.setBoolean("LightPopulated", chunk.isLightPopulated());
		compound.setLong("InhabitedTime", chunk.getInhabitedTime());
		ExtendedBlockStorage[] aextendedblockstorage = chunk.getBlockStorageArray();
		NBTTagList nbttaglist = new NBTTagList();
		boolean flag = world.provider.hasSkyLight();

		for (ExtendedBlockStorage extendedblockstorage : aextendedblockstorage)
		{
			if (extendedblockstorage != Chunk.NULL_BLOCK_STORAGE)
			{
				NBTTagCompound nbttagcompound = new NBTTagCompound();
				nbttagcompound.setByte("Y", (byte)(extendedblockstorage.getYLocation() >> 4 & 255));
				byte[] abyte = new byte[4096];
				NibbleArray nibblearray = new NibbleArray();
				NibbleArray nibblearray1 = extendedblockstorage.getData().getDataForNBT(abyte, nibblearray);
				nbttagcompound.setByteArray("Blocks", abyte);
				nbttagcompound.setByteArray("Data", nibblearray.getData());

				if (nibblearray1 != null)
				{
					nbttagcompound.setByteArray("Add", nibblearray1.getData());
				}

				nbttagcompound.setByteArray("BlockLight", extendedblockstorage.getBlockLight().getData());

				if (flag)
				{
					nbttagcompound.setByteArray("SkyLight", extendedblockstorage.getSkyLight().getData());
				}
				else
				{
					nbttagcompound.setByteArray("SkyLight", new byte[extendedblockstorage.getBlockLight().getData().length]);
				}

				nbttaglist.appendTag(nbttagcompound);
			}
		}

		compound.setTag("Sections", nbttaglist);
		compound.setByteArray("Biomes", chunk.getBiomeArray());
		chunk.setHasEntities(false);
		NBTTagList nbttaglist1 = new NBTTagList();

		for (int i = 0; i < chunk.getEntityLists().length; ++i)
		{
			for (Entity entity : chunk.getEntityLists()[i])
			{
				NBTTagCompound nbttagcompound2 = new NBTTagCompound();

				try
				{
					if (entity.writeToNBTOptional(nbttagcompound2))
					{
						chunk.setHasEntities(true);
						nbttaglist1.appendTag(nbttagcompound2);
					}
				}
				catch (Exception e)
				{
					Chunkblaze.getLogger().error("An Entity type {} has thrown an exception trying to write state. It will not persist. Report this to the mod author", entity.getClass().getName(), e);
				}
			}
		}

		compound.setTag("Entities", nbttaglist1);
		NBTTagList nbttaglist2 = new NBTTagList();

		for (TileEntity tileentity : chunk.getTileEntityMap().values())
		{
			try
			{
				NBTTagCompound nbttagcompound3 = tileentity.writeToNBT(new NBTTagCompound());
				nbttaglist2.appendTag(nbttagcompound3);
			}
			catch (Exception e)
			{
				Chunkblaze.getLogger().error("A TileEntity type {} has throw an exception trying to write state. It will not persist. Report this to the mod author", tileentity.getClass().getName(), e);
			}
		}

		compound.setTag("TileEntities", nbttaglist2);
		List<NextTickListEntry> list = world.getPendingBlockUpdates(chunk, false);

		if (list != null)
		{
			long j = world.getTotalWorldTime();
			NBTTagList nbttaglist3 = new NBTTagList();

			for (NextTickListEntry nextticklistentry : list)
			{
				NBTTagCompound nbttagcompound1 = new NBTTagCompound();
				ResourceLocation resourcelocation = Block.REGISTRY.getNameForObject(nextticklistentry.getBlock());
				nbttagcompound1.setString("i", resourcelocation == null ? "" : resourcelocation.toString());
				nbttagcompound1.setInteger("x", nextticklistentry.position.getX());
				nbttagcompound1.setInteger("y", nextticklistentry.position.getY());
				nbttagcompound1.setInteger("z", nextticklistentry.position.getZ());
				nbttagcompound1.setInteger("t", (int)(nextticklistentry.scheduledTime - j));
				nbttagcompound1.setInteger("p", nextticklistentry.priority);
				nbttaglist3.appendTag(nbttagcompound1);
			}

			compound.setTag("TileTicks", nbttaglist3);
		}

		if (chunk.getCapabilities() != null)
		{
			try
			{
				compound.setTag("ForgeCaps", chunk.getCapabilities().serializeNBT());
			}
			catch (Exception exception)
			{
				Chunkblaze.getLogger().error("A capability provider has thrown an exception trying to write state. It will not persist. Report this to the mod author", exception);
			}
		}
	}

	/**
	 * Gets the world name of the currently loaded server
	 *
	 * @return The server name in the format "[Server IP] - [Server Name]", or null if it's a singleplayer world
	 */
	public static String getServerName()
	{
		ServerData data = Minecraft.getMinecraft().getCurrentServerData();

		if (data == null) // We're on a singleplayer world
			return null;

		return String.format("%s - %s", data.serverIP, data.serverName);
	}

	/**
	 * Creates a save handler associated with the given world that
	 * will save world data in the remote-saves folder
	 *
	 * @param world The world to create a save handler for
	 *
	 * @return A save handler that will save world data
	 */
	public static AnvilSaveHandler createSaveHandler(World world)
	{
		File savesDir = Chunkblaze.getRemoteSaveFolder();

		String worldName = getServerName();
		String safeWorldName = worldName.replaceAll("[^\\w_\\- ]+", "-");

		AnvilSaveHandler handler = new AnvilSaveHandler(savesDir, safeWorldName, true, Minecraft.getMinecraft().getDataFixer());
		WorldInfo info = getWorldInfo(world, worldName);
		handler.saveWorldInfo(info);

		return handler;
	}

	/**
	 * Creates a {@link WorldInfo} for the given world which allows
	 * commands, spawns players in spectator mode, has doFireTick
	 * disabled, and has mobGriefing disabled.
	 *
	 * @param world     The world to create a info container for
	 * @param worldName The name of the new save
	 *
	 * @return The requested world info container
	 */
	private static WorldInfo getWorldInfo(World world, String worldName)
	{
		WorldInfo info = world.getWorldInfo();

		info.setWorldName(worldName);
		info.setGameType(GameType.SPECTATOR);
		info.setAllowCommands(true);

		info.getGameRulesInstance().setOrCreateGameRule("doFireTick", "false");
		info.getGameRulesInstance().setOrCreateGameRule("mobGriefing", "false");

		return info;
	}

	/**
	 * Creates an NBT chunk container and populates it with metadata and
	 * the chunk's data
	 *
	 * @param chunk The chunk to serialize
	 *
	 * @return The NBT representation of the chunk
	 */
	public static NBTTagCompound serializeChunk(Chunk chunk)
	{
		NBTTagCompound nbtChunkContainer = new NBTTagCompound();
		NBTTagCompound nbtChunkData = new NBTTagCompound();

		nbtChunkContainer.setTag("Level", nbtChunkData);
		nbtChunkContainer.setInteger("DataVersion", 1343);

		FMLCommonHandler.instance().getDataFixer().writeVersionData(nbtChunkContainer);

		writeChunkData(chunk, nbtChunkData);
		ForgeChunkManager.storeChunkNBT(chunk, nbtChunkData);

		MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, nbtChunkContainer));

		return nbtChunkContainer;
	}
}
