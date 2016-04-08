(defproject witan.router "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [me.raynes/fs "1.4.6"]
                 [me.raynes/conch "0.8.0"]
                 [de.ubercode.clostache/clostache "1.4.0"]]
  :profiles {:dev {:plugins [[lein-bin "0.3.5"]]}}
  :main witan.router
  :bin {:name "create-docker"
        :bootclasspath true})
