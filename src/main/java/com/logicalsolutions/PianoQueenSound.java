package com.logicalsolutions;

import net.runelite.api.SoundEffectID;

public enum PianoQueenSound
{
	OFF("Off", -1),
	TELEPORT("Teleport", SoundEffectID.TELEPORT_VWOOP),
	BELL("Bell", SoundEffectID.TOWN_CRIER_BELL_DING),
	DINGALING("Dingaling", SoundEffectID.GE_ADD_OFFER_DINGALING),
	BLOOP("Bloop", SoundEffectID.GE_COLLECT_BLOOP),
	BOOP("Boop", SoundEffectID.UI_BOOP);

	private final String name;
	private final int soundId;

	PianoQueenSound(String name, int soundId)
	{
		this.name = name;
		this.soundId = soundId;
	}

	public int getSoundId()
	{
		return soundId;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
