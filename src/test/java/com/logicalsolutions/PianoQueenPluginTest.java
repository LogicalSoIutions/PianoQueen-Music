package com.logicalsolutions;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PianoQueenPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PianoQueenPlugin.class);
		RuneLite.main(args);
	}
}
