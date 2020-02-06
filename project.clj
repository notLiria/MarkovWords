(defproject word-guessing "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-http "3.4.1"]
                 [clj-json "0.5.3"]
                 [enlive "1.1.6"]
                 [http-kit "2.1.18"]
                 [org.clojure/java.jdbc "0.3.7"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
]
  :main ^:skip-aot word-guessing.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
