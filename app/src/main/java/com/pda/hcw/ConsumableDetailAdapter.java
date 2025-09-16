package com.pda.hcw;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class ConsumableDetailAdapter extends RecyclerView.Adapter<ConsumableDetailAdapter.ViewHolder> {
    
    private List<ConsumableCheckoutNewActivity.ConsumableGroupItem> items;
    private OnItemRemoveListener removeListener;
    private boolean isViewMode = false; // 查看模式标志
    
    public interface OnItemRemoveListener {
        void onRemove(int position);
    }
    
    public ConsumableDetailAdapter(List<ConsumableCheckoutNewActivity.ConsumableGroupItem> items, 
                                 OnItemRemoveListener removeListener) {
        this.items = items;
        this.removeListener = removeListener;
    }
    
    // 设置查看模式
    public void setViewMode(boolean viewMode) {
        this.isViewMode = viewMode;
        notifyDataSetChanged(); // 刷新所有item的显示状态
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_consumable_detail, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConsumableCheckoutNewActivity.ConsumableGroupItem item = items.get(position);
        
        // 设置序号（从1开始）
        holder.serialNoText.setText(String.valueOf(position + 1));
        
        // 设置商品编号
        holder.productCodeText.setText(item.productCode);
        
        // 设置基本信息
        holder.commonNameText.setText(item.commonName);
        holder.specificationText.setText("   " + item.specification);
        holder.manufacturerText.setText("   " + item.manufacturer);
        holder.quantityText.setText(String.format("   数量：%d %s", item.quantity, item.unit));
        
        // 设置RFID详情（沿用条码展示区域）
        StringBuilder barcodeDetails = new StringBuilder();
        for (int i = 0; i < item.rfids.size(); i++) {
            if (i > 0) barcodeDetails.append("\n");
            barcodeDetails.append(String.format("%d. %s", i + 1, item.rfids.get(i)));
        }
        holder.barcodeText.setText(barcodeDetails.toString());
        
        // 设置展开/折叠状态
        holder.barcodeDetailsArea.setVisibility(item.isExpanded ? View.VISIBLE : View.GONE);
        holder.expandIcon.setRotation(item.isExpanded ? 180f : 90f);
        
        // 展开/折叠点击事件
        holder.expandIcon.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            if (currentPosition != RecyclerView.NO_POSITION) {
                ConsumableCheckoutNewActivity.ConsumableGroupItem currentItem = items.get(currentPosition);
                currentItem.isExpanded = !currentItem.isExpanded;
                notifyItemChanged(currentPosition);
            }
        });
        
        // 移除按钮点击事件
        holder.removeButton.setOnClickListener(v -> {
            android.util.Log.d("ConsumableDetailAdapter", "Remove button clicked at position: " + holder.getAdapterPosition());
            android.util.Log.d("ConsumableDetailAdapter", "isViewMode: " + isViewMode);
            android.util.Log.d("ConsumableDetailAdapter", "removeListener: " + (removeListener != null));
            
            if (removeListener != null && !isViewMode) {
                android.util.Log.d("ConsumableDetailAdapter", "Calling removeListener.onRemove for position: " + holder.getAdapterPosition());
                removeListener.onRemove(holder.getAdapterPosition());
            } else {
                android.util.Log.d("ConsumableDetailAdapter", "Remove action blocked - isViewMode: " + isViewMode + ", removeListener: " + (removeListener != null));
            }
        });
        
        // 设置移除按钮状态
        holder.removeButton.setEnabled(!isViewMode);
        holder.removeButton.setAlpha(isViewMode ? 0.5f : 1.0f);
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView serialNoText;
        TextView productCodeText;
        TextView barcodeText;
        TextView commonNameText;
        TextView specificationText;
        TextView manufacturerText;
        TextView quantityText;
        ImageView expandIcon;
        LinearLayout barcodeDetailsArea;
        MaterialButton removeButton;
        
        ViewHolder(View itemView) {
            super(itemView);
            serialNoText = itemView.findViewById(R.id.serial_no_text);
            productCodeText = itemView.findViewById(R.id.product_code_text);
            barcodeText = itemView.findViewById(R.id.barcode_text);
            commonNameText = itemView.findViewById(R.id.common_name_text);
            specificationText = itemView.findViewById(R.id.specification_text);
            manufacturerText = itemView.findViewById(R.id.manufacturer_text);
            quantityText = itemView.findViewById(R.id.quantity_text);
            expandIcon = itemView.findViewById(R.id.expand_icon);
            barcodeDetailsArea = itemView.findViewById(R.id.barcode_details_area);
            removeButton = itemView.findViewById(R.id.remove_button);
        }
    }
}
