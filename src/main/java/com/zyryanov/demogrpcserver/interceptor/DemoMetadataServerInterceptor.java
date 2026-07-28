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

package com.zyryanov.demogrpcserver.interceptor;

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

/**
 * Добавить демонстрационные response headers и trailers ко всем RPC.
 */
@Component
@GlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DemoMetadataServerInterceptor implements ServerInterceptor {

	private static final Metadata.Key<String> DEMO_SERVER_HEADER =
			Metadata.Key.of("x-demo-server", Metadata.ASCII_STRING_MARSHALLER);

	private static final Metadata.Key<String> DEMO_TRAILER =
			Metadata.Key.of("x-demo-trailer", Metadata.ASCII_STRING_MARSHALLER);

	private static final Metadata.Key<String> REQUEST_ID =
			Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

	private static final Metadata.Key<byte[]> BINARY_CONTEXT =
			Metadata.Key.of("x-demo-bin", Metadata.BINARY_BYTE_MARSHALLER);

	@Override
	public <RequestT, ResponseT> ServerCall.Listener<RequestT> interceptCall(
			ServerCall<RequestT, ResponseT> call, Metadata requestHeaders,
			ServerCallHandler<RequestT, ResponseT> next) {

		String requestId = requestHeaders.get(REQUEST_ID);
		byte[] binaryContext = requestHeaders.get(BINARY_CONTEXT);
		ServerCall<RequestT, ResponseT> metadataCall =
				new SimpleForwardingServerCall<>(call) {
					@Override
					public void sendHeaders(Metadata responseHeaders) {
						responseHeaders.put(DEMO_SERVER_HEADER, "zyryal-grpc-demo");
						if (requestId != null) {
							responseHeaders.put(REQUEST_ID, requestId);
						}
						if (binaryContext != null) {
							responseHeaders.put(BINARY_CONTEXT, binaryContext);
						}
						super.sendHeaders(responseHeaders);
					}

					@Override
					public void close(Status status, Metadata trailers) {
						trailers.put(DEMO_TRAILER, status.getCode().name());
						super.close(status, trailers);
					}
				};
		return next.startCall(metadataCall, requestHeaders);
	}

}
