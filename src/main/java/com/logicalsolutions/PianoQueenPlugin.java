package com.logicalsolutions;

import com.google.inject.Provides;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.GameState;
import net.runelite.api.WorldView;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "PianoQueen"
)
public class PianoQueenPlugin extends Plugin
{
	private static final int POH_ENTRY_COOLDOWN_TICKS = 8;
	private static final int RETRIGGER_MUSIC_SCRIPT = 9238;
	private static final double MAX_VOL_OPTION = 100.0;

	@Inject
	private Client client;

	@Inject
	private PianoQueenConfig config;

	@Inject
	private PianoQueenSoundFileManager soundFileManager;

	@Inject
	private PianoQueenSoundEngine soundEngine;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	private boolean wasInHouse;
	private int pohEntryCooldownTicks;

	private String lastPlayingTrack;
	private boolean overrideWidgetsOutdated = true;
	private String actualCurTrack;
	private PianoQueenSound activeSound;
	private boolean inPohSession;
	private boolean shouldRetriggerMusic;

	@Override
	protected void startUp()
	{
		log.debug("PianoQueen started");
		wasInHouse = false;
		pohEntryCooldownTicks = 0;
		overrideWidgetsOutdated = true;
		soundFileManager.ensureDownloadDirectoryExists();
		for (PianoQueenSound sound : PianoQueenSound.values())
		{
			if (sound != PianoQueenSound.OFF)
			{
				soundFileManager.downloadSound(okHttpClient, sound, () -> {
					overrideWidgetsOutdated = true;
				});
			}
		}
	}

	@Override
	protected void shutDown()
	{
		log.debug("PianoQueen stopped");
		wasInHouse = false;
		pohEntryCooldownTicks = 0;
		soundEngine.stop();
		activeSound = null;
		inPohSession = false;

		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				int musicVol = client.getVarpValue(VarPlayerID.OPTION_MUSIC);
				client.runScript(RETRIGGER_MUSIC_SCRIPT, InterfaceID.SettingsSide.MUSIC_SLIDER_BOBBLE, 0, 116, 1);
				client.runScript(RETRIGGER_MUSIC_SCRIPT, InterfaceID.SettingsSide.MUSIC_SLIDER_BOBBLE, musicVol, 116, 1);
			}

			Widget curTrackWidget = client.getWidget(InterfaceID.Music.NOW_PLAYING_TEXT);
			Widget playingWidget = client.getWidget(InterfaceID.Music.NOW_PLAYING_TITLE);
			if (curTrackWidget != null && actualCurTrack != null)
			{
				curTrackWidget.setText(actualCurTrack);
			}
			if (playingWidget != null)
			{
				playingWidget.setFontId(FontID.PLAIN_12);
			}
			actualCurTrack = null;

			Widget trackList = client.getWidget(InterfaceID.Music.JUKEBOX);
			if (trackList != null)
			{
				for (Widget e : trackList.getDynamicChildren())
				{
					String text = e.getText();
					if (text != null && text.startsWith("PQ-"))
					{
						e.setText(text.substring(3));
					}
					e.setFontId(FontID.PLAIN_12);
					e.revalidate();
				}
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			WorldView worldView = client.getTopLevelWorldView();
			boolean isInstance = worldView != null && worldView.isInstance();
			int[] regions = client.getMapRegions();
			String regionsStr = java.util.Arrays.toString(regions);
			log.info("Transitioned to LOGGED_IN. isInstance={}, regions={}",
				isInstance, regionsStr);

			if (isInPlayerOwnedHouse() && config.pohEntrySound() != PianoQueenSound.OFF)
			{
				client.setMusicVolume(0);
				log.debug("Pre-emptively muted OSRS music upon entering POH");
			}
		}
		else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN || gameStateChanged.getGameState() == GameState.HOPPING)
		{
			if (activeSound != null)
			{
				soundEngine.stop();
				activeSound = null;
			}
			inPohSession = false;
			wasInHouse = false;
			pohEntryCooldownTicks = 0;
			actualCurTrack = null;
			overrideWidgetsOutdated = true;
			shouldRetriggerMusic = false;
		}
		else if (gameStateChanged.getGameState() == GameState.LOADING)
		{
			boolean shouldStop = true;
			if (inPohSession && !config.stopPlayingOnLeave())
			{
				shouldStop = false;
			}

			if (shouldStop && activeSound != null)
			{
				log.debug("Stopping active sound {} on game state change to {}", activeSound, gameStateChanged.getGameState());
				soundEngine.stop();
				activeSound = null;
				shouldRetriggerMusic = true;
			}

			if (shouldStop)
			{
				inPohSession = false;
				actualCurTrack = null;
			}
			overrideWidgetsOutdated = true;
		}
	}


	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (pohEntryCooldownTicks > 0)
		{
			pohEntryCooldownTicks--;
		}
	}

	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		// Handle POH entry/leave checks
		boolean isInHouse = isInPlayerOwnedHouse();
		if (isInHouse && !wasInHouse)
		{
			log.info("POH entry detected check. isInHouse={}, wasInHouse={}, pohEntryCooldownTicks={}", 
				isInHouse, wasInHouse, pohEntryCooldownTicks);
		}

		if (isInHouse && !wasInHouse && pohEntryCooldownTicks == 0)
		{
			pohEntryCooldownTicks = POH_ENTRY_COOLDOWN_TICKS;
			PianoQueenSound entrySound = config.pohEntrySound();
			if (entrySound != PianoQueenSound.OFF)
			{
				inPohSession = true;
				if (activeSound == entrySound && soundEngine.isPlaying())
				{
					log.debug("POH entry sound is already playing, not restarting");
				}
				else
				{
					activeSound = entrySound;
					if (soundFileManager.hasSoundFile(entrySound))
					{
						soundEngine.play(soundFileManager.getSoundFile(activeSound), getEffectiveVolume(), executor);
					}
					else
					{
						soundFileManager.downloadSound(okHttpClient, entrySound, () -> {
							if (inPohSession && activeSound == entrySound)
							{
								soundEngine.play(soundFileManager.getSoundFile(entrySound), getEffectiveVolume(), executor);
							}
						});
					}
				}
			}
		}
		else if (!isInHouse && wasInHouse)
		{
			if (config.stopPlayingOnLeave() && inPohSession)
			{
				soundEngine.stop();
				activeSound = null;
				inPohSession = false;
				shouldRetriggerMusic = true;
			}
		}
		wasInHouse = isInHouse;

		// Update track list overrides if outdated
		if (overrideWidgetsOutdated)
		{
			updateOverridesInTrackList();
		}

		// Fetch NOW_PLAYING widgets
		Widget curTrackWidget = client.getWidget(InterfaceID.Music.NOW_PLAYING_TEXT);
		Widget playingWidget = client.getWidget(InterfaceID.Music.NOW_PLAYING_TITLE);
		if (curTrackWidget == null || playingWidget == null)
		{
			applyVolume();
			return;
		}

		String curTrack = curTrackWidget.getText();
		if (curTrack == null || curTrack.isEmpty())
		{
			applyVolume();
			return;
		}

		// If the track name doesn't start with "PQ-", the game client has started playing a new track.
		if (!curTrack.startsWith("PQ-"))
		{
			actualCurTrack = null;
		}

		// Update lastPlayingTrack to detect change from the client
		if (!curTrack.startsWith("PQ-") && !curTrack.equals(lastPlayingTrack))
		{
			lastPlayingTrack = curTrack;
			overrideWidgetsOutdated = true;
		}

		// Determine target sound if not in POH session
		if (!inPohSession)
		{
			String originalTrack = actualCurTrack != null ? actualCurTrack : curTrack;
			PianoQueenSound targetSound = PianoQueenSound.forTrackName(originalTrack);
			if (targetSound != PianoQueenSound.OFF && soundFileManager.hasSoundFile(targetSound))
			{
				if (activeSound != targetSound)
				{
					activeSound = targetSound;
					soundEngine.play(soundFileManager.getSoundFile(activeSound), getEffectiveVolume(), executor);
				}
			}
			else
			{
				if (activeSound != null)
				{
					soundEngine.stop();
					activeSound = null;
					shouldRetriggerMusic = true;
				}
			}
		}

		// Handle Now Playing widget texts
		if (activeSound != null)
		{
			if (actualCurTrack == null)
			{
				actualCurTrack = curTrack;
			}
			curTrackWidget.setText(activeSound.toString());
			playingWidget.setFontId(FontID.BOLD_12);
		}
		else
		{
			if (actualCurTrack != null)
			{
				curTrackWidget.setText(actualCurTrack);
				playingWidget.setFontId(FontID.PLAIN_12);
				actualCurTrack = null;
			}
		}

		// Check if activeSound finished playing
		if (activeSound != null && !soundEngine.isPlaying())
		{
			if (client.getVarbitValue(VarbitID.MUSIC_ENABLELOOP) == 1)
			{
				soundEngine.play(soundFileManager.getSoundFile(activeSound), getEffectiveVolume(), executor);
			}
			else
			{
				soundEngine.stop();
				activeSound = null;
				inPohSession = false;
				shouldRetriggerMusic = true;
			}
		}

		applyVolume();
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged varClientIntChanged)
	{
		if (varClientIntChanged.getIndex() == VarClientID.TOPLEVEL_PANEL && client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL) == 13)
		{
			overrideWidgetsOutdated = true;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		int param1 = event.getParam1();
		if (param1 == InterfaceID.Music.JUKEBOX
			|| param1 == InterfaceID.Music.SHUFFLE
			|| param1 == InterfaceID.Music.SINGLE
			|| param1 == InterfaceID.Music.SKIP)
		{
			if (inPohSession)
			{
				log.debug("User interacted with music controls; terminating POH session");
				inPohSession = false;
				actualCurTrack = null;
			}
		}
	}

	@Subscribe(priority = -100)
	public void onScriptCallbackEvent(ScriptCallbackEvent ev)
	{
		if ("musicTrackFilter".equals(ev.getEventName()))
		{
			overrideWidgetsOutdated = true;
			String searchVal = null;
			if (chatboxPanelManager != null)
			{
				net.runelite.client.game.chatbox.ChatboxInput currentInput = chatboxPanelManager.getCurrentInput();
				if (currentInput == null)
				{
					log.debug("musicTrackFilter: currentInput is null");
				}
				else
				{
					log.debug("musicTrackFilter: currentInput class = {}", currentInput.getClass().getName());
					if (currentInput instanceof net.runelite.client.game.chatbox.ChatboxTextInput)
					{
						net.runelite.client.game.chatbox.ChatboxTextInput textInput = (net.runelite.client.game.chatbox.ChatboxTextInput) currentInput;
						log.debug("musicTrackFilter: prompt = '{}'", textInput.getPrompt());
						if ("Search music list".equals(textInput.getPrompt()))
						{
							searchVal = textInput.getValue().toLowerCase();
							log.debug("musicTrackFilter: searchVal = '{}'", searchVal);
						}
					}
				}
			}

			if (searchVal != null && !searchVal.isEmpty())
			{
				int dbrow = client.getIntStack()[client.getIntStackSize() - 2];
				Object[] displayNameFields = client.getDBTableField(dbrow, DBTableID.Music.COL_DISPLAYNAME, 0);
				if (displayNameFields.length > 0 && displayNameFields[0] instanceof String)
				{
					String trackName = (String) displayNameFields[0];
					PianoQueenSound pqSound = PianoQueenSound.forTrackName(trackName);
					if (pqSound != PianoQueenSound.OFF && soundFileManager.hasSoundFile(pqSound))
					{
						String pqDisplayName = pqSound.toString().toLowerCase();
						if (pqDisplayName.contains(searchVal))
						{
							log.debug("musicTrackFilter: Matching PQ track: {}, forcing visible", pqDisplayName);
							client.getIntStack()[client.getIntStackSize() - 3] = 1;
						}
					}
				}
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (!"piano-queen".equals(configChanged.getGroup()))
		{
			return;
		}

		overrideWidgetsOutdated = true;
		soundEngine.setVolume(getEffectiveVolume());
	}

	private void updateOverridesInTrackList()
	{
		clientThread.invokeLater(() ->
		{
			Widget trackList = client.getWidget(InterfaceID.Music.JUKEBOX);
			if (trackList == null || trackList.getDynamicChildren() == null || trackList.getDynamicChildren().length == 0) return;

			for (Widget e : trackList.getDynamicChildren())
			{
				String trackName = e.getText();
				if (trackName != null)
				{
					if (trackName.startsWith("PQ-"))
					{
						trackName = trackName.substring(3);
					}
					PianoQueenSound pqSound = PianoQueenSound.forTrackName(trackName);
					if (pqSound != PianoQueenSound.OFF && soundFileManager.hasSoundFile(pqSound))
					{
						e.setText(pqSound.toString());
						e.setFontId(FontID.BOLD_12);
					}
					else
					{
						e.setText(trackName);
						e.setFontId(FontID.PLAIN_12);
					}
					e.revalidate();
				}
			}
			overrideWidgetsOutdated = false;
		});
	}

	private double getEffectiveVolume()
	{
		double masterVol = client.getVarpValue(VarPlayerID.OPTION_MASTER_VOLUME) / MAX_VOL_OPTION;
		double musicVol = client.getVarpValue(VarPlayerID.OPTION_MUSIC) / MAX_VOL_OPTION;
		double effectiveVol = masterVol * musicVol;
		return effectiveVol * effectiveVol;
	}

	private void applyVolume()
	{
		double effectiveVolume = getEffectiveVolume();
		if (activeSound == null)
		{
			if (shouldRetriggerMusic && client.getGameState() == GameState.LOGGED_IN)
			{
				shouldRetriggerMusic = false;
				int musicVol = client.getVarpValue(VarPlayerID.OPTION_MUSIC);
				log.debug("Executing OSRS music retrigger script for volume: {}", musicVol);
				client.runScript(RETRIGGER_MUSIC_SCRIPT, InterfaceID.SettingsSide.MUSIC_SLIDER_BOBBLE, 0, 116, 1);
				client.runScript(RETRIGGER_MUSIC_SCRIPT, InterfaceID.SettingsSide.MUSIC_SLIDER_BOBBLE, musicVol, 116, 1);
			}
		}
		else
		{
			if (soundEngine.isPlaying())
			{
				soundEngine.setVolume(effectiveVolume);
			}
			client.setMusicVolume(0);
		}
	}

	private static final int[] POH_REGIONS = {7534, 7535, 7790, 7791, 8046, 8047, 8302, 8303};

	private boolean isInPlayerOwnedHouse()
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || !worldView.isInstance())
		{
			return false;
		}

		int[] regions = client.getMapRegions();
		if (regions != null)
		{
			for (int region : regions)
			{
				for (int pohRegion : POH_REGIONS)
				{
					if (region == pohRegion)
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	@Provides
	PianoQueenConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PianoQueenConfig.class);
	}
}
