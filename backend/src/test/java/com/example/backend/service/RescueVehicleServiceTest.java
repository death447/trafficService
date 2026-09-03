package com.example.backend.service;

import com.example.backend.dto.NearbyVehicleVO;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RescueVehicleServiceTest {

    @Mock RescueVehicleMapper vehicleMapper;
    @Mock DispatchOrderMapper dispatchOrderMapper;
    @InjectMocks RescueVehicleService service;

    @Test
    void deleteRejectsWhenActiveOrdersExist() {
        when(dispatchOrderMapper.countActiveByVehicleId(1L)).thenReturn(2);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.deleteVehicle(1L));
        assertTrue(ex.getMessage().contains("进行中"));
        verify(vehicleMapper, never()).deleteById(any());
    }

    @Test
    void updateRejectsIdleWhenDispatchedOrdersExist() {
        RescueVehicle v = vehicle(1L, "粤B1", "114.058", "22.543");
        v.setStatus("IDLE");
        when(vehicleMapper.findByPlateNo("粤B1")).thenReturn(null);
        when(dispatchOrderMapper.countDispatchedByVehicleId(1L)).thenReturn(1);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.updateVehicle(v));
        assertTrue(ex.getMessage().contains("派单"));
        verify(vehicleMapper, never()).update(any());
    }

    @Test
    void updateRejectsOfflineWhenDispatchedOrdersExist() {
        RescueVehicle v = vehicle(1L, "粤B1", "114.058", "22.543");
        v.setStatus("OFFLINE");
        when(vehicleMapper.findByPlateNo("粤B1")).thenReturn(null);
        when(dispatchOrderMapper.countDispatchedByVehicleId(1L)).thenReturn(2);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.updateVehicle(v));
        assertTrue(ex.getMessage().contains("空闲或离线"));
        verify(vehicleMapper, never()).update(any());
    }

    @Test
    void updateAllowsBusyEvenWhenDispatched() {
        RescueVehicle v = vehicle(1L, "粤B1", "114.058", "22.543");
        v.setStatus("BUSY");
        when(vehicleMapper.findByPlateNo("粤B1")).thenReturn(null);
        when(vehicleMapper.update(v)).thenReturn(1);

        assertTrue(service.updateVehicle(v));
        verify(dispatchOrderMapper, never()).countDispatchedByVehicleId(any());
        verify(vehicleMapper).update(v);
    }

    @Test
    void updateAllowsIdleWhenNoDispatchedOrders() {
        RescueVehicle v = vehicle(1L, "粤B1", "114.058", "22.543");
        v.setStatus("IDLE");
        when(vehicleMapper.findByPlateNo("粤B1")).thenReturn(null);
        when(dispatchOrderMapper.countDispatchedByVehicleId(1L)).thenReturn(0);
        when(vehicleMapper.update(v)).thenReturn(1);

        assertTrue(service.updateVehicle(v));
        verify(vehicleMapper).update(v);
    }

    @Test
    void nearbySortsIdleByDistanceAscending() {
        RescueVehicle near = vehicle(1L, "粤B1", "114.058", "22.543");
        RescueVehicle far = vehicle(2L, "粤B2", "114.100", "22.600");
        when(vehicleMapper.findByStatus("IDLE")).thenReturn(List.of(far, near));

        List<NearbyVehicleVO> list = service.findNearby(
                new BigDecimal("114.057868"), new BigDecimal("22.543099"), 10);

        assertEquals(2, list.size());
        assertEquals(1L, list.get(0).getVehicle().getId());
        assertTrue(list.get(0).getDistanceMeters() < list.get(1).getDistanceMeters());
    }

    @Test
    void createRejectsDuplicatePlate() {
        RescueVehicle v = new RescueVehicle();
        v.setPlateNo("粤B·救援01");
        when(vehicleMapper.findByPlateNo("粤B·救援01")).thenReturn(new RescueVehicle());
        assertThrows(RuntimeException.class, () -> service.createVehicle(v));
    }

    private static RescueVehicle vehicle(Long id, String plate, String lng, String lat) {
        RescueVehicle v = new RescueVehicle();
        v.setId(id);
        v.setPlateNo(plate);
        v.setStatus("IDLE");
        v.setLongitude(new BigDecimal(lng));
        v.setLatitude(new BigDecimal(lat));
        return v;
    }
}
