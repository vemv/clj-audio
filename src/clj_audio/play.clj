(ns clj-audio.player
  (:require
   [clj-audio.core :refer :all]
   [clj-audio.sampled :refer :all])
  (:import
   (java.io File)))

;;;; playlist

(def music-file-extensions ["mp3" "flac"])

(defn extension [f]
  (let [n (.getName f)
        i (.lastIndexOf n ".")]
    (.substring n (+ i 1))))

(defn music-file? [f]
  (when (.isFile f)
    (some #(= % (extension f))
          music-file-extensions)))

(defn music-files [paths]
  (filter (fn [path]
            (-> path File. music-file?))
          paths))

(defn playlist [songs current action]
  (condp = action
    :previous (when (> @current 0)
                (swap! current dec)
                (nth songs @current))
    :next     (when (< @current (dec (count songs)))
                (swap! current inc)
                (nth songs @current))
    :random   (do
                (swap! current (constantly (rand-int (count songs))))
                (nth songs @current))
    (nth songs @current)))

(def next-playlist-id (atom 0))

(defn make-playlist [paths]
  (let [songs (music-files (if (coll? paths)
                             paths
                             [paths]))
        current (atom 0)]
    {:id (swap! next-playlist-id inc)
     :fn (fn [action]
           (playlist songs current action))}))

;;;; player actions

(def player-actions (ref clojure.lang.PersistentQueue/EMPTY))

(defn clear-actions []
  (dosync
    (ref-set player-actions
             clojure.lang.PersistentQueue/EMPTY)))

(defn send-action [action & actions]
  (dosync
    (alter player-actions #(apply conj % action actions))))

(defn pop-current-action []
  (dosync
    (let [action (peek @player-actions)]
      (when action
        (alter player-actions pop))
      action)))

(defn wait? [action]
  (or (= action :pause)
      (= action :stop)))

;;;; player

(defn read-file [file]
  (try
    (->stream file)
    (catch Exception e
      (println "Error reading " file "\n" e))))

(def default-sleep-duration 20)

(defmacro sleep-while [test]
  `(while ~test
     (Thread/sleep default-sleep-duration)))

(defn play_ [source audio-stream mark]
  (sleep-while (running? source))
  (future
    (-> audio-stream
        (skip @mark)
        decode
        (play-with source)))
  (sleep-while (not (running? source))))

(defn action-loop [source audio-stream]
  (loop [action (pop-current-action)]
    (when (and action (running? source))
      (stop))
    (Thread/sleep default-sleep-duration)
    (cond
      (finished? audio-stream) nil
      (and action (not (wait? action))) action
      :default (recur (pop-current-action)))))

(def default-action :next)

(defn player [playlist]
  (future
    (stop)
    (while (or @*playing* (not @play-cycle-complete?))
      (Thread/sleep default-sleep-duration))
    (let [source (make-line :source *default-format* (* 4 1024))
          mark (ref 0)]
      (loop [song ((:fn playlist) :play)]
        (when (and song (= (:id playlist)
                           @next-playlist-id))
          (if-let [stream (read-file song)]
            (let [length (.available stream)
                  _ (play_ source stream mark)
                  action (action-loop source stream)]
              (dosync
                (ref-set mark (if (and (= action :play)
                                       (not (finished? stream)))
                                (- length (.available stream))
                                0)))
              (when-not (= action :close)
                (recur ((:fn playlist) (or action default-action)))))
            (recur ((:fn playlist) default-action)))))

      (flush-close source)))
  playlist)

(comment
  (stop)
  (-> "/Users/vemv/Dropbox/Tracks/Psychemagik - Dreams - Fleetwood Mac (Psychemagik Remix).mp3"
      make-playlist
      player)
  (-> "/Users/vemv/Dropbox/Tracks/St Germain - 01. Real Blues (Atjazz Astro Remix).flac"
      make-playlist
      player))
