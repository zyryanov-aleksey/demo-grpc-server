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

package com.zyryanov.demogrpcserver.service.error;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.util.Timestamps;
import com.google.rpc.BadRequest;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Status;
import com.zyryanov.demogrpcserver.proto.error.v1.DelayRequest;
import com.zyryanov.demogrpcserver.proto.error.v1.DelayResponse;
import com.zyryanov.demogrpcserver.proto.error.v1.DemoErrorDetail;
import com.zyryanov.demogrpcserver.proto.error.v1.ErrorServiceGrpc;
import com.zyryanov.demogrpcserver.proto.error.v1.FailureRequest;
import io.grpc.Context;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;

import org.springframework.stereotype.Service;

/**
 * Реализовать сценарии ошибок, deadline и отмены вызова.
 */
@Service
public class ErrorGrpcService extends ErrorServiceGrpc.ErrorServiceImplBase {

	private static final long CANCELLATION_POLL_MILLIS = 25L;

	@Override
	public void simpleFailure(FailureRequest request,
			StreamObserver<Empty> responseObserver) {

		responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
			.withDescription(request.getMessage())
			.asRuntimeException());
	}

	@Override
	public void richFailure(FailureRequest request,
			StreamObserver<Empty> responseObserver) {

		String field = (request.hasField() ? request.getField() : "message");
		String requestId = (request.hasRequestId()
				? request.getRequestId() : "demo-request-1");

		DemoErrorDetail customDetail = DemoErrorDetail.newBuilder()
			.setReason("DEMO_VALIDATION_FAILED")
			.setField(field)
			.setRequestId(requestId)
			.build();
		BadRequest badRequest = BadRequest.newBuilder()
			.addFieldViolations(BadRequest.FieldViolation.newBuilder()
				.setField(field)
				.setDescription(request.getMessage())
				.build())
			.build();
		ErrorInfo errorInfo = ErrorInfo.newBuilder()
			.setReason("DEMO_VALIDATION_FAILED")
			.setDomain("demo.error.v1")
			.putMetadata("request_id", requestId)
			.build();
		Status status = Status.newBuilder()
			.setCode(io.grpc.Status.Code.INVALID_ARGUMENT.value())
			.setMessage(request.getMessage())
			.addDetails(Any.pack(customDetail))
			.addDetails(Any.pack(badRequest))
			.addDetails(Any.pack(errorInfo))
			.build();

		responseObserver.onError(StatusProto.toStatusRuntimeException(status));
	}

	@Override
	public void delayedResponse(DelayRequest request,
			StreamObserver<DelayResponse> responseObserver) {

		long startedAt = System.nanoTime();
		while (!Context.current().isCancelled() &&
				elapsedMillis(startedAt) < request.getDelayMillis()) {

			long remainingMillis = request.getDelayMillis() - elapsedMillis(startedAt);
			long sleepMillis = Math.min(remainingMillis, CANCELLATION_POLL_MILLIS);
			try {
				Thread.sleep(sleepMillis);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				responseObserver.onError(io.grpc.Status.CANCELLED
					.withDescription("Delayed response was interrupted")
					.withCause(ex)
					.asRuntimeException());
				return;
			}
		}

		if (Context.current().isCancelled()) {
			return;
		}

		responseObserver.onNext(DelayResponse.newBuilder()
			.setPayload(request.getPayload())
			.setElapsedMillis(Math.toIntExact(elapsedMillis(startedAt)))
			.setCompletedAt(Timestamps.fromMillis(Instant.now().toEpochMilli()))
			.build());
		responseObserver.onCompleted();
	}

	private long elapsedMillis(long startedAt) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
	}

}
