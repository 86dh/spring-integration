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

package org.springframework.integration.ws;

import java.io.IOException;

import org.jspecify.annotations.Nullable;

import org.springframework.messaging.Message;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;
import org.springframework.util.Assert;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.destination.DestinationProvider;

/**
 * An outbound Messaging Gateway for invoking Web Services that also supports
 * marshalling and unmarshalling of the request and response messages.
 *
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Artem Bilan
 *
 * @see Marshaller
 * @see Unmarshaller
 */
public class MarshallingWebServiceOutboundGateway extends AbstractWebServiceOutboundGateway {

	@SuppressWarnings("this-escape")
	public MarshallingWebServiceOutboundGateway(DestinationProvider destinationProvider, Marshaller marshaller,
			@Nullable Unmarshaller unmarshaller, @Nullable WebServiceMessageFactory messageFactory) {
		super(destinationProvider, messageFactory);
		configureMarshallers(marshaller, unmarshaller);
	}

	public MarshallingWebServiceOutboundGateway(DestinationProvider destinationProvider, Marshaller marshaller,
			Unmarshaller unmarshaller) {
		this(destinationProvider, marshaller, unmarshaller, null);
	}

	public MarshallingWebServiceOutboundGateway(DestinationProvider destinationProvider, Marshaller marshaller,
			@Nullable WebServiceMessageFactory messageFactory) {
		this(destinationProvider, marshaller, null, messageFactory);
	}

	public MarshallingWebServiceOutboundGateway(DestinationProvider destinationProvider, Marshaller marshaller) {
		this(destinationProvider, marshaller, (WebServiceMessageFactory) null);
	}

	@SuppressWarnings("this-escape")
	public MarshallingWebServiceOutboundGateway(@Nullable String uri, Marshaller marshaller, @Nullable Unmarshaller unmarshaller,
			@Nullable WebServiceMessageFactory messageFactory) {
		super(uri, messageFactory);
		configureMarshallers(marshaller, unmarshaller);
	}

	public MarshallingWebServiceOutboundGateway(String uri, Marshaller marshaller, Unmarshaller unmarshaller) {
		this(uri, marshaller, unmarshaller, null);
	}

	public MarshallingWebServiceOutboundGateway(String uri, Marshaller marshaller,
			@Nullable WebServiceMessageFactory messageFactory) {
		this(uri, marshaller, null, messageFactory);
	}

	public MarshallingWebServiceOutboundGateway(String uri, Marshaller marshaller) {
		this(uri, marshaller, (WebServiceMessageFactory) null);
	}

	/**
	 * Construct an instance based on the provided Web Service URI and {@code WebServiceTemplate}.
	 * @param uri the Web Service URI to use
	 * @param webServiceTemplate the WebServiceTemplate
	 * @since 5.0
	 */
	@SuppressWarnings("this-escape")
	public MarshallingWebServiceOutboundGateway(@Nullable String uri, WebServiceTemplate webServiceTemplate) {
		super(uri, null);
		doSetWebServiceTemplate(webServiceTemplate);
	}

	/**
	 * Construct an instance based on the provided {@code DestinationProvider} and {@code WebServiceTemplate}.
	 * @param destinationProvider the {@link DestinationProvider} to resolve Web Service URI at runtime
	 * @param webServiceTemplate the WebServiceTemplate
	 * @since 5.0
	 */
	@SuppressWarnings("this-escape")
	public MarshallingWebServiceOutboundGateway(DestinationProvider destinationProvider,
			WebServiceTemplate webServiceTemplate) {
		super(destinationProvider, null);
		doSetWebServiceTemplate(webServiceTemplate);
	}

	/**
	 * Sets the provided Marshaller and Unmarshaller on this gateway's WebServiceTemplate.
	 * Neither may be null.
	 * @param marshaller The marshaller.
	 * @param unmarshallerArg The unmarshaller.
	 */
	@SuppressWarnings("this-escape")
	private void configureMarshallers(Marshaller marshaller, @Nullable Unmarshaller unmarshallerArg) {
		Unmarshaller unmarshaller = unmarshallerArg;
		Assert.notNull(marshaller, "marshaller must not be null");
		if (unmarshaller == null) {
			Assert.isInstanceOf(Unmarshaller.class, marshaller,
					"Marshaller [" + marshaller + "] does not implement the Unmarshaller interface. " +
							"Please set an Unmarshaller explicitly by using one of the constructors that accepts " +
							"both Marshaller and Unmarshaller arguments.");
			unmarshaller = (Unmarshaller) marshaller;
		}

		Assert.notNull(unmarshaller, "unmarshaller must not be null");

		getWebServiceTemplate().setMarshaller(marshaller);
		getWebServiceTemplate().setUnmarshaller(unmarshaller);
	}

	@Override
	public String getComponentType() {
		return "ws:outbound-gateway(marshalling)";
	}

	@Override
	protected @Nullable Object doHandle(String uri, Message<?> requestMessage, @Nullable WebServiceMessageCallback requestCallback) {
		return getWebServiceTemplate()
				.marshalSendAndReceive(uri, requestMessage.getPayload(),
						new PassThroughRequestMessageCallback(requestCallback, requestMessage));
	}

	private final class PassThroughRequestMessageCallback extends RequestMessageCallback {

		PassThroughRequestMessageCallback(@Nullable WebServiceMessageCallback requestCallback, Message<?> requestMessage) {
			super(requestCallback, requestMessage);
		}

		@Override
		public void doWithMessageInternal(WebServiceMessage message, Object payload) throws IOException {
		}

	}

}
