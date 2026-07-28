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

package com.zyryanov.demogrpcserver.service.echo;

import java.time.Instant;

import com.google.protobuf.Empty;
import com.google.protobuf.util.Timestamps;
import com.zyryanov.demogrpcserver.proto.echo.v1.EchoRequest;
import com.zyryanov.demogrpcserver.proto.echo.v1.EchoResponse;
import com.zyryanov.demogrpcserver.proto.echo.v1.EchoServiceGrpc;
import io.grpc.stub.StreamObserver;

import org.springframework.stereotype.Service;

/**
 * Реализовать простые unary-примеры gRPC.
 */
@Service
public class EchoGrpcService extends EchoServiceGrpc.EchoServiceImplBase {

	@Override
	public void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {
		EchoResponse.Builder response = EchoResponse.newBuilder()
			.setMessage(request.getMessage())
			.addAllLabels(request.getLabelsList())
			.putAllAttributes(request.getAttributesMap())
			.setReceivedAt(Timestamps.fromMillis(Instant.now().toEpochMilli()));

		if (request.hasSentAt()) {
			response.setSentAt(request.getSentAt());
		}
		if (request.hasTtl()) {
			response.setTtl(request.getTtl());
		}

		switch (request.getSelectorCase()) {
			case TEXT -> response.setText(request.getText());
			case NUMBER -> response.setNumber(request.getNumber());
			case SELECTOR_NOT_SET -> {
				// Отсутствующий oneof сохраняется отсутствующим в ответе.
			}
		}

		responseObserver.onNext(response.build());
		responseObserver.onCompleted();
	}

	@Override
	public void emptyCall(Empty request, StreamObserver<Empty> responseObserver) {
		responseObserver.onNext(Empty.getDefaultInstance());
		responseObserver.onCompleted();
	}

}
