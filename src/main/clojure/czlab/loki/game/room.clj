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
;; Copyright © 2013-2022, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.room

  (:require [czlab.loki.xpis :as loki]
            [czlab.basal.core :as c]
            [czlab.basal.util :as bu]
            [czlab.loki.util :as u]
            [czlab.niou.core :as cc]
            [czlab.loki.session :as ss]
            [czlab.loki.net.core :as nc]
            [czlab.loki.game.arena :as ga])

  (:use [flatland.ordered.map])

  (:import [clojure.lang Keyword]
           [java.util.concurrent.atomic AtomicInteger]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private _room-mutex_ (Object.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; {game-id -> map
;;             { room-id -> room }}
(def ^:private free-rooms (atom (ordered-map)))
(def ^:private game-rooms (atom (ordered-map)))
(def ^:private vacancy 1000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn count-free-rooms

  ""
  ^long [gameid]

  (count (get @free-rooms gameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn count-game-rooms

  ""
  ^long [gameid]

  (count (get @game-rooms gameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn clear-free-rooms

  "Internal only"
  {:no-doc true}
  [gameid]

  (locking _room-mutex_
    (swap! free-rooms assoc gameid (ordered-map))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn clear-game-rooms

  "Internal only"
  {:no-doc true}
  [gameid]

  (locking _room-mutex_
    (swap! game-rooms assoc gameid (ordered-map))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- remove-xxx-room

  ""
  [gameid roomid db dbtxt]

  (locking _room-mutex_
    (when-some [r (-> (@db gameid)
                      (get roomid))]
      (c/debug "remove %s: %s, game: %s" dbtxt roomid gameid)
      (swap! db update-in [gameid] dissoc roomid)
      r)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn remove-game-room

  "Remove an active room"
  [gameid roomid]

  (remove-xxx-room gameid roomid game-rooms "room(A)"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn remove-free-room

  "Remove a waiting room"
  [gameid roomid]

  (remove-xxx-room gameid roomid free-rooms "room(F)"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- add-xxx-room

  ""
  [room db dbt]

  {:pre [(some? room)]}

  (locking _room-mutex_
    (let [gid (c/id  (:game @room))
          rid (c/id room)
          m? (contains? @db gid)]
      (c/debug "adding a %s: %s, game: %s" dbt rid gid)
      (if m?
        (swap! db update-in [gid] assoc rid room)
        (swap! db assoc gid (ordered-map rid room)))
      room)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- add-free-room

  "Add a new partially filled room into the pending set"
  [room]

  (add-xxx-room room free-rooms "room(F)"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- add-game-room

  "Move room into the active set"
  [room]

  (add-xxx-room room game-rooms "room(A)"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- detach-free-room

  ""
  [gameid roomid]

  (c/debug "%s: %s, game: %s"
             "found a room(F)" roomid gameid)
  (swap! free-rooms update-in [gameid] dissoc roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-free-room

  "Returns a free room which is detached from the pending set"

  ([gameid roomid]
   (locking _room-mutex_
     (when-some [r (get (@free-rooms gameid) roomid)]
       (detach-free-room gameid (c/id?? r))
       r)))

  ([gameid]
   (locking _room-mutex_
     (when-some [[_ r] (first (@free-rooms gameid))]
       (detach-free-room gameid (c/id?? r))
       r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn lookup-free-room

  ""
  [gameid roomid]

  (c/debug "looking for room(F): %s, game: %s" roomid gameid)
  (get (@free-rooms gameid) roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn lookup-game-room

  ""
  [gameid roomid]

  (c/debug "looking for room(A): %s, game: %s" roomid gameid)
  (get (@game-rooms gameid) roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- new-free-room

  ""
  [game {:keys [source] :as options}]

  (let [p (partial remove-game-room (:id game))
        room (ga/arena<> game p source)]
    (c/debug "created a new room(F): %s" (:id room))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn connect

  "Connect a player to a romm"
  [room player arg]

  (locking room
    (let [{:keys [conns numctr]}
          @room
          n (. ^AtomicInteger
               numctr
               incrementAndGet)
          s (ss/session<> room
                          player
                          (merge arg {:number n}))]
      (swap! room assoc :conns (assoc conns (:id s) s))
      (doto s ss/add-session))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn disconnect

  "Disconnect a player from room"
  [room session]

  (let [{:keys [player]} @session
        {:keys [conns disp]} @room]
    (swap! room assoc :conns (dissoc conns (:id session)))
    (ss/remove-session session)
    (loki/unsub-if-session disp session)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn open-room

  ""
  [game plyr arg]

  (locking _room-mutex_
    (let [room (or (get-free-room (c/id game))
                   (new-free-room game arg))
          s (get-in arg [:body :settings])
          pss (some-> room (connect plyr s))]
      (when (some? pss)
        (let
          [ch (:socket arg)
           src {:gameid (c/sname (:id game))
                :roomid (:id room)
                :puid (:id plyr)
                :pnum (:number @pss)}
           evt (nc/private-event<> loki/playreq-ok src)]
          (c/open pss arg)
          (cc/setattr ch u/RMSN {:room room :session pss})
          (c/debug "replying msg to user: %s" (nc/pretty-event evt))
          (nc/reply-event ch evt)
          (nc/bcast! room
                     loki/player-joined
                     (select-keys src [:pnum :puid]))
          (if (loki/can-open-room? room)
            (do
              (c/debug "room has enough players, can open")
              (c/debug "room.canOpen = true")
              (add-game-room room)
              (c/open room))
            (add-free-room room))))
      pss)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn join-room

  ""
  [plyr gameid roomid arg]

  {:pre [(some? plyr)]}

  (locking _room-mutex_
    (let [room (or (lookup-game-room gameid roomid)
                   (get-free-room gameid roomid))
          s (get-in arg [:body :settings])
          game (:game (some-> room deref))
          ch (:socket arg)]
      (when (and room
                 (< (loki/count-players room)
                    (:maxPlayers game)))
        (let [pss (connect room plyr s)
              src {:gameid (c/sname (:id game))
                   :roomid (:id room)
                   :puid (:id plyr)
                   :pnum (:number @pss)}
              evt (nc/private-event<> loki/joinreq-ok src)]
          (c/open pss arg)
          (cc/setattr ch u/RMSN {:room room :session pss})
          (nc/reply-event ch evt)
          (c/debug "replying back to user: %s" (nc/pretty-event evt))
          (if (loki/can-open-room? room)
            (do
              (c/debug "room has enough players, can open")
              (c/debug "room.canOpen = true")
              (add-game-room room)
              (c/open room {})
            (add-free-room room))
          pss))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

