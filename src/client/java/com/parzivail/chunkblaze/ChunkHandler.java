package com.parzivail.chunkblaze;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.parzivail.chunkblaze.mixin.WorldAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.path.SymlinkValidationException;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.gen.FlatLevelGeneratorPresets;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.carver.CarvingMask;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.LevelProperties;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.storage.StorageIoWorker;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ChunkHandler
{
	private static final Codec<PalettedContainer<BlockState>> BLOCKSTATE_CODEC = PalettedContainer.createPalettedContainerCodec(
			Block.STATE_IDS, BlockState.CODEC, PalettedContainer.PaletteProvider.BLOCK_STATE, Blocks.AIR.getDefaultState()
	);

	private final DataConfiguration _dataConfig;
	private final GameRules _gameRules;
	private final GeneratorOptions _generatorOpts;

	private StorageIoWorker _regionStorage;

	public ChunkHandler()
	{
		ClientChunkEvents.CHUNK_UNLOAD.register(this::unload);
		ClientPlayConnectionEvents.JOIN.register(this::joinServer);
		ClientPlayConnectionEvents.DISCONNECT.register(this::disconnect);
		ClientEntityEvents.ENTITY_LOAD.register(this::entityLoaded);

		_dataConfig = new DataConfiguration(
				DataPackSettings.SAFE_MODE,
				FeatureSet.empty()
		);

		_gameRules = new GameRules();
		Stream.of(
				GameRules.DO_DAYLIGHT_CYCLE,
				GameRules.DO_DAYLIGHT_CYCLE,
				GameRules.DO_ENTITY_DROPS,
				GameRules.DO_FIRE_TICK,
				GameRules.DO_INSOMNIA,
				GameRules.DO_MOB_LOOT,
				GameRules.DO_MOB_SPAWNING,
				GameRules.DO_PATROL_SPAWNING,
				GameRules.DO_TILE_DROPS,
				GameRules.DO_TRADER_SPAWNING,
				GameRules.DO_VINES_SPREAD,
				GameRules.DO_WARDEN_SPAWNING,
				GameRules.DO_WEATHER_CYCLE,
				GameRules.DO_MOB_GRIEFING,
				GameRules.PROJECTILES_CAN_BREAK_BLOCKS,
				GameRules.LAVA_SOURCE_CONVERSION,
				GameRules.WATER_SOURCE_CONVERSION,
				GameRules.SPECTATORS_GENERATE_CHUNKS
		).forEach(key -> _gameRules.get(key).set(false, null));

		_generatorOpts = new GeneratorOptions(
				0,
				false,
				false
		);
	}

	private void joinServer(ClientPlayNetworkHandler cpnh, PacketSender sender, MinecraftClient mc)
	{
		var serverInfo = cpnh.getServerInfo();
		var levelName = String.format("%s - %s", serverInfo.address, serverInfo.name).replaceAll("[^\\w_\\- ]+", "-");

		var levelProps = new LevelProperties(
				new LevelInfo(
						levelName,
						GameMode.SPECTATOR,
						false,
						Difficulty.NORMAL,
						true,
						_gameRules,
						_dataConfig
				),
				_generatorOpts,
				LevelProperties.SpecialProperty.FLAT,
				Lifecycle.stable()
		);

		var lookup = BuiltinRegistries.createWrapperLookup().createRegistryLookup();
		var preset = lookup
				.getOrThrow(RegistryKeys.FLAT_LEVEL_GENERATOR_PRESET)
				.getOrThrow(FlatLevelGeneratorPresets.THE_VOID);
		var generator = new FlatChunkGenerator(preset.value().settings());

		var overworldEntry = lookup
				.getOrThrow(RegistryKeys.DIMENSION_TYPE)
				.getOrThrow(DimensionTypes.OVERWORLD);
		MutableRegistry<DimensionOptions> dimRegistry = new SimpleRegistry<>(RegistryKeys.DIMENSION, Lifecycle.stable());
		dimRegistry.add(DimensionOptions.OVERWORLD, new DimensionOptions(overworldEntry, generator), Lifecycle.stable());
		var manager = new DynamicRegistryManager.ImmutableImpl(List.of(dimRegistry));

		try
		{
			var containerDir = mc.runDirectory.toPath().resolve("remote-saves");

			var session = LevelStorage.create(containerDir).createSession(levelName);
			session.backupLevelDataFile(manager, levelProps);
			_regionStorage = new StorageIoWorker(session.getWorldDirectory(World.OVERWORLD).resolve("region"), true, "chunkblaze");
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		catch (SymlinkValidationException e)
		{
			throw new RuntimeException(e);
		}
	}

	private void unload(ClientWorld world, WorldChunk chunk)
	{
		if (!Chunkblaze.isEnabled() || _regionStorage == null)
			return;

		_regionStorage.setResult(chunk.getPos(), serialize(world, chunk));
	}

	public void disconnect(ClientPlayNetworkHandler cpnh, MinecraftClient mc)
	{
		if (!Chunkblaze.isEnabled() || _regionStorage == null)
			return;

		// Capture remaining chunks
		var chunkManager = mc.world.getChunkManager();
		var chunks = chunkManager.chunks.chunks;
		var nChunks = chunks.length();
		for (var i = 0; i < nChunks; i++)
		{
			var chunk = chunks.get(i);
			if (chunk == null)
				continue;
			_regionStorage.setResult(chunk.getPos(), serialize(mc.world, chunk));
		}

		_regionStorage.completeAll(true);

		_regionStorage = null;
	}

	private void entityLoaded(Entity entity, ClientWorld world)
	{
		if (!Chunkblaze.isEnabled() || _regionStorage == null)
			return;

		var pos = entity.getChunkPos();
		var chunk = world.getChunk(pos.x, pos.z);

		if (chunk == null)
			return;

		_regionStorage.setResult(pos, serialize(world, chunk));
	}

	public void loadChunkFromPacket(int x, int z, PacketByteBuf buf, NbtCompound nbt, Consumer<ChunkData.BlockEntityVisitor> consumer)
	{
		if (!Chunkblaze.isEnabled() || _regionStorage == null)
			return;

		var mc = MinecraftClient.getInstance();

		var pos = new ChunkPos(x, z);

		// Load preliminary chunk to be updated later if possible
		var localBuf = new PacketByteBuf(buf.retainedDuplicate());
		var worldChunk = new WorldChunk(mc.world, pos);
		worldChunk.loadFromPacket(localBuf, nbt, consumer);

		_regionStorage.setResult(pos, serialize(mc.world, worldChunk));
	}

	public static NbtCompound serialize(World world, Chunk chunk)
	{
		ChunkPos chunkPos = chunk.getPos();
		var posLong = chunkPos.toLong();

		NbtCompound nbtCompound = NbtHelper.putDataVersion(new NbtCompound());
		nbtCompound.putInt("xPos", chunkPos.x);
		nbtCompound.putInt("yPos", chunk.getBottomSectionCoord());
		nbtCompound.putInt("zPos", chunkPos.z);
		nbtCompound.putLong("LastUpdate", world.getTime());
		nbtCompound.putLong("InhabitedTime", chunk.getInhabitedTime());
		nbtCompound.putString("Status", ChunkStatus.SPAWN.toString());

		ChunkSection[] chunkSections = chunk.getSectionArray();
		NbtList nbtList = new NbtList();
		LightingProvider lightingProvider = world.getChunkManager().getLightingProvider();
		Registry<Biome> registry = world.getRegistryManager().get(RegistryKeys.BIOME);
		Codec<ReadableContainer<RegistryEntry<Biome>>> codec = createCodec(registry);
		boolean isLightOn = chunk.isLightOn();

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
		if (isLightOn)
		{
			nbtCompound.putBoolean("isLightOn", true);
		}

		NbtList blockEntities = new NbtList();
		for (BlockPos blockPos : chunk.getBlockEntityPositions())
		{
			NbtCompound beNbt = chunk.getPackedBlockEntityNbt(blockPos);
			if (beNbt != null)
				blockEntities.add(beNbt);
		}

		nbtCompound.put("block_entities", blockEntities);
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
		else
		{
			var entities = ((WorldAccessor)world).getEntityLookup();

			NbtList entityNbts = new NbtList();
			entities.iterate().forEach(entity -> {
				var thisChunkPos = entity.getChunkPos().toLong();

				if (thisChunkPos != posLong)
					return;

				NbtCompound entityNbt = new NbtCompound();
				if (entity.saveNbt(entityNbt))
				{
					entityNbt.putByte("NoAI", (byte)1);
					entityNbts.add(entityNbt);
				}
			});

			nbtCompound.put("entities", entityNbts);
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
