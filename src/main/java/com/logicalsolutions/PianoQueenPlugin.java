package com.logicalsolutions;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "PianoQueen"
)
public class PianoQueenPlugin extends Plugin
{
	private static final int POH_ENTRY_COOLDOWN_TICKS = 8;

	@Inject
	private Client client;

	@Inject
	private PianoQueenConfig config;

	private boolean wasInHouse;
	private int pohEntryCooldownTicks;
	private int pohMarkerObjectCount;

	@Override
	protected void startUp()
	{
		log.debug("PianoQueen started");
		wasInHouse = false;
		pohEntryCooldownTicks = 0;
		pohMarkerObjectCount = 0;
	}

	@Override
	protected void shutDown()
	{
		log.debug("PianoQueen stopped");
		wasInHouse = false;
		pohEntryCooldownTicks = 0;
		pohMarkerObjectCount = 0;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN)
		{
			wasInHouse = false;
			pohEntryCooldownTicks = 0;
			pohMarkerObjectCount = 0;
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned gameObjectSpawned)
	{
		if (isPohMarkerObject(gameObjectSpawned.getGameObject().getId()))
		{
			pohMarkerObjectCount++;
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned gameObjectDespawned)
	{
		if (isPohMarkerObject(gameObjectDespawned.getGameObject().getId()) && pohMarkerObjectCount > 0)
		{
			pohMarkerObjectCount--;
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (pohEntryCooldownTicks > 0)
		{
			pohEntryCooldownTicks--;
		}

		checkHouseEntry();
	}

	private void checkHouseEntry()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		boolean isInHouse = isInPlayerOwnedHouse();
		if (isInHouse && !wasInHouse && pohEntryCooldownTicks == 0)
		{
			playConfiguredPohEntrySound();
			pohEntryCooldownTicks = POH_ENTRY_COOLDOWN_TICKS;
		}

		wasInHouse = isInHouse;
	}

	private boolean isInPlayerOwnedHouse()
	{
		WorldView worldView = client.getTopLevelWorldView();
		return worldView != null && worldView.isInstance() && pohMarkerObjectCount > 0;
	}

	private static boolean isPohMarkerObject(int objectId)
	{
		return objectId == ObjectID.POH_EXIT_PORTAL
			|| objectId == ObjectID.POH_PORTAL_OP
			|| objectId == ObjectID.POH_PORTAL_NOOP;
	}

	private void playConfiguredPohEntrySound()
	{
		PianoQueenSound sound = config.pohEntrySound();
		if (sound == PianoQueenSound.OFF)
		{
			return;
		}

		client.playSoundEffect(sound.getSoundId());
		log.debug("Played POH entry sound {}", sound);
	}

	@Provides
	PianoQueenConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PianoQueenConfig.class);
	}
}
