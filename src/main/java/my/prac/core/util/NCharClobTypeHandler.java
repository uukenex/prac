package my.prac.core.util;

import java.io.StringReader;
import java.sql.CallableStatement;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * [2026-09-05] TBOT_WORD_HIS 이모지 깨짐 수정용(NCharStringTypeHandler와 짝) -- RES(NCLOB)
 * 컬럼용. MyBatis 내장 NClobTypeHandler는 PreparedStatement.setCharacterStream()을 쓰는데,
 * 이건 이름과 달리 "국가문자셋 바인딩"을 driver에 명확히 보장하지 않는다(Oracle JDBC가
 * 여전히 DB 기본 문자셋으로 변환해서 라이브에서 실제로 이모지가 깨지는 걸 확인함). 대신
 * PreparedStatement.setNClob()/ResultSet.getNClob()을 직접 호출해서 국가문자셋(UTF8) 경유를
 * 확실히 강제한다.
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.NCLOB)
public class NCharClobTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setNClob(i, new StringReader(parameter), parameter.length());
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toString(rs.getNClob(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toString(rs.getNClob(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toString(cs.getNClob(columnIndex));
    }

    private String toString(NClob clob) throws SQLException {
        if (clob == null) return null;
        try {
            return clob.getSubString(1, (int) clob.length());
        } finally {
            clob.free();
        }
    }
}
