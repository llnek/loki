;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.sys.player

  (:require [czlab.basal.logging :as log])

  (:use [czlab.basal.core]
        [czlab.basal.io]
        [czlab.basal.str])

  (:import [czlab.loki.sys Player Session]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; player-db
;; {player-id -> {:p player :s {id -> session}}}
(def ^:private player-db (atom {}))
;; map of nicknames
(def ^:private userid-db (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removePlayer
  "Remove player and
  close all his sessions" ^Player [user]

  (let [pid (get @userid-db user)
        m (get @player-db pid)]
    (when (some? m)
      (swap! player-db dissoc pid)
      (doseq [[_ v] (:s m)] (closeQ v)) (:p m))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- player<>
  "" ^Player
  [^String user ^chars pwd]
  {:pre [(hgl? user)]}

  (let [impl (muble<>)
        pid (uid<>)]
    (reify

      Player

      (nickname [_] user)
      (id [_] pid)

      (removeSession [_ ps]
        (if-some [m (get @player-db pid)]
          (swap! player-db
                 assoc
                 pid
                 (update-in m
                            [:s]
                            dissoc (.id ps)))))

      (countSessions [_]
        (if-some [m (get @player-db pid)]
          (int (count (:s m)))
          (int 0)))

      (addSession [_ ps]
        (let [m (get @player-db pid)]
          (swap! player-db
                 assoc
                 pid
                 (update-in m
                            [:s]
                            assoc (.id ps) ps))))

      (updateGist [_ g] (if (map? g)
                          (.copyEx impl g)))
      (gist [_] (.intern impl))

      (logout [_]
        (removePlayer user)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createPlayer
  ""
  ^Player
  [^String user ^chars pwd]
  (let [pid (get @userid-db user)
        m (get @player-db pid)]
    (if (some? m)
      (:p m)
      (let [p2 (player<> user pwd)]
        (swap! userid-db assoc user (.id p2))
        (swap! player-db
               assoc (.id p2) {:p p2 :s {}})
        p2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupPlayer "" {:tag Player}

  ([user pwd] (createPlayer user pwd))
  ([user] (->> (get @userid-db user)
               (get @player-db) :p )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

