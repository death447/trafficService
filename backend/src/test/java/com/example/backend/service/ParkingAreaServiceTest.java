package com.example.backend.service;

import com.example.backend.dto.ParkingAreaRequest;
import com.example.backend.entity.ParkingArea;
import com.example.backend.entity.ParkingLot;
import com.example.backend.mapper.DetainedVehicleMapper;
import com.example.backend.mapper.ParkingAreaMapper;
import com.example.backend.mapper.ParkingLotMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingAreaServiceTest {
    @Mock ParkingAreaMapper parkingAreaMapper;
    @Mock ParkingLotMapper parkingLotMapper;
    @Mock DetainedVehicleMapper detainedVehicleMapper;
    @InjectMocks ParkingAreaService service;

    @Test
    void createRejectsDuplicateNameOnSameLot() {
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        when(parkingLotMapper.findById(1L)).thenReturn(lot);
        ParkingArea existing = new ParkingArea();
        existing.setId(3L);
        existing.setName("A区");
        when(parkingAreaMapper.findByLotIdAndName(1L, "A区")).thenReturn(existing);

        ParkingAreaRequest req = new ParkingAreaRequest();
        req.setName(" A区 ");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(1L, req));
        assertTrue(ex.getMessage().contains("同名"));
        verify(parkingAreaMapper, never()).insert(any());
    }

    @Test
    void deleteBlockedWhenVehiclesReferenceArea() {
        ParkingArea area = new ParkingArea();
        area.setId(8L);
        area.setParkingLotId(1L);
        when(parkingAreaMapper.findById(8L)).thenReturn(area);
        when(detainedVehicleMapper.countByParkingAreaId(8L)).thenReturn(1);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.delete(8L));
        assertTrue(ex.getMessage().contains("扣留") || ex.getMessage().contains("停用"));
        verify(parkingAreaMapper, never()).deleteById(any());
    }

    @Test
    void requireEnabledForLotRejectsOtherLot() {
        ParkingArea area = new ParkingArea();
        area.setId(2L);
        area.setParkingLotId(9L);
        area.setStatus("ENABLED");
        when(parkingAreaMapper.findById(2L)).thenReturn(area);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.requireEnabledForLot(2L, 1L));
        assertTrue(ex.getMessage().contains("不属于"));
    }
}
