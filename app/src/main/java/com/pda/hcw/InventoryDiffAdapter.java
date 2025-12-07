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

/**
 * 盘点差异明细列表适配器。
 * 仅负责将差异条目简单绑定到列表项，点击事件交由调用方处理。
 */
public class InventoryDiffAdapter extends RecyclerView.Adapter<InventoryDiffAdapter.VH> {

    public enum DiffType {
        MISSING, SURPLUS
    }

    public static class DiffItem {
        public final DiffType type;
        public final String rfid;
        public final String name;
        public final String spec;
        public final String manufacturer;
        public final String roomName;

        public DiffItem(DiffType type,
                        String rfid,
                        String name,
                        String spec,
                        String manufacturer,
                        String roomName) {
            this.type = type;
            this.rfid = rfid;
            this.name = name;
            this.spec = spec;
            this.manufacturer = manufacturer;
            this.roomName = roomName;
        }
    }

    public interface OnItemClickListener {
        void onClick(DiffItem item);
    }

    private final List<DiffItem> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public InventoryDiffAdapter(OnItemClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    /**
     * 【性能优化】使用 DiffUtil 进行增量更新
     */
    public void setItems(List<DiffItem> data) {
        final List<DiffItem> oldList = new ArrayList<>(items);
        final List<DiffItem> newList = data != null ? new ArrayList<>(data) : new ArrayList<>();

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                DiffItem oldItem = oldList.get(oldItemPosition);
                DiffItem newItem = newList.get(newItemPosition);
                // 使用 rfid 判断是否同一条记录
                return oldItem.rfid != null && oldItem.rfid.equals(newItem.rfid);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                DiffItem oldItem = oldList.get(oldItemPosition);
                DiffItem newItem = newList.get(newItemPosition);
                // 比较所有显示的字段
                return areItemsTheSame(oldItemPosition, newItemPosition)
                        && oldItem.type == newItem.type
                        && safeEquals(oldItem.name, newItem.name)
                        && safeEquals(oldItem.spec, newItem.spec)
                        && safeEquals(oldItem.roomName, newItem.roomName);
            }
        });

        items.clear();
        items.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    private static boolean safeEquals(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory_diff, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        DiffItem item = items.get(position);
        // 名称 + 规格简单拼接
        StringBuilder nameSpec = new StringBuilder();
        if (item.name != null && !item.name.isEmpty()) {
            nameSpec.append(item.name.trim());
        }
        if (item.spec != null && !item.spec.isEmpty()) {
            if (nameSpec.length() > 0) {
                nameSpec.append(" / ");
            }
            nameSpec.append(item.spec.trim());
        }
        holder.nameSpec.setText(nameSpec.length() > 0 ? nameSpec.toString() : safe("未知耗材"));

        // 差异类型标签
        if (item.type == DiffType.MISSING) {
            holder.diffTag.setText("缺失");
        } else {
            holder.diffTag.setText("盈余");
        }

        // 地点信息
        String location = item.roomName;
        if (location == null || location.trim().isEmpty()) {
            location = "未知地点";
        }
        holder.location.setText("地点：" + location);

        // RFID
        holder.rfid.setText("RFID：" + safe(item.rfid));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        DiffItem item = items.get(position);
        return item.rfid != null ? item.rfid.hashCode() : position;
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView nameSpec;
        final TextView diffTag;
        final TextView location;
        final TextView rfid;

        VH(@NonNull View itemView) {
            super(itemView);
            nameSpec = itemView.findViewById(R.id.tv_name_spec);
            diffTag = itemView.findViewById(R.id.tv_diff_tag);
            location = itemView.findViewById(R.id.tv_location);
            rfid = itemView.findViewById(R.id.tv_rfid);
        }
    }

    private static String safe(String s) {
        return (s == null || s.isEmpty()) ? "—" : s;
    }
}

