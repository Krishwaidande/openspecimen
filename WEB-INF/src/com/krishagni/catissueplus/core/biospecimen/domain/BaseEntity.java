package com.krishagni.catissueplus.core.biospecimen.domain;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.NotAudited;
import org.hibernate.proxy.HibernateProxyHelper;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

public class BaseEntity {
	private static final Map<String, Set<String>> entityProperties = new ConcurrentHashMap<>();

	protected Long id;
	
	protected transient List<Runnable> onSaveProcs;

	protected transient String opComments;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public List<Runnable> getOnSaveProcs() {
		return onSaveProcs;
	}

	public void setOnSaveProcs(List<Runnable> onSaveProcs) {
		this.onSaveProcs = onSaveProcs;
	}

	public void addOnSaveProc(Runnable onSaveProc) {
		if (onSaveProcs == null) {
			onSaveProcs = new ArrayList<>();
		}

		onSaveProcs.add(onSaveProc);
	}

	public String getOpComments() {
		return opComments;
	}

	public void setOpComments(String opComments) {
		this.opComments = opComments;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		return prime + ((getId() == null) ? 0 : getId().hashCode());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (obj == null	|| 
			getClass() != HibernateProxyHelper.getClassWithoutInitializingProxy(obj)) {
			return false;
		}

		BaseEntity other = (BaseEntity) obj;
		return getId() != null && getId().equals(other.getId());
	}

	public boolean sameAs(Object obj) {
		return equals(obj);
	}

	public String toAuditString() {
		return toAuditString(Collections.emptySet());
	}

	public String toAuditString(Set<String> requiredProps) {
		List<String> props = getAuditStringInclusionProps().stream().sorted().collect(Collectors.toList());

		Set<String> exProps = getAuditStringExclusionProps();
		if (CollectionUtils.isNotEmpty(exProps)) {
			props = props.stream().filter(prop -> !exProps.contains(prop)).collect(Collectors.toList());
		}

		if (CollectionUtils.isNotEmpty(requiredProps)) {
			props = props.stream().filter(requiredProps::contains).collect(Collectors.toList());
		} else {
			requiredProps = Collections.emptySet();
		}

		BeanWrapper bean = PropertyAccessorFactory.forBeanPropertyAccess(this);

		StringBuilder result = new StringBuilder();
		for (String prop : props) {
			String value = getPropValue(bean, prop);
			if (requiredProps.contains(prop) || StringUtils.isNotBlank(value)) {
				result.append(prop).append("=").append(value).append(",");
			}
		}

		if (result.length() > 0) {
			result.deleteCharAt(result.length() - 1);
		}

		return result.toString();
	}

	protected Set<String> getAuditStringInclusionProps() {
		return getProperties();
	}

	protected Set<String> getAuditStringExclusionProps() {
		return Collections.emptySet();
	}

	private Set<String> getProperties() {
		Set<String> properties = entityProperties.get(getClass().getName());
		if (properties != null) {
			return properties;
		}

		try {
			properties = new LinkedHashSet<>();

			for (Field field : getClass().getDeclaredFields()) {
				if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
					continue;
				}

				String name = field.getName();
				String capitalizedName = name.substring(0, 1).toUpperCase() + name.substring(1);

				if (!hasMethod("set" + capitalizedName, field.getType())) {
					continue;
				}

				if (!hasMethod("get" + capitalizedName) &&
					(!isBooleanType(field.getType()) || !hasMethod("is" + capitalizedName))) {
					continue;
				}

				properties.add(name);
			}

			entityProperties.put(getClass().getName(), properties);
			return properties;
		} catch (Exception e) {
			throw new RuntimeException("Error querying entity properties", e);
		}
	}

	private boolean hasMethod(String methodName, Class<?> ... parameters) {
		try {
			Method method = getClass().getDeclaredMethod(methodName, parameters);
			return method != null && method.getDeclaredAnnotation(NotAudited.class) == null;
		} catch (NoSuchMethodException nsme) {
			return false;
		}
	}

	private boolean isBooleanType(Class<?> klass) {
		return klass.getSimpleName().equalsIgnoreCase("boolean");
	}

	private String getPropValue(BeanWrapper bean, String prop) {
		String result;

		try {
			Object value = bean.getPropertyValue(prop);
			result = toObjectString(value);
		} catch (Exception e) {
			e.printStackTrace();
			result = "Error - " + e.getMessage();
		}

		return result;
	}

	private String toObjectString(Object obj) {
		String result = null;

		if (isSimpleType(obj)) {
			result = obj.toString();
		} else if (obj instanceof BigDecimal) {
			result = ((BigDecimal) obj).setScale(6, BigDecimal.ROUND_HALF_UP).toString();
		} else if (obj instanceof Number) {
			result = obj.toString();
		} else if (obj instanceof Iterable) {
			result = toCollectionString((Iterable) obj);
		} else if (isAssignableFrom(BaseEntity.class, obj)) {
			result = "{id=" + getObjId(obj) + "}";
		} else if (obj != null) {
			result = "Unknown object type: " + obj.getClass().getName();
		}

		return result;
	}

	private boolean isSimpleType(Object value) {
		if (value == null) {
			return false;
		}

		return value instanceof String ||
			ClassUtils.isPrimitiveOrWrapper(value.getClass()) ||
			value instanceof Date ||
			value.getClass().isEnum();
	}

	private String toCollectionString(Iterable<?> collection) {
		try {
			StringBuilder collStr = new StringBuilder();
			for (Object element : collection) {
				collStr.append(toObjectString(element)).append(",");
			}

			if (collStr.length() > 0) {
				collStr.deleteCharAt(collStr.length() - 1);
			}

			return collStr.length() > 0 ? "[" + collStr.toString() + "]" : null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private boolean isAssignableFrom(Class<?> superClass, Object value) {
		return value != null && superClass.isAssignableFrom(value.getClass());
	}

	private Object getObjId(Object value) {
		try {
			Object id = PropertyAccessorFactory.forBeanPropertyAccess(value).getPropertyValue("id");
			return id == null ? StringUtils.EMPTY : id;
		} catch (Exception e) {
			e.getMessage();
			return e.getMessage();
		}
	}
}