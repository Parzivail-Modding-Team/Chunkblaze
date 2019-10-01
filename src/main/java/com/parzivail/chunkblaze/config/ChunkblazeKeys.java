package com.parzivail.chunkblaze.config;

import com.parzivail.chunkblaze.Chunkblaze;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

public class ChunkblazeKeys
{
	public static KeyBinding keyControlPanel;

	public static void registerAll()
	{
		keyControlPanel = registerKeybind("controlPanel", Keyboard.KEY_Y);
	}

	public static KeyBinding registerKeybind(String keyName, int keyCode)
	{
		KeyBinding b = new KeyBinding("key." + Chunkblaze.MODID + "." + keyName, keyCode, "key." + Chunkblaze.MODID);
		ClientRegistry.registerKeyBinding(b);
		return b;
	}
}
