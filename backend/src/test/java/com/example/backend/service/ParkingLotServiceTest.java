package com.example.backend.service;

import com.example.backend.dto.ParkingLotRequest;
import com.example.backend.entity.ParkingLot;
import com.example.backend.mapper.DetainedVehicleMapper;
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
class ParkingLotServiceTest {
    @Mock ParkingLotMapper parkingLotMapper;
    @Mock DetainedVehicleMapper detainedVehicleMapper;
    @InjectMocks ParkingLotService service;

    @Test
    void deleteBlockedWhenInYardVehiclesExist() {
        ParkingLot lot = new ParkingLot();
        lot.setId(1L);
        when(parkingLotMapper.findById(1L)).thenReturn(lot);
        when(detainedVehicleMapper.countInYardByParkingLotId(1L)).thenReturn(2);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("在库"));
        verify(parkingLotMapper, never()).deleteById(any());
    }

    @Test
    void requireEnabledRejectsDisabled() {
        ParkingLot lot = new ParkingLot();
        lot.setId(2L);
        lot.setStatus("DISABLED");
        when(parkingLotMapper.findById(2L)).thenReturn(lot);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.requireEnabled(2L));
        assertTrue(ex.getMessage().contains("禁用") || ex.getMessage().contains("不存在"));
    }

    @Test
    void createRejectsDuplicateCode() {
        ParkingLotRequest req = new ParkingLotRequest();
        req.setName("A");
        req.setCode("PK-1");
        req.setStatus("ENABLED");
        ParkingLot existing = new ParkingLot();
        existing.setId(9L);
        existing.setCode("PK-1");
        when(parkingLotMapper.findByCode("PK-1")).thenReturn(existing);
        assertThrows(RuntimeException.class, () -> service.create(req));
    }
}
