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

package com.zyryanov.demo_grpc_server;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.zyryanov.demo_grpc_server.proto.DemoServiceGrpc;
import com.zyryanov.demo_grpc_server.proto.EchoRequest;
import com.zyryanov.demo_grpc_server.proto.EchoResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.Test;

import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DemoServerIntegrationTests {

	@Test
	void servesHealthEchoAndDemonstrationMetadata(
			@LocalGrpcServerPort int serverPort) throws Exception {

		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", serverPort)
			.usePlaintext()
			.build();
		try {
			ServingStatus health = HealthGrpc.newBlockingStub(channel)
				.check(HealthCheckRequest.getDefaultInstance())
				.getStatus();
			assertThat(health).isEqualTo(ServingStatus.SERVING);

			Metadata requestHeaders = new Metadata();
			Metadata.Key<String> requestId =
					Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
			requestHeaders.put(requestId, "integration-test");
			AtomicReference<Metadata> responseHeaders = new AtomicReference<>();
			AtomicReference<Metadata> responseTrailers = new AtomicReference<>();

			EchoResponse response = DemoServiceGrpc.newBlockingStub(channel)
				.withInterceptors(
						MetadataUtils.newAttachHeadersInterceptor(requestHeaders),
						MetadataUtils.newCaptureMetadataInterceptor(
								responseHeaders, responseTrailers))
				.echo(EchoRequest.newBuilder().setMessage("hello").build());

			assertThat(response.getMessage()).isEqualTo("hello");
			assertThat(responseHeaders.get().get(requestId)).isEqualTo("integration-test");
			assertThat(responseHeaders.get().get(Metadata.Key.of(
					"x-demo-server", Metadata.ASCII_STRING_MARSHALLER)))
				.isEqualTo("zyryal-grpc-demo");
			assertThat(responseTrailers.get().get(Metadata.Key.of(
					"x-demo-trailer", Metadata.ASCII_STRING_MARSHALLER)))
				.isEqualTo("OK");
		}
		finally {
			channel.shutdownNow();
			assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}
	}

}
