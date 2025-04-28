# brave-instrumentation-jdbi3

This includes a JDBI 3 plugin will report to Zipkin
how long each statement takes, along with relevant tags like the query.

To use it, call the `installPlugin` on the `Jdbi` instance you want do instrument,
with a suitably configured `Jdbi3BRavePlugin`.

By default, bind variable values are not included in the traces, for security
reasons. If you want to include them, pass `true` as `includeBindVariables` in
the `Jdbi3BravePlugin` constructor.
