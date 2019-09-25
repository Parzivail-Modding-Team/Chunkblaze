package com.parzivail.chunkblaze;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class ChunkblazeConfig extends Configuration
{
	public ChunkblazeConfig(File file)
	{
		super(file);
		load();
	}

	public void load()
	{
		super.load();

		//		prop = get(Configuration.CATEGORY_GENERAL, "propName", defaultValue, "Proprty Description");

		if (hasChanged())
			save();
	}
}
