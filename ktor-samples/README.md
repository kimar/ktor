# Running samples in IntelliJ IDEA

* Create new Java application configuration
* Set main class to
    * For Jetty: `io.ktor.server.jetty.DevelopmentHost`
    * For Netty: `io.ktor.server.netty.DevelopmentHost`
* Set classpath ("Use class path of module") to sample module (eg. `ktor-samples-jackson_main`)
* Run
* Open http://localhost:8080 in browser

