/*
 * Copyright (C) 2017-2017 DataStax Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.driver.api.core.session;

import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import com.datastax.oss.driver.internal.core.context.InternalDriverContext;
import com.datastax.oss.driver.internal.core.cql.CqlRequestSyncProcessor;
import com.datastax.oss.driver.internal.core.session.DefaultSession;
import com.datastax.oss.driver.internal.core.session.RequestHandler;
import com.datastax.oss.driver.internal.core.session.RequestProcessor;

/**
 * A request processor that takes a given {@link KeyRequest#getKey} and generates a query, delegates
 * it to {@link CqlRequestSyncProcessor} to get the integer value of a row and return it as a
 * result.
 */
public class KeyRequestProcessor implements RequestProcessor<KeyRequest, Integer> {

  static final GenericType<Integer> INT_TYPE = GenericType.of(Integer.class);

  private final CqlRequestSyncProcessor subProcessor;

  KeyRequestProcessor(CqlRequestSyncProcessor subProcessor) {
    this.subProcessor = subProcessor;
  }

  @Override
  public boolean canProcess(Request request, GenericType<?> resultType) {
    return request instanceof KeyRequest && resultType.equals(INT_TYPE);
  }

  @Override
  public RequestHandler<KeyRequest, Integer> newHandler(
      KeyRequest request,
      DefaultSession session,
      InternalDriverContext context,
      String sessionLogPrefix) {
    // Create statement from key and delegate it to CqlRequestSyncProcessor
    SimpleStatement statement =
        SimpleStatement.newInstance(
            "select v1 from test where k = ? and v0 = ?", RequestProcessorIT.KEY, request.getKey());
    RequestHandler<Statement<?>, ResultSet> subHandler =
        subProcessor.newHandler(statement, session, context, sessionLogPrefix);
    return new KeyRequestHandler(subHandler);
  }

  class KeyRequestHandler implements RequestHandler<KeyRequest, Integer> {

    private final RequestHandler<Statement<?>, ResultSet> subHandler;

    KeyRequestHandler(RequestHandler<Statement<?>, ResultSet> subHandler) {
      this.subHandler = subHandler;
    }

    @Override
    public Integer handle() {
      ResultSet result = subHandler.handle();
      // If not exactly 1 rows were found, return Integer.MIN_VALUE, otherwise return the value.
      if (result.getAvailableWithoutFetching() != 1) {
        return Integer.MIN_VALUE;
      } else {
        return result.iterator().next().getInt("v1");
      }
    }
  }
}
