package com.example.backend.service;

import com.example.backend.mapper.RoleMapper;
import com.example.backend.mapper.RolePermissionMapper;
import com.example.backend.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private RolePermissionMapper rolePermissionMapper;
    @InjectMocks
    private RoleService roleService;

    @Test
    void assignPermissionsDeletesExistingThenInsertsEach() {
        roleService.assignPermissions(5L, List.of(10L, 11L));

        verify(rolePermissionMapper).deleteAllByRoleId(5L);
        verify(rolePermissionMapper).insert(5L, 10L);
        verify(rolePermissionMapper).insert(5L, 11L);
        verifyNoMoreInteractions(rolePermissionMapper);
    }

    @Test
    void assignPermissionsWithNullListOnlyDeletes() {
        roleService.assignPermissions(3L, null);

        verify(rolePermissionMapper).deleteAllByRoleId(3L);
        verifyNoMoreInteractions(rolePermissionMapper);
    }
}
