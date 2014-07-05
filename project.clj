(defproject flowdock-xmpp "0.1.0-SNAPSHOT"
  :description "Unofficial Flowdock XMPP (Jabber) Gateway"
  :url "https://github.com/osener/flowdock-xmpp"
  :license {:name "MIT License"
            :url "http://www.opensource.org/licenses/mit-license.php"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2202"
                  :exclusions [org.apache.ant/ant]]
                 [org.clojure/core.async "0.1.298.0-2a82a1-alpha"]]
  :plugins [[lein-cljsbuild "1.0.3"]]
  :cljsbuild {:builds [{:source-paths ["src"]
                        :compiler {:target :nodejs
                                   :output-dir "bin/build"
                                   :output-to "bin/flowdock-xmpp.js"
                                   :source-map "bin/flowdock-xmpp.js.map"
                                   :optimizations :simple
                                   :static-fns true
                                   :pretty-print true}}]})
