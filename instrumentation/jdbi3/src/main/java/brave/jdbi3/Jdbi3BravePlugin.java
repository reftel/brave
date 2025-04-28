/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import java.time.Instant;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.spi.JdbiPlugin;
import org.jdbi.v3.core.statement.SqlStatements;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.statement.StatementContextListener;

import brave.Span;
import brave.Tracing;

/**
 * Instrumentation for JDBI 3.
 
 * JDBI plugin that sends a trace span for every SQL command executed by Jdbi.
 * Install by calling {@code jdbi.installPlugin(new Jdbi3BravePlugin(tracing));}
 */
public class Jdbi3BravePlugin extends JdbiPlugin.Singleton {
    private final Tracing tracing;
    private final boolean includeBindVariables;

    public Jdbi3BravePlugin(Tracing tracing) {
            this(tracing, false);
    }

    public Jdbi3BravePlugin(Tracing tracing, boolean includeBindVariables) {
            this.tracing = tracing;
            this.includeBindVariables = includeBindVariables;
    }

    @Override
    public void customizeJdbi(Jdbi jdbi) {
        if (tracing == null) {
            return;
        }
        jdbi.getConfig(SqlStatements.class)
            .addContextListener(new StatementContextListener() {
                @Override
                public void contextCreated(final StatementContext ctx) {
                    String spanName = "jdbi." + ctx.describeJdbiStatementType();
                    Span span = tracing.tracer().nextSpan().name(spanName);

                    Instant start = ctx.getExecutionMoment();
                    if (start != null) {
                        span.start(start.toEpochMilli() * 1000);
                    } else {
                        span.start();
                    }

                    ctx.setTraceId(span.context().traceIdString());

                    ctx.addCleanable(() -> {
                        final SqlStatements stmtConfig = ctx.getConfig(SqlStatements.class);

                        span.kind(Span.Kind.CLIENT);

                        final String renderedSql = ctx.getRenderedSql();
                        if (renderedSql != null) {
                            String truncated = renderedSql.substring(
                                0,
                                Math.min(renderedSql.length(), stmtConfig.getJfrSqlMaxLength())
                            );
                            span.tag("sql.query", truncated);
                        }

                        if (includeBindVariables) {
                            String bindVariables = ctx.getBinding()
                                .describe(stmtConfig.getJfrParamMaxLength());
                            span.tag("sql.binding", bindVariables);
                        }

                        span.tag("sql.rows", Long.toString(ctx.getMappedRows()));

                        if (ctx.getCompletionMoment() == null) {
                            span.tag("error", "true");
                        }

                        if (ctx.getCompletionMoment() != null) {
                            span.finish(ctx.getCompletionMoment().toEpochMilli() * 1000);
                        } else if (ctx.getExceptionMoment() != null) {
                            span.finish(ctx.getExceptionMoment().toEpochMilli() * 1000);
                        } else {
                            span.finish();
                        }
                    });
                }
            });
    }
}
