
package com.network_monitor.portal_service.mapper;

import com.network_monitor.portal_service.model.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceMapper {

    List<Device> searchDevices(
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") int limit,
            @Param("sortBy") String sortBy,
            @Param("direction") String direction
    );

    long countSearchDevices(
            @Param("keyword") String keyword
    );
}
