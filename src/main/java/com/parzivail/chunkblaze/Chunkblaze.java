package com.parzivail.chunkblaze;

import com.parzivail.chunkblaze.handler.ChunkDaemon;
import com.parzivail.chunkblaze.handler.ChunkblazeEventHandler;
import com.parzivail.chunkblaze_gen.Version;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = Chunkblaze.MODID, name = Chunkblaze.NAME, version = Version.VERSION, clientSideOnly = true)
@SideOnly(Side.CLIENT)
public class Chunkblaze
{
	public static class Session
	{
		private static boolean running = false;

		public static int chunksMirrored = 0;

		public static boolean canRun()
		{
			return !Minecraft.getMinecraft().isSingleplayer();
		}

		public static boolean isRunning()
		{
			return canRun() && running;
		}

		public static void setRunning(boolean running)
		{
			Session.running = canRun() && running;
		}
	}

	public static final String MODID = "chunkblaze";
	public static final String NAME = "Chunkblaze";

	private static Logger logger;
	private static File remoteSaveFolder;
	private static ChunkDaemon chunkDaemon;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		logger = event.getModLog();

		remoteSaveFolder = new File(event.getModConfigurationDirectory().getParentFile(), "remote-saves");
		remoteSaveFolder.mkdirs();
	}

	@EventHandler
	public void init(FMLInitializationEvent event)
	{
		MinecraftForge.EVENT_BUS.register(chunkDaemon = new ChunkDaemon());
		MinecraftForge.EVENT_BUS.register(new ChunkblazeEventHandler());

		logger.info("Initialized.");
	}

	@EventHandler
	public void postInit(FMLPostInitializationEvent e)
	{
		ChunkblazeKeys.registerAll();
	}

	public static Logger getLogger()
	{
		return logger;
	}

	public static File getRemoteSaveFolder()
	{
		return remoteSaveFolder;
	}

	public static ChunkDaemon getChunkDaemon()
	{
		return chunkDaemon;
	}
}
