package com.parzivail.chunkblaze;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Chunkblaze implements ClientModInitializer
{
	public static final Logger LOGGER = LoggerFactory.getLogger("modid");

	public static final ChunkHandler CHUNK_HANDLER = new ChunkHandler();

	@Override
	public void onInitializeClient()
	{
	}

	public static boolean isEnabled()
	{
		if (mc.isInSingleplayer())
			return false;

		return true;
	}
}
