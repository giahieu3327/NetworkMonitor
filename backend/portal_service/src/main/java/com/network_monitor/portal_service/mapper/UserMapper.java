package com.network_monitor.portal_service.mapper;

import com.network_monitor.portal_service.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    // Lấy danh sách User có filter keyword, phân trang và sắp xếp động
    List<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") int limit,
            @Param("sortBy") String sortBy,
            @Param("direction") String direction
    );

    // Đếm tổng số lượng bản ghi thỏa mãn điều kiện keyword để phục vụ phân trang
    long countSearchUsers(@Param("keyword") String keyword);
}