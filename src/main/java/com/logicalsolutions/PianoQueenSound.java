package com.logicalsolutions;

public enum PianoQueenSound
{
	OFF("Off", null, null),
	BACKGROUND("PQ-Background", "Background", "Background.wav"),
	BAROQUE("PQ-Baroque", "Baroque", "Baroque.wav"),
	BOOK_OF_SPELLS("PQ-Book of Spells", "Book of Spells", "Book of Spells.wav"),
	EXPANSE("PQ-Expanse", "Expanse", "Expanse.wav"),
	FISHING("PQ-Fishing", "Fishing", "Fishing.wav"),
	FOREVER("PQ-Forever", "Forever", "Forever.wav"),
	HOME_SWEET_HOME("PQ-Home Sweet Home", "Home Sweet Home", "Home Sweet Home.wav"),
	HORIZON("PQ-Horizon", "Horizon", "Horizon.wav"),
	MILES_AWAY("PQ-Miles Away", "Miles Away", "Miles Away.wav"),
	NEWBIE_MELODY("PQ-Newbie Melody", "Newbie Melody", "Newbie Melody.wav"),
	TALKING_FOREST("PQ-Talking Forest", "Talking Forest", "Talking Forest.wav"),
	UNKNOWN_LAND("PQ-Unknown Land", "Unknown Land", "Unknown Land.wav");

	private final String name;
	private final String trackName;
	private final String fileName;

	PianoQueenSound(String name, String trackName, String fileName)
	{
		this.name = name;
		this.trackName = trackName;
		this.fileName = fileName;
	}

	public String getTrackName()
	{
		return trackName;
	}

	public String getFileName()
	{
		return fileName;
	}

	public static PianoQueenSound forTrackName(String trackName)
	{
		for (PianoQueenSound sound : values())
		{
			if (sound.trackName != null && sound.trackName.equals(trackName))
			{
				return sound;
			}
		}

		return OFF;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
