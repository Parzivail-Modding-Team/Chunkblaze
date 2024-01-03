package com.parzivail.chunkblaze;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Chunkblaze implements ClientModInitializer
{
	public static final Logger LOGGER = LoggerFactory.getLogger("chunkblaze");

	public static final ChunkHandler CHUNK_HANDLER = new ChunkHandler();

	@Override
	public void onInitializeClient()
	{
	}

	public static boolean isEnabled()
	{
		var mc = MinecraftClient.getInstance();

		if (mc.isInSingleplayer())
			return false;

		return true;
	}
}
