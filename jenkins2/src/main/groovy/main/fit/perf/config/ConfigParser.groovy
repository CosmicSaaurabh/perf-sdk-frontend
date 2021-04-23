package main.fit.perf.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovyjarjarantlr4.v4.runtime.misc.Nullable

import java.util.concurrent.ThreadLocalRandom
import java.util.stream.Collectors

@CompileStatic
class ConfigParser {
    private final static ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private final static ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    static PerfConfig readPerfConfig(String filename) {
        return yamlMapper.readValue(new File(filename), PerfConfig.class)
    }

    static List<Run> allPerms(PerfConfig config) {
        def out = new ArrayList<Run>()

        for (cluster in config.matrix.clusters) {
            for (impl in config.matrix.implementations) {
                for (workload in config.matrix.workloads) {
                    var setWorkloads = genSetWorkloadsUnrolledCustom(workload, [], workload.variables.custom);

                    for (setWorkload in setWorkloads) {
                        def run = new Run()
                        run.cluster = cluster
                        run.impl = impl
                        run.workload = setWorkload
                        run.description = getTransactionDescription(run.workload)

                        out.add(run)

                    }
                }
            }
        }

        return out
    }

    static String getDocId(SetWorkload workload, PerfConfig.Workload.Transaction.Doc doc, int repeatIdx) {
        switch (doc.from) {
            case PerfConfig.Workload.Transaction.Doc.From.UUID:
                return "__doc_" + repeatIdx
            case PerfConfig.Workload.Transaction.Doc.From.POOL:
                String ret = null

                for (def predefined : workload.variables.predefined) {
                    if (predefined.name == PerfConfig.Workload.PredefinedVariable.PredefinedVariableName.DOC_POOL_SIZE.name()) {
                        Integer poolSize = (Integer) predefined.value
                        ret = "__doc-" + ThreadLocalRandom.current().nextInt(poolSize)
                    }
                }

                if (ret == null) {
                    throw new IllegalArgumentException("'pool' doc used but doc_pool_size is not specified")
                }

                return ret
            default:
                throw new IllegalArgumentException("Unknown doc " + doc.from)
        }
    }

    static private def addOp(@Nullable StringBuilder sb, SetWorkload workload, PerfConfig.Workload.Transaction.Operation op, int repeatIdx) {
        String docId = getDocId(workload, op.doc, repeatIdx)

        switch (op.op) {
            case PerfConfig.Workload.Transaction.Operation.Op.INSERT:
                if (sb != null) {
                    sb.append("inserting ")
                    sb.append(op.doc.toString())
                }
                break
//                return new OpInsert(docId, initial)
//                builder.insert(docId, initial, EXPECT_ANYTHING);
            case PerfConfig.Workload.Transaction.Operation.Op.REPLACE:
                if (sb != null) {
                    sb.append("replacing ")
                    sb.append(op.doc.toString())
                }
                break
//                return new OpReplace(docId, updated)
//                builder.replace(docId, updated, EXPECT_ANYTHING);

//            case INSERT -> builder.insert(docId, null, EXPECT_ANYTHING);
//            case REPLACE -> builder.replace(docId, null, EXPECT_ANYTHING);
            case PerfConfig.Workload.Transaction.Operation.Op.REMOVE:
                if (sb != null) {
                    sb.append("removing ")
                    sb.append(op.doc.toString())
                }
                break
//                return new OpRemove(docId)
//                builder.remove(docId, EXPECT_ANYTHING);
            default:
                throw new IllegalArgumentException("Unknown op " + op)
        }
    }

//    interface Op {
//        void applyTo(TransactionBuilder builder);
//    }
//
//    class OpInsert implements Op {
//        String docId
//        JsonObject content
//
//        @Override
//        void applyTo(TransactionBuilder builder) {
//            builder.insert(docId, content, PredefinedResults.EXPECT_ANYTHING)
//        }
//    }
//
//    class OpReplace implements Op {
//        String docId
//        JsonObject content
//
//        @Override
//        void applyTo(TransactionBuilder builder) {
//            builder.replace(docId, content, PredefinedResults.EXPECT_ANYTHING)
//        }
//    }
//
//    class OpRemove implements Op {
//        String docId
//
//        @Override
//        void applyTo(TransactionBuilder builder) {
//            builder.remove(docId, PredefinedResults.EXPECT_ANYTHING)
//        }
//    }
//
//    class BuiltTransaction {
////        List<Op> transaction
//        String description
//    }


    static private String getTransactionDescription(SetWorkload workload) {
        StringBuilder sb = new StringBuilder();
//        var ops = new ArrayList<Op>();

        workload.transaction.operations.forEach(op -> {
            if (op.repeat != null) {
//                op.repeat.forEach(repeatedOp -> {
                int count = evaluateCount(workload, op.repeat.count);
                sb.append("Repeating ");
                sb.append(op.repeat.count);
                sb.append(' ');
                for (int i = 0; i < count; i++) {
                    addOp(i == 0 ? sb : null, workload, op.repeat, i)
//                        ops.add();
                }
                sb.append("; ");
//                });
            }

            if (op.op != null) {
                addOp(sb, workload, op, 0)
//                ops.add();
            }
        });

        return sb.toString()
    }

    static private int evaluateCount(SetWorkload workload, String count) {
        try {
            return Integer.parseInt(count)
        } catch (RuntimeException err) {
            if (count.startsWith('$')) {
                return workload.variables.getCustomVarAsInt(count)
            }

            throw new IllegalArgumentException("Don't know how to handle repeated count " + count)
        }
    }

    @CompileDynamic
    static List<SetWorkload> genSetWorkloadsRolled(PerfConfig.Workload workload) {
        var customVars = new ArrayList<SetCustomVariable>()
        var predefinedVars = new ArrayList<SetPredefinedVariable>()

        workload.variables.custom.forEach(custom ->
                custom.values.forEach(value -> {
                    customVars.add(new SetCustomVariable(custom.name, value))
                }))

        workload.variables.predefined.forEach(predefined ->
                predefined.values.forEach(value ->
                        predefinedVars.add(
                                new SetPredefinedVariable(predefined.name, value))))

        var customVarsPerms = createVariablePerms(customVars)
        var predefinedVarsPerms = createVariablePerms(predefinedVars)

        return customVarsPerms.stream().flatMap(cv ->
                predefinedVarsPerms.stream().map(nc ->
                        new SetWorkload(workload.transaction, new SetVariables(nc, cv))))
                .collect(Collectors.toList())
    }

    /**
     * Want
     * 3 1 10 M
     * 3 1 10 P
     * 3 1 1000 M
     * 3 1 1000 P
     * 3 5 10 M
     * 3 5 10 P
     * 3 5 1000 M
     * 3 5 1000 P
     * 50 1 10 M
     * 50 1 10 P
     * 50 1 1000 M
     * 50 1 1000 P
     * 50 5 10 M
     * 50 5 10 P
     * 50 5 1000 M
     * 50 5 1000 P
     */

    @CompileStatic
    static List<SetWorkload> genSetWorkloadsUnrolledCustom(PerfConfig.Workload workload,
                                                           List<SetCustomVariable> setSoFar,
                                                           List<PerfConfig.Workload.CustomVariable> varsRest) {
        if (varsRest.size() == 0) {
            return genSetWorkloadsUnrolledPredefined(workload, setSoFar, new ArrayList<SetPredefinedVariable>(), workload.variables.predefined)
        } else {
            def head = varsRest.get(0)
            def rest = varsRest.subList(1, varsRest.size())
            def out = new ArrayList<SetWorkload>()

            head.values.forEach(value -> {
                def cloned = new ArrayList(setSoFar)
                cloned.add(new SetCustomVariable(head.name, value))
                out.addAll(genSetWorkloadsUnrolledCustom(workload, cloned, rest))
            })

            return out
        }
    }

    @CompileStatic
    static List<SetWorkload> genSetWorkloadsUnrolledPredefined(PerfConfig.Workload workload,
                                                               List<SetCustomVariable> custom,
                                                               List<SetPredefinedVariable> setSoFar,
                                                               List<PerfConfig.Workload.PredefinedVariable> varsRest) {
        if (varsRest.size() == 0) {
            def sw = new SetWorkload(workload.transaction, new SetVariables(setSoFar, custom))
            return [sw]
        }
        else {
            def head = varsRest.get(0)
            def rest = varsRest.subList(1, varsRest.size())
            def out = new ArrayList<SetWorkload>()

            head.values.forEach(value -> {
                def cloned = new ArrayList(setSoFar)
                cloned.add(new SetPredefinedVariable(head.name, value))
                out.addAll(genSetWorkloadsUnrolledPredefined(workload, custom, cloned, rest))
            })

            return out
        }
    }

    private static <T extends HasName> List<List<T>> createVariablePerms(ArrayList<T> predefinedVars) {
        var grouped = predefinedVars.stream()
                .collect(Collectors.groupingBy((HasName a) -> a.getName()))

        def next = []
        grouped.values().forEach(list -> list.forEach(v -> next.add(v)))
        return createVariablePerms(next) as List<List<T>>
    }

// In:  [[A1,A2],[B1,B2],[C1,C2]]
// Out: [[A1,B1,C1],[A1,B1,C2],[A1,B2,C1]...]
    @CompileDynamic
    private static <T> List<List<T>> createVariablePerms(List<List<T>> cons) {
        if (cons.size() == 1) {
            // [[C1],[C2]]
            return cons.get(0).stream().map(Arrays::asList).collect(Collectors.toList())
        } else {
            // [A1,A2]
            var head = cons.get(0)
            // [[B1,B2],[C1,C2]]
            var nextCons = cons.subList(1, cons.size())
            // [[B1,C1],[B1,C2],[B2,C1],[B2,C2]]
            var next = createVariablePerms(nextCons)
            return head.stream().flatMap(h ->
                    next.stream().map(nc -> {
                        // h=A1, nc=[B1,C1]
                        var ret = new ArrayList<>(nc)
                        // [B1,C1,A1]
                        ret.add(h)
                        return ret
                    }))
                    .collect(Collectors.toList())
        }
    }

}
