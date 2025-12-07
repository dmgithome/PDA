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
 * 盘点任务列表适配器
 */
public class InventoryTaskAdapter extends RecyclerView.Adapter<InventoryTaskAdapter.ViewHolder> {

    private List<InventoryTask> items;
    private OnItemClickListener clickListener;

    public interface OnItemClickListener {
        void onItemClick(InventoryTask task);
    }

    public InventoryTaskAdapter(List<InventoryTask> items, OnItemClickListener clickListener) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.clickListener = clickListener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory_task, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryTask task = items.get(position);
        
        // 设置任务名称
        holder.taskNameText.setText(task.getTaskName());
        
        // 设置状态
        holder.taskStatusText.setText(task.getStatus());
        holder.taskStatusText.setTextColor(
            holder.taskStatusText.getContext().getResources().getColor(task.getStatusColor(), null)
        );
        
        // 设置操作员
        holder.operatorText.setText("操作员：" + task.getOperator());
        
        // 设置时间
        holder.timeText.setText(task.getShortTime());
        
        // 设置总数
        holder.totalItemsText.setText("总数：" + task.getTotalItems());
        
        // 如果任务已开始，显示进度信息
        if ("暂存".equals(task.getStatus()) || "已完成".equals(task.getStatus())) {
            holder.progressText.setVisibility(View.VISIBLE);
            // 这里可以计算并显示进度信息
            holder.progressText.setText("进度：待实现");
        } else {
            holder.progressText.setVisibility(View.GONE);
        }
        
        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onItemClick(task);
            }
        });
        
        // 根据状态设置不同的背景色
        switch (task.getStatus()) {
            case "未执行":
                holder.itemView.setBackgroundColor(
                    holder.itemView.getContext().getResources().getColor(
                        android.R.color.holo_orange_light, null
                    )
                );
                break;
            case "暂存":
                holder.itemView.setBackgroundColor(
                    holder.itemView.getContext().getResources().getColor(
                        android.R.color.holo_blue_light, null
                    )
                );
                break;
            case "已完成":
                holder.itemView.setBackgroundColor(
                    holder.itemView.getContext().getResources().getColor(
                        android.R.color.holo_green_light, null
                    )
                );
                break;
            default:
                holder.itemView.setBackgroundColor(
                    holder.itemView.getContext().getResources().getColor(
                        android.R.color.white, null
                    )
                );
                break;
        }
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    /**
     * 【性能优化】使用 DiffUtil 进行增量更新
     */
    public void updateItems(List<InventoryTask> newItems) {
        final List<InventoryTask> oldList = this.items;
        final List<InventoryTask> newList = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();

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
                InventoryTask oldItem = oldList.get(oldItemPosition);
                InventoryTask newItem = newList.get(newItemPosition);
                // 使用 taskId 判断是否同一条记录
                String oldId = oldItem.getTaskId();
                String newId = newItem.getTaskId();
                return oldId != null && oldId.equals(newId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                InventoryTask oldItem = oldList.get(oldItemPosition);
                InventoryTask newItem = newList.get(newItemPosition);
                // 比较所有显示的字段
                return areItemsTheSame(oldItemPosition, newItemPosition)
                        && safeEquals(oldItem.getStatus(), newItem.getStatus())
                        && safeEquals(oldItem.getOperator(), newItem.getOperator())
                        && oldItem.getTotalItems() == newItem.getTotalItems();
            }
        });

        this.items = newList;
        diffResult.dispatchUpdatesTo(this);
    }

    private static boolean safeEquals(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView taskNameText;
        TextView taskStatusText;
        TextView operatorText;
        TextView timeText;
        TextView totalItemsText;
        TextView progressText;
        
        ViewHolder(View itemView) {
            super(itemView);
            taskNameText = itemView.findViewById(R.id.task_name_text);
            taskStatusText = itemView.findViewById(R.id.task_status_text);
            operatorText = itemView.findViewById(R.id.operator_text);
            timeText = itemView.findViewById(R.id.time_text);
            totalItemsText = itemView.findViewById(R.id.total_items_text);
            progressText = itemView.findViewById(R.id.progress_text);
        }
    }
}