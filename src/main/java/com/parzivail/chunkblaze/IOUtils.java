package com.parzivail.chunkblaze;

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
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.AnvilSaveHandler;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.RegionFileCache;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class IOUtils
{
	static File createProviderFolder(AnvilSaveHandler handler, WorldProvider provider)
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

	static void writeChunkData(File worldDir, ChunkPos pos, NBTTagCompound compound) throws IOException
	{
		DataOutputStream dataoutputstream = RegionFileCache.getChunkOutputStream(worldDir, pos.x, pos.z);
		CompressedStreamTools.write(compound, dataoutputstream);
		dataoutputstream.close();
	}

	static void writeChunkToNBT(Chunk chunkIn, World worldIn, NBTTagCompound compound)
	{
		compound.setInteger("xPos", chunkIn.x);
		compound.setInteger("zPos", chunkIn.z);
		compound.setLong("LastUpdate", worldIn.getTotalWorldTime());
		compound.setIntArray("HeightMap", chunkIn.getHeightMap());
		compound.setBoolean("TerrainPopulated", chunkIn.isTerrainPopulated());
		compound.setBoolean("LightPopulated", chunkIn.isLightPopulated());
		compound.setLong("InhabitedTime", chunkIn.getInhabitedTime());
		ExtendedBlockStorage[] aextendedblockstorage = chunkIn.getBlockStorageArray();
		NBTTagList nbttaglist = new NBTTagList();
		boolean flag = worldIn.provider.hasSkyLight();

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
		compound.setByteArray("Biomes", chunkIn.getBiomeArray());
		chunkIn.setHasEntities(false);
		NBTTagList nbttaglist1 = new NBTTagList();

		for (int i = 0; i < chunkIn.getEntityLists().length; ++i)
		{
			for (Entity entity : chunkIn.getEntityLists()[i])
			{
				NBTTagCompound nbttagcompound2 = new NBTTagCompound();

				try
				{
					if (entity.writeToNBTOptional(nbttagcompound2))
					{
						chunkIn.setHasEntities(true);
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

		for (TileEntity tileentity : chunkIn.getTileEntityMap().values())
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
		List<NextTickListEntry> list = worldIn.getPendingBlockUpdates(chunkIn, false);

		if (list != null)
		{
			long j = worldIn.getTotalWorldTime();
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

		if (chunkIn.getCapabilities() != null)
		{
			try
			{
				compound.setTag("ForgeCaps", chunkIn.getCapabilities().serializeNBT());
			}
			catch (Exception exception)
			{
				Chunkblaze.getLogger().error("A capability provider has thrown an exception trying to write state. It will not persist. Report this to the mod author", exception);
			}
		}
	}

	static String getWorldName(World world)
	{
		ServerData data = Minecraft.getMinecraft().getCurrentServerData();

		if (data != null) // We're on a server
			return String.format("%s %s", data.serverIP, data.serverName);
		else // Singleplayer world
			return world.getWorldInfo().getWorldName();
	}

	static AnvilSaveHandler createSaveHandler(World world)
	{
		File savesDir = Chunkblaze.getRemoteSaveFolder();

		String safeWorldName = getWorldName(world).replaceAll("[^\\w ]+", "-");

		AnvilSaveHandler handler = new AnvilSaveHandler(savesDir, safeWorldName, true, Minecraft.getMinecraft().getDataFixer());
		handler.saveWorldInfo(world.getWorldInfo());

		return handler;
	}
}
