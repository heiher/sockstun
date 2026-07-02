/*
 ============================================================================
 Name        : QSTileService.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2024 xyz
 Description : Quick Settings Tile Service
 ============================================================================
 */

package hev.sockstun;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class QSTileService extends TileService {
	@Override
	public void onStartListening() {
		super.onStartListening();
		updateTile();
	}

	@Override
	public void onClick() {
		super.onClick();

		Preferences prefs = new Preferences(this);
		if (prefs.getEnable()) {
			/* Stop the tunnel */
			prefs.setEnable(false);
			Intent intent = new Intent(this, TProxyService.class);
			startService(intent.setAction(TProxyService.ACTION_DISCONNECT));
			updateTile();
			return;
		}

		/* Start the tunnel */
		Intent prepare = VpnService.prepare(this);
		if (prepare != null) {
			/* VPN permission has not been granted yet: let MainActivity
			   request it and start the tunnel afterwards. */
			prefs.setEnable(true);
			Intent intent = new Intent(this, MainActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
				PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
					PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
				startActivityAndCollapse(pi);
			} else {
				startActivityAndCollapse(intent);
			}
			return;
		}

		prefs.setEnable(true);
		Intent intent = new Intent(this, TProxyService.class);
		intent.setAction(TProxyService.ACTION_CONNECT);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		  startForegroundService(intent);
		else
		  startService(intent);
		updateTile();
	}

	private void updateTile() {
		Tile tile = getQsTile();
		if (tile == null)
		  return;
		boolean enable = new Preferences(this).getEnable();
		tile.setState(enable ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
		tile.setLabel(getString(R.string.app_name));
		tile.updateTile();
	}

	/* Ask the system to refresh the tile so it reflects the current state
	   even when it was toggled from somewhere else. */
	public static void requestUpdate(Context context) {
		TileService.requestListeningState(context,
			new ComponentName(context, QSTileService.class));
	}
}
