package io.rapidpro.surveyor.adapter;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.data.Org;
import io.rapidpro.surveyor.utils.OrgColors;

public class OrgListAdapter extends ArrayAdapter<Org> {

    public OrgListAdapter(Context context, int resourceId, List<Org> orgs) {
        super(context, resourceId, orgs);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewCache cache;
        Org org = getItem(position);

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_org, parent, false);
            cache = new ViewCache();
            cache.titleView = convertView.findViewById(R.id.text_org);
            cache.avatarView = convertView.findViewById(R.id.org_avatar);
            convertView.setTag(cache);
        } else {
            cache = (ViewCache) convertView.getTag();
        }

        cache.titleView.setText(org.getName());
        cache.avatarView.setText(OrgColors.getInitial(org));
        cache.avatarView.setBackground(avatarDrawable(OrgColors.getPrimaryColor(org)));
        return convertView;
    }

    private Drawable avatarDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    public static class ViewCache {
        TextView titleView;
        TextView avatarView;
    }
}