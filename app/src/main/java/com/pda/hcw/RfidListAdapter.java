package com.pda.hcw;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

class RfidItem {
    final String rfid;
    final boolean counted;
    RfidItem(String rfid, boolean counted) { this.rfid = rfid; this.counted = counted; }
}

public class RfidListAdapter extends RecyclerView.Adapter<RfidListAdapter.VH> {
    public interface OnItemClickListener { void onClick(RfidItem item); }

    private final List<RfidItem> all;
    private final List<RfidItem> shown;
    private final OnItemClickListener listener;

    public RfidListAdapter(List<RfidItem> source, boolean showCounted, OnItemClickListener listener) {
        this.all = new ArrayList<>(source);
        this.shown = new ArrayList<>();
        this.listener = listener;
        applyFilter(showCounted);
        setHasStableIds(true);
    }

    public void applyFilter(boolean showCounted) {
        shown.clear();
        for (RfidItem it : all) {
            if (!it.counted || showCounted) shown.add(it);
        }
        notifyDataSetChanged();
    }

    public int getTotal() { return all.size(); }
    public int getCounted() {
        int c = 0; for (RfidItem it : all) if (it.counted) c++; return c;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rfid_row, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        RfidItem it = shown.get(pos);
        h.status.setText(it.counted ? "[已盘]" : "[未盘]");
        h.rfid.setText(it.rfid);
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onClick(it); });
    }

    @Override public int getItemCount() { return shown.size(); }

    @Override public long getItemId(int position) {
        return shown.get(position).rfid.hashCode();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView status; TextView rfid;
        VH(@NonNull View itemView) { super(itemView); status = itemView.findViewById(R.id.tv_status); rfid = itemView.findViewById(R.id.tv_rfid); }
    }
}

