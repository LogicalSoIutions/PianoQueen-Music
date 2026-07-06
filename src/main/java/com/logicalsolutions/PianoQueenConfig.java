package com.logicalsolutions;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("piano-queen")
public interface PianoQueenConfig extends Config
{
	@ConfigItem(
		keyName = "pohEntrySound",
		name = "POH entry song",
		description = "The PianoQueen song to play when entering your player-owned house"
	)
	default PianoQueenSound pohEntrySound()
	{
		return PianoQueenSound.HOME_SWEET_HOME;
	}

	@ConfigItem(
		keyName = "stopPlayingOnLeave",
		name = "Stop music on exit",
		description = "Stop playing the POH entry song when leaving your player-owned house"
	)
	default boolean stopPlayingOnLeave()
	{
		return true;
	}
}
