package com.pda.hcw;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
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

    /**
     * 【性能优化】使用 DiffUtil 进行增量更新
     */
    public void applyFilter(boolean showCounted) {
        final List<RfidItem> oldShown = new ArrayList<>(this.shown);
        final List<RfidItem> newShown = new ArrayList<>();
        for (RfidItem it : all) {
            if (!it.counted || showCounted) newShown.add(it);
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldShown.size();
            }

            @Override
            public int getNewListSize() {
                return newShown.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                RfidItem oldItem = oldShown.get(oldItemPosition);
                RfidItem newItem = newShown.get(newItemPosition);
                return oldItem.rfid != null && oldItem.rfid.equals(newItem.rfid);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                RfidItem oldItem = oldShown.get(oldItemPosition);
                RfidItem newItem = newShown.get(newItemPosition);
                return areItemsTheSame(oldItemPosition, newItemPosition)
                        && oldItem.counted == newItem.counted;
            }
        });

        this.shown.clear();
        this.shown.addAll(newShown);
        diffResult.dispatchUpdatesTo(this);
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

