/*
 * Copyright (C) 2021 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.rtpstreamer.utils;

import android.content.res.Resources;
import androidx.core.content.res.ResourcesCompat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import com.pedro.rtpstreamer.R;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class ImageAdapter extends BaseAdapter {

  private List<ActivityLink> links;

  public ImageAdapter(List<ActivityLink> links) {
    this.links = links;
  }

  public int getCount() {
    return links.size();
  }

  public ActivityLink getItem(int position) {
    return links.get(position);
  }

  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    TextView button;
    Resources resources = parent.getResources();
    float fontSize = resources.getDimension(R.dimen.menu_font);
    int paddingH = resources.getDimensionPixelSize(R.dimen.grid_2);
    int paddingV = resources.getDimensionPixelSize(R.dimen.grid_5);
    if (convertView == null) {
      button = new TextView(parent.getContext());
      button.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
      button.setTextColor(ResourcesCompat.getColor(resources, R.color.white, null));
      button.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.appColor, null));
      button.setLayoutParams(new GridView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
      button.setPadding(paddingH, paddingV, paddingH, paddingV);
      button.setGravity(Gravity.CENTER);
      convertView = button;
    } else {
      button = (TextView) convertView;
    }
    button.setText(links.get(position).getLabel());
    return convertView;
  }
}
