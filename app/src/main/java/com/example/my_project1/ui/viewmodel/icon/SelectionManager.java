package com.example.my_project1.ui.viewmodel.icon;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.icon.IconItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SelectionManager
 * ─────────────────────────────────────────────────────────────
 * 职责：管理多选模式的所有状态，与 IconItem 列表完全解耦。
 *
 * 设计要点：
 * ① 使用 LinkedHashSet<String> 保存选中 ID，O(1) 查询，保持插入顺序。
 * ② isMultiSelectMode / selectedIds / selectedCount 均为 LiveData，
 *    UI 只需 observe，不直接操作数据。
 * ③ toggle / selectAll / clearAll 操作后统一发射新快照（Set 浅拷贝），
 *    避免 Observer 拿到同一引用无法感知变化。
 * ④ 持有 allItems 引用（由 ViewModel 注入），用于 selectAll。
 *    allItems 不可被外部修改。
 */
public class SelectionManager {

    // ──────────────────────────────────────────────────────────
    // 状态
    // ──────────────────────────────────────────────────────────

    /** 是否处于多选模式 */
    private final MutableLiveData<Boolean> _multiSelectMode = new MutableLiveData<>(false);
    public final LiveData<Boolean> multiSelectMode = _multiSelectMode;

    /** 当前选中的图标 ID 集合（快照），Observer 用于判断某个 item 是否选中 */
    private final MutableLiveData<Set<String>> _selectedIds = new MutableLiveData<>(Collections.emptySet());
    public final LiveData<Set<String>> selectedIds = _selectedIds;

    /** 选中数量，供 Toolbar 标题显示 */
    private final MutableLiveData<Integer> _selectedCount = new MutableLiveData<>(0);
    public final LiveData<Integer> selectedCount = _selectedCount;

    /** 内部可变集合（LinkedHashSet 保持插入顺序） */
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();

    // ──────────────────────────────────────────────────────────
    // 多选模式入口：长按时由 Adapter 回调触发
    // ──────────────────────────────────────────────────────────

    /**
     * 进入多选模式，并选中触发长按的图标。
     * 若已在多选模式，直接 toggle 该图标即可。
     */
    public void enterMultiSelectMode(String iconId) {
        if (!Boolean.TRUE.equals(_multiSelectMode.getValue())) {
            _multiSelectMode.setValue(true);
        }
        select(iconId);
    }

    // ──────────────────────────────────────────────────────────
    // 选中操作
    // ──────────────────────────────────────────────────────────

    /** 单击切换选中状态 */
    public void toggle(String iconId) {
        if (selected.contains(iconId)) {
            selected.remove(iconId);
        } else {
            selected.add(iconId);
        }
        notifyChange();
    }

    /** 强制选中 */
    private void select(String iconId) {
        selected.add(iconId);
        notifyChange();
    }

    /**
     * 全选：传入当前可见的全部 item ID 列表。
     * 若当前已全部选中（isAllSelected），则清空（取消全选）。
     */
    public void toggleSelectAll(List<String> allIconIds) {
        if (allIconIds == null || allIconIds.isEmpty()) return;

        boolean isAllSelected = selected.containsAll(allIconIds);
        if (isAllSelected) {
            selected.removeAll(allIconIds);
        } else {
            selected.addAll(allIconIds);
        }
        notifyChange();
    }

    /**
     * 检查针对给定列表是否已全选
     */
    public boolean isAllSelected(List<String> allIconIds) {
        if (allIconIds == null || allIconIds.isEmpty()) return false;
        return selected.containsAll(allIconIds);
    }

    /** 查询某个图标是否选中（供 Adapter ViewHolder 调用）*/
    public boolean isSelected(String iconId) {
        return selected.contains(iconId);
    }

    /** 退出多选模式，清空所有选中 */
    public void exitMultiSelectMode() {
        selected.clear();
        _multiSelectMode.setValue(false);
        notifyChange();
    }

    /** 获取当前选中的图标 ID 列表（有序） */
    public List<String> getSelectedIds() {
        return new ArrayList<>(selected);
    }

    /** 获取当前选中的 IconItem 列表（需传入数据源）*/
    public List<IconItem> getSelectedItems(List<IconItem> source) {
        if (source == null) return new ArrayList<>();
        List<IconItem> result = new ArrayList<>();
        for (IconItem item : source) {
            if (selected.contains(item.getId())) {
                result.add(item);
            }
        }
        return result;
    }

    public int getCount() {
        return selected.size();
    }

    // ──────────────────────────────────────────────────────────
    // 内部：发射状态快照
    // ──────────────────────────────────────────────────────────

    private void notifyChange() {
        // 浅拷贝，让 Observer 感知到新对象
        _selectedIds.setValue(new LinkedHashSet<>(selected));
        _selectedCount.setValue(selected.size());
    }
}