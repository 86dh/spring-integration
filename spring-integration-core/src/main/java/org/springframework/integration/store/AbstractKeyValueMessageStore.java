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

package org.springframework.integration.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

/**
 * Base class for implementations of Key/Value style {@link MessageGroupStore} and {@link MessageStore}.
 *
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Artem Bilan
 * @author Ngoc Nhan
 * @author Youbin Wu
 *
 * @since 2.1
 */
public abstract class AbstractKeyValueMessageStore extends AbstractMessageGroupStore implements MessageStore {

	protected static final String MESSAGE_KEY_PREFIX = "MESSAGE_";

	protected static final String MESSAGE_GROUP_KEY_PREFIX = "GROUP_OF_MESSAGES_";

	private final String messagePrefix;

	private final String groupPrefix;

	protected AbstractKeyValueMessageStore() {
		this("");
	}

	/**
	 * Construct an instance based on the provided prefix for keys to distinguish between
	 * different store instances in the same target key-value database. Defaults to an
	 * empty string - no prefix. The actual prefix for messages is
	 * {@code prefix + MESSAGE_}; for message groups - {@code prefix + GROUP_OF_MESSAGES_}
	 * @param prefix the prefix to use
	 * @since 4.3.12
	 */
	protected AbstractKeyValueMessageStore(String prefix) {
		Assert.notNull(prefix, "'prefix' must not be null");
		this.messagePrefix = prefix + MESSAGE_KEY_PREFIX;
		this.groupPrefix = prefix + MESSAGE_GROUP_KEY_PREFIX;
	}

	/**
	 * Return the configured prefix for message keys to distinguish between different
	 * store instances in the same target key-value database. Defaults to the
	 * {@value MESSAGE_KEY_PREFIX} - without a custom prefix.
	 * @return the prefix for keys
	 * @since 4.3.12
	 */
	public String getMessagePrefix() {
		return this.messagePrefix;
	}

	/**
	 * Return the configured prefix for message group keys to distinguish between
	 * different store instances in the same target key-value database. Defaults to the
	 * {@value MESSAGE_GROUP_KEY_PREFIX} - without custom prefix.
	 * @return the prefix for keys
	 * @since 4.3.12
	 */
	public String getGroupPrefix() {
		return this.groupPrefix;
	}

	// MessageStore methods

	@Override
	public Message<?> getMessage(UUID messageId) {
		Assert.notNull(messageId, "'messageId' must not be null");
		Object object = doRetrieve(this.messagePrefix + messageId);
		if (object != null) {
			return extractMessage(object);
		}
		else {
			return null;
		}
	}

	private Message<?> extractMessage(Object object) {
		if (object instanceof MessageHolder messageHolder) {
			return messageHolder.getMessage();
		}
		else if (object instanceof Message<?> message) {
			return message;
		}
		else {
			throw new IllegalArgumentException(
					"Object of class [" + object.getClass().getName() +
							"] must be an instance of [org.springframework.integration.store.MessageHolder].");
		}
	}

	@Override
	public MessageMetadata getMessageMetadata(UUID messageId) {
		Assert.notNull(messageId, "'messageId' must not be null");
		Object object = doRetrieve(this.messagePrefix + messageId);
		if (object != null) {
			extractMessage(object);
			if (object instanceof MessageHolder messageHolder) {
				return messageHolder.getMessageMetadata();
			}
		}
		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Message<T> addMessage(Message<T> message) {
		doAddMessage(message);
		return (Message<T>) getMessage(message.getHeaders().getId());
	}

	protected void doAddMessage(Message<?> message) {
		doAddMessage(message, null);
	}

	protected void doAddMessage(Message<?> message, @Nullable Object groupId) {
		Assert.notNull(message, "'message' must not be null");
		UUID messageId = message.getHeaders().getId();
		Assert.notNull(messageId, "Cannot store messages without an ID header");
		String messageKey = this.messagePrefix + (groupId != null ? groupId.toString() + '_' : "") + messageId;
		doStoreIfAbsent(messageKey, new MessageHolder(message));
	}

	@Override
	public Message<?> removeMessage(UUID id) {
		Assert.notNull(id, "'id' must not be null");
		Object object = doRemove(this.messagePrefix + id);
		if (object != null) {
			return extractMessage(object);
		}
		else {
			return null;
		}
	}

	@Override
	@ManagedAttribute
	public long getMessageCount() {
		Collection<?> messageIds = doListKeys(this.messagePrefix + '*');
		return (messageIds != null) ? messageIds.size() : 0;
	}

	// MessageGroupStore methods

	/**
	 * Will create a new instance of SimpleMessageGroup if necessary.
	 */
	@Override
	public MessageGroup getMessageGroup(Object groupId) {
		MessageGroupMetadata metadata = getGroupMetadata(groupId);
		if (metadata != null) {

			MessageGroup messageGroup = getMessageGroupFactory()
					.create(this, groupId, metadata.getTimestamp(), metadata.isComplete());
			messageGroup.setLastModified(metadata.getLastModified());
			messageGroup.setLastReleasedMessageSequenceNumber(metadata.getLastReleasedMessageSequenceNumber());
			messageGroup.setCondition(metadata.getCondition());
			return messageGroup;
		}
		else {
			return new SimpleMessageGroup(groupId);
		}
	}

	@Override
	public MessageGroupMetadata getGroupMetadata(Object groupId) {
		Assert.notNull(groupId, GROUP_ID_MUST_NOT_BE_NULL);
		Object mgm = this.doRetrieve(this.groupPrefix + groupId);
		if (mgm != null) {
			Assert.isInstanceOf(MessageGroupMetadata.class, mgm);
			return (MessageGroupMetadata) mgm;
		}
		return null;
	}

	@Override
	protected void doAddMessagesToGroup(Object groupId, Message<?>... messages) {
		Assert.notNull(groupId, GROUP_ID_MUST_NOT_BE_NULL);
		Assert.notNull(messages, "'messages' must not be null");

		MessageGroupMetadata metadata = getGroupMetadata(groupId);
		SimpleMessageGroup group = null;
		if (metadata == null) {
			group = new SimpleMessageGroup(groupId);
		}

		for (Message<?> message : messages) {
			doAddMessage(message, groupId);
			if (metadata != null) {
				metadata.add(message.getHeaders().getId());
			}
			else {
				group.add(message);
			}
		}

		if (group != null) {
			metadata = new MessageGroupMetadata(group);
			// When the group is new reuse "create time" as a "last modified"
			metadata.setLastModified(group.getTimestamp());
		}
		else {
			metadata.setLastModified(System.currentTimeMillis());
		}

		// store MessageGroupMetadata built from enriched MG
		doStore(this.groupPrefix + groupId, metadata);
	}

	@Override
	protected void doRemoveMessagesFromGroup(Object groupId, Collection<Message<?>> messages) {
		Assert.notNull(groupId, GROUP_ID_MUST_NOT_BE_NULL);
		Assert.notNull(messages, "'messages' must not be null");

		Object mgm = doRetrieve(this.groupPrefix + groupId);
		if (mgm != null) {
			Assert.isInstanceOf(MessageGroupMetadata.class, mgm);
			MessageGroupMetadata messageGroupMetadata = (MessageGroupMetadata) mgm;

			List<UUID> ids = new ArrayList<>();
			for (Message<?> messageToRemove : messages) {
				ids.add(messageToRemove.getHeaders().getId());
			}

			messageGroupMetadata.removeAll(ids);

			List<Object> messageIds = new ArrayList<>();
			for (UUID id : ids) {
				messageIds.add(this.messagePrefix + groupId + '_' + id);
			}

			doRemoveAll(messageIds);

			messageGroupMetadata.setLastModified(System.currentTimeMillis());
			doStore(this.groupPrefix + groupId, messageGroupMetadata);
		}
	}

	@Override
	@Nullable
	public Message<?> getMessageFromGroup(Object groupId, UUID messageId) {
		Assert.notNull(groupId, GROUP_ID_MUST_NOT_BE_NULL);
		Assert.notNull(messageId, "'messageId' must not be null");
		Object object = doRetrieve(this.messagePrefix + groupId + '_' + messageId);
		if (object != null) {
			return extractMessage(object);
		}
		else {
			return null;
		}
	}

	@Override
	protected boolean doRemoveMessageFromGroupById(Object groupId, UUID messageId) {
		Assert.notNull(groupId, GROUP_ID_MUST_NOT_BE_NULL);
		Assert.notNull(messageId, "'messageId' must not be null");
		Object mgm = doRetrieve(this.groupPrefix + groupId);
		if (mgm != null) {
			Assert.isInstanceOf(MessageGroupMetadata.class, mgm);
			MessageGroupMetadata messageGroupMetadata = (MessageGroupMetadata) mgm;

			if (messageGroupMetadata.getMessageIds().contains(messageId)) {
				messageGroupMetadata.remove(messageId);
				String groupToMessageId = this.messagePrefix + groupId + '_' + messageId;
				if (doRemove(groupToMessageId) != null) {
					messageGroupMetadata.setLastModified(System.currentTimeMillis());
					doStore(this.groupPrefix + groupId, messageGroupMetadata);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected void doCompleteGroup(Object groupId) {
		Assert.notNull(groupId, GROUP_ID_MUST_NOT_BE_NULL);
		MessageGroupMetadata metadata = getGroupMetadata(groupId);
		if (metadata != null) {
			metadata.complete();
			metadata.setLastModified(System.currentTimeMillis());
			doStore(this.groupPrefix + groupId, metadata);
		}
	}

	/**
	 * Remove the MessageGroup with the provided group ID.
	 */
	@Override
	protected void doRemoveMessageGroup(Object groupId) {
		Assert.notNull(groupId, GROUP_ID_MUST_NOT_BE_NULL);
		Object mgm = doRemove(this.groupPrefix + groupId);
		if (mgm != null) {
			Assert.isInstanceOf(MessageGroupMetadata.class, mgm);
			MessageGroupMetadata messageGroupMetadata = (MessageGroupMetadata) mgm;

			List<Object> messageIds =
					messageGroupMetadata.getMessageIds()
							.stream()
							.map(id -> this.messagePrefix + groupId + '_' + id)
							.collect(Collectors.toList());

			doRemoveAll(messageIds);
		}
	}

	@Override
	protected void doSetGroupCondition(Object groupId, String condition) {
		MessageGroupMetadata metadata = getGroupMetadata(groupId);
		if (metadata != null) {
			metadata.setCondition(condition);
			doStore(this.groupPrefix + groupId, metadata);
		}
	}

	@Override
	protected void doSetLastReleasedSequenceNumberForGroup(Object groupId, int sequenceNumber) {
		Assert.notNull(groupId, GROUP_ID_MUST_NOT_BE_NULL);
		MessageGroupMetadata metadata = getGroupMetadata(groupId);
		if (metadata == null) {
			SimpleMessageGroup messageGroup = new SimpleMessageGroup(groupId);
			metadata = new MessageGroupMetadata(messageGroup);
		}
		metadata.setLastReleasedMessageSequenceNumber(sequenceNumber);
		metadata.setLastModified(System.currentTimeMillis());
		doStore(this.groupPrefix + groupId, metadata);
	}

	@Override
	protected Message<?> doPollMessageFromGroup(Object groupId) {
		MessageGroupMetadata groupMetadata = getGroupMetadata(groupId);
		if (groupMetadata != null) {
			UUID firstId = groupMetadata.firstId();
			if (firstId != null) {
				groupMetadata.remove(firstId);
				groupMetadata.setLastModified(System.currentTimeMillis());
				doStore(this.groupPrefix + groupId, groupMetadata);
				return removeMessageFromGroup(firstId, groupId);
			}
		}
		return null;
	}

	private Message<?> removeMessageFromGroup(UUID id, Object groupId) {
		Assert.notNull(id, "'id' must not be null");
		Object object = doRemove(this.messagePrefix + groupId + '_' + id);
		if (object != null) {
			return extractMessage(object);
		}
		else {
			return null;
		}
	}

	@Override
	public Message<?> getOneMessageFromGroup(Object groupId) {
		MessageGroupMetadata groupMetadata = getGroupMetadata(groupId);
		if (groupMetadata != null) {
			UUID messageId = groupMetadata.firstId();
			if (messageId != null) {
				return getMessageFromGroup(messageId, groupId);
			}
		}
		return null;
	}

	@Nullable
	private Message<?> getMessageFromGroup(UUID messageId, Object groupId) {
		Assert.notNull(messageId, "'messageId' must not be null");
		Object object = doRetrieve(this.messagePrefix + groupId + '_' + messageId);
		if (object != null) {
			return extractMessage(object);
		}
		else {
			return null;
		}
	}

	@Override
	public Collection<Message<?>> getMessagesForGroup(Object groupId) {
		MessageGroupMetadata groupMetadata = getGroupMetadata(groupId);
		ArrayList<Message<?>> messages = new ArrayList<>();
		if (groupMetadata != null) {
			Iterator<UUID> messageIds = groupMetadata.messageIdIterator();
			while (messageIds.hasNext()) {
				messages.add(getMessageFromGroup(messageIds.next(), groupId));
			}
		}
		return messages;
	}

	@Override
	public Stream<Message<?>> streamMessagesForGroup(Object groupId) {
		return getGroupMetadata(groupId)
				.getMessageIds()
				.stream()
				.map((messageId) -> getMessageFromGroup(messageId, groupId));
	}

	@Override
	@SuppressWarnings("unchecked")
	public Iterator<MessageGroup> iterator() {
		final Iterator<?> idIterator = normalizeKeys(
				(Collection<String>) doListKeys(this.groupPrefix + '*'))
				.iterator();
		return new MessageGroupIterator(idIterator);
	}

	private Collection<String> normalizeKeys(Collection<String> keys) {
		Set<String> normalizedKeys = new HashSet<>();
		for (String key : keys) {
			String strKey = key;
			if (strKey.startsWith(this.groupPrefix)) {
				strKey = strKey.replace(this.groupPrefix, "");
			}
			else if (strKey.startsWith(this.messagePrefix)) {
				strKey = strKey.replace(this.messagePrefix, "");
			}
			normalizedKeys.add(strKey);
		}
		return normalizedKeys;
	}

	@Override
	public int messageGroupSize(Object groupId) {
		MessageGroupMetadata mgm = getGroupMetadata(groupId);
		if (mgm != null) {
			return mgm.size();
		}
		else {
			return 0;
		}
	}

	protected abstract Object doRetrieve(Object id);

	protected abstract void doStore(Object id, Object objectToStore);

	protected abstract void doStoreIfAbsent(Object id, Object objectToStore);

	protected abstract Object doRemove(Object id);

	protected abstract void doRemoveAll(Collection<Object> ids);

	protected abstract Collection<?> doListKeys(String keyPattern);

	private final class MessageGroupIterator implements Iterator<MessageGroup> {

		private final Iterator<?> idIterator;

		MessageGroupIterator(Iterator<?> idIterator) {
			this.idIterator = idIterator;
		}

		@Override
		public boolean hasNext() {
			return this.idIterator.hasNext();
		}

		@Override
		public MessageGroup next() {
			Object messageGroupId = this.idIterator.next();
			return getMessageGroup(messageGroupId);
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

}
