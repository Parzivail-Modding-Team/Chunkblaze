package com.parzivail.chunkblaze;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

public class ChunkblazeConfig extends Configuration
{
	private Property verbose;

	public ChunkblazeConfig(File file)
	{
		super(file);
		load();
	}

	public void load()
	{
		super.load();

		verbose = get(Configuration.CATEGORY_GENERAL, "verbose", false, "Set to true to enable verbose logging.");

		if (hasChanged())
			save();
	}

	public boolean getVerbose()
	{
		return verbose.getBoolean();
	}
}
