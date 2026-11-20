
package com.network_monitor.portal_service.mapper;

import com.network_monitor.portal_service.model.entity.DeviceInterface;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceInterfaceMapper {

    // ==================== GET ALL INTERFACES BY DEVICE ID ====================

    List<DeviceInterface> searchInterfacesByDeviceId(
            @Param("deviceId") Long deviceId,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize,
            @Param("sortBy") String sortBy,
            @Param("direction") String direction
    );

    long countInterfacesByDeviceId(
            @Param("deviceId") Long deviceId,
            @Param("keyword") String keyword
    );

}
