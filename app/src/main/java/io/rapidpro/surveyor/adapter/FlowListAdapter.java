package io.rapidpro.surveyor.adapter;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.data.Flow;

public class FlowListAdapter extends ArrayAdapter<Flow> {

    private final List<Flow> allFlows;

    private Map<String, Integer> pendingCounts = new HashMap<>();

    public FlowListAdapter(Context context, int resourceId, List<Flow> flows) {
        super(context, resourceId, flows);

        this.allFlows = new ArrayList<>(flows);
    }

    /**
     * Sets the pre-computed pending submission counts by flow UUID (avoids per-row disk access)
     */
    public void setPendingCounts(Map<String, Integer> counts) {
        this.pendingCounts = counts != null ? counts : new HashMap<>();
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String query = constraint == null ? "" : constraint.toString().trim().toLowerCase();

                List<Flow> filtered = new ArrayList<>();
                for (Flow flow : allFlows) {
                    if (query.isEmpty() || flow.getName().toLowerCase().contains(query)) {
                        filtered.add(flow);
                    }
                }

                FilterResults results = new FilterResults();
                results.values = filtered;
                results.count = filtered.size();
                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                clear();
                addAll((List<Flow>) results.values);
                notifyDataSetChanged();
            }
        };
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        ViewCache cache;

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

        if (row == null) {
            row = inflater.inflate(R.layout.item_flow, parent, false);

            cache = new ViewCache();
            cache.titleView = row.findViewById(R.id.text_flow_name);
            cache.questionView = row.findViewById(R.id.text_flow_questions);
            cache.pendingSubmissions = row.findViewById(R.id.text_pending_submissions);

            row.setTag(cache);
        } else {
            cache = (ViewCache) row.getTag();
        }

        Flow flow = getItem(position);
        cache.titleView.setText(flow.getName());

        Integer pendingCount = pendingCounts.get(flow.getUuid());
        int pending = pendingCount == null ? 0 : pendingCount;

        NumberFormat nf = NumberFormat.getInstance();
        cache.pendingSubmissions.setText(nf.format(pending));
        cache.pendingSubmissions.setTag(flow);
        cache.pendingSubmissions.setVisibility(pending > 0 ? View.VISIBLE : View.GONE);

        int numQuestions = flow.getQuestionCount();
        String questionsString = getContext().getResources().getQuantityString(R.plurals.questions, numQuestions, numQuestions);

        cache.questionView.setText(questionsString + " (v" + nf.format(flow.getRevision()) + ")");
        return row;
    }

    public static class ViewCache {
        TextView titleView;
        TextView questionView;
        TextView pendingSubmissions;
    }
}