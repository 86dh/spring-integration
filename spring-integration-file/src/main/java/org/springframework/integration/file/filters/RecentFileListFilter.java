/*
 * Copyright 2025 the original author or authors.
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

package org.springframework.integration.file.filters;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

/**
 * The {@link AbstractRecentFileListFilter} implementation for local file system.
 *
 * @author Artem Bilan
 *
 * @since 6.5
 */
public class RecentFileListFilter extends AbstractRecentFileListFilter<File> {

	public RecentFileListFilter() {
	}

	public RecentFileListFilter(Duration age) {
		super(age);
	}

	@Override
	protected Instant getLastModified(File file) {
		return Instant.ofEpochSecond(file.lastModified() / ONE_SECOND);
	}

}
