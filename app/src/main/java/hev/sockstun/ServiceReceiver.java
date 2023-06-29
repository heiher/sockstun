/*
 ============================================================================
 Name        : ServiceReceiver.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : ServiceReceiver
 ============================================================================
 */

package hev.sockstun;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

public class ServiceReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			Preferences prefs = new Preferences(context);

			/* Auto-start */
			if (prefs.getEnable()) {
				Intent i = VpnService.prepare(context);
				if (i != null) {
					i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					context.startActivity(i);
				}
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					context.startForegroundService(i.setAction(TProxyService.ACTION_CONNECT));
				} else {
					context.startService(i.setAction(TProxyService.ACTION_CONNECT));
				}
			}
		}
	}
}
