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
 * 领用记录适配器 - 重新设计以支持新的领用记录功能
 * 【性能优化】使用 DiffUtil 实现增量刷新，替代 notifyDataSetChanged
 */
public class CheckoutRecordAdapter extends RecyclerView.Adapter<CheckoutRecordAdapter.CheckoutRecordViewHolder> {

    private List<CheckoutRecord> recordList;
    private OnRecordClickListener listener;

    public interface OnRecordClickListener {
        void onRecordClick(CheckoutRecord record);
    }

    public CheckoutRecordAdapter(List<CheckoutRecord> recordList, OnRecordClickListener listener) {
        this.recordList = recordList != null ? new ArrayList<>(recordList) : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public CheckoutRecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_checkout_record, parent, false);
        return new CheckoutRecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CheckoutRecordViewHolder holder, int position) {
        CheckoutRecord record = recordList.get(position);

        holder.recordIdText.setText(record.recordId);
        holder.recordTimeText.setText(record.getShortTime());
        holder.operatingRoomText.setText("术间：" + record.operatingRoom);
        holder.quantityText.setText("数量：" + record.totalQuantity + "项");
        holder.operatorText.setText("操作人：" + record.operator);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRecordClick(record);
            }
        });
    }

    @Override
    public int getItemCount() {
        return recordList != null ? recordList.size() : 0;
    }

    /**
     * 【性能优化】使用 DiffUtil 进行增量更新
     */
    public void updateRecords(List<CheckoutRecord> newRecords) {
        final List<CheckoutRecord> oldList = this.recordList;
        final List<CheckoutRecord> newList = newRecords != null ? new ArrayList<>(newRecords) : new ArrayList<>();

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
                CheckoutRecord oldItem = oldList.get(oldItemPosition);
                CheckoutRecord newItem = newList.get(newItemPosition);
                // 使用 recordId 判断是否同一条记录
                return oldItem.recordId != null && oldItem.recordId.equals(newItem.recordId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                CheckoutRecord oldItem = oldList.get(oldItemPosition);
                CheckoutRecord newItem = newList.get(newItemPosition);
                // 比较所有显示的字段
                return areItemsTheSame(oldItemPosition, newItemPosition)
                        && safeEquals(oldItem.operatingRoom, newItem.operatingRoom)
                        && oldItem.totalQuantity == newItem.totalQuantity
                        && safeEquals(oldItem.operator, newItem.operator)
                        && safeEquals(oldItem.time, newItem.time);
            }
        });

        this.recordList = newList;
        diffResult.dispatchUpdatesTo(this);
    }

    private static boolean safeEquals(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    static class CheckoutRecordViewHolder extends RecyclerView.ViewHolder {
        TextView recordIdText;
        TextView recordTimeText;
        TextView operatingRoomText;
        TextView quantityText;
        TextView operatorText;

        public CheckoutRecordViewHolder(@NonNull View itemView) {
            super(itemView);
            recordIdText = itemView.findViewById(R.id.record_id_text);
            recordTimeText = itemView.findViewById(R.id.record_time_text);
            operatingRoomText = itemView.findViewById(R.id.operating_room_text);
            quantityText = itemView.findViewById(R.id.quantity_text);
            operatorText = itemView.findViewById(R.id.operator_text);
        }
    }
}