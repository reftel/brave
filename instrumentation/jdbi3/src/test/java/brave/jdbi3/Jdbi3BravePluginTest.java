/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Cleanable;
import org.jdbi.v3.core.statement.SqlStatements;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.statement.StatementContextListener;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContext;

class Jdbi3BravePluginTest {
    @Test
    public void shouldSendSpans() throws SQLException {
        Span span = Mockito.mock(Span.class);
        Tracer tracer = Mockito.mock(Tracer.class);
        Mockito.when(tracer.nextSpan()).thenReturn(span);
        Mockito.when(span.name(Mockito.anyString())).thenReturn(span);
        TraceContext traceContext = Mockito.mock(TraceContext.class);
        Mockito.when(span.context()).thenReturn(traceContext);
        Tracing tracing = Mockito.mock(Tracing.class);
        Mockito.when(tracing.tracer()).thenReturn(tracer);
        Jdbi jdbi = Mockito.mock(Jdbi.class);
        SqlStatements sqlStatements = Mockito.mock(SqlStatements.class);
        Mockito.when(sqlStatements.addContextListener(Mockito.any())).thenReturn(sqlStatements);
        Mockito.when(sqlStatements.getJfrSqlMaxLength()).thenReturn(100);
        Mockito.when(jdbi.getConfig(SqlStatements.class)).thenReturn(sqlStatements);

        Jdbi3BravePlugin plugin = new Jdbi3BravePlugin(tracing);

        plugin.customizeJdbi(jdbi);

        ArgumentCaptor<StatementContextListener> statementContextListenerArgumentCaptor =
            ArgumentCaptor.forClass(StatementContextListener.class);
        Mockito.verify(sqlStatements, Mockito.times(1))
            .addContextListener(statementContextListenerArgumentCaptor.capture());

        StatementContext statementContext = Mockito.mock(StatementContext.class);
        Mockito.when(statementContext.describeJdbiStatementType()).thenReturn("statement");
        Mockito.when(statementContext.getRenderedSql()).thenReturn("SELECT * FROM table");
        Mockito.when(statementContext.getConfig(SqlStatements.class)).thenReturn(sqlStatements);
        Mockito.when(statementContext.getMappedRows()).thenReturn(42L);

        statementContextListenerArgumentCaptor.getValue().contextCreated(statementContext);

        ArgumentCaptor<Cleanable> cleanableArgumentCaptor = ArgumentCaptor.forClass(Cleanable.class);
        Mockito.verify(statementContext, Mockito.times(1)).addCleanable(cleanableArgumentCaptor.capture());

        cleanableArgumentCaptor.getValue().close();

        Mockito.verify(span, Mockito.times(1)).start();
        Mockito.verify(span, Mockito.times(1)).name("jdbi.statement");
        Mockito.verify(span, Mockito.times(1)).kind(Span.Kind.CLIENT);
        Mockito.verify(span, Mockito.times(1)).tag("sql.query", "SELECT * FROM table");
        Mockito.verify(span, Mockito.times(1)).tag("sql.rows", "42");
        Mockito.verify(span, Mockito.times(1)).finish();
    }
}