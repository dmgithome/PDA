package com.pda.hcw;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 术间未盘明细：按子码聚合展示，并支持折叠 RFID 明细。
 */
public class RoomGroupedRfidAdapter extends RecyclerView.Adapter<RoomGroupedRfidAdapter.ViewHolder> {

    public interface OnRfidClickListener {
        void onClick(String rfid);
    }

    public static class RoomGroupedItem {
        public String code;
        public String name;
        public String spec;
        public String manufacturer;
        public final List<String> rfids = new ArrayList<>();
        public boolean isExpanded = false;
    }

    private final List<RoomGroupedItem> items;
    private final OnRfidClickListener onRfidClickListener;

    public RoomGroupedRfidAdapter(List<RoomGroupedItem> items, OnRfidClickListener listener) {
        this.items = items == null ? new ArrayList<>() : items;
        this.onRfidClickListener = listener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_room_grouped_rfid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RoomGroupedItem item = items.get(position);
        holder.codeText.setText("子码：" + safe(item.code));
        holder.countText.setText("未盘 " + item.rfids.size());
        holder.nameText.setText(safe(item.name));
        holder.specText.setText("规格：" + safe(item.spec));
        holder.manufacturerText.setText("生产厂家：" + safe(item.manufacturer));

        bindRfidRows(holder.rfidContainer, item.rfids);

        holder.detailArea.setVisibility(item.isExpanded ? View.VISIBLE : View.GONE);
        holder.expandIcon.setRotation(item.isExpanded ? 180f : 90f);

        View.OnClickListener toggle = v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;
            RoomGroupedItem current = items.get(currentPos);
            current.isExpanded = !current.isExpanded;
            notifyItemChanged(currentPos);
        };
        holder.headerArea.setOnClickListener(toggle);
        holder.expandIcon.setOnClickListener(toggle);
    }

    private void bindRfidRows(LinearLayout container, List<String> rfids) {
        container.removeAllViews();
        if (rfids == null || rfids.isEmpty()) {
            TextView empty = new TextView(container.getContext());
            empty.setText("暂无未盘RFID");
            empty.setTextColor(container.getContext().getResources().getColor(R.color.text_secondary, null));
            empty.setTextSize(12f);
            container.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (int i = 0; i < rfids.size(); i++) {
            String rfid = rfids.get(i);
            TextView row = (TextView) inflater.inflate(R.layout.item_rfid_epc, container, false);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            row.setText((i + 1) + ". " + rfid);
            row.setOnClickListener(v -> {
                if (onRfidClickListener != null) {
                    onRfidClickListener.onClick(rfid);
                }
            });
            container.addView(row);
        }
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        RoomGroupedItem item = items.get(position);
        String key = (item.code == null ? "" : item.code) + "#" + position;
        return key.hashCode();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout headerArea;
        TextView codeText;
        TextView countText;
        TextView nameText;
        TextView specText;
        TextView manufacturerText;
        ImageView expandIcon;
        LinearLayout detailArea;
        LinearLayout rfidContainer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            headerArea = itemView.findViewById(R.id.header_area);
            codeText = itemView.findViewById(R.id.code_text);
            countText = itemView.findViewById(R.id.count_text);
            nameText = itemView.findViewById(R.id.name_text);
            specText = itemView.findViewById(R.id.spec_text);
            manufacturerText = itemView.findViewById(R.id.manufacturer_text);
            expandIcon = itemView.findViewById(R.id.expand_icon);
            detailArea = itemView.findViewById(R.id.detail_area);
            rfidContainer = itemView.findViewById(R.id.rfid_list_container);
        }
    }
}
