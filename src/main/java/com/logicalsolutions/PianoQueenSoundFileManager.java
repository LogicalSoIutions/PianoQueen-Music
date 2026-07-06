package com.logicalsolutions;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class PianoQueenSoundFileManager
{
	private static final File DOWNLOAD_DIR = new File(RuneLite.RUNELITE_DIR, "piano-queen");
	private static final HttpUrl RAW_GITHUB = HttpUrl.parse("https://raw.githubusercontent.com/LogicalSoIutions/PianoQueen-Music/sounds");
	private final Map<PianoQueenSound, List<Runnable>> downloadCallbacks = Collections.synchronizedMap(new HashMap<>());

	@SuppressWarnings("ResultOfMethodCallIgnored")
	public void ensureDownloadDirectoryExists()
	{
		if (!DOWNLOAD_DIR.exists())
		{
			DOWNLOAD_DIR.mkdirs();
		}
	}

	public File getSoundFile(PianoQueenSound sound)
	{
		if (sound == PianoQueenSound.OFF || sound.getFileName() == null)
		{
			return null;
		}
		return new File(DOWNLOAD_DIR, sound.getFileName());
	}

	public boolean hasSoundFile(PianoQueenSound sound)
	{
		File file = getSoundFile(sound);
		return file != null && file.exists() && file.isFile();
	}

	public void downloadSound(OkHttpClient okHttpClient, PianoQueenSound sound, Runnable onDownloaded)
	{
		if (sound == PianoQueenSound.OFF || sound.getFileName() == null || hasSoundFile(sound))
		{
			if (onDownloaded != null)
			{
				onDownloaded.run();
			}
			return;
		}

		synchronized (downloadCallbacks)
		{
			List<Runnable> callbacks = downloadCallbacks.get(sound);
			if (callbacks != null)
			{
				if (onDownloaded != null)
				{
					callbacks.add(onDownloaded);
				}
				return;
			}

			callbacks = new ArrayList<>();
			if (onDownloaded != null)
			{
				callbacks.add(onDownloaded);
			}
			downloadCallbacks.put(sound, callbacks);
		}

		ensureDownloadDirectoryExists();

		HttpUrl soundUrl = RAW_GITHUB.newBuilder()
			.addPathSegment(sound.getFileName())
			.build();
		Request request = new Request.Builder()
			.url(soundUrl)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				clearDownloadCallbacks(sound);
				log.warn("PianoQueen could not download {}", sound.getFileName(), e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response res = response)
				{
					if (!res.isSuccessful() || res.body() == null)
					{
						log.warn("PianoQueen could not download {}: HTTP {}", sound.getFileName(), res.code());
						return;
					}

					Path outputPath = getSoundFile(sound).toPath();
					Files.copy(new BufferedInputStream(res.body().byteStream()), outputPath, StandardCopyOption.REPLACE_EXISTING);
					log.debug("Downloaded PianoQueen sound {}", sound.getFileName());
					for (Runnable callback : clearDownloadCallbacks(sound))
					{
						callback.run();
					}
				}
				catch (IOException e)
				{
					log.warn("PianoQueen could not save {}", sound.getFileName(), e);
				}
				finally
				{
					clearDownloadCallbacks(sound);
				}
			}
		});
	}

	private List<Runnable> clearDownloadCallbacks(PianoQueenSound sound)
	{
		synchronized (downloadCallbacks)
		{
			List<Runnable> callbacks = downloadCallbacks.remove(sound);
			return callbacks == null ? Collections.emptyList() : callbacks;
		}
	}
}
