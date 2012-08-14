package com.benitobertoli.largeimagezoom;

import java.io.File;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class TestActivity extends Activity {
	static final int NEW_FROM_RES_ID = 1;
	static final int NEW_FROM_FILE_ID = 2;

	ZoomImageView ziv;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		ziv = (ZoomImageView) findViewById(R.id.zoomImageView);

	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(0, NEW_FROM_RES_ID, 0, "From Res");
		menu.add(0, NEW_FROM_FILE_ID, 0, "From File");

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case NEW_FROM_RES_ID:
			ziv.setImageResource(R.drawable.huge_image);
			return true;
		case NEW_FROM_FILE_ID:
			File f = new File("path_to_huge_image_here");
			Uri uri = Uri.fromFile(f);
			ziv.setImageUri(uri);
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

}
