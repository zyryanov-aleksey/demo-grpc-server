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

package com.zyryanov.demogrpcserver.service.streaming;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.util.Timestamps;
import com.zyryanov.demogrpcserver.proto.streaming.v1.ClientStreamResponse;
import com.zyryanov.demogrpcserver.proto.streaming.v1.PartialFailureRequest;
import com.zyryanov.demogrpcserver.proto.streaming.v1.ServerStreamRequest;
import com.zyryanov.demogrpcserver.proto.streaming.v1.StreamRequest;
import com.zyryanov.demogrpcserver.proto.streaming.v1.StreamResponse;
import com.zyryanov.demogrpcserver.proto.streaming.v1.StreamingServiceGrpc;
import io.grpc.stub.StreamObserver;

import org.springframework.stereotype.Service;

/**
 * Реализовать четыре формы gRPC streaming.
 */
@Service
public class StreamingGrpcService extends StreamingServiceGrpc.StreamingServiceImplBase {

	private static final int DEFAULT_RESPONSE_COUNT = 3;

	private static final int DEFAULT_SUCCESSFUL_RESPONSE_COUNT = 2;

	@Override
	public void serverStream(ServerStreamRequest request,
			StreamObserver<StreamResponse> responseObserver) {

		int responseCount = (request.hasResponseCount()
				? request.getResponseCount() : DEFAULT_RESPONSE_COUNT);
		for (int sequence = 1; sequence <= responseCount; sequence++) {
			responseObserver.onNext(response(request.getMessage(), sequence));
		}
		responseObserver.onCompleted();
	}

	@Override
	public StreamObserver<StreamRequest> clientStream(
			StreamObserver<ClientStreamResponse> responseObserver) {

		List<String> messages = new ArrayList<>();
		return new StreamObserver<>() {
			@Override
			public void onNext(StreamRequest request) {
				messages.add(request.getMessage());
			}

			@Override
			public void onError(Throwable throwable) {
				responseObserver.onError(throwable);
			}

			@Override
			public void onCompleted() {
				responseObserver.onNext(ClientStreamResponse.newBuilder()
					.setRequestCount(messages.size())
					.addAllMessages(messages)
					.build());
				responseObserver.onCompleted();
			}
		};
	}

	@Override
	public StreamObserver<StreamRequest> bidirectionalStream(
			StreamObserver<StreamResponse> responseObserver) {

		return new StreamObserver<>() {

			private int sequence;

			@Override
			public void onNext(StreamRequest request) {
				this.sequence++;
				responseObserver.onNext(response(request.getMessage(), this.sequence));
			}

			@Override
			public void onError(Throwable throwable) {
				responseObserver.onError(throwable);
			}

			@Override
			public void onCompleted() {
				responseObserver.onCompleted();
			}
		};
	}

	@Override
	public void partialFailure(PartialFailureRequest request,
			StreamObserver<StreamResponse> responseObserver) {

		int responseCount = (request.hasSuccessfulResponseCount()
				? request.getSuccessfulResponseCount()
				: DEFAULT_SUCCESSFUL_RESPONSE_COUNT);
		for (int sequence = 1; sequence <= responseCount; sequence++) {
			responseObserver.onNext(response(request.getMessage(), sequence));
		}
		responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
			.withDescription("Failure after " + responseCount + " response messages")
			.asRuntimeException());
	}

	private StreamResponse response(String message, int sequence) {
		return StreamResponse.newBuilder()
			.setMessage(message)
			.setSequence(sequence)
			.setSentAt(Timestamps.fromMillis(Instant.now().toEpochMilli()))
			.build();
	}

}
