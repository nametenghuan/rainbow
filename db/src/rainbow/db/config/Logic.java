package rainbow.db.config;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * 系统配置的逻辑数据源
 * 
 * @author lijinghui
 * 
 */
public class Logic {

	/**
	 * 对应的模型名称，为空则与id相
	 */
	@XmlAttribute
	private String model;

	@XmlAttribute
	private String physic;

	@XmlAttribute
	private String id;
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getPhysic() {
		return physic;
	}

	public void setPhysic(String physic) {
		this.physic = physic;
	}

	@Override
	public String toString() {
		return String.format("logic:%s physic:%s",  id, physic);
	}

}
