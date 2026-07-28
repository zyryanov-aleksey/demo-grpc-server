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

package com.zyryanov.demogrpcserver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import build.buf.validate.Violations;
import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.StringValue;
import com.google.rpc.BadRequest;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Status;
import com.zyryanov.demogrpcserver.proto.echo.v1.EchoRequest;
import com.zyryanov.demogrpcserver.proto.echo.v1.EchoResponse;
import com.zyryanov.demogrpcserver.proto.echo.v1.EchoServiceGrpc;
import com.zyryanov.demogrpcserver.proto.error.v1.DelayRequest;
import com.zyryanov.demogrpcserver.proto.error.v1.DelayResponse;
import com.zyryanov.demogrpcserver.proto.error.v1.DemoErrorDetail;
import com.zyryanov.demogrpcserver.proto.error.v1.ErrorServiceGrpc;
import com.zyryanov.demogrpcserver.proto.error.v1.FailureRequest;
import com.zyryanov.demogrpcserver.proto.streaming.v1.ClientStreamResponse;
import com.zyryanov.demogrpcserver.proto.streaming.v1.PartialFailureRequest;
import com.zyryanov.demogrpcserver.proto.streaming.v1.ServerStreamRequest;
import com.zyryanov.demogrpcserver.proto.streaming.v1.StreamRequest;
import com.zyryanov.demogrpcserver.proto.streaming.v1.StreamResponse;
import com.zyryanov.demogrpcserver.proto.streaming.v1.StreamingServiceGrpc;
import com.zyryanov.demogrpcserver.proto.types.v1.AllTypes;
import com.zyryanov.demogrpcserver.proto.types.v1.NestedMessage;
import com.zyryanov.demogrpcserver.proto.types.v1.TypesServiceGrpc;
import com.zyryanov.demogrpcserver.proto.types.v1.WellKnownTypes;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest
class DemoServerIntegrationTests {

	@Test
	void servesHealthEchoEmptyCallAndDemonstrationMetadata(
			@LocalGrpcServerPort int serverPort) throws Exception {

		ManagedChannel channel = channel(serverPort);
		try {
			ServingStatus health = HealthGrpc.newBlockingStub(channel)
				.check(HealthCheckRequest.getDefaultInstance())
				.getStatus();
			assertThat(health).isEqualTo(ServingStatus.SERVING);

			Metadata requestHeaders = new Metadata();
			Metadata.Key<String> requestId =
					Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
			requestHeaders.put(requestId, "integration-test");
			Metadata.Key<byte[]> binaryContext =
					Metadata.Key.of("x-demo-bin", Metadata.BINARY_BYTE_MARSHALLER);
			requestHeaders.put(binaryContext, new byte[] { 1, 2, 3 });
			AtomicReference<Metadata> responseHeaders = new AtomicReference<>();
			AtomicReference<Metadata> responseTrailers = new AtomicReference<>();

			EchoServiceGrpc.EchoServiceBlockingStub stub =
					EchoServiceGrpc.newBlockingStub(channel)
						.withInterceptors(
								MetadataUtils.newAttachHeadersInterceptor(requestHeaders),
								MetadataUtils.newCaptureMetadataInterceptor(
										responseHeaders, responseTrailers));
			EchoResponse response = stub.echo(EchoRequest.newBuilder()
				.setMessage("hello")
				.setText("primary")
				.putAttributes("source", "test")
				.addLabels("greeting")
				.build());

			assertThat(response.getMessage()).isEqualTo("hello");
			assertThat(response.getText()).isEqualTo("primary");
			assertThat(response.getAttributesMap()).containsEntry("source", "test");
			assertThat(response.getReceivedAt()).isNotNull();
			assertThat(stub.emptyCall(Empty.getDefaultInstance()))
				.isEqualTo(Empty.getDefaultInstance());
			assertThat(responseHeaders.get().get(requestId)).isEqualTo("integration-test");
			assertThat(responseHeaders.get().get(binaryContext))
				.containsExactly(1, 2, 3);
			assertThat(responseHeaders.get().get(Metadata.Key.of(
					"x-demo-server", Metadata.ASCII_STRING_MARSHALLER)))
				.isEqualTo("zyryal-grpc-demo");
			assertThat(responseTrailers.get().get(Metadata.Key.of(
					"x-demo-trailer", Metadata.ASCII_STRING_MARSHALLER)))
				.isEqualTo("OK");
		}
		finally {
			shutdown(channel);
		}
	}

	@Test
	void echoesAllTypesAndWellKnownTypes(@LocalGrpcServerPort int serverPort)
			throws Exception {

		ManagedChannel channel = channel(serverPort);
		try {
			TypesServiceGrpc.TypesServiceBlockingStub stub =
					TypesServiceGrpc.newBlockingStub(channel);
			AllTypes allTypes = AllTypes.newBuilder()
				.setDoubleValue(12.5)
				.setInt32Value(42)
				.setStringValue("sample")
				.setOptionalValue("optional")
				.addRepeatedValues("one")
				.putMapValues("answer", 42)
				.setMessageValue(NestedMessage.newBuilder().setValue("nested").build())
				.setSelectedStringValue("selected")
				.build();
			assertThat(stub.echoAllTypes(allTypes)).isEqualTo(allTypes);

			WellKnownTypes wellKnownTypes = WellKnownTypes.newBuilder()
				.setStringValue(StringValue.of("wrapped"))
				.setAnyValue(Any.pack(allTypes))
				.build();
			assertThat(stub.echoWellKnownTypes(wellKnownTypes)).isEqualTo(wellKnownTypes);
		}
		finally {
			shutdown(channel);
		}
	}

	@Test
	void supportsAllStreamingCardinalities(@LocalGrpcServerPort int serverPort)
			throws Exception {

		ManagedChannel channel = channel(serverPort);
		try {
			StreamingServiceGrpc.StreamingServiceBlockingStub blockingStub =
					StreamingServiceGrpc.newBlockingStub(channel);
			Iterator<StreamResponse> serverStream = blockingStub.serverStream(
					ServerStreamRequest.newBuilder()
						.setMessage("server")
						.setResponseCount(3)
						.build());
			List<StreamResponse> serverResponses = new ArrayList<>();
			serverStream.forEachRemaining(serverResponses::add);
			assertThat(serverResponses)
				.extracting(StreamResponse::getSequence)
				.containsExactly(1, 2, 3);

			StreamingServiceGrpc.StreamingServiceStub asyncStub =
					StreamingServiceGrpc.newStub(channel);
			AtomicReference<ClientStreamResponse> clientStreamResponse =
					new AtomicReference<>();
			AtomicReference<Throwable> clientStreamError = new AtomicReference<>();
			CountDownLatch clientStreamCompleted = new CountDownLatch(1);
			StreamObserver<StreamRequest> clientStream = asyncStub.clientStream(
					new StreamObserver<>() {
						@Override
						public void onNext(ClientStreamResponse response) {
							clientStreamResponse.set(response);
						}

						@Override
						public void onError(Throwable throwable) {
							clientStreamError.set(throwable);
							clientStreamCompleted.countDown();
						}

						@Override
						public void onCompleted() {
							clientStreamCompleted.countDown();
						}
					});
			clientStream.onNext(streamRequest("first"));
			clientStream.onNext(streamRequest("second"));
			clientStream.onCompleted();

			assertThat(clientStreamCompleted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(clientStreamError.get()).isNull();
			assertThat(clientStreamResponse.get().getRequestCount()).isEqualTo(2);
			assertThat(clientStreamResponse.get().getMessagesList())
				.containsExactly("first", "second");

			List<StreamResponse> bidirectionalResponses = new ArrayList<>();
			AtomicReference<Throwable> bidirectionalError = new AtomicReference<>();
			CountDownLatch bidirectionalCompleted = new CountDownLatch(1);
			StreamObserver<StreamRequest> bidirectionalStream =
					asyncStub.bidirectionalStream(new StreamObserver<>() {
						@Override
						public void onNext(StreamResponse response) {
							bidirectionalResponses.add(response);
						}

						@Override
						public void onError(Throwable throwable) {
							bidirectionalError.set(throwable);
							bidirectionalCompleted.countDown();
						}

						@Override
						public void onCompleted() {
							bidirectionalCompleted.countDown();
						}
					});
			bidirectionalStream.onNext(streamRequest("one"));
			bidirectionalStream.onNext(streamRequest("two"));
			bidirectionalStream.onCompleted();

			assertThat(bidirectionalCompleted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(bidirectionalError.get()).isNull();
			assertThat(bidirectionalResponses)
				.extracting(StreamResponse::getMessage)
				.containsExactly("one", "two");

			Iterator<StreamResponse> partialFailure = blockingStub.partialFailure(
					PartialFailureRequest.newBuilder()
						.setMessage("accepted")
						.setSuccessfulResponseCount(2)
						.build());
			List<StreamResponse> partialResponses = new ArrayList<>();
			StatusRuntimeException exception = catchThrowableOfType(
					StatusRuntimeException.class, () -> {
				while (partialFailure.hasNext()) {
					partialResponses.add(partialFailure.next());
				}
			});

			assertThat(partialResponses).hasSize(2);
			assertThat(exception.getStatus().getCode()).isEqualTo(Code.INVALID_ARGUMENT);
		}
		finally {
			shutdown(channel);
		}
	}

	@Test
	void validatesRequestsAndReturnsRichViolationDetails(
			@LocalGrpcServerPort int serverPort) throws Exception {

		ManagedChannel channel = channel(serverPort);
		try {
			StatusRuntimeException exception = catchThrowableOfType(
					StatusRuntimeException.class,
					() -> EchoServiceGrpc.newBlockingStub(channel)
						.echo(EchoRequest.getDefaultInstance()));

			assertThat(exception.getStatus().getCode()).isEqualTo(Code.INVALID_ARGUMENT);
			Status status = Objects.requireNonNull(StatusProto.fromThrowable(exception));
			assertThat(status.getMessage()).isEqualTo("Request validation failed");
			assertThat(status.getDetailsList()).hasSize(1);
			Violations violations = status.getDetails(0).unpack(Violations.class);
			assertThat(violations.getViolationsList()).isNotEmpty();
			assertThat(violations.getViolationsList())
				.extracting(build.buf.validate.Violation::getRuleId)
				.contains("string.min_len");
		}
		finally {
			shutdown(channel);
		}
	}

	@Test
	void exposesSimpleRichDeadlineAndUnimplementedErrors(
			@LocalGrpcServerPort int serverPort) throws Exception {

		ManagedChannel channel = channel(serverPort);
		try {
			ErrorServiceGrpc.ErrorServiceBlockingStub stub =
					ErrorServiceGrpc.newBlockingStub(channel);
			FailureRequest request = FailureRequest.newBuilder()
				.setMessage("invalid input")
				.setField("message")
				.setRequestId("req-42")
				.build();

			StatusRuntimeException simpleException = catchThrowableOfType(
					StatusRuntimeException.class, () -> stub.simpleFailure(request));
			assertThat(simpleException.getStatus().getCode())
				.isEqualTo(Code.INVALID_ARGUMENT);

			StatusRuntimeException richException = catchThrowableOfType(
					StatusRuntimeException.class, () -> stub.richFailure(request));
			Status richStatus = Objects.requireNonNull(
					StatusProto.fromThrowable(richException));
			assertThat(richStatus.getDetailsList())
				.extracting(Any::getTypeUrl)
				.containsExactly(
						"type.googleapis.com/demo.error.v1.DemoErrorDetail",
						"type.googleapis.com/google.rpc.BadRequest",
						"type.googleapis.com/google.rpc.ErrorInfo");
			assertThat(richStatus.getDetails(0).unpack(DemoErrorDetail.class)
				.getRequestId()).isEqualTo("req-42");
			assertThat(richStatus.getDetails(1).unpack(BadRequest.class)
				.getFieldViolations(0).getField()).isEqualTo("message");
			assertThat(richStatus.getDetails(2).unpack(ErrorInfo.class)
				.getMetadataMap()).containsEntry("request_id", "req-42");

			DelayResponse delayedResponse = stub.delayedResponse(
					DelayRequest.newBuilder()
						.setDelayMillis(10)
						.setPayload("completed")
						.build());
			assertThat(delayedResponse.getPayload()).isEqualTo("completed");
			assertThat(delayedResponse.getElapsedMillis()).isGreaterThanOrEqualTo(10);

			StatusRuntimeException deadlineException = catchThrowableOfType(
					StatusRuntimeException.class,
					() -> stub.withDeadlineAfter(20, TimeUnit.MILLISECONDS)
						.delayedResponse(DelayRequest.newBuilder()
							.setDelayMillis(250)
							.setPayload("too slow")
							.build()));
			assertThat(deadlineException.getStatus().getCode())
				.isEqualTo(Code.DEADLINE_EXCEEDED);

			StatusRuntimeException unimplementedException = catchThrowableOfType(
					StatusRuntimeException.class,
					() -> stub.notImplemented(Empty.getDefaultInstance()));
			assertThat(unimplementedException.getStatus().getCode())
				.isEqualTo(Code.UNIMPLEMENTED);
		}
		finally {
			shutdown(channel);
		}
	}

	private ManagedChannel channel(int serverPort) {
		return ManagedChannelBuilder.forAddress("localhost", serverPort)
			.usePlaintext()
			.build();
	}

	private StreamRequest streamRequest(String message) {
		return StreamRequest.newBuilder().setMessage(message).build();
	}

	private void shutdown(ManagedChannel channel) throws InterruptedException {
		channel.shutdownNow();
		assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
	}

}
