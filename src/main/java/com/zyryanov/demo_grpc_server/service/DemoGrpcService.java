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

package com.zyryanov.demo_grpc_server.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Any;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.Timestamps;
import com.google.rpc.Status;
import com.zyryanov.demo_grpc_server.proto.AllTypesRequest;
import com.zyryanov.demo_grpc_server.proto.AllTypesResponse;
import com.zyryanov.demo_grpc_server.proto.DemoErrorDetail;
import com.zyryanov.demo_grpc_server.proto.DemoServiceGrpc;
import com.zyryanov.demo_grpc_server.proto.EchoRequest;
import com.zyryanov.demo_grpc_server.proto.EchoResponse;
import com.zyryanov.demo_grpc_server.proto.WellKnownTypes;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;

import org.springframework.stereotype.Service;

/**
 * Реализовать демонстрационные unary- и streaming-методы gRPC.
 */
@Service
public class DemoGrpcService extends DemoServiceGrpc.DemoServiceImplBase {

	@Override
	public void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {
		responseObserver.onNext(EchoResponse.newBuilder()
			.setMessage(request.getMessage())
			.setIndex(1)
			.addAllLabels(request.getLabelsList())
			.putAllAttributes(request.getAttributesMap())
			.setReceivedAt(Timestamps.fromMillis(Instant.now().toEpochMilli()))
			.setDuration(Duration.newBuilder().setSeconds(10).build())
			.build());
		responseObserver.onCompleted();
	}

	@Override
	public void nonStream(AllTypesRequest request,
			StreamObserver<AllTypesResponse> responseObserver) {

		responseObserver.onNext(response(request));
		responseObserver.onCompleted();
	}

	@Override
	public void wellKnownTypes(WellKnownTypes request,
			StreamObserver<WellKnownTypes> responseObserver) {

		responseObserver.onNext(request);
		responseObserver.onCompleted();
	}

	@Override
	public void serverStream(AllTypesRequest request,
			StreamObserver<AllTypesResponse> responseObserver) {

		for (int index = 0; index < 3; index++) {
			responseObserver.onNext(response(request).toBuilder()
				.setInt32Value(request.getInt32Value() + index)
				.build());
		}
		responseObserver.onCompleted();
	}

	@Override
	public void partialFailure(AllTypesRequest request,
			StreamObserver<AllTypesResponse> responseObserver) {

		responseObserver.onNext(numberedResponse(request, 1));
		responseObserver.onNext(numberedResponse(request, 2));
		responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
			.withDescription("Failure after two response messages")
			.asException());

		// Эти вызовы намеренно нарушают контракт StreamObserver. Они отклоняются
		// или игнорируются реализацией observer, но никогда не достигают клиента.
		trySendAfterTerminal(responseObserver, request, 4);
		trySendAfterTerminal(responseObserver, request, 5);
	}

	@Override
	public StreamObserver<AllTypesRequest> clientStream(
			StreamObserver<AllTypesResponse> responseObserver) {

		List<AllTypesRequest> requests = new ArrayList<>();
		return new StreamObserver<>() {
			@Override
			public void onNext(AllTypesRequest value) {
				requests.add(value);
			}

			@Override
			public void onError(Throwable throwable) {
				responseObserver.onError(throwable);
			}

			@Override
			public void onCompleted() {
				AllTypesRequest value = (requests.isEmpty()
						? AllTypesRequest.getDefaultInstance() : requests.getLast());
				responseObserver.onNext(response(value).toBuilder()
					.setInt32Value(requests.size())
					.build());
				responseObserver.onCompleted();
			}
		};
	}

	@Override
	public StreamObserver<AllTypesRequest> binaryStream(
			StreamObserver<AllTypesResponse> responseObserver) {

		return new StreamObserver<>() {
			@Override
			public void onNext(AllTypesRequest value) {
				responseObserver.onNext(response(value));
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
	public void alwaysFails(Empty request, StreamObserver<Empty> responseObserver) {
		DemoErrorDetail detail = DemoErrorDetail.newBuilder()
			.setReason("DEMO_VALIDATION_FAILED")
			.setField("request")
			.setRequestId("debug-request-1")
			.build();
		Status status = Status.newBuilder()
			.setCode(io.grpc.Status.Code.INVALID_ARGUMENT.value())
			.setMessage("Demo method always fails")
			.addDetails(Any.pack(detail))
			.build();
		responseObserver.onError(StatusProto.toStatusRuntimeException(status));
	}

	/**
	 * Сообщения запроса и ответа намеренно используют совместимые номера полей
	 * wire-формата.
	 */
	private AllTypesResponse response(AllTypesRequest request) {
		try {
			return AllTypesResponse.parseFrom(request.toByteArray());
		}
		catch (InvalidProtocolBufferException ex) {
			throw new IllegalStateException("Cannot mirror demo request", ex);
		}
	}

	private AllTypesResponse numberedResponse(AllTypesRequest request, int number) {
		return response(request).toBuilder()
			.setInt32Value(number)
			.setStringValue("Response #" + number)
			.build();
	}

	private void trySendAfterTerminal(StreamObserver<AllTypesResponse> responseObserver,
			AllTypesRequest request, int number) {

		try {
			responseObserver.onNext(numberedResponse(request, number));
		}
		catch (IllegalStateException ex) {
			// Ожидается: onError уже закрыл серверный поток.
		}
	}

}
