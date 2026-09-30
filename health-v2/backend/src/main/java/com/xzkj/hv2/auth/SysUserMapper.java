package com.xzkj.hv2.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {

    SysUser findByUsername(@Param("username") String username);

    SysUser findById(@Param("id") long id);

    int updateLastLoginAt(@Param("id") long id);
}
