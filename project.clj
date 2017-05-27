;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defproject io.czlab/loki "1.0.0"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :description "Network game server framework"
  :url "https://github.com/llnek/loki"

  :dependencies [[org.clojure/core.async "0.3.442"]
                 [io.czlab/wabbit-plugs "1.0.0"]
                 [io.czlab/wabbit "1.0.0"]]

  :plugins [[cider/cider-nrepl "0.14.0"]
            [lein-cprint "1.2.0"]
            [lein-codox "0.10.3"]]

  :profiles {:provided {:dependencies
                        [[org.clojure/clojure "1.8.0" :scope "provided"]]}
             :uberjar {:aot :all}}

  :global-vars {*warn-on-reflection* true}
  :target-path "out/%s"
  :aot :all

  :coordinate! "czlab"
  :omit-source true

  :java-source-paths ["src/main/java"]
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]

  :jvm-opts ["-Dlog4j.configurationFile=file:attic/log4j2.xml"]
  :javac-options ["-source" "8"
                  "-Xlint:unchecked" "-Xlint:-options" "-Xlint:deprecation"])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


