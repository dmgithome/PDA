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
        holder.name.setText(room.name);
        String meta = "ID: " + (room.serverId != null && room.serverId > 0 ? room.serverId : "本地");
        holder.meta.setText(meta);

        // 事件
        if (actionListener != null) {
            holder.btnEdit.setOnClickListener(v -> actionListener.onEdit(room));
            holder.btnDelete.setOnClickListener(v -> actionListener.onDelete(room));
        } else {
            holder.btnEdit.setOnClickListener(null);
            holder.btnDelete.setOnClickListener(null);
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
