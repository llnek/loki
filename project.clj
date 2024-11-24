;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defproject io.czlab/loki "2.2.0"

  :license {:url "https://www.apache.org/licenses/LICENSE-2.0.txt"
            :name "Apache License"}

  :description "Network game server framework"
  :url "https://github.com/llnek/loki"

  :dependencies [[org.clojure/core.async "1.6.681"]
                 [io.czlab/jasal "2.2.0"]
                 [io.czlab/bixby "2.2.0"]]

  :plugins [[cider/cider-nrepl "0.50.2" :exclusions [nrepl/nrepl]]
            [lein-codox "0.10.8"]
            [lein-libjars "2.2.0"]
            [lein-cljsbuild "1.1.8"]]

  :profiles {:provided {:dependencies
                        [[org.clojure/clojure "1.12.0"]]}
             :uberjar {:aot :all}}

  :global-vars {*warn-on-reflection* true}
  :preserve-eval-meta true
  :target-path "out/%s"
  :aot :all

  :coordinate! "czlab"
  :omit-source true

  :java-source-paths ["src/main/java"]
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]

  :aliases { "bixby-run" ["trampoline" "run" "-m" "czlab.bixby.exec"] }

  :jvm-opts ["-Dlog4j.configurationFile=file:attic/log4j2.xml"]
  :javac-options ["-source" "16"
                  "-target" "22"
                  "-Xlint:unchecked" "-Xlint:-options" "-Xlint:deprecation"])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


