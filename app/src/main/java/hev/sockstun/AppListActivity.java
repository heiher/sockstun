/*
 ============================================================================
 Name        : AppListActivity.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : App List Activity
 ============================================================================
 */

package hev.sockstun;

import java.util.Set;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Comparator;
import java.util.Collections;
import android.Manifest;
import android.os.Bundle;
import android.app.ListActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;

public class AppListActivity extends ListActivity {
	private Preferences prefs;
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
			LayoutInflater inflater = (LayoutInflater) getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View rowView = inflater.inflate(R.layout.appitem, parent, false);
			ImageView imageView = (ImageView) rowView.findViewById(R.id.icon);
			TextView textView = (TextView) rowView.findViewById(R.id.name);
			CheckBox checkBox = (CheckBox) rowView.findViewById(R.id.checked);

			Package pkg = getItem(position);
			PackageManager pm = getContext().getPackageManager();
			ApplicationInfo appinfo = pkg.info.applicationInfo;
			imageView.setImageDrawable(appinfo.loadIcon(pm));
			textView.setText(appinfo.loadLabel(pm).toString());
			checkBox.setChecked(pkg.selected);

			return rowView;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

		prefs = new Preferences(this);
		Set<String> apps = prefs.getApps();
		PackageManager pm = getPackageManager();
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
