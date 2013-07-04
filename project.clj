(defproject com.creeaaakk.dtm-dispatch "0.1.0-SNAPSHOT"
  :description "Rapidly dispatch to handler functions, based on incoming datomic transactions."
  :url "https://github.com/creeaaakk/dtm-dispatch"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.match "0.2.0-rc2"]
                 
                 ;; TODO: Figure out the best way to depend on datomic
                 ;; for a public library. We need to provide consumers
                 ;; of this library the ability to use either
                 ;; datomic-free or datomic-pro.
                 [com.datomic/datomic-pro "0.8.3862"]]

  :repositories [["snapshots" {:url "https://oss.sonatype.org/content/repositories/snapshots/"
                               :creds :gpg}]]  
  
  :profiles {:dev {:dependencies [[clj-stacktrace "0.2.5"]
                                  [org.clojure/tools.namespace "0.2.3"]
                                  [criterium "0.4.1"]]
                   :source-paths ["dev"]}})
