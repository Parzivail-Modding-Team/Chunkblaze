package com.parzivail.chunkblaze;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.carver.CarvingMask;
import net.minecraft.world.gen.chunk.BlendingData;
import net.minecraft.world.storage.StorageIoWorker;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

public class ChunkHandler
{
	private static final Codec<PalettedContainer<BlockState>> BLOCKSTATE_CODEC = PalettedContainer.createPalettedContainerCodec(
			Block.STATE_IDS, BlockState.CODEC, PalettedContainer.PaletteProvider.BLOCK_STATE, Blocks.AIR.getDefaultState()
	);

	private StorageIoWorker _regionStorage;

	public ChunkHandler()
	{
		_regionStorage = new StorageIoWorker(Path.of("E:\\colby\\Desktop\\temp\\aaaa"), true, "chunk");
	}

	public void loadChunkFromPacket(int x, int z, PacketByteBuf buf, NbtCompound nbt, Consumer<ChunkData.BlockEntityVisitor> consumer)
	{
		if (!Chunkblaze.isEnabled())
			return;

		var mc = MinecraftClient.getInstance();

		var pos = new ChunkPos(x, z);

		var localBuf = new PacketByteBuf(buf.retainedDuplicate());
		var worldChunk = new WorldChunk(mc.world, pos);
		worldChunk.loadFromPacket(localBuf, nbt, consumer);

		// TODO: block entities present but invisible
		// TODO: entities missing
		// TODO: block updates missing?
		_regionStorage.setResult(pos, serialize(mc.world, worldChunk));

		// TODO: let this be async and join/wait when leaving server or manually flushing?
		_regionStorage.completeAll(true);
	}

	public static NbtCompound serialize(World world, Chunk chunk)
	{
		ChunkPos chunkPos = chunk.getPos();
		NbtCompound nbtCompound = NbtHelper.putDataVersion(new NbtCompound());
		nbtCompound.putInt("xPos", chunkPos.x);
		nbtCompound.putInt("yPos", chunk.getBottomSectionCoord());
		nbtCompound.putInt("zPos", chunkPos.z);
		nbtCompound.putLong("LastUpdate", world.getTime());
		nbtCompound.putLong("InhabitedTime", chunk.getInhabitedTime());
		nbtCompound.putString("Status", Registries.CHUNK_STATUS.getId(chunk.getStatus()).toString());
		BlendingData blendingData = chunk.getBlendingData();
		if (blendingData != null)
		{
			BlendingData.CODEC
					.encodeStart(NbtOps.INSTANCE, blendingData)
					.resultOrPartial(Chunkblaze.LOGGER::error)
					.ifPresent(nbtElement -> nbtCompound.put("blending_data", nbtElement));
		}

		BelowZeroRetrogen belowZeroRetrogen = chunk.getBelowZeroRetrogen();
		if (belowZeroRetrogen != null)
		{
			BelowZeroRetrogen.CODEC
					.encodeStart(NbtOps.INSTANCE, belowZeroRetrogen)
					.resultOrPartial(Chunkblaze.LOGGER::error)
					.ifPresent(nbtElement -> nbtCompound.put("below_zero_retrogen", nbtElement));
		}

		UpgradeData upgradeData = chunk.getUpgradeData();
		if (!upgradeData.isDone())
		{
			nbtCompound.put("UpgradeData", upgradeData.toNbt());
		}

		ChunkSection[] chunkSections = chunk.getSectionArray();
		NbtList nbtList = new NbtList();
		LightingProvider lightingProvider = world.getChunkManager().getLightingProvider();
		Registry<Biome> registry = world.getRegistryManager().get(RegistryKeys.BIOME);
		Codec<ReadableContainer<RegistryEntry<Biome>>> codec = createCodec(registry);
		boolean bl = chunk.isLightOn();

		for (int i = lightingProvider.getBottomY(); i < lightingProvider.getTopY(); ++i)
		{
			int j = chunk.sectionCoordToIndex(i);
			boolean bl2 = j >= 0 && j < chunkSections.length;
			ChunkNibbleArray chunkNibbleArray = lightingProvider.get(LightType.BLOCK).getLightSection(ChunkSectionPos.from(chunkPos, i));
			ChunkNibbleArray chunkNibbleArray2 = lightingProvider.get(LightType.SKY).getLightSection(ChunkSectionPos.from(chunkPos, i));
			if (bl2 || chunkNibbleArray != null || chunkNibbleArray2 != null)
			{
				NbtCompound nbtCompound2 = new NbtCompound();
				if (bl2)
				{
					ChunkSection chunkSection = chunkSections[j];
					nbtCompound2.put("block_states", BLOCKSTATE_CODEC.encodeStart(NbtOps.INSTANCE, chunkSection.getBlockStateContainer()).getOrThrow(false, Chunkblaze.LOGGER::error));
					nbtCompound2.put("biomes", codec.encodeStart(NbtOps.INSTANCE, chunkSection.getBiomeContainer()).getOrThrow(false, Chunkblaze.LOGGER::error));
				}

				if (chunkNibbleArray != null && !chunkNibbleArray.isUninitialized())
				{
					nbtCompound2.putByteArray("BlockLight", chunkNibbleArray.asByteArray());
				}

				if (chunkNibbleArray2 != null && !chunkNibbleArray2.isUninitialized())
				{
					nbtCompound2.putByteArray("SkyLight", chunkNibbleArray2.asByteArray());
				}

				if (!nbtCompound2.isEmpty())
				{
					nbtCompound2.putByte("Y", (byte)i);
					nbtList.add(nbtCompound2);
				}
			}
		}

		nbtCompound.put("sections", nbtList);
		if (bl)
		{
			nbtCompound.putBoolean("isLightOn", true);
		}

		NbtList nbtList2 = new NbtList();

		for (BlockPos blockPos : chunk.getBlockEntityPositions())
		{
			NbtCompound nbtCompound3 = chunk.getPackedBlockEntityNbt(blockPos);
			if (nbtCompound3 != null)
			{
				nbtList2.add(nbtCompound3);
			}
		}

		nbtCompound.put("block_entities", nbtList2);
		if (chunk.getStatus().getChunkType() == ChunkStatus.ChunkType.PROTOCHUNK)
		{
			ProtoChunk protoChunk = (ProtoChunk)chunk;
			NbtList nbtList3 = new NbtList();
			nbtList3.addAll(protoChunk.getEntities());
			nbtCompound.put("entities", nbtList3);
			NbtCompound nbtCompound3 = new NbtCompound();

			for (GenerationStep.Carver carver : GenerationStep.Carver.values())
			{
				CarvingMask carvingMask = protoChunk.getCarvingMask(carver);
				if (carvingMask != null)
				{
					nbtCompound3.putLongArray(carver.toString(), carvingMask.getMask());
				}
			}

			nbtCompound.put("CarvingMasks", nbtCompound3);
		}

		serializeTicks(world, nbtCompound, chunk.getTickSchedulers());
		nbtCompound.put("PostProcessing", ChunkSerializer.toNbt(chunk.getPostProcessingLists()));
		NbtCompound nbtCompound4 = new NbtCompound();

		for (Map.Entry<Heightmap.Type, Heightmap> entry : chunk.getHeightmaps())
		{
			if (chunk.getStatus().getHeightmapTypes().contains(entry.getKey()))
			{
				nbtCompound4.put(entry.getKey().getName(), new NbtLongArray(entry.getValue().asLongArray()));
			}
		}

		nbtCompound.put("Heightmaps", nbtCompound4);
		nbtCompound.put("structures", writeStructures());
		return nbtCompound;
	}

	private static void serializeTicks(World world, NbtCompound nbt, Chunk.TickSchedulers tickSchedulers)
	{
		long l = world.getLevelProperties().getTime();
		nbt.put("block_ticks", tickSchedulers.blocks().toNbt(l, block -> Registries.BLOCK.getId(block).toString()));
		nbt.put("fluid_ticks", tickSchedulers.fluids().toNbt(l, fluid -> Registries.FLUID.getId(fluid).toString()));
	}

	private static NbtCompound writeStructures()
	{
		NbtCompound structures = new NbtCompound();
		structures.put("starts", new NbtCompound());
		structures.put("References", new NbtCompound());
		return structures;
	}

	private static Codec<ReadableContainer<RegistryEntry<Biome>>> createCodec(Registry<Biome> biomeRegistry)
	{
		return PalettedContainer.createReadableContainerCodec(
				biomeRegistry.getIndexedEntries(), biomeRegistry.createEntryCodec(), PalettedContainer.PaletteProvider.BIOME, biomeRegistry.entryOf(BiomeKeys.PLAINS)
		);
	}
}
