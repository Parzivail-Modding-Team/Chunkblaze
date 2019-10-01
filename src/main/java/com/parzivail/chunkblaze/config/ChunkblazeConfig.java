package com.parzivail.chunkblaze.config;

import com.parzivail.chunkblaze.Chunkblaze;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = Chunkblaze.MODID)
@Config.LangKey("chunkblaze.config.category.general")
public class ChunkblazeConfig extends Configuration
{
	@Config.LangKey("chunkblaze.config.entry.verbose")
	@Config.Comment("Set to true to enable verbose logging.")
	public static boolean verbose = false;

	@Config.LangKey("chunkblaze.config.entry.launchRunning")
	@Config.Comment({
			                "Set to false to launch worlds Stopped instead of Mirroring.",
			                "Be warned, this will not mirror any of the chunks that you spawn in until they're re-sent by the server."
	                })
	public static boolean launchRunning = true;

	@Mod.EventBusSubscriber
	private static class EventHandler
	{
		@SubscribeEvent
		public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event)
		{
			if (event.getModID().equals(Chunkblaze.MODID))
				ConfigManager.sync(Chunkblaze.MODID, Config.Type.INSTANCE);
		}
	}
}
