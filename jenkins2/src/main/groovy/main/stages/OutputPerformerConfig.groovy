package main.stages

import groovy.json.JsonBuilder
import groovy.json.JsonGenerator
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.yaml.YamlBuilder
import main.context.StageContext
import main.fit.perf.config.PerfConfig
import main.fit.perf.config.Run
import main.fit.stages.BuildDockerJavaFITPerformer
import main.fit.stages.StartDockerImagePerformer
import org.apache.groovy.yaml.util.YamlConverter

import java.util.stream.Collectors

/**
 * Outputs the runner config
 */
@CompileStatic
class OutputPerformerConfig extends Stage {
    private final List<Run> runs
    private final String outputFilename
    private String absoluteOutputFilename = null
    private final PerfConfig.Cluster cluster
    private final PerfConfig.Implementation impl
    private final config
    private final InitialiseCluster stageCluster
    private final InitialisePerformer stagePerformer

    OutputPerformerConfig(InitialiseCluster stageCluster,
                          InitialisePerformer stagePerformer,
                                  config,
                          PerfConfig.Cluster cluster,
                          PerfConfig.Implementation impl,
                          List<Run> runs,
                          String outputFilename) {
        this.stagePerformer = stagePerformer
        this.stageCluster = stageCluster
        this.impl = impl
        this.cluster = cluster
        this.runs = runs
        this.outputFilename = outputFilename
        this.config = config
    }

    @Override
    String name() {
        return "Output performer config for ${runs.size()} runs to $outputFilename"
    }

    String absoluteConfigFilename() {
        return absoluteOutputFilename
    }

    @Override
    @CompileDynamic
    void executeImpl(StageContext ctx) {
        var runsAsYaml = runs.stream().map(run -> {
            def yaml = new YamlBuilder()
            yaml {
                uuid UUID.randomUUID().toString()
                description run.description
                transaction(run.workload.transaction)
                variables(run.workload.variables)
            }
            yaml.content
        }).collect(Collectors.toList())

        def gen = new JsonGenerator.Options()
            .excludeNulls()
            .build()
        def json = new JsonBuilder(gen)

        json {
            variables(config.variables)
            connections {
                cluster {
                    hostname stageCluster.hostname()
                    username 'Administrator'
                    password 'password'
                }

                performer {
                    hostname stagePerformer.hostname()
                    port stagePerformer.port()
                }

                database(config.database)
            }
            db {
                cluster cluster
                impl impl
            }
            runs runsAsYaml
        }

        def converted = YamlConverter.convertJsonToYaml(new StringReader(json.toString()))

        ctx.env.tempDir {
            absoluteOutputFilename = ctx.env.currentDir() + "/" + outputFilename
            new File(absoluteOutputFilename).write(converted)
        }
    }
}