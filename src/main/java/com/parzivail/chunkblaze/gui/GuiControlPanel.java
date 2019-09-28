package com.parzivail.chunkblaze.gui;

import com.parzivail.chunkblaze.Chunkblaze;
import com.parzivail.chunkblaze.io.IOUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;

public class GuiControlPanel extends GuiScreen
{
	protected final GuiScreen parent;

	private GuiButtonExt bStart;
	private GuiButtonExt bStop;
	private GuiButtonExt bShowFolder;

	public GuiControlPanel(GuiScreen parent)
	{
		this.parent = parent;
	}

	@Override
	public void initGui()
	{
		super.initGui();
		buttonList.clear();

		int startX = width / 2 - 195;
		int startY = height / 6 + 40;

		buttonList.add(bStart = new GuiButtonExt(0, startX, startY, 170, 20, I18n.format("chunkblaze.gui.controlpanel.start")));
		buttonList.add(bShowFolder = new GuiButtonExt(2, startX, startY + 88, 170, 20, I18n.format("chunkblaze.gui.controlpanel.showFolder")));

		buttonList.add(bStop = new GuiButtonExt(1, startX + 215, startY, 170, 20, I18n.format("chunkblaze.gui.controlpanel.stop")));

		boolean canRun = Chunkblaze.Session.canRun();
		boolean running = Chunkblaze.Session.isRunning();

		bStart.enabled = canRun && !running;
		bStop.enabled = !bStart.enabled;
		bShowFolder.enabled = canRun;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks)
	{
		drawDefaultBackground();

		String title = I18n.format("chunkblaze.gui.controlpanel.title");
		drawCenteredString(fontRenderer, TextFormatting.BOLD + title, width / 2, 15, 0xFFFFFF);

		String status = I18n.format(String.format("chunkblaze.gui.controlpanel.%s", Chunkblaze.Session.isRunning() ? "running" : "stopped"));
		drawCenteredString(fontRenderer, status, width / 2, 45, 0xFFFFFF);

		String chunksMirrored = I18n.format("chunkblaze.gui.controlpanel.chunksMirrored", Chunkblaze.Session.chunksMirrored);
		drawCenteredString(fontRenderer, chunksMirrored, width / 2, 60, 0xFFFFFF);

		int startX = width / 2 - 195;
		int startY = height / 6 + 40;

		String worldName = Chunkblaze.Session.canRun() ? IOUtils.getWorldName() : I18n.format("chunkblaze.gui.controlpanel.warnSingleplayer");
		drawString(fontRenderer, worldName, startX + 215, startY + 94, 0xFFFFFF);

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException
	{
		if (keyCode == Keyboard.KEY_ESCAPE)
			returnToParent();
	}

	@Override
	protected void actionPerformed(GuiButton button) throws IOException
	{
		super.actionPerformed(button);

		if (button == bStart)
		{
			Chunkblaze.Session.setRunning(true);
			initGui();
		}
		else if (button == bStop)
		{
			Chunkblaze.Session.setRunning(false);
			initGui();
		}
		else if (button == bShowFolder)
		{
			File file = Chunkblaze.getChunkDaemon().getCurrentWorldDirectory();
			OpenGlHelper.openFile(file);
		}
	}

	private void returnToParent()
	{
		mc.displayGuiScreen(parent);

		if (mc.currentScreen == null)
			mc.setIngameFocus();
	}
}
