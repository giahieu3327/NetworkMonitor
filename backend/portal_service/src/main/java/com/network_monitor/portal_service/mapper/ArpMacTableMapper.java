
package com.network_monitor.portal_service.mapper;

import com.network_monitor.portal_service.model.entity.ArpMacTable;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArpMacTableMapper {

    List<ArpMacTable> searchByDeviceId(
            @Param("deviceId") Long deviceId,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize,
            @Param("sortBy") String sortBy,
            @Param("direction") String direction
    );

    long countByDeviceId(
            @Param("deviceId") Long deviceId,
            @Param("keyword") String keyword
    );
}
