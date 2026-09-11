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
import java.util.List;

import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.adapter.OrgListAdapter;
import io.rapidpro.surveyor.data.Org;

/**
 * A list of orgs than can be selected from
 */
public class OrgListFragment extends Fragment implements AbsListView.OnItemClickListener {

    private Container container;
    private ListAdapter adapter;

    public OrgListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        List<Org> items = container != null ? container.getListItems() : null;
        if (items == null) {
            items = Collections.emptyList();
        }

        adapter = new OrgListAdapter(requireContext(), R.layout.item_org, items);
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
            throw new ClassCastException(context.toString() + " must implement OrgListFragment.Container");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        container = null;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        container.onItemClick((Org) adapter.getItem(position));
    }

    /**
     * Container activity should implement this to be notified when an org is clicked
     */
    public interface Container {
        List<Org> getListItems();

        void onItemClick(Org org);
    }
}
