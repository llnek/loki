;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

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

