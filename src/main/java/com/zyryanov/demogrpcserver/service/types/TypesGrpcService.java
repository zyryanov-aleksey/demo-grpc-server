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

package com.zyryanov.demogrpcserver.service.types;

import com.zyryanov.demogrpcserver.proto.types.v1.AllTypes;
import com.zyryanov.demogrpcserver.proto.types.v1.TypesServiceGrpc;
import com.zyryanov.demogrpcserver.proto.types.v1.WellKnownTypes;
import io.grpc.stub.StreamObserver;

import org.springframework.stereotype.Service;

/**
 * Возвращать protobuf-сообщения разных типов без изменения.
 */
@Service
public class TypesGrpcService extends TypesServiceGrpc.TypesServiceImplBase {

	@Override
	public void echoAllTypes(AllTypes request, StreamObserver<AllTypes> responseObserver) {
		responseObserver.onNext(request);
		responseObserver.onCompleted();
	}

	@Override
	public void echoWellKnownTypes(WellKnownTypes request,
			StreamObserver<WellKnownTypes> responseObserver) {

		responseObserver.onNext(request);
		responseObserver.onCompleted();
	}

}
