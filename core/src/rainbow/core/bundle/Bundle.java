package rainbow.core.bundle;

import java.util.Collections;
import java.util.List;

public class Bundle {

	/**
	 * Bundle的配置信息
	 */
	private BundleData data;

	private BundleState state = BundleState.FOUND;

	private BundleClassLoader classLoader;

	/**
	 * 所有的前辈
	 */
	private List<Bundle> ancestors = Collections.emptyList();

	/**
	 * 父辈
	 */
	private List<Bundle> parents = Collections.emptyList();

	/**
	 * Bundle入口类
	 */
	BundleActivator activator;

	public Bundle(BundleData data, BundleClassLoader classLoader) {
		this.data = data;
		this.classLoader = classLoader;
		this.classLoader.setBundle(this);
	}

	public BundleClassLoader getClassLoader() {
		return classLoader;
	}

	public BundleData getData() {
		return data;
	}

	public String getId() {
		return data.getId();
	}

	public String getDesc() {
		return data.getDesc();
	}

	public String[] getParentId() {
		return data.getParents();
	}

	public BundleState getState() {
		return state;
	}

	public void setState(BundleState state) {
		this.state = state;
	}

	public List<Bundle> getAncestors() {
		if (ancestors == null)
			return Collections.emptyList();
		return ancestors;
	}

	void setAncestors(List<Bundle> ancestors) {
		this.ancestors = ancestors;
	}

	public List<Bundle> getParents() {
		return parents;
	}

	void setParents(List<Bundle> parents) {
		if (parents == null)
			this.parents = Collections.emptyList();
		else
			this.parents = parents;
	}

	void setActivator(BundleActivator activator) {
		this.activator = activator;
	}

	public BundleActivator getActivator() {
		return activator;
	}

	public void destroy() {
		setParents(null);
		setAncestors(null);
		classLoader.destroy();
		classLoader = null;
	}

	@Override
	public String toString() {
		return getId();
	}

	@Override
	public int hashCode() {
		return data.getId().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Bundle other = (Bundle) obj;
		return getId().equals(other.getId());
	}
}
