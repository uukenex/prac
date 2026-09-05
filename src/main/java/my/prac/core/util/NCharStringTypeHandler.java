package my.prac.core.util;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * [2026-09-05] TBOT_WORD_HIS 이모지 깨짐 수정용 -- MyBatis 내장 NStringTypeHandler를 먼저
 * 썼는데도(REQ/RES를 NVARCHAR2/NCLOB로 바꾸고, jdbcType/typeHandler까지 지정) 라이브에서
 * 여전히 이모지가 깨져서, 확실하게 PreparedStatement.setNString()/ResultSet.getNString()을
 * 호출하는 걸 직접 보장하려고 만든 핸들러(NCHAR/NVARCHAR2 컬럼용). setNString()은 국가
 * 문자셋(NLS_NCHAR_CHARACTERSET, 이 DB에선 UTF8)을 거치므로 이모지(서로게이트 페어)도
 * 안전하게 저장/조회된다.
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.NVARCHAR)
public class NCharStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setNString(i, parameter);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getNString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getNString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getNString(columnIndex);
    }
}
