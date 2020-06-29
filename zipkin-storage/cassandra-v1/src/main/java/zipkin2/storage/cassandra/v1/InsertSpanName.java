/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.storage.cassandra.v1;

import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.ResultSetFuture;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.querybuilder.Insert;
import com.datastax.oss.driver.api.core.cql.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import java.util.concurrent.CompletionStage;
import zipkin2.storage.cassandra.internal.call.DeduplicatingVoidCallFactory;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

final class InsertSpanName extends ResultSetFutureCall<Void> {

  @AutoValue
  abstract static class Input {
    static Input create(String service_name, String span_name) {
      return new AutoValue_InsertSpanName_Input(service_name, span_name);
    }

    abstract String service_name();

    abstract String span_name();

    Input() {
    }
  }

  static class Factory extends DeduplicatingVoidCallFactory<Input> {
    final CqlSession session;
    final PreparedStatement preparedStatement;

    Factory(CassandraStorage storage, int indexTtl) {
      super(storage.autocompleteTtl, storage.autocompleteCardinality);
      session = storage.session();
      Insert insertQuery = QueryBuilder.insertInto(Tables.SPAN_NAMES)
        .value("service_name", QueryBuilder.bindMarker("service_name"))
        .value("bucket", 0) // bucket is deprecated on this index
        .value("span_name", QueryBuilder.bindMarker("span_name"));
      if (indexTtl > 0) insertQuery.using(QueryBuilder.ttl(indexTtl));
      preparedStatement = session.prepare(insertQuery);
    }

    Input newInput(String service_name, String span_name) {
      return Input.create(service_name, span_name);
    }

    @Override protected InsertSpanName newCall(Input input) {
      return new InsertSpanName(this, input);
    }
  }

  final Factory factory;
  final Input input;

  InsertSpanName(Factory factory, Input input) {
    this.factory = factory;
    this.input = input;
  }

  @Override protected CompletionStage<AsyncResultSet> newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind()
      .setString("service_name", input.service_name())
      .setString("span_name", input.span_name()));
  }

  @Override public Void map(ResultSet input) {
    return null;
  }

  @Override public String toString() {
    return input.toString().replace("Input", "InsertSpanName");
  }

  @Override public InsertSpanName clone() {
    return new InsertSpanName(factory, input);
  }
}
