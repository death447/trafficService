package com.example.backend.service;

import com.example.backend.dto.VehicleTypeRequest;
import com.example.backend.entity.AccidentVehicleType;
import com.example.backend.mapper.AccidentVehicleTypeMapper;
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
class AccidentVehicleTypeServiceTest {

    @Mock AccidentVehicleTypeMapper mapper;
    @InjectMocks AccidentVehicleTypeService service;

    @Test
    void createRejectsDuplicateName() {
        VehicleTypeRequest req = new VehicleTypeRequest();
        req.setName("轿车");
        when(mapper.findByName("轿车")).thenReturn(new AccidentVehicleType());
        assertThrows(RuntimeException.class, () -> service.create(req));
        verify(mapper, never()).insert(any());
    }

    @Test
    void listEnabledFiltersAndSorts() {
        AccidentVehicleType a = new AccidentVehicleType();
        a.setName("越野车"); a.setStatus("ENABLED"); a.setSortOrder(9);
        AccidentVehicleType b = new AccidentVehicleType();
        b.setName("轿车"); b.setStatus("ENABLED"); b.setSortOrder(1);
        AccidentVehicleType c = new AccidentVehicleType();
        c.setName("旧型"); c.setStatus("DISABLED"); c.setSortOrder(0);
        when(mapper.findAll()).thenReturn(List.of(a, b, c));
        List<AccidentVehicleType> list = service.listEnabled();
        assertEquals(2, list.size());
        assertEquals("轿车", list.get(0).getName());
        assertEquals("越野车", list.get(1).getName());
    }

    @Test
    void requireEnabledRejectsDisabled() {
        AccidentVehicleType t = new AccidentVehicleType();
        t.setId(1L); t.setStatus("DISABLED");
        when(mapper.findById(1L)).thenReturn(t);
        assertThrows(RuntimeException.class, () -> service.requireEnabled(1L));
    }
}
