/*
 * Copyright 2015-present the original author or authors.
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
package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;

import edu.umd.cs.mtc.TestFramework;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.lang.NonNull;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Integration Tests testing the concurrent access of a {@link Session} stored in an Apache Geode
 * {@link ClientCache client} {@link ClientRegionShortcut#LOCAL} {@link Region}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see edu.umd.cs.mtc.TestFramework
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheApplication
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractConcurrentSessionOperationsIntegrationTests
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 2.1.x
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
public class ConcurrentSessionOperationsUsingClientLocalRegionIntegrationTests
		extends AbstractConcurrentSessionOperationsIntegrationTests {

	@Test
	public void concurrentLocalSessionAccessIsCorrect() throws Throwable {
		TestFramework.runOnce(new ConcurrentLocalSessionAccessTestCase(this));
	}

	@SuppressWarnings("unused")
	public static class ConcurrentLocalSessionAccessTestCase extends AbstractConcurrentSessionOperationsTestCase {

		public ConcurrentLocalSessionAccessTestCase(
				@NonNull ConcurrentSessionOperationsUsingClientLocalRegionIntegrationTests testInstance) {

			super(testInstance);
		}

		public void thread1() {

			Thread.currentThread().setName("User Session One");

			assertTick(0);

			Session session = newSession();

			assertThat(session).isNotNull();
			assertThat(session.getId()).isNotEmpty();
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			save(session);

			setSessionId(session.getId());

			waitForTick(2);
			assertTick(2);

			// modify the Session without saving
			session.setAttribute("attributeOne", "one");
			session.setAttribute("attributeTwo", "two");
		}

		public void thread2() {

			Thread.currentThread().setName("User Session Two");

			waitForTick(1);
			assertTick(1);
			waitOnAvailableSessionId();

			Session session = findById(getSessionId());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(getSessionId());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			waitForTick(3);
			assertTick(3);

			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("one");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("two");
		}
	}

	@ClientCacheApplication
	@EnableGemFireHttpSession(
		clientRegionShortcut = ClientRegionShortcut.LOCAL,
		poolName = "DEFAULT",
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	@SuppressWarnings("unused")
	static class TestConfiguration { }

}
