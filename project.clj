(defproject clj-audio/clj-audio "0.3.0-SNAPSHOT"
  :description "Idiomatic Clojure wrapper for the Java Sound API."
  :dependencies [[org.clojure/clojure "1.10.0"]
                 ;; https://github.com/pdudits/soundlibs:
                 [com.googlecode.soundlibs/tritonus-share "0.3.7.4"]
                 [com.googlecode.soundlibs/jlayer "1.0.1.4"]
                 [com.googlecode.soundlibs/mp3spi "1.9.5.4"]])
