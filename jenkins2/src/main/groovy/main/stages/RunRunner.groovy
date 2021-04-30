package main.stages

import groovy.json.JsonBuilder
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import main.context.StageContext

/**
 * Outputs the runner config
 */
@CompileStatic
class RunRunner extends Stage {
    private final InitialiseCluster stageCluster
    private final InitialisePerformer stagePerf
    private final OutputPerformerConfig stageOutput

    RunRunner(InitialiseCluster cluster, InitialisePerformer stagePerf, OutputPerformerConfig stageOutput) {
        this.stageCluster = cluster
        this.stagePerf = stagePerf
        this.stageOutput = stageOutput
    }

    @Override
    String name() {
        return "Run for ${stageOutput.absoluteConfigFilename()}"
    }

    @CompileDynamic
    @Override
    void executeImpl(StageContext ctx) {
        def json = new JsonBuilder()

        def hostname = stageCluster.hostname()
        if (hostname.equals("localhost") && stagePerf.isDocker()) {
            // Note Docker requirement of host.docker.internal - https://stackoverflow.com/questions/24319662/from-inside-of-a-docker-container-how-do-i-connect-to-the-localhost-of-the-mach
            hostname = "host.docker.internal"
        }

        json {
          cluster hostname
          bucket {
            bucketName "default"
          }
          performerPorts([stagePerf.port()])
          excludeTests(["situational"])
          loggerLevel "all:Info"
          "performance" {
            "config" stageOutput.absoluteConfigFilename()
          }
        }

        ctx.inSourceDir {
            ctx.env.dir("/txn-driver", {
                new File("${ctx.env.currentDir()}/TransactionFITConfiguration.json").write(json.toPrettyString())
                ctx.env.execute("mvn -Dtest=PerfRunnerTest test")
            })
        }
    }
}