;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.player

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.xlib.guids]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str])

  (:import [czlab.loki.core Room Player Session]
           [czlab.loki.game Game]))

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
  ""
  ^Player
  [user]
  (let [uid (get @userid-db user)
        m (get @player-db uid)]
    (when (some? m)
      (swap! player-db dissoc uid)
      (doseq [[_ v] (:s m)] (closeQ v))
      (:p m))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- player<>
  ""
  ^Player
  [^String user ^String pwd]
  {:pre [(hgl? user)]}
  (let [impl (muble<>)
        uid (uuid<>)]
    (reify

      Player

      (nickname [_] user)
      (id [_] uid)

      (removeSession [_ ps]
        (if-some [m (get @player-db uid)]
          (swap! player-db
                 assoc
                 uid
                 (update-in m
                            [:s]
                            dissoc (.id ps)))))

      (countSessions [_]
        (if-some [m (get @player-db uid)]
          (int (count (:s m)))
          (int 0)))

      (addSession [_ ps]
        (let [m (get @player-db uid)]
          (swap! player-db
                 assoc
                 uid
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
  [^String user ^String pwd]
  (let [uid (get @userid-db user)
        m (get @player-db uid)]
    (if (some? m)
      (:p m)
      (let [p2 (player<> user pwd)]
        (swap! userid-db assoc user (.id p2))
        (swap! player-db
               assoc (.id p2) {:p p2 :s {}})
        p2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupPlayer
  ""
  {:tag Player}
  ([user pwd] (createPlayer user pwd))
  ([user] (->> (get @userid-db user)
               (get @player-db)
               (:p ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

