package com.trip.booking.spa.platform.mybatis.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 功能描述: <br>
 * <>
 *
 * @param: [ps, i, parameter, jdbcType]
 * i:Jdbc预编译时设置参数的索引值
 * parameter:要插入的参数值  true 或者false
 * jdbcType:要插入JDBC的类型
 * @return:
 * @author: zhe.hao
 **/
public class StatusEnumHandler extends BaseTypeHandler<Boolean> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Boolean parameter, JdbcType jdbcType) throws SQLException {
        if (parameter) {
            ps.setInt(i, 1);
        } else {
            ps.setInt(i, 0);
        }
    }

    @Override
    public Boolean getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int man = rs.getInt(columnName);
        return man == 1 ? true : false;
    }

    @Override
    public Boolean getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int man = rs.getInt(columnIndex);
        return man == 1 ? true : false;
    }

    @Override
    public Boolean getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int man = cs.getInt(columnIndex);
        return man == 1 ? true : false;
    }
}
