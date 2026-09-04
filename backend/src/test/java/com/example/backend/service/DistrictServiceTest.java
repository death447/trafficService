package com.example.backend.service;

import com.example.backend.dto.DistrictRequest;
import com.example.backend.dto.LngLat;
import com.example.backend.entity.District;
import com.example.backend.mapper.DistrictMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistrictServiceTest {

    static final List<LngLat> SQUARE = List.of(
            new LngLat(114.04, 22.53), new LngLat(114.08, 22.53),
            new LngLat(114.08, 22.56), new LngLat(114.04, 22.56));

    static final String SQUARE_JSON =
            "[{\"lng\":114.04,\"lat\":22.53},{\"lng\":114.08,\"lat\":22.53},"
                    + "{\"lng\":114.08,\"lat\":22.56},{\"lng\":114.04,\"lat\":22.56}]";

    @Mock
    DistrictMapper districtMapper;

    @InjectMocks
    DistrictService service;

    @Test
    void createRejectsFewerThanThreeVertices() {
        DistrictRequest req = new DistrictRequest();
        req.setName("坏围栏");
        req.setCode("BAD");
        req.setFence(List.of(new LngLat(1, 1), new LngLat(2, 2)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("围栏至少需要3个顶点"));
        verify(districtMapper, never()).insert(any());
    }

    @Test
    void createRejectsDuplicateCode() {
        DistrictRequest req = validRequest("FT-CENTER");
        when(districtMapper.findByCode("FT-CENTER")).thenReturn(existing(1L, "FT-CENTER", SQUARE_JSON));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("编码"));
        verify(districtMapper, never()).insert(any());
    }

    @Test
    void createDefaultsStatusToEnabled() {
        DistrictRequest req = validRequest("NEW-CODE");
        req.setStatus(null);
        when(districtMapper.findByCode("NEW-CODE")).thenReturn(null);
        when(districtMapper.insert(any(District.class))).thenReturn(1);

        assertTrue(service.create(req));

        ArgumentCaptor<District> captor = ArgumentCaptor.forClass(District.class);
        verify(districtMapper).insert(captor.capture());
        assertEquals("ENABLED", captor.getValue().getStatus());
        assertEquals("NEW-CODE", captor.getValue().getCode());
        assertNotNull(captor.getValue().getFenceJson());
    }

    @Test
    void resolveReturnsSmallerIdWhenOverlapping() {
        District smaller = existing(1L, "A", SQUARE_JSON);
        District larger = existing(2L, "B", SQUARE_JSON);
        // return unsorted to verify service sorts by id
        when(districtMapper.findByStatus("ENABLED")).thenReturn(List.of(larger, smaller));

        District hit = service.resolve(new BigDecimal("114.057868"), new BigDecimal("22.543099"));
        assertNotNull(hit);
        assertEquals(1L, hit.getId());
    }

    @Test
    void resolveReturnsNullOutsideAllFences() {
        when(districtMapper.findByStatus("ENABLED"))
                .thenReturn(List.of(existing(1L, "A", SQUARE_JSON)));

        assertNull(service.resolve(new BigDecimal("114.10"), new BigDecimal("22.60")));
    }

    @Test
    void resolveSkipsCorruptFenceAndHitsGoodDistrict() {
        District corrupt = existing(1L, "BAD", "not-valid-json");
        District good = existing(2L, "GOOD", SQUARE_JSON);
        when(districtMapper.findByStatus("ENABLED")).thenReturn(List.of(corrupt, good));

        District hit = service.resolve(new BigDecimal("114.057868"), new BigDecimal("22.543099"));
        assertNotNull(hit);
        assertEquals(2L, hit.getId());
    }

    @Test
    void resolveReturnsNullWhenAllFencesCorrupt() {
        District corrupt1 = existing(1L, "BAD1", "not-valid-json");
        District corrupt2 = existing(2L, "BAD2", "[]");
        when(districtMapper.findByStatus("ENABLED")).thenReturn(List.of(corrupt1, corrupt2));

        assertNull(service.resolve(new BigDecimal("114.057868"), new BigDecimal("22.543099")));
    }

    @Test
    void deleteRejectsWhenVehicleReferenced() {
        when(districtMapper.findById(5L)).thenReturn(existing(5L, "X", SQUARE_JSON));
        when(districtMapper.countVehiclesByDistrictId(5L)).thenReturn(1);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.delete(5L));
        assertTrue(ex.getMessage().contains("片区仍被车辆或排班引用"));
        verify(districtMapper, never()).deleteById(any());
    }

    private static DistrictRequest validRequest(String code) {
        DistrictRequest req = new DistrictRequest();
        req.setName("测试片区");
        req.setCode(code);
        req.setFence(SQUARE);
        return req;
    }

    private static District existing(Long id, String code, String fenceJson) {
        District d = new District();
        d.setId(id);
        d.setName("片区" + id);
        d.setCode(code);
        d.setFenceJson(fenceJson);
        d.setStatus("ENABLED");
        return d;
    }
}
