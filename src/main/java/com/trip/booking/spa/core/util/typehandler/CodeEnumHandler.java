package com.trip.booking.spa.core.util.typehandler;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *  Created by zhe.hao
 */
public class CodeEnumHandler implements TypeHandler<Enum<?>> {

    public Class<Enum<?>> enumType;

    public CodeEnumHandler(Class<Enum<?>> enumType) {
        this.enumType = enumType;
    }

    @Override
    public void setParameter(PreparedStatement ps, int i, Enum parameter, JdbcType jdbcType) throws SQLException {
        ps.setInt(i, EnumCodeUtil.getCode(parameter));
    }

    @Override
    public Enum getResult(ResultSet rs, String columnName) throws SQLException {
        return EnumCodeUtil.getEnumByCode(rs.getInt(columnName), enumType);
    }

    @Override
    public Enum getResult(ResultSet rs, int columnIndex) throws SQLException {
        return EnumCodeUtil.getEnumByCode(rs.getInt(columnIndex), enumType);
    }

    @Override
    public Enum getResult(CallableStatement cs, int columnIndex) throws SQLException {
        return EnumCodeUtil.getEnumByCode(cs.getInt(columnIndex), enumType);
    }
}
