package com.example.backend.service;

import com.example.backend.common.PageParams;
import com.example.backend.dto.PoliceTaskDetail;
import com.example.backend.dto.PoliceTaskItem;
import com.example.backend.entity.DispatchFieldRecord;
import com.example.backend.entity.DispatchMedia;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.DispatchOrderEvaluation;
import com.example.backend.mapper.DispatchFieldRecordMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderEvaluationMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoliceMobileServiceTest {

    @Mock DispatchOrderMapper dispatchOrderMapper;
    @Mock DispatchFieldRecordMapper fieldRecordMapper;
    @Mock DispatchMediaMapper mediaMapper;
    @Mock DispatchOrderEvaluationMapper evaluationMapper;
    @InjectMocks PoliceMobileService service;

    @Test
    void listMarksRatedAndPaginates() {
        DispatchOrder a = new DispatchOrder();
        a.setId(1L);
        a.setOrderNo("RO1");
        a.setStatus("COMPLETED");
        a.setAccidentAddress("路A");
        a.setPlateNo("粤B1");
        a.setPartyName("张三");
        a.setCreateTime(LocalDateTime.parse("2026-09-01T10:00:00"));
        DispatchOrder b = new DispatchOrder();
        b.setId(2L);
        b.setOrderNo("RO2");
        b.setStatus("ACCEPTED");
        when(dispatchOrderMapper.count(null, null, null, null)).thenReturn(2L);
        when(dispatchOrderMapper.selectPage(null, null, null, null, 0, 10)).thenReturn(List.of(a, b));
        when(evaluationMapper.findOrderIds(List.of(1L, 2L))).thenReturn(List.of(1L));

        Map<String, Object> result = service.list(PageParams.normalize(1, 10));
        assertEquals(2L, result.get("total"));
        assertEquals(1, result.get("page"));
        @SuppressWarnings("unchecked")
        List<PoliceTaskItem> list = (List<PoliceTaskItem>) result.get("list");
        assertEquals(2, list.size());
        assertEquals("RO1", list.get(0).getOrderNo());
        assertTrue(list.get(0).isRated());
        assertFalse(list.get(1).isRated());
        assertEquals("张三", list.get(0).getPartyName());
    }

    @Test
    void listEmptyPageDoesNotQueryRatedIds() {
        when(dispatchOrderMapper.count(null, null, null, null)).thenReturn(0L);
        when(dispatchOrderMapper.selectPage(null, null, null, null, 0, 10)).thenReturn(List.of());
        Map<String, Object> result = service.list(PageParams.normalize(1, 10));
        assertEquals(0L, result.get("total"));
        assertEquals(List.of(), result.get("list"));
    }

    @Test
    void getTaskReturnsRecordMediaAndNullEvaluation() {
        DispatchOrder order = new DispatchOrder();
        order.setId(8L);
        order.setStatus("ACCEPTED");
        order.setCheckedInAt(LocalDateTime.parse("2026-09-01T11:00:00"));
        DispatchFieldRecord rec = new DispatchFieldRecord();
        rec.setPlateNo("粤B8");
        DispatchMedia m = new DispatchMedia();
        m.setBizType("DAMAGE");
        m.setFilePath("dispatch/8/a.jpg");
        when(dispatchOrderMapper.findById(8L)).thenReturn(order);
        when(fieldRecordMapper.findByOrderId(8L)).thenReturn(rec);
        when(mediaMapper.findByOrderId(8L)).thenReturn(List.of(m));
        when(evaluationMapper.findByOrderId(8L)).thenReturn(null);

        PoliceTaskDetail d = service.getTask(8L);
        assertEquals(8L, d.getOrder().getId());
        assertEquals("粤B8", d.getFieldRecord().getPlateNo());
        assertEquals(1, d.getMedias().size());
        assertNull(d.getEvaluation());
    }

    @Test
    void getTaskMissingOrderThrows() {
        when(dispatchOrderMapper.findById(9L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getTask(9L));
        assertEquals("工单不存在", ex.getMessage());
    }
}
