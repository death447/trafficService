package com.example.backend.service;

import com.example.backend.common.PageParams;
import com.example.backend.dto.DictItemRequest;
import com.example.backend.entity.SysDictItem;
import com.example.backend.mapper.SysDictItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysDictServiceTest {

    @Mock SysDictItemMapper mapper;
    @InjectMocks SysDictService service;

    private DictItemRequest request(String dictType, String name) {
        DictItemRequest req = new DictItemRequest();
        req.setDictType(dictType);
        req.setName(name);
        return req;
    }

    private SysDictItem item(String name, int sortOrder, String status) {
        SysDictItem row = new SysDictItem();
        row.setName(name);
        row.setSortOrder(sortOrder);
        row.setStatus(status);
        return row;
    }

    @Test
    void listRejectsUnknownType() {
        assertThrows(RuntimeException.class,
                () -> service.list("UNKNOWN", null, PageParams.normalize(1, 10)));
        verifyNoInteractions(mapper);
    }

    @Test
    void createRejectsBlankName() {
        assertThrows(RuntimeException.class, () -> service.create(request("DETAIN_DEPT", "   ")));
        verify(mapper, never()).insert(any());
    }

    @Test
    void createRejectsDuplicateNameInSameType() {
        when(mapper.findByTypeAndName("DETAIN_DEPT", "福田交警大队"))
                .thenReturn(new SysDictItem());
        assertThrows(RuntimeException.class,
                () -> service.create(request("DETAIN_DEPT", "福田交警大队")));
        verify(mapper, never()).insert(any());
    }

    @Test
    void createAllowsSameNameInDifferentType() {
        when(mapper.insert(any())).thenReturn(1);
        assertTrue(service.create(request("RESCUE_METHOD", "拖车")));
        verify(mapper).findByTypeAndName("RESCUE_METHOD", "拖车");
        verify(mapper).insert(any());
    }

    @Test
    void createRejectsInvalidStatus() {
        DictItemRequest req = request("DETAIN_DEPT", "福田交警大队");
        req.setStatus("ARCHIVED");
        assertThrows(RuntimeException.class, () -> service.create(req));
        verify(mapper, never()).insert(any());
    }

    @Test
    void updateRejectsMissingItem() {
        when(mapper.findById(99L)).thenReturn(null);
        assertThrows(RuntimeException.class,
                () -> service.update(99L, request("DETAIN_DEPT", "X")));
    }

    @Test
    void updateRejectsDuplicateName() {
        SysDictItem existing = item("福田交警大队", 1, "ENABLED");
        existing.setId(1L);
        existing.setDictType("DETAIN_DEPT");
        SysDictItem other = item("南山交警大队", 2, "ENABLED");
        other.setId(2L);
        when(mapper.findById(1L)).thenReturn(existing);
        when(mapper.findByTypeAndName("DETAIN_DEPT", "南山交警大队")).thenReturn(other);
        assertThrows(RuntimeException.class,
                () -> service.update(1L, request("DETAIN_DEPT", "南山交警大队")));
        verify(mapper, never()).update(any());
    }

    @Test
    void deleteRejectsMissingItem() {
        when(mapper.findById(99L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.delete(99L));
        verify(mapper, never()).deleteById(any());
    }

    @Test
    void deleteRemovesExistingItem() {
        SysDictItem existing = item("拖车", 1, "ENABLED");
        existing.setId(1L);
        when(mapper.findById(1L)).thenReturn(existing);
        when(mapper.deleteById(1L)).thenReturn(1);
        assertTrue(service.delete(1L));
        verify(mapper).deleteById(1L);
    }

    @Test
    void listEnabledFiltersAndSorts() {
        SysDictItem a = item("清障车", 2, "ENABLED");
        SysDictItem b = item("拖车", 1, "ENABLED");
        SysDictItem c = item("旧方式", 0, "DISABLED");
        when(mapper.findByType("RESCUE_METHOD")).thenReturn(List.of(a, b, c));
        List<SysDictItem> list = service.listEnabled("RESCUE_METHOD");
        assertEquals(2, list.size());
        assertEquals("拖车", list.get(0).getName());
        assertEquals("清障车", list.get(1).getName());
    }
}
