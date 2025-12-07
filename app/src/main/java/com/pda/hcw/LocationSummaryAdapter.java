package com.pda.hcw;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * 地点汇总列表适配器
 */
public class LocationSummaryAdapter extends RecyclerView.Adapter<LocationSummaryAdapter.ViewHolder> {
    
    private List<LocationSummary> items;
    private OnItemClickListener clickListener;
    
    public interface OnItemClickListener {
        void onItemClick(LocationSummary summary);
    }
    
    public LocationSummaryAdapter(List<LocationSummary> items, OnItemClickListener clickListener) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.clickListener = clickListener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_location_summary, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocationSummary summary = items.get(position);
        
        // 设置地点名称
        holder.locationNameText.setText(summary.getLocation());
        
        // 设置进度信息
        holder.progressText.setText(summary.getProgressText());
        
        // 设置百分比
        holder.percentageText.setText("完成度：" + summary.getFormattedPercentage());
        
        // 设置状态
        holder.statusText.setText(summary.getStatusDescription());
        holder.statusText.setTextColor(
            holder.statusText.getContext().getResources().getColor(summary.getStatusColor(), null)
        );
        
        // 设置进度条
        holder.progressBar.setProgress((int) summary.getCompletionPercentage());
        holder.progressBar.setProgressDrawable(
            holder.progressBar.getContext().getResources().getDrawable(
                android.R.drawable.progress_horizontal, null
            )
        );
        
        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onItemClick(summary);
            }
        });
        
        // 颜色规则：全量=绿色，部分=黄色，未盘=灰色
        int colorRes;
        if (summary.getTotalExpected() == 0) {
            colorRes = android.R.color.darker_gray;
        } else if (summary.isCompleted()) {
            colorRes = android.R.color.holo_green_light;
        } else if (summary.isStarted()) {
            colorRes = android.R.color.holo_orange_light;
        } else {
            colorRes = android.R.color.darker_gray;
        }
        holder.itemView.setBackgroundColor(
            holder.itemView.getContext().getResources().getColor(colorRes, null)
        );
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    /**
     * 【性能优化】使用 DiffUtil 进行增量更新
     */
    public void updateItems(List<LocationSummary> newItems) {
        final List<LocationSummary> oldList = this.items;
        final List<LocationSummary> newList = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();

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
                LocationSummary oldItem = oldList.get(oldItemPosition);
                LocationSummary newItem = newList.get(newItemPosition);
                // 使用地点名称判断是否同一条记录
                String oldLoc = oldItem.getLocation();
                String newLoc = newItem.getLocation();
                return oldLoc != null && oldLoc.equals(newLoc);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                LocationSummary oldItem = oldList.get(oldItemPosition);
                LocationSummary newItem = newList.get(newItemPosition);
                // 比较进度和完成度
                return areItemsTheSame(oldItemPosition, newItemPosition)
                        && oldItem.getTotalExpected() == newItem.getTotalExpected()
                        && oldItem.getScannedCount() == newItem.getScannedCount()
                        && oldItem.isCompleted() == newItem.isCompleted()
                        && oldItem.isStarted() == newItem.isStarted();
            }
        });

        this.items = newList;
        diffResult.dispatchUpdatesTo(this);
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView locationNameText;
        TextView progressText;
        ProgressBar progressBar;
        TextView percentageText;
        TextView statusText;
        
        ViewHolder(View itemView) {
            super(itemView);
            locationNameText = itemView.findViewById(R.id.location_name_text);
            progressText = itemView.findViewById(R.id.progress_text);
            progressBar = itemView.findViewById(R.id.progress_bar);
            percentageText = itemView.findViewById(R.id.percentage_text);
            statusText = itemView.findViewById(R.id.status_text);
        }
    }
}
