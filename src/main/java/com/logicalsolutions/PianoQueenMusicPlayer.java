/*
 * Adapted from Music Replacer,
 * Copyright (c) 2020, Alowan
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.logicalsolutions;

import com.adonax.audiocue.AudioCue;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.Function;
import jaco.mp3.player.MP3Player;
import lombok.SneakyThrows;
import org.slf4j.LoggerFactory;

public interface PianoQueenMusicPlayer
{
	ImmutableMap<String, Function<URI, PianoQueenMusicPlayer>> PLAYER_PER_EXT = ImmutableMap.of(
		".mp3", JacoPlayer::new,
		".wav", AudioCuePlayer::new
	);

	static PianoQueenMusicPlayer create(URI media)
	{
		for (Map.Entry<String, Function<URI, PianoQueenMusicPlayer>> extAndPlayer : PLAYER_PER_EXT.entrySet())
		{
			if (media.getPath().endsWith(extAndPlayer.getKey()))
			{
				try
				{
					return extAndPlayer.getValue().apply(media);
				}
				catch (Exception e)
				{
					LoggerFactory.getLogger(PianoQueenMusicPlayer.class).warn("Couldn't load player for " + media, e);
				}
			}
		}

		return null;
	}

	void play();

	boolean isPlaying();

	void setVolume(double volume);

	default void close()
	{
	}

	class JacoPlayer implements PianoQueenMusicPlayer
	{
		public static final MP3Player player = new MP3Player();
		private final File tempPlayFile; // A hacky solution for overriding/deleting current playing song

		@SneakyThrows
		public JacoPlayer(URI mediaFile)
		{
			player.clearPlayList();
			tempPlayFile = File.createTempFile("tmpJacoPlayfile", ".mp3");
			tempPlayFile.deleteOnExit();
			Files.copy(new File(mediaFile).toPath(), tempPlayFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			player.add(tempPlayFile);
		}

		@Override
		public void play()
		{
			player.play();
		}

		@Override
		public boolean isPlaying()
		{
			return player.isPlaying();
		}

		@Override
		public void setVolume(double volume)
		{
			int intVol = (int) (volume * 100);
			if (volume > 0 && intVol == 0)
			{
				intVol = 1;
			}
			player.setVolume(intVol);
		}

		@Override
		public void close()
		{
			player.stop();
			tempPlayFile.delete();
		}
	}

	class AudioCuePlayer implements PianoQueenMusicPlayer
	{
		// Same hacky solution as JaCo even though it shouldn't happen here cause audiocue fully loads in memory
		private final File tempPlayFile;
		private final AudioCue audioCue;

		@SneakyThrows
		private AudioCuePlayer(URI media)
		{
			tempPlayFile = File.createTempFile("tmpJacoPlayfile", ".mp3");
			tempPlayFile.deleteOnExit();
			Files.copy(new File(media).toPath(), tempPlayFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			audioCue = AudioCue.makeStereoCue(tempPlayFile.toURL(), 1);
			audioCue.open();
		}

		@Override
		public void play()
		{
			if (audioCue.getIsActive(0))
			{
				audioCue.releaseInstance(0);
			}
			audioCue.play();
		}

		@Override
		public boolean isPlaying()
		{
			return audioCue.getIsActive(0);
		}

		@Override
		public void setVolume(double volume)
		{
			audioCue.setVolume(0, volume);
		}

		@Override
		public void close()
		{
			tempPlayFile.delete();
			audioCue.close();
		}
	}
}
