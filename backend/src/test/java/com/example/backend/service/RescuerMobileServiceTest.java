package com.example.backend.service;

import com.example.backend.dto.BindVehicleRequest;
import com.example.backend.dto.SceneRequest;
import com.example.backend.entity.DispatchOrder;
import com.example.backend.entity.RescueVehicle;
import com.example.backend.mapper.DispatchFieldRecordMapper;
import com.example.backend.mapper.DispatchMediaMapper;
import com.example.backend.mapper.DispatchOrderMapper;
import com.example.backend.mapper.RescueVehicleMapper;
import com.example.backend.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RescuerMobileServiceTest {

    @Mock DispatchOrderMapper dispatchOrderMapper;
    @Mock DispatchFieldRecordMapper fieldRecordMapper;
    @Mock DispatchMediaMapper mediaMapper;
    @Mock RescueVehicleMapper rescueVehicleMapper;
    @Mock UserMapper userMapper;
    @Mock LocalFileStorageService fileStorageService;
    @InjectMocks RescuerMobileService service;

    @Test
    void bindVehicleParsesRvPayloadAndClearsOldBind() {
        RescueVehicle vehicle = new RescueVehicle();
        vehicle.setId(1L);
        vehicle.setPlateNo("粤B救援1");
        when(rescueVehicleMapper.findById(1L)).thenReturn(vehicle);
        when(rescueVehicleMapper.update(any())).thenReturn(1);

        BindVehicleRequest req = new BindVehicleRequest();
        req.setQrPayload("RV:1");
        RescueVehicle bound = service.bindVehicle(9L, req);

        verify(rescueVehicleMapper).clearDriverByUserId(9L);
        ArgumentCaptor<RescueVehicle> captor = ArgumentCaptor.forClass(RescueVehicle.class);
        verify(rescueVehicleMapper).update(captor.capture());
        assertEquals(9L, captor.getValue().getDriverUserId());
        assertEquals(1L, bound.getId());
    }

    @Test
    void saveSceneFailsWhenNotAccepted() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setRescuerId(9L);
        order.setStatus("DISPATCHED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);

        SceneRequest req = new SceneRequest();
        req.setPlateNo("粤B12345");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.saveScene(9L, 1L, req));
        assertTrue(ex.getMessage().contains("接单") || ex.getMessage().contains("状态"));
        verify(fieldRecordMapper, never()).insert(any());
        verify(fieldRecordMapper, never()).update(any());
    }

    @Test
    void addMediaFailsOnIllegalBizType() {
        DispatchOrder order = new DispatchOrder();
        order.setId(1L);
        order.setRescuerId(9L);
        order.setStatus("ACCEPTED");
        when(dispatchOrderMapper.findById(1L)).thenReturn(order);

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.addMedia(9L, 1L, "OTHER", file));
        assertTrue(ex.getMessage().contains("bizType") || ex.getMessage().contains("类型"));
        verifyNoInteractions(fileStorageService);
        verify(mediaMapper, never()).insert(any());
    }
}
