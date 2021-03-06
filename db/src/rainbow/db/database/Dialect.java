package rainbow.db.database;

import java.util.List;

import rainbow.db.dao.Dao;
import rainbow.db.model.Field;

/**
 * 数据库方言接口
 * 
 * @author lijinghui
 * 
 */
public interface Dialect {

	/**
	 * 返回获取数据库服务器当前的时间SQL
	 * 
	 * @return
	 */
	String now();

	/**
	 * 返回取前几条记录的语句
	 * 
	 * @param sql   要查询的语句
	 * @param limit 需要返回的行数
	 * @return
	 */
	String wrapLimitSql(String sql, int limit);

	/**
	 * 返回分页的查询语句
	 * 
	 * @param sql   要查询的语句
	 * @param pager 分页信息
	 * @return
	 */
	String wrapPagedSql(String sql, int pageSize, int pageNo);

	/**
	 * 返回使用脏读的sql
	 * 
	 * @param sql
	 * @return
	 */
	String wrapDirtyRead(String sql);

	/**
	 * 获取数据库中所有数据表的sql,应该按id排序
	 * 
	 * @return
	 */
	String getTableListSql();

	/**
	 * 获取数据库中指定数据表字段的sql
	 * 
	 * @param table
	 * @return
	 */
	List<Field> getColumn(String table, Dao dao);

	/**
	 * 返回清空一个数据表sql
	 * 
	 * @param tableName
	 * @return
	 */
	String clearTable(String tableName);

}