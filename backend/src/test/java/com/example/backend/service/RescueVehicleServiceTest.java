package com.example.backend.service;

import com.example.backend.dto.NearbyVehicleVO;
import com.example.backend.dto.NearbyVehiclesResponse;
import com.example.backend.entity.District;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.DistrictMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RescueVehicleServiceTest {

    @Mock RescueVehicleMapper vehicleMapper;
    @Mock DispatchOrderMapper dispatchOrderMapper;
    @Mock DistrictMapper districtMapper;
    @Mock DistrictService districtService;
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
        near.setLocationUpdatedAt(java.time.LocalDateTime.now());
        RescueVehicle far = vehicle(2L, "粤B2", "114.100", "22.600");
        far.setLocationUpdatedAt(java.time.LocalDateTime.now());
        when(vehicleMapper.findByStatus("IDLE")).thenReturn(List.of(far, near));
        when(districtService.resolve(any(), any())).thenReturn(null);

        NearbyVehiclesResponse resp = service.findNearby(
                new BigDecimal("114.057868"), new BigDecimal("22.543099"), 10);
        assertNull(resp.getMatchedDistrict());
        assertEquals(2, resp.getVehicles().size());
        assertEquals(1L, resp.getVehicles().get(0).getVehicle().getId());
        assertFalse(resp.getVehicles().get(0).isInMatchedDistrict());
        assertTrue(resp.getVehicles().get(0).getDistanceMeters()
                < resp.getVehicles().get(1).getDistanceMeters());
    }

    @Test
    void nearbySortsByDistanceOnlyIgnoringDistrict() {
        District matched = new District();
        matched.setId(1L);
        matched.setName("福田中心片区");
        matched.setCode("FT-CENTER");
        matched.setStatus("ENABLED");
        when(districtService.resolve(any(), any())).thenReturn(matched);

        RescueVehicle inDistrictFar = freshVehicle(10L, "粤B远本区", "114.100", "22.600", 1L);
        RescueVehicle otherNear = freshVehicle(11L, "粤B近外区", "114.058", "22.543", 2L);
        RescueVehicle inDistrictNear = freshVehicle(12L, "粤B近本区", "114.058", "22.544", 1L);
        when(vehicleMapper.findByStatus("IDLE"))
                .thenReturn(List.of(inDistrictFar, otherNear, inDistrictNear));

        NearbyVehiclesResponse resp = service.findNearby(
                new BigDecimal("114.057868"), new BigDecimal("22.543099"), 10);

        assertEquals(1L, resp.getMatchedDistrict().getId());
        assertEquals(List.of(11L, 12L, 10L),
                resp.getVehicles().stream().map(v -> v.getVehicle().getId()).toList());
        assertTrue(resp.getVehicles().get(0).isLocationFresh());
        assertTrue(resp.getStaleVehicles() == null || resp.getStaleVehicles().isEmpty());
    }

    @Test
    void nearbySplitsFreshAndStaleByLocationUpdatedAt() {
        when(districtService.resolve(any(), any())).thenReturn(null);
        RescueVehicle fresh = freshVehicle(1L, "粤B新", "114.058", "22.543", null);
        RescueVehicle stale = vehicle(2L, "粤B旧", "114.059", "22.544");
        stale.setLocationUpdatedAt(java.time.LocalDateTime.now().minusMinutes(10));
        RescueVehicle never = vehicle(3L, "粤B无", "114.060", "22.545");
        never.setLocationUpdatedAt(null);
        RescueVehicle noCoord = vehicle(4L, "粤B空", "114.061", "22.546");
        noCoord.setLongitude(null);
        noCoord.setLatitude(null);
        noCoord.setLocationUpdatedAt(java.time.LocalDateTime.now());
        when(vehicleMapper.findByStatus("IDLE"))
                .thenReturn(List.of(fresh, stale, never, noCoord));

        NearbyVehiclesResponse resp = service.findNearby(
                new BigDecimal("114.057868"), new BigDecimal("22.543099"), 10);

        assertEquals(1, resp.getVehicles().size());
        assertEquals(1L, resp.getVehicles().get(0).getVehicle().getId());
        assertEquals(3, resp.getStaleVehicles().size());
        assertTrue(resp.getStaleVehicles().stream().noneMatch(NearbyVehicleVO::isLocationFresh));
    }

    @Test
    void nearbyUnmatchedMarksAllFalse() {
        RescueVehicle withDistrict = vehicle(1L, "粤B1", "114.058", "22.543");
        withDistrict.setDistrictId(5L);
        withDistrict.setLocationUpdatedAt(java.time.LocalDateTime.now());
        RescueVehicle alsoWithDistrict = vehicle(2L, "粤B2", "114.100", "22.600");
        alsoWithDistrict.setDistrictId(5L);
        alsoWithDistrict.setLocationUpdatedAt(java.time.LocalDateTime.now());
        when(vehicleMapper.findByStatus("IDLE"))
                .thenReturn(List.of(withDistrict, alsoWithDistrict));
        when(districtService.resolve(any(), any())).thenReturn(null);

        NearbyVehiclesResponse resp = service.findNearby(
                new BigDecimal("114.057868"), new BigDecimal("22.543099"), 10);

        assertNull(resp.getMatchedDistrict());
        assertEquals(2, resp.getVehicles().size());
        assertFalse(resp.getVehicles().get(0).isInMatchedDistrict());
        assertFalse(resp.getVehicles().get(1).isInMatchedDistrict());
    }

    @Test
    void createRejectsDuplicatePlate() {
        RescueVehicle v = new RescueVehicle();
        v.setPlateNo("粤B·救援01");
        when(vehicleMapper.findByPlateNo("粤B·救援01")).thenReturn(new RescueVehicle());
        assertThrows(RuntimeException.class, () -> service.createVehicle(v));
    }

    @Test
    void createRejectsDisabledDistrict() {
        RescueVehicle v = new RescueVehicle();
        v.setPlateNo("粤B·救援02");
        v.setDistrictId(5L);
        when(vehicleMapper.findByPlateNo("粤B·救援02")).thenReturn(null);
        District disabled = new District();
        disabled.setId(5L);
        disabled.setStatus("DISABLED");
        when(districtMapper.findById(5L)).thenReturn(disabled);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createVehicle(v));
        assertTrue(ex.getMessage().contains("片区"));
        verify(vehicleMapper, never()).insert(any());
    }

    @Test
    void createRejectsMissingDistrict() {
        RescueVehicle v = new RescueVehicle();
        v.setPlateNo("粤B·救援03");
        v.setDistrictId(99L);
        when(vehicleMapper.findByPlateNo("粤B·救援03")).thenReturn(null);
        when(districtMapper.findById(99L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createVehicle(v));
        assertTrue(ex.getMessage().contains("片区"));
        verify(vehicleMapper, never()).insert(any());
    }

    @Test
    void createSucceedsWithNullDistrictId() {
        RescueVehicle v = new RescueVehicle();
        v.setPlateNo("粤B·救援04");
        when(vehicleMapper.findByPlateNo("粤B·救援04")).thenReturn(null);
        when(vehicleMapper.insert(v)).thenReturn(1);

        assertTrue(service.createVehicle(v));
        verify(districtMapper, never()).findById(any());
        verify(vehicleMapper).insert(v);
    }

    @Test
    void createSucceedsWithEnabledDistrict() {
        RescueVehicle v = new RescueVehicle();
        v.setPlateNo("粤B·救援05");
        v.setDistrictId(3L);
        when(vehicleMapper.findByPlateNo("粤B·救援05")).thenReturn(null);
        District enabled = new District();
        enabled.setId(3L);
        enabled.setStatus("ENABLED");
        when(districtMapper.findById(3L)).thenReturn(enabled);
        when(vehicleMapper.insert(v)).thenReturn(1);

        assertTrue(service.createVehicle(v));
        verify(vehicleMapper).insert(v);
    }

    @Test
    void updateRejectsDisabledDistrict() {
        RescueVehicle v = vehicle(1L, "粤B1", "114.058", "22.543");
        v.setDistrictId(5L);
        when(vehicleMapper.findByPlateNo("粤B1")).thenReturn(null);
        District disabled = new District();
        disabled.setId(5L);
        disabled.setStatus("DISABLED");
        when(districtMapper.findById(5L)).thenReturn(disabled);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.updateVehicle(v));
        assertTrue(ex.getMessage().contains("片区"));
        verify(vehicleMapper, never()).update(any());
    }

    @Test
    void updateSucceedsWithNullDistrictId() {
        RescueVehicle v = vehicle(1L, "粤B1", "114.058", "22.543");
        v.setStatus("BUSY");
        when(vehicleMapper.findByPlateNo("粤B1")).thenReturn(null);
        when(vehicleMapper.update(v)).thenReturn(1);

        assertTrue(service.updateVehicle(v));
        verify(districtMapper, never()).findById(any());
        verify(vehicleMapper).update(v);
    }

    @Test
    void updateSucceedsWithEnabledDistrict() {
        RescueVehicle v = vehicle(1L, "粤B1", "114.058", "22.543");
        v.setStatus("BUSY");
        v.setDistrictId(3L);
        when(vehicleMapper.findByPlateNo("粤B1")).thenReturn(null);
        District enabled = new District();
        enabled.setId(3L);
        enabled.setStatus("ENABLED");
        when(districtMapper.findById(3L)).thenReturn(enabled);
        when(vehicleMapper.update(v)).thenReturn(1);

        assertTrue(service.updateVehicle(v));
        verify(vehicleMapper).update(v);
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

    private static RescueVehicle freshVehicle(Long id, String plate, String lng, String lat, Long districtId) {
        RescueVehicle v = vehicle(id, plate, lng, lat);
        v.setDistrictId(districtId);
        v.setLocationUpdatedAt(java.time.LocalDateTime.now());
        return v;
    }
}
