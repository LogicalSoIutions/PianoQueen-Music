package com.logicalsolutions;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("piano-queen")
public interface PianoQueenConfig extends Config
{
	@ConfigItem(
		keyName = "pohEntrySound",
		name = "POH entry sound",
		description = "The sound to play when entering your player-owned house"
	)
	default PianoQueenSound pohEntrySound()
	{
		return PianoQueenSound.TELEPORT;
	}
}
