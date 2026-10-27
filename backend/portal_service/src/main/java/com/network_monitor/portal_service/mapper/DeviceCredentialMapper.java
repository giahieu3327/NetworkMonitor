
package com.network_monitor.portal_service.mapper;

import com.network_monitor.portal_service.model.entity.DeviceCredential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceCredentialMapper {

    List<DeviceCredential> searchCredentialsByDeviceId(
            @Param("deviceId") Long deviceId,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") int limit,
            @Param("sortBy") String sortBy,
            @Param("direction") String direction
    );

    long countCredentialsByDeviceId(
            @Param("deviceId") Long deviceId,
            @Param("keyword") String keyword
    );
}
