package rainbow.db.modelx;

import java.util.List;

import rainbow.core.model.object.NameObject;
import rainbow.core.util.Utils;

public class TableTag extends NameObject {
	
	private List<Field> fields;
	
	private List<TagProperty> properties;

	public List<Field> getFields() {
		return fields;
	}

	public void setFields(List<Field> fields) {
		this.fields = fields;
	}

	public List<TagProperty> getProperties() {
		return properties;
	}

	public void setProperties(List<TagProperty> properties) {
		this.properties = properties;
	}

	public boolean hasProperty() {
		return !Utils.isNullOrEmpty(properties);
	}

}