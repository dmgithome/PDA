package com.pda.hcw;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class OperatingRoomAdapter extends RecyclerView.Adapter<OperatingRoomAdapter.ViewHolder> {
    private final List<OperatingRoom> data;

    public OperatingRoomAdapter(List<OperatingRoom> data) {
        this.data = data;
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
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView meta;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_room_name);
            meta = itemView.findViewById(R.id.tv_room_meta);
        }
    }
}

