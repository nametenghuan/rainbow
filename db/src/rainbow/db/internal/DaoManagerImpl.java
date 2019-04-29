package rainbow.db.internal;

import static rainbow.core.util.Preconditions.checkNotNull;
import static rainbow.core.util.Preconditions.checkState;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.xml.bind.JAXBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson.JSON;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import rainbow.core.bundle.Bean;
import rainbow.core.model.IAdaptable;
import rainbow.core.model.exception.AppException;
import rainbow.core.platform.Platform;
import rainbow.core.util.Utils;
import rainbow.core.util.encrypt.EncryptUtils;
import rainbow.core.util.ioc.ActivatorAwareObject;
import rainbow.core.util.ioc.DisposableBean;
import rainbow.core.util.ioc.InitializingBean;
import rainbow.core.util.ioc.InjectProvider;
import rainbow.db.DaoManager;
import rainbow.db.config.Config;
import rainbow.db.config.Logic;
import rainbow.db.config.Physic;
import rainbow.db.config.Property;
import rainbow.db.dao.Dao;
import rainbow.db.dao.DaoImpl;
import rainbow.db.dao.model.Entity;
import rainbow.db.dao.model.Link;
import rainbow.db.modelx.ModelX;
import rainbow.db.modelx.Unit;

@Bean(extension = InjectProvider.class)
public class DaoManagerImpl extends ActivatorAwareObject
		implements DaoManager, IAdaptable, InitializingBean, DisposableBean {

	private static Logger logger = LoggerFactory.getLogger(DaoManagerImpl.class);

	private Map<String, Map<String, Entity>> modelMap = new HashMap<String, Map<String, Entity>>();

	private Map<String, DruidDataSource> physicMap;

	private Map<String, Dao> logicMap;

	private Dao onlyDao;

	private Context ctx;

	@Override
	public Dao getDao(String name) {
		return logicMap.get(name);
	}

	private Config loadConfig(String filename) throws FileNotFoundException, JAXBException, IOException {
		Path file = activator.getConfigureFile(filename);
		if (!Files.exists(file))
			return null;
		return Config.getXmlBinder().unmarshal(file);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Config config = null;
		if (Platform.isDev()) {
			config = loadConfig("database.xml.dev");
		}
		if (config == null)
			config = loadConfig("database.xml");
		if (config == null)
			return;

		physicMap = readPhysicConfig(config);
		try {
			logicMap = readLogicConfig(config);
		} finally {
			if (ctx != null) {
				try {
					ctx.close();
				} catch (NamingException ex) {
					logger.debug("Could not close JNDI InitialContext", ex);
				}
			}
		}
	}

	/**
	 * 读取物理数据源配置
	 * 
	 * @param config
	 * @return
	 * @throws IntrospectionException
	 * @throws InvocationTargetException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	private Map<String, DruidDataSource> readPhysicConfig(Config config)
			throws IntrospectionException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		ImmutableMap.Builder<String, DruidDataSource> builder = ImmutableMap.builder();

		BeanInfo beanInfo = Introspector.getBeanInfo(DruidDataSource.class, Object.class);
		Map<String, PropertyDescriptor> map = new HashMap<String, PropertyDescriptor>();
		Set<String> general = ImmutableSet.of("url", "name", "username", "password", "driverClassName", "driver",
				"driverClassLoader");
		for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
			String name = pd.getName();
			if (general.contains(name))
				continue;
			map.put(pd.getName(), pd);
		}

		for (Physic physic : config.getPhysics()) {
			DruidDataSource dataSource = new DruidDataSource();
			dataSource.setName(physic.getId());
			dataSource.setDriverClassName(physic.getDriverClass());
			dataSource.setUrl(physic.getJdbcUrl());
			dataSource.setUsername(EncryptUtils.decrypt("DB_USER", physic.getUsername()));
			dataSource.setPassword(EncryptUtils.decrypt("DB_PASS", physic.getPassword()));
			if (!Utils.isNullOrEmpty(physic.getProperty())) {
				for (Property property : physic.getProperty()) {
					PropertyDescriptor pd = map.get(property.getName());
					if (pd == null) {
						logger.warn("unsupported datasource property [{}]", property.getName());
					} else {
						Class<?> ptype = pd.getWriteMethod().getParameterTypes()[0];
						if (ptype == String.class) {
							pd.getWriteMethod().invoke(dataSource, property.getValue());
						} else if (ptype == Integer.TYPE || ptype == Integer.class) {
							int value = Integer.parseInt(property.getValue());
							pd.getWriteMethod().invoke(dataSource, value);
						} else if (ptype == Long.TYPE || ptype == Long.class) {
							long value = Long.parseLong(property.getValue());
							pd.getWriteMethod().invoke(dataSource, value);
						} else if (ptype == Boolean.TYPE || ptype == Boolean.class) {
							boolean value = property.getValue().equalsIgnoreCase("true");
							pd.getWriteMethod().invoke(dataSource, value);
						} else
							throw new AppException("错误的数据库参数配置[{}]", property.toString());
					}
					logger.info("read datasource [{}] property: {}", physic.getId(), property.toString());
				}
			}
			builder.put(physic.getId(), dataSource);
			logger.debug("register physic datasource {}", physic.toString());
		}
		return builder.build();
	}

	private Map<String, Entity> loadModel(String name) {
		Map<String, Entity> model = modelMap.get(name);
		if (model!=null) return model;
		Path modelFile = activator.getConfigureFile(name + ".rdmx");
		String fileName = modelFile.getFileName().toString();
		checkState(Files.exists(modelFile), "database model file not exist:{}", fileName);
		ModelX modelX = null;
		try (InputStream is = Files.newInputStream(modelFile)) {
			modelX = JSON.parseObject(is, StandardCharsets.UTF_8, ModelX.class);
		} catch (Exception e) {
			logger.error("load rdmx file {} faild", fileName);
			throw new RuntimeException(e);
		}
		model = new HashMap<String, Entity>();
		loadUnit(model, modelX);
		loadLink(model, modelX);
		modelMap.put(name, model);
		return model;
	}

	private void loadUnit(Map<String, Entity> model, Unit unit) {
		unit.getTables().stream().map(Entity::new).forEach(e -> model.put(e.getName(), e));
		unit.getUnits().stream().forEach(u -> loadUnit(model, u));
	}

	private void loadLink(Map<String, Entity> model, Unit unit) {
		unit.getTables().stream().forEach(e -> {
			if (Utils.isNullOrEmpty(e.getLinkFields()))
				return;
			Entity entity = model.get(e.getName());
			Map<String, Link> links = e.getLinkFields().stream().map(l-> new Link(model, entity, l))
			.collect(Collectors.toMap(Link::getName, Functions.identity()));
			entity.setLinks(links);
		});
		unit.getUnits().stream().forEach(u -> loadLink(model, u));
	}

	/**
	 * 读取逻辑数据源配置
	 * 
	 * @param config     配置对象
	 * @param entityMaps 数据模型Map
	 * @return
	 */
	private Map<String, Dao> readLogicConfig(Config config) {
		ImmutableMap.Builder<String, Dao> logicBuilder = ImmutableMap.builder();
		for (Logic logic : config.getLogics()) {
			String model = logic.getModel();
			if (model == null)
				model = logic.getId();
			DataSource dataSource = getDataSource(logic.getPhysic());
			checkNotNull(dataSource, "physic datasource[{}] of logic source[{}] not defined", logic.getPhysic(),
					logic.getId());

			Map<String, Entity> entityMap = loadModel(model);
			DaoImpl dao = new DaoImpl(dataSource, entityMap);
			dao.setName(logic.getId());
			logicBuilder.put(logic.getId(), dao);
			onlyDao = dao;
			logger.info("register logic datasource [{}] with model [{}]", logic.toString(), model);
		}
		Map<String, Dao> result = logicBuilder.build();
		if (result.size() != 1)
			onlyDao = null;
		return result;
	}

	@Override
	public void destroy() throws Exception {
		if (logicMap != null)
			logicMap = null;
		if (physicMap != null) {
			for (DruidDataSource ds : physicMap.values()) {
				ds.close();
			}
			physicMap = null;
		}
	}

	@Override
	public Collection<String> getLogicSources() {
		return logicMap.keySet();
	}

	private DataSource getDataSource(String name) {
		DataSource dataSource = physicMap.get(name);
		if (dataSource == null) {
			logger.debug("Looking up JNDI dataSource object with name {}", name);
			try {
				if (ctx == null)
					ctx = new InitialContext();
				dataSource = (DataSource) ctx.lookup(name);
			} catch (Exception e) {
				logger.error("lookup JNDI datasource {} object failed", name, e);
			}
		}
		return dataSource;
	}

	@Override
	public Object getAdapter(Class<?> adapter) {
		if (adapter != InjectProvider.class)
			return null;

		return new InjectProvider() {

			@Override
			public Class<?> getInjectClass() {
				return Dao.class;
			}

			@Override
			public Object getInjectObject(String name, String destClassName) {
				if (onlyDao != null)
					return onlyDao;
				// 未来考虑在配置中根据类名注入对应的Dao
				return logicMap.get(name);
			}
		};
	}

}
