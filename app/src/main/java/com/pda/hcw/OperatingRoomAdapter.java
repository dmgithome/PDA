package com.pda.hcw;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class OperatingRoomAdapter extends RecyclerView.Adapter<OperatingRoomAdapter.ViewHolder> {
    public interface OnRoomActionListener {
        void onEdit(OperatingRoom room);
        void onDelete(OperatingRoom room);
    }

    private final List<OperatingRoom> data;
    private final OnRoomActionListener actionListener;

    public OperatingRoomAdapter(List<OperatingRoom> data) {
        this(data, null);
    }

    public OperatingRoomAdapter(List<OperatingRoom> data, OnRoomActionListener listener) {
        this.data = data;
        this.actionListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_operating_room, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OperatingRoom room = data.get(position);
        // 默认归还标识
        boolean isDefault = false;
        try {
            android.content.SharedPreferences sp = holder.itemView.getContext()
                    .getSharedPreferences("locations_prefs", android.content.Context.MODE_PRIVATE);
            int defId = sp.getInt("default_return_location_id", -1);
            isDefault = (room.serverId != null && room.serverId == defId);
        } catch (Exception ignore) {}

        holder.name.setText(isDefault ? (room.name + "（默认归还）") : room.name);
        String meta = "ID: " + (room.serverId != null && room.serverId > 0 ? room.serverId : "本地");
        holder.meta.setText(meta);

        // 事件 / 只读模式处理
        android.view.View actions = holder.itemView.findViewById(R.id.layout_actions);
        if (actionListener != null) {
            if (actions != null) actions.setVisibility(android.view.View.VISIBLE);
            holder.btnEdit.setOnClickListener(v -> actionListener.onEdit(room));
            holder.btnDelete.setOnClickListener(v -> actionListener.onDelete(room));
        } else {
            // 只读：隐藏删除按钮，将“编辑”改为“设为默认归还”，用于本地默认目的地设置
            if (actions != null) actions.setVisibility(android.view.View.VISIBLE);
            holder.btnDelete.setVisibility(android.view.View.GONE);
            if (holder.btnEdit instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) holder.btnEdit).setText("设为默认归还");
            }
            holder.btnEdit.setOnClickListener(v -> {
                try {
                    android.content.Context ctx = holder.itemView.getContext();
                    android.content.SharedPreferences sp = ctx.getSharedPreferences("locations_prefs", android.content.Context.MODE_PRIVATE);
                    android.content.SharedPreferences.Editor editor = sp.edit();
                    int sid = room.serverId != null ? room.serverId : -1;
                    editor.putInt("default_return_location_id", sid);
                    editor.putString("default_return_location_name", room.name == null ? "" : room.name);
                    editor.apply();
                    if (ctx != null) android.widget.Toast.makeText(ctx, "已设为默认归还目的地", android.widget.Toast.LENGTH_SHORT).show();
                    // 刷新高亮
                    try { notifyDataSetChanged(); } catch (Exception ignore) {}
                } catch (Exception ignore) {}
            });
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView meta;
        View btnEdit;
        View btnDelete;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_room_name);
            meta = itemView.findViewById(R.id.tv_room_meta);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
