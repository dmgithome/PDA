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
 * 未归还耗材列表适配器（仅展示当前用户数据）
 */
public class UnreturnListAdapter extends RecyclerView.Adapter<UnreturnListAdapter.VH> {

    private List<ConsumableApiService.UnreturnListItem> items = new ArrayList<>();

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_unreturn_consumable, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ConsumableApiService.UnreturnListItem it = items.get(position);
        holder.nameText.setText(safeText(it == null ? null : it.name, "-"));
        holder.timeText.setText(formatTime(it == null ? null : it.takeTime));
        holder.rfidText.setText("RFID：" + safeText(it == null ? null : it.rfid, ""));

        String code = safeText(it == null ? null : it.code, "");
        String spec = safeText(it == null ? null : it.spec, "");
        String batch = safeText(it == null ? null : it.batch, "");
        holder.metaText.setText("编码：" + code + "  /  规格：" + spec + "  /  批号：" + batch);

        String loc = safeText(it == null ? null : it.locationName, "");
        int num = it == null ? 0 : it.number;
        holder.locationText.setText("地点：" + loc + "  /  数量：" + (num > 0 ? num : 1));
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    public void updateItems(List<ConsumableApiService.UnreturnListItem> newItems) {
        final List<ConsumableApiService.UnreturnListItem> oldList = this.items != null ? this.items : new ArrayList<>();
        final List<ConsumableApiService.UnreturnListItem> next = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldList.size(); }
            @Override public int getNewListSize() { return next.size(); }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                ConsumableApiService.UnreturnListItem a = oldList.get(oldItemPosition);
                ConsumableApiService.UnreturnListItem b = next.get(newItemPosition);
                if (a == null || b == null) return false;
                if (a.id > 0 && b.id > 0) return a.id == b.id;
                return safeEquals(a.rfid, b.rfid);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ConsumableApiService.UnreturnListItem a = oldList.get(oldItemPosition);
                ConsumableApiService.UnreturnListItem b = next.get(newItemPosition);
                if (a == null || b == null) return false;
                return areItemsTheSame(oldItemPosition, newItemPosition)
                        && safeEquals(a.code, b.code)
                        && safeEquals(a.name, b.name)
                        && safeEquals(a.spec, b.spec)
                        && safeEquals(a.manufacturer, b.manufacturer)
                        && safeEquals(a.batch, b.batch)
                        && safeEquals(a.rfid, b.rfid)
                        && safeEquals(a.takeTime, b.takeTime)
                        && safeEquals(a.locationName, b.locationName)
                        && a.locationId == b.locationId
                        && a.number == b.number
                        && safeEqualsInt(a.durationMinutes, b.durationMinutes);
            }
        });

        this.items = next;
        diff.dispatchUpdatesTo(this);
    }

    private static String formatTime(String takeTime) {
        if (takeTime == null) return "";
        String t = takeTime.trim();
        if (t.length() >= 16) return t.substring(0, 16);
        return t;
    }

    private static String safeText(String s, String fallback) {
        if (s == null) return fallback;
        String v = s.trim();
        return v.isEmpty() ? fallback : v;
    }

    private static boolean safeEquals(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    private static boolean safeEqualsInt(Integer a, Integer b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView timeText;
        TextView rfidText;
        TextView metaText;
        TextView locationText;

        VH(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.name_text);
            timeText = itemView.findViewById(R.id.time_text);
            rfidText = itemView.findViewById(R.id.rfid_text);
            metaText = itemView.findViewById(R.id.meta_text);
            locationText = itemView.findViewById(R.id.location_text);
        }
    }
}

