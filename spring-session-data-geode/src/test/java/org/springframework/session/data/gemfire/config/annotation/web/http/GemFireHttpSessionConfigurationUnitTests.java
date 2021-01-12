/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.session.data.gemfire.config.annotation.web.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.ExpirationAttributes;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.data.gemfire.RegionAttributesFactoryBean;
import org.springframework.data.gemfire.util.ArrayUtils;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.SessionCacheTypeAwareRegionFactoryBean;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.SpringSessionGemFireConfigurer;
import org.springframework.session.data.gemfire.support.DeltaAwareDirtyPredicate;
import org.springframework.session.data.gemfire.support.EqualsDirtyPredicate;
import org.springframework.session.data.gemfire.support.IsDirtyPredicate;
import org.springframework.util.ReflectionUtils;

/**
 * Unit Tests for {@link GemFireHttpSessionConfiguration} class.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.ExpirationAttributes
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.RegionAttributes
 * @see org.apache.geode.cache.RegionShortcut
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.apache.geode.cache.client.ClientRegionShortcut
 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory
 * @see org.springframework.context.ApplicationContext
 * @see org.springframework.context.ConfigurableApplicationContext
 * @see org.springframework.core.env.ConfigurableEnvironment
 * @see org.springframework.core.env.Environment
 * @see org.springframework.core.env.PropertySource
 * @see org.springframework.core.type.AnnotationMetadata
 * @see org.springframework.data.gemfire.GemfireTemplate
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.SessionCacheTypeAwareRegionFactoryBean
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.SpringSessionGemFireConfigurer
 * @since 1.1.0
 */
public class GemFireHttpSessionConfigurationUnitTests {

	@SuppressWarnings("unchecked")
	private static <T> T getField(Object obj, String fieldName) {

		try {

			Field field = resolveField(obj, fieldName);

			field.setAccessible(true);

			return (T) field.get(obj);
		}
		catch (NoSuchFieldException cause) {
			throw new IllegalArgumentException(cause);
		}
		catch (IllegalAccessException cause) {
			throw new Error(String.format("Unable to access field [%1$s] on object of type [%2$s]",
				fieldName, obj.getClass().getName()), cause);
		}
	}

	private static Field resolveField(Object obj, String fieldName) throws NoSuchFieldException {
		return resolveField(obj.getClass(), fieldName);
	}

	private static Field resolveField(Class<?> type, String fieldName) throws NoSuchFieldException {

		Field field = ReflectionUtils.findField(type, fieldName);

		if (field == null) {
			throw new NoSuchFieldException(String.format("Field with name [%1$s] was not found in class [%2$s]",
				fieldName, type.getName()));
		}

		return field;
	}

	private GemFireHttpSessionConfiguration gemfireConfiguration;

	@Before
	public void setup() {

		this.gemfireConfiguration = spy(new GemFireHttpSessionConfiguration());

		ApplicationContext mockApplicationContext = mock(ApplicationContext.class);

		when(mockApplicationContext.getBean(eq(SpringSessionGemFireConfigurer.class)))
			.thenThrow(new NoSuchBeanDefinitionException("No SpringSessionGemFireConfigurer bean present"));

		this.gemfireConfiguration.setApplicationContext(mockApplicationContext);
	}

	@Test
	public void setAndGetBeanClassLoader() {

		assertThat(this.gemfireConfiguration.getBeanClassLoader()).isNull();

		this.gemfireConfiguration.setBeanClassLoader(Thread.currentThread().getContextClassLoader());

		assertThat(this.gemfireConfiguration.getBeanClassLoader())
			.isEqualTo(Thread.currentThread().getContextClassLoader());

		this.gemfireConfiguration.setBeanClassLoader(null);

		assertThat(this.gemfireConfiguration.getBeanClassLoader()).isNull();
	}

	@Test
	public void setAndGetClientRegionShortcut() {

		assertThat(this.gemfireConfiguration.getClientRegionShortcut())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT);

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.CACHING_PROXY);

		assertThat(this.gemfireConfiguration.getClientRegionShortcut())
			.isEqualTo(ClientRegionShortcut.CACHING_PROXY);

		this.gemfireConfiguration.setClientRegionShortcut(null);

		assertThat(this.gemfireConfiguration.getClientRegionShortcut())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	@Test
	public void setAndGetExposeConfigurationAsProperties() {

		assertThat(this.gemfireConfiguration.isExposeConfigurationAsProperties()).isFalse();

		this.gemfireConfiguration.setExposeConfigurationAsProperties(true);

		assertThat(this.gemfireConfiguration.isExposeConfigurationAsProperties()).isTrue();

		this.gemfireConfiguration.setExposeConfigurationAsProperties(false);

		assertThat(this.gemfireConfiguration.isExposeConfigurationAsProperties()).isFalse();
	}

	@Test
	public void setAndGetIndexedSessionAttributes() {

		assertThat(this.gemfireConfiguration.getIndexableSessionAttributes()).isEmpty();

		this.gemfireConfiguration.setIndexableSessionAttributes(ArrayUtils.asArray("one", "two"));

		assertThat(this.gemfireConfiguration.getIndexableSessionAttributes()).containsExactly("one", "two");

		this.gemfireConfiguration.setIndexableSessionAttributes(new String[0]);

		assertThat(this.gemfireConfiguration.getIndexableSessionAttributes()).isEmpty();

		this.gemfireConfiguration.setIndexableSessionAttributes(ArrayUtils.asArray("two"));

		assertThat(this.gemfireConfiguration.getIndexableSessionAttributes()).containsExactly("two");

		this.gemfireConfiguration.setIndexableSessionAttributes(null);

		assertThat(this.gemfireConfiguration.getIndexableSessionAttributes()).isEmpty();
	}

	@Test
	public void setAndGetIsDirtyPredicate() {

		assertThat(this.gemfireConfiguration.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);

		IsDirtyPredicate mockDirtyPredicate = mock(IsDirtyPredicate.class);

		this.gemfireConfiguration.setIsDirtyPredicate(mockDirtyPredicate);

		assertThat(this.gemfireConfiguration.getIsDirtyPredicate()).isEqualTo(mockDirtyPredicate);

		this.gemfireConfiguration.setIsDirtyPredicate(null);

		assertThat(this.gemfireConfiguration.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);

		this.gemfireConfiguration.setIsDirtyPredicate(EqualsDirtyPredicate.INSTANCE);

		assertThat(this.gemfireConfiguration.getIsDirtyPredicate()).isEqualTo(EqualsDirtyPredicate.INSTANCE);
	}

	@Test
	public void setAndGetMaxInactiveIntervalInSeconds() {

		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(300);

		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(300);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(Integer.MAX_VALUE);

		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(Integer.MAX_VALUE);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(-1);

		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(-1);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(Integer.MIN_VALUE);

		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(Integer.MIN_VALUE);
	}

	@Test
	public void setAndGetPoolName() {

		assertThat(this.gemfireConfiguration.getPoolName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_POOL_NAME);

		this.gemfireConfiguration.setPoolName("TestPoolName");

		assertThat(this.gemfireConfiguration.getPoolName()).isEqualTo("TestPoolName");

		this.gemfireConfiguration.setPoolName("  ");

		assertThat(this.gemfireConfiguration.getPoolName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_POOL_NAME);

		this.gemfireConfiguration.setPoolName("");

		assertThat(this.gemfireConfiguration.getPoolName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_POOL_NAME);

		this.gemfireConfiguration.setPoolName(null);

		assertThat(this.gemfireConfiguration.getPoolName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_POOL_NAME);
	}

	@Test
	public void setAndGetServerRegionShortcut() {

		assertThat(this.gemfireConfiguration.getServerRegionShortcut())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT);

		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE_PERSISTENT);

		assertThat(this.gemfireConfiguration.getServerRegionShortcut())
			.isEqualTo(RegionShortcut.REPLICATE_PERSISTENT);

		this.gemfireConfiguration.setServerRegionShortcut(null);

		assertThat(this.gemfireConfiguration.getServerRegionShortcut())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT);
	}

	@Test
	public void setAndGetSessionExpirationPolicyBeanName() {

		assertThat(this.gemfireConfiguration.getSessionExpirationPolicyBeanName().orElse(null)).isNull();

		this.gemfireConfiguration.setSessionExpirationPolicyBeanName("TestSessionExpirationPolicy");

		assertThat(this.gemfireConfiguration.getSessionExpirationPolicyBeanName().orElse(null))
			.isEqualTo("TestSessionExpirationPolicy");

		this.gemfireConfiguration.setSessionExpirationPolicyBeanName("  ");

		assertThat(this.gemfireConfiguration.getSessionExpirationPolicyBeanName().orElse(null)).isNull();

		this.gemfireConfiguration.setSessionExpirationPolicyBeanName("MockSessionExpirationPolicy");

		assertThat(this.gemfireConfiguration.getSessionExpirationPolicyBeanName().orElse(null))
			.isEqualTo("MockSessionExpirationPolicy");

		this.gemfireConfiguration.setSessionExpirationPolicyBeanName("");

		assertThat(this.gemfireConfiguration.getSessionExpirationPolicyBeanName().orElse(null)).isNull();

		this.gemfireConfiguration.setSessionExpirationPolicyBeanName("SessionExpirationPolicySpy");

		assertThat(this.gemfireConfiguration.getSessionExpirationPolicyBeanName().orElse(null))
			.isEqualTo("SessionExpirationPolicySpy");

		this.gemfireConfiguration.setSessionExpirationPolicyBeanName(null);

		assertThat(this.gemfireConfiguration.getSessionExpirationPolicyBeanName().orElse(null)).isNull();
	}

	@Test
	public void setAndGetSessionRegionName() {

		assertThat(this.gemfireConfiguration.getSessionRegionName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME);

		this.gemfireConfiguration.setSessionRegionName("test");

		assertThat(this.gemfireConfiguration.getSessionRegionName()).isEqualTo("test");

		this.gemfireConfiguration.setSessionRegionName("  ");

		assertThat(this.gemfireConfiguration.getSessionRegionName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME);

		this.gemfireConfiguration.setSessionRegionName("");

		assertThat(this.gemfireConfiguration.getSessionRegionName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME);

		this.gemfireConfiguration.setSessionRegionName(null);

		assertThat(this.gemfireConfiguration.getSessionRegionName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME);
	}

	@Test
	public void setAndGetSessionSerializerBeanName() {

		assertThat(this.gemfireConfiguration.getSessionSerializerBeanName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_SESSION_SERIALIZER_BEAN_NAME);

		this.gemfireConfiguration.setSessionSerializerBeanName(
			GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME);

		assertThat(this.gemfireConfiguration.getSessionSerializerBeanName())
			.isEqualTo(GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME);

		this.gemfireConfiguration.setSessionSerializerBeanName(null);

		assertThat(this.gemfireConfiguration.getSessionSerializerBeanName())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_SESSION_SERIALIZER_BEAN_NAME);

		this.gemfireConfiguration.setSessionSerializerBeanName(
			GemFireHttpSessionConfiguration.SESSION_PDX_SERIALIZER_BEAN_NAME);

		assertThat(this.gemfireConfiguration.getSessionSerializerBeanName())
			.isEqualTo(GemFireHttpSessionConfiguration.SESSION_PDX_SERIALIZER_BEAN_NAME);
	}

	@Test
	public void isUsingDataSerializationReturnsFalse() {

		this.gemfireConfiguration.setSessionSerializerBeanName("test");

		assertThat(this.gemfireConfiguration.getSessionSerializerBeanName()).isEqualTo("test");
		assertThat(this.gemfireConfiguration.isUsingDataSerialization()).isFalse();

		this.gemfireConfiguration.setSessionSerializerBeanName(
			GemFireHttpSessionConfiguration.SESSION_PDX_SERIALIZER_BEAN_NAME);

		assertThat(this.gemfireConfiguration.getSessionSerializerBeanName())
			.isEqualTo(GemFireHttpSessionConfiguration.SESSION_PDX_SERIALIZER_BEAN_NAME);

		assertThat(this.gemfireConfiguration.isUsingDataSerialization()).isFalse();
	}

	@Test
	public void isUsingPdxSerializationReturnsTrue() {

		assertThat(this.gemfireConfiguration.getSessionSerializerBeanName())
			.isEqualTo(GemFireHttpSessionConfiguration.SESSION_PDX_SERIALIZER_BEAN_NAME);

		assertThat(this.gemfireConfiguration.isUsingDataSerialization()).isFalse();

		this.gemfireConfiguration.setSessionSerializerBeanName(null);

		assertThat(this.gemfireConfiguration.getSessionSerializerBeanName())
			.isEqualTo(GemFireHttpSessionConfiguration.SESSION_PDX_SERIALIZER_BEAN_NAME);

		assertThat(this.gemfireConfiguration.isUsingDataSerialization()).isFalse();
	}

	@Test
	public void setsImportMetadata() {

		AnnotationMetadata mockAnnotationMetadata = mock(AnnotationMetadata.class);

		Map<String, Object> annotationAttributes = new HashMap<>(4);

		annotationAttributes.put("clientRegionShortcut", ClientRegionShortcut.CACHING_PROXY);
		annotationAttributes.put("exposeConfigurationAsProperties", Boolean.TRUE);
		annotationAttributes.put("indexableSessionAttributes", ArrayUtils.asArray("one", "two", "three"));
		annotationAttributes.put("maxInactiveIntervalInSeconds", 600);
		annotationAttributes.put("poolName", "TestPool");
		annotationAttributes.put("serverRegionShortcut", RegionShortcut.REPLICATE);
		annotationAttributes.put("regionName", "TEST");
		annotationAttributes.put("sessionExpirationPolicyBeanName", "testSessionExpirationPolicy");
		annotationAttributes.put("sessionSerializerBeanName", "testSessionSerializer");

		when(mockAnnotationMetadata.getAnnotationAttributes(eq(EnableGemFireHttpSession.class.getName())))
			.thenReturn(annotationAttributes);

		this.gemfireConfiguration.setImportMetadata(mockAnnotationMetadata);

		assertThat(this.gemfireConfiguration.getClientRegionShortcut()).isEqualTo(ClientRegionShortcut.CACHING_PROXY);
		assertThat(this.gemfireConfiguration.isExposeConfigurationAsProperties()).isTrue();
		assertThat(this.gemfireConfiguration.getIndexableSessionAttributes())
			.isEqualTo(ArrayUtils.asArray("one", "two", "three"));
		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(600);
		assertThat(this.gemfireConfiguration.getPoolName()).isEqualTo("TestPool");
		assertThat(this.gemfireConfiguration.getServerRegionShortcut()).isEqualTo(RegionShortcut.REPLICATE);
		assertThat(this.gemfireConfiguration.getSessionRegionName()).isEqualTo("TEST");
		assertThat(this.gemfireConfiguration.getSessionExpirationPolicyBeanName().orElse(null))
			.isEqualTo("testSessionExpirationPolicy");
		assertThat(this.gemfireConfiguration.getSessionSerializerBeanName()).isEqualTo("testSessionSerializer");

		verify(mockAnnotationMetadata, times(1))
			.getAnnotationAttributes(eq(EnableGemFireHttpSession.class.getName()));
	}

	@Test
	public void applyConfigurationFromSpringSessionGemFireConfigurer() {

		ApplicationContext mockApplicationContext = mock(ApplicationContext.class);

		SpringSessionGemFireConfigurer mockConfigurer = mock(SpringSessionGemFireConfigurer.class);

		when(mockApplicationContext.getBean(eq(SpringSessionGemFireConfigurer.class))).thenReturn(mockConfigurer);
		when(mockConfigurer.getClientRegionShortcut()).thenReturn(ClientRegionShortcut.CACHING_PROXY);
		when(mockConfigurer.getExposeConfigurationAsProperties()).thenReturn(true);
		when(mockConfigurer.getIndexableSessionAttributes()).thenReturn(new String[] { "one", "two" });
		when(mockConfigurer.getMaxInactiveIntervalInSeconds()).thenReturn(300);
		when(mockConfigurer.getPoolName()).thenReturn("DeadPool");
		when(mockConfigurer.getRegionName()).thenReturn("Sessions");
		when(mockConfigurer.getServerRegionShortcut()).thenReturn(RegionShortcut.PARTITION_REDUNDANT);
		when(mockConfigurer.getSessionExpirationPolicyBeanName()).thenReturn("TestSessionExpirationPolicy");
		when(mockConfigurer.getSessionSerializerBeanName()).thenReturn("TestSessionSerializer");

		this.gemfireConfiguration.setApplicationContext(mockApplicationContext);
		this.gemfireConfiguration.applySpringSessionGemFireConfigurer();

		assertThat(this.gemfireConfiguration.getClientRegionShortcut()).isEqualTo(ClientRegionShortcut.CACHING_PROXY);
		assertThat(this.gemfireConfiguration.isExposeConfigurationAsProperties()).isEqualTo(true);
		assertThat(this.gemfireConfiguration.getIndexableSessionAttributes()).containsExactly("one", "two");
		assertThat(this.gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(300);
		assertThat(this.gemfireConfiguration.getPoolName()).isEqualTo("DeadPool");
		assertThat(this.gemfireConfiguration.getServerRegionShortcut()).isEqualTo(RegionShortcut.PARTITION_REDUNDANT);
		assertThat(this.gemfireConfiguration.getSessionRegionName()).isEqualTo("Sessions");
		assertThat(this.gemfireConfiguration.getSessionExpirationPolicyBeanName().orElse(null))
			.isEqualTo("TestSessionExpirationPolicy");
		assertThat(this.gemfireConfiguration.getSessionSerializerBeanName()).isEqualTo("TestSessionSerializer");

		verify(mockConfigurer, times(1)).getClientRegionShortcut();
		verify(mockConfigurer, times(1)).getExposeConfigurationAsProperties();
		verify(mockConfigurer, times(1)).getIndexableSessionAttributes();
		verify(mockConfigurer, times(1)).getMaxInactiveIntervalInSeconds();
		verify(mockConfigurer, times(1)).getPoolName();
		verify(mockConfigurer, times(1)).getRegionName();
		verify(mockConfigurer, times(1)).getServerRegionShortcut();
		verify(mockConfigurer, times(1)).getSessionExpirationPolicyBeanName();
		verify(mockConfigurer, times(1)).getSessionSerializerBeanName();
	}

	@Test
	public void applyConfigurationFromNonExistingSpringSessionGemFireConfigurer() {

		this.gemfireConfiguration.applySpringSessionGemFireConfigurer();

		verify(this.gemfireConfiguration, never()).setClientRegionShortcut(any(ClientRegionShortcut.class));
		verify(this.gemfireConfiguration, never()).setExposeConfigurationAsProperties(anyBoolean());
		verify(this.gemfireConfiguration, never()).setIndexableSessionAttributes(any(String[].class));
		verify(this.gemfireConfiguration, never()).setMaxInactiveIntervalInSeconds(anyInt());
		verify(this.gemfireConfiguration, never()).setPoolName(anyString());
		verify(this.gemfireConfiguration, never()).setServerRegionShortcut(any(RegionShortcut.class));
		verify(this.gemfireConfiguration, never()).setSessionExpirationPolicyBeanName(anyString());
		verify(this.gemfireConfiguration, never()).setSessionRegionName(anyString());
		verify(this.gemfireConfiguration, never()).setSessionSerializerBeanName(anyString());
	}

	@Test(expected = NoUniqueBeanDefinitionException.class)
	public void applyConfigurationFromMultipleSpringSessionGemFireConfigurersThrowsException() {

		ApplicationContext mockApplicationContext = mock(ApplicationContext.class);

		when(mockApplicationContext.getBean(eq(SpringSessionGemFireConfigurer.class)))
			.thenThrow(new NoUniqueBeanDefinitionException(SpringSessionGemFireConfigurer.class, 2, "TEST"));

		this.gemfireConfiguration.setApplicationContext(mockApplicationContext);

		try {
			this.gemfireConfiguration.applySpringSessionGemFireConfigurer();
		}
		catch (NoUniqueBeanDefinitionException expected) {

			assertThat(expected).hasMessageContaining("TEST");
			assertThat(expected).hasNoCause();

			throw expected;
		}
		finally {
			verify(this.gemfireConfiguration, never()).setClientRegionShortcut(any(ClientRegionShortcut.class));
			verify(this.gemfireConfiguration, never()).setExposeConfigurationAsProperties(anyBoolean());
			verify(this.gemfireConfiguration, never()).setIndexableSessionAttributes(any(String[].class));
			verify(this.gemfireConfiguration, never()).setMaxInactiveIntervalInSeconds(anyInt());
			verify(this.gemfireConfiguration, never()).setPoolName(anyString());
			verify(this.gemfireConfiguration, never()).setServerRegionShortcut(any(RegionShortcut.class));
			verify(this.gemfireConfiguration, never()).setSessionExpirationPolicyBeanName(anyString());
			verify(this.gemfireConfiguration, never()).setSessionRegionName(anyString());
			verify(this.gemfireConfiguration, never()).setSessionSerializerBeanName(anyString());
		}
	}

	@Test
	@SuppressWarnings({ "rawtypes" })
	public void exposeSpringSessionGemFireConfigurationAsPropertiesMutatesSpringEnvironment() {

		ConfigurableEnvironment environment = new StandardEnvironment();

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.CACHING_PROXY);
		this.gemfireConfiguration.setEnvironment(environment);
		this.gemfireConfiguration.setExposeConfigurationAsProperties(true);
		this.gemfireConfiguration.setIndexableSessionAttributes(ArrayUtils.asArray("one", "two"));
		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(300);
		this.gemfireConfiguration.setPoolName("DeadPool");
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_REDUNDANT);
		this.gemfireConfiguration.setSessionExpirationPolicyBeanName("TestSessionExpirationPolicy");
		this.gemfireConfiguration.setSessionRegionName("Sessions");
		this.gemfireConfiguration.setSessionSerializerBeanName("TestSessionSerializer");
		this.gemfireConfiguration.setExposeConfigurationAsProperties(true);
		this.gemfireConfiguration.exposeSpringSessionGemFireConfiguration();

		PropertySource springSessionGemFirePropertySource = environment.getPropertySources()
			.get(GemFireHttpSessionConfiguration.SPRING_SESSION_GEMFIRE_PROPERTY_SOURCE);

		assertThat(springSessionGemFirePropertySource).isNotNull();

		assertThat(springSessionGemFirePropertySource.getProperty("spring.session.data.gemfire.cache.client.region.shortcut"))
			.isEqualTo(ClientRegionShortcut.CACHING_PROXY.name());

		assertThat(springSessionGemFirePropertySource.getProperty("spring.session.data.gemfire.session.configuration.expose"))
			.isEqualTo(Boolean.TRUE.toString());

		assertThat(springSessionGemFirePropertySource.getProperty("spring.session.data.gemfire.session.attributes.indexable"))
			.isEqualTo("one,two");

		assertThat(springSessionGemFirePropertySource.getProperty("spring.session.data.gemfire.session.attributes.indexed"))
			.isEqualTo("one,two");

		assertThat(springSessionGemFirePropertySource.getProperty("spring.session.data.gemfire.session.expiration.max-inactive-interval-seconds"))
			.isEqualTo("300");

		assertThat(springSessionGemFirePropertySource.getProperty("spring.session.data.gemfire.cache.client.pool.name"))
			.isEqualTo("DeadPool");

		assertThat(springSessionGemFirePropertySource.getProperty("spring.session.data.gemfire.cache.server.region.shortcut"))
			.isEqualTo(RegionShortcut.PARTITION_REDUNDANT.name());

		assertThat(springSessionGemFirePropertySource.getProperty("spring.session.data.gemfire.session.expiration.bean-name"))
			.isEqualTo("TestSessionExpirationPolicy");

		assertThat(springSessionGemFirePropertySource.getProperty("spring.session.data.gemfire.session.region.name"))
			.isEqualTo("Sessions");

		assertThat(springSessionGemFirePropertySource.getProperty("spring.session.data.gemfire.session.serializer.bean-name"))
			.isEqualTo("TestSessionSerializer");

		verify(this.gemfireConfiguration, times(1)).getClientRegionShortcut();
		verify(this.gemfireConfiguration, times(1)).getEnvironment();
		verify(this.gemfireConfiguration, times(2)).isExposeConfigurationAsProperties();
		verify(this.gemfireConfiguration, times(2)).getIndexableSessionAttributes();
		verify(this.gemfireConfiguration, times(1)).getMaxInactiveIntervalInSeconds();
		verify(this.gemfireConfiguration, times(1)).getPoolName();
		verify(this.gemfireConfiguration, times(1)).getSessionRegionName();
		verify(this.gemfireConfiguration, times(1)).getServerRegionShortcut();
		verify(this.gemfireConfiguration, times(1)).getSessionExpirationPolicyBeanName();
		verify(this.gemfireConfiguration, times(1)).getSessionSerializerBeanName();
	}

	@Test
	public void exposeSpringSessionGemFireConfigurationAsPropertiesIsNullSafe() {

		this.gemfireConfiguration.setEnvironment(null);
		this.gemfireConfiguration.setExposeConfigurationAsProperties(true);
		this.gemfireConfiguration.exposeSpringSessionGemFireConfiguration();

		verify(this.gemfireConfiguration, never()).getClientRegionShortcut();
		verify(this.gemfireConfiguration, times(1)).getEnvironment();
		verify(this.gemfireConfiguration, times(1)).isExposeConfigurationAsProperties();
		verify(this.gemfireConfiguration, never()).getIndexableSessionAttributes();
		verify(this.gemfireConfiguration, never()).getMaxInactiveIntervalInSeconds();
		verify(this.gemfireConfiguration, never()).getPoolName();
		verify(this.gemfireConfiguration, never()).getSessionRegionName();
		verify(this.gemfireConfiguration, never()).getServerRegionShortcut();
		verify(this.gemfireConfiguration, never()).getSessionExpirationPolicyBeanName();
		verify(this.gemfireConfiguration, never()).getSessionSerializerBeanName();
	}

	@Test
	public void exposeSpringSessionGemFireConfigurationAsPropertiesWhenExposureIsFalse() {

		ConfigurableEnvironment mockEnvironment = mock(ConfigurableEnvironment.class);

		this.gemfireConfiguration.setEnvironment(mockEnvironment);
		this.gemfireConfiguration.setExposeConfigurationAsProperties(false);
		this.gemfireConfiguration.exposeSpringSessionGemFireConfiguration();

		verify(this.gemfireConfiguration, never()).getClientRegionShortcut();
		verify(this.gemfireConfiguration, never()).getEnvironment();
		verify(this.gemfireConfiguration, times(1)).isExposeConfigurationAsProperties();
		verify(this.gemfireConfiguration, never()).getIndexableSessionAttributes();
		verify(this.gemfireConfiguration, never()).getMaxInactiveIntervalInSeconds();
		verify(this.gemfireConfiguration, never()).getPoolName();
		verify(this.gemfireConfiguration, never()).getSessionRegionName();
		verify(this.gemfireConfiguration, never()).getServerRegionShortcut();
		verify(this.gemfireConfiguration, never()).getSessionExpirationPolicyBeanName();
		verify(this.gemfireConfiguration, never()).getSessionSerializerBeanName();
		verifyNoInteractions(mockEnvironment);
	}

	@Test
	public void exposeSpringSessionGemFireConfigurationAsPropertiesWithNonConfigurableEnvironment() {

		Environment mockEnvironment = mock(Environment.class);

		this.gemfireConfiguration.setEnvironment(mockEnvironment);
		this.gemfireConfiguration.setExposeConfigurationAsProperties(true);
		this.gemfireConfiguration.exposeSpringSessionGemFireConfiguration();

		verify(this.gemfireConfiguration, never()).getClientRegionShortcut();
		verify(this.gemfireConfiguration, times(1)).getEnvironment();
		verify(this.gemfireConfiguration, times(1)).isExposeConfigurationAsProperties();
		verify(this.gemfireConfiguration, never()).getIndexableSessionAttributes();
		verify(this.gemfireConfiguration, never()).getMaxInactiveIntervalInSeconds();
		verify(this.gemfireConfiguration, never()).getPoolName();
		verify(this.gemfireConfiguration, never()).getSessionRegionName();
		verify(this.gemfireConfiguration, never()).getServerRegionShortcut();
		verify(this.gemfireConfiguration, never()).getSessionExpirationPolicyBeanName();
		verify(this.gemfireConfiguration, never()).getSessionSerializerBeanName();
		verifyNoInteractions(mockEnvironment);
	}

	@Test
	public void postConstructInitRegistersBeanAlias() {

		ConfigurableListableBeanFactory mockBeanFactory = mock(ConfigurableListableBeanFactory.class);

		ConfigurableApplicationContext mockApplicationContext = mock(ConfigurableApplicationContext.class);

		given(mockApplicationContext.getBeanFactory()).willReturn(mockBeanFactory);

		assertThat(System.getProperty(GemFireHttpSessionConfiguration.SESSION_SERIALIZER_BEAN_ALIAS)).isNull();

		this.gemfireConfiguration.setApplicationContext(mockApplicationContext);
		this.gemfireConfiguration.setSessionSerializerBeanName("testSessionSerializer");
		this.gemfireConfiguration.initGemFire();
		this.gemfireConfiguration.init();

		assertThat(this.gemfireConfiguration.getApplicationContext()).isSameAs(mockApplicationContext);

		verify(mockApplicationContext, times(1)).getBeanFactory();

		verify(mockBeanFactory, times(1))
			.registerAlias(eq("testSessionSerializer"), eq(GemFireHttpSessionConfiguration.SESSION_SERIALIZER_BEAN_ALIAS));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createsAndInitializesSessionRepositoryBean() {

		Region<Object, Session> mockRegion = mock(Region.class);

		GemfireTemplate mockGemfireOperations = mock(GemfireTemplate.class);

		doReturn(mockRegion).when(mockGemfireOperations).getRegion();

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(120);
		this.gemfireConfiguration.setIsDirtyPredicate(EqualsDirtyPredicate.INSTANCE);

		GemFireOperationsSessionRepository sessionRepository =
			this.gemfireConfiguration.sessionRepository(mockGemfireOperations);

		assertThat(sessionRepository).isNotNull();
		assertThat(sessionRepository.getIsDirtyPredicate()).isEqualTo(EqualsDirtyPredicate.INSTANCE);
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(120);
		assertThat(sessionRepository.getSessionsTemplate()).isSameAs(mockGemfireOperations);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createsAndInitializesSessionRegionTemplateBean() {

		GemFireCache mockGemFireCache = mock(GemFireCache.class);

		Region<Object, Object> mockRegion = mock(Region.class);

		given(mockGemFireCache.getRegion(eq("Example"))).willReturn(mockRegion);

		this.gemfireConfiguration.setSessionRegionName("Example");

		GemfireTemplate template = this.gemfireConfiguration.sessionRegionTemplate(mockGemFireCache);

		assertThat(this.gemfireConfiguration.getSessionRegionName()).isEqualTo("Example");
		assertThat(template).isNotNull();
		assertThat(template.getRegion()).isSameAs(mockRegion);

		verify(mockGemFireCache, times(1)).getRegion(eq("Example"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createsAndInitializesSessionRegionBean() {

		GemFireCache mockGemFireCache = mock(GemFireCache.class);

		RegionAttributes<Object, Session> mockRegionAttributes = mock(RegionAttributes.class);

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.CACHING_PROXY);
		this.gemfireConfiguration.setPoolName("TestPool");
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_REDUNDANT);
		this.gemfireConfiguration.setSessionRegionName("TestRegion");

		SessionCacheTypeAwareRegionFactoryBean<Object, Session> sessionRegionFactoryBean =
			this.gemfireConfiguration.sessionRegion(mockGemFireCache, mockRegionAttributes);

		assertThat(sessionRegionFactoryBean).isNotNull();
		assertThat(sessionRegionFactoryBean.getClientRegionShortcut()).isEqualTo(ClientRegionShortcut.CACHING_PROXY);
		assertThat(sessionRegionFactoryBean.getCache()).isEqualTo(mockGemFireCache);
		assertThat(GemFireHttpSessionConfigurationUnitTests.<String>getField(sessionRegionFactoryBean, "poolName")).isEqualTo("TestPool");
		assertThat(GemFireHttpSessionConfigurationUnitTests.<RegionAttributes<Object, Session>>getField(sessionRegionFactoryBean, "regionAttributes")).isEqualTo(mockRegionAttributes);
		assertThat(GemFireHttpSessionConfigurationUnitTests.<String>getField(sessionRegionFactoryBean, "regionName")).isEqualTo("TestRegion");
		assertThat(sessionRegionFactoryBean.getServerRegionShortcut()).isEqualTo(RegionShortcut.PARTITION_REDUNDANT);

		verifyNoInteractions(mockGemFireCache);
		verifyNoInteractions(mockRegionAttributes);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void createsAndInitializesSessionRegionAttributesWithExpiration() throws Exception {

		Cache mockCache = mock(Cache.class);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(300);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.LOCAL);

		RegionAttributesFactoryBean regionAttributesFactory =
			this.gemfireConfiguration.sessionRegionAttributes(mockCache);

		assertThat(regionAttributesFactory).isNotNull();

		regionAttributesFactory.afterPropertiesSet();

		RegionAttributes<Object, Session> sessionRegionAttributes = regionAttributesFactory.getObject();

		assertThat(sessionRegionAttributes).isNotNull();
		assertThat(sessionRegionAttributes.getKeyConstraint())
			.isEqualTo(GemFireHttpSessionConfiguration.SESSION_REGION_KEY_CONSTRAINT);
		assertThat(sessionRegionAttributes.getValueConstraint())
			.isEqualTo(GemFireHttpSessionConfiguration.SESSION_REGION_VALUE_CONSTRAINT);

		ExpirationAttributes entryIdleTimeoutExpiration = sessionRegionAttributes.getEntryIdleTimeout();

		assertThat(entryIdleTimeoutExpiration).isNotNull();
		assertThat(entryIdleTimeoutExpiration.getAction()).isEqualTo(ExpirationAction.INVALIDATE);
		assertThat(entryIdleTimeoutExpiration.getTimeout()).isEqualTo(300);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void createsAndInitializesSessionRegionAttributesWithoutExpiration() throws Exception {

		ClientCache mockClientCache = mock(ClientCache.class);

		this.gemfireConfiguration.setMaxInactiveIntervalInSeconds(300);

		RegionAttributesFactoryBean regionAttributesFactory =
			this.gemfireConfiguration.sessionRegionAttributes(mockClientCache);

		assertThat(regionAttributesFactory).isNotNull();

		regionAttributesFactory.afterPropertiesSet();

		RegionAttributes<Object, Session> sessionRegionAttributes = regionAttributesFactory.getObject();

		assertThat(sessionRegionAttributes).isNotNull();
		assertThat(sessionRegionAttributes.getKeyConstraint())
			.isEqualTo(GemFireHttpSessionConfiguration.SESSION_REGION_KEY_CONSTRAINT);
		assertThat(sessionRegionAttributes.getValueConstraint())
			.isEqualTo(GemFireHttpSessionConfiguration.SESSION_REGION_VALUE_CONSTRAINT);

		ExpirationAttributes entryIdleTimeoutExpiration = sessionRegionAttributes.getEntryIdleTimeout();

		assertThat(entryIdleTimeoutExpiration).isNotNull();
		assertThat(entryIdleTimeoutExpiration.getAction()).isEqualTo(ExpirationAction.INVALIDATE);
		assertThat(entryIdleTimeoutExpiration.getTimeout()).isEqualTo(0);
	}

	@Test
	public void clientExpirationIsAllowed() {

		ClientCache mockClientCache = mock(ClientCache.class);

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.CACHING_PROXY);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_PROXY);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockClientCache)).isTrue();

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockClientCache)).isTrue();
	}

	@Test
	public void serverExpirationIsAllowed() {

		Cache mockCache = mock(Cache.class);

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.PROXY);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockCache)).isTrue();

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.LOCAL);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT_OVERFLOW);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockCache)).isTrue();
	}

	@Test
	public void clientExpirationIsNotAllowed() {

		ClientCache mockClientCache = mock(ClientCache.class);

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.PROXY);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_PROXY);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockClientCache)).isFalse();

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.PROXY);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockClientCache)).isFalse();
	}

	@Test
	public void serverExpirationIsNotAllowed() {

		Cache mockCache = mock(Cache.class);

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.PROXY);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_PROXY);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockCache)).isFalse();

		this.gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.LOCAL);
		this.gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE_PROXY);

		assertThat(this.gemfireConfiguration.isExpirationAllowed(mockCache)).isFalse();
	}
}
