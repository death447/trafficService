package com.example.backend.service;

import com.example.backend.entity.Permission;
import com.example.backend.mapper.PermissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionMapper permissionMapper;
    @InjectMocks
    private PermissionService permissionService;

    @Test
    void getMenuTreeAttachesChildren() {
        Permission parent = new Permission();
        parent.setId(1L);
        parent.setPermissionName("System");
        Permission child = new Permission();
        child.setId(2L);
        child.setPermissionName("User");
        when(permissionMapper.findByParentId(0L)).thenReturn(List.of(parent));
        when(permissionMapper.findByParentId(1L)).thenReturn(List.of(child));
        when(permissionMapper.findByParentId(2L)).thenReturn(Collections.emptyList());

        List<Permission> tree = permissionService.getMenuTree();

        assertEquals(1, tree.size());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals(2L, tree.get(0).getChildren().get(0).getId());
    }
}
