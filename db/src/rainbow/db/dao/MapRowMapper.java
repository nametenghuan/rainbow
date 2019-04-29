package rainbow.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rainbow.core.util.Utils;
import rainbow.db.dao.model.Entity;
import rainbow.db.jdbc.RowMapper;

public class MapRowMapper implements RowMapper<Map<String, Object>> {

	private List<Field> fields;

	public MapRowMapper(Select select) {
		this.fields = select.getFields();
	}

	public MapRowMapper(Entity entity) {
		this.fields = Utils.transform(entity.getColumns(), Field::fromColumn);
	}

	@Override
	public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
		Map<String, Object> map = new HashMap<String, Object>(fields.size());
		int index = 1;
		for (Field field : fields) {
			Object value = DaoUtils.getResultSetValue(rs, index++, field.getColumn());
			if (value != null) {
				map.put(field.getName(), value);
			}
		}
		return map;
	}

}
