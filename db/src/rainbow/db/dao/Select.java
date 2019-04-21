package rainbow.db.dao;

import static rainbow.core.util.Preconditions.checkArgument;
import static rainbow.core.util.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.common.base.Joiner;

import rainbow.core.model.exception.AppException;
import rainbow.core.util.Utils;
import rainbow.db.dao.condition.C;
import rainbow.db.dao.condition.EmptyCondition;
import rainbow.db.dao.condition.JoinCondition;
import rainbow.db.dao.condition.Op;
import rainbow.db.dao.model.Column;
import rainbow.db.dao.model.Entity;

public class Select {

	private String[] select;

	private String fromStr;

	private Join join;

	private boolean distinct = false;

	private List<FieldOld> fields;

	private C cnd = EmptyCondition.INSTANCE;

	private Pager pager;

	private List<OrderBy> orderBy;

	private String[] groupBy;

	private Function<String, FieldOld> fieldFunction;

	// 以下是普通select需要的
	private Entity entity;

	// 以下是joinSelect需要的信息
	private Map<String, Entity> entityMap;
	private List<String> tableAliases;

	public Entity getEntity() {
		return entity;
	}

	public List<FieldOld> getFields() {
		return fields;
	}

	public Select() {
	}

	public C getCondition() {
		return cnd;
	}

	public Select(String selectStr) {
		select = Utils.splitTrim(selectStr, ',');
	}

	public Select limit(int limit) {
		pager = new Pager(1, limit);
		return this;
	}

	public Select paging(int pageNo, int pageSize) {
		pager = new Pager(pageNo, pageSize);
		return this;
	}

	public Select setPager(Pager pager) {
		this.pager = pager;
		return this;
	}

	public Pager getPager() {
		return pager;
	}

	public Select distinct() {
		distinct = true;
		return this;
	}

	public Select from(String fromStr) {
		this.fromStr = fromStr;
		return this;
	}

	public Select from(Join join) {
		this.join = join;
		return this;
	}

	/**
	 * 添加第一个条件
	 * 
	 * @param property
	 * @param op
	 * @param param
	 * @return
	 */
	public Select where(String property, Op op, Object param) {
		cnd = C.make(property, op, param);
		return this;
	}

	/**
	 * 添加第一个条件
	 * 
	 * @param property
	 * @param op
	 * @param param
	 * @return
	 */
	public Select where(String property, Object param) {
		cnd = C.make(property, param);
		return this;
	}

	/**
	 * 添加第一个条件
	 * 
	 * @param cnd
	 * @return
	 */
	public Select where(C cnd) {
		this.cnd = cnd;
		return this;
	}

	/**
	 * And一个条件
	 * 
	 * @param cnd
	 * @return
	 */
	public Select and(C cnd) {
		this.cnd = this.cnd.and(cnd);
		return this;
	}

	/**
	 * And一个条件
	 * 
	 * @param property
	 * @param op
	 * @param param
	 * @return
	 */
	public Select and(String property, Op op, Object param) {
		return and(C.make(property, op, param));
	}

	/**
	 * And一个条件
	 * 
	 * @param property
	 * @param param
	 * @return
	 */
	public Select and(String property, Object param) {
		return and(C.make(property, param));
	}

	/**
	 * Or一个条件
	 * 
	 * @param cnd
	 * @return
	 */
	public Select or(C cnd) {
		this.cnd = this.cnd.or(cnd);
		return this;
	}

	/**
	 * Or一个条件
	 * 
	 * @param property
	 * @param op
	 * @param param
	 * @return
	 */
	public Select or(String property, Op op, Object param) {
		return or(C.make(property, op, param));
	}

	/**
	 * Or一个相等条件
	 * 
	 * @param property
	 * @param param
	 * @return
	 */
	public Select or(String property, Object param) {
		return or(C.make(property, param));
	}

	public Select andJoin(String left, String right) {
		return andJoin(left, Op.Equal, right);
	}

	public Select andJoin(String left, Op op, String right) {
		cnd = cnd.and(new JoinCondition(left, op, right));
		return this;
	}

	/**
	 * 设置OrderBy项
	 * 
	 * @param input
	 * @return
	 */
	public Select orderBy(String orderByStr) {
		if (Utils.hasContent(orderByStr)) {
			orderBy = OrderBy.parse(orderByStr);
		}
		return this;
	}

	public Select orderBy(List<OrderBy> orderBy) {
		this.orderBy = orderBy;
		return this;
	}

	public Select orderBy(OrderBy orderBy) {
		if (this.orderBy == null)
			this.orderBy = new ArrayList<OrderBy>();
		this.orderBy.add(orderBy);
		return this;
	}

	/**
	 * 添加GroupBy项
	 * 
	 * @param property
	 * @return
	 */
	public Select groupBy(String groupByStr) {
		groupBy = Utils.splitTrim(groupByStr, ',');
		return this;
	}

	public int getSelCount() {
		return select == null ? 0 : select.length;
	}

	public Sql build(Dao dao) {
		return build(dao, true);
	}

	private Sql build(Dao dao, boolean includePage) {
		fields = new ArrayList<FieldOld>();
		final Sql sql = new Sql().append("SELECT ");
		if (distinct)
			sql.append("DISTINCT ");
		if (join != null) {
			prepareJoin(dao);
			buildSelectMulti(dao, sql);
			join.build(entityMap, sql);
		} else {
			String[] tables = Utils.splitTrim(fromStr, ',');
			checkArgument(tables.length > 0, "from table not set");
			if (tables.length > 1) {
				prepareMulti(dao, tables);
				buildSelectMulti(dao, sql);
				buildFromMulti(sql);
			} else {
				buildSelectFrom(dao, sql, tables[0]);
			}
		}
		sql.whereCnd(dao, fieldFunction, cnd);
		if (groupBy != null) {
			sql.append(" GROUP BY ");
			Arrays.asList(groupBy).forEach(g -> {
				for (FieldOld field : fields) {
					String sqlPart = field.match(g);
					if (sqlPart != null) {
						sql.append(sqlPart);
						sql.appendTempComma();
						return;
					}
				}
				throw new AppException("GroupBy field {} not in select Fields", g);
			});
			sql.clearTemp();
		}
		if (orderBy != null) {
			sql.append(" ORDER BY ");
			orderBy.forEach(o -> {
				for (FieldOld field : fields) {
					String sqlPart = field.match(o.getProperty());
					if (sqlPart != null) {
						sql.append(sqlPart);
						if (o.isDesc())
							sql.append(" DESC");
						sql.appendTempComma();
						return;
					}
				}
				// not in select part
				FieldOld field = fieldFunction.apply(o.getProperty());
				checkNotNull(field, "order by field not found: {}", o.getProperty());
				sql.append(field.fullSqlName());
				if (o.isDesc())
					sql.append(" DESC");
				sql.appendTempComma();
			});
			sql.clearTemp();
		}
		if (includePage && pager != null)
			sql.paging(dao, pager);
		return sql;
	}

	public Sql buildCount(Dao dao) {
		Sql sql = build(dao, false);
		return new Sql().append("SELECT COUNT(1) FROM (").append(sql).append(") C");
	}

	private void buildSelectFrom(Dao dao, Sql sql, String table) {
		entity = dao.getEntity(table);
		checkNotNull(entity, "entity {} not found", fromStr);
		fieldFunction = new Function<String, FieldOld>() {
			@Override
			public FieldOld apply(String input) {
				return new FieldOld(input, entity);
			}
		};
		if (select == null || select.length == 0) {
			sql.append("*");
			addAllField(null, entity);
		} else {
			for (String s : select) {
				fields.add(new FieldOld(s, entity));
			}
			Joiner.on(',').appendTo(sql.getStringBuilder(), fields);
		}
		sql.append(" FROM ").append(entity.getDbName());
	}

	private void prepareMulti(Dao dao, String[] tables) {
		entityMap = new HashMap<String, Entity>();
		tableAliases = new ArrayList<String>(tables.length);
		for (String tableName : tables) {
			String[] table = Utils.split(tableName, ' ');
			checkArgument(table.length == 2, "{} need table alias", tableName);
			Entity entity = dao.getEntity(table[0]);
			checkNotNull(entity, "entity {} not found", tableName);
			entityMap.put(table[1], entity);
			tableAliases.add(table[1]);
		}
	}

	private void buildSelectMulti(Dao dao, final Sql sql) {
		final ColumnFinder columnFinder = new ColumnFinder() {
			@Override
			public Column find(String tableAlias, String fieldName) {
				Entity entity = entityMap.get(tableAlias);
				checkNotNull(entity, "table alias not found->[{}.{}]", tableAlias, fieldName);
				Column column = entity.getColumn(fieldName);
				return checkNotNull(column, "column {} of entity {} not defined", fieldName, entity.getName());
			}
		};
		fieldFunction = new Function<String, FieldOld>() {
			@Override
			public FieldOld apply(String input) {
				return new FieldOld(input, columnFinder);
			}
		};
		if (select == null || select.length == 0) {
			for (String tableAlias : tableAliases) {
				Entity entity = entityMap.get(tableAlias);
				addAllField(tableAlias, entity);
				sql.append(tableAlias).append(".*");
				sql.appendTempComma();
			}
		} else {
			for (String s : select) {
				if (s.endsWith(".*")) {
					final String tableAlias = s.substring(0, s.length() - 2);
					Entity entity = entityMap.get(tableAlias);
					checkNotNull(entity, "table alias not exist:{}", s);
					addAllField(tableAlias, entity);
					sql.append(s);
				} else {
					FieldOld field = fieldFunction.apply(s);
					fields.add(field);
					sql.append(field);
				}
				sql.appendTempComma();
			}
		}
		sql.clearTemp();
	}

	private void buildFromMulti(final Sql sql) {
		sql.append(" FROM ");
		for (String tableAlias : tableAliases) {
			Entity entity = entityMap.get(tableAlias);
			sql.append(entity.getDbName()).append(' ').append(tableAlias);
			sql.appendTempComma();
		}
		sql.clearTemp();
	}

	private void prepareJoin(Dao dao) {
		entityMap = new HashMap<String, Entity>();
		tableAliases = new ArrayList<String>();
		Entity entity = dao.getEntity(join.getMaster());
		entityMap.put(join.getAlias(), entity);
		tableAliases.add(join.getAlias());
		for (JoinTarget t : join.getTargets()) {
			entity = dao.getEntity(t.getTarget());
			entityMap.put(t.getAlias(), entity);
			tableAliases.add(t.getAlias());
		}
	}

	private void addAllField(String tableAlias, Entity entity) {
		for (Column column : entity.getColumns()) {
			fields.add(new FieldOld(tableAlias, column));
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Select [").append("select ");
		if (distinct)
			sb.append("distinct ");
		if (select == null)
			sb.append("*");
		else
			sb.append(Arrays.toString(select));
		sb.append(" from ").append(fromStr);
		if (cnd != null && !cnd.isEmpty())
			sb.append(" where...");
		sb.append("]");
		return sb.toString();
	}
}
