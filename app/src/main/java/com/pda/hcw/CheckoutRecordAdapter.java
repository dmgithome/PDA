package com.pda.hcw;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * 领用记录适配器 - 重新设计以支持新的领用记录功能
 */
public class CheckoutRecordAdapter extends RecyclerView.Adapter<CheckoutRecordAdapter.CheckoutRecordViewHolder> {
    
    private List<CheckoutRecord> recordList;
    private OnRecordClickListener listener;
    
    public interface OnRecordClickListener {
        void onRecordClick(CheckoutRecord record);
    }
    
    public CheckoutRecordAdapter(List<CheckoutRecord> recordList, OnRecordClickListener listener) {
        this.recordList = recordList;
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
    
    public void updateRecords(List<CheckoutRecord> newRecords) {
        this.recordList = newRecords;
        notifyDataSetChanged();
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