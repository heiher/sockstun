/*
 ============================================================================
 Name        : MainActivity.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : Main Activity
 ============================================================================
 */

package hev.sockstun;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Toast;

import hev.sockstun.databinding.MainBinding;

public class MainActivity extends Activity{

    MainBinding binding;
    private Preferences prefs;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = MainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = new Preferences(this);
        binding.global.setOnClickListener(v -> {
            savePrefs();
            updateUI();
        });

        binding.apps.setOnClickListener(v -> {
            startActivity(new Intent(this, AppListActivity.class));
        });

        binding.save.setOnClickListener(v -> {
            savePrefs();
            Context context = getApplicationContext();
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show();
        });

        binding.control.setOnClickListener(v -> {
            NotificationUtils.requestNotificationPermission(this);
            boolean isEnable = prefs.getEnable();
            prefs.setEnable(!isEnable);
            savePrefs();
            updateUI();
            Intent intent = new Intent(this, TProxyService.class);
            if (isEnable) {
                startService(intent.setAction(TProxyService.ACTION_DISCONNECT));
            } else {
                startService(intent.setAction(TProxyService.ACTION_CONNECT));
            }
        });

        updateUI();

        /* Request VPN permission */
        Intent intent = VpnService.prepare(MainActivity.this);
        if (intent != null)
            startActivityForResult(intent, 0);
        else
            onActivityResult(0, RESULT_OK, null);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if ((result == RESULT_OK) && prefs.getEnable()) {
            Intent intent = new Intent(this, TProxyService.class);
            startService(intent.setAction(TProxyService.ACTION_CONNECT));
        }
    }

    private void updateUI() {

        //binding.edittext_socks_addr.setText(prefs.getSocksAddress());
        binding.socksAddr.setText(prefs.getSocksAddress());
        //edittext_socks_port.setText(Integer.toString(prefs.getSocksPort()));
        binding.socksPort.setText(Integer.toString(prefs.getSocksPort()));
//		edittext_socks_user.setText(prefs.getSocksUsername());
        binding.socksUser.setText(prefs.getSocksUsername());
//		edittext_socks_pass.setText(prefs.getSocksPassword());
        binding.socksPass.setText(prefs.getSocksPassword());
//		edittext_dns_ipv4.setText(prefs.getDnsIpv4());
        binding.dnsIpv4.setText(prefs.getDnsIpv4());
//		edittext_dns_ipv6.setText(prefs.getDnsIpv6());
        binding.dnsIpv6.setText(prefs.getDnsIpv6());
//		checkbox_ipv4.setChecked(prefs.getIpv4());
        binding.ipv4.setChecked(prefs.getIpv4());
//		checkbox_ipv6.setChecked(prefs.getIpv6());
        binding.ipv6.setChecked(prefs.getIpv6());
//		checkbox_global.setChecked(prefs.getGlobal());
        binding.global.setChecked(prefs.getGlobal());
//		checkbox_udp_in_tcp.setChecked(prefs.getUdpInTcp());
        binding.udpInTcp.setChecked(prefs.getUdpInTcp());

        boolean editable = !prefs.getEnable();
        binding.socksAddr.setEnabled(editable);
        binding.socksPort.setEnabled(editable);
        binding.socksUser.setEnabled(editable);
        binding.socksPass.setEnabled(editable);
        binding.dnsIpv4.setEnabled(editable);
        binding.dnsIpv6.setEnabled(editable);
        binding.udpInTcp.setEnabled(editable);
        binding.global.setEnabled(editable);
        binding.ipv4.setEnabled(editable);
        binding.ipv6.setEnabled(editable);
        binding.apps.setEnabled(editable && !prefs.getGlobal());
        binding.save.setEnabled(editable);

        if (editable)
            binding.control.setText(R.string.control_enable);
        else
            binding.control.setText(R.string.control_disable);
    }

    private void savePrefs() {
        prefs.setSocksAddress(binding.socksAddr.getText().toString());
        prefs.setSocksPort(Integer.parseInt(binding.socksPort.getText().toString()));
        prefs.setSocksUsername(binding.socksUser.getText().toString());
        prefs.setSocksPassword(binding.socksPass.getText().toString());
        prefs.setDnsIpv4(binding.dnsIpv4.getText().toString());
        prefs.setDnsIpv6(binding.dnsIpv6.getText().toString());
        if (!binding.ipv4.isChecked() && !binding.ipv4.isChecked())
            binding.ipv4.setChecked(prefs.getIpv4());
        prefs.setIpv4(binding.ipv4.isChecked());
        prefs.setIpv6(binding.ipv6.isChecked());
        prefs.setGlobal(binding.global.isChecked());
        prefs.setUdpInTcp(binding.udpInTcp.isChecked());
    }
}
