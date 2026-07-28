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

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.Validator;
import build.buf.protovalidate.exceptions.ValidationException;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.rpc.Status;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

/**
 * Проверить каждое входящее protobuf-сообщение по правилам Protovalidate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@GlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class ProtovalidateServerInterceptor implements ServerInterceptor {

	private final Validator validator;

	@Override
	public <RequestT, ResponseT> ServerCall.Listener<RequestT> interceptCall(
			ServerCall<RequestT, ResponseT> call, Metadata requestHeaders,
			ServerCallHandler<RequestT, ResponseT> next) {

		ServerCall.Listener<RequestT> delegate = next.startCall(call, requestHeaders);
		return new SimpleForwardingServerCallListener<>(delegate) {

			private boolean rejected;

			@Override
			public void onMessage(RequestT request) {
				if (this.rejected) {
					return;
				}
				if (!(request instanceof Message message)) {
					super.onMessage(request);
					return;
				}

				try {
					ValidationResult result = validator.validate(message);
					if (result.isSuccess()) {
						super.onMessage(request);
						return;
					}
					rejectValidation(result);
				}
				catch (ValidationException ex) {
					log.error("Не удалось выполнить Protovalidate-проверку", ex);
					this.rejected = true;
					call.close(io.grpc.Status.INTERNAL
						.withDescription("Request validation could not be completed")
						.withCause(ex), new Metadata());
				}
			}

			private void rejectValidation(ValidationResult result) {
				this.rejected = true;
				Status status = Status.newBuilder()
					.setCode(io.grpc.Status.Code.INVALID_ARGUMENT.value())
					.setMessage("Request validation failed")
					.addDetails(Any.pack(result.toProto()))
					.build();
				StatusRuntimeException exception =
						StatusProto.toStatusRuntimeException(status);
				Metadata trailers = exception.getTrailers();
				call.close(exception.getStatus(),
						(trailers != null ? trailers : new Metadata()));
			}

			@Override
			public void onHalfClose() {
				if (!this.rejected) {
					super.onHalfClose();
				}
			}

			@Override
			public void onCancel() {
				if (!this.rejected) {
					super.onCancel();
				}
			}

			@Override
			public void onComplete() {
				if (!this.rejected) {
					super.onComplete();
				}
			}

			@Override
			public void onReady() {
				if (!this.rejected) {
					super.onReady();
				}
			}
		};
	}

}
