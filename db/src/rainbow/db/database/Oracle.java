package rainbow.db.database;

public class Oracle extends AbstractDialect {

	@Override
	public String now() {
		return "sysdate()";
	}

	@Override
	public String wrapLimitSql(String sql, int limit) {
		return String.format("select A.*,ROWNUM from (%s) A where ROWNUM<=%d", sql, limit);
	}

	@Override
	public String wrapPagedSql(String sql, int pageSize, int pageNo) {
		int from = (pageNo - 1) * pageSize + 1;
		int to = pageNo * pageSize;
		return String.format("select * from (select A.*,ROWNUM AS RNfrom (%s) A where ROWNUM <=%d) where RN>=%d", sql,
				to, from);
	}

	@Override
	public String wrapDirtyRead(String sql) {
		return sql;
	}

}
