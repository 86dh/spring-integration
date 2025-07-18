/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.integration.jpa.test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.integration.config.SourcePollingChannelAdapterFactoryBean;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.jpa.test.entity.Gender;
import org.springframework.integration.jpa.test.entity.StudentDomain;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.messaging.MessageChannel;

/**
 *
 * @author Gunnar Hillert
 * @author Gary Russell
 * @author Artem Bilan
 * @since 2.2
 *
 */
public final class JpaTestUtils {

	private JpaTestUtils() {
		super();
	}

	public static StudentDomain getTestStudent() {

		return new StudentDomain()
				.withFirstName("First Executor")
				.withLastName("Last Executor")
				.withGender(Gender.MALE)
				.withDateOfBirth(LocalDate.of(1984, 1, 31))
				.withLastUpdated(LocalDateTime.now());
	}

	public static SourcePollingChannelAdapter getSourcePollingChannelAdapter(MessageSource<?> adapter,
			MessageChannel channel,
			PollerMetadata poller,
			GenericApplicationContext context,
			ClassLoader beanClassLoader) {

		SourcePollingChannelAdapterFactoryBean fb = new SourcePollingChannelAdapterFactoryBean();
		fb.setSource(adapter);
		fb.setOutputChannel(channel);
		fb.setPollerMetadata(poller);
		fb.setBeanClassLoader(beanClassLoader);
		fb.setAutoStartup(false);
		fb.setBeanFactory(context.getBeanFactory());
		fb.afterPropertiesSet();

		return fb.getObject();
	}

}
