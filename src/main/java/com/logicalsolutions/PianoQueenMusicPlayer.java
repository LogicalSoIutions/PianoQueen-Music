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
import java.io.File;
import java.util.Locale;
import lombok.SneakyThrows;
import org.slf4j.LoggerFactory;

public interface PianoQueenMusicPlayer
{
	static PianoQueenMusicPlayer create(File media)
	{
		if (!media.getName().toLowerCase(Locale.ROOT).endsWith(".wav"))
		{
			LoggerFactory.getLogger(PianoQueenMusicPlayer.class).warn("Unsupported PianoQueen sound format: {}", media);
			return null;
		}

		try
		{
			return new AudioCuePlayer(media);
		}
		catch (Exception e)
		{
			LoggerFactory.getLogger(PianoQueenMusicPlayer.class).warn("Couldn't load player for " + media, e);
			return null;
		}
	}

	void play();

	boolean isPlaying();

	void setVolume(double volume);

	default void close()
	{
	}

	class AudioCuePlayer implements PianoQueenMusicPlayer
	{
		private final AudioCue audioCue;
		private double volume = 1;

		@SneakyThrows
		private AudioCuePlayer(File media)
		{
			audioCue = AudioCue.makeStereoCue(media.toURI().toURL(), 1);
			audioCue.open();
		}

		@Override
		public void play()
		{
			if (audioCue.getIsActive(0))
			{
				audioCue.releaseInstance(0);
			}
			audioCue.play(volume);
		}

		@Override
		public boolean isPlaying()
		{
			return audioCue.getIsActive(0);
		}

		@Override
		public void setVolume(double volume)
		{
			this.volume = volume;
			if (audioCue.getIsActive(0))
			{
				audioCue.setVolume(0, volume);
			}
		}

		@Override
		public void close()
		{
			audioCue.close();
		}
	}
}
