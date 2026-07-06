package com.logicalsolutions;

public enum PianoQueenSound
{
	OFF("Off", null, null),
	BACKGROUND("PQ-Background", "Background", "Background.mp3"),
	BAROQUE("PQ-Baroque", "Baroque", "Baroque.mp3"),
	BOOK_OF_SPELLS("PQ-Book of Spells", "Book of Spells", "Book of Spells.mp3"),
	EXPANSE("PQ-Expanse", "Expanse", "Expanse.mp3"),
	FISHING("PQ-Fishing", "Fishing", "Fishing.mp3"),
	FOREVER("PQ-Forever", "Forever", "Forever.mp3"),
	HOME_SWEET_HOME("PQ-Home Sweet Home", "Home Sweet Home", "Home Sweet Home.mp3"),
	HORIZON("PQ-Horizon", "Horizon", "Horizon.mp3"),
	MILES_AWAY("PQ-Miles Away", "Miles Away", "Miles Away.mp3"),
	NEWBIE_MELODY("PQ-Newbie Melody", "Newbie Melody", "Newbie Melody.mp3"),
	TALKING_FOREST("PQ-Talking Forest", "Talking Forest", "Talking Forest.mp3"),
	UNKNOWN_LAND("PQ-Unknown Land", "Unknown Land", "Unknown Land.mp3");

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
