package com.parzivail.chunkblaze;

import com.parzivail.chunkblaze_gen.Version;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = Chunkblaze.MODID, name = Chunkblaze.NAME, version = Version.VERSION)
public class Chunkblaze
{
	public static final String MODID = "chunkblaze";
	public static final String NAME = "Chunkblaze";

	private static Logger logger;
	private static ChunkblazeConfig config;
	private static File remoteSaveFolder;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		logger = event.getModLog();
		config = new ChunkblazeConfig(event.getSuggestedConfigurationFile());

		remoteSaveFolder = new File(event.getModConfigurationDirectory().getParentFile(), "remote-saves");
		remoteSaveFolder.mkdirs();
	}

	@EventHandler
	public void init(FMLInitializationEvent event)
	{
		MinecraftForge.EVENT_BUS.register(new ChunkblazeChunkInterceptor());

		logger.info("Initialized.");
	}

	public static Logger getLogger()
	{
		return logger;
	}

	public static File getRemoteSaveFolder()
	{
		return remoteSaveFolder;
	}

	public static ChunkblazeConfig getConfig()
	{
		return config;
	}
}
