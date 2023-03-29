/*
 ============================================================================
 Name        : AppListActivity.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : App List Activity
 ============================================================================
 */

package hev.sockstun;

import android.Manifest;
import android.app.ListActivity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import hev.sockstun.databinding.AppitemBinding;

public class AppListActivity extends ListActivity {

	private Preferences prefs;
	public PackageManager pm;
	private boolean isChanged = false;

	private class Package {
		public PackageInfo info;
		public boolean selected;
		public String label;

		public Package(PackageInfo info, boolean selected, String label) {
			this.info = info;
			this.selected = selected;
			this.label = label;
		}
	}

	private class AppArrayAdapter extends ArrayAdapter<Package> {

		public AppArrayAdapter(Context context) {
			super(context, R.layout.appitem);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			AppitemBinding binding;
			if (convertView==null){
				binding=AppitemBinding.inflate(getLayoutInflater(),parent,false);
				convertView = binding.getRoot();
				convertView.setTag(R.id.viewBinding, binding);
			}else {
				binding = (AppitemBinding) convertView.getTag(R.id.viewBinding);
			}

			Package pkg = getItem(position);
			ApplicationInfo appinfo = pkg.info.applicationInfo;
			binding.icon.setImageDrawable(appinfo.loadIcon(pm));
			binding.name.setText(appinfo.loadLabel(pm));
			binding.checked.setChecked(pkg.selected);

			return convertView;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		pm = getPackageManager();
		getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

		prefs = new Preferences(this);
		Set<String> apps = prefs.getApps();
		AppArrayAdapter adapter = new AppArrayAdapter(this);

		for (PackageInfo info : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
			if (info.packageName.equals(getPackageName()))
			  continue;
			if (info.requestedPermissions == null)
			  continue;
			if (!Arrays.asList(info.requestedPermissions).contains(Manifest.permission.INTERNET))
			  continue;
			boolean selected = apps.contains(info.packageName);
			String label = info.applicationInfo.loadLabel(pm).toString();
			Package pkg = new Package(info, selected, label);
			adapter.add(pkg);
		}

		adapter.sort(new Comparator<Package>() {
			public int compare(Package a, Package b) {
				if (a.selected != b.selected)
				  return a.selected ? -1 : 1;
				return a.label.compareTo(b.label);
			}
		});

		setListAdapter(adapter);
	}

	@Override
	protected void onDestroy() {
		if (isChanged) {
			AppArrayAdapter adapter = (AppArrayAdapter) getListView().getAdapter();
			Set<String> apps = new HashSet<String>();

			for (int i = 0; i < adapter.getCount(); i++) {
				Package pkg = adapter.getItem(i);
				if (pkg.selected)
				  apps.add(pkg.info.packageName);
			}

			prefs.setApps(apps);
		}

		super.onDestroy();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		AppArrayAdapter adapter = (AppArrayAdapter) l.getAdapter();
		adapter.getItem(position).selected = !adapter.getItem(position).selected;
		CheckBox checkbox = (CheckBox) v.findViewById(R.id.checked);
		checkbox.setChecked(adapter.getItem(position).selected);
		isChanged = true;
	}
}
