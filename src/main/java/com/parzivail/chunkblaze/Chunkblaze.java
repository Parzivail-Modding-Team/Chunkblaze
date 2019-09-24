package com.parzivail.chunkblaze;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = Chunkblaze.MODID, name = Chunkblaze.NAME, version = Chunkblaze.VERSION)
public class Chunkblaze
{
	public static final String MODID = "chunkblaze";
	public static final String NAME = "Chunkblaze World Downloader";
	public static final String VERSION = "1.0";

	public static Logger logger;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		logger = event.getModLog();
	}

	@EventHandler
	public void init(FMLInitializationEvent event)
	{
		MinecraftForge.EVENT_BUS.register(new ChunkblazeChunkInterceptor());

		logger.info("Initialized.");
	}
}
