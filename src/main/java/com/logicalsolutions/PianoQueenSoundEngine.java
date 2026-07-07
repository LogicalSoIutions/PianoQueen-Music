package com.logicalsolutions;

import java.io.File;
import java.util.concurrent.Executor;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class PianoQueenSoundEngine
{
	private final Object playerLock = new Object();
	private PianoQueenMusicPlayer player;
	private boolean loading = false;

	public void play(File file, double volume, Executor executor)
	{
		if (file == null)
		{
			return;
		}

		synchronized (playerLock)
		{
			loading = true;
		}
		executor.execute(() -> playInternal(file, volume));
	}

	public void stop()
	{
		synchronized (playerLock)
		{
			if (player != null)
			{
				player.close();
				player = null;
			}
			loading = false;
		}
	}

	public boolean isPlaying()
	{
		synchronized (playerLock)
		{
			return loading || (player != null && player.isPlaying());
		}
	}

	public void setVolume(double volume)
	{
		synchronized (playerLock)
		{
			if (player != null)
			{
				player.setVolume(normalizeVolume(volume));
			}
		}
	}

	private void playInternal(File file, double volume)
	{
		synchronized (playerLock)
		{
			if (player != null)
			{
				player.close();
				player = null;
			}
		}

		try
		{
			PianoQueenMusicPlayer nextPlayer = PianoQueenMusicPlayer.create(file);
			if (nextPlayer == null)
			{
				log.warn("Failed to create PianoQueen player for {}", file.getName());
				synchronized (playerLock)
				{
					loading = false;
				}
				return;
			}

			nextPlayer.setVolume(normalizeVolume(volume));
			synchronized (playerLock)
			{
				player = nextPlayer;
				loading = false;
				nextPlayer.play();
			}
		}
		catch (RuntimeException e)
		{
			log.warn("Failed to play PianoQueen sound {}", file.getName(), e);
			synchronized (playerLock)
			{
				loading = false;
			}
		}
	}

	private static double normalizeVolume(double volume)
	{
		return Math.max(0, Math.min(volume, 1));
	}
}
