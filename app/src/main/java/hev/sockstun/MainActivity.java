/*
 ============================================================================
 Name        : MainActivity.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : Main Activity
 ============================================================================
 */

package hev.sockstun;

import android.os.Bundle;
import android.content.Intent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.net.VpnService;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
	private Preferences prefs;
	private TextInputLayout til_socks_addr;
	private TextInputLayout til_socks_udp_addr;
	private TextInputLayout til_socks_port;
	private TextInputLayout til_socks_user;
	private TextInputLayout til_socks_pass;
	private TextInputLayout til_dns_ipv4;
	private TextInputLayout til_dns_ipv6;
	private EditText edittext_socks_addr;
	private EditText edittext_socks_udp_addr;
	private EditText edittext_socks_port;
	private EditText edittext_socks_user;
	private EditText edittext_socks_pass;
	private EditText edittext_dns_ipv4;
	private EditText edittext_dns_ipv6;
	private CheckBox checkbox_udp_in_tcp;
	private CheckBox checkbox_remote_dns;
	private CheckBox checkbox_global;
	private CheckBox checkbox_ipv4;
	private CheckBox checkbox_ipv6;
	private Button button_apps;
	private Button button_save;
	private Button button_control;
	private Button button_profile_prev;
	private Button button_profile_next;
	private TextView textview_profile_name;

	/* Refresh the control state when the tunnel is toggled elsewhere
	   (e.g. from the Quick Settings tile) while this screen is visible. */
	private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
		new SharedPreferences.OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
				if (Preferences.ENABLE.equals(key))
				  updateControlState();
			}
		};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		DynamicColors.applyToActivityIfAvailable(this);

		prefs = new Preferences(this);
		setContentView(R.layout.main);

		til_socks_addr = (TextInputLayout) findViewById(R.id.til_socks_addr);
		til_socks_udp_addr = (TextInputLayout) findViewById(R.id.til_socks_udp_addr);
		til_socks_port = (TextInputLayout) findViewById(R.id.til_socks_port);
		til_socks_user = (TextInputLayout) findViewById(R.id.til_socks_user);
		til_socks_pass = (TextInputLayout) findViewById(R.id.til_socks_pass);
		til_dns_ipv4 = (TextInputLayout) findViewById(R.id.til_dns_ipv4);
		til_dns_ipv6 = (TextInputLayout) findViewById(R.id.til_dns_ipv6);
		edittext_socks_addr = (EditText) findViewById(R.id.socks_addr);
		edittext_socks_udp_addr = (EditText) findViewById(R.id.socks_udp_addr);
		edittext_socks_port = (EditText) findViewById(R.id.socks_port);
		edittext_socks_user = (EditText) findViewById(R.id.socks_user);
		edittext_socks_pass = (EditText) findViewById(R.id.socks_pass);
		edittext_dns_ipv4 = (EditText) findViewById(R.id.dns_ipv4);
		edittext_dns_ipv6 = (EditText) findViewById(R.id.dns_ipv6);
		checkbox_ipv4 = (CheckBox) findViewById(R.id.ipv4);
		checkbox_ipv6 = (CheckBox) findViewById(R.id.ipv6);
		checkbox_global = (CheckBox) findViewById(R.id.global);
		checkbox_udp_in_tcp = (CheckBox) findViewById(R.id.udp_in_tcp);
		checkbox_remote_dns = (CheckBox) findViewById(R.id.remote_dns);
		button_apps = (Button) findViewById(R.id.apps);
		button_save = (Button) findViewById(R.id.save);
		button_control = (Button) findViewById(R.id.control);
		button_profile_prev = (Button) findViewById(R.id.profile_prev);
		button_profile_next = (Button) findViewById(R.id.profile_next);
		textview_profile_name = (TextView) findViewById(R.id.profile_name);

		button_profile_prev.setOnClickListener(this);
		button_profile_next.setOnClickListener(this);
		textview_profile_name.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View view) {
				if (prefs.getEnable())
				  return false;
				showProfileMenu();
				return true;
			}
		});
		checkbox_udp_in_tcp.setOnClickListener(this);
		checkbox_remote_dns.setOnClickListener(this);
		checkbox_global.setOnClickListener(this);
		button_apps.setOnClickListener(this);
		button_save.setOnClickListener(this);
		button_control.setOnClickListener(this);
		updateUI();

		/* Request VPN permission */
		Intent intent = VpnService.prepare(MainActivity.this);
		if (intent != null)
		  startActivityForResult(intent, 0);
		else
		  onActivityResult(0, RESULT_OK, null);
	}

	@Override
	protected void onStart() {
		super.onStart();
		prefs.registerOnChange(prefsListener);
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateControlState();
	}

	@Override
	protected void onStop() {
		super.onStop();
		prefs.unregisterOnChange(prefsListener);
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		if ((result == RESULT_OK) && prefs.getEnable()) {
			Intent intent = new Intent(this, TProxyService.class);
			startService(intent.setAction(TProxyService.ACTION_CONNECT));
		}
	}

	@Override
	public void onClick(View view) {
		if (view == checkbox_global || view == checkbox_remote_dns) {
			savePrefs();
			updateUI();
		} else if (view == button_apps) {
			startActivity(new Intent(this, AppListActivity.class));
		} else if (view == button_save) {
			savePrefs();
			Context context = getApplicationContext();
			Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show();
		} else if (view == button_control) {
			boolean isEnable = prefs.getEnable();
			prefs.setEnable(!isEnable);
			savePrefs();
			updateUI();
			Intent intent = new Intent(this, TProxyService.class);
			if (isEnable)
			  startService(intent.setAction(TProxyService.ACTION_DISCONNECT));
			else
			  startService(intent.setAction(TProxyService.ACTION_CONNECT));
			QSTileService.requestUpdate(this);
		} else if (view == button_profile_prev) {
			switchProfile(-1);
		} else if (view == button_profile_next) {
			switchProfile(1);
		}
	}

	private void switchProfile(int direction) {
		if (prefs.getEnable())
		  return;
		int count = prefs.getProfileCount();
		prefs.setSelected((prefs.getSelected() + direction + count) % count);
		updateUI();
	}

	private void showProfileMenu() {
		String[] items = {
			getString(R.string.profile_rename),
			getString(R.string.profile_add),
			getString(R.string.profile_delete),
		};
		new AlertDialog.Builder(this)
			.setTitle(prefs.getProfileName())
			.setItems(items, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
					case 0:
						showProfileNameDialog(R.string.profile_rename, false);
						break;
					case 1:
						if (prefs.getProfileCount() >= Preferences.MAX_PROFILES)
						  Toast.makeText(MainActivity.this, R.string.profile_limit, Toast.LENGTH_SHORT).show();
						else
						  showProfileNameDialog(R.string.profile_add, true);
						break;
					case 2:
						deleteProfile();
						break;
					}
				}
			})
			.show();
	}

	private void showProfileNameDialog(int titleId, final boolean add) {
		final EditText input = new EditText(this);
		input.setText(prefs.getProfileName());
		input.setHint(R.string.profile_name);
		input.setSingleLine(true);
		input.selectAll();

		FrameLayout container = new FrameLayout(this);
		int padding = (int) (16 * getResources().getDisplayMetrics().density);
		container.setPadding(padding, 0, padding, 0);
		container.addView(input);

		new AlertDialog.Builder(this)
			.setTitle(titleId)
			.setView(container)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String name = input.getText().toString().trim();
					if (name.isEmpty())
					  return;
					if (add)
					  prefs.addProfile(name);
					else
					  prefs.setProfileName(name);
					updateUI();
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	private void deleteProfile() {
		if (prefs.getProfileCount() <= 1) {
			Toast.makeText(this, R.string.profile_last, Toast.LENGTH_SHORT).show();
			return;
		}
		new AlertDialog.Builder(this)
			.setMessage(getString(R.string.profile_delete_confirm, prefs.getProfileName()))
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					prefs.deleteProfile();
					updateUI();
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	private void updateUI() {
		edittext_socks_addr.setText(prefs.getSocksAddress());
		edittext_socks_udp_addr.setText(prefs.getSocksUdpAddress());
		edittext_socks_port.setText(Integer.toString(prefs.getSocksPort()));
		edittext_socks_user.setText(prefs.getSocksUsername());
		edittext_socks_pass.setText(prefs.getSocksPassword());
		edittext_dns_ipv4.setText(prefs.getDnsIpv4());
		edittext_dns_ipv6.setText(prefs.getDnsIpv6());
		checkbox_ipv4.setChecked(prefs.getIpv4());
		checkbox_ipv6.setChecked(prefs.getIpv6());
		checkbox_global.setChecked(prefs.getGlobal());
		checkbox_udp_in_tcp.setChecked(prefs.getUdpInTcp());
		checkbox_remote_dns.setChecked(prefs.getRemoteDns());

		updateControlState();
	}

	private void updateControlState() {
		boolean editable = !prefs.getEnable();
		textview_profile_name.setText(prefs.getProfileName());
		button_profile_prev.setEnabled(editable);
		button_profile_next.setEnabled(editable);
		textview_profile_name.setEnabled(editable);
		til_socks_addr.setEnabled(editable);
		til_socks_udp_addr.setEnabled(editable);
		til_socks_port.setEnabled(editable);
		til_socks_user.setEnabled(editable);
		til_socks_pass.setEnabled(editable);
		til_dns_ipv4.setEnabled(editable && !prefs.getRemoteDns());
		til_dns_ipv6.setEnabled(editable && !prefs.getRemoteDns());
		checkbox_udp_in_tcp.setEnabled(editable);
		checkbox_remote_dns.setEnabled(editable);
		checkbox_global.setEnabled(editable);
		checkbox_ipv4.setEnabled(editable);
		checkbox_ipv6.setEnabled(editable);
		button_apps.setEnabled(editable && !prefs.getGlobal());
		button_save.setEnabled(editable);

		if (editable)
		  button_control.setText(R.string.control_enable);
		else
		  button_control.setText(R.string.control_disable);
	}

	private void savePrefs() {
		prefs.setSocksAddress(edittext_socks_addr.getText().toString());
		prefs.setSocksUdpAddress(edittext_socks_udp_addr.getText().toString());
		prefs.setSocksPort(Integer.parseInt(edittext_socks_port.getText().toString()));
		prefs.setSocksUsername(edittext_socks_user.getText().toString());
		prefs.setSocksPassword(edittext_socks_pass.getText().toString());
		prefs.setDnsIpv4(edittext_dns_ipv4.getText().toString());
		prefs.setDnsIpv6(edittext_dns_ipv6.getText().toString());
		if (!checkbox_ipv4.isChecked() && !checkbox_ipv6.isChecked())
		  checkbox_ipv4.setChecked(prefs.getIpv4());
		prefs.setIpv4(checkbox_ipv4.isChecked());
		prefs.setIpv6(checkbox_ipv6.isChecked());
		prefs.setGlobal(checkbox_global.isChecked());
		prefs.setUdpInTcp(checkbox_udp_in_tcp.isChecked());
		prefs.setRemoteDns(checkbox_remote_dns.isChecked());
	}
}
