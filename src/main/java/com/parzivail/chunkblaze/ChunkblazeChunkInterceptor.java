package com.parzivail.chunkblaze;

import com.google.common.collect.Maps;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.AnvilSaveHandler;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.storage.IThreadedFileIO;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChunkblazeChunkInterceptor implements IThreadedFileIO
{
	private World workingWorld;
	private AnvilSaveHandler saveHandler;
	private final Map<ChunkPos, NBTTagCompound> chunksToSave = Maps.newConcurrentMap();
	private final Set<ChunkPos> chunksBeingSaved = Collections.newSetFromMap(Maps.newConcurrentMap());
	private boolean flushing;
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
			createWorldFolder(workingWorld);
		}

		world.addEventListener(new ChunkSaveEventListener(this));
	}

	public File getChunkSaveLocation(WorldProvider provider)
	{
		File file1 = saveHandler.getWorldDirectory();

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

	private void saveChunk(World world, Chunk chunk)
	{
		try
		{
			NBTTagCompound nbttagcompound = new NBTTagCompound();
			NBTTagCompound nbttagcompound1 = new NBTTagCompound();
			nbttagcompound.setTag("Level", nbttagcompound1);
			nbttagcompound.setInteger("DataVersion", 1343);
			net.minecraftforge.fml.common.FMLCommonHandler.instance().getDataFixer().writeVersionData(nbttagcompound);
			writeChunkToNBT(chunk, world, nbttagcompound1);
			net.minecraftforge.common.ForgeChunkManager.storeChunkNBT(chunk, nbttagcompound1);
			net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.ChunkDataEvent.Save(chunk, nbttagcompound));
			addChunkToPending(chunk.getPos(), nbttagcompound);
		}
		catch (Exception exception)
		{
			Chunkblaze.logger.error("Failed to save chunk", exception);
		}
	}

	protected void addChunkToPending(ChunkPos pos, NBTTagCompound compound)
	{
		if (!this.chunksBeingSaved.contains(pos))
		{
			this.chunksToSave.put(pos, compound);
		}

		ThreadedFileIOBase.getThreadedIOInstance().queueIO(this);
	}

	public boolean writeNextIO()
	{
		if (this.chunksToSave.isEmpty())
		{
			return false;
		}
		else
		{
			ChunkPos chunkpos = this.chunksToSave.keySet().iterator().next();
			boolean lvt_3_1_;

			try
			{
				this.chunksBeingSaved.add(chunkpos);
				NBTTagCompound nbttagcompound = this.chunksToSave.remove(chunkpos);

				if (nbttagcompound != null)
				{
					try
					{
						this.writeChunkData(chunkpos, nbttagcompound);
					}
					catch (Exception exception)
					{
						Chunkblaze.logger.error("Failed to save chunk", exception);
					}
				}

				lvt_3_1_ = true;
			}
			finally
			{
				this.chunksBeingSaved.remove(chunkpos);
			}

			return lvt_3_1_;
		}
	}

	private void writeChunkData(ChunkPos pos, NBTTagCompound compound) throws IOException
	{
		DataOutputStream dataoutputstream = RegionFileCache.getChunkOutputStream(chunkSaveLocation, pos.x, pos.z);
		CompressedStreamTools.write(compound, dataoutputstream);
		dataoutputstream.close();
	}

	private void writeChunkToNBT(Chunk chunkIn, World worldIn, NBTTagCompound compound)
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
					net.minecraftforge.fml.common.FMLLog.log.error("An Entity type {} has thrown an exception trying to write state. It will not persist. Report this to the mod author", entity.getClass().getName(), e);
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
				net.minecraftforge.fml.common.FMLLog.log.error("A TileEntity type {} has throw an exception trying to write state. It will not persist. Report this to the mod author", tileentity.getClass().getName(), e);
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
				net.minecraftforge.fml.common.FMLLog.log.error("A capability provider has thrown an exception trying to write state. It will not persist. Report this to the mod author", exception);
			}
		}
	}

	private void createWorldFolder(World world)
	{
		String safeWorldName = "world1";

		File savesDir = Paths.get("E:\\colby\\Desktop\\worlds\\").toFile();
		savesDir.mkdirs();

		saveHandler = new AnvilSaveHandler(savesDir, safeWorldName, true, Minecraft.getMinecraft().getDataFixer());
		saveHandler.saveWorldInfo(world.getWorldInfo());

		Chunkblaze.logger.info("Saved workingWorld info.");
	}

	public void saveChunks(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		int minX = Math.min(x1 / 16, x2 / 16);
		int minZ = Math.min(z1 / 16, z2 / 16);
		int maxX = Math.max(x1 / 16, x2 / 16);
		int maxZ = Math.max(z1 / 16, z2 / 16);

		for (int cX = minX; cX <= maxX; cX++)
			for (int cZ = minZ; cZ <= maxZ; cZ++)
			{
				Chunk chunk = workingWorld.getChunkFromChunkCoords(cX, cZ);

				IBlockState s = workingWorld.getBlockState(new BlockPos(cX * 16, 50, cZ * 16));

				chunkSaveLocation = getChunkSaveLocation(workingWorld.provider);
				saveChunk(workingWorld, chunk);

				Chunkblaze.logger.info("Saved chunk ({},{})/{} {}.", chunk.x, chunk.z, workingWorld.provider.getDimensionType().getName(), s.getBlock());
			}
	}
}
