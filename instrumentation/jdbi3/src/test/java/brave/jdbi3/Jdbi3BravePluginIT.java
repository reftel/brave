/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.JdbiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import brave.Span;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;

class Jdbi3BravePluginIT {
    ListSpanHandler spanHandler = new ListSpanHandler();

    Tracing tracing = Tracing.newBuilder()
        .addSpanHandler(spanHandler)
        .build();

    @BeforeEach
    void clearSpans() {
        spanHandler.clear();
    }

    @Test
    public void shouldSendSpans() {
        buildJdbi().withHandle(h -> {
            h.execute("CREATE TABLE test (id INT PRIMARY KEY, name VARCHAR(255))");
            h.execute("INSERT INTO test (id, name) VALUES (1, 'test')");
            return h.createQuery("SELECT * FROM test where name = :name")
                .bind("name", "test")
                .mapToMap()
                .list();
        });

        assertThat(spanHandler.getSpans())
            .anySatisfy(span -> {
                assertThat(span.kind()).isEqualTo(Span.Kind.CLIENT);
                assertThat(span.name()).isEqualTo("jdbi.Query");
                assertThat(span.tags()).containsEntry("sql.query", "SELECT * FROM test where name = :name")
                    .containsEntry("sql.rows", "1")
                    .containsEntry("sql.binding", "{named:{name:test}}");
            });
    }

    @Test
    public void shouldSendSpansForFailedStatements() {
        assertThatThrownBy(() -> buildJdbi().useHandle(h -> {
            h.execute("INSERT INTO nonexistant (id, name) VALUES (1, 'test')");
        }))
            .isInstanceOf(JdbiException.class)
            .hasMessageContaining("Table \"NONEXISTANT\" not found");

        assertThat(spanHandler.getSpans())
            .anySatisfy(span -> {
                assertThat(span.name()).isEqualTo("jdbi.Update");
                assertThat(span.error()).isNull();
                assertThat(span.tags()).containsKey("error");
            });
    }

    private Jdbi buildJdbi() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:");
        jdbi.installPlugin(new Jdbi3BravePlugin(tracing, true));
        return jdbi;
    }

    private static class ListSpanHandler extends SpanHandler {
        private final List<MutableSpan> spans = new ArrayList<>();

        @Override
        public boolean end(TraceContext context, MutableSpan span, Cause cause) {
            spans.add(span);
            return super.end(context, span, cause);
        }

        public List<MutableSpan> getSpans() {
            return spans;
        }

        public void clear() {
            spans.clear();
        }
    }
}