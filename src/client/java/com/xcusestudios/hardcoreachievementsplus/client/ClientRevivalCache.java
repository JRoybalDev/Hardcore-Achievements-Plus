package com.xcusestudios.hardcoreachievementsplus.client;

import java.util.List;

import com.xcusestudios.hardcoreachievementsplus.net.SyncPayloads.ActiveEntry;
import com.xcusestudios.hardcoreachievementsplus.net.SyncPayloads.CompletionEntry;
import com.xcusestudios.hardcoreachievementsplus.net.SyncPayloads.UpcomingEntry;

/**
 * Client-side copy of the server's panel state. Written only by payload
 * receivers on the client thread; read by the panel screen.
 */
public final class ClientRevivalCache {
	private static List<ActiveEntry> active = List.of();
	private static List<CompletionEntry> completed = List.of();
	private static List<UpcomingEntry> upcoming = List.of();
	private static boolean restoreInventory;

	private ClientRevivalCache() {
	}

	public static void update(List<ActiveEntry> newActive, List<CompletionEntry> newCompleted,
			List<UpcomingEntry> newUpcoming, boolean newRestoreInventory) {
		active = List.copyOf(newActive);
		completed = List.copyOf(newCompleted);
		upcoming = List.copyOf(newUpcoming);
		restoreInventory = newRestoreInventory;
	}

	public static void clear() {
		active = List.of();
		completed = List.of();
		upcoming = List.of();
		restoreInventory = false;
	}

	public static List<UpcomingEntry> upcoming() {
		return upcoming;
	}

	public static List<ActiveEntry> active() {
		return active;
	}

	public static List<CompletionEntry> completed() {
		return completed;
	}

	public static boolean restoreInventory() {
		return restoreInventory;
	}
}
