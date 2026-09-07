package com.example.backend.service;

import com.example.backend.dto.DetainInRequest;
import com.example.backend.dto.DetainUpdateRequest;
import com.example.backend.entity.DetainedVehicle;
import com.example.backend.entity.ParkingLot;
import com.example.backend.mapper.DetainedVehicleMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DetainedVehicleServiceTest {
    @Mock DetainedVehicleMapper detainedVehicleMapper;
    @Mock ParkingLotService parkingLotService;
    @Mock DispatchOrderMapper dispatchOrderMapper;
    @InjectMocks DetainedVehicleService service;

    @Test
    void checkInRejectsWhenPlateAlreadyInYard() {
        DetainInRequest req = new DetainInRequest();
        req.setPlateNo(" 粤B12345 ");
        req.setParkingLotId(1L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.countInYardByPlateNo("粤B12345")).thenReturn(1);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.checkIn(req, 9L));
        assertTrue(ex.getMessage().contains("在库") || ex.getMessage().contains("车牌"));
        verify(detainedVehicleMapper, never()).insert(any());
    }

    @Test
    void checkOutThenClearHappyPath() {
        DetainedVehicle v = inYard(10L, "粤B1");
        when(detainedVehicleMapper.findById(10L)).thenReturn(v);
        when(detainedVehicleMapper.update(any())).thenReturn(1);
        assertTrue(service.checkOut(10L, 3L));
        assertEquals("OUT", v.getStatus());
        assertNotNull(v.getOutTime());
        assertEquals(3L, v.getOperatorOutId());

        assertTrue(service.clear(10L));
        assertEquals("CLEARED", v.getStatus());
        assertNotNull(v.getClearedAt());
    }

    @Test
    void clearRejectsWhenStillInYard() {
        when(detainedVehicleMapper.findById(1L)).thenReturn(inYard(1L, "粤B1"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.clear(1L));
        assertTrue(ex.getMessage().contains("出库") || ex.getMessage().contains("清理"));
    }

    @Test
    void updateRejectsPlateConflict() {
        DetainedVehicle v = inYard(5L, "粤B旧");
        when(detainedVehicleMapper.findById(5L)).thenReturn(v);
        DetainUpdateRequest req = new DetainUpdateRequest();
        req.setPlateNo("粤B新");
        req.setParkingLotId(1L);
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        lot.setStatus("ENABLED");
        when(parkingLotService.requireEnabled(1L)).thenReturn(lot);
        when(detainedVehicleMapper.countInYardByPlateNoExcludingId("粤B新", 5L)).thenReturn(1);
        assertThrows(RuntimeException.class, () -> service.update(5L, req));
    }

    private static DetainedVehicle inYard(Long id, String plate) {
        DetainedVehicle v = new DetainedVehicle();
        v.setId(id);
        v.setPlateNo(plate);
        v.setStatus("IN_YARD");
        v.setParkingLotId(1L);
        v.setInTime(java.time.LocalDateTime.now());
        return v;
    }
}
