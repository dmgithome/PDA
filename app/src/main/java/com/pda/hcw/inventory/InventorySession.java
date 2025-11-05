package com.pda.hcw.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单次盘点任务的内存态会话，用于高频更新与UI展示。
 * - 仅在本地存储计划清单与扫描状态，不持久化。
 * - 用于驱动表头与术间卡片、明细列表。
 */
public class InventorySession {
    public static final int ROOM_ID_CONSUMABLE_HOUSE = 0; // 耗材屋

    private final String taskId;
    private final Map<Integer, Set<String>> plannedByRoom = new HashMap<>();     // roomId -> planned RFIDs (canonical key)
    private final Map<Integer, Set<String>> countedByRoom = new HashMap<>();     // roomId -> counted RFIDs (subset of planned, canonical key)

    private final Set<String> allPlanned = new HashSet<>(); // 去重后的计划集合（canonical key）
    private final Set<String> countedAll = new HashSet<>(); // 计划内已盘集合
    private final Set<String> surplus = new HashSet<>();    // 不在计划中的RFID

    // 计划元数据：rfid -> 计划条目（用于导出差异、明细展示）
    private final Map<String, InventoryPlanItem> planMeta = new HashMap<>(); // key=canonical rfid

    // 备用键映射：各种表示形式 -> canonical（统一使用18位十进制作为优先canonical；否则用原值）
    private final Map<String, String> altKeyToCanonical = new HashMap<>();

    private final Map<Integer, String> roomNameMap = new HashMap<>(); // roomId -> displayName（占位用）

    // 最近一批扫描的去重窗口（简单TTL可在接入处处理，这里只做一次性去重）
    private final Set<String> seen = new HashSet<>();

    public InventorySession(String taskId) {
        this.taskId = taskId;
        // 默认包含耗材屋
        roomNameMap.put(ROOM_ID_CONSUMABLE_HOUSE, "耗材屋");
        plannedByRoom.put(ROOM_ID_CONSUMABLE_HOUSE, new HashSet<>());
        countedByRoom.put(ROOM_ID_CONSUMABLE_HOUSE, new HashSet<>());
    }

    public String getTaskId() { return taskId; }

    public void reset() {
        plannedByRoom.clear();
        countedByRoom.clear();
        allPlanned.clear();
        countedAll.clear();
        surplus.clear();
        seen.clear();
        roomNameMap.clear();
        planMeta.clear();
        altKeyToCanonical.clear();
        roomNameMap.put(ROOM_ID_CONSUMABLE_HOUSE, "耗材屋");
        plannedByRoom.put(ROOM_ID_CONSUMABLE_HOUSE, new HashSet<>());
        countedByRoom.put(ROOM_ID_CONSUMABLE_HOUSE, new HashSet<>());
    }

    /**
     * 仅清空已盘与盈余状态，保留计划清单。
     */
    public void clearRuntimeStates() {
        countedByRoom.clear();
        for (Integer roomId : plannedByRoom.keySet()) {
            countedByRoom.put(roomId, new HashSet<>());
        }
        countedAll.clear();
        surplus.clear();
        seen.clear();
    }

    public void loadPlan(List<InventoryPlanItem> items) {
        reset();
        if (items == null) return;
        for (InventoryPlanItem it : items) {
            if (it == null) continue;
            String raw = safeRfid(it.getRfid());
            if (raw.isEmpty()) continue;
            // 业务计划数据保持原样，不做格式化
            String canonical = raw;
            indexAltKeys(canonical, canonical);

            int roomId = it.getRoomId();
            String rn = it.getRoomName();
            roomNameMap.putIfAbsent(roomId, rn != null && !rn.isEmpty() ? rn : (roomId == 0 ? "耗材屋" : ("术间" + roomId)));
            plannedByRoom.computeIfAbsent(roomId, k -> new HashSet<>()).add(canonical);
            countedByRoom.computeIfAbsent(roomId, k -> new HashSet<>()); // init bucket
            allPlanned.add(canonical);
            planMeta.put(canonical, it);
            // 允许按 canonical 直接命中
            indexAltKeys(canonical, canonical);
        }
    }

    public boolean onTag(String rawRfid) {
        String raw = safeRfid(rawRfid);
        if (raw.isEmpty()) return false;
        if (seen.contains(raw)) return false; // 简单去重（按原始值）
        seen.add(raw);

        String canonical = canonicalize(raw);
        if (canonical != null && allPlanned.contains(canonical)) {
            // 找到所属术间并标记已盘
            for (Map.Entry<Integer, Set<String>> e : plannedByRoom.entrySet()) {
                if (e.getValue().contains(canonical)) {
                    countedByRoom.get(e.getKey()).add(canonical);
                    countedAll.add(canonical);
                    break;
                }
            }
        } else {
            // 记为盈余（保留原始读数，便于导出）
            surplus.add(raw);
        }
        return true;
    }

    public int getTotalPlanned() { return allPlanned.size(); }
    public int getCounted() { return countedAll.size(); }
    public int getUncounted() { return Math.max(0, getTotalPlanned() - getCounted()); }
    public int getSurplus() { return surplus.size(); }

    public Set<String> getAllPlannedRfids() { return new HashSet<>(allPlanned); }
    public Set<String> getCountedRfids() { return new HashSet<>(countedAll); }
    public Set<String> getSurplusRfids() { return new HashSet<>(surplus); }
    public InventoryPlanItem getPlanMeta(String rfid) { return planMeta.get(rfid); }
    public String getRoomName(int roomId) { return roomNameMap.get(roomId); }

    public List<InventoryRoomStatus> getRoomStatusesSorted() {
        List<InventoryRoomStatus> list = new ArrayList<>();
        // 确保耗材屋存在
        if (!plannedByRoom.containsKey(ROOM_ID_CONSUMABLE_HOUSE)) {
            plannedByRoom.put(ROOM_ID_CONSUMABLE_HOUSE, new HashSet<>());
            countedByRoom.put(ROOM_ID_CONSUMABLE_HOUSE, new HashSet<>());
            roomNameMap.putIfAbsent(ROOM_ID_CONSUMABLE_HOUSE, "耗材屋");
        }

        for (Map.Entry<Integer, Set<String>> e : plannedByRoom.entrySet()) {
            int roomId = e.getKey();
            int planned = e.getValue().size();
            int counted = countedByRoom.getOrDefault(roomId, Collections.emptySet()).size();
            String name = roomNameMap.getOrDefault(roomId, roomId == 0 ? "耗材屋" : ("术间" + roomId));
            list.add(new InventoryRoomStatus(roomId, name, planned, counted));
        }

        // 根据要求按 roomId 升序，两列排布交给布局管理器
        Collections.sort(list, Comparator.comparingInt(InventoryRoomStatus::getRoomId));
        return list;
    }

    public List<String> getRoomRfidList(int roomId, boolean uncountedFirst) {
        Set<String> planned = plannedByRoom.getOrDefault(roomId, Collections.emptySet());
        Set<String> counted = countedByRoom.getOrDefault(roomId, Collections.emptySet());

        // 排序：先按未盘/已盘，再按rfid升序
        List<String> result = new ArrayList<>(planned);
        Collections.sort(result, (a, b) -> {
            boolean aCounted = counted.contains(a);
            boolean bCounted = counted.contains(b);
            if (uncountedFirst && aCounted != bCounted) {
                return aCounted ? 1 : -1; // 未盘在前
            }
            return a.compareTo(b);
        });
        return result;
    }

    public Map<String, Boolean> getRoomRfidStateMap(int roomId) {
        Set<String> counted = countedByRoom.getOrDefault(roomId, Collections.emptySet());
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (String r : getRoomRfidList(roomId, true)) {
            map.put(r, counted.contains(r));
        }
        return map;
    }

    private String safeRfid(String s) {
        return s == null ? "" : s.trim();
    }

    // ========== 辅助：RFID 规范化匹配 ==========
    private void indexAltKeys(String alt, String canonical) {
        if (alt == null || alt.isEmpty()) return;
        altKeyToCanonical.put(alt, canonical);
    }

    private String canonicalize(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        if (altKeyToCanonical.containsKey(raw)) return altKeyToCanonical.get(raw);
        // 不再二次格式化；仅按原值判断
        String c = computeCanonical(raw);
        if (c != null && allPlanned.contains(c)) return c;
        // 不在计划集则返回null（不做额外形态推断）
        return null;
    }

    private String computeCanonical(String val) {
        if (val == null) return null;
        String s = val.trim();
        // 不在会话层做格式化，直接返回原值
        return s;
    }

    private boolean is18Digits(String s) {
        if (s == null || s.length() != 18) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    // 保留 is18Digits 供会话内部校验/排序使用
}
