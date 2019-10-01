package com.parzivail.chunkblaze.handler;

import com.parzivail.chunkblaze.config.ChunkblazeKeys;
import com.parzivail.chunkblaze.gui.GuiControlPanel;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ChunkblazeEventHandler
{
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void on(InputEvent.KeyInputEvent e)
	{
		if (ChunkblazeKeys.keyControlPanel.isPressed())
		{
			Minecraft mc = Minecraft.getMinecraft();
			mc.displayGuiScreen(new GuiControlPanel(mc.currentScreen));
		}
	}
}
