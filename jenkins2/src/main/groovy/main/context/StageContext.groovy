package main.context

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import main.context.environments.Environment
import main.fit.perf.config.PerfConfig
import main.stages.OutputPerformerConfig
import main.stages.Stage

@CompileStatic
class StageContext {
    // Abstraction over executing things on CI, localhost, and others
    Environment env

    // Where the performer is running, a hostname or IP
    String performerServer

    // Whether anything will actually be performed
    boolean dryRun = false

    // Whether to ignore whatever's already in the database
    boolean force = false

    // The full job config
    Object jc

    boolean allowedToExecute(Stage stage) {
        // OutputPerformerConfig is allowed to run as it's pretty instant, very useful, and in the spirit of dry running
        return !dryRun || stage instanceof OutputPerformerConfig
    }

    @CompileDynamic
    private String sourceDir() {
        return jc.servers.driver.source
    }

    // Runs the closure inside the source directory, checking it out if necessary
    def inSourceDir(Closure closure) {
        def sd = sourceDir()
        if (sd.startsWith("http")) {
            env.tempDir {
                env.checkout("https://github.com/couchbaselabs/transactions-fit-performer")
                closure.run()
            }
        }
        else {
            env.dirAbsolute(sd, closure)
        }
    }
}