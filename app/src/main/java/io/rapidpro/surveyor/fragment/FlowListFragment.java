package io.rapidpro.surveyor.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.fragment.app.Fragment;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.SurveyorApplication;
import io.rapidpro.surveyor.adapter.FlowListAdapter;
import io.rapidpro.surveyor.data.Flow;
import io.rapidpro.surveyor.data.Org;
import io.rapidpro.surveyor.utils.AppExecutors;

/**
 * A list of flows than can be selected from
 */
public class FlowListFragment extends Fragment implements AbsListView.OnItemClickListener {

    private Container container;
    private ListAdapter adapter;

    public FlowListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        List<Flow> items = container != null ? container.getListItems() : null;
        if (items == null) {
            items = Collections.emptyList();
        }

        adapter = new FlowListAdapter(requireContext(), R.layout.item_flow, items);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);
        ListView m_listView = view.findViewById(android.R.id.list);
        m_listView.setAdapter(adapter);
        m_listView.setOnItemClickListener(this);
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            container = (Container) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement FlowListFragment.Container");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        container = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPendingCounts();
    }

    /**
     * Filters the displayed flows by name
     */
    public void filter(String query) {
        if (adapter instanceof FlowListAdapter) {
            ((FlowListAdapter) adapter).getFilter().filter(query);
        }
    }

    /**
     * Loads the pending submission counts for all flows off the main thread (filesystem access)
     * so the list scrolls and filters smoothly.
     */
    private void loadPendingCounts() {
        final Org org = container != null ? container.getOrg() : null;
        if (org == null) {
            return;
        }

        AppExecutors.io().execute(() -> {
            Map<String, Integer> counts = new HashMap<>();
            for (Flow flow : org.getFlows()) {
                counts.put(flow.getUuid(), SurveyorApplication.get().getSubmissionService().getCompletedCount(org, flow));
            }

            AppExecutors.runOnMain(() -> {
                if (adapter instanceof FlowListAdapter) {
                    ((FlowListAdapter) adapter).setPendingCounts(counts);
                }
            });
        });
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        container.onItemClick((Flow) adapter.getItem(position));
    }

    /**
     * Container activity should implement this to be notified when a flow is clicked
     */
    public interface Container {
        Org getOrg();

        List<Flow> getListItems();

        void onItemClick(Flow flow);
    }
}
